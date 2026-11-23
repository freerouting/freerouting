using System;
using System.Text;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn.Emitters
{
    public class LibraryScopeEmitter : IScopeEmitter
    {
        public string ScopeName
        {
            get { return "library"; }
        }

        public void EmitScope(IndentStreamWriter writer, BoardModel model)
        {
            writer.StartScope("library");

            // 1. Component Images (Pins, outline, courtyard via-keepout, and padstack mappings)
            foreach (var img in model.Images)
            {
                writer.StartScope(string.Format("image {0}", IndentStreamWriter.QuoteIdentifier(img.Name)));
                // Emit closed courtyard/outline boundary paths as thin graphical strokes (100 um / 3937 units)
                // matching KiCad Specctra DSN output and rendering clean courtyard boundaries without obscuring pins.
                if (img.OutlinePaths != null && img.OutlinePaths.Count > 0)
                {
                    foreach (var path in img.OutlinePaths)
                    {
                        if (path == null || path.Count < 2) continue;
                        var sb = new StringBuilder();
                        sb.Append("(outline (path signal 0");
                        foreach (var pt in path)
                        {
                            sb.Append(" ");
                            sb.Append(IndentStreamWriter.FormatCoord(pt.X));
                            sb.Append(" ");
                            sb.Append(IndentStreamWriter.FormatCoord(pt.Y));
                        }
                        if (path[0].X != path[path.Count - 1].X || path[0].Y != path[path.Count - 1].Y)
                        {
                            sb.Append(" ");
                            sb.Append(IndentStreamWriter.FormatCoord(path[0].X));
                            sb.Append(" ");
                            sb.Append(IndentStreamWriter.FormatCoord(path[0].Y));
                        }
                        sb.Append("))");
                        writer.WriteLine(sb.ToString());
                    }
                }
                else if (img.HasCourtyard || img.HasOutline)
                {
                    var min = img.HasCourtyard ? img.CourtyardMin : img.OutlineMin;
                    var max = img.HasCourtyard ? img.CourtyardMax : img.OutlineMax;
                    writer.WriteLine(string.Format("(outline (path signal 0 {0} {1} {2} {1} {2} {3} {0} {3} {0} {1}))",
                        IndentStreamWriter.FormatCoord(min.X),
                        IndentStreamWriter.FormatCoord(min.Y),
                        IndentStreamWriter.FormatCoord(max.X),
                        IndentStreamWriter.FormatCoord(max.Y)));
                }
                if (img.Keepouts != null)
                {
                    foreach (var ko in img.Keepouts)
                    {
                        EmitImageKeepout(writer, ko, "keepout");
                    }
                }
                if (img.ViaKeepouts != null)
                {
                    foreach (var vko in img.ViaKeepouts)
                    {
                        EmitImageKeepout(writer, vko, "via_keepout");
                    }
                }
                foreach (var pin in img.Pins)
                {
                    writer.WriteLine(string.Format("(pin {0} {1} {2} {3})",
                        IndentStreamWriter.QuoteIdentifier(pin.PadstackName),
                        IndentStreamWriter.QuoteIdentifier(pin.PinId),
                        IndentStreamWriter.FormatCoord(pin.RelativePosition.X),
                        IndentStreamWriter.FormatCoord(pin.RelativePosition.Y)));
                }
                writer.EndScope();
            }

            // 2. Padstacks
            foreach (var ps in model.Padstacks)
            {
                writer.StartScope(string.Format("padstack {0}", IndentStreamWriter.QuoteIdentifier(ps.Name)));
                foreach (var shape in ps.Shapes)
                {
                    EmitShape(writer, shape);
                }
                if (ps.IsThroughHole && ps.HoleSize > 0)
                {
                    writer.WriteLine("(attach on)");
                    writer.WriteLine(string.Format("(freerouting_drill_diameter {0})", IndentStreamWriter.FormatCoord(ps.HoleSize)));
                }
                writer.EndScope();
            }

            writer.EndScope();
        }

        private void EmitImageKeepout(IndentStreamWriter writer, KeepoutArea ko, string scopeKeyword)
        {
            if (ko == null) return;
            string layerStr = IndentStreamWriter.QuoteIdentifier(ko.LayerName ?? "signal");
            if (ko.IsRect)
            {
                writer.WriteLine(string.Format("({0} (rect {1} {2} {3} {4} {5}))",
                    scopeKeyword,
                    layerStr,
                    IndentStreamWriter.FormatCoord(ko.RectMin.X),
                    IndentStreamWriter.FormatCoord(ko.RectMin.Y),
                    IndentStreamWriter.FormatCoord(ko.RectMax.X),
                    IndentStreamWriter.FormatCoord(ko.RectMax.Y)));
            }
            else if (ko.PolygonVertices != null && ko.PolygonVertices.Count >= 3)
            {
                var sb = new StringBuilder();
                sb.Append(string.Format("({0} (polygon {1} 0", scopeKeyword, layerStr));
                foreach (var pt in ko.PolygonVertices)
                {
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(pt.X));
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(pt.Y));
                }
                sb.Append("))");
                writer.WriteLine(sb.ToString());
            }
        }

        private void EmitShape(IndentStreamWriter writer, PadstackShape shape)
        {
            string layerStr = IndentStreamWriter.QuoteIdentifier(shape.LayerName);

            if (shape.Kind == PadShapeKind.Round)
            {
                writer.WriteLine(string.Format("(shape (circle {0} {1}))",
                    layerStr, IndentStreamWriter.FormatCoord(shape.Width)));
            }
            else if (shape.Kind == PadShapeKind.Rectangular && Math.Abs(shape.RotationDegrees) < 0.01)
            {
                int halfW = shape.Width / 2;
                int halfH = shape.Height / 2;
                writer.WriteLine(string.Format("(shape (rect {0} {1} {2} {3} {4}))",
                    layerStr,
                    IndentStreamWriter.FormatCoord(-halfW),
                    IndentStreamWriter.FormatCoord(-halfH),
                    IndentStreamWriter.FormatCoord(halfW),
                    IndentStreamWriter.FormatCoord(halfH)));
            }
            else if (shape.Kind == PadShapeKind.Rectangular && Math.Abs(shape.RotationDegrees) >= 0.01)
            {
                // Rotated rectangle emitted as polygon since Specctra rect has no rotation
                double rad = shape.RotationDegrees * Math.PI / 180.0;
                double cos = Math.Cos(rad);
                double sin = Math.Sin(rad);

                double hw = shape.Width / 2.0;
                double hh = shape.Height / 2.0;

                // 4 corners: (-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)
                Point2D p1 = RotatePoint(-hw, -hh, cos, sin);
                Point2D p2 = RotatePoint(hw, -hh, cos, sin);
                Point2D p3 = RotatePoint(hw, hh, cos, sin);
                Point2D p4 = RotatePoint(-hw, hh, cos, sin);

                writer.WriteLine(string.Format("(shape (polygon {0} 0 {1} {2} {3} {4} {5} {6} {7} {8}))",
                    layerStr,
                    IndentStreamWriter.FormatCoord(p1.X), IndentStreamWriter.FormatCoord(p1.Y),
                    IndentStreamWriter.FormatCoord(p2.X), IndentStreamWriter.FormatCoord(p2.Y),
                    IndentStreamWriter.FormatCoord(p3.X), IndentStreamWriter.FormatCoord(p3.Y),
                    IndentStreamWriter.FormatCoord(p4.X), IndentStreamWriter.FormatCoord(p4.Y)));
            }
            else if (shape.Kind == PadShapeKind.Polygon && shape.PolygonVertices != null && shape.PolygonVertices.Count >= 3)
            {
                var sb = new StringBuilder();
                sb.Append(string.Format("(shape (polygon {0} 0", layerStr));
                foreach (var v in shape.PolygonVertices)
                {
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(v.X));
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(v.Y));
                }
                sb.Append("))");
                writer.WriteLine(sb.ToString());
            }
            else
            {
                // Fallback to circle
                writer.WriteLine(string.Format("(shape (circle {0} {1}))",
                    layerStr, IndentStreamWriter.FormatCoord(shape.Width)));
            }
        }

        private Point2D RotatePoint(double x, double y, double cos, double sin)
        {
            int rx = (int)Math.Round(x * cos - y * sin);
            int ry = (int)Math.Round(x * sin + y * cos);
            return new Point2D(rx, ry);
        }
    }
}
