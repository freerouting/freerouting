using System;
using System.Collections.Generic;
using System.IO;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Ses
{
    public class SesStreamParser
    {
        private const int MinSegmentLengthUnitsSquared = 100 * 100; // 100 internal units = 0.01 mil

        public SesResult Parse(TextReader reader)
        {
            if (reader == null) throw new ArgumentNullException("reader");

            var result = new SesResult();
            var tokens = Tokenize(reader);

            int index = 0;
            // Parse S-expression tree
            while (index < tokens.Count)
            {
                if (tokens[index] == "(")
                {
                    index++;
                    if (index < tokens.Count && tokens[index].Equals("session", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        if (index < tokens.Count && tokens[index] != "(" && tokens[index] != ")")
                        {
                            result.SessionName = tokens[index++];
                        }
                        ParseSessionBody(tokens, ref index, result);
                    }
                    else
                    {
                        SkipScope(tokens, ref index);
                    }
                }
                else
                {
                    index++;
                }
            }

            return result;
        }

        private void ParseSessionBody(List<string> tokens, ref int index, SesResult result)
        {
            double scaleFactor = 1.0; // Default resolution scale

            while (index < tokens.Count && tokens[index] != ")")
            {
                if (tokens[index] == "(")
                {
                    index++;
                    if (index >= tokens.Count) break;

                    string scopeName = tokens[index];
                    if (scopeName.Equals("base_design", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        if (index < tokens.Count && tokens[index] != ")")
                        {
                            result.BaseDesignName = tokens[index++];
                        }
                        SkipUntilScopeEnd(tokens, ref index);
                    }
                    else if (scopeName.Equals("routes", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        ParseRoutesScope(tokens, ref index, result, ref scaleFactor);
                    }
                    else
                    {
                        SkipScope(tokens, ref index);
                    }
                }
                else
                {
                    index++;
                }
            }

            if (index < tokens.Count && tokens[index] == ")")
            {
                index++;
            }
        }

        private void ParseRoutesScope(List<string> tokens, ref int index, SesResult result, ref double scaleFactor)
        {
            while (index < tokens.Count && tokens[index] != ")")
            {
                if (tokens[index] == "(")
                {
                    index++;
                    if (index >= tokens.Count) break;

                    string scopeName = tokens[index];
                    if (scopeName.Equals("resolution", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        // e.g. resolution mil 10000 or resolution um 1000
                        if (index + 1 < tokens.Count)
                        {
                            string unit = tokens[index++];
                            double res;
                            if (double.TryParse(tokens[index++], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out res))
                            {
                                double altiumUnitsPerUnit = 10000.0;
                                if (unit.Equals("mil", StringComparison.OrdinalIgnoreCase))
                                {
                                    altiumUnitsPerUnit = 10000.0;
                                }
                                else if (unit.Equals("um", StringComparison.OrdinalIgnoreCase))
                                {
                                    // 1 mil = 25.4 um => 10,000 Altium units = 25.4 um => 1 um = 10000.0 / 25.4 Altium units
                                    altiumUnitsPerUnit = 10000.0 / 25.4;
                                }
                                else if (unit.Equals("mm", StringComparison.OrdinalIgnoreCase))
                                {
                                    // 1 mm = 1000 um => 1 mm = 10000000.0 / 25.4 Altium units (~393700.787 units)
                                    altiumUnitsPerUnit = 10000000.0 / 25.4;
                                }
                                else if (unit.Equals("inch", StringComparison.OrdinalIgnoreCase))
                                {
                                    // 1 inch = 1000 mils = 10,000,000 Altium units
                                    altiumUnitsPerUnit = 10000000.0;
                                }

                                scaleFactor = res > 0 ? (altiumUnitsPerUnit / res) : 1.0;
                            }
                        }
                        SkipUntilScopeEnd(tokens, ref index);
                    }
                    else if (scopeName.Equals("network_out", StringComparison.OrdinalIgnoreCase) ||
                             scopeName.Equals("net", StringComparison.OrdinalIgnoreCase))
                    {
                        if (scopeName.Equals("network_out", StringComparison.OrdinalIgnoreCase))
                        {
                            index++;
                            ParseNetworkOut(tokens, ref index, result, scaleFactor);
                        }
                        else
                        {
                            ParseSingleNet(tokens, ref index, result, scaleFactor);
                        }
                    }
                    else
                    {
                        SkipScope(tokens, ref index);
                    }
                }
                else
                {
                    index++;
                }
            }

            if (index < tokens.Count && tokens[index] == ")")
            {
                index++;
            }
        }

        private void ParseNetworkOut(List<string> tokens, ref int index, SesResult result, double scaleFactor)
        {
            while (index < tokens.Count && tokens[index] != ")")
            {
                if (tokens[index] == "(")
                {
                    index++;
                    if (index < tokens.Count && tokens[index].Equals("net", StringComparison.OrdinalIgnoreCase))
                    {
                        ParseSingleNet(tokens, ref index, result, scaleFactor);
                    }
                    else
                    {
                        SkipScope(tokens, ref index);
                    }
                }
                else
                {
                    index++;
                }
            }
            if (index < tokens.Count && tokens[index] == ")")
            {
                index++;
            }
        }

        private void ParseSingleNet(List<string> tokens, ref int index, SesResult result, double scaleFactor)
        {
            // index is at "net"
            index++;
            string netName = "";
            if (index < tokens.Count && tokens[index] != "(" && tokens[index] != ")")
            {
                netName = tokens[index++];
            }

            while (index < tokens.Count && tokens[index] != ")")
            {
                if (tokens[index] == "(")
                {
                    index++;
                    if (index >= tokens.Count) break;

                    string itemType = tokens[index];
                    if (itemType.Equals("wire", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        ParseWire(tokens, ref index, netName, result, scaleFactor);
                    }
                    else if (itemType.Equals("via", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        ParseVia(tokens, ref index, netName, result, scaleFactor);
                    }
                    else
                    {
                        SkipScope(tokens, ref index);
                    }
                }
                else
                {
                    index++;
                }
            }

            if (index < tokens.Count && tokens[index] == ")")
            {
                index++;
            }
        }

        private void ParseWire(List<string> tokens, ref int index, string netName, SesResult result, double scaleFactor)
        {
            // Wire contains (path <layer> <width> <x1> <y1> ...)
            while (index < tokens.Count && tokens[index] != ")")
            {
                if (tokens[index] == "(")
                {
                    index++;
                    if (index < tokens.Count && tokens[index].Equals("path", StringComparison.OrdinalIgnoreCase))
                    {
                        index++;
                        if (index + 1 < tokens.Count)
                        {
                            string layerName = tokens[index++];
                            int width = ScaleCoord(tokens[index++], scaleFactor);

                            var wire = new SesWire(netName, layerName, width);
                            var rawPoints = new List<Point2D>();

                            while (index + 1 < tokens.Count && tokens[index] != ")")
                            {
                                int x = ScaleCoord(tokens[index++], scaleFactor);
                                int y = ScaleCoord(tokens[index++], scaleFactor);
                                rawPoints.Add(new Point2D(x, y));
                            }

                            // Perform geometric vertex snapping to remove micro-segments
                            wire.Vertices = SnapVertices(rawPoints);
                            if (wire.Vertices.Count >= 2)
                            {
                                result.Wires.Add(wire);
                            }
                        }
                    }
                    SkipUntilScopeEnd(tokens, ref index);
                }
                else
                {
                    index++;
                }
            }
            if (index < tokens.Count && tokens[index] == ")") index++;
        }

        private void ParseVia(List<string> tokens, ref int index, string netName, SesResult result, double scaleFactor)
        {
            // Via format: <padstack_name> <x> <y>
            if (index + 2 < tokens.Count && tokens[index] != ")")
            {
                string padstack = tokens[index++];
                int x = ScaleCoord(tokens[index++], scaleFactor);
                int y = ScaleCoord(tokens[index++], scaleFactor);
                var via = new SesVia(netName, padstack, x, y);
                InferViaDimensions(via);
                result.Vias.Add(via);
            }
            SkipUntilScopeEnd(tokens, ref index);
        }

        private static void InferViaDimensions(SesVia via)
        {
            if (via == null || string.IsNullOrEmpty(via.PadstackName)) return;

            string name = via.PadstackName;

            // Pattern 1: Via_{width}_{hole}
            if (name.StartsWith("Via_", StringComparison.OrdinalIgnoreCase))
            {
                string[] parts = name.Substring(4).Split('_');
                if (parts.Length >= 2)
                {
                    int w, h;
                    if (int.TryParse(parts[0], out w) && int.TryParse(parts[1], out h))
                    {
                        if (w < 1000 && h < 1000)
                        {
                            w *= 10000;
                            h *= 10000;
                        }
                        via.Diameter = w;
                        via.HoleSize = h;
                        return;
                    }
                }
            }

            // Pattern 2: Via..._{width}:{hole}_um (Freerouting generated via padstack)
            int colonIdx = name.IndexOf(':');
            if (colonIdx > 0)
            {
                int underscoreIdx = name.LastIndexOf('_', colonIdx);
                if (underscoreIdx >= 0)
                {
                    string wStr = name.Substring(underscoreIdx + 1, colonIdx - underscoreIdx - 1);
                    int umIdx = name.IndexOf("_um", colonIdx, StringComparison.OrdinalIgnoreCase);
                    string hStr = umIdx > colonIdx ? name.Substring(colonIdx + 1, umIdx - colonIdx - 1) : name.Substring(colonIdx + 1);

                    double wUm, hUm;
                    if (double.TryParse(wStr, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out wUm) &&
                        double.TryParse(hStr, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out hUm))
                    {
                        // 1 um = 10000 / 25.4 internal units (~393.7 units)
                        via.Diameter = (int)Math.Round(wUm * 10000.0 / 25.4);
                        via.HoleSize = (int)Math.Round(hUm * 10000.0 / 25.4);
                        return;
                    }
                }
            }

            // Safe fallback: standard 24 mil pad / 12 mil drill
            if (via.Diameter <= 0) via.Diameter = 240000;
            if (via.HoleSize <= 0) via.HoleSize = 120000;
        }

        public static List<Point2D> SnapVertices(List<Point2D> raw)
        {
            if (raw == null || raw.Count <= 1) return raw ?? new List<Point2D>();

            var snapped = new List<Point2D> { raw[0] };
            for (int i = 1; i < raw.Count; i++)
            {
                Point2D prev = snapped[snapped.Count - 1];
                Point2D curr = raw[i];

                // If micro-segment, collapse into prev unless it's the very last terminating point
                if (prev.DistanceSquaredTo(curr) < MinSegmentLengthUnitsSquared && i < raw.Count - 1)
                {
                    continue;
                }

                snapped.Add(curr);
            }

            return snapped;
        }

        private int ScaleCoord(string token, double scale)
        {
            double val;
            if (double.TryParse(token, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out val))
            {
                return (int)Math.Round(val * scale);
            }
            return 0;
        }

        private void SkipScope(List<string> tokens, ref int index)
        {
            int depth = 1;
            while (index < tokens.Count && depth > 0)
            {
                if (tokens[index] == "(") depth++;
                else if (tokens[index] == ")") depth--;
                index++;
            }
        }

        private void SkipUntilScopeEnd(List<string> tokens, ref int index)
        {
            while (index < tokens.Count && tokens[index] != ")")
            {
                if (tokens[index] == "(")
                {
                    index++;
                    SkipScope(tokens, ref index);
                }
                else
                {
                    index++;
                }
            }
            if (index < tokens.Count && tokens[index] == ")") index++;
        }

        private List<string> Tokenize(TextReader reader)
        {
            var tokens = new List<string>();
            var sb = new System.Text.StringBuilder();

            int c;
            while ((c = reader.Read()) != -1)
            {
                char ch = (char)c;
                if (char.IsWhiteSpace(ch))
                {
                    FlushToken(tokens, sb);
                }
                else if (ch == '(' || ch == ')')
                {
                    FlushToken(tokens, sb);
                    tokens.Add(ch.ToString());
                }
                else if (ch == '"')
                {
                    FlushToken(tokens, sb);
                    // Read quoted token
                    while ((c = reader.Read()) != -1)
                    {
                        char qc = (char)c;
                        if (qc == '"') break;
                        sb.Append(qc);
                    }
                    tokens.Add(sb.ToString());
                    sb.Clear();
                }
                else
                {
                    sb.Append(ch);
                }
            }
            FlushToken(tokens, sb);
            return tokens;
        }

        private void FlushToken(List<string> tokens, System.Text.StringBuilder sb)
        {
            if (sb.Length > 0)
            {
                tokens.Add(sb.ToString());
                sb.Clear();
            }
        }
    }
}
