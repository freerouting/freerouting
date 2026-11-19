package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoroutePassRunnerTest {

  private static final String DSN =
      """
      (pcb pass_runner_test (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 1000000 1000000)) (rule (width 150) (clearance 150)))
        (placement
          (component ONE_PIN (place U1 1000 1000 front 0))
          (component FOUR_PIN (place U2 5000 1000 front 0))
          (component FAR_PIN (place U3 900000 900000 front 0)))
        (library
          (image ONE_PIN (pin P1 1 0 0))
          (image FOUR_PIN (pin P1 1 0 0) (pin P1 2 1000 0) (pin P1 3 0 1000) (pin P1 4 1000 1000))
          (image FAR_PIN (pin P1 1 0 0))
          (padstack P1 (shape (rect top -100 -100 100 100)) (attach off)))
        (network
          (net N1 (pins U1-1 U2-1))
          (net N2 (pins U3-1))))
      """;

  private static RoutingBoard loadBoard() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)), null, null, "test");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void deterministicItemComparatorOrdersHigherPinCountComponentFirstWithinSameNet() {
    RoutingBoard board = loadBoard();
    int netN1 = board.rules.nets.get("N1", 1).netNumber;

    List<Item> n1Pins = new ArrayList<>();
    Pin u1Pin = null;
    Pin u2Pin = null;
    for (Pin pin : board.getPins()) {
      if (pin.containsNet(netN1)) {
        n1Pins.add(pin);
        String compName = board.components.get(pin.getComponentId()).name;
        if ("U1".equals(compName)) {
          u1Pin = pin;
        } else if ("U2".equals(compName)) {
          u2Pin = pin;
        }
      }
    }

    assertEquals(2, n1Pins.size());
    assertEquals(1, AutoroutePassRunner.componentPinCount(board, u1Pin));
    assertEquals(4, AutoroutePassRunner.componentPinCount(board, u2Pin));

    // Ensure U1 appears before U2 initially (lower ID), then sort with comparator.
    n1Pins.sort(java.util.Comparator.comparingInt(Item::getId));
    assertEquals(u1Pin, n1Pins.get(0));

    n1Pins.sort(AutoroutePassRunner.deterministicItemComparator(board));
    assertEquals(u2Pin, n1Pins.get(0));
    assertEquals(u1Pin, n1Pins.get(1));
  }

  @Test
  void invalidateZeroRipupPairsKeepsDistantPairsAndDropsIntersectingPairs() {
    RoutingBoard board = loadBoard();
    int netN1 = board.rules.nets.get("N1", 1).netNumber;
    int netN2 = board.rules.nets.get("N2", 1).netNumber;

    List<Item> allPins = new ArrayList<>(board.getPins());
    Item n1Pin = allPins.stream().filter(p -> p.containsNet(netN1)).findFirst().orElseThrow();
    Item n2Pin = allPins.stream().filter(p -> p.containsNet(netN2)).findFirst().orElseThrow();

    Map<Integer, IntBox> paddedBounds = AutoroutePassRunner.buildPaddedNetBounds(allPins);
    assertTrue(paddedBounds.containsKey(netN1));
    assertTrue(paddedBounds.containsKey(netN2));

    var pairN1 =
        new AutoroutePassRunner.FailedConnectionPair(
            netN1, Set.of(n1Pin.getId()), Set.of(n1Pin.getId() + 100));
    var pairN2 =
        new AutoroutePassRunner.FailedConnectionPair(
            netN2, Set.of(n2Pin.getId()), Set.of(n2Pin.getId() + 100));

    Map<AutoroutePassRunner.FailedConnectionPair, IntBox> zeroRipupPairs = new LinkedHashMap<>();
    zeroRipupPairs.put(pairN1, paddedBounds.get(netN1));
    zeroRipupPairs.put(pairN2, paddedBounds.get(netN2));

    AutoroutePassRunner.invalidateZeroRipupPairs(zeroRipupPairs, List.of(n1Pin));

    assertFalse(zeroRipupPairs.containsKey(pairN1));
    assertTrue(zeroRipupPairs.containsKey(pairN2));
  }
}
