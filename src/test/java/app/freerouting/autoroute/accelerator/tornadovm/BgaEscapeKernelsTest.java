package app.freerouting.autoroute.accelerator.tornadovm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import app.freerouting.autoroute.accelerator.BgaEscapeGrid;
import org.junit.jupiter.api.Test;

/**
 * Checks the BGA escape kernels through their plain Java path ({@link BgaEscapeRunner#runOnCpu}),
 * which runs the same per-item methods TornadoVM compiles for the GPU. No TornadoVM runtime or
 * device is needed.
 */
class BgaEscapeKernelsTest {

  private static final int PITCH = 800;
  private static final int STEP = 100;
  private static final int GRID = 40;
  private static final int LAYERS = 2;

  private static final int STATUS = 8;
  private static final int VIA_X = 3;
  private static final int VIA_Y = 4;
  private static final int COST = 6;

  private static final int PASSIVE = 0;
  private static final int PLANE_DROP = 1;
  private static final int SIGNAL = 2;

  /** Writes one pin record in the layout produced by {@code BgaBufferMarshaller}. */
  private static void pin(int[] pins, int index, int u, int v, int qU, int qV, int disposition) {
    int o = index * BgaEscapeKernels.PIN_STRIDE;
    int halfWidth = PITCH / 4 + 71;
    pins[o] = u;
    pins[o + 1] = v;
    pins[o + 2] = u - halfWidth;
    pins[o + 3] = u + halfWidth;
    pins[o + 4] = v - halfWidth;
    pins[o + 5] = v + halfWidth;
    pins[o + 6] = 1; // target layer
    pins[o + 7] = 0; // breakout sector
    pins[o + 8] = qU;
    pins[o + 9] = qV;
    pins[o + 10] = 0; // ring
    pins[o + 11] = disposition;
  }

  /** An obstacle on all layers covering the box [minU, maxU] x [minV, maxV]. */
  private static int[] boxObstacle(int minU, int maxU, int minV, int maxV) {
    return new int[] {
      minU, maxU, minV, maxV, minU - maxV, maxU - minV, minU + minV, maxU + maxV, -1, 0
    };
  }

  private static int[] noObstacle() {
    // The OpenCL host always passes at least one record; a far away one keeps it out of the grid.
    return boxObstacle(-100_000, -99_000, -100_000, -99_000);
  }

  private static BgaEscapeGrid grid(int[] pins, int[] obstacles) {
    return new BgaEscapeGrid(
        pins,
        pins.length / BgaEscapeKernels.PIN_STRIDE,
        obstacles,
        obstacles.length / BgaEscapeKernels.OBSTACLE_STRIDE,
        GRID,
        GRID,
        LAYERS,
        STEP,
        0,
        0,
        PITCH,
        0);
  }

  private static int field(int[] solutions, int pin, int field) {
    return solutions[pin * BgaEscapeGrid.SOLUTION_STRIDE + field];
  }

  @Test
  void singlePinTakesTheOutwardDiagonalSite() {
    int[] pins = new int[BgaEscapeKernels.PIN_STRIDE];
    pin(pins, 0, 2000, 2000, 1, 1, SIGNAL);

    int[] solutions = BgaEscapeRunner.runOnCpu(grid(pins, noObstacle()));

    assertEquals(2, field(solutions, 0, STATUS), "pin must be committed");
    assertEquals((2000 + PITCH / 2) / STEP, field(solutions, 0, VIA_X));
    assertEquals((2000 + PITCH / 2) / STEP, field(solutions, 0, VIA_Y));
  }

  @Test
  void passivePinIsNotEscaped() {
    int[] pins = new int[BgaEscapeKernels.PIN_STRIDE];
    pin(pins, 0, 2000, 2000, 1, 1, PASSIVE);

    int[] solutions = BgaEscapeRunner.runOnCpu(grid(pins, noObstacle()));

    assertEquals(0, field(solutions, 0, STATUS));
  }

  @Test
  void planeDropHalvesTheSiteCost() {
    int[] signal = new int[BgaEscapeKernels.PIN_STRIDE];
    pin(signal, 0, 2000, 2000, 1, 1, SIGNAL);
    int[] plane = new int[BgaEscapeKernels.PIN_STRIDE];
    pin(plane, 0, 2000, 2000, 1, 1, PLANE_DROP);

    int signalCost = field(BgaEscapeRunner.runOnCpu(grid(signal, noObstacle())), 0, COST);
    int planeCost = field(BgaEscapeRunner.runOnCpu(grid(plane, noObstacle())), 0, COST);

    // Same site and same congestion term; only the 1000 site cost is halved.
    assertEquals(500, signalCost - planeCost);
  }

  @Test
  void blockedSiteMovesTheViaToTheNextSite() {
    // The pin under test is pin 1: the occupancy grid tags obstacles with 1 and pin pads with
    // index + 1, so for pin 0 an obstacle cell is indistinguishable from its own pad (the same
    // holds for the OpenCL kernel).
    int[] pins = new int[2 * BgaEscapeKernels.PIN_STRIDE];
    pin(pins, 0, 3600, 600, 1, -1, PASSIVE);
    pin(pins, 1, 2000, 2000, 1, 1, SIGNAL);
    int primary = 2000 + PITCH / 2;

    int[] solutions =
        BgaEscapeRunner.runOnCpu(
            grid(pins, boxObstacle(primary - 50, primary + 50, primary - 50, primary + 50)));

    assertEquals(2, field(solutions, 1, STATUS));
    assertNotEquals(
        primary / STEP * 1000 + primary / STEP,
        field(solutions, 1, VIA_X) * 1000 + field(solutions, 1, VIA_Y),
        "via must not land on the blocked site");
  }

  @Test
  void twoPinsSharingASiteAreSeparatedByNegotiation() {
    // Pin 0 escapes up-right and pin 1 down-left; both primary sites are the same cell.
    int[] pins = new int[2 * BgaEscapeKernels.PIN_STRIDE];
    pin(pins, 0, 1000, 1000, 1, 1, SIGNAL);
    pin(pins, 1, 1800, 1800, -1, -1, SIGNAL);
    int shared = 1400 / STEP;

    int[] solutions = BgaEscapeRunner.runOnCpu(grid(pins, noObstacle()));

    assertEquals(2, field(solutions, 0, STATUS));
    assertEquals(2, field(solutions, 1, STATUS));
    // Equal cost, so the lower pin index wins the shared cell and the other pin moves.
    assertEquals(shared, field(solutions, 0, VIA_X));
    assertEquals(shared, field(solutions, 0, VIA_Y));
    assertNotEquals(
        field(solutions, 0, VIA_X) * 1000 + field(solutions, 0, VIA_Y),
        field(solutions, 1, VIA_X) * 1000 + field(solutions, 1, VIA_Y));
  }

  @Test
  void repeatedRunsGiveTheSameBuffer() {
    int[] pins = new int[2 * BgaEscapeKernels.PIN_STRIDE];
    pin(pins, 0, 1000, 1000, 1, 1, SIGNAL);
    pin(pins, 1, 1800, 1800, -1, -1, SIGNAL);
    BgaEscapeGrid grid = grid(pins, noObstacle());

    assertArrayEquals(BgaEscapeRunner.runOnCpu(grid), BgaEscapeRunner.runOnCpu(grid));
  }
}
