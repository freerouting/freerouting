using System;
using System.IO;
using System.Text;
using Freerouting.Altium.Core.Dsn;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Tests
{
    public static class FixtureGenerator
    {
        public static BoardModel CreateTwoLayerBenchmarkModel()
        {
            var model = new BoardModel("Altium-TwoLayerBenchmark");
            model.Layers.Add(new LayerInfo("TopLayer", LayerType.Signal, 1));
            model.Layers.Add(new LayerInfo("BottomLayer", LayerType.Signal, 2));

            // Outline 1000 x 1000 mil (10,000,000 x 10,000,000 internal units)
            model.BoundaryVertices.Add(new Point2D(0, 0));
            model.BoundaryVertices.Add(new Point2D(10000000, 0));
            model.BoundaryVertices.Add(new Point2D(10000000, 10000000));
            model.BoundaryVertices.Add(new Point2D(0, 10000000));

            // Default rules
            model.Rules.DefaultWidth = 100000;         // 10 mil
            model.Rules.DefaultClearance = 100000;     // 10 mil
            model.Rules.BoardOutlineClearance = 250000; // 25 mil
            model.Rules.DefaultViaPadstack = "Via_24_12";

            // Padstacks
            var viaStack = new PadstackInfo("Via_24_12", 120000, true);
            viaStack.Shapes.Add(new PadstackShape("TopLayer", PadShapeKind.Round, 240000, 240000, 0.0));
            viaStack.Shapes.Add(new PadstackShape("BottomLayer", PadShapeKind.Round, 240000, 240000, 0.0));
            model.Padstacks.Add(viaStack);

            var dipPad = new PadstackInfo("DIP_PAD", 300000, true);
            dipPad.Shapes.Add(new PadstackShape("TopLayer", PadShapeKind.Round, 600000, 600000, 0.0));
            dipPad.Shapes.Add(new PadstackShape("BottomLayer", PadShapeKind.Round, 600000, 600000, 0.0));
            model.Padstacks.Add(dipPad);

            var smdPad = new PadstackInfo("SMD_PAD", 0, false);
            smdPad.Shapes.Add(new PadstackShape("TopLayer", PadShapeKind.Rectangular, 400000, 500000, 0.0));
            model.Padstacks.Add(smdPad);

            var smdRotPad = new PadstackInfo("SMD_ROT_PAD", 0, false);
            smdRotPad.Shapes.Add(new PadstackShape("TopLayer", PadShapeKind.Rectangular, 400000, 500000, 45.0));
            model.Padstacks.Add(smdRotPad);

            // Component Images
            var dip8 = new ComponentImage("DIP8");
            dip8.Pins.Add(new ComponentPin("1", "DIP_PAD", new Point2D(0, 0)));
            dip8.Pins.Add(new ComponentPin("2", "DIP_PAD", new Point2D(1000000, 0)));
            dip8.Pins.Add(new ComponentPin("3", "DIP_PAD", new Point2D(2000000, 0)));
            dip8.Pins.Add(new ComponentPin("4", "DIP_PAD", new Point2D(3000000, 0)));
            dip8.Pins.Add(new ComponentPin("5", "DIP_PAD", new Point2D(3000000, 3000000)));
            dip8.Pins.Add(new ComponentPin("6", "DIP_PAD", new Point2D(2000000, 3000000)));
            dip8.Pins.Add(new ComponentPin("7", "DIP_PAD", new Point2D(1000000, 3000000)));
            dip8.Pins.Add(new ComponentPin("8", "DIP_PAD", new Point2D(0, 3000000)));
            model.Images.Add(dip8);

            var r0805 = new ComponentImage("R0805");
            r0805.Pins.Add(new ComponentPin("1", "SMD_PAD", new Point2D(-400000, 0)));
            r0805.Pins.Add(new ComponentPin("2", "SMD_PAD", new Point2D(400000, 0)));
            model.Images.Add(r0805);

            var r0805Rot = new ComponentImage("R0805_Rot");
            r0805Rot.Pins.Add(new ComponentPin("1", "SMD_ROT_PAD", new Point2D(-400000, 0)));
            r0805Rot.Pins.Add(new ComponentPin("2", "SMD_ROT_PAD", new Point2D(400000, 0)));
            model.Images.Add(r0805Rot);

            // Placements
            model.Components.Add(new PlacedComponent("U1", "DIP8", new Point2D(2000000, 3000000), 0.0, "front"));
            model.Components.Add(new PlacedComponent("R1", "R0805", new Point2D(7000000, 2000000), 0.0, "front"));
            model.Components.Add(new PlacedComponent("R2", "R0805", new Point2D(7000000, 4000000), 0.0, "front"));
            model.Components.Add(new PlacedComponent("R3", "R0805_Rot", new Point2D(7000000, 6000000), 0.0, "front"));

            // Nets
            var net1 = new NetInfo("Net_U1_1_R1_1");
            net1.PinReferences.Add("U1-1");
            net1.PinReferences.Add("R1-1");
            model.Nets.Add(net1);

            var net2 = new NetInfo("Net_U1_2_R2_1");
            net2.PinReferences.Add("U1-2");
            net2.PinReferences.Add("R2-1");
            model.Nets.Add(net2);

            var net3 = new NetInfo("Net_U1_3_R3_1");
            net3.PinReferences.Add("U1-3");
            net3.PinReferences.Add("R3-1");
            model.Nets.Add(net3);

            return model;
        }

        public static void GenerateFixtureFile(string outputPath)
        {
            var model = CreateTwoLayerBenchmarkModel();
            var writer = new DsnStreamWriter();
            using (var sw = new StreamWriter(outputPath, false, Encoding.ASCII))
            {
                writer.Write(model, sw);
            }
        }
    }
}
