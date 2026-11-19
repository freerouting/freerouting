package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The fill settings of the zones read from a KiCad board file. */
class KiCadBoardZonesTest {

  @Test
  void readsTheClearanceAndTheMinimumWidthOfAKiCad10Zone() {
    KiCadBoardZones board =
        KiCadBoardZones.parse(
            "(kicad_pcb (version 20260206) (generator \"pcbnew\")\n"
                + " (footprint \"R_0603\" (layer \"F.Cu\") (at 10 10)\n"
                + "  (pad \"1\" smd rect (at 0 0) (size 1 1) (layers \"F.Cu\") (net \"GND\")))\n"
                + " (zone (net \"GND\") (layers \"F.Cu\" \"B.Cu\") (uuid \"a\") (hatch edge 0.5)\n"
                + "  (connect_pads yes (clearance 0.5)) (min_thickness 0.25)\n"
                + "  (fill yes (thermal_gap 0.5) (thermal_bridge_width 0.5))\n"
                + "  (polygon (pts (xy 58.42 82.55) (xy 146.05 82.55) (xy 146.05 171.45)"
                + " (xy 58.42 171.45)))\n"
                + "  (filled_polygon (layer \"F.Cu\") (pts (xy 0 0) (xy 500 0) (xy 500 500)))))",
            "board.kicad_pcb");

    assertEquals(1, board.zones().size());
    KiCadBoardZones.Zone zone = board.zones().getFirst();
    assertEquals("GND", zone.netName());
    assertEquals(List.of("F.Cu", "B.Cu"), zone.layers());
    assertEquals(500.0, zone.clearanceUm(), 1e-9);
    assertEquals(250.0, zone.minThicknessUm(), 1e-9);
    // the outline only: the fill polygon is not part of the bounding box
    assertEquals(58420.0, zone.minXUm(), 1e-6);
    assertEquals(82550.0, zone.minYUm(), 1e-6);
    assertEquals(146050.0, zone.maxXUm(), 1e-6);
    assertEquals(171450.0, zone.maxYUm(), 1e-6);
    assertTrue(zone.isOnLayer("B.Cu"));
    assertFalse(zone.isOnLayer("In1.Cu"));
  }

  @Test
  void readsAKiCad5ZoneWithANetNumberAndAnUnquotedName() {
    KiCadBoardZones board =
        KiCadBoardZones.parse(
            "(kicad_pcb (version 4) (host pcbnew 4.0.2)\n"
                + "  (zone (net 2) (net_name GND) (layer B.Cu) (tstamp 0) (hatch edge 0.508)\n"
                + "    (connect_pads (clearance 0.508))\n"
                + "    (min_thickness 0.254)\n"
                + "    (fill yes (arc_segments 16) (thermal_gap 0.508)"
                + " (thermal_bridge_width 0.508))\n"
                + "    (polygon (pts (xy 193.04 148.59) (xy 193.04 82.55) (xy 123.19 82.55)"
                + " (xy 123.19 148.59)))))",
            "old.kicad_pcb");

    KiCadBoardZones.Zone zone = board.zones().getFirst();
    assertEquals("GND", zone.netName());
    assertEquals(List.of("B.Cu"), zone.layers());
    assertEquals(508.0, zone.clearanceUm(), 1e-9);
    assertEquals(254.0, zone.minThicknessUm(), 1e-9);
  }

  @Test
  void ruleAreasAreLeftOutAndLayerWildcardsAreUnderstood() {
    KiCadBoardZones board =
        KiCadBoardZones.parse(
            "(kicad_pcb (version 20260206)\n"
                + " (zone (net \"\") (layers \"F.Cu\") (name \"no (tracks) here\")\n"
                + "  (keepout (tracks not_allowed) (vias allowed) (pads allowed)"
                + " (copperpour allowed) (footprints allowed))\n"
                + "  (polygon (pts (xy 0 0) (xy 1 0) (xy 1 1))))\n"
                + " (zone (net \"+3V3\") (layers \"F&B.Cu\") (connect_pads (clearance 0.3))\n"
                + "  (min_thickness 0.2) (polygon (pts (xy 0 0) (xy 1 0) (xy 1 1))))\n"
                + " (zone (net \"GND\") (layers \"*.Cu\") (connect_pads no (clearance 0))\n"
                + "  (min_thickness 0.2) (polygon (pts (xy 0 0) (xy 1 0) (xy 1 1)))))",
            "board.kicad_pcb");

    assertEquals(2, board.zones().size());
    KiCadBoardZones.Zone outer = board.zones().get(0);
    assertEquals("+3V3", outer.netName());
    assertTrue(outer.isOnLayer("F.Cu"));
    assertTrue(outer.isOnLayer("B.Cu"));
    assertFalse(outer.isOnLayer("In1.Cu"));
    KiCadBoardZones.Zone all = board.zones().get(1);
    assertTrue(all.isOnLayer("In2.Cu"));
    assertEquals(0.0, all.clearanceUm(), 1e-9);
  }

  @Test
  void aFileThatIsNotAKiCadBoardIsRefused() {
    assertThrows(
        IllegalArgumentException.class, () -> KiCadBoardZones.parse("(pcb \"x.dsn\")", "x.dsn"));
    assertThrows(
        IllegalArgumentException.class,
        () -> KiCadBoardZones.parse("(kicad_pcb (zone (net \"GND\")", "cut.kicad_pcb"));
  }
}
