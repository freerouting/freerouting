package app.freerouting.io;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardMetadataTest {

  @Test
  void recordCanBeConstructed() {
    BoardMetadata meta = new BoardMetadata(
        "KiCad", "8.0", 4, Unit.MM, 1000,
        AngleRestriction.FORTYFIVE_DEGREE, null);

    assertEquals("KiCad", meta.hostCad());
    assertEquals("8.0", meta.hostVersion());
    assertEquals(4, meta.layerCount());
    assertEquals(Unit.MM, meta.unit());
    assertEquals(1000, meta.resolution());
    assertEquals(AngleRestriction.FORTYFIVE_DEGREE, meta.snapAngle());
    assertNull(meta.routerSettings());
  }

  @Test
  void recordEquality() {
    BoardMetadata a = new BoardMetadata("EAGLE", "9.6", 2, Unit.MIL, 3937, AngleRestriction.NINETY_DEGREE, null);
    BoardMetadata b = new BoardMetadata("EAGLE", "9.6", 2, Unit.MIL, 3937, AngleRestriction.NINETY_DEGREE, null);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    BoardMetadata meta = new BoardMetadata("KiCad", "8.0", 4, Unit.MM, 1000, AngleRestriction.FORTYFIVE_DEGREE, null);
    String str = meta.toString();

    // Java record toString() includes the field names and values
    assertTrue(str.contains("KiCad"), "toString should contain host CAD");
    assertTrue(str.contains("4"), "toString should contain layer count");
  }
}