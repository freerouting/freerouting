using System;

namespace Freerouting.Altium.Extension.Execution
{
    public class ProcessOutputEventArgs : EventArgs
    {
        public string Line { get; private set; }
        public int? CurrentPass { get; private set; }
        public int? UnroutedCount { get; private set; }
        public int? ClearanceViolations { get; private set; }
        public double? Score { get; private set; }
        public bool IsError { get; private set; }

        public ProcessOutputEventArgs(string line, bool isError = false)
        {
            Line = line;
            IsError = isError;

            ParseLine(line);
        }

        private void ParseLine(string line)
        {
            if (string.IsNullOrEmpty(line)) return;

            // Example Freerouting log:
            // "Auto-routing pass #1 on board 'xxx' was completed in 0.17 seconds with score 1000.00 (0 unrouted and 0 violations)."
            if (line.Contains("Auto-routing pass #"))
            {
                int hashIdx = line.IndexOf("pass #");
                if (hashIdx >= 0)
                {
                    int start = hashIdx + 6;
                    int end = line.IndexOf(' ', start);
                    if (end > start)
                    {
                        int pass;
                        if (int.TryParse(line.Substring(start, end - start), out pass))
                        {
                            CurrentPass = pass;
                        }
                    }
                }

                int unroutedIdx = line.IndexOf(" unrouted");
                if (unroutedIdx > 0)
                {
                    int parenIdx = line.LastIndexOf('(', unroutedIdx);
                    if (parenIdx >= 0)
                    {
                        string countStr = line.Substring(parenIdx + 1, unroutedIdx - (parenIdx + 1)).Trim();
                        int unrouted;
                        if (int.TryParse(countStr, out unrouted))
                        {
                            UnroutedCount = unrouted;
                        }
                    }
                }

                int violIdx = line.IndexOf(" violations");
                if (violIdx > 0)
                {
                    int andIdx = line.LastIndexOf("and ", violIdx);
                    if (andIdx >= 0)
                    {
                        string violStr = line.Substring(andIdx + 4, violIdx - (andIdx + 4)).Trim();
                        int viol;
                        if (int.TryParse(violStr, out viol))
                        {
                            ClearanceViolations = viol;
                        }
                    }
                }
            }
        }
    }
}
