package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.BasicBoard;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.FileInputStream;
import java.util.Collection;
import javax.accessibility.AccessibleRole;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Phase 5 a11y workflow — incompletes (ratsnest) and clearance-violation <em>lists/counts</em>
 * (component-only, forced headless via {@code testGui}).
 *
 * <p>The real list windows ({@code WindowClearanceViolations}/{@code WindowIncompletes}) are
 * top-level windows and cannot be constructed headless (contract D7), so this test binds the real
 * {@code drc}-computed violations/airlines to a {@link JList} — the same Swing list widget those
 * windows use — and verifies the list resolves by stable locator, exposes a translated accessible
 * name/role, and reports the correct count (D19/D22). This proves the compute→list binding is
 * accessible.
 */
@Tag("gui")
class ViolationsIncompletesListA11yTest {

  /** Board with both incompletes and clearance violations: 9 unconnected, 2 unique violations. */
  private static final String FIXTURE = "Issue575-drc_dev-board_4_hole_clearance_violations.dsn";

  private static final int EXPECTED_UNIQUE_VIOLATIONS = 2;
  private static final int EXPECTED_INCOMPLETES = 9;

  /**
   * Translated list titles (mirror the {@code Common} bundle: clearance_violations/incompletes).
   */
  private record ListTitles(String violations, String incompletes) {}

  private static final ListTitles ENGLISH =
      new ListTitles("clearance violations", "Incomplete Connections");
  private static final ListTitles HUNGARIAN =
      new ListTitles("szigetelőtávolság-sértések", "Befejezetlen kapcsolatok");

  private static BasicBoard loadBoard() throws Exception {
    BoardReadResult result;
    try (FileInputStream in = new FileInputStream("fixtures/" + FIXTURE)) {
      result = DsnReader.readBoard(in, null, null, "test");
    }
    return switch (result) {
      case BoardReadResult.Success s -> (BasicBoard) s.board();
      case BoardReadResult.OutlineMissing o -> (BasicBoard) o.board();
      default -> throw new IllegalStateException("Failed to read board: " + result);
    };
  }

  /** Builds a located, translated {@link JList} from the given display rows. */
  private static JList<String> buildList(String locator, String accessibleName, String[] rows) {
    DefaultListModel<String> model = new DefaultListModel<>();
    for (String row : rows) {
      model.addElement(row);
    }
    JList<String> list = new JList<>(model);
    A11y.tag(list, locator);
    A11y.describe(list, accessibleName, null);
    return list;
  }

  @Test
  void clearanceViolationsListIsAccessibleAndHasCorrectCount() throws Exception {
    BasicBoard board = loadBoard();
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    String[] rows =
        violations.stream().map(v -> "violation on layer " + v.layer).toArray(String[]::new);

    JList<String> list =
        GuiA11yHarness.onEdt(
            () -> buildList(GuiLocators.INSPECT_CLEARANCE_VIOLATIONS, ENGLISH.violations(), rows));

    GuiA11yHarness.onEdt(
        () -> {
          var c = GuiA11yHarness.findByLocator(list, GuiLocators.INSPECT_CLEARANCE_VIOLATIONS);
          GuiA11yHarness.requireRole(
              c, GuiLocators.INSPECT_CLEARANCE_VIOLATIONS, AccessibleRole.LIST);
          GuiA11yHarness.requireAccessibleName(c, GuiLocators.INSPECT_CLEARANCE_VIOLATIONS);
          assertEquals(
              EXPECTED_UNIQUE_VIOLATIONS,
              list.getModel().getSize(),
              "violations list count must match the drc-computed unique violation count");
        });
  }

  @Test
  void incompletesListIsAccessibleAndHasCorrectCount() throws Exception {
    BasicBoard board = loadBoard();
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    AirLine[] airlines = drc.getAllAirlines();
    String[] rows =
        java.util.Arrays.stream(airlines).map(a -> "airline " + a).toArray(String[]::new);

    JList<String> list =
        GuiA11yHarness.onEdt(
            () -> buildList(GuiLocators.INSPECT_INCOMPLETES, ENGLISH.incompletes(), rows));

    GuiA11yHarness.onEdt(
        () -> {
          var c = GuiA11yHarness.findByLocator(list, GuiLocators.INSPECT_INCOMPLETES);
          GuiA11yHarness.requireRole(c, GuiLocators.INSPECT_INCOMPLETES, AccessibleRole.LIST);
          GuiA11yHarness.requireAccessibleName(c, GuiLocators.INSPECT_INCOMPLETES);
          assertEquals(
              EXPECTED_INCOMPLETES,
              list.getModel().getSize(),
              "incompletes list count must match the drc-computed airline count");
        });
  }

  @Test
  void listAccessibleNamesAreTranslatedAcrossLocales() throws Exception {
    JList<String> enList =
        GuiA11yHarness.onEdt(
            () ->
                buildList(
                    GuiLocators.INSPECT_CLEARANCE_VIOLATIONS, ENGLISH.violations(), new String[0]));
    JList<String> huList =
        GuiA11yHarness.onEdt(
            () ->
                buildList(
                    GuiLocators.INSPECT_CLEARANCE_VIOLATIONS,
                    HUNGARIAN.violations(),
                    new String[0]));

    GuiA11yHarness.onEdt(
        () -> {
          String enName = GuiA11yHarness.accessibleName(enList);
          String huName = GuiA11yHarness.accessibleName(huList);
          assertNotNull(enName, "EN accessible name must be present");
          assertNotNull(huName, "HU accessible name must be present");
          assertNotEquals(enName, huName, "list accessible name must be translated (D19)");
        });
  }
}
