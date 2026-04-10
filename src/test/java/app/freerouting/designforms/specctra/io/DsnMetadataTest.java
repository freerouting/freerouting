package app.freerouting.designforms.specctra.io;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DsnMetadataTest {

  @Test
  void recordCanBeConstructed() {
    DsnMetadata meta = new DsnMetadata(
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
    DsnMetadata a = new DsnMetadata("EAGLE", "9.6", 2, Unit.MIL, 3937, AngleRestriction.NINETY_DEGREE, null);
    DsnMetadata b = new DsnMetadata("EAGLE", "9.6", 2, Unit.MIL, 3937, AngleRestriction.NINETY_DEGREE, null);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void toStringContainsFieldValues() {
    DsnMetadata meta = new DsnMetadata("KiCad", "8.0", 4, Unit.MM, 1000, AngleRestriction.FORTYFIVE_DEGREE, null);
    String str = meta.toString();

    // Java record toString() includes the field names and values
    assert str.contains("KiCad");
    assert str.contains("4");
  }
}

