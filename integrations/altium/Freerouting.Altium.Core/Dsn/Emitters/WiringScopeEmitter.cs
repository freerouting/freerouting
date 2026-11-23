using System.Text;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn.Emitters
{
    public class WiringScopeEmitter : IScopeEmitter
    {
        public string ScopeName
        {
            get { return "wiring"; }
        }

        public void EmitScope(IndentStreamWriter writer, BoardModel model)
        {
            if (model == null) return;
            if ((model.PreExistingWires == null || model.PreExistingWires.Count == 0) &&
                (model.PreExistingVias == null || model.PreExistingVias.Count == 0))
            {
                return;
            }

            writer.StartScope("wiring");

            // 1. Pre-existing routed wires
            if (model.PreExistingWires != null)
            {
                foreach (var wire in model.PreExistingWires)
                {
                    if (wire.Vertices == null || wire.Vertices.Count < 2) continue;

                    var sb = new StringBuilder();
                    sb.Append(string.Format("(wire (path {0} {1}",
                        IndentStreamWriter.QuoteIdentifier(wire.LayerName),
                        IndentStreamWriter.FormatCoord(wire.Width)));

                    foreach (var pt in wire.Vertices)
                    {
                        sb.Append(" ");
                        sb.Append(IndentStreamWriter.FormatCoord(pt.X));
                        sb.Append(" ");
                        sb.Append(IndentStreamWriter.FormatCoord(pt.Y));
                    }
                    sb.Append(")");

                    if (!string.IsNullOrEmpty(wire.NetName))
                    {
                        sb.Append(string.Format(" (net {0})", IndentStreamWriter.QuoteIdentifier(wire.NetName)));
                    }

                    sb.Append(" (type fix)");
                    sb.Append(")");
                    writer.WriteLine(sb.ToString());
                }
            }

            // 2. Pre-existing routed vias
            if (model.PreExistingVias != null)
            {
                foreach (var via in model.PreExistingVias)
                {
                    string padstack = string.IsNullOrEmpty(via.PadstackName) ? "Via_24_12" : via.PadstackName;
                    var sb = new StringBuilder();
                    sb.Append(string.Format("(via {0} {1} {2}",
                        IndentStreamWriter.QuoteIdentifier(padstack),
                        IndentStreamWriter.FormatCoord(via.Location.X),
                        IndentStreamWriter.FormatCoord(via.Location.Y)));

                    if (!string.IsNullOrEmpty(via.NetName))
                    {
                        sb.Append(string.Format(" (net {0})", IndentStreamWriter.QuoteIdentifier(via.NetName)));
                    }

                    sb.Append(" (type fix)");
                    sb.Append(")");
                    writer.WriteLine(sb.ToString());
                }
            }

            writer.EndScope();
        }
    }
}
