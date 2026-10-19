package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DifferentialPairsTest {

  private Net pos1;
  private Net neg1;
  private Net pos2;
  private Net neg2;
  private Net singleEnded;

  private app.freerouting.board.facade.RoutingBoard createTestBoard() {
    app.freerouting.board.model.structure.Layer layer1 =
        new app.freerouting.board.model.structure.Layer("Top", true);
    app.freerouting.board.model.structure.Layer[] layers =
        new app.freerouting.board.model.structure.Layer[] {layer1};
    app.freerouting.board.model.structure.LayerStructure layerStructure =
        new app.freerouting.board.model.structure.LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    app.freerouting.board.state.Communication communication =
        new app.freerouting.board.state.Communication();

    return new app.freerouting.board.facade.RoutingBoard(
        new app.freerouting.geometry.planar.IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new app.freerouting.geometry.planar.PolylineShape[] {
          app.freerouting.geometry.planar.TileShape.getInstance(0, 0, 2000000, 2000000)
        },
        0,
        boardRules,
        communication);
  }

  @BeforeEach
  void setUp() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    pos1 = board.rules.nets.add("USB_P", 1, false);
    neg1 = board.rules.nets.add("USB_N", 1, false);
    pos2 = board.rules.nets.add("CLK_P", 1, false);
    neg2 = board.rules.nets.add("CLK_N", 1, false);
    singleEnded = board.rules.nets.add("RESET", 1, false);
  }

  @Test
  void testAddAndGet() {
    DifferentialPairs registry = new DifferentialPairs();
    assertTrue(registry.isEmpty());
    assertEquals(0, registry.count());

    DifferentialPair usb = new DifferentialPair(pos1, neg1, "USB", 250.0, 50.0);
    registry.add(usb);

    assertFalse(registry.isEmpty());
    assertEquals(1, registry.count());
    assertEquals(usb, registry.get("USB"));
    assertEquals(usb, registry.get("usb"));
    assertEquals(usb, registry.get("Usb"));
    assertNull(registry.get("CLK"));
    assertNull(registry.get(null));
  }

  @Test
  void testFindForNet() {
    DifferentialPairs registry = new DifferentialPairs();
    DifferentialPair usb = new DifferentialPair(pos1, neg1, "USB");
    DifferentialPair clk = new DifferentialPair(pos2, neg2, "CLK");

    registry.add(usb);
    registry.add(clk);

    assertEquals(usb, registry.findForNet(pos1));
    assertEquals(usb, registry.findForNet(neg1));
    assertEquals(usb, registry.findForNet(pos1.netNumber));
    assertEquals(usb, registry.findForNet(neg1.netNumber));

    assertEquals(clk, registry.findForNet(pos2));
    assertEquals(clk, registry.findForNet(neg2));

    assertNull(registry.findForNet(singleEnded));
    assertNull(registry.findForNet(null));
    assertNull(registry.findForNet(9999));
  }

  @Test
  void testDuplicateNetRegistrationRejected() {
    DifferentialPairs registry = new DifferentialPairs();
    DifferentialPair usb = new DifferentialPair(pos1, neg1, "USB");
    registry.add(usb);

    // Re-adding identical pair or updating constraints for the same pair is allowed
    registry.add(usb);
    DifferentialPair updatedUsb = usb.withCoupledSpacing(300.0).withMaxSkew(25.0);
    registry.add(updatedUsb);
    assertEquals(300.0, registry.get("USB").coupledSpacing());
    assertEquals(updatedUsb, registry.findForNet(pos1));

    // Attempting to register different nets under an existing baseName must throw
    DifferentialPair conflictBaseName = new DifferentialPair(pos2, neg2, "USB");
    assertThrows(IllegalArgumentException.class, () -> registry.add(conflictBaseName));

    // Attempting to pair an already-paired net with another net must throw
    DifferentialPair conflictPos = new DifferentialPair(pos1, neg2, "CONFLICT");
    assertThrows(IllegalArgumentException.class, () -> registry.add(conflictPos));

    DifferentialPair conflictNeg = new DifferentialPair(pos2, neg1, "CONFLICT");
    assertThrows(IllegalArgumentException.class, () -> registry.add(conflictNeg));
  }

  @Test
  void testRemove() {
    DifferentialPairs registry = new DifferentialPairs();
    DifferentialPair usb = new DifferentialPair(pos1, neg1, "USB");
    registry.add(usb);

    assertEquals(1, registry.count());
    assertTrue(registry.remove(usb));
    assertEquals(0, registry.count());
    assertTrue(registry.isEmpty());
    assertNull(registry.get("USB"));
    assertNull(registry.findForNet(pos1));
    assertNull(registry.findForNet(neg1));

    assertFalse(registry.remove(usb));
    assertFalse(registry.remove(null));
  }

  @Test
  void testClearAndAll() {
    DifferentialPairs registry = new DifferentialPairs();
    registry.add(new DifferentialPair(pos1, neg1, "USB"));
    registry.add(new DifferentialPair(pos2, neg2, "CLK"));

    assertEquals(2, registry.count());
    assertEquals(2, registry.all().size());

    registry.clear();
    assertEquals(0, registry.count());
    assertTrue(registry.isEmpty());
    assertTrue(registry.all().isEmpty());
  }

  @Test
  void testBoardRulesField() {
    app.freerouting.board.facade.RoutingBoard board = createTestBoard();
    assertNotNull(board.rules.differentialPairs);
    assertTrue(board.rules.differentialPairs.isEmpty());
  }

  @Test
  void testSerializationParity() throws Exception {
    DifferentialPairs registry = new DifferentialPairs();
    registry.add(new DifferentialPair(pos1, neg1, "USB", 250.0, 50.0));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(baos)) {
      output.writeObject(registry);
    }

    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      DifferentialPairs deserialized = (DifferentialPairs) input.readObject();
      assertNotNull(deserialized);
      assertEquals(1, deserialized.count());
      assertNotNull(deserialized.get("USB"));
    }
  }
}
