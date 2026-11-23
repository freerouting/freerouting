using System;

namespace Freerouting.Altium.Installer
{
    public class AltiumInstallation
    {
        public string DisplayName { get; set; }
        public string Version { get; set; }
        public string InstallPath { get; set; }
        public string SystemPath { get; set; }
        public bool IsExtensionInstalled { get; set; }

        public override string ToString()
        {
            string status = IsExtensionInstalled ? "[Installed]" : "[Not Installed]";
            return string.Format("{0} (v{1}) {2} - {3}", DisplayName, Version, status, InstallPath);
        }
    }
}
