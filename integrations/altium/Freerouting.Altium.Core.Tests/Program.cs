using System;
using System.IO;
using System.Reflection;

namespace Freerouting.Altium.Core.Tests
{
    public static class Program
    {
        public static int Main(string[] args)
        {
            AppDomain.CurrentDomain.AssemblyResolve += (sender, resolveArgs) =>
            {
                var name = new AssemblyName(resolveArgs.Name).Name + ".dll";
                var altiumSys = @"C:\Program Files\Altium\AD26\System";
                var path = Path.Combine(altiumSys, name);
                return File.Exists(path) ? Assembly.LoadFrom(path) : null;
            };

            return Run(args);
        }

        private static int Run(string[] args)
        {
            Console.WriteLine("==================================================");
            Console.WriteLine("  Freerouting Altium Core Test Runner");
            Console.WriteLine("==================================================");

            try
            {
                Console.WriteLine("\n[1/5] Running DsnStreamWriterTests...");
                DsnStreamWriterTests.RunAll();

                Console.WriteLine("\n[2/5] Running SesStreamParserTests...");
                SesStreamParserTests.RunAll();

                Console.WriteLine("\n[3/5] Running ExtensionIntegrationTests...");
                ExtensionIntegrationTests.RunAll();

                Console.WriteLine("\n[4/5] Running ProcessRunnerTests...");
                ProcessRunnerTests.RunAll();

                Console.WriteLine("\n[5/5] Generating Golden Fixture...");
                string fixturePath = (args != null && args.Length > 0 && !args[0].StartsWith("-")) ? args[0] : "fixtures/Altium-TwoLayerBenchmark.dsn";
                string dir = Path.GetDirectoryName(fixturePath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }
                FixtureGenerator.GenerateFixtureFile(fixturePath);
                Console.WriteLine("  [PASS] Fixture generated: " + fixturePath);

                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine("\n==================================================");
                Console.WriteLine("  ALL TESTS PASSED SUCCESSFULLY");
                Console.WriteLine("==================================================");
                Console.ResetColor();
                return 0;
            }
            catch (Exception ex)
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine("\n[ERROR] " + ex.Message);
                Console.WriteLine(ex.StackTrace);
                Console.ResetColor();
                return 1;
            }
        }
    }
}
