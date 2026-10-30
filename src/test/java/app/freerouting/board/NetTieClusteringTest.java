package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Tests the precalculated net-tie clustering on {@link RoutingBoard#clusterNetTies()} and {@link
 * Pin#getNetTieClusterId()}.
 */
class NetTieClusteringTest {

  /**
   * DSN fixture with:
   *
   * <ul>
   *   <li>Component R1 with two distinct net-tie clusters on front (P1+P2 overlapping at 0,0, and
   *       P3+P4 overlapping at 5000,0)
   *   <li>Component R2 with pads overlapping in (X,Y) but on different layers (F.Cu vs B.Cu) ->
   *       must NOT cluster
   *   <li>Component R3 with a pad overlapping R1's pad across different components -> must NOT
   *       cluster
   * </ul>
   */
  private static final String DSN =
      """
      (pcb net_tie_clustering (resolution um 1) (unit um)
        (structure
          (layer F.Cu (type signal))
          (layer B.Cu (type signal))
          (boundary (rect pcb 0 0 50000 50000))
          (via V)
          (rule (width 200) (clearance 200)))
        (placement
          (component COMP1 (place R1 10000 10000 front 0))
          (component COMP2 (place R2 20000 10000 front 0))
          (component COMP3 (place R3 10000 10000 front 0)))
        (library
          (image COMP1
            (pin FRONT_PAD 1 0 0)
            (pin FRONT_PAD 2 0 0)
            (pin FRONT_PAD 3 5000 0)
            (pin FRONT_PAD 4 5000 0)
            (pin FRONT_PAD 5 15000 0))
          (image COMP2
            (pin FRONT_PAD 1 0 0)
            (pin BACK_PAD 2 0 0))
          (image COMP3
            (pin FRONT_PAD 1 0 0))
          (padstack FRONT_PAD (shape (rect F.Cu -500 -500 500 500)) (attach off))
          (padstack BACK_PAD (shape (rect B.Cu -500 -500 500 500)) (attach off))
          (padstack V (shape (circle F.Cu 300)) (shape (circle B.Cu 300)) (attach off)))
        (network
          (net NET1 (pins R1-1))
          (net NET2 (pins R1-2))
          (net NET3 (pins R1-3))
          (net NET4 (pins R1-4))
          (net NET5 (pins R1-5))
          (net NET6 (pins R2-1))
          (net NET7 (pins R2-2))
          (net NET8 (pins R3-1))))
      """;

  private static RoutingBoard load() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)),
            null,
            null,
            "net_tie_clustering");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static Pin pin(RoutingBoard board, String component, String name) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name)
                    && name.equals(p.name()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void testNetTieClusteringPartitionsClustersAndRespectsLayersAndComponents() {
    RoutingBoard board = load();

    // 1. Initial state: cluster IDs are 0
    Pin r1p1 = pin(board, "R1", "1");
    Pin r1p2 = pin(board, "R1", "2");
    Pin r1p3 = pin(board, "R1", "3");
    Pin r1p4 = pin(board, "R1", "4");
    Pin r1p5 = pin(board, "R1", "5");
    Pin r2p1 = pin(board, "R2", "1");
    Pin r2p2 = pin(board, "R2", "2");
    Pin r3p1 = pin(board, "R3", "1");

    assertEquals(0, r1p1.getNetTieClusterId());
    assertEquals(0, r1p2.getNetTieClusterId());

    // 2. Run clustering
    int clusters = board.clusterNetTies();
    assertEquals(2, clusters, "Expected exactly 2 multi-pin clusters on R1");

    // 3. Cluster 1 on R1 (pins 1 and 2)
    int cluster1 = r1p1.getNetTieClusterId();
    assertTrue(cluster1 > 0, "Cluster 1 must have a positive ID");
    assertEquals(cluster1, r1p2.getNetTieClusterId(), "R1 pin 1 and 2 must share cluster ID");

    // 4. Cluster 2 on R1 (pins 3 and 4)
    int cluster2 = r1p3.getNetTieClusterId();
    assertTrue(cluster2 > 0, "Cluster 2 must have a positive ID");
    assertEquals(cluster2, r1p4.getNetTieClusterId(), "R1 pin 3 and 4 must share cluster ID");
    assertNotEquals(cluster1, cluster2, "Cluster 1 and Cluster 2 on R1 must be distinct");

    // 5. Standalone pin on R1 (pin 5)
    assertEquals(0, r1p5.getNetTieClusterId(), "Isolated pin must have cluster ID 0");

    // 6. Layer separation: R2 pins 1 & 2 overlap in 2D space but are on F.Cu and B.Cu respectively
    assertEquals(0, r2p1.getNetTieClusterId(), "Pads on different layers must not cluster");
    assertEquals(0, r2p2.getNetTieClusterId(), "Pads on different layers must not cluster");

    // 7. Component separation: R3 pin 1 overlaps R1 pin 1 in location, but belongs to COMP3
    assertEquals(0, r3p1.getNetTieClusterId(), "Pads on different components must not cluster");
  }
}
