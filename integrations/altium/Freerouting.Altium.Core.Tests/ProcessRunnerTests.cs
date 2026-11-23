using System;
using Freerouting.Altium.Extension.Execution;

namespace Freerouting.Altium.Core.Tests
{
    public static class ProcessRunnerTests
    {
        public static void RunAll()
        {
            VerifyCommandLineArgumentsFormatting();
            VerifyProcessOutputParsing();
            VerifyRunnerCancellationSafety();
        }

        public static void VerifyCommandLineArgumentsFormatting()
        {
            var settings = new RoutingProcessSettings("input.dsn", "output.ses");
            settings.MaxPasses = 15;
            settings.MaxThreads = 4;
            settings.CopperToEdgeClearanceUm = 300.0;

            string args = settings.BuildCommandLineArguments();

            Assert("Arguments contain -de input.dsn", args.Contains("-de \"input.dsn\""));
            Assert("Arguments contain -do output.ses", args.Contains("-do \"output.ses\""));
            Assert("Arguments contain canonical --router.autorouter.max_passes=15", args.Contains("--router.autorouter.max_passes=15"));
            Assert("Arguments contain canonical --router.max_threads=4", args.Contains("--router.max_threads=4"));
            Assert("Arguments contain canonical --router.copper_to_edge_clearance_um=300.0", args.Contains("--router.copper_to_edge_clearance_um=300.0"));
            Assert("No deprecated camelCase flags present", !args.Contains("maxPasses") && !args.Contains("maxThreads"));
        }

        public static void VerifyProcessOutputParsing()
        {
            // Case 1: Pass 1 clean completion
            string log1 = "2026-11-20 00:40:15.448 INFO [C1C9C2\\7498E9] Auto-routing pass #1 on board 'test' was completed in 0.17 seconds with score 1000.00 (0 unrouted and 0 violations).";
            var ev1 = new ProcessOutputEventArgs(log1, false);

            Assert("Parsed pass #1", ev1.CurrentPass == 1);
            Assert("Parsed 0 unrouted", ev1.UnroutedCount == 0);
            Assert("Parsed 0 violations", ev1.ClearanceViolations == 0);

            // Case 2: Intermediate pass with unrouted items and violations
            string log2 = "Auto-routing pass #5 on board 'test' was completed in 1.20 seconds with score 850.00 (12 unrouted and 4 violations).";
            var ev2 = new ProcessOutputEventArgs(log2, false);

            Assert("Parsed pass #5", ev2.CurrentPass == 5);
            Assert("Parsed 12 unrouted", ev2.UnroutedCount == 12);
            Assert("Parsed 4 violations", ev2.ClearanceViolations == 4);
        }

        public static void VerifyRunnerCancellationSafety()
        {
            using (var runner = new FreeroutingProcessRunner())
            {
                Assert("Runner initially not cancelled", !runner.IsCancelled);
                runner.Cancel();
                Assert("Runner cancelled after Cancel() call", runner.IsCancelled);
            }
        }

        private static void Assert(string name, bool condition)
        {
            if (condition)
            {
                Console.WriteLine("  [PASS] " + name);
            }
            else
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine("  [FAIL] " + name);
                Console.ResetColor();
                throw new Exception("Assertion failed: " + name);
            }
        }
    }
}
