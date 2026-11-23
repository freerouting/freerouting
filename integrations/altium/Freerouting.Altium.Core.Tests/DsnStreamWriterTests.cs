using System;
using System.IO;
using Freerouting.Altium.Core.Dsn;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Tests
{
    public static class DsnStreamWriterTests
    {
        public static void RunAll()
        {
            VerifyLosslessIntegerScaling();
            VerifyRotatedSmdPadPolygon();
            VerifySpecialCharacterEscaping();
            VerifyBoardCutoutsAsKeepouts();
            VerifyCourtyardAndDrillDiameterAndRules();
            VerifyClosedBoundaryPath();
            VerifyNetClassesAndWiringFix();
            VerifyPlacementEmitsPartNumber();
            VerifyBoardBoundaryKeepoutFiltered();
        }

        public static void VerifyCourtyardAndDrillDiameterAndRules()
        {
            var model = new BoardModel("AdvancedRulesBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));
            model.Layers.Add(new LayerInfo("BottomLayer", LayerType.Signal, 2));

            // Image with courtyard
            var img = new ComponentImage("SOIC8");
            img.HasOutline = true;
            img.OutlineMin = new Point2D(-1000000, -500000);
            img.OutlineMax = new Point2D(1000000, 500000);
            img.HasCourtyard = true;
            img.CourtyardMin = img.OutlineMin;
            img.CourtyardMax = img.OutlineMax;
            model.Images.Add(img);

            // Through-hole via padstack with drill diameter
            var viaPs = new PadstackInfo("Via_240000_120000", 120000, true);
            viaPs.Shapes.Add(new PadstackShape("TopLayer", PadShapeKind.Round, 240000, 240000));
            viaPs.Shapes.Add(new PadstackShape("BottomLayer", PadShapeKind.Round, 240000, 240000));
            model.Padstacks.Add(viaPs);

            // Board-level keepout
            model.BoardKeepouts.Add(new KeepoutArea("pcb", new Point2D(500000, 500000), new Point2D(1500000, 1500000), false));

            // Design rules
            model.Rules.DefaultClearance = 100000;
            model.Rules.DefaultWidth = 100000;
            model.Rules.BoardOutlineClearance = 250000;
            model.Rules.HoleToHoleClearance = 120000;
            model.Rules.DefaultViaPadstack = "Via_240000_120000";

            // Net
            var net = new NetInfo("VCC");
            net.PinReferences.Add("U1-8");
            model.Nets.Add(net);

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN emits thin closed outline path for courtyard", dsn.Contains("(outline (path signal 0 -100.0000 -50.0000"));
            Assert("DSN does NOT emit solid via_keepout blocking pads", !dsn.Contains("(via_keepout (rect signal"));
            Assert("DSN emits padstack outer shape circle", dsn.Contains("(shape (circle TopLayer 24.0000))"));
            Assert("DSN emits padstack attach on", dsn.Contains("(attach on)"));
            Assert("DSN emits padstack freerouting_drill_diameter", dsn.Contains("(freerouting_drill_diameter 12.0000)"));
            Assert("DSN emits board keepout in structure", dsn.Contains("(rect signal 50.0000 50.0000 150.0000 150.0000)"));
            Assert("DSN structure contains proper clearance rules", dsn.Contains("(clearance 10.0000 (type default_clearance))") && dsn.Contains("(clearance 10.0000 (type wire_to_wire))"));
            Assert("DSN structure contains board_outline clearance", dsn.Contains("(clearance 25.0000 (type board_outline))"));
            Assert("DSN structure contains hole_to_hole clearance", dsn.Contains("(clearance 12.0000 (type hole_to_hole))"));
            Assert("DSN class contains explicit clearance rule", dsn.Contains("(class default") && dsn.Contains("(clearance 10.0000)"));
        }

        public static void VerifyLosslessIntegerScaling()
        {
            string c1 = IndentStreamWriter.FormatCoord(12500);
            string c2 = IndentStreamWriter.FormatCoord(-35000);
            string c3 = IndentStreamWriter.FormatCoord(-100);
            string c4 = IndentStreamWriter.FormatCoord(0);

            Assert("FormatCoord 12500 == 1.2500", c1 == "1.2500");
            Assert("FormatCoord -35000 == -3.5000", c2 == "-3.5000");
            Assert("FormatCoord -100 == -0.0100", c3 == "-0.0100");
            Assert("FormatCoord 0 == 0.0000", c4 == "0.0000");
        }

        public static void VerifyRotatedSmdPadPolygon()
        {
            var model = new BoardModel("RotatedPadBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));

            var padstack = new PadstackInfo("SMD_Rot45", 0, false);
            var shape = new PadstackShape("TopLayer", PadShapeKind.Rectangular, 600000, 200000, 45.0);
            padstack.Shapes.Add(shape);
            model.Padstacks.Add(padstack);

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN emits polygon for rotated pad", dsn.Contains("(polygon TopLayer 0"));
            Assert("DSN does NOT emit rect for rotated pad", !dsn.Contains("(rect TopLayer"));
        }

        public static void VerifySpecialCharacterEscaping()
        {
            var model = new BoardModel("SpecialNetBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));

            var net1 = new NetInfo("CAN_H/P(1)");
            net1.PinReferences.Add("U1-1");
            model.Nets.Add(net1);

            var net2 = new NetInfo("GND+3V3");
            net2.PinReferences.Add("U1-2");
            model.Nets.Add(net2);

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN quotes net with parentheses and slashes", dsn.Contains("(net \"CAN_H/P(1)\""));
            Assert("DSN quotes net with plus sign", dsn.Contains("(net \"GND+3V3\""));
        }

        public static void VerifyBoardCutoutsAsKeepouts()
        {
            var model = new BoardModel("BoardWithCutout");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));

            // Boundary
            model.BoundaryVertices.Add(new Point2D(0, 0));
            model.BoundaryVertices.Add(new Point2D(10000000, 0));
            model.BoundaryVertices.Add(new Point2D(10000000, 10000000));
            model.BoundaryVertices.Add(new Point2D(0, 10000000));

            // Cutout
            var cutout = new System.Collections.Generic.List<Point2D>();
            cutout.Add(new Point2D(2000000, 2000000));
            cutout.Add(new Point2D(4000000, 2000000));
            cutout.Add(new Point2D(4000000, 4000000));
            cutout.Add(new Point2D(2000000, 4000000));
            model.Cutouts.Add(cutout);

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN emits boundary path", dsn.Contains("(boundary"));
            Assert("DSN emits cutout keepout polygon", dsn.Contains("(keepout") && dsn.Contains("(polygon signal 0 200.0000 200.0000"));
        }

        public static void VerifyClosedBoundaryPath()
        {
            var model = new BoardModel("ClosedBoundaryBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));

            // Add 4 non-repeating vertices
            model.BoundaryVertices.Add(new Point2D(1000000, 2000000));
            model.BoundaryVertices.Add(new Point2D(5000000, 2000000));
            model.BoundaryVertices.Add(new Point2D(5000000, 6000000));
            model.BoundaryVertices.Add(new Point2D(1000000, 6000000));

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            // Must end with first point repeated (100.0000 200.0000)
            Assert("DSN boundary guarantees closed polygon", dsn.Contains("(path pcb 0 100.0000 200.0000 500.0000 200.0000 500.0000 600.0000 100.0000 600.0000 100.0000 200.0000)"));
        }

        public static void VerifyNetClassesAndWiringFix()
        {
            var model = new BoardModel("NetClassWiringBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));
            model.Layers.Add(new LayerInfo("BottomLayer", LayerType.Signal, 2));

            // Net Class
            var powerClass = new NetClassInfo("Power", 150000, 250000);
            powerClass.NetNames.Add("VCC");
            powerClass.NetNames.Add("GND");
            model.NetClasses.Add(powerClass);

            var netVcc = new NetInfo("VCC");
            netVcc.PinReferences.Add("U1-8");
            model.Nets.Add(netVcc);

            var netGnd = new NetInfo("GND");
            netGnd.PinReferences.Add("U1-1");
            model.Nets.Add(netGnd);

            var netSig = new NetInfo("SignalNet");
            netSig.PinReferences.Add("U1-2");
            model.Nets.Add(netSig);

            // Pre-existing wire and via
            var wire = new WireModel("VCC", "TopLayer", 250000, true);
            wire.Vertices.Add(new Point2D(100000, 100000));
            wire.Vertices.Add(new Point2D(500000, 100000));
            model.PreExistingWires.Add(wire);

            var via = new ViaModel("GND", "Via_24_12", new Point2D(300000, 300000), true);
            model.PreExistingVias.Add(via);

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN emits explicit Net Class Power", dsn.Contains("(class Power"));
            Assert("DSN emits class-specific width and clearance", dsn.Contains("(width 25.0000)") && dsn.Contains("(clearance 15.0000)"));
            Assert("DSN emits wiring scope", dsn.Contains("(wiring"));
            Assert("DSN pre-existing wire is locked with type fix", dsn.Contains("(wire (path TopLayer 25.0000 10.0000 10.0000 50.0000 10.0000) (net VCC) (type fix))"));
            Assert("DSN pre-existing via is locked with type fix", dsn.Contains("(via Via_24_12 30.0000 30.0000 (net GND) (type fix))"));
        }

        public static void VerifyPlacementEmitsPartNumber()
        {
            var model = new BoardModel("PlacementPnBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));
            var img = new ComponentImage("DIP8");
            model.Images.Add(img);
            model.Components.Add(new PlacedComponent("U1", "DIP8", new Point2D(1000000, 2000000), 0.0, "front"));

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN placement emits component with (pn <Designator>)", dsn.Contains("(place U1 100.0000 200.0000 front 0 (pn U1) (lock_type position))"));
        }

        public static void VerifyBoardBoundaryKeepoutFiltered()
        {
            var model = new BoardModel("BoundaryKeepoutBoard");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));

            // Boundary rectangle 100 to 500
            model.BoundaryVertices.Add(new Point2D(1000000, 1000000));
            model.BoundaryVertices.Add(new Point2D(5000000, 1000000));
            model.BoundaryVertices.Add(new Point2D(5000000, 4000000));
            model.BoundaryVertices.Add(new Point2D(1000000, 4000000));

            // Keepout 1: Matches board boundary exactly (should be filtered out)
            model.BoardKeepouts.Add(new KeepoutArea("signal", new Point2D(1000000, 1000000), new Point2D(5000000, 4000000), false));

            // Keepout 2: Genuine interior keepout (should NOT be filtered)
            model.BoardKeepouts.Add(new KeepoutArea("signal", new Point2D(2000000, 2000000), new Point2D(3000000, 3000000), false));

            // Cutout 1: Matches board boundary exactly (should be filtered out)
            var fullCutout = new System.Collections.Generic.List<Point2D>
            {
                new Point2D(1000000, 1000000),
                new Point2D(5000000, 1000000),
                new Point2D(5000000, 4000000),
                new Point2D(1000000, 4000000)
            };
            model.Cutouts.Add(fullCutout);

            // Cutout 2: Genuine interior cutout (should NOT be filtered)
            var interiorCutout = new System.Collections.Generic.List<Point2D>
            {
                new Point2D(2500000, 2500000),
                new Point2D(3500000, 2500000),
                new Point2D(3500000, 3500000),
                new Point2D(2500000, 3500000)
            };
            model.Cutouts.Add(interiorCutout);

            var sw = new StringWriter();
            var dsnWriter = new DsnStreamWriter();
            dsnWriter.Write(model, sw);
            string dsn = sw.ToString();

            Assert("DSN does NOT emit keepout matching board boundary", !dsn.Contains("(rect signal 100.0000 100.0000 500.0000 400.0000)"));
            Assert("DSN does NOT emit cutout keepout matching board boundary", !dsn.Contains("(polygon signal 0 100.0000 100.0000 500.0000 100.0000 500.0000 400.0000 100.0000 400.0000"));
            Assert("DSN emits genuine interior keepout", dsn.Contains("(rect signal 200.0000 200.0000 300.0000 300.0000)"));
            Assert("DSN emits genuine interior cutout", dsn.Contains("250.0000 250.0000 350.0000 250.0000 350.0000 350.0000 250.0000 350.0000"));
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
