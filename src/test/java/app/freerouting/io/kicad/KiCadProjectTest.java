package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The minimum track width read from a KiCad project file. */
class KiCadProjectTest {

  private static String project(String rules) {
    return "{\"board\": {\"design_settings\": {\"rules\": {"
        + rules
        + "}}}, \"meta\": {\"filename\": \"board.kicad_pro\", \"version\": 3}}";
  }

  @Test
  void readsTheMinimumTrackWidthInMicrometres() {
    assertEquals(
        200.0,
        KiCadProject.parse(project("\"min_track_width\": 0.2"), "board.kicad_pro")
            .minTrackWidthUm());
    // 6 mil, as KiCad writes it
    assertEquals(
        152.4,
        KiCadProject.parse(
                project("\"min_clearance\": 0.0, \"min_track_width\": 0.1524"), "board.kicad_pro")
            .minTrackWidthUm(),
        1e-9);
  }

  @Test
  void noPositiveMinimumMeansNone() {
    assertNull(KiCadProject.parse(project("\"min_track_width\": 0.0"), "p").minTrackWidthUm());
    assertNull(KiCadProject.parse(project("\"min_clearance\": 0.2"), "p").minTrackWidthUm());
    assertNull(KiCadProject.parse("{\"meta\": {\"version\": 3}}", "p").minTrackWidthUm());
    assertNull(KiCadProject.parse(project("\"min_track_width\": \"0.2\""), "p").minTrackWidthUm());
  }

  @Test
  void aFileThatIsNotAProjectIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> KiCadProject.parse("(rules)", "p"));
    assertThrows(IllegalArgumentException.class, () -> KiCadProject.parse("[1, 2]", "p"));
  }
}
