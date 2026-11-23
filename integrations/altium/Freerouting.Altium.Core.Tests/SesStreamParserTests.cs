using System;
using System.IO;
using Freerouting.Altium.Core.Ses;

namespace Freerouting.Altium.Core.Tests
{
    public static class SesStreamParserTests
    {
        public static void RunAll()
        {
            VerifyMicroSegmentSnapping();
            VerifyMultiVertexPolyline();
            VerifyUnrecognizedScopeSkipping();
            VerifyDynamicResolutionScaling();
        }

        public static void VerifyMicroSegmentSnapping()
        {
            // Polyline with 3 vertices: (0,0) -> (10, 10) -> (5000, 5000)
            // (10, 10) is < 100 internal units distance squared from (0,0), so it snaps
            string ses = @"(session test
  (routes
    (resolution mil 10000)
    (network_out
      (net Net1
        (wire (path TopLayer 100000 0 0 10 10 5000 5000))
      )
    )
  )
)";
            var parser = new SesStreamParser();
            var res = parser.Parse(new StringReader(ses));

            Assert("Ses wires parsed", res.Wires.Count == 1);
            var wire = res.Wires[0];
            // After snapping the middle point (0.0010, 0.0010) = (10, 10), vertices should be (0,0) and (5000, 5000)
            Assert("Wire vertices count snapped to 2", wire.Vertices.Count == 2);
            Assert("First vertex is (0,0)", wire.Vertices[0].X == 0 && wire.Vertices[0].Y == 0);
            Assert("Second vertex is (5000,5000)", wire.Vertices[1].X == 5000 && wire.Vertices[1].Y == 5000);
        }

        public static void VerifyMultiVertexPolyline()
        {
            string ses = @"(session test
  (routes
    (resolution mil 10000)
    (network_out
      (net NetRoute
        (wire (path TopLayer 100000 0 0 10000 0 10000 10000 20000 10000))
      )
    )
  )
)";
            var parser = new SesStreamParser();
            var res = parser.Parse(new StringReader(ses));

            Assert("Wire parsed", res.Wires.Count == 1);
            var wire = res.Wires[0];
            Assert("4 distinct orthogonal vertices preserved", wire.Vertices.Count == 4);
            Assert("Vertex 0 is (0, 0)", wire.Vertices[0].X == 0 && wire.Vertices[0].Y == 0);
            Assert("Vertex 1 is (10000, 0)", wire.Vertices[1].X == 10000 && wire.Vertices[1].Y == 0);
            Assert("Vertex 2 is (10000, 10000)", wire.Vertices[2].X == 10000 && wire.Vertices[2].Y == 10000);
            Assert("Vertex 3 is (20000, 10000)", wire.Vertices[3].X == 20000 && wire.Vertices[3].Y == 10000);
        }

        public static void VerifyUnrecognizedScopeSkipping()
        {
            string ses = @"(session test
  (future_scope_1 (nested_key 12345 (deeper_item ""foo"")))
  (routes
    (unknown_subscope (a b c))
    (resolution mil 10000)
    (network_out
      (net Net1
        (unknown_net_property 999)
        (wire (path TopLayer 100000 0 0 10000 10000))
        (via Via1 10000 10000)
      )
    )
  )
)";
            var parser = new SesStreamParser();
            var res = parser.Parse(new StringReader(ses));

            Assert("Unrecognized scopes skipped gracefully", res.Wires.Count == 1 && res.Vias.Count == 1);
            Assert("Extracted wire on Net1", res.Wires[0].NetName == "Net1");
            Assert("Extracted via on Net1", res.Vias[0].PadstackName == "Via1");
        }

        public static void VerifyDynamicResolutionScaling()
        {
            // Case 1: um 1000 -> 1 unit = 1/1000 um = 1 nm.
            // 25.4 um = 25400 units. In Altium internal units (1 mil = 10,000 units = 25.4 um):
            // 25.4 um = 10,000 Altium units.
            string sesUm = @"(session test
  (routes
    (resolution um 1000)
    (network_out
      (net NetUm
        (via ViaUm 25400 50800)
      )
    )
  )
)";
            var parser = new SesStreamParser();
            var resUm = parser.Parse(new StringReader(sesUm));
            Assert("Via parsed from um resolution", resUm.Vias.Count == 1);
            Assert("Via X scaled accurately (25.4 um -> 10,000 units)", Math.Abs(resUm.Vias[0].X - 10000) < 5);
            Assert("Via Y scaled accurately (50.8 um -> 20,000 units)", Math.Abs(resUm.Vias[0].Y - 20000) < 5);

            // Case 2: mm 1000 -> 1 unit = 1/1000 mm = 1 um.
            // 2.54 mm = 2540 units = 0.1 inch = 100 mil = 1,000,000 Altium units.
            string sesMm = @"(session test
  (routes
    (resolution mm 1000)
    (network_out
      (net NetMm
        (via ViaMm 2540 5080)
      )
    )
  )
)";
            var resMm = parser.Parse(new StringReader(sesMm));
            Assert("Via parsed from mm resolution", resMm.Vias.Count == 1);
            Assert("Via X scaled accurately (2.54 mm -> 1,000,000 units)", Math.Abs(resMm.Vias[0].X - 1000000) < 5);
            Assert("Via Y scaled accurately (5.08 mm -> 2,000,000 units)", Math.Abs(resMm.Vias[0].Y - 2000000) < 5);
        }

        private static void Assert(string name, bool condition)
        {
            if (condition)
            {
                Console.WriteLine("  [PASS] " + name);
            }
            else
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine("  [FAIL] " + name);
                Console.ResetColor();
                throw new Exception("Assertion failed: " + name);
            }
        }
    }
}
