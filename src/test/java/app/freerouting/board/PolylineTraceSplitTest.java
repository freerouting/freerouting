package app.freerouting.board;

import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

/**
 * Test cases for PolylineTrace split operations, particularly around
 * overlap handling and cycle detection.
 */
public class PolylineTraceSplitTest {

    /**
     * Creates a minimal test board for trace operations.
     */
    private RoutingBoard createTestBoard() {
        Layer layer1 = new Layer("Top", true);
        Layer[] layers = new Layer[] { layer1 };
        LayerStructure layerStructure = new LayerStructure(layers);

        ClearanceMatrix clearanceMatrix = ClearanceMatrix.get_default_instance(layerStructure, 10);
        BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
        boardRules.create_default_net_class();

        Communication communication = new Communication();

        return new RoutingBoard(
                new IntBox(0, 0, 2000000, 2000000),
                layerStructure,
                new PolylineShape[] { TileShape.get_instance(0, 0, 2000000, 2000000) },
                0,
                boardRules,
                communication);
    }

    /**
     * Test that reproduces the bug where a valid trace segment is incorrectly
     * removed during split/overlap operations.
     * 
     * This test simulates the scenario from the bug report where:
     * 1. Two traces are combined (trace 360 + 361 -> combined trace 361)
     * 2. A new overlapping trace is inserted (trace 362)
     * 3. The split operation incorrectly removes the combined trace 361
     * 4. Important routing segments are lost
     */
    @Test
    void testSplitDoesNotRemoveValidSegments() {
        BasicBoard board = createTestBoard();
        int layerIndex = 0;
        int netNo = 98; // Net #98 (DCD) from the bug report
        int clearanceClass = 1;
        int halfWidth = 1000;

        // Step 1: Create first trace (corresponds to trace 360 in bug)
        // From (1291423,-987076) to (1243227,-964893)
        Point p1 = new IntPoint(1291423, -987076);
        Point p2 = new IntPoint(1270000, -975000); // intermediate point
        Point p3 = new IntPoint(1250000, -970000); // intermediate point
        Point p4 = new IntPoint(1243227, -964893);

        Polyline polyline1 = new Polyline(new Point[] { p1, p2, p3, p4 });
        PolylineTrace trace1 = new PolylineTrace(
                polyline1, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 360, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(trace1);

        // Step 2: Create second trace (corresponds to trace 361 before combining in
        // bug)
        // From (1243227,-964893) to (1241414,-964893) - a horizontal segment
        Point p5 = new IntPoint(1241414, -964893);
        Polyline polyline2 = new Polyline(p4, p5);
        PolylineTrace trace2 = new PolylineTrace(
                polyline2, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 361, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(trace2);

        // Step 3: Combine the traces (simulates the router's combine operation)
        // After combination, we should have one trace from p1 to p5
        boolean combined = trace2.combine();
        Assertions.assertTrue(combined, "Traces should be combined");

        // Get the combined trace (trace2 should now contain the combined path)
        PolylineTrace combinedTrace = null;
        for (Item item : board.get_items()) {
            if (item instanceof PolylineTrace trace) {
                if (trace.contains_net(netNo) && trace.is_on_the_board()) {
                    combinedTrace = trace;
                    break;
                }
            }
        }
        Assertions.assertNotNull(combinedTrace, "Combined trace should exist");

        // Verify the combined trace spans from p1 to p5
        Point firstCorner = combinedTrace.first_corner();
        Point lastCorner = combinedTrace.last_corner();
        boolean hasP1 = firstCorner.equals(p1) || lastCorner.equals(p1);
        boolean hasP5 = firstCorner.equals(p5) || lastCorner.equals(p5);
        Assertions.assertTrue(hasP1 && hasP5,
                "Combined trace should span from " + p1 + " to " + p5 +
                        ", but goes from " + firstCorner + " to " + lastCorner);

        // Step 4: Insert a new overlapping trace (corresponds to trace 362 in bug)
        // This trace overlaps with part of the combined trace
        // From (1243227,-964893) to (1241171,-952775) - diagonal/vertical path
        Point p6 = new IntPoint(1241171, -952775);
        Point p7 = new IntPoint(1242000, -960000); // intermediate

        Polyline polyline3 = new Polyline(new Point[] { p4, p7, p6 });
        PolylineTrace trace3 = new PolylineTrace(
                polyline3, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 362, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(trace3);

        // Step 5: Trigger split operation (this happens during normalization)
        // The bug occurs here - the combined trace should be split at overlap points,
        // but should NOT be completely removed as a "cycle"
        try {
            Collection<PolylineTrace> splitResult = combinedTrace.split((IntOctagon) null);

            // Step 6: Verify that we haven't lost the segment from p1 to p4
            // After split, there should be traces that connect p1 to something
            boolean hasConnectionFromP1 = false;
            for (PolylineTrace piece : splitResult) {
                if (piece.is_on_the_board()) {
                    Point pieceFirst = piece.first_corner();
                    Point pieceLast = piece.last_corner();
                    if (pieceFirst.equals(p1) || pieceLast.equals(p1)) {
                        hasConnectionFromP1 = true;
                        break;
                    }
                }
            }

            Assertions.assertTrue(hasConnectionFromP1,
                    "After split, there should still be a trace connected to the starting point " + p1 +
                            ". The segment from " + p1 + " to " + p4 + " should not be lost.");

        } catch (Exception e) {
            Assertions.fail("Split operation threw exception: " + e.getMessage());
        }

        // Step 7: Additional verification - check that there's still a path on the
        // board
        // connecting near p1
        int tracesNearP1 = 0;
        for (Item item : board.get_items()) {
            if (item instanceof PolylineTrace trace) {
                if (trace.contains_net(netNo) && trace.is_on_the_board()) {
                    double distToP1First = trace.first_corner().to_float().distance(p1.to_float());
                    double distToP1Last = trace.last_corner().to_float().distance(p1.to_float());
                    if (distToP1First < 100000 || distToP1Last < 100000) {
                        tracesNearP1++;
                    }
                }
            }
        }

        Assertions.assertTrue(tracesNearP1 > 0,
                "There should be at least one trace near the original start point after split. "
                        + "Found " + tracesNearP1 + " traces.");
    }

    /**
     * Simpler test: verify that split operation preserves non-overlapping segments.
     */
    @Test
    void testSplitPreservesNonOverlappingSegments() {
        BasicBoard board = createTestBoard();
        int layerIndex = 0;
        int netNo = 1;
        int clearanceClass = 1;
        int halfWidth = 1000;

        // Create a long trace with multiple segments
        Point p1 = new IntPoint(0, 0);
        Point p2 = new IntPoint(10000, 0);
        Point p3 = new IntPoint(20000, 0);
        Point p4 = new IntPoint(30000, 0);

        Polyline polyline1 = new Polyline(new Point[] { p1, p2, p3, p4 });
        PolylineTrace trace1 = new PolylineTrace(
                polyline1, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 1, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(trace1);

        // Create a second trace that only overlaps with the middle segment
        Point p5 = new IntPoint(10000, 0);
        Point p6 = new IntPoint(20000, 0);

        Polyline polyline2 = new Polyline(p5, p6);
        PolylineTrace trace2 = new PolylineTrace(
                polyline2, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 2, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(trace2);

        // Split trace1
        Collection<PolylineTrace> splitResult = trace1.split((IntOctagon) null);

        // Verify that segments from p1 to p2 and p3 to p4 are preserved
        // (the middle segment p2-p3 overlaps with trace2)
        boolean hasFirstSegment = false;
        boolean hasLastSegment = false;

        for (PolylineTrace piece : splitResult) {
            if (piece.is_on_the_board()) {
                Point first = piece.first_corner();
                Point last = piece.last_corner();

                // Check for segment containing p1
                if (first.equals(p1) || last.equals(p1)) {
                    hasFirstSegment = true;
                }
                // Check for segment containing p4
                if (first.equals(p4) || last.equals(p4)) {
                    hasLastSegment = true;
                }
            }
        }

        Assertions.assertTrue(hasFirstSegment,
                "First segment (containing p1) should be preserved after split");
        Assertions.assertTrue(hasLastSegment,
                "Last segment (containing p4) should be preserved after split");
    }

    /**
     * Test cycle detection doesn't trigger false positives when both ends
     * of a split piece temporarily touch the same newly-inserted trace.
     */
    @Test
    void testCycleDetectionDuringOverlap() {
        BasicBoard board = createTestBoard();
        int layerIndex = 0;
        int netNo = 1;
        int clearanceClass = 1;
        int halfWidth = 1000;

        // Create trace from A to C via B
        Point pA = new IntPoint(0, 0);
        Point pB = new IntPoint(10000, 0);
        Point pC = new IntPoint(20000, 0);

        Polyline polyline1 = new Polyline(new Point[] { pA, pB, pC });
        PolylineTrace traceABC = new PolylineTrace(
                polyline1, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 1, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(traceABC);

        // Insert overlapping trace from B to C
        Polyline polyline2 = new Polyline(pB, pC);
        PolylineTrace traceBC = new PolylineTrace(
                polyline2, layerIndex, halfWidth, new int[] { netNo },
                clearanceClass, 2, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
        board.insert_item(traceBC);

        // Check that traceABC is NOT detected as a cycle
        // (even though after insertion of traceBC, if we were to split inappropriately,
        // both ends of a remaining piece might touch traceBC)
        boolean isCycle = traceABC.is_cycle();

        Assertions.assertFalse(isCycle,
                "Trace should not be detected as a cycle just because an overlapping trace exists");
    }
}
