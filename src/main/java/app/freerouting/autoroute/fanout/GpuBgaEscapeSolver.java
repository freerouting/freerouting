package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.accelerator.AcceleratorRegistry;
import app.freerouting.autoroute.accelerator.BgaEscapeGrid;
import app.freerouting.autoroute.accelerator.BgaEscapeKernelBackend;
import app.freerouting.autoroute.accelerator.ComputeBackend;
import app.freerouting.autoroute.accelerator.GpuComputeSession;
import app.freerouting.autoroute.accelerator.opencl.PanamaOpenClBackend;
import app.freerouting.autoroute.accelerator.opencl.PanamaOpenClBackend.PanamaGpuSession;
import app.freerouting.autoroute.accelerator.opencl.PanamaOpenClBindings;
import app.freerouting.autoroute.fanout.BgaBufferMarshaller.MarshaledBgaData;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.FanoutSettings;
import app.freerouting.settings.RouterSettings;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Host coordinator for GPU/iGPU BGA escape routing. Manages driver memory allocation, OpenCL kernel
 * sequencing across concentric Chebyshev rings, iterative rip-up negotiation, and deterministic
 * time-budget agnosticism.
 */
public final class GpuBgaEscapeSolver {

  /** Encapsulates an escaped pin solution ready for continuous DRC validation. */
  public record PinEscapeProposal(
      BgaPinDescriptor pinDescriptor,
      IntPoint viaCenter,
      int targetLayer,
      int cost,
      boolean escaped) {}

  private GpuBgaEscapeSolver() {}

  /**
   * Solves BGA escape routing on the GPU for the given board and descriptor. If GPU acceleration is
   * disabled, unavailable, or encounters an issue, gracefully returns an empty list triggering CPU
   * fallback.
   *
   * @param board routing board
   * @param bga BGA escape descriptor
   * @param settings router settings
   * @return list of escaped pin proposals, or empty list on fallback
   */
  public static List<PinEscapeProposal> solve(
      RoutingBoard board, BgaEscapeDescriptor bga, RouterSettings settings) {
    return solve(board, bga, settings, null);
  }

