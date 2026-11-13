#!/usr/bin/env python3
"""Zone grafting for PCBench unrouted boards (issue #1122)."""

from __future__ import annotations

import unittest

from strip_kicad_routing import graft_zones, strip_routing

PROCESSED = """(kicad_pcb (version 20171130) (host pcbnew "(5.1.12)-1")
  (net 0 "")
  (net 1 "Net-(D1-Pad2)")
  (net 2 GND)
  (module R_0805 (layer F.Cu) (at 10 10)
    (pad 1 smd rect (at 0 0) (size 1 1) (layers F.Cu) (net 2 GND)))
  (segment (start 0 0) (end 1 1) (width 0.25) (layer F.Cu) (net 2))
)
"""

RAW = """(kicad_pcb (version 20171130) (host pcbnew "(5.1.12)-1")
  (net 0 "")
  (net 1 GND)
  (net 2 "Net-(D1-Pad2)")
  (net 3 NC_ONLY_IN_RAW)
  (zone (net 1) (net_name GND) (layer B.Cu) (tstamp 0) (hatch edge 0.508)
    (polygon (pts (xy 0 0) (xy 20 0) (xy 20 20) (xy 0 20)))
    (filled_polygon (pts (xy 1 1) (xy 19 1) (xy 19 19) (xy 1 19))))
  (zone (net 2) (net_name "Net-(D1-Pad2)") (layer F.Cu) (tstamp 0) (hatch edge 0.508)
    (polygon (pts (xy 0 0) (xy 5 0) (xy 5 5))))
  (zone (net 0) (net_name "") (layer F.Cu) (tstamp 0) (hatch edge 0.508)
    (keepout (tracks not_allowed) (vias not_allowed) (copperpour not_allowed))
    (polygon (pts (xy 30 30) (xy 31 30) (xy 31 31))))
  (zone (net 3) (net_name NC_ONLY_IN_RAW) (layer F.Cu) (tstamp 0) (hatch edge 0.508)
    (polygon (pts (xy 40 40) (xy 41 40) (xy 41 41))))
)
"""


def zones(text: str) -> list[str]:
    return [line.strip() for line in text.splitlines() if line.lstrip().startswith("(zone ")]


class GraftZonesTest(unittest.TestCase):
    def test_zones_of_raw_are_grafted_with_the_net_codes_of_processed(self) -> None:
        out, grafted, dropped = graft_zones(PROCESSED, RAW)
        self.assertEqual(3, grafted)
        self.assertEqual(1, dropped)
        self.assertEqual(
            [
                "(zone (net 2) (net_name GND) (layer B.Cu) (tstamp 0) (hatch edge 0.508)",
                '(zone (net 1) (net_name "Net-(D1-Pad2)") (layer F.Cu) (tstamp 0) (hatch edge 0.508)',
                '(zone (net 0) (net_name "") (layer F.Cu) (tstamp 0) (hatch edge 0.508)',
            ],
            zones(out),
        )
        self.assertNotIn("NC_ONLY_IN_RAW", out)

    def test_everything_else_in_processed_is_kept(self) -> None:
        out, _grafted, _dropped = graft_zones(PROCESSED, RAW)
        self.assertTrue(out.startswith(PROCESSED.rstrip()[:-1].rstrip()))
        self.assertEqual(0, out.count("(") - out.count(")"))
        self.assertTrue(out.rstrip().endswith(")"))

    def test_a_board_that_already_has_zones_is_left_alone(self) -> None:
        with_zone, _g, _d = graft_zones(PROCESSED, RAW)
        again, grafted, dropped = graft_zones(with_zone, RAW)
        self.assertEqual(with_zone, again)
        self.assertEqual((0, 0), (grafted, dropped))

    def test_a_raw_board_without_zones_changes_nothing(self) -> None:
        out, grafted, dropped = graft_zones(PROCESSED, PROCESSED)
        self.assertEqual(PROCESSED, out)
        self.assertEqual((0, 0), (grafted, dropped))

    def test_stripping_keeps_the_grafted_outlines_and_drops_their_fill(self) -> None:
        out, _g, _d = graft_zones(PROCESSED, RAW)
        stripped = strip_routing(out)
        self.assertEqual(3, len(zones(stripped)))
        self.assertNotIn("filled_polygon", stripped)
        self.assertNotIn("(segment", stripped)
        self.assertIn("(polygon (pts (xy 0 0) (xy 20 0) (xy 20 20) (xy 0 20)))", stripped)


if __name__ == "__main__":
    unittest.main()
