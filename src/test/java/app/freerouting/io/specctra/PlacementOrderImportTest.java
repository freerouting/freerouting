package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.BoardReadResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlacementOrderImportTest {
  private static final String FIXTURE = "Issue1016-placement-order.dsn";

  @ParameterizedTest
  @ValueSource(
      strings = {"Issue1016-placement-order-reversed.dsn", "Issue1016-placement-order-split.dsn"})
  void placementPermutationsPreserveNamedAllocationAndGeometry(String fixture) throws IOException {
    RoutingBoard expected = load(FIXTURE);
    RoutingBoard actual = load(fixture);
    assertEquals(componentIds(expected), componentIds(actual));
    assertEquals(itemIds(expected), itemIds(actual));
    assertFixtureSemantics(actual);
  }

  @Test
  void existingReaderOverloadsRetainInputOrder() throws IOException {
    RoutingBoard original = DsnTestFixtures.loadBoard(FIXTURE);
    RoutingBoard reversed = DsnTestFixtures.loadBoard("Issue1016-placement-order-reversed.dsn");
    original.awaitPostLoad();
    reversed.awaitPostLoad();
    assertEquals("A1", original.components.get(1).name);
    assertEquals("B2", reversed.components.get(1).name);
    assertNotEquals(componentIds(original), componentIds(reversed));
  }

  @Test
  void nullReadOptionsRetainInputOrder() throws IOException {
    try (var in = DsnTestFixtures.openResource("Issue1016-placement-order-reversed.dsn")) {
      var result = (BoardReadResult.Success) DsnReader.readBoard(in, null, null, null, null);
      assertEquals("B2", result.board().components.get(1).name);
    }
  }

  @Test
  void exactReferenceOrderingDoesNotFoldCaseOrInterpretNumericSuffixes() throws IOException {
    RoutingBoard board = fromText(text().replace("A1", "a1").replace("A2", "A10"));
    assertEquals(Map.of("A10", 1, "B1", 2, "B2", 3, "C1", 4, "a1", 5), componentIds(board));
  }

  @Test
  void duplicateReferencesKeepTheirOriginalPackageAssociation() throws IOException {
    String original = text().replace("A1", "DUP").replace("B2", "DUP");
    RoutingBoard firstPad = fromText(original);
    String reversed =
        text("Issue1016-placement-order-reversed.dsn").replace("A1", "DUP").replace("B2", "DUP");
    RoutingBoard firstGuard = fromText(reversed);
    assertEquals(5, firstPad.components.count());
    assertEquals(5, firstGuard.components.count());
    assertEquals("PAD", firstPad.components.get("DUP").getPackage().name);
    assertEquals(50000, firstPad.components.get("DUP").getExactLocation().x);
    assertEquals("GUARD", firstGuard.components.get("DUP").getPackage().name);
    assertEquals(100000, firstGuard.components.get("DUP").getExactLocation().x);
    assertEquals("guard-123", firstGuard.components.get("DUP").getPartNumber());
  }

  @Test
  void unplacedComponentsRetainTheirPackageWithoutAllocatingCopper() throws IOException {
    RoutingBoard board =
        fromText(text().replace("(component PAD ", "(component PAD (place UNUSED) "));
    assertEquals(6, board.components.count());
    assertFalse(board.components.get("UNUSED").isPlaced());
    assertEquals("PAD", board.components.get("UNUSED").getPackage().name);
    assertEquals(5, board.getPins().size());
    assertEquals(6, itemIds(board).size());
  }

  private static void assertFixtureSemantics(RoutingBoard board) {
    assertEquals(5, board.components.count());
    assertEquals(5, board.getPins().size());
    assertEquals(6, itemIds(board).size(), "Five pads and one package keepout");
    var guard = board.components.get("B2");
    assertFalse(guard.placedOnFront());
    assertEquals(12.5, guard.getRotationInDegree());
    assertTrue(guard.positionFixed);
    assertEquals("guard-123", guard.getPartNumber());
    assertEquals("GUARD", guard.getPackage().name);
    assertEquals(100000, guard.getExactLocation().x);
    assertEquals(80000, guard.getExactLocation().y);
    Map<String, IntPoint> centers =
        Map.of(
            "A1", new IntPoint(50000, 50000),
            "A2", new IntPoint(54000, 50000),
            "C1", new IntPoint(150000, 50000),
            "B1", new IntPoint(100000, 20000),
            "B2", new IntPoint(100000, 80000));
    for (var pin : board.getPins()) {
      assertEquals(centers.get(pin.componentName()), pin.getCenter());
      assertEquals("1", pin.name());
      assertEquals(1, pin.netCount());
      assertEquals(
          pin.componentName().startsWith("B") ? "B" : "A",
          board.rules.nets.get(pin.getNetNumber(0)).name);
      assertEquals(pin.componentName().equals("B2") ? 1 : 0, pin.firstLayer());
    }
  }

  private static Map<String, Integer> componentIds(RoutingBoard board) {
    Map<String, Integer> ids = new TreeMap<>();
    for (int i = 1; i <= board.components.count(); i++) {
      ids.put(board.components.get(i).name, i);
    }
    return ids;
  }

  private static Map<String, Integer> itemIds(RoutingBoard board) {
    Map<String, Integer> ids = new TreeMap<>();
    for (Item item : board.getItems()) {
      if (item.getComponentId() > 0) {
        String key = item.componentName() + ":" + item.getClass().getSimpleName();
        assertNull(ids.put(key, item.getId()), "Fixture item keys must be unique");
      }
    }
    return ids;
  }

  private static RoutingBoard load(String name) throws IOException {
    RoutingBoard board;
    try (var in = DsnTestFixtures.openResource(name)) {
      board =
          (RoutingBoard)
              ((BoardReadResult.Success)
                      DsnReader.readBoard(in, null, null, name, DsnReadOptions.REFERENCE_NAME))
                  .board();
    }
    board.awaitPostLoad();
    return board;
  }

  private static String text() throws IOException {
    return text(FIXTURE);
  }

  private static String text(String name) throws IOException {
    try (var in = DsnTestFixtures.openResource(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static RoutingBoard fromText(String text) throws IOException {
    RoutingBoard board =
        (RoutingBoard)
            ((BoardReadResult.Success)
                    DsnReader.readBoard(
                        new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                        null,
                        null,
                        null,
                        DsnReadOptions.REFERENCE_NAME))
                .board();
    board.awaitPostLoad();
    return board;
  }
}
