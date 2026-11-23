using System.Collections.Generic;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Ses
{
    public class SesWire
    {
        public string NetName { get; set; }
        public string LayerName { get; set; }
        public int Width { get; set; }
        public List<Point2D> Vertices { get; set; }

        public SesWire(string netName, string layerName, int width)
        {
            NetName = netName;
            LayerName = layerName;
            Width = width;
            Vertices = new List<Point2D>();
        }
    }

    public class SesVia
    {
        public string NetName { get; set; }
        public string PadstackName { get; set; }
        public int X { get; set; }
        public int Y { get; set; }
        public int Diameter { get; set; }
        public int HoleSize { get; set; }
        public string LowLayerName { get; set; }
        public string HighLayerName { get; set; }

        public SesVia(string netName, string padstackName, int x, int y)
        {
            NetName = netName;
            PadstackName = padstackName;
            X = x;
            Y = y;
        }
    }

    public class SesResult
    {
        public string SessionName { get; set; }
        public string BaseDesignName { get; set; }
        public List<SesWire> Wires { get; set; }
        public List<SesVia> Vias { get; set; }

        public SesResult()
        {
            Wires = new List<SesWire>();
            Vias = new List<SesVia>();
        }
    }
}
