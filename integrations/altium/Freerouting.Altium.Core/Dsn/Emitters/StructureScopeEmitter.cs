using System;
using System.Collections.Generic;
using System.Text;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn.Emitters
{
    public class StructureScopeEmitter : IScopeEmitter
    {
        public string ScopeName
        {
            get { return "structure"; }
        }

        public void EmitScope(IndentStreamWriter writer, BoardModel model)
        {
            writer.StartScope("structure");

            // 1. Layer Definitions
            foreach (var layer in model.Layers)
            {
                string layerTypeStr = layer.Type == LayerType.Signal ? "signal" : "power";
                writer.WriteLine(string.Format("(layer {0} (type {1}))", IndentStreamWriter.QuoteIdentifier(layer.Name), layerTypeStr));
            }

            // 2. Board Boundary
            if (model.BoundaryVertices != null && model.BoundaryVertices.Count > 0)
            {
                writer.StartScope("boundary");
                var sb = new StringBuilder();
                sb.Append("(path pcb 0");
                foreach (var pt in model.BoundaryVertices)
                {
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(pt.X));
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(pt.Y));
                }

                // Guarantee closed boundary polygon
                var first = model.BoundaryVertices[0];
                var last = model.BoundaryVertices[model.BoundaryVertices.Count - 1];
                if (first.X != last.X || first.Y != last.Y)
                {
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(first.X));
                    sb.Append(" ");
                    sb.Append(IndentStreamWriter.FormatCoord(first.Y));
                }

                sb.Append(")");
                writer.WriteLine(sb.ToString());
                writer.EndScope();
            }

            // 3. Board Cutouts as Keepouts
            if (model.Cutouts != null)
            {
                foreach (var cutout in model.Cutouts)
                {
                    if (cutout.Count < 3) continue;
                    if (IsBoardBoundaryPolygon(cutout, model.BoundaryVertices)) continue;
                    writer.StartScope("keepout");
                    var sb = new StringBuilder();
                    sb.Append("(polygon signal 0");
                    foreach (var pt in cutout)
                    {
                        sb.Append(" ");
                        sb.Append(IndentStreamWriter.FormatCoord(pt.X));
                        sb.Append(" ");
                        sb.Append(IndentStreamWriter.FormatCoord(pt.Y));
                    }
                    sb.Append(")");
                    writer.WriteLine(sb.ToString());
                    writer.EndScope();
                }
            }

            // 4. Board Keepouts & Via Keepouts
            if (model.BoardKeepouts != null)
            {
                foreach (var ko in model.BoardKeepouts)
                {
                    if (IsBoardBoundaryKeepout(ko, model.BoundaryVertices))
                    {
                        continue;
                    }

                    string scopeKind = ko.IsViaKeepoutOnly ? "via_keepout" : "keepout";
                    writer.StartScope(scopeKind);
                    string targetLayer = (string.IsNullOrEmpty(ko.LayerName) || string.Equals(ko.LayerName, "pcb", StringComparison.OrdinalIgnoreCase)) ? "signal" : ko.LayerName;
                    string layerStr = IndentStreamWriter.QuoteIdentifier(targetLayer);
                    if (ko.IsRect)
                    {
                        writer.WriteLine(string.Format("(rect {0} {1} {2} {3} {4})",
                            layerStr,
                            IndentStreamWriter.FormatCoord(ko.RectMin.X),
                            IndentStreamWriter.FormatCoord(ko.RectMin.Y),
                            IndentStreamWriter.FormatCoord(ko.RectMax.X),
                            IndentStreamWriter.FormatCoord(ko.RectMax.Y)));
                    }
                    else if (ko.PolygonVertices != null && ko.PolygonVertices.Count >= 3)
                    {
                        var sb = new StringBuilder();
                        sb.Append(string.Format("(polygon {0} 0", layerStr));
                        foreach (var pt in ko.PolygonVertices)
                        {
                            sb.Append(" ");
                            sb.Append(IndentStreamWriter.FormatCoord(pt.X));
                            sb.Append(" ");
                            sb.Append(IndentStreamWriter.FormatCoord(pt.Y));
                        }
                        sb.Append(")");
                        writer.WriteLine(sb.ToString());
                    }
                    writer.EndScope();
                }
            }

            // 5. Default Rules (Authoritative clearance rules)
            if (model.Rules != null)
            {
                writer.StartScope("rule");
                writer.WriteLine(string.Format("(width {0})", IndentStreamWriter.FormatCoord(model.Rules.DefaultWidth)));
                writer.WriteLine(string.Format("(clearance {0})", IndentStreamWriter.FormatCoord(model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type default_clearance))", IndentStreamWriter.FormatCoord(model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type smd_smd))", IndentStreamWriter.FormatCoord(model.Rules.SmdToSmdClearance > 0 ? model.Rules.SmdToSmdClearance : model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type smd_to_turn_gap))", IndentStreamWriter.FormatCoord(model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type smd_to_via_gap))", IndentStreamWriter.FormatCoord(model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type wire_to_wire))", IndentStreamWriter.FormatCoord(model.Rules.WireToWireClearance > 0 ? model.Rules.WireToWireClearance : model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type wire_to_via))", IndentStreamWriter.FormatCoord(model.Rules.WireToViaClearance > 0 ? model.Rules.WireToViaClearance : model.Rules.DefaultClearance)));
                writer.WriteLine(string.Format("(clearance {0} (type via_to_via))", IndentStreamWriter.FormatCoord(model.Rules.ViaToViaClearance > 0 ? model.Rules.ViaToViaClearance : model.Rules.DefaultClearance)));
                if (model.Rules.BoardOutlineClearance > 0)
                {
                    writer.WriteLine(string.Format("(clearance {0} (type board_outline))", IndentStreamWriter.FormatCoord(model.Rules.BoardOutlineClearance)));
                }
                if (model.Rules.HoleToHoleClearance > 0)
                {
                    writer.WriteLine(string.Format("(clearance {0} (type hole_to_hole))", IndentStreamWriter.FormatCoord(model.Rules.HoleToHoleClearance)));
                }
                writer.EndScope();

                // 5. Available Vias declared in structure scope
                var viaNames = new List<string>();
                if (model.Rules.AvailableViaPadstacks != null && model.Rules.AvailableViaPadstacks.Count > 0)
                {
                    viaNames.AddRange(model.Rules.AvailableViaPadstacks);
                }
                else if (!string.IsNullOrEmpty(model.Rules.DefaultViaPadstack))
                {
                    viaNames.Add(model.Rules.DefaultViaPadstack);
                }

                if (viaNames.Count > 0)
                {
                    var sb = new StringBuilder();
                    sb.Append("(via");
                    foreach (var v in viaNames)
                    {
                        sb.Append(" ");
                        sb.Append(IndentStreamWriter.QuoteIdentifier(v));
                    }
                    sb.Append(")");
                    writer.WriteLine(sb.ToString());
                }
            }

            writer.EndScope();
        }

        private static bool IsBoardBoundaryPolygon(List<Point2D> pts, List<Point2D> boundaryVertices)
        {
            if (boundaryVertices == null || boundaryVertices.Count < 3 || pts == null || pts.Count < 3) return false;

            int bMinX = int.MaxValue, bMaxX = int.MinValue, bMinY = int.MaxValue, bMaxY = int.MinValue;
            foreach (var p in boundaryVertices)
            {
                if (p.X < bMinX) bMinX = p.X;
                if (p.X > bMaxX) bMaxX = p.X;
                if (p.Y < bMinY) bMinY = p.Y;
                if (p.Y > bMaxY) bMaxY = p.Y;
            }

            int kMinX = int.MaxValue, kMaxX = int.MinValue, kMinY = int.MaxValue, kMaxY = int.MinValue;
            foreach (var p in pts)
            {
                if (p.X < kMinX) kMinX = p.X;
                if (p.X > kMaxX) kMaxX = p.X;
                if (p.Y < kMinY) kMinY = p.Y;
                if (p.Y > kMaxY) kMaxY = p.Y;
            }

            const int tolerance = 500000; // 50 mils (1.27 mm)
            if ((Math.Abs(kMinX - bMinX) <= tolerance &&
                 Math.Abs(kMaxX - bMaxX) <= tolerance &&
                 Math.Abs(kMinY - bMinY) <= tolerance &&
                 Math.Abs(kMaxY - bMaxY) <= tolerance) ||
                (kMinX <= bMinX + tolerance && kMaxX >= bMaxX - tolerance &&
                 kMinY <= bMinY + tolerance && kMaxY >= bMaxY - tolerance))
            {
                return true;
            }

            return false;
        }

        private static bool IsBoardBoundaryKeepout(KeepoutArea ko, List<Point2D> boundaryVertices)
        {
            if (boundaryVertices == null || boundaryVertices.Count < 3) return false;

            if (ko.IsRect)
            {
                var rectPts = new List<Point2D>
                {
                    ko.RectMin,
                    new Point2D(ko.RectMax.X, ko.RectMin.Y),
                    ko.RectMax,
                    new Point2D(ko.RectMin.X, ko.RectMax.Y)
                };
                return IsBoardBoundaryPolygon(rectPts, boundaryVertices);
            }

            return IsBoardBoundaryPolygon(ko.PolygonVertices, boundaryVertices);
        }
    }
}
