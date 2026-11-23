using System;
using System.Collections.Generic;
using System.IO;
using Freerouting.Altium.Core.Dsn.Emitters;
using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn
{
    public class DsnStreamWriter
    {
        private readonly List<IScopeEmitter> _emitters = new List<IScopeEmitter>();

        public DsnStreamWriter()
        {
            // Register standard default emitters in canonical Specctra order
            _emitters.Add(new StructureScopeEmitter());
            _emitters.Add(new PlacementScopeEmitter());
            _emitters.Add(new LibraryScopeEmitter());
            _emitters.Add(new NetworkScopeEmitter());
            _emitters.Add(new WiringScopeEmitter());
        }

        public void RegisterEmitter(IScopeEmitter emitter)
        {
            if (emitter == null) throw new ArgumentNullException("emitter");
            _emitters.Add(emitter);
        }

        public void Write(BoardModel model, TextWriter target)
        {
            if (model == null) throw new ArgumentNullException("model");
            if (target == null) throw new ArgumentNullException("target");

            var writer = new IndentStreamWriter(target);

            // 1. Root DSN design header
            writer.StartScope(string.Format("pcb {0}", IndentStreamWriter.QuoteIdentifier(model.Name)));

            // 2. Parser header
            writer.StartScope("parser");
            writer.WriteLine("(string_quote \")");
            writer.WriteLine("(space_in_quoted_tokens on)");
            writer.WriteLine("(host_cad \"Altium Designer\")");
            writer.WriteLine("(host_version \"24.0\")");
            writer.EndScope();

            // 3. Resolution and Unit headers (lossless 1:1 mil 10,000 internal units mapping)
            writer.WriteLine("(resolution mil 10000)");
            writer.WriteLine("(unit mil)");

            // 5. Execute each registered scope emitter
            foreach (var emitter in _emitters)
            {
                emitter.EmitScope(writer, model);
            }

            // Close root scope
            writer.EndScope();
        }
    }
}
