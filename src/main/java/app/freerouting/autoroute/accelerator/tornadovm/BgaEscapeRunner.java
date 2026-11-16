package app.freerouting.autoroute.accelerator.tornadovm;

import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.PARAM_COUNT;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_CURRENT_RING;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_GRID_H;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_GRID_STEP;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_GRID_W;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_LAYERS;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_OBSTACLE_COUNT;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_ORIGIN_X;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_ORIGIN_Y;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_PIN_COUNT;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.P_PITCH;
import static app.freerouting.autoroute.accelerator.tornadovm.BgaEscapeKernels.VIA_SITE_STRIDE;

import app.freerouting.autoroute.accelerator.BgaEscapeGrid;
import java.util.stream.IntStream;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Runs the BGA escape kernels for one package, either through TornadoVM on a device or as plain
 * Java on the calling thread. Both paths run the same per-item methods in the same order, so they
 * return the same solution buffer.
 */
public final class BgaEscapeRunner {

  /** Negotiation passes per ring, as in the OpenCL host code. */
  public static final int NEGOTIATION_PASSES = 6;

  private static final int BLOCK = 64;
  private static final int CELL_BLOCK = 256;

  private BgaEscapeRunner() {}

  /**
   * Runs the kernels through TornadoVM.
   *
   * @param device the device to run on, or {@code null} for the TornadoVM default device
   * @return the solution buffer, {@link BgaEscapeGrid#SOLUTION_STRIDE} ints per pin
   */
  public static int[] runOnDevice(BgaEscapeGrid grid, TornadoDevice device) {
    IntArray params = params(grid);
    IntArray pins = IntArray.fromArray(grid.pins());
    IntArray obstacles = IntArray.fromArray(grid.obstacles());
    IntArray occupancy = new IntArray(grid.totalCells());
    IntArray rudy = new IntArray(grid.gridWidth() * grid.gridHeight());
    IntArray viaSites = new IntArray(grid.pinCount() * VIA_SITE_STRIDE);
    IntArray history = new IntArray(grid.totalCells());
    IntArray winners = new IntArray(grid.totalCells());
    IntArray solutions = new IntArray(grid.pinCount() * BgaEscapeGrid.SOLUTION_STRIDE);
    history.init(0);
    solutions.init(0);
    winners.init(BgaEscapeKernels.NO_WINNER);

    KernelContext context = new KernelContext();

    // The rasterisation and via site kernels run once per package, the negotiation kernels once per
    // ring and pass. They run as two plans: the first copies its small outputs (RUDY map and via
    // sites) back, the second starts from host data. This keeps every buffer explicitly initialised
    // on each backend instead of relying on buffers shared between graphs.
    TaskGraph setup =
        new TaskGraph("bgaSetup")
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, params, pins, obstacles)
            .task(
                "rasterize",
                BgaEscapeKernels::rasterizeObstaclesAndRudy,
                context,
                params,
                obstacles,
                pins,
                occupancy,
                rudy)
            .task(
                "viaSites",
                BgaEscapeKernels::evaluateViaDogboneSites,
                context,
                params,
                pins,
                occupancy,
                viaSites)
            .transferToHost(DataTransferMode.EVERY_EXECUTION, rudy, viaSites);

    TaskGraph negotiate =
        new TaskGraph("bgaNegotiate")
            .transferToDevice(
                DataTransferMode.FIRST_EXECUTION, pins, rudy, viaSites, history, winners, solutions)
            .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
            .task(
                "expand",
                BgaEscapeKernels::expandRingCandidates,
                context,
                params,
                pins,
                viaSites,
                rudy,
                history,
                solutions)
            .task("elect", BgaEscapeKernels::electCellWinners, context, params, solutions, winners)
            .task(
                "commit",
                BgaEscapeKernels::commitCellWinners,
                context,
                params,
                solutions,
                winners,
                history)
            .transferToHost(DataTransferMode.UNDER_DEMAND, solutions);

    WorkerGrid1D cells = new WorkerGrid1D(roundUp(grid.totalCells(), CELL_BLOCK));
    cells.setLocalWork(CELL_BLOCK, 1, 1);
    WorkerGrid1D perPin = new WorkerGrid1D(roundUp(grid.pinCount(), BLOCK));
    perPin.setLocalWork(BLOCK, 1, 1);
    GridScheduler setupGrid = new GridScheduler();
    setupGrid.addWorkerGrid("bgaSetup.rasterize", cells);
    setupGrid.addWorkerGrid("bgaSetup.viaSites", perPin);
    GridScheduler negotiateGrid = new GridScheduler();
    negotiateGrid.addWorkerGrid("bgaNegotiate.expand", perPin);
    negotiateGrid.addWorkerGrid("bgaNegotiate.elect", perPin);
    negotiateGrid.addWorkerGrid("bgaNegotiate.commit", perPin);

