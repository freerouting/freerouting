package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Verifies that the search/locator clearance envelope aligns with the insertion stage safeguards,
 * preventing trace corners from being planned on search boundaries that insertion later rejects as
 * inside the obstacle's clearance envelope.
 */
class ClearanceEnvelopeAlignmentTest {

  @Test
  void testProbeRstPadClearanceEnvelopeAlignment() {
    // Exact coordinates captured from the release 2.5.0 RST pad probe
    IntOctagon pad =
        new IntOctagon(2109040, -636500, 2117040, -628500, 2739884, 2751197, 1474884, 1486197);
    Point capturedCorner = new IntPoint(2111512, -625248);

    int traceHalfWidth = 1250;
    int clearance = 2000;
    int clearanceCompensation = (clearance + 1) / 2; // 1000
    int searchTolerance = 2;
    int clearanceSafetyMargin = 16;
    int halfClearanceOffset = (clearance + clearanceSafetyMargin) / 2; // 1008

    // 1. Insertion envelope: built by TraceShover.springOver in uncompensated mode
    TileShape insertionEnvelope =
        (TileShape)
            pad.enlarge(traceHalfWidth + 1 + halfClearanceOffset).enlarge(halfClearanceOffset);

    // 2. Legacy search envelope: only compensated trace half-width + 2
    int legacyCompensatedTraceHalfWidth = traceHalfWidth + clearanceCompensation; // 2250
    TileShape legacySearchEnvelope =
        (TileShape)
            pad.enlarge(clearance - clearanceCompensation)
                .enlarge(
                    legacyCompensatedTraceHalfWidth
                        + searchTolerance); // enlarge(1000).enlarge(2252)

    // Verify the reproduced bug: legacy search allowed capturedCorner, but insertion rejected it
    assertFalse(
        legacySearchEnvelope.containsInside(capturedCorner),
        "Legacy search envelope incorrectly allowed the planned corner");
    assertEquals(
        0.0,
        legacySearchEnvelope.borderDistance(capturedCorner.toFloat()),
        0.001,
        "Legacy search placed corner exactly on its outer boundary");
    assertTrue(
        insertionEnvelope.containsInside(capturedCorner),
        "Insertion stage correctly rejected the corner as inside the clearance envelope");
    assertEquals(
        15.0,
        insertionEnvelope.borderDistance(capturedCorner.toFloat()),
        0.001,
        "Corner was 15 units inside insertion envelope");

    // 3. Aligned search envelope: includes the insertion clearance safety margin (2268)
    int alignedTraceClearanceOffset =
        legacyCompensatedTraceHalfWidth + searchTolerance + clearanceSafetyMargin; // 2268
    TileShape alignedSearchEnvelope =
        (TileShape)
            pad.enlarge(clearance - clearanceCompensation).enlarge(alignedTraceClearanceOffset);

    // Verify the fix: aligned search recognizes the captured corner as forbidden
    assertTrue(
        alignedSearchEnvelope.containsInside(capturedCorner),
        "Aligned search envelope must recognize the corner as inside the forbidden region");

    // Verify that points on or outside the aligned search envelope are accepted by insertion
    Point movedCorner = new IntPoint(2111512, -625232); // 16 units outward
    assertFalse(
        alignedSearchEnvelope.containsInside(movedCorner),
        "Point outward on the aligned search boundary is permitted by search");
    assertFalse(
        insertionEnvelope.containsInside(movedCorner),
        "Point outward on the aligned search boundary is also permitted by insertion");
  }

  @ParameterizedTest
  @CsvSource({
    "1250, 2000",
    "750, 2000",
    "500, 1500",
    "1000, 2001", // odd clearance
    "1250, 2501" // odd clearance and half-width
  })
  void testSearchEnvelopeContainsInsertionEnvelope(int traceHalfWidth, int clearance) {
    IntOctagon pad = new IntOctagon(100000, -200000, 150000, -150000, 250000, 350000, -100000, 0);

    int clearanceCompensation = (clearance + 1) / 2;
    int searchTolerance = 2;
    int clearanceSafetyMargin = 16;
    int halfClearanceOffset = (clearance + clearanceSafetyMargin) / 2;

    TileShape insertionEnvelope =
        (TileShape)
            pad.enlarge(traceHalfWidth + 1 + halfClearanceOffset).enlarge(halfClearanceOffset);

    int alignedTraceClearanceOffset =
        traceHalfWidth + clearanceCompensation + searchTolerance + clearanceSafetyMargin;
    TileShape alignedSearchEnvelope =
        (TileShape)
            pad.enlarge(clearance - clearanceCompensation).enlarge(alignedTraceClearanceOffset);

    // The aligned search envelope must contain the insertion envelope everywhere
    assertTrue(
        alignedSearchEnvelope.contains(insertionEnvelope),
        "Aligned search envelope must completely contain insertion envelope for traceHalfWidth="
            + traceHalfWidth
            + ", clearance="
            + clearance);
  }

  @Test
  void testProbeGndTargetEmptySafeRegion() {
    // Reconstruct the exact geometry captured from the release 2.5.0 GND probe
    TileShape room =
        new IntOctagon(2096240, -652900, 2188005, -637500, 2733740, 2825505, 1443340, 1550505);
    TileShape target =
        new IntOctagon(2107787, -639750, 2110037, -637500, 2745287, 2749787, 1470287, 1470287);
    int traceClearanceOffset = 2268;

    // 1. Raw intersection touches room within the forbidden clearance zone of an obstacle
    TileShape rawIntersection = target.intersection(room);
    assertFalse(rawIntersection.isEmpty(), "Raw target intersects the room");

    // 2. Width-safe intersection (inset by traceClearanceOffset) is empty
    TileShape safeRoom = (TileShape) room.offset(-traceClearanceOffset);
    TileShape safeIntersection = target.intersection(safeRoom);
    assertTrue(
        safeIntersection.isEmpty(),
        "Width-safe target region must be empty when target only touches clearance margin");

    // 3. Target extending deeper into the room has a non-empty safe region inside safeRoom
    TileShape deeperTarget = new IntBox(2107787, -645000, 2110037, -637500).boundingOctagon();
    TileShape safeDeeperIntersection = deeperTarget.intersection(safeRoom);
    assertFalse(safeDeeperIntersection.isEmpty(), "Deep target must have non-empty safe region");
    Point safeCenter = safeDeeperIntersection.centreOfGravity().round();
    assertTrue(safeRoom.contains(safeCenter), "Safe connection center must lie inside safe room");
  }
}
