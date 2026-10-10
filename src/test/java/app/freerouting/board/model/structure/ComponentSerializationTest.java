package app.freerouting.board.model.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Vector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import org.junit.jupiter.api.Test;

/** Tests serialization backward and forward compatibility for Component and Package.Pin. */
class ComponentSerializationTest {

  @Test
  void testComponentSerialVersionUidMatchesPinnedBaseline() {
    assertEquals(
        8656938804017005076L,
        ObjectStreamClass.lookup(Component.class).getSerialVersionUID(),
        "Component serialVersionUID must match baseline v2.5.0");
  }

  @Test
  void testPackagePinSerialVersionUidMatchesPinnedBaseline() {
    assertEquals(
        -7159316874351447008L,
        ObjectStreamClass.lookup(Package.Pin.class).getSerialVersionUID(),
        "Package.Pin serialVersionUID must match baseline v2.5.0");
  }

  @Test
  void testComponentSerializationRoundTrip() throws Exception {
    Component cmp =
        new Component(
            "C1", new FloatPoint(1234.56, 7890.12), 45.0, true, null, null, 1, false, "CAP-0603");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream outputStream = new ObjectOutputStream(baos)) {
      outputStream.writeObject(cmp);
    }

    try (ObjectInputStream inputStream =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      Component reloaded = (Component) inputStream.readObject();
      assertNotNull(reloaded);
      assertEquals("C1", reloaded.name);
      assertNotNull(reloaded.getExactLocation());
      assertEquals(1234.56, reloaded.getExactLocation().x, 1e-6);
      assertEquals(7890.12, reloaded.getExactLocation().y, 1e-6);
      assertEquals(new IntPoint(1235, 7890), reloaded.getLocation());
      assertEquals(45.0, reloaded.getRotationInDegree(), 1e-6);
      assertTrue(reloaded.placedOnFront());
    }
  }

  @Test
  void testPackagePinSerializationRoundTrip() throws Exception {
    Package.Pin pin = new Package.Pin("1", 10, new FloatPoint(15.25, -20.75), 90.0);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream outputStream = new ObjectOutputStream(baos)) {
      outputStream.writeObject(pin);
    }

    try (ObjectInputStream inputStream =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      Package.Pin reloaded = (Package.Pin) inputStream.readObject();
      assertNotNull(reloaded);
      assertEquals("1", reloaded.name);
      assertEquals(10, reloaded.padstackId);
      assertNotNull(reloaded.getExactRelativeLocation());
      assertEquals(15.25, reloaded.getExactRelativeLocation().x, 1e-6);
      assertEquals(-20.75, reloaded.getExactRelativeLocation().y, 1e-6);
      assertEquals(new IntVector(15, -21), reloaded.relativeLocation);
      assertEquals(90.0, reloaded.rotationInDegree, 1e-6);
    }
  }

  @Test
  void testLegacyComponentMigrationWhenExactLocationNull() throws Exception {
    // Construct component with integer location
    Component cmp =
        new Component("R1", new IntPoint(1000, 2000), 0.0, true, null, null, 2, false, "RES-0805");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream outputStream = new ObjectOutputStream(baos)) {
      outputStream.writeObject(cmp);
    }

    try (ObjectInputStream inputStream =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      Component reloaded = (Component) inputStream.readObject();
      assertNotNull(reloaded);
      assertEquals(new IntPoint(1000, 2000), reloaded.getLocation());
      assertNotNull(reloaded.getExactLocation());
      assertEquals(1000.0, reloaded.getExactLocation().x, 1e-6);
      assertEquals(2000.0, reloaded.getExactLocation().y, 1e-6);
    }
  }

  @Test
  void testLegacyPackagePinFallbackWhenExactRelativeLocationNull() {
    Package.Pin legacyPin = new Package.Pin("2", 5, (Vector) new IntVector(500, -300), 0.0);
    assertNotNull(legacyPin.getExactRelativeLocation());
    assertEquals(500.0, legacyPin.getExactRelativeLocation().x, 1e-6);
    assertEquals(-300.0, legacyPin.getExactRelativeLocation().y, 1e-6);
  }
}
