package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

class DifferentialPairTest {

  private Net posNet;
  private Net negNet;
  private Net otherNet;

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
    posNet = board.rules.nets.add("USB_P", 1, false);
    negNet = board.rules.nets.add("USB_N", 1, false);
    otherNet = board.rules.nets.add("CLK", 1, false);
  }

  @Test
  void testConstructorAndGetters() {
    DifferentialPair pair = new DifferentialPair(posNet, negNet, "USB", 250.0, 50.0);

    assertEquals(posNet, pair.positiveNet());
    assertEquals(negNet, pair.negativeNet());
    assertEquals("USB", pair.baseName());
    assertEquals(250.0, pair.coupledSpacing(), 1e-4);
    assertEquals(50.0, pair.maxSkew(), 1e-4);
    assertTrue(pair.hasCoupledSpacing());
    assertTrue(pair.hasMaxSkew());
  }

  @Test
  void testThreeArgConstructorDefaults() {
    DifferentialPair pair = new DifferentialPair(posNet, negNet, "USB");

    assertEquals(0.0, pair.coupledSpacing());
    assertEquals(0.0, pair.maxSkew());
    assertFalse(pair.hasCoupledSpacing());
    assertFalse(pair.hasMaxSkew());
  }

  @Test
  void testPartnerLookups() {
    DifferentialPair pair = new DifferentialPair(posNet, negNet, "USB");

    assertTrue(pair.contains(posNet));
    assertTrue(pair.contains(negNet));
    assertFalse(pair.contains(otherNet));
    assertFalse(pair.contains(null));

    assertEquals(negNet, pair.getPartner(posNet));
    assertEquals(posNet, pair.getPartner(negNet));
    assertNull(pair.getPartner(otherNet));
    assertNull(pair.getPartner(null));

    assertTrue(pair.isPositive(posNet));
    assertFalse(pair.isPositive(negNet));
    assertFalse(pair.isPositive(otherNet));

    assertTrue(pair.isNegative(negNet));
    assertFalse(pair.isNegative(posNet));
    assertFalse(pair.isNegative(otherNet));
  }

  @Test
  void testConstructorValidations() {
    assertThrows(
        NullPointerException.class,
        () -> new DifferentialPair(null, negNet, "USB"),
        "positiveNet cannot be null");
    assertThrows(
        NullPointerException.class,
        () -> new DifferentialPair(posNet, null, "USB"),
        "negativeNet cannot be null");
    assertThrows(
        NullPointerException.class,
        () -> new DifferentialPair(posNet, negNet, null),
        "baseName cannot be null");
    assertThrows(
        IllegalArgumentException.class,
        () -> new DifferentialPair(posNet, posNet, "USB"),
        "positiveNet and negativeNet cannot be the same net");
  }

  @Test
  void testNegativeBoundsClampedToZero() {
    DifferentialPair pair = new DifferentialPair(posNet, negNet, "USB", -10.0, -5.0);

    assertEquals(0.0, pair.coupledSpacing());
    assertEquals(0.0, pair.maxSkew());
  }

  @Test
  void testWithers() {
    DifferentialPair initial = new DifferentialPair(posNet, negNet, "USB");

    DifferentialPair withSpacing = initial.withCoupledSpacing(300.0);
    assertEquals(300.0, withSpacing.coupledSpacing());
    assertEquals(0.0, withSpacing.maxSkew());

    DifferentialPair withSkew = withSpacing.withMaxSkew(20.0);
    assertEquals(300.0, withSkew.coupledSpacing());
    assertEquals(20.0, withSkew.maxSkew());

    assertNotEquals(initial, withSkew);
  }

  @Test
  void testSerializationParity() throws Exception {
    DifferentialPair original = new DifferentialPair(posNet, negNet, "USB", 250.0, 50.0);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(baos)) {
      output.writeObject(original);
    }

    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      DifferentialPair deserialized = (DifferentialPair) input.readObject();
      assertNotNull(deserialized);
      assertEquals(original.baseName(), deserialized.baseName());
      assertEquals(original.coupledSpacing(), deserialized.coupledSpacing());
      assertEquals(original.maxSkew(), deserialized.maxSkew());
      assertEquals(original.positiveNet().name, deserialized.positiveNet().name);
      assertEquals(original.negativeNet().name, deserialized.negativeNet().name);
    }
  }
}
