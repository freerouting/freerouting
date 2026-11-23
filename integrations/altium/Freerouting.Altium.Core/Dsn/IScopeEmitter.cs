using Freerouting.Altium.Core.Model;

namespace Freerouting.Altium.Core.Dsn
{
    public interface IScopeEmitter
    {
        string ScopeName { get; }
        void EmitScope(IndentStreamWriter writer, BoardModel model);
    }
}
