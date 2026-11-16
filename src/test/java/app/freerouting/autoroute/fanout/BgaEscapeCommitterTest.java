package app.freerouting.autoroute.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.fanout.BgaBufferMarshaller.MarshaledBgaData;
import app.freerouting.autoroute.fanout.BgaEscapeCommitter.CommitResult;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BgaEscapeCommitterTest {

  private static RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication =
        new Communication(
            Unit.UM,
            1,
            new Communication.SpecctraParserInfo("\"", null, null, null, null, false),
            new CoordinateTransform(1, 0, 0),
            new ItemIdGenerator(),
            new BoardObserverAdaptor());

    RoutingBoard board =
        new RoutingBoard(
            new IntBox(0, 0, 100_000, 100_000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 100_000, 100_000)},
            0,
            boardRules,
            communication);

    board.library.padstacks = new app.freerouting.core.library.Padstacks(layerStructure);
    board.library.packages = new Packages(board.library.padstacks);
    board.rules.nets.add("SIG", 1, false);

    ConvexShape[] viaShapes =
        new ConvexShape[] {new Circle(Point.ZERO, 100), new Circle(Point.ZERO, 100)};
    Padstack viaPadstack = board.library.padstacks.add("DefaultViaPad", viaShapes, true, false);
    ViaInfo viaInfo = new ViaInfo("DefaultVia", viaPadstack, 0, false, boardRules);
    boardRules.viaInfos.add(viaInfo);
    ViaRule viaRule = new ViaRule("DefaultViaRule");
    viaRule.appendVia(viaInfo);
    boardRules.viaRules.add(viaRule);
    boardRules.getDefaultNetClass().setViaRule(viaRule);
    boardRules.getDefaultNetClass().setTraceHalfWidth(50);

    return board;
  }

  @Test
  void testAuthoritativeContinuousDrcCommitWithZeroViolations() {
    RoutingBoard board = createTestBoard();

    // 8x8 = 64 pin BGA on 1000 um pitch
    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    List<PinEscapeProposal> proposals;
    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, desc, settings)) {
      proposals = GpuBgaEscapeSolver.executeDeterministicWavefront(board, desc, data);
    }
    assertNotNull(proposals);

    // Commit proposals with authoritative host DRC validation
    CommitResult result = BgaEscapeCommitter.commit(board, desc, proposals);
    assertTrue(result.pinsCommitted() > 0, "At least some pins must be successfully committed");
    assertEquals(result.pinsCommitted(), result.viasInserted());
    assertEquals(result.pinsCommitted(), result.tracesInserted());

    // Authoritative full design rules check: ZERO violations allowed
    List<?> violations =
        new ArrayList<>(new DesignRulesChecker(board, null).getAllClearanceViolations());
    assertEquals(0, violations.size(), "Committed escapes must have 0 DRC clearance violations");
  }

  @Test
  void testFallbackCandidateChosenWhenPrimaryViaObstructed() {
    RoutingBoard board = createTestBoard();

    // 8x8 = 64 pin BGA on 1000 um pitch
    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    // Place an obstacle passive pad at the primary via site of an inner pin (P18: r=2, c=2)
    // P18 is at (48500, 48500). Primary via site is (48500 - 500, 48500 - 500) = (48000, 48000)
    Package.Pin obstPin = new Package.Pin("C1_1", pad.id, FloatPoint.ZERO, 0);
    Package obstPkg = board.library.packages.add(new Package.Pin[] {obstPin});
    Component obstComp =
        board.components.addPrecise(
            "C1", new FloatPoint(48000, 48000), 0.0, true, obstPkg, obstPkg, false, null);
    board.insertPin(obstComp.id, 0, new int[] {99}, 1, FixedState.USER_FIXED);

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    List<PinEscapeProposal> proposals;
    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, desc, settings)) {
      proposals = GpuBgaEscapeSolver.executeDeterministicWavefront(board, desc, data);
    }
    assertNotNull(proposals);

    CommitResult result = BgaEscapeCommitter.commit(board, desc, proposals);
    assertTrue(result.pinsCommitted() > 0);

    // Verify ZERO DRC clearance violations with obstacle and alternate candidate
    List<?> violations =
        new ArrayList<>(new DesignRulesChecker(board, null).getAllClearanceViolations());
    assertEquals(0, violations.size(), "Committed escapes must have 0 DRC clearance violations");
  }

  @Test
  void testContinuousOkLengthTruncationWhenObstacleInHalo() {
    RoutingBoard board = createTestBoard();

    // 8x8 = 64 pin BGA on 1000 um pitch
    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    // Place an obstacle passive in the halo corridor outside the East perimeter
    // BGA maxX is 53500. Standard exit is 53500 + 1500 = 55000.
    // Place obstacle at X = 54400, Y = 50500 (in the path of P39)
    Package.Pin obstPin = new Package.Pin("R1_1", pad.id, FloatPoint.ZERO, 0);
    Package obstPkg = board.library.packages.add(new Package.Pin[] {obstPin});
    Component obstComp =
        board.components.addPrecise(
            "R1", new FloatPoint(54400, 50500), 0.0, true, obstPkg, obstPkg, false, null);
    board.insertPin(obstComp.id, 0, new int[] {99}, 1, FixedState.USER_FIXED);

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    List<PinEscapeProposal> proposals;
    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, desc, settings)) {
      proposals = GpuBgaEscapeSolver.executeDeterministicWavefront(board, desc, data);
    }
    assertNotNull(proposals);

    CommitResult result = BgaEscapeCommitter.commit(board, desc, proposals);
    assertTrue(result.pinsCommitted() > 0);
    assertEquals(result.pinsCommitted(), result.committedPins().size());

    // Verify ZERO DRC violations (continuous okLength snapped cleanly before R1)
    List<?> violations =
        new ArrayList<>(new DesignRulesChecker(board, null).getAllClearanceViolations());
    assertEquals(0, violations.size(), "Committed escapes must have 0 DRC clearance violations");
  }

  @Test
  void testCommittedPinsBypassedByBatchFanout() {
    RoutingBoard board = createTestBoard();

    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    for (int i = 0; i < 64; i++) {
      board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
    }

    RouterSettings settings = new RouterSettings();
    settings.setFanoutEnabled(true);
    settings.fanout.gpuMinBgaPins = 64;
    settings.fanout.gpuAccelerationEnabled = true;

    int viasBefore = board.getViaCount();
    int tracesBefore = board.getTraces().size();

    // Run BatchFanout pipeline
    app.freerouting.autoroute.pipeline.BatchFanout.FanoutRunSummary summary =
        app.freerouting.autoroute.pipeline.BatchFanout.fanoutBoard(board, settings, null);

    assertNotNull(summary);
    // BGA escape committer handles all 64 pins in pre-pass; CPU fanout has 0 remaining pins
    assertTrue(summary.completedPassCount() <= 1);
    assertTrue(board.getTraces().size() > tracesBefore);

    int tracesAfter = board.getTraces().size();
    int viasAfter = board.getViaCount();

    // Re-running BatchFanout must find 0 pins to fanout and make 0 modifications
    app.freerouting.autoroute.pipeline.BatchFanout.FanoutRunSummary summary2 =
        app.freerouting.autoroute.pipeline.BatchFanout.fanoutBoard(board, settings, null);
    assertNotNull(summary2);
    assertEquals(tracesAfter, board.getTraces().size(), "No duplicate traces should be created");
    assertEquals(viasAfter, board.getViaCount(), "No duplicate vias should be created");
  }

  @Test
  void testStrictViaInPadRejectionOnBallAndSameNetPassive() {
    RoutingBoard board = createTestBoard();
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    Package.Pin bgaPin = new Package.Pin("BGA_1", pad.id, FloatPoint.ZERO, 0);
    Package bgaPkg = board.library.packages.add(new Package.Pin[] {bgaPin});
    Component bgaComp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, bgaPkg, bgaPkg, false, null);
    Pin pin = board.insertPin(bgaComp.id, 0, new int[] {1}, 1, FixedState.UNFIXED);

    // Place a same-net decoupling capacitor pad on layer 1 (Bottom layer) at (50500, 50500)
    Padstack bottomPad =
        board.library.padstacks.add(new ConvexShape[] {null, new Circle(Point.ZERO, 150)});
    Package.Pin capPin = new Package.Pin("C1_1", bottomPad.id, FloatPoint.ZERO, 0);
    Package capPkg = board.library.packages.add(new Package.Pin[] {capPin});
    Component capComp =
        board.components.addPrecise(
            "C1", new FloatPoint(50500, 50500), 0.0, true, capPkg, capPkg, false, null);
    board.insertPin(capComp.id, 0, new int[] {1}, 1, FixedState.USER_FIXED);

    Padstack viaPadstack = board.rules.viaInfos.get(0).getPadstack();

    // 1. Via positioned directly over the BGA ball pad itself (50000, 50000) -> must be rejected
    boolean clearOverBall =
        BgaEscapeCommitter.checkViaClearance(
            board, viaPadstack, new IntPoint(50000, 50000), pin, 1, new int[] {1}, 0);
    assertFalse(clearOverBall, "Via directly on ball pad must be rejected as via-in-pad");

    // 2. Via directly over same-net passive pad on Bottom layer (50500, 50500) -> must be rejected
    boolean clearOverCap =
        BgaEscapeCommitter.checkViaClearance(
            board, viaPadstack, new IntPoint(50500, 50500), pin, 1, new int[] {1}, 0);
    assertFalse(clearOverCap, "Via on same-net decoupling cap must be rejected as via-in-pad");

    // 3. Via placed in interstitial site with clear spacing (52000, 52000) -> must be accepted
    boolean clearInterstitial =
        BgaEscapeCommitter.checkViaClearance(
            board, viaPadstack, new IntPoint(52000, 52000), pin, 1, new int[] {1}, 0);
    assertTrue(clearInterstitial, "Via in clear interstitial location should be accepted");
  }

  @Test
  void testBgaWithNoConnectPinsSkippedWhenRoutablePinsEscaped() {
    RoutingBoard board = createTestBoard();
    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    // Make first 4 pins No-Connect (netCount == 0), remaining 60 pins routable
    for (int i = 0; i < 4; i++) {
      board.insertPin(comp.id, i, new int[0], 0, FixedState.UNFIXED);
    }
    for (int i = 4; i < 64; i++) {
      board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
    }

    RouterSettings settings = new RouterSettings();
    settings.setFanoutEnabled(true);
    settings.fanout.gpuMinBgaPins = 64;
    settings.fanout.gpuAccelerationEnabled = true;

    // First detection must find U1
    List<BgaEscapeDescriptor> detected = BgaFootprintDetector.detect(board, settings);
    assertEquals(1, detected.size());

    // Fanout the board
    app.freerouting.autoroute.pipeline.BatchFanout.fanoutBoard(board, settings, null);

    // Second detection must skip U1 even though 4 NC pins have no contacts
    List<BgaEscapeDescriptor> detectedAfter = BgaFootprintDetector.detect(board, settings);
    assertEquals(
        0, detectedAfter.size(), "BGA with NC pins must be skipped once routable pins are escaped");
  }

  @Test
  void testCommitterIdempotencyAndNoDuplicates() {
    RoutingBoard board = createTestBoard();

    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    for (int i = 0; i < 64; i++) {
      board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor bga =
        BgaFootprintDetector.analyzeComponent(
            board, comp, new ArrayList<>(board.getSmdPins()), settings);
    assertNotNull(bga);

    List<PinEscapeProposal> proposals;
    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, bga, settings)) {
      proposals = GpuBgaEscapeSolver.executeDeterministicWavefront(board, bga, data);
    }
    assertFalse(proposals.isEmpty());

    // First commit: should commit pins successfully
    CommitResult res1 = BgaEscapeCommitter.commit(board, bga, proposals);
    assertTrue(res1.pinsCommitted() > 0, "First commit must commit pins");
    int traceCountAfterFirst = board.getTraces().size();
    int viaCountAfterFirst = board.getVias().size();

    // Second commit: must be idempotent and insert 0 duplicates
    CommitResult res2 = BgaEscapeCommitter.commit(board, bga, proposals);
    assertEquals(0, res2.pinsCommitted(), "Second commit must commit 0 new pins");
    assertEquals(0, res2.tracesInserted(), "Second commit must insert 0 new traces");
    assertEquals(0, res2.viasInserted(), "Second commit must insert 0 new vias");
    assertEquals(
        traceCountAfterFirst,
        board.getTraces().size(),
        "Trace count must remain identical after duplicate commit");
    assertEquals(
        viaCountAfterFirst,
        board.getVias().size(),
        "Via count must remain identical after duplicate commit");

    // Verify zero clearance violations
    List<?> violations =
        new ArrayList<>(new DesignRulesChecker(board, null).getAllClearanceViolations());
    assertEquals(0, violations.size());
  }

  @Test
  void testCommittedEscapesSurviveTailRemovalAndDanglingCopper() {
    RoutingBoard board = createTestBoard();

    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 150), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    for (int i = 0; i < 64; i++) {
      board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor bga =
        BgaFootprintDetector.analyzeComponent(
            board, comp, new ArrayList<>(board.getSmdPins()), settings);
    assertNotNull(bga);

    List<PinEscapeProposal> proposals;
    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, bga, settings)) {
      proposals = GpuBgaEscapeSolver.executeDeterministicWavefront(board, bga, data);
    }
    assertFalse(proposals.isEmpty());

    CommitResult res = BgaEscapeCommitter.commit(board, bga, proposals);
    assertTrue(res.pinsCommitted() > 0);
    int tracesCommitted = board.getTraces().size();
    int viasCommitted = board.getVias().size();
    assertTrue(tracesCommitted > 0);
    assertTrue(viasCommitted > 0);

    // Execute aggressive tail removal (NONE stops at nothing)
    board.removeTraceTails(-1, app.freerouting.board.model.items.Item.StopConnectionOption.NONE);
    assertEquals(
        tracesCommitted,
        board.getTraces().size(),
        "Committed BGA escape traces must survive removeTraceTails(NONE)");
    assertEquals(
        viasCommitted,
        board.getVias().size(),
        "Committed BGA escape vias must survive removeTraceTails(NONE)");

    // Execute DanglingCopper cleanup
    app.freerouting.autoroute.pipeline.DanglingCopper.remove(board, "after the last stage");
    assertEquals(
        tracesCommitted,
        board.getTraces().size(),
        "Committed BGA escape traces must survive DanglingCopper.remove()");
    assertEquals(
        viasCommitted,
        board.getVias().size(),
        "Committed BGA escape vias must survive DanglingCopper.remove()");
  }
}
