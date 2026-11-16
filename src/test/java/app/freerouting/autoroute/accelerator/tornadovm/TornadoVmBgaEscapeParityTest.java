package app.freerouting.autoroute.accelerator.tornadovm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import app.freerouting.autoroute.accelerator.AcceleratorRegistry;
import app.freerouting.autoroute.accelerator.BgaEscapeGrid;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor;
import app.freerouting.autoroute.fanout.BgaFootprintDetector;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs the BGA escape kernels on a TornadoVM device and checks the result bit for bit against the
 * plain Java path, and against the OpenCL backend when an OpenCL device is present.
 *
 * <p>Skipped unless TornadoVM and a device are available, for example with {@code ./gradlew test
 * -Ptornadovm=opencl,cuda --tests TornadoVmBgaEscapeParityTest}.
 */
class TornadoVmBgaEscapeParityTest {

  static {
    // The backend only enables the TornadoVM backend named in the preference. This test compares
    // all of them in one JVM, so it enables every one before TornadoVM starts.
    if (System.getProperty(TornadoVmComputeBackend.ENABLED_BACKENDS_PROPERTY) == null) {
      System.setProperty(TornadoVmComputeBackend.ENABLED_BACKENDS_PROPERTY, "opencl,cuda,metal");
    }
  }

  @AfterEach
  void resetRegistry() {
    AcceleratorRegistry.reset();
  }

  private static RouterSettings settings(String devicePreference) {
    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuAccelerationEnabled = true;
    settings.fanout.gpuMinBgaPins = 64;
    settings.fanout.gpuDevicePreference = devicePreference;
    return settings;
  }

  /** Every TornadoVM backend that has a device here, skipping the test if there is none. */
  private static List<String> tornadoVmPreferencesOrSkip() {
    List<String> available = new ArrayList<>();
    for (String preference : List.of("tornadovm:opencl", "tornadovm:cuda", "tornadovm:metal")) {
      AcceleratorRegistry.reset();
      if (AcceleratorRegistry.discover(settings(preference).fanout)
          instanceof TornadoVmComputeBackend) {
        available.add(preference);
      }
    }
    AcceleratorRegistry.reset();
    assumeFalse(available.isEmpty(), "no TornadoVM device available");
    return available;
  }

  private static TornadoVmComputeBackend backend(String preference) {
    AcceleratorRegistry.reset();
    return (TornadoVmComputeBackend) AcceleratorRegistry.discover(settings(preference).fanout);
  }

