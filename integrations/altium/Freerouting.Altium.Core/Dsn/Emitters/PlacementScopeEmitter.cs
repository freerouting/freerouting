using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn.Emitters
{
    public class PlacementScopeEmitter : IScopeEmitter
    {
        public string ScopeName
        {
            get { return "placement"; }
        }

        public void EmitScope(IndentStreamWriter writer, BoardModel model)
        {
            writer.StartScope("placement");

            // Group placed components by their image pattern
            var byImage = new System.Collections.Generic.Dictionary<string, System.Collections.Generic.List<PlacedComponent>>();
            foreach (var comp in model.Components)
            {
                System.Collections.Generic.List<PlacedComponent> list;
                if (!byImage.TryGetValue(comp.ImageName, out list))
                {
                    list = new System.Collections.Generic.List<PlacedComponent>();
                    byImage[comp.ImageName] = list;
                }
                list.Add(comp);
            }

            foreach (var kvp in byImage)
            {
                writer.StartScope(string.Format("component {0}", IndentStreamWriter.QuoteIdentifier(kvp.Key)));
                foreach (var comp in kvp.Value)
                {
                    writer.WriteLine(string.Format("(place {0} {1} {2} {3} {4} (pn {0}) (lock_type position))",
                        IndentStreamWriter.QuoteIdentifier(comp.Designator),
                        IndentStreamWriter.FormatCoord(comp.Location.X),
                        IndentStreamWriter.FormatCoord(comp.Location.Y),
                        comp.Side,
                        comp.RotationDegrees.ToString("0.##", System.Globalization.CultureInfo.InvariantCulture)));
                }
                writer.EndScope();
            }

            writer.EndScope();
        }
    }
}
