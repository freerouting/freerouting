using System.Collections.Generic;
using System.Text;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn.Emitters
{
    public class NetworkScopeEmitter : IScopeEmitter
    {
        public string ScopeName
        {
            get { return "network"; }
        }

        public void EmitScope(IndentStreamWriter writer, BoardModel model)
        {
            writer.StartScope("network");

            // 1. Emit each net and its pin references
            foreach (var net in model.Nets)
            {
                writer.StartScope(string.Format("net {0}", IndentStreamWriter.QuoteIdentifier(net.Name)));

                var sb = new StringBuilder();
                sb.Append("(pins");
                foreach (var pinRef in net.PinReferences)
                {
                    sb.Append(" ");
                    sb.Append(pinRef); // Pin references are formatted as Component-Pin (e.g. U1-1) without quotes
                }
                sb.Append(")");
                writer.WriteLine(sb.ToString());

                writer.EndScope();
            }

            // 2. Net Class definitions
            var assignedNets = new HashSet<string>(System.StringComparer.OrdinalIgnoreCase);

            var defaultViaNames = new List<string>();
            if (model.Rules != null && model.Rules.AvailableViaPadstacks != null && model.Rules.AvailableViaPadstacks.Count > 0)
            {
                defaultViaNames.AddRange(model.Rules.AvailableViaPadstacks);
            }
            else if (model.Rules != null && !string.IsNullOrEmpty(model.Rules.DefaultViaPadstack))
            {
                defaultViaNames.Add(model.Rules.DefaultViaPadstack);
            }

            if (model.NetClasses != null)
            {
                foreach (var nc in model.NetClasses)
                {
                    if (nc.NetNames == null || nc.NetNames.Count == 0) continue;

                    writer.StartScope(string.Format("class {0}", IndentStreamWriter.QuoteIdentifier(nc.Name)));
                    var sb = new StringBuilder();
                    bool first = true;
                    foreach (var netName in nc.NetNames)
                    {
                        if (!first) sb.Append(" ");
                        sb.Append(IndentStreamWriter.QuoteIdentifier(netName));
                        first = false;
                        assignedNets.Add(netName);
                    }
                    writer.WriteLine(sb.ToString());

                    writer.StartScope("rule");
                    int w = nc.Width > 0 ? nc.Width : (model.Rules != null ? model.Rules.DefaultWidth : 100000);
                    int cl = nc.Clearance > 0 ? nc.Clearance : (model.Rules != null ? model.Rules.DefaultClearance : 100000);
                    writer.WriteLine(string.Format("(width {0})", IndentStreamWriter.FormatCoord(w)));
                    writer.WriteLine(string.Format("(clearance {0})", IndentStreamWriter.FormatCoord(cl)));
                    writer.EndScope();

                    var vias = (nc.ViaPadstackNames != null && nc.ViaPadstackNames.Count > 0) ? nc.ViaPadstackNames : defaultViaNames;
                    if (vias.Count > 0)
                    {
                        var sbVia = new StringBuilder();
                        sbVia.Append("(circuit (use_via");
                        foreach (var v in vias)
                        {
                            sbVia.Append(" ");
                            sbVia.Append(IndentStreamWriter.QuoteIdentifier(v));
                        }
                        sbVia.Append("))");
                        writer.WriteLine(sbVia.ToString());
                    }

                    writer.EndScope(); // class
                }
            }

            // 3. Class default for any unassigned nets
            var unassignedNets = new List<string>();
            foreach (var net in model.Nets)
            {
                if (!assignedNets.Contains(net.Name))
                {
                    unassignedNets.Add(net.Name);
                }
            }

            if (unassignedNets.Count > 0)
            {
                writer.StartScope("class default");
                var sb = new StringBuilder();
                bool first = true;
                foreach (var netName in unassignedNets)
                {
                    if (!first) sb.Append(" ");
                    sb.Append(IndentStreamWriter.QuoteIdentifier(netName));
                    first = false;
                }
                writer.WriteLine(sb.ToString());

                // Class-specific rules (width and clearance)
                if (model.Rules != null)
                {
                    writer.StartScope("rule");
                    writer.WriteLine(string.Format("(width {0})", IndentStreamWriter.FormatCoord(model.Rules.DefaultWidth)));
                    writer.WriteLine(string.Format("(clearance {0})", IndentStreamWriter.FormatCoord(model.Rules.DefaultClearance)));
                    writer.EndScope();
                }

                if (defaultViaNames.Count > 0)
                {
                    var sbVia = new StringBuilder();
                    sbVia.Append("(circuit (use_via");
                    foreach (var v in defaultViaNames)
                    {
                        sbVia.Append(" ");
                        sbVia.Append(IndentStreamWriter.QuoteIdentifier(v));
                    }
                    sbVia.Append("))");
                    writer.WriteLine(sbVia.ToString());
                }

                writer.EndScope(); // default class scope
            }

            writer.EndScope();
        }
    }
}
