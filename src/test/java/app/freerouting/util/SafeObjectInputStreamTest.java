package app.freerouting.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafeObjectInputStreamTest {

  @Test
  void allowsPermittedJdkAndDomainClasses() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(new Point(10, 20));
      oos.writeObject(new ArrayList<>(List.of("test", "data")));
    }

    try (SafeObjectInputStream sois =
        new SafeObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      Point p = (Point) sois.readObject();
      assertNotNull(p);
      assertEquals(10, p.x);
      assertEquals(20, p.y);

      @SuppressWarnings("unchecked")
      List<String> list = (List<String>) sois.readObject();
      assertEquals(2, list.size());
      assertEquals("test", list.get(0));
    }
  }

  @Test
  void rejectsUnapprovedClassesViaFilter() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(new javax.management.ObjectName("com.example:type=Test"));
    }

    try (SafeObjectInputStream sois =
        new SafeObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      assertThrows(InvalidClassException.class, sois::readObject);
    }
  }
}
