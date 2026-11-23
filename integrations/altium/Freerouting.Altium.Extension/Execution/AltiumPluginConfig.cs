using System;
using System.Globalization;
using System.IO;
using System.Text;

namespace Freerouting.Altium.Extension.Execution
{
    public class AltiumPluginConfig
    {
        public string JarPath { get; set; }
        public int MaxPasses { get; set; }
        public int MaxThreads { get; set; }
        public double CopperToEdgeClearanceUm { get; set; }
        public string ExtraArgs { get; set; }

        public AltiumPluginConfig()
        {
            JarPath = "";
            MaxPasses = 20;
            MaxThreads = 8;
            CopperToEdgeClearanceUm = 250.0;
            ExtraArgs = "";
        }

        public static string GetConfigFilePath()
        {
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string dir = Path.Combine(appData, "Freerouting");
            return Path.Combine(dir, "altium_settings.json");
        }

        public static AltiumPluginConfig Load()
        {
            var config = new AltiumPluginConfig();
            string path = GetConfigFilePath();

            try
            {
                if (File.Exists(path))
                {
                    string content = File.ReadAllText(path);
                    config.ParseJson(content);
                }
            }
            catch { }

            // Auto-detect JAR if not explicitly configured or path no longer exists
            if (string.IsNullOrEmpty(config.JarPath) || !File.Exists(config.JarPath))
            {
                string detected = FreeroutingProcessRunner.ResolveJarPath(null);
                if (!string.IsNullOrEmpty(detected) && File.Exists(detected))
                {
                    config.JarPath = detected;
                }
            }

            return config;
        }

        public void Save()
        {
            try
            {
                string path = GetConfigFilePath();
                string dir = Path.GetDirectoryName(path);
                if (!Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                string json = ToJson();
                File.WriteAllText(path, json, Encoding.UTF8);
            }
            catch { }
        }

        private string ToJson()
        {
            var sb = new StringBuilder();
            sb.AppendLine("{");
            sb.AppendFormat("  \"jar_path\": \"{0}\",\r\n", EscapeJson(JarPath ?? ""));
            sb.AppendFormat("  \"max_passes\": {0},\r\n", MaxPasses);
            sb.AppendFormat("  \"max_threads\": {0},\r\n", MaxThreads);
            sb.AppendFormat(CultureInfo.InvariantCulture, "  \"copper_to_edge_clearance_um\": {0:F1},\r\n", CopperToEdgeClearanceUm);
            sb.AppendFormat("  \"extra_args\": \"{0}\"\r\n", EscapeJson(ExtraArgs ?? ""));
            sb.AppendLine("}");
            return sb.ToString();
        }

        private void ParseJson(string json)
        {
            if (string.IsNullOrEmpty(json)) return;

            string[] lines = json.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (string rawLine in lines)
            {
                string line = rawLine.Trim().TrimEnd(',');
                int colonIdx = line.IndexOf(':');
                if (colonIdx < 0) continue;

                string key = line.Substring(0, colonIdx).Trim().Trim('\"');
                string val = line.Substring(colonIdx + 1).Trim().Trim('\"');

                if (string.Equals(key, "jar_path", StringComparison.OrdinalIgnoreCase))
                {
                    JarPath = UnescapeJson(val);
                }
                else if (string.Equals(key, "max_passes", StringComparison.OrdinalIgnoreCase))
                {
                    int passes;
                    if (int.TryParse(val, NumberStyles.Integer, CultureInfo.InvariantCulture, out passes) && passes > 0)
                    {
                        MaxPasses = passes;
                    }
                }
                else if (string.Equals(key, "max_threads", StringComparison.OrdinalIgnoreCase))
                {
                    int threads;
                    if (int.TryParse(val, NumberStyles.Integer, CultureInfo.InvariantCulture, out threads) && threads > 0)
                    {
                        MaxThreads = threads;
                    }
                }
                else if (string.Equals(key, "copper_to_edge_clearance_um", StringComparison.OrdinalIgnoreCase))
                {
                    double clearance;
                    if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out clearance) && clearance >= 0)
                    {
                        CopperToEdgeClearanceUm = clearance;
                    }
                }
                else if (string.Equals(key, "extra_args", StringComparison.OrdinalIgnoreCase))
                {
                    ExtraArgs = UnescapeJson(val);
                }
            }
        }

        private static string EscapeJson(string str)
        {
            if (string.IsNullOrEmpty(str)) return "";
            return str.Replace("\\", "\\\\").Replace("\"", "\\\"");
        }

        private static string UnescapeJson(string str)
        {
            if (string.IsNullOrEmpty(str)) return "";
            return str.Replace("\\\"", "\"").Replace("\\\\", "\\");
        }
    }
}
