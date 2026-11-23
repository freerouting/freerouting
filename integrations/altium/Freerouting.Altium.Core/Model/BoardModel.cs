using System.Collections.Generic;

namespace Freerouting.Altium.Core.Model
{
    public enum LayerType
    {
        Signal,
        Plane,
        Dielectric
    }

    public class LayerInfo
    {
        public string Name { get; set; }
        public LayerType Type { get; set; }
        public int LayerNumber { get; set; }

        public LayerInfo(string name, LayerType type, int layerNumber)
        {
            Name = name;
            Type = type;
            LayerNumber = layerNumber;
        }
    }

    public enum PadShapeKind
    {
        Round,
        Rectangular,
        Octagonal,
        RoundedRect,
        Polygon
    }

    public class PadstackShape
    {
        public string LayerName { get; set; }
        public PadShapeKind Kind { get; set; }
        public int Width { get; set; }
        public int Height { get; set; }
        public double RotationDegrees { get; set; }
        public List<Point2D> PolygonVertices { get; set; }

        public PadstackShape(string layerName, PadShapeKind kind, int width, int height, double rotationDegrees = 0)
        {
            LayerName = layerName;
            Kind = kind;
            Width = width;
            Height = height;
            RotationDegrees = rotationDegrees;
            PolygonVertices = new List<Point2D>();
        }
    }

    public class PadstackInfo
    {
        public string Name { get; set; }
        public int HoleSize { get; set; }
        public bool IsThroughHole { get; set; }
        public List<PadstackShape> Shapes { get; set; }

        public PadstackInfo(string name, int holeSize, bool isThroughHole = true)
        {
            Name = name;
            HoleSize = holeSize;
            IsThroughHole = isThroughHole;
            Shapes = new List<PadstackShape>();
        }
    }

    public class ComponentPin
    {
        public string PinId { get; set; }
        public string PadstackName { get; set; }
        public Point2D RelativePosition { get; set; }
        public double RotationDegrees { get; set; }

        public ComponentPin(string pinId, string padstackName, Point2D relativePosition, double rotationDegrees = 0)
        {
            PinId = pinId;
            PadstackName = padstackName;
            RelativePosition = relativePosition;
            RotationDegrees = rotationDegrees;
        }
    }

    public class ComponentImage
    {
        public string Name { get; set; }
        public List<ComponentPin> Pins { get; set; }
        public Point2D OutlineMin { get; set; }
        public Point2D OutlineMax { get; set; }
        public bool HasOutline { get; set; }
        public Point2D CourtyardMin { get; set; }
        public Point2D CourtyardMax { get; set; }
        public bool HasCourtyard { get; set; }
        public List<List<Point2D>> OutlinePaths { get; set; }
        public List<KeepoutArea> Keepouts { get; set; }
        public List<KeepoutArea> ViaKeepouts { get; set; }

        public ComponentImage(string name)
        {
            Name = name;
            Pins = new List<ComponentPin>();
            HasOutline = false;
            HasCourtyard = false;
            OutlinePaths = new List<List<Point2D>>();
            Keepouts = new List<KeepoutArea>();
            ViaKeepouts = new List<KeepoutArea>();
        }
    }

    public class KeepoutArea
    {
        public string LayerName { get; set; } // "signal", "pcb", or layer name
        public bool IsViaKeepoutOnly { get; set; }
        public bool IsRect { get; set; }
        public Point2D RectMin { get; set; }
        public Point2D RectMax { get; set; }
        public List<Point2D> PolygonVertices { get; set; }

        public KeepoutArea(string layerName, Point2D min, Point2D max, bool isViaKeepoutOnly = false)
        {
            LayerName = layerName;
            IsRect = true;
            RectMin = min;
            RectMax = max;
            IsViaKeepoutOnly = isViaKeepoutOnly;
            PolygonVertices = new List<Point2D>();
        }

        public KeepoutArea(string layerName, List<Point2D> vertices, bool isViaKeepoutOnly = false)
        {
            LayerName = layerName;
            IsRect = false;
            PolygonVertices = vertices ?? new List<Point2D>();
            IsViaKeepoutOnly = isViaKeepoutOnly;
        }
    }

    public class WireModel
    {
        public string NetName { get; set; }
        public string LayerName { get; set; }
        public int Width { get; set; }
        public List<Point2D> Vertices { get; set; }
        public bool IsLocked { get; set; }

