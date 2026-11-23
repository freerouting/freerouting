using System;
using System.IO;

namespace Freerouting.Altium.Core.Dsn
{
    public class IndentStreamWriter
    {
        private readonly TextWriter _writer;
        private int _indent;
        private bool _atLineStart = true;

        public IndentStreamWriter(TextWriter writer)
        {
            if (writer == null) throw new ArgumentNullException("writer");
            _writer = writer;
        }

        public void StartScope(string scopeName)
        {
            WriteIndent();
            _writer.Write("(");
            _writer.Write(scopeName);
            _writer.WriteLine();
            _indent += 2;
            _atLineStart = true;
        }

        public void EndScope()
        {
            if (_indent >= 2)
            {
                _indent -= 2;
            }
            WriteIndent();
            _writer.WriteLine(")");
            _atLineStart = true;
        }

        public void WriteLine(string text)
        {
            WriteIndent();
            _writer.WriteLine(text);
            _atLineStart = true;
        }

        public void Write(string text)
        {
            WriteIndent();
            _writer.Write(text);
        }

        public void WriteIndent()
        {
            if (_atLineStart)
            {
                if (_indent > 0)
                {
                    _writer.Write(new string(' ', _indent));
                }
                _atLineStart = false;
            }
        }

        public static string FormatCoord(int altiumUnits)
        {
            int whole = altiumUnits / 10000;
            int frac = Math.Abs(altiumUnits % 10000);
            if (altiumUnits < 0 && whole == 0)
            {
                return string.Format(System.Globalization.CultureInfo.InvariantCulture, "-0.{0:D4}", frac);
            }
            return string.Format(System.Globalization.CultureInfo.InvariantCulture, "{0}.{1:D4}", whole, frac);
        }

        public static string QuoteIdentifier(string id)
        {
            if (string.IsNullOrEmpty(id)) return "\"\"";
            if (id.IndexOfAny(new[] { '(', ')', ' ', ';', '-', '/', '~', '{', '}', '"', '+' }) >= 0)
            {
                return "\"" + id.Replace("\"", "\\\"") + "\"";
            }
            return id;
        }
    }
}
