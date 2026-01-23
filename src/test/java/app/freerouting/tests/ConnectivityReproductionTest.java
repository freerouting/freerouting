package app.freerouting.tests;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Communication;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Unit;
import app.freerouting.board.Via;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConnectivityReproductionTest {

        @Test
        void test_connectivity_tolerance() {
                // 1. Setup a minimal board
                Layer layer1 = new Layer("Top", true);
                Layer layer2 = new Layer("Bottom", true);
                Layer[] layers = new Layer[] { layer1, layer2 };
                LayerStructure layerStructure = new LayerStructure(layers);

                ClearanceMatrix clearanceMatrix = ClearanceMatrix.get_default_instance(layerStructure, 10);
                BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);

                // Create a default via rule so we can create vias
                boardRules.create_default_net_class();

                Communication communication = new Communication(); // Default communication

                BasicBoard board = new BasicBoard(
                                new IntBox(0, 0, 10000, 10000),
                                layerStructure,
                                new PolylineShape[] { TileShape.get_instance(0, 0, 10000, 10000) }, // outline shapes
                                0, // outline clearance class
                                boardRules,
                                communication);

                int layerIndex = 0;
                int netNo = 1;
                int clearanceClass = 1;
                int halfWidth = 20;

                // Trace from (0, 100) to (100, 100)
                Point start = new IntPoint(0, 100);
                Point end = new IntPoint(100, 100);
                Polyline polyline = new Polyline(start, end);
                PolylineTrace trace = new PolylineTrace(polyline, layerIndex, halfWidth, new int[] { netNo },
                                clearanceClass, 1, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
                board.item_list.insert(trace);

                // Trace 2: (121, 100) to (200, 100). HalfWidth 20.
                // Trace 1 ends 100. Trace 2 starts 121.
                // Gap = 21.
                // Trace 1 tolerance = 20 + 1 = 21.
                // Distance from trace end (100) to next trace start (121) = 21.
                // They should be connected within tolerance.

                Point start2 = new IntPoint(121, 100);
                Point end2 = new IntPoint(200, 100);
                Polyline polyline2 = new Polyline(start2, end2);
                PolylineTrace trace2 = new PolylineTrace(polyline2, layerIndex, halfWidth, new int[] { netNo },
                                clearanceClass, 2, 0, app.freerouting.board.FixedState.NOT_FIXED, board);
                board.item_list.insert(trace2);

                // Manually insert into search tree because we bypassed board.insert_item
                board.search_tree_manager.insert(trace);
                board.search_tree_manager.insert(trace2);

                Set<app.freerouting.board.Item> contacts = trace.get_normal_contacts();

                Assertions.assertFalse(contacts.isEmpty(), "Trace should find connected item within tolerance");
                Assertions.assertTrue(contacts.contains(trace2), "Trace should be connected to Trace2");
        }
}