  /** A square ball grid with rings, mixed pin dispositions and random obstacles. */
  private static BgaEscapeGrid syntheticGrid(int ballsPerSide, int obstacleCount, long seed) {
    int pitch = 8000;
    int step = 1000;
    int margin = 45000;
    int layers = 6;
    Random random = new Random(seed);
    int pinCount = ballsPerSide * ballsPerSide;
    int[] pins = new int[pinCount * BgaEscapeKernels.PIN_STRIDE];
    int half = (ballsPerSide - 1) * pitch / 2;
    for (int row = 0; row < ballsPerSide; row++) {
      for (int col = 0; col < ballsPerSide; col++) {
        int o = (row * ballsPerSide + col) * BgaEscapeKernels.PIN_STRIDE;
        int u = col * pitch - half;
        int v = row * pitch - half;
        int halfWidth = pitch / 4 + 708;
        int ring =
            Math.min(Math.min(row, col), Math.min(ballsPerSide - 1 - row, ballsPerSide - 1 - col));
        pins[o] = u;
        pins[o + 1] = v;
        pins[o + 2] = u - halfWidth;
        pins[o + 3] = u + halfWidth;
        pins[o + 4] = v - halfWidth;
        pins[o + 5] = v + halfWidth;
        pins[o + 6] = 1 + ring % (layers - 1);
        pins[o + 8] = u >= 0 ? 1 : -1;
        pins[o + 9] = v >= 0 ? 1 : -1;
        pins[o + 10] = ring;
        pins[o + 11] = random.nextInt(10) == 0 ? random.nextInt(2) : 2;
      }
    }
    int origin = -half - margin;
    int cells = (2 * half + 2 * margin) / step + 1;
    int[] obstacles = new int[obstacleCount * BgaEscapeKernels.OBSTACLE_STRIDE];
    for (int k = 0; k < obstacleCount; k++) {
      int cx = origin + random.nextInt(cells * step);
      int cy = origin + random.nextInt(cells * step);
      int size = 300 + random.nextInt(2000);
      int o = k * BgaEscapeKernels.OBSTACLE_STRIDE;
      obstacles[o] = cx - size;
      obstacles[o + 1] = cx + size;
      obstacles[o + 2] = cy - size;
      obstacles[o + 3] = cy + size;
      obstacles[o + 4] = (cx - size) - (cy + size);
      obstacles[o + 5] = (cx + size) - (cy - size);
      obstacles[o + 6] = (cx - size) + (cy - size);
      obstacles[o + 7] = (cx + size) + (cy + size);
      obstacles[o + 8] = random.nextInt(layers + 1) - 1;
    }
    return new BgaEscapeGrid(
        pins,
        pinCount,
        obstacles,
        obstacleCount,
        cells,
        cells,
        layers,
        step,
        origin,
        origin,
        pitch,
        (ballsPerSide - 1) / 2);
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 20, 25})
  void deviceMatchesPlainJava(int ballsPerSide) {
    List<String> preferences = tornadoVmPreferencesOrSkip();
    BgaEscapeGrid grid = syntheticGrid(ballsPerSide, ballsPerSide * 100, 42);
    int[] expected = BgaEscapeRunner.runOnCpu(grid);

    for (String preference : preferences) {
      TornadoVmComputeBackend backend = backend(preference);
      // Twice, so that a second run in the same process is covered as well.
      assertArrayEquals(expected, backend.runBgaEscape(grid), preference);
      assertArrayEquals(expected, backend.runBgaEscape(grid), preference);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "BGA_Escape_Test_HARD.dsn",
        "BGA_Escape_Test_HARD_v2.dsn",
        "PocketBone-BGA400.dsn"
      })
  void fixtureProposalsMatchOpenCl(String fixture) throws Exception {
    List<String> preferences = tornadoVmPreferencesOrSkip();
    RoutingBoard board = DsnTestFixtures.loadBoard(fixture);
    RouterSettings openClSettings = settings("auto");
    openClSettings.setLayerCount(board.getLayerCount());
    List<BgaEscapeDescriptor> bgas = BgaFootprintDetector.detect(board, openClSettings, null);
    assertFalse(bgas.isEmpty(), "fixture must contain a BGA");

    // The OpenCL backend is the reference when an OpenCL device is present; otherwise the
    // TornadoVM backends are compared with each other.
    AcceleratorRegistry.reset();
    boolean openClAvailable = AcceleratorRegistry.discover(openClSettings.fanout).isAvailable();

    for (BgaEscapeDescriptor bga : bgas) {
      List<PinEscapeProposal> reference = null;
      if (openClAvailable) {
        AcceleratorRegistry.reset();
        reference = GpuBgaEscapeSolver.solve(board, bga, openClSettings);
      }
      // The CPU fallback runs the same kernels and must give the same proposals.
      List<PinEscapeProposal> cpu = GpuBgaEscapeSolver.solveOnCpu(board, bga, openClSettings, null);
      if (reference == null) {
        reference = cpu;
      } else {
        assertSameProposals(cpu, reference, "cpu");
      }
      for (String preference : preferences) {
        AcceleratorRegistry.reset();
        RouterSettings tornadoSettings = settings(preference);
        tornadoSettings.setLayerCount(board.getLayerCount());
        List<PinEscapeProposal> proposals = GpuBgaEscapeSolver.solve(board, bga, tornadoSettings);
        assertFalse(proposals.isEmpty(), preference);
        if (reference == null) {
          reference = proposals;
        } else {
          assertSameProposals(proposals, reference, preference);
        }
      }
    }
  }

  private static void assertSameProposals(
      List<PinEscapeProposal> actual, List<PinEscapeProposal> expected, String backend) {
    assertEquals(expected.size(), actual.size(), backend);
    for (int i = 0; i < expected.size(); i++) {
      PinEscapeProposal e = expected.get(i);
      PinEscapeProposal a = actual.get(i);
      String pin = backend + ", pin " + i;
      assertEquals(e.escaped(), a.escaped(), pin);
      assertEquals(e.targetLayer(), a.targetLayer(), pin);
      assertEquals(e.cost(), a.cost(), pin);
      assertEquals(e.viaCenter() == null, a.viaCenter() == null, pin);
      if (e.viaCenter() != null) {
        assertEquals(e.viaCenter().x, a.viaCenter().x, pin);
        assertEquals(e.viaCenter().y, a.viaCenter().y, pin);
      }
    }
  }
}
