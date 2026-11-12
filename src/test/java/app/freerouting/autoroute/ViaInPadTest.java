package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.path.FoundConnectionInserter;
import app.freerouting.autoroute.path.PlannedConnection;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ViaInfo;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for via-in-pad auto-detection and placement permissions (FR-103). */
class ViaInPadTest {

  private static final String DSN =
      """
      (pcb viainpad (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 7000 6000)) (via V) (rule (width 150) (clearance 150)))
        (placement
          (component CA (place A 5000 5000 front 0)) (component CB (place B 5095 4751 front 0))
          (component CL (place L 2300 4950 front 0)) (component CR (place R 6200 4950 front 0))
          (component CT (place T 3500 3750 front 0)) (component CU (place U 3500 3775 front 0)))
        (library
          (image CA (pin PA 1 0 0)) (image CB (pin PB 1 0 0)) (image CL (pin PL 1 0 0)) (image CR (pin PR 1 0 0))
          (image CT (pin PT 1 0 0)) (image CU (pin PU 1 0 0))
          (padstack PA (shape (rect top -200 -300 200 300)) (attach off))
          (padstack PB (shape (rect bottom -300 -200 300 200)) (attach off))
          (padstack PL (shape (rect top -2000 -650 2000 650)) (attach off))
          (padstack PR (shape (rect top -500 -650 500 650)) (attach off))
          (padstack PT (shape (rect top -3200 -250 3200 250)) (attach off))
          (padstack PU (shape (rect bottom -3200 -275 3200 275)) (attach off))
          (padstack V (shape (circle top 700)) (shape (circle bottom 700)) (attach off)))
        (network (net N (pins A-1 B-1)) (net G1 (pins L-1)) (net G2 (pins R-1)) (net G3 (pins T-1)) (net G4 (pins U-1))))
      """;