        public WireModel(string netName, string layerName, int width, bool isLocked = false)
        {
            NetName = netName;
            LayerName = layerName;
            Width = width;
            IsLocked = isLocked;
            Vertices = new List<Point2D>();
        }
    }

    public class ViaModel
    {
        public string NetName { get; set; }
        public string PadstackName { get; set; }
        public Point2D Location { get; set; }
        public bool IsLocked { get; set; }

        public ViaModel(string netName, string padstackName, Point2D location, bool isLocked = false)
        {
            NetName = netName;
            PadstackName = padstackName;
            Location = location;
            IsLocked = isLocked;
        }
    }

    public class PlacedComponent
    {
        public string Designator { get; set; }
        public string ImageName { get; set; }
        public Point2D Location { get; set; }
        public double RotationDegrees { get; set; }
        public string Side { get; set; } // "front" or "back"

        public PlacedComponent(string designator, string imageName, Point2D location, double rotationDegrees = 0, string side = "front")
        {
            Designator = designator;
            ImageName = imageName;
            Location = location;
            RotationDegrees = rotationDegrees;
            Side = side;
        }
    }

    public class NetInfo
    {
        public string Name { get; set; }
        public List<string> PinReferences { get; set; } // e.g. "R1-1", "U1-A2"

        public NetInfo(string name)
        {
            Name = name;
            PinReferences = new List<string>();
        }
    }

    public class DesignRules
    {
        public int DefaultClearance { get; set; }
        public int DefaultWidth { get; set; }
        public int MinWidth { get; set; }
        public int MaxWidth { get; set; }
        public int BoardOutlineClearance { get; set; }
        public int HoleToHoleClearance { get; set; }
        public int SmdToSmdClearance { get; set; }
        public int WireToWireClearance { get; set; }
        public int WireToViaClearance { get; set; }
        public int ViaToViaClearance { get; set; }
        public string DefaultViaPadstack { get; set; }
        public List<string> AvailableViaPadstacks { get; set; }

        public DesignRules()
        {
            DefaultClearance = 100000; // 10 mil default
            DefaultWidth = 100000; // 10 mil default
            MinWidth = 60000; // 6 mil default
            MaxWidth = 500000; // 50 mil default
            BoardOutlineClearance = 250000; // 25 mil default (approx 250 um)
            HoleToHoleClearance = 100000; // 10 mil default
            SmdToSmdClearance = 100000;
            WireToWireClearance = 100000;
            WireToViaClearance = 100000;
            ViaToViaClearance = 100000;
            DefaultViaPadstack = "Via_24_12";
            AvailableViaPadstacks = new List<string>();
        }
    }

    public class NetClassInfo
    {
        public string Name { get; set; }
        public List<string> NetNames { get; set; }
        public int Clearance { get; set; }
        public int Width { get; set; }
        public List<string> ViaPadstackNames { get; set; }

        public NetClassInfo(string name, int clearance = 0, int width = 0)
        {
            Name = name;
            Clearance = clearance;
            Width = width;
            NetNames = new List<string>();
            ViaPadstackNames = new List<string>();
        }
    }

    public class BoardModel
    {
        public string Name { get; set; }
        public List<Point2D> BoundaryVertices { get; set; }
        public List<List<Point2D>> Cutouts { get; set; }
        public List<KeepoutArea> BoardKeepouts { get; set; }
        public List<LayerInfo> Layers { get; set; }
        public List<PadstackInfo> Padstacks { get; set; }
        public List<ComponentImage> Images { get; set; }
        public List<PlacedComponent> Components { get; set; }
        public List<NetInfo> Nets { get; set; }
        public List<NetClassInfo> NetClasses { get; set; }
        public List<WireModel> PreExistingWires { get; set; }
        public List<ViaModel> PreExistingVias { get; set; }
        public DesignRules Rules { get; set; }
        public IDictionary<string, object> ExtensibleProperties { get; set; }

        public BoardModel(string name = "AltiumBoard")
        {
            Name = name;
            BoundaryVertices = new List<Point2D>();
            Cutouts = new List<List<Point2D>>();
            BoardKeepouts = new List<KeepoutArea>();
            Layers = new List<LayerInfo>();
            Padstacks = new List<PadstackInfo>();
            Images = new List<ComponentImage>();
            Components = new List<PlacedComponent>();
            Nets = new List<NetInfo>();
            NetClasses = new List<NetClassInfo>();
            PreExistingWires = new List<WireModel>();
            PreExistingVias = new List<ViaModel>();
            Rules = new DesignRules();
            ExtensibleProperties = new Dictionary<string, object>();
        }
    }
}
