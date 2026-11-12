package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.GlobalSettings;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rounded-rectangle pads exported by KiCad 10.0 and earlier (KiCad issue 25751).
 *
 * <p>The fixture holds two 0603 resistors of the KiCad standard library, with the pad polygons
 * written by KiCad 10.0.8, and a clearance of 200 um everywhere:
 *
 * <ul>
 *   <li>R1 pad 2 and R2 pad 1 are exactly 200 um apart. The exported polygons are 0.761 um too
 *       large on every straight edge, which turned this into a clearance violation that KiCad does
 *       not report.
 *   <li>A 0.6 mm via sits 198.5 um from the real upper left corner of R1 pad 1, facing the middle
 *       of a chord of the exported polygon (201.6 um away). KiCad reports this violation;
 *       Freerouting did not, so it could route copper that close.
 * </ul>
 */
class KiCadRoundRectPadFixtureTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void clearanceIsCheckedAgainstTheRealPadShape() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("KiCad10-roundrect-pads.dsn");
    Collection<ClearanceViolation> violations =
        new DesignRulesChecker(board, null).getAllClearanceViolations();

    long padToPad =
        violations.stream()
            .filter(v -> v.firstItem instanceof Pin && v.secondItem instanceof Pin)
            .count();
    long viaToPad =
        violations.stream()
            .filter(
                v ->
                    (v.firstItem instanceof Via && v.secondItem instanceof Pin)
                        || (v.firstItem instanceof Pin && v.secondItem instanceof Via))
            .count();
    assertEquals(0, padToPad, "pads exactly at clearance must not be reported");
    assertEquals(1, viaToPad, "the via 198.5 um from the real corner must be reported");
  }

  /**
   * Two 20 x 20 mm pads with 9 mm corners (KiCad 10.0.8 export), corner to corner 200 um + 2.5 um
   * apart (KiCad reports nothing), on the diagonal of a vertex of the corrected corner. With at
   * most 32 segments per quarter, that corner exceeded the real arc by 2.7 um on each pad, beyond
   * the 1 um tolerance of the checker, and the two pads were reported as a violation.
   */
  @Test
  void largeCornersStayWithinHalfAMicrometreOfTheRealArc() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("KiCad10-large-roundrect-pads.dsn");
    Collection<ClearanceViolation> violations =
        new DesignRulesChecker(board, null).getAllClearanceViolations();
    assertEquals(0, violations.size(), "pads 2.5 um beyond the clearance must not be reported");
  }
}