  private static RoutingBoard readBoard(String dsn) {
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void settingIsOffByDefaultAndControlsViaAttachAllowed() {
    RoutingBoard board = readBoard(DSN);
    RouterSettings settings = new DefaultSettings().getSettings();
    assertFalse(Boolean.TRUE.equals(settings.autorouter.viaInPad));

    AutorouteControl controlOff = new AutorouteControl(board, 1, settings);
    assertTrue(controlOff.attachSmdAllowed); // forced for net with SMD pin
    assertFalse(controlOff.viaInPad);
    ViaInfo viaInfo = controlOff.viaRule.getVia(0);
    assertFalse(viaInfo.attachSmdAllowed());
    assertFalse(controlOff.viaAttachAllowed(viaInfo));

    // Turn via_in_pad on
    settings.autorouter.viaInPad = true;
    AutorouteControl controlOn = new AutorouteControl(board, 1, settings);
    assertTrue(controlOn.viaInPad);
    assertTrue(controlOn.viaAttachAllowed(viaInfo));
  }

  @Test
  void centreOfGravityCandidateCanBeExplicitlyControlled() {
    RoutingBoard board = readBoard(DSN);
    RouterSettings settings = new DefaultSettings().getSettings();

    // Default: viaInPad=false => centreOfGravityViaCandidate=true
    AutorouteControl c1 = new AutorouteControl(board, 1, settings);
    assertTrue(c1.centreOfGravityViaCandidate);

    // viaInPad=true => centreOfGravityViaCandidate=false by default
    settings.autorouter.viaInPad = true;
    AutorouteControl c2 = new AutorouteControl(board, 1, settings);
    assertFalse(c2.centreOfGravityViaCandidate);

    // Explicit override
    settings.autorouter.viaCentreOfGravity = true;
    AutorouteControl c3 = new AutorouteControl(board, 1, settings);
    assertTrue(c3.centreOfGravityViaCandidate);

    settings.autorouter.viaCentreOfGravity = false;
    AutorouteControl c4 = new AutorouteControl(board, 1, settings);
    assertFalse(c4.centreOfGravityViaCandidate);
  }

  @Test
  void holeInSmdPadDetectsPadOverlap() {
    RoutingBoard board = readBoard(DSN);
    ViaInfo viaInfo = board.rules.viaInfos.get(0);
    Point onPadA = new IntPoint(5000, 5000);
    Point offPad = new IntPoint(3000, 3000);

    assertTrue(ForcedViaInserter.holeInSmdPad(board, viaInfo.getPadstack(), onPadA, new int[] {1}));
    assertFalse(
        ForcedViaInserter.holeInSmdPad(board, viaInfo.getPadstack(), offPad, new int[] {1}));
    // Wrong net number does not match
    assertFalse(
        ForcedViaInserter.holeInSmdPad(board, viaInfo.getPadstack(), onPadA, new int[] {2}));
  }

  @Test
  void forcedViaCheckAndInsertWithViaInPadPermission() {
    RoutingBoard board = readBoard(DSN);
    ViaInfo viaInfo = board.rules.viaInfos.get(0);
    Point padA = new IntPoint(5000, 5000);

    // Without attach permission, check fails because pad A is an obstacle
    assertFalse(
        ForcedViaInserter.check(
            viaInfo,
            padA,
            new int[] {1},
            3,
            0,
            board,
            null,
            BoardRules.defaultClearanceClass(),
            false));

    // With attach permission, check succeeds
    assertTrue(
        ForcedViaInserter.check(
            viaInfo,
            padA,
            new int[] {1},
            3,
            0,
            board,
            null,
            BoardRules.defaultClearanceClass(),
            true));

    // Insert with attach permission
    assertTrue(
        ForcedViaInserter.insert(
            viaInfo,
            padA,
            new int[] {1},
            BoardRules.defaultClearanceClass(),
            new int[] {0, 0},
            3,
            0,
            board,
            true));

    assertEquals(1, board.getVias().size());
    Via via = board.getVias().iterator().next();
    assertTrue(via.attachAllowed);
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  private static final String DSN_FOUND_VIA =
      """
      (pcb foundvia (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 7000 6000)) (via V) (rule (width 150) (clearance 150)))
        (placement
          (component CA (place A 5000 5000 front 0)) (component CB (place B 2000 2000 front 0)))
        (library
          (image CA (pin PA 1 0 0)) (image CB (pin PB 1 0 0))
          (padstack PA (shape (rect top -200 -300 200 300)) (attach off))
          (padstack PB (shape (rect bottom -300 -200 300 200)) (attach off))
          (padstack V (shape (circle top 700)) (shape (circle bottom 700)) (attach off)))
        (network (net N (pins A-1 B-1))))
      """;

  /**
   * A found connection from the bottom pad B to the top pad A whose via lands in pad A (via_in_pad)
   * or overlaps it with the hole outside (via_touching_pad). The via rule itself does not allow SMD
   * attachment, so the insert has to use the same permission as the maze search.
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void foundConnectionViaAttachesToOwnNetSmdPad(boolean viaInPad) {
    RoutingBoard board = readBoard(DSN_FOUND_VIA);
    board.rules.setViaInPad(viaInPad, !viaInPad);
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.autorouter.viaInPad = viaInPad;
    settings.autorouter.viaTouchingPad = !viaInPad;
    AutorouteControl control = new AutorouteControl(board, 1, settings);
    assertTrue(control.attachSmdAllowed);
    assertFalse(control.viaRule.getVia(0).attachSmdAllowed());
    Pin padA = pinAt(board, new IntPoint(5000, 5000));
    Pin padB = pinAt(board, new IntPoint(2000, 2000));
    IntPoint viaLocation = viaInPad ? new IntPoint(5000, 5000) : new IntPoint(5400, 5000);
    board.startMarkingChangedArea();

    // Segments run from the target to the start; the layer change between them is the via.
    assertNotNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            padA,
            padB,
            0,
            1,
            new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(
                  new IntPoint[] {new IntPoint(2000, 2000), new IntPoint(2000, 5000), viaLocation},
                  1),
              new PlannedConnection.Segment(
                  viaInPad
                      ? new IntPoint[] {viaLocation}
                      : new IntPoint[] {viaLocation, new IntPoint(5000, 5000)},
                  0)
            }));

    assertEquals(1, board.getVias().size());
    assertTrue(board.getVias().iterator().next().attachAllowed);
    assertTrue(padA.getConnectedSet(1).contains(padB));
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  private static Pin pinAt(RoutingBoard board, Point center) {
    return board.getPins().stream()
        .filter(pin -> pin.getCenter().equals(center))
        .findFirst()
        .orElseThrow();
  }

  private static final String DSN_WITH_VIA_IN_PAD =
      """
      (pcb viainpad (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 7000 6000)) (via V) (rule (width 150) (clearance 150)))
        (placement
          (component CA (place A 5000 5000 front 0)) (component CB (place B 5095 4751 front 0)))
        (library
          (image CA (pin PA 1 0 0)) (image CB (pin PB 1 0 0))
          (padstack PA (shape (rect top -200 -300 200 300)) (attach off))
          (padstack PB (shape (rect bottom -300 -200 300 200)) (attach off))
          (padstack V (shape (circle top 700)) (shape (circle bottom 700)) (attach off)))
        (network (net N (pins A-1 B-1)))
        (wiring (via V 5000 5000 (net N))))
      """;

  private static final String DSN_WITH_VIA_TOUCHING_PAD =
      """
      (pcb viatouchingpad (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 7000 6000)) (via V) (rule (width 150) (clearance 150)))
        (placement
          (component CA (place A 5000 5000 front 0)) (component CB (place B 2000 2000 bottom 0)))
        (library
          (image CA (pin PA 1 0 0)) (image CB (pin PB 1 0 0))
          (padstack PA (shape (rect top -200 -300 200 300)) (attach off))
          (padstack PB (shape (rect bottom -300 -200 300 200)) (attach off))
          (padstack V (shape (circle top 700)) (shape (circle bottom 700)) (attach off)))
        (network (net N (pins A-1 B-1)))
        (wiring (via V 5400 5000 (net N))))
      """;

  private static app.freerouting.management.HeadlessBoardManager load(
      app.freerouting.core.RoutingJob job) {
    return load(job, DSN_WITH_VIA_IN_PAD);
  }

  private static app.freerouting.management.HeadlessBoardManager load(
      app.freerouting.core.RoutingJob job, String dsnContent) {
    app.freerouting.management.HeadlessBoardManager manager =
        new app.freerouting.management.HeadlessBoardManager(job);
    ByteArrayInputStream stream =
        new ByteArrayInputStream(dsnContent.getBytes(StandardCharsets.UTF_8));
    manager.loadFromSpecctraDsn(stream, null, null);
    return manager;
  }

  @Test
  void autoDetectionFromDsnEnablesViaInPad() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    load(job);
    assertTrue(job.routerSettings.autorouter.viaInPad);
    assertTrue(job.routerSettings.autorouter.viaTouchingPad);
  }

  @Test
  void autoDetectionPreservesExplicitViaTouchingPadFalse() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    job.routerSettings.autorouter.viaTouchingPad = false;
    RoutingBoard board = load(job).getRoutingBoard();
    assertFalse(job.routerSettings.autorouter.viaTouchingPad);
    assertFalse(job.routerSettings.autorouter.viaInPad);
    assertFalse(board.rules.isViaTouchingPad());
    assertFalse(board.rules.isViaInPad());
    assertFalse(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void viaInPadAllowedBySettingsIsNotAClearanceViolation() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    RoutingBoard board = load(job).getRoutingBoard();
    assertTrue(board.rules.isViaInPad());
    assertFalse(board.getVias().iterator().next().attachAllowed);
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void viaInPadRefusedBySettingsStaysAClearanceViolation() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    job.routerSettings.autorouter.viaInPad = false;
    job.routerSettings.autorouter.viaTouchingPad = false;
    RoutingBoard board = load(job).getRoutingBoard();
    assertFalse(board.rules.isViaInPad());
    assertFalse(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void viaTouchingPadAllowedWhenHoleIsOutsidePad() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    job.routerSettings.autorouter.viaInPad = false;
    job.routerSettings.autorouter.viaTouchingPad = true;
    RoutingBoard board = load(job, DSN_WITH_VIA_TOUCHING_PAD).getRoutingBoard();
    assertFalse(board.rules.isViaInPad());
    assertTrue(board.rules.isViaTouchingPad());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void viaTouchingPadRefusesViaWhenHoleIsInsidePad() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    job.routerSettings.autorouter.viaInPad = false;
    job.routerSettings.autorouter.viaTouchingPad = true;
    RoutingBoard board = load(job, DSN_WITH_VIA_IN_PAD).getRoutingBoard();
    assertFalse(board.rules.isViaInPad());
    assertTrue(board.rules.isViaTouchingPad());
    assertFalse(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }
}
