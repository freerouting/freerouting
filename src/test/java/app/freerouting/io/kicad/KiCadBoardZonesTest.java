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

  @Test
  void readsThePadConnectionOfTheZonesAndThePads() {
    KiCadBoardZones board =
        KiCadBoardZones.parse(
            "(kicad_pcb (version 20171130) (host pcbnew 5.1)\n"
                + " (module Lib:R (layer F.Cu) (tedit 0) (at 100 50 90) (zone_connect 2)\n"
                + "  (fp_text reference R1 (at 3 3) (layer F.SilkS))\n"
                + "  (pad 1 thru_hole circle (at 2 0 90) (size 1.6 1.6) (drill 0.8)"
                + " (layers *.Cu *.Mask) (net 1 GND))\n"
                + "  (pad 2 smd rect (at -2 0 90) (size 1 2) (drill (offset 0.5 0))"
                + " (layers F.Cu F.Paste) (net 2 SIG) (zone_connect 1) (thermal_width 0.3)"
                + " (thermal_gap 0.4) (thermal_bridge_angle 45)))\n"
                + " (zone (net 1) (net_name GND) (layer F.Cu) (connect_pads thru_hole_only"
                + " (clearance 0.3)) (min_thickness 0.2)\n"
                + "  (fill yes (thermal_gap 0.6) (thermal_bridge_width 0.7))\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10))))\n"
                + " (zone (net 1) (net_name GND) (layer B.Cu) (connect_pads no (clearance 0.3))\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10))))\n"
                + " (zone (net 1) (net_name GND) (layer In1.Cu) (connect_pads yes)\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10))))\n"
                + " (zone (net 1) (net_name GND) (layer In2.Cu) (connect_pads (clearance 0.3))\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10)))))",
            "pads.kicad_pcb");

    assertEquals(KiCadBoardZones.PadConnection.THT_THERMAL, board.zones().get(0).padConnection());
    assertEquals(600.0, board.zones().get(0).thermalGapUm(), 1e-9);
    assertEquals(700.0, board.zones().get(0).thermalBridgeWidthUm(), 1e-9);
    assertEquals(KiCadBoardZones.PadConnection.NONE, board.zones().get(1).padConnection());
    assertEquals(KiCadBoardZones.PadConnection.SOLID, board.zones().get(2).padConnection());
    assertEquals(KiCadBoardZones.PadConnection.THERMAL, board.zones().get(3).padConnection());

    assertEquals(2, board.pads().size());
    // the footprint is turned by 90 degrees: its x axis points up on screen (y down)
    KiCadBoardZones.Pad first = board.pads().get(0);
    assertEquals(100000.0, first.xUm(), 1e-6);
    assertEquals(48000.0, first.yUm(), 1e-6);
    assertTrue(first.circular());
    assertTrue(first.throughHole());
    assertEquals(45.0, first.spokeAngleDeg(), 1e-9, "the default of KiCad for a round pad");
    assertEquals(
        KiCadBoardZones.PadConnection.SOLID, first.connection(), "inherited from the footprint");
    assertTrue(first.isOnLayer("B.Cu"));

    KiCadBoardZones.Pad second = board.pads().get(1);
    assertEquals(100000.0, second.xUm(), 1e-6);
    assertEquals(52000.0, second.yUm(), 1e-6);
    // the offset of 0.5 mm along the x axis of the pad, itself turned by 90 degrees
    assertEquals(100000.0, second.shapeXUm(), 1e-6);
    assertEquals(51500.0, second.shapeYUm(), 1e-6);
    assertEquals(1000.0, second.sizeXUm(), 1e-9);
    assertEquals(2000.0, second.sizeYUm(), 1e-9);
    assertFalse(second.circular());
    assertFalse(second.throughHole());
    assertEquals(KiCadBoardZones.PadConnection.THERMAL, second.connection());
    assertEquals(400.0, second.thermalGapUm(), 1e-9);
    assertEquals(300.0, second.thermalBridgeWidthUm(), 1e-9);
    assertEquals(45.0, second.spokeAngleDeg(), 1e-9);
    assertFalse(second.isOnLayer("B.Cu"));
  }

  @Test
  void readsTheCornerSmoothingOfTheZones() {
    KiCadBoardZones board =
        KiCadBoardZones.parse(
            "(kicad_pcb (version 20171130) (host pcbnew 5.1)\n"
                + " (zone (net 1) (net_name GND) (layer F.Cu)\n"
                + "  (fill yes (arc_segments 16) (thermal_gap 0.5) (thermal_bridge_width 0.5)"
                + " (smoothing fillet) (radius 1))\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10))))\n"
                + " (zone (net 1) (net_name GND) (layer B.Cu)\n"
                + "  (fill yes (smoothing chamfer) (radius 0.3))\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10))))\n"
                + " (zone (net 1) (net_name GND) (layer In1.Cu) (fill yes (smoothing none))\n"
                + "  (polygon (pts (xy 0 0) (xy 10 0) (xy 10 10)))))",
            "corners.kicad_pcb");

    assertEquals(KiCadBoardZones.CornerSmoothing.FILLET, board.zones().get(0).cornerSmoothing());
    assertEquals(1000.0, board.zones().get(0).cornerRadiusUm(), 1e-9);
    assertEquals(500.0, board.zones().get(0).thermalGapUm(), 1e-9);
    assertEquals(KiCadBoardZones.CornerSmoothing.CHAMFER, board.zones().get(1).cornerSmoothing());
    assertEquals(300.0, board.zones().get(1).cornerRadiusUm(), 1e-9);
    assertEquals(KiCadBoardZones.CornerSmoothing.NONE, board.zones().get(2).cornerSmoothing());
  }

  @Test
  void aThroughHolePadIsOnTheCopperLayersThatTheBoardRenamed() {
    KiCadBoardZones board =
        KiCadBoardZones.parse(
            "(kicad_pcb (version 20171130) (host pcbnew 5.1)\n"
                + " (layers (0 Top signal) (1 Inner power) (31 Bottom signal) (36 B.SilkS user))\n"
                + " (module Lib:J (layer Top) (tedit 0) (at 10 10)\n"
                + "  (pad 1 thru_hole circle (at 0 0) (size 1.6 1.6) (drill 0.8)"
                + " (layers *.Cu *.Mask B.SilkS) (net 1 GND))\n"
                + "  (pad 2 thru_hole circle (at 2 0) (size 1.6 1.6) (drill 0.8)"
                + " (layers F&B.Cu) (net 1 GND))\n"
                + "  (pad 3 smd rect (at 4 0) (size 1 1) (layers Bottom B.Paste) (net 1 GND))))",
            "renamed.kicad_pcb");

    KiCadBoardZones.Pad all = board.pads().get(0);
    assertTrue(all.isOnLayer("Top"));
    assertTrue(all.isOnLayer("Inner"));
    assertTrue(all.isOnLayer("Bottom"));
    assertFalse(all.isOnLayer("B.SilkS.Cu"));
    KiCadBoardZones.Pad outer = board.pads().get(1);
    assertTrue(outer.isOnLayer("Top"));
    assertFalse(outer.isOnLayer("Inner"));
    assertTrue(outer.isOnLayer("Bottom"));
    KiCadBoardZones.Pad back = board.pads().get(2);
    assertFalse(back.isOnLayer("Top"));
    assertTrue(back.isOnLayer("Bottom"));
  }
}
