using System;
using System.Collections.Generic;
using System.IO;
using System.Windows.Forms;

namespace Freerouting.Altium.Installer
{
    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            bool silent = false;
            bool uninstall = false;
            bool force = false;
            string customPath = null;

            for (int i = 0; i < args.Length; i++)
            {
                string arg = args[i].TrimStart('-', '/');
                if (arg.Equals("silent", StringComparison.OrdinalIgnoreCase) || arg.Equals("s", StringComparison.OrdinalIgnoreCase))
                {
                    silent = true;
                }
                else if (arg.Equals("uninstall", StringComparison.OrdinalIgnoreCase) || arg.Equals("u", StringComparison.OrdinalIgnoreCase))
                {
                    uninstall = true;
                }
                else if (arg.Equals("force", StringComparison.OrdinalIgnoreCase) || arg.Equals("f", StringComparison.OrdinalIgnoreCase))
                {
                    force = true;
                }
                else if (arg.StartsWith("path=", StringComparison.OrdinalIgnoreCase))
                {
                    customPath = arg.Substring("path=".Length).Trim('\"');
                }
                else if (arg.Equals("path", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                {
                    customPath = args[++i].Trim('\"');
                }
            }

            if (!IsAdministrator())
            {
                try
                {
                    var psi = new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = Application.ExecutablePath,
                        Arguments = string.Join(" ", args),
                        UseShellExecute = true,
                        Verb = "runas"
                    };
                    System.Diagnostics.Process p = System.Diagnostics.Process.Start(psi);
                    p.WaitForExit();
                    return p.ExitCode;
                }
                catch (Exception ex)
                {
                    if (silent)
                    {
                        Console.Error.WriteLine("Elevation required: " + ex.Message);
                    }
                    else
                    {
                        MessageBox.Show("Administrator privileges are required to install to Program Files.\n\n" + ex.Message, "Elevation Required", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    }
                    return 1;
                }
            }

            if (silent)
            {
                return RunHeadless(uninstall, customPath, force);
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new InstallerForm());
            return 0;
        }

        private static bool IsAdministrator()
        {
            try
            {
                var id = System.Security.Principal.WindowsIdentity.GetCurrent();
                var principal = new System.Security.Principal.WindowsPrincipal(id);
                return principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator);
            }
            catch
            {
                return false;
            }
        }

        private static int RunHeadless(bool uninstall, string customPath, bool force)
        {
            Console.WriteLine("Freerouting Altium Extension Setup (Headless Mode)");
            List<AltiumInstallation> targets = new List<AltiumInstallation>();

            if (!string.IsNullOrEmpty(customPath) && Directory.Exists(customPath))
            {
                string sys = Path.Combine(customPath, "System");
                targets.Add(new AltiumInstallation
                {
                    DisplayName = "Custom Path",
                    Version = "Custom",
                    InstallPath = customPath,
                    SystemPath = Directory.Exists(sys) ? sys : customPath
                });
            }
            else
            {
                targets = AltiumDetector.FindInstallations();
            }

            if (targets.Count == 0)
            {
                Console.Error.WriteLine("Error: No Altium Designer installations detected.");
                return 1;
            }

            if (AltiumDetector.IsAltiumRunning())
            {
                if (force)
                {
                    Console.WriteLine("Closing running Altium Designer (X2.exe)...");
                    AltiumDetector.CloseAltium();
                }
                else
                {
                    Console.Error.WriteLine("Error: Altium Designer (X2.exe) is running. Use /force to close automatically.");
                    return 2;
                }
            }

            foreach (var inst in targets)
            {
                if (uninstall)
                {
                    Console.WriteLine("Uninstalling from: {0}", inst.SystemPath);
                    AltiumDetector.UninstallExtension(inst, Console.WriteLine);
                }
                else
                {
                    Console.WriteLine("Installing into: {0}", inst.SystemPath);
                    AltiumDetector.InstallExtension(inst, Console.WriteLine);
                }
            }

            Console.WriteLine("Completed successfully.");
            return 0;
        }
    }
}