  /**
   * Executes GPU-accelerated BGA escape routing for the specified area-array component, reporting
   * progress messages to an optional info logger.
   *
   * @param board routing board
   * @param bga BGA escape descriptor
   * @param settings router settings
   * @param infoLogger optional logger callback for UI/job messaging
   * @return list of escaped pin proposals, or empty list on fallback
   */
  public static List<PinEscapeProposal> solve(
      RoutingBoard board,
      BgaEscapeDescriptor bga,
      RouterSettings settings,
      java.util.function.Consumer<String> infoLogger) {

    FanoutSettings fs = (settings != null) ? settings.fanout : null;
    ComputeBackend backend = AcceleratorRegistry.discover(fs);
    if (backend == null || !backend.isAvailable()) {
      String msg = "GPU acceleration unavailable, using CPU fanout fallback";
      FRLogger.info(msg);
      if (infoLogger != null) {
        infoLogger.accept(msg);
      }
      return Collections.emptyList();
    }

    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, bga, settings)) {
      return executeOnBackend(backend, board, bga, data, fs, infoLogger);
    } catch (Throwable t) {
      String msg =
          "GPU BGA escape solver encountered error, falling back to CPU: " + t.getMessage();
      FRLogger.warn(msg);
      if (infoLogger != null) {
        infoLogger.accept(msg);
      }
      return Collections.emptyList();
    }
  }

  private static List<PinEscapeProposal> executeOnBackend(
      ComputeBackend backend,
      RoutingBoard board,
      BgaEscapeDescriptor bga,
      MarshaledBgaData data,
      FanoutSettings settings,
      java.util.function.Consumer<String> infoLogger) {

    int pinCount = data.pinCount();
    if (pinCount <= 0) {
      return Collections.emptyList();
    }

    if (backend instanceof BgaEscapeKernelBackend kernelBackend) {
      try {
        return executeKernelBackend(backend, kernelBackend, bga, data, infoLogger);
      } catch (Throwable t) {
        String msg =
            "GPU kernel execution failed, falling back to deterministic wavefront: "
                + t.getMessage();
        FRLogger.warn(msg);
        if (infoLogger != null) {
          infoLogger.accept(msg);
        }
      }
    }

    if (backend instanceof PanamaOpenClBackend openClBackend) {
      try {
        return executeOpenClKernels(openClBackend, board, bga, data, settings, infoLogger);
      } catch (Throwable t) {
        String msg =
            "OpenCL execution failed, falling back to deterministic wavefront: " + t.getMessage();
        FRLogger.warn(msg);
        if (infoLogger != null) {
          infoLogger.accept(msg);
        }
      }
    }

    // Use host-side simulation when running against headless or synthetic backend
    // to guarantee determinism in test suites and zero-device CI environments.
    return executeDeterministicWavefront(board, bga, data);
  }

  private static List<PinEscapeProposal> executeOpenClKernels(
      PanamaOpenClBackend openClBackend,
      RoutingBoard board,
      BgaEscapeDescriptor bga,
      MarshaledBgaData data,
      FanoutSettings settings,
      java.util.function.Consumer<String> infoLogger)
      throws Throwable {

    try (GpuComputeSession rawSession = openClBackend.openSession(settings)) {
      if (!(rawSession instanceof PanamaGpuSession session)) {
        return executeDeterministicWavefront(board, bga, data);
      }

      int pinCount = data.pinCount();
      int gridW = data.gridWidth();
      int gridH = data.gridHeight();
      int layers = data.layerCount();
      int totalCells = gridW * gridH * layers;
      int obsCount = Math.max(1, data.obstacleCount());

      FRLogger.info(
          "GPU acceleration: executing OpenCL BGA escape solver on "
              + session.deviceInfo().deviceName()
              + " for component "
              + bga.component().name
              + " ("
              + pinCount
              + " pins, grid "
              + gridW
              + "x"
              + gridH
              + "x"
              + layers
              + ")...");

      PanamaOpenClBindings bindings = session.bindings();
      Arena arena = session.arena();
      MemorySegment context = session.context();
      MemorySegment queue = session.queue();
      MemorySegment program = session.program();

      if (program == null || program.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("OpenCL program is null or uncompiled");
      }

      MemorySegment errBuf = arena.allocate(ValueLayout.JAVA_INT);

      // 1. Allocate device buffers
      long pinBufSize = (long) pinCount * 12 * 4;
      long obsBufSize = (long) obsCount * 10 * 4;
      long occGridSize = (long) totalCells * 4;
      long rudyGridSize = (long) gridW * gridH * 4;
      long viaSiteBufSize = (long) pinCount * 8 * 4 * 4;
      long cellHistCostSize = (long) totalCells * 4;
      long solBufSize = (long) pinCount * 16 * 4;
      long cellWinnerSize = (long) totalCells * 4;
      long cellDemandSize = (long) totalCells * 4;

      List<MemorySegment> memObjects = new ArrayList<>();
      List<MemorySegment> kernels = new ArrayList<>();

      try {
        MemorySegment clPinBuf =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_ONLY | PanamaOpenClBindings.CL_MEM_COPY_HOST_PTR,
                pinBufSize,
                data.pinBuffer(),
                errBuf);
        memObjects.add(clPinBuf);
        MemorySegment clObsBuf =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_ONLY | PanamaOpenClBindings.CL_MEM_COPY_HOST_PTR,
                obsBufSize,
                data.obstacleBuffer(),
                errBuf);
        memObjects.add(clObsBuf);
        MemorySegment clOccGrid =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                occGridSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clOccGrid);
        MemorySegment clRudyGrid =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                rudyGridSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clRudyGrid);
        MemorySegment clViaSites =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                viaSiteBufSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clViaSites);
        MemorySegment clHistCost =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                cellHistCostSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clHistCost);
        MemorySegment clSolBuf =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                solBufSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clSolBuf);
        MemorySegment clCellWinner =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                cellWinnerSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clCellWinner);
        MemorySegment clCellDemand =
            bindings.clCreateBuffer(
                context,
                PanamaOpenClBindings.CL_MEM_READ_WRITE,
                cellDemandSize,
                MemorySegment.NULL,
                errBuf);
        memObjects.add(clCellDemand);

        MemorySegment k1Name = arena.allocateFrom("rasterize_obstacles_and_rudy");
        MemorySegment k2Name = arena.allocateFrom("evaluate_via_dogbone_sites");
        MemorySegment k3Name = arena.allocateFrom("expand_ring_candidates");
        MemorySegment k4aName = arena.allocateFrom("elect_cell_winners");
        MemorySegment k4bName = arena.allocateFrom("commit_cell_winners");

        MemorySegment k1 = bindings.clCreateKernel(program, k1Name, errBuf);
        kernels.add(k1);
        MemorySegment k2 = bindings.clCreateKernel(program, k2Name, errBuf);
        kernels.add(k2);
        MemorySegment k3 = bindings.clCreateKernel(program, k3Name, errBuf);
        kernels.add(k3);
        MemorySegment k4a = bindings.clCreateKernel(program, k4aName, errBuf);
        kernels.add(k4a);
        MemorySegment k4b = bindings.clCreateKernel(program, k4bName, errBuf);
        kernels.add(k4b);

        // Initialize cellWinner buffer with 0xFFFFFFFF (max unsigned int)
        MemorySegment hostWinners = arena.allocate(ValueLayout.JAVA_INT, totalCells);
        for (int c = 0; c < totalCells; c++) {
          hostWinners.set(ValueLayout.JAVA_INT, (long) c * 4, 0xFFFFFFFF);
        }
        bindings.clEnqueueWriteBuffer(
            queue,
            clCellWinner,
            PanamaOpenClBindings.CL_TRUE,
            0,
            cellWinnerSize,
            hostWinners,
            0,
            MemorySegment.NULL,
            MemorySegment.NULL);

        // Helper buffers for kernel arguments
        MemorySegment argBuf = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment argInt = arena.allocate(ValueLayout.JAVA_INT);

        // Set args for Kernel 1: rasterize_obstacles_and_rudy
        setKernelArgMem(bindings, k1, 0, clObsBuf, argBuf);
        setKernelArgInt(bindings, k1, 1, obsCount, argInt);
        setKernelArgMem(bindings, k1, 2, clPinBuf, argBuf);
        setKernelArgInt(bindings, k1, 3, pinCount, argInt);
        setKernelArgMem(bindings, k1, 4, clOccGrid, argBuf);
        setKernelArgMem(bindings, k1, 5, clRudyGrid, argBuf);
        setKernelArgInt(bindings, k1, 6, gridW, argInt);
        setKernelArgInt(bindings, k1, 7, gridH, argInt);
        setKernelArgInt(bindings, k1, 8, layers, argInt);
        setKernelArgInt(bindings, k1, 9, data.gridStepBoardUnits(), argInt);
        setKernelArgInt(bindings, k1, 10, data.originX(), argInt);
        setKernelArgInt(bindings, k1, 11, data.originY(), argInt);

        // Enqueue Kernel 1: 3D global work size [ceil(gridW/16)*16, ceil(gridH/16)*16, layers]
        long g1X = ((gridW + 15) / 16) * 16L;
        long g1Y = ((gridH + 15) / 16) * 16L;
        long g1Z = layers;
        MemorySegment gws1 = arena.allocate(ValueLayout.JAVA_LONG, 3);
        gws1.set(ValueLayout.JAVA_LONG, 0, g1X);
        gws1.set(ValueLayout.JAVA_LONG, 8, g1Y);
        gws1.set(ValueLayout.JAVA_LONG, 16, g1Z);
        MemorySegment lws1 = arena.allocate(ValueLayout.JAVA_LONG, 3);
        lws1.set(ValueLayout.JAVA_LONG, 0, 16L);
        lws1.set(ValueLayout.JAVA_LONG, 8, 16L);
        lws1.set(ValueLayout.JAVA_LONG, 16, 1L);

        bindings.clEnqueueNDRangeKernel(
            queue,
            k1,
            3,
            MemorySegment.NULL,
            gws1,
            lws1,
            0,
            MemorySegment.NULL,
            MemorySegment.NULL);

        // Set args for Kernel 2: evaluate_via_dogbone_sites
        setKernelArgMem(bindings, k2, 0, clPinBuf, argBuf);
        setKernelArgMem(bindings, k2, 1, clOccGrid, argBuf);
        setKernelArgMem(bindings, k2, 2, clViaSites, argBuf);
        setKernelArgInt(bindings, k2, 3, pinCount, argInt);
        setKernelArgInt(bindings, k2, 4, gridW, argInt);
        setKernelArgInt(bindings, k2, 5, gridH, argInt);
        setKernelArgInt(bindings, k2, 6, layers, argInt);
        setKernelArgInt(bindings, k2, 7, data.gridStepBoardUnits(), argInt);
        setKernelArgInt(bindings, k2, 8, data.originX(), argInt);
        setKernelArgInt(bindings, k2, 9, data.originY(), argInt);
        setKernelArgInt(bindings, k2, 10, (int) Math.round(bga.pitchBoardUnits()), argInt);
        setKernelArgInt(bindings, k2, 11, data.gridStepBoardUnits(), argInt);

        long g2 = ((pinCount + 63) / 64) * 64L;
        MemorySegment gws2 = arena.allocate(ValueLayout.JAVA_LONG, 1);
        gws2.set(ValueLayout.JAVA_LONG, 0, g2);
        MemorySegment lws2 = arena.allocate(ValueLayout.JAVA_LONG, 1);
        lws2.set(ValueLayout.JAVA_LONG, 0, 64L);

        bindings.clEnqueueNDRangeKernel(
            queue,
            k2,
            1,
            MemorySegment.NULL,
            gws2,
            lws2,
            0,
            MemorySegment.NULL,
            MemorySegment.NULL);

        // Kernel 3 args invariant setup
        setKernelArgMem(bindings, k3, 0, clPinBuf, argBuf);
        setKernelArgMem(bindings, k3, 1, clViaSites, argBuf);
        setKernelArgMem(bindings, k3, 2, clOccGrid, argBuf);
        setKernelArgMem(bindings, k3, 3, clRudyGrid, argBuf);
        setKernelArgMem(bindings, k3, 4, clHistCost, argBuf);
        setKernelArgMem(bindings, k3, 5, clSolBuf, argBuf);
        setKernelArgInt(bindings, k3, 6, pinCount, argInt);
        // arg 7 is currentRing
        setKernelArgInt(bindings, k3, 8, gridW, argInt);
        setKernelArgInt(bindings, k3, 9, gridH, argInt);
        setKernelArgInt(bindings, k3, 10, layers, argInt);
        setKernelArgInt(bindings, k3, 11, 64, argInt); // maxWavefrontSteps
        setKernelArgInt(bindings, k3, 12, data.originX(), argInt);
        setKernelArgInt(bindings, k3, 13, data.originY(), argInt);
        setKernelArgInt(bindings, k3, 14, data.gridStepBoardUnits(), argInt);

        // Kernel 4a (elect_cell_winners) args setup
        setKernelArgMem(bindings, k4a, 0, clSolBuf, argBuf);
        setKernelArgMem(bindings, k4a, 1, clCellWinner, argBuf);
        setKernelArgMem(bindings, k4a, 2, clCellDemand, argBuf);
        setKernelArgInt(bindings, k4a, 3, pinCount, argInt);
        // arg 4 is currentRing
        setKernelArgInt(bindings, k4a, 5, gridW, argInt);
        setKernelArgInt(bindings, k4a, 6, gridH, argInt);
        setKernelArgInt(bindings, k4a, 7, layers, argInt);
        // arg 8 is negotiationPass

        // Kernel 4b (commit_cell_winners) args setup
        setKernelArgMem(bindings, k4b, 0, clSolBuf, argBuf);
        setKernelArgMem(bindings, k4b, 1, clCellWinner, argBuf);
        setKernelArgMem(bindings, k4b, 2, clHistCost, argBuf);
        setKernelArgInt(bindings, k4b, 3, pinCount, argInt);
        // arg 4 is currentRing
        setKernelArgInt(bindings, k4b, 5, gridW, argInt);
        setKernelArgInt(bindings, k4b, 6, gridH, argInt);
        setKernelArgInt(bindings, k4b, 7, layers, argInt);
        // arg 8 is negotiationPass

        int maxRing = bga.maxRing();
        for (int r = 0; r <= maxRing; r++) {
          setKernelArgInt(bindings, k3, 7, r, argInt);
          setKernelArgInt(bindings, k4a, 4, r, argInt);
          setKernelArgInt(bindings, k4b, 4, r, argInt);

          for (int pass = 0; pass < 6; pass++) {
            // Re-initialize cellWinner buffer with 0xFFFFFFFF for each negotiation pass
            bindings.clEnqueueWriteBuffer(
                queue,
                clCellWinner,
                PanamaOpenClBindings.CL_FALSE,
                0,
                cellWinnerSize,
                hostWinners,
                0,
                MemorySegment.NULL,
                MemorySegment.NULL);

            setKernelArgInt(bindings, k4a, 8, pass, argInt);
            setKernelArgInt(bindings, k4b, 8, pass, argInt);

            // Dispatch K3: Expand ring candidates
            bindings.clEnqueueNDRangeKernel(
                queue,
                k3,
                1,
                MemorySegment.NULL,
                gws2,
                lws2,
                0,
                MemorySegment.NULL,
                MemorySegment.NULL);

            // Dispatch K4a: Elect cell winners via atomic_min
            bindings.clEnqueueNDRangeKernel(
                queue,
                k4a,
                1,
                MemorySegment.NULL,
                gws2,
                lws2,
                0,
                MemorySegment.NULL,
                MemorySegment.NULL);

            // Dispatch K4b: Commit unique winners and penalize losers
            bindings.clEnqueueNDRangeKernel(
                queue,
                k4b,
                1,
                MemorySegment.NULL,
                gws2,
                lws2,
                0,
                MemorySegment.NULL,
                MemorySegment.NULL);
          }
        }

        // Read back solutionBuffer into host memory synchronously
        MemorySegment hostSolutions = arena.allocate(ValueLayout.JAVA_INT, (long) pinCount * 16);
        bindings.clEnqueueReadBuffer(
            queue,
            clSolBuf,
            PanamaOpenClBindings.CL_TRUE,
            0,
            solBufSize,
            hostSolutions,
            0,
            MemorySegment.NULL,
            MemorySegment.NULL);

        String deviceName =
            openClBackend.getDeviceInfo() != null
                ? openClBackend.getDeviceInfo().deviceName()
                : "GPU";
        return toProposals(
            bga, data, hostSolutions.toArray(ValueLayout.JAVA_INT), deviceName, infoLogger);

      } finally {
        for (MemorySegment k : kernels) {
          try {
            bindings.clReleaseKernel(k);
          } catch (Throwable _) {
          }
        }
        for (MemorySegment m : memObjects) {
          try {
            bindings.clReleaseMemObject(m);
          } catch (Throwable _) {
          }
        }
      }
    }
  }

  /**
   * Runs the escape kernels on a backend that manages its own device memory (TornadoVM). The
   * kernels and the host loop are the same as in {@link #executeOpenClKernels}, so the solution
   * buffer is decoded the same way.
   */
  private static List<PinEscapeProposal> executeKernelBackend(
      ComputeBackend backend,
      BgaEscapeKernelBackend kernelBackend,
      BgaEscapeDescriptor bga,
      MarshaledBgaData data,
      java.util.function.Consumer<String> infoLogger) {

    BgaEscapeGrid grid =
        new BgaEscapeGrid(
            data.pinBuffer().toArray(ValueLayout.JAVA_INT),
            data.pinCount(),
            data.obstacleBuffer().toArray(ValueLayout.JAVA_INT),
            Math.max(1, data.obstacleCount()),
            data.gridWidth(),
            data.gridHeight(),
            data.layerCount(),
            data.gridStepBoardUnits(),
            data.originX(),
            data.originY(),
            (int) Math.round(bga.pitchBoardUnits()),
            bga.maxRing());

    String deviceName =
        backend.getDeviceInfo() != null ? backend.getDeviceInfo().deviceName() : "GPU";
    FRLogger.info(
        "GPU acceleration: executing BGA escape kernels on "
            + deviceName
            + " for component "
            + bga.component().name
            + " ("
            + grid.pinCount()
            + " pins, grid "
            + grid.gridWidth()
            + "x"
            + grid.gridHeight()
            + "x"
            + grid.layerCount()
            + ")...");

    int[] solutions = kernelBackend.runBgaEscape(grid);
    return toProposals(bga, data, solutions, deviceName, infoLogger);
  }

  /** Turns the solution buffer written by the escape kernels into one proposal per pin. */
  private static List<PinEscapeProposal> toProposals(
      BgaEscapeDescriptor bga,
      MarshaledBgaData data,
      int[] solutions,
      String deviceName,
      java.util.function.Consumer<String> infoLogger) {
    int pinCount = data.pinCount();
    List<PinEscapeProposal> proposals = new ArrayList<>(pinCount);
    List<BgaPinDescriptor> pins = bga.pins();
    int step = data.gridStepBoardUnits();
    int origX = data.originX();
    int origY = data.originY();

    int pinLayer = pins.isEmpty() ? 0 : pins.get(0).pin().firstLayer();
    int escapedCount = 0;

    for (int i = 0; i < pinCount; i++) {
      BgaPinDescriptor pd = pins.get(i);
      if (pd.disposition() == BgaEscapeDescriptor.PinDisposition.PASSIVE_OBSTACLE) {
        proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 999999, false));
        continue;
      }
      if (pd.targetLayer() == pinLayer
          && pd.disposition() == BgaEscapeDescriptor.PinDisposition.SIGNAL_ESCAPE) {
        proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 0, true));
        escapedCount++;
        continue;
      }
      int off = i * BgaEscapeGrid.SOLUTION_STRIDE;
      int status = solutions[off + 8];

      if (status == 2 || status == 1) { // Committed or resolved
        int tgx = solutions[off + 3];
        int tgy = solutions[off + 4];
        int tl = solutions[off + 5];
        int cost = solutions[off + 6];

        int viaBx = origX + tgx * step;
        int viaBy = origY + tgy * step;

        proposals.add(new PinEscapeProposal(pd, new IntPoint(viaBx, viaBy), tl, cost, true));
        escapedCount++;
      } else {
        proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 999999, false));
      }
    }

    String kernelCompletionMsg =
        "GPU BGA escape kernel execution complete: "
            + escapedCount
            + "/"
            + pinCount
            + " pins escaped on "
            + deviceName;
    FRLogger.info(kernelCompletionMsg);
    if (infoLogger != null) {
      infoLogger.accept(kernelCompletionMsg);
    }

    return proposals;
  }

  private static void setKernelArgMem(
      PanamaOpenClBindings bindings,
      MemorySegment kernel,
      int argIdx,
      MemorySegment memObj,
      MemorySegment scratchAddr)
      throws Throwable {
    scratchAddr.set(ValueLayout.ADDRESS, 0, memObj);
    bindings.clSetKernelArg(kernel, argIdx, ValueLayout.ADDRESS.byteSize(), scratchAddr);
  }

  private static void setKernelArgInt(
      PanamaOpenClBindings bindings,
      MemorySegment kernel,
      int argIdx,
      int val,
      MemorySegment scratchInt)
      throws Throwable {
    scratchInt.set(ValueLayout.JAVA_INT, 0, val);
    bindings.clSetKernelArg(kernel, argIdx, ValueLayout.JAVA_INT.byteSize(), scratchInt);
  }

  /**
   * Deterministic two-phase ring-staged wavefront solver (CPU fallback).
   *
   * <p>Runs congruent 4-quadrant interstitial dogbone evaluations and concentric ring ordering as
   * the OpenCL C kernels. Note: Unlike the GPU OpenCL pipeline which rasterizes obstacles into a 3D
   * occupancy grid on device, the CPU simulation evaluates candidate sites geometrically using
   * quantized local cell grids, delegating fine-grained obstacle clearance verification to {@link
   * BgaEscapeCommitter#commit}.
   */
  public static List<PinEscapeProposal> executeDeterministicWavefront(
      RoutingBoard board, BgaEscapeDescriptor bga, MarshaledBgaData data) {

    int pinCount = data.pinCount();
    List<BgaPinDescriptor> pins = bga.pins();
    int maxRing = bga.maxRing();

    int pitch = (int) Math.round(bga.pitchBoardUnits());
    int pinLayer = pins.isEmpty() ? 0 : pins.get(0).pin().firstLayer();
    int halfPitch = pitch / 2;
    double centerU = (bga.minX() + bga.maxX()) / 2.0;
    double centerV = (bga.minY() + bga.maxY()) / 2.0;

    List<PinEscapeProposal> proposals = new ArrayList<>(pinCount);

    // Track occupied dog-bone target sites (cell index -> winning candidate)
    java.util.Map<Long, Integer> cellWinners = new java.util.HashMap<>();

    // Process concentric rings from outer perimeter (ring 0) inward (ring maxRing)
    for (int r = 0; r <= maxRing; r++) {
      for (int i = 0; i < pinCount; i++) {
        BgaPinDescriptor pd = pins.get(i);
        if (pd.ring() != r
            || pd.disposition() == BgaEscapeDescriptor.PinDisposition.PASSIVE_OBSTACLE) {
          continue;
        }

        // If targetLayer is pinLayer, this pin escapes directly on surface with NO via
        if (pd.targetLayer() == pinLayer
            && pd.disposition() == BgaEscapeDescriptor.PinDisposition.SIGNAL_ESCAPE) {
          proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 0, true));
          continue;
        }

        int pinU = (int) Math.round(pd.localU());
        int pinV = (int) Math.round(pd.localV());

        int bestCost = 999999;
        IntPoint bestVia = null;
        long bestCell = -1;

        int qU = (pd.localU() >= centerU) ? 1 : -1;
        int qV = (pd.localV() >= centerV) ? 1 : -1;

        // 4-Quadrant 45-degree interstitial dog-bone sites: (+-p/2, +-p/2)
        int[] dX;
        int[] dY;
        if (pd.breakoutSector() == 0 || pd.breakoutSector() == 2) { // East/West: outward axis is U
          dX = new int[] {qU * halfPitch, qU * halfPitch, -qU * halfPitch, -qU * halfPitch};
          dY = new int[] {qV * halfPitch, -qV * halfPitch, qV * halfPitch, -qV * halfPitch};
        } else { // North/South: outward axis is V
          dX = new int[] {qU * halfPitch, -qU * halfPitch, qU * halfPitch, -qU * halfPitch};
          dY = new int[] {qV * halfPitch, qV * halfPitch, -qV * halfPitch, -qV * halfPitch};
        }
        int[] siteCosts = {1000, 1500, 1500, 2500};

        for (int s = 0; s < 4; s++) {
          int vx = pinU + dX[s];
          int vy = pinV + dY[s];
          int scost = siteCosts[s];

          // PLANE_DROP sharing incentive
          if (pd.disposition() == BgaEscapeDescriptor.PinDisposition.PLANE_DROP) {
            scost /= 2;
          }

          // Quantized cell hash in local coordinate frame
          int cellStep = Math.max(1, data.gridStepBoardUnits() * 2);
          long cellKey = (((long) (vx / cellStep)) << 32) | ((long) (vy / cellStep) & 0xFFFFFFFFL);

          if (!cellWinners.containsKey(cellKey)) {
            if (scost < bestCost) {
              bestCost = scost;
              bestVia = new IntPoint(vx, vy);
              bestCell = cellKey;
            }
          }
        }

        if (bestVia != null && bestCell != -1) {
          cellWinners.put(bestCell, i);
          proposals.add(new PinEscapeProposal(pd, bestVia, pd.targetLayer(), bestCost, true));
        } else {
          proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 999999, false));
        }
      }
    }

    return proposals;
  }
}
