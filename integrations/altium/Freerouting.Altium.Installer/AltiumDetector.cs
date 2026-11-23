using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using Microsoft.Win32;

namespace Freerouting.Altium.Installer
{
    public static class AltiumDetector
    {
        public static readonly string[] PayloadFiles = new string[]
        {
            "Freerouting.Altium.Extension.dll",
            "Freerouting.Altium.Core.dll",
            "Freerouting.ins",
            "Freerouting.rcs"
        };

        public static List<AltiumInstallation> FindInstallations()
        {
            var results = new List<AltiumInstallation>();
            var seenPaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            string[] regKeys = new string[]
            {
                @"SOFTWARE\Altium\Builds",
                @"SOFTWARE\WOW6432Node\Altium\Builds"
            };

            foreach (string regKey in regKeys)
            {
                try
                {
                    using (RegistryKey baseKey = Registry.LocalMachine.OpenSubKey(regKey))
                    {
                        if (baseKey != null)
                        {
                            foreach (string subKeyName in baseKey.GetSubKeyNames())
                            {
                                using (RegistryKey subKey = baseKey.OpenSubKey(subKeyName))
                                {
                                    if (subKey != null)
                                    {
                                        string progPath = subKey.GetValue("ProgramsInstallPath") as string;
                                        string version = subKey.GetValue("Version") as string ?? "Unknown";
                                        string dispName = subKey.GetValue("DisplayName") as string ?? subKeyName;

                                        if (!string.IsNullOrEmpty(progPath) && Directory.Exists(progPath))
                                        {
                                            if (seenPaths.Add(progPath))
                                            {
                                                string sysPath = Path.Combine(progPath, "System");
                                                if (!Directory.Exists(sysPath)) sysPath = progPath;

                                                string marker = Path.Combine(sysPath, "Freerouting.Altium.Extension.dll");
                                                results.Add(new AltiumInstallation
                                                {
                                                    DisplayName = dispName,
                                                    Version = version,
                                                    InstallPath = progPath,
                                                    SystemPath = sysPath,
                                                    IsExtensionInstalled = File.Exists(marker)
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch { }
            }

            // Fallback directory scan
            string[] commonRoots = new string[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Altium"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Altium")
            };

            foreach (string root in commonRoots)
            {
                if (Directory.Exists(root))
                {
                    try
                    {
                        foreach (string dir in Directory.GetDirectories(root))
                        {
                            if (seenPaths.Add(dir))
                            {
                                string sys = Path.Combine(dir, "System");
                                if (Directory.Exists(sys))
                                {
                                    string marker = Path.Combine(sys, "Freerouting.Altium.Extension.dll");
                                    results.Add(new AltiumInstallation
                                    {
                                        DisplayName = Path.GetFileName(dir),
                                        Version = "Detected",
                                        InstallPath = dir,
                                        SystemPath = sys,
                                        IsExtensionInstalled = File.Exists(marker)
                                    });
                                }
                            }
                        }
                    }
                    catch { }
                }
            }

            return results;
        }

        public static bool IsAltiumRunning()
        {
            Process[] processes = Process.GetProcessesByName("X2");
            return processes != null && processes.Length > 0;
        }

        public static bool CloseAltium()
        {
            Process[] processes = Process.GetProcessesByName("X2");
            if (processes == null || processes.Length == 0) return true;

            foreach (var p in processes)
            {
                try
                {
                    p.CloseMainWindow();
                    if (!p.WaitForExit(3000))
                    {
                        p.Kill();
                    }
                }
                catch { }
            }
            return !IsAltiumRunning();
        }

        public static void InstallExtension(AltiumInstallation install, Action<string> logger)
        {
            if (install == null) throw new ArgumentNullException("install");

            if (!Directory.Exists(install.SystemPath))
            {
                Directory.CreateDirectory(install.SystemPath);
            }

            Assembly asm = Assembly.GetExecutingAssembly();
            foreach (string fileName in PayloadFiles)
            {
                string resourceName = "Freerouting.Altium.Installer.Payload." + fileName;
                string destPath = Path.Combine(install.SystemPath, fileName);

                using (Stream stream = asm.GetManifestResourceStream(resourceName))
                {
                    if (stream == null)
                    {
                        // Fallback to local files alongside installer
                        string localPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, fileName);
                        if (File.Exists(localPath))
                        {
                            File.Copy(localPath, destPath, true);
                            if (logger != null) logger(string.Format("Copied {0} (from local disk)", fileName));
                            continue;
                        }
                        throw new FileNotFoundException("Cannot find payload resource: " + resourceName);
                    }

                    using (FileStream fileStream = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        stream.CopyTo(fileStream);
                    }
                    if (logger != null) logger(string.Format("Installed {0}", fileName));
                }
            }

            string legacyExt = Path.Combine(install.SystemPath, "Freerouting.ext");
            if (File.Exists(legacyExt))
            {
                try { File.Delete(legacyExt); if (logger != null) logger("Cleaned up legacy Freerouting.ext"); } catch { }
            }

            install.IsExtensionInstalled = true;
        }

        public static void UninstallExtension(AltiumInstallation install, Action<string> logger)
        {
            if (install == null) throw new ArgumentNullException("install");

            foreach (string fileName in PayloadFiles)
            {
                string destPath = Path.Combine(install.SystemPath, fileName);
                if (File.Exists(destPath))
                {
                    try
                    {
                        File.Delete(destPath);
                        if (logger != null) logger(string.Format("Removed {0}", fileName));
                    }
                    catch (Exception ex)
                    {
                        if (logger != null) logger(string.Format("Error removing {0}: {1}", fileName, ex.Message));
                    }
                }
            }

            string legacyExt = Path.Combine(install.SystemPath, "Freerouting.ext");
            if (File.Exists(legacyExt))
            {
                try { File.Delete(legacyExt); } catch { }
            }

            install.IsExtensionInstalled = false;
        }
    }
}