    try (TornadoExecutionPlan plan = new TornadoExecutionPlan(setup.snapshot())) {
      plan.withGridScheduler(setupGrid);
      if (device != null) {
        plan.withDevice(device);
      }
      plan.execute();
    } catch (Exception e) {
      throw new IllegalStateException("TornadoVM BGA escape setup failed: " + e.getMessage(), e);
    }

    try (TornadoExecutionPlan plan = new TornadoExecutionPlan(negotiate.snapshot())) {
      plan.withGridScheduler(negotiateGrid);
      if (device != null) {
        plan.withDevice(device);
      }
      TornadoExecutionResult result = null;
      for (int ring = 0; ring <= grid.maxRing(); ring++) {
        params.set(P_CURRENT_RING, ring);
        for (int pass = 0; pass < NEGOTIATION_PASSES; pass++) {
          result = plan.execute();
        }
      }
      if (result != null) {
        result.transferToHost(solutions);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "TornadoVM BGA escape negotiation failed: " + e.getMessage(), e);
    }
    return solutions.toHeapArray();
  }

  /**
   * Runs the same kernels as plain Java. Gives the same solution buffer as {@link #runOnDevice},
   * and is the CPU fallback when no GPU is available.
   */
  public static int[] runOnCpu(BgaEscapeGrid grid) {
    IntArray params = params(grid);
    IntArray pins = IntArray.fromArray(grid.pins());
    IntArray obstacles = IntArray.fromArray(grid.obstacles());
    IntArray occupancy = new IntArray(grid.totalCells());
    IntArray rudy = new IntArray(grid.gridWidth() * grid.gridHeight());
    IntArray viaSites = new IntArray(grid.pinCount() * VIA_SITE_STRIDE);
    IntArray history = new IntArray(grid.totalCells());
    IntArray winners = new IntArray(grid.totalCells());
    IntArray solutions = new IntArray(grid.pinCount() * BgaEscapeGrid.SOLUTION_STRIDE);
    occupancy.init(0);
    rudy.init(0);
    history.init(0);
    solutions.init(0);
    winners.init(BgaEscapeKernels.NO_WINNER);

    // Each cell and each pin writes only its own entries, so these two loops can run in parallel
    // without changing the result. The negotiation passes below stay sequential.
    IntStream.range(0, grid.totalCells())
        .parallel()
        .forEach(
            cell -> BgaEscapeKernels.rasterizeCell(cell, params, obstacles, pins, occupancy, rudy));
    IntStream.range(0, grid.pinCount())
        .parallel()
        .forEach(pin -> BgaEscapeKernels.evaluateViaSites(pin, params, pins, occupancy, viaSites));
    for (int ring = 0; ring <= grid.maxRing(); ring++) {
      params.set(P_CURRENT_RING, ring);
      for (int pass = 0; pass < NEGOTIATION_PASSES; pass++) {
        for (int pin = 0; pin < grid.pinCount(); pin++) {
          BgaEscapeKernels.expandCandidate(pin, params, pins, viaSites, rudy, history, solutions);
        }
        for (int pin = 0; pin < grid.pinCount(); pin++) {
          BgaEscapeKernels.electWinner(pin, params, solutions, winners);
        }
        for (int pin = 0; pin < grid.pinCount(); pin++) {
          BgaEscapeKernels.commitWinner(pin, params, solutions, winners, history);
        }
      }
    }
    return solutions.toHeapArray();
  }

  private static IntArray params(BgaEscapeGrid grid) {
    IntArray params = new IntArray(PARAM_COUNT);
    params.set(P_PIN_COUNT, grid.pinCount());
    params.set(P_OBSTACLE_COUNT, grid.obstacleCount());
    params.set(P_GRID_W, grid.gridWidth());
    params.set(P_GRID_H, grid.gridHeight());
    params.set(P_LAYERS, grid.layerCount());
    params.set(P_GRID_STEP, grid.gridStep());
    params.set(P_ORIGIN_X, grid.originX());
    params.set(P_ORIGIN_Y, grid.originY());
    params.set(P_PITCH, grid.pitch());
    params.set(P_CURRENT_RING, 0);
    return params;
  }

  private static int roundUp(int value, int block) {
    return ((Math.max(1, value) + block - 1) / block) * block;
  }
}
