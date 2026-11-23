using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text;
using System.Threading;

namespace Freerouting.Altium.Extension.Execution
{
    public class RoutingProcessSettings
    {
        public string InputDsnPath { get; set; }
        public string OutputSesPath { get; set; }
        public int MaxPasses { get; set; }
        public int MaxThreads { get; set; }
        public double? CopperToEdgeClearanceUm { get; set; }
        public string JarPath { get; set; }
        public string ExtraArgs { get; set; }

        public RoutingProcessSettings(string dsnPath, string sesPath)
        {
            InputDsnPath = dsnPath;
            OutputSesPath = sesPath;
            MaxPasses = 20;
            MaxThreads = 8;
        }

        public string BuildCommandLineArguments()
        {
            var sb = new StringBuilder();

            // Canonical Freerouting command line flags
            sb.AppendFormat("-de \"{0}\" -do \"{1}\"", InputDsnPath, OutputSesPath);
            sb.AppendFormat(" --router.autorouter.max_passes={0}", MaxPasses);
            sb.AppendFormat(" --router.max_threads={0}", MaxThreads);

            if (CopperToEdgeClearanceUm.HasValue && CopperToEdgeClearanceUm.Value > 0)
            {
                sb.AppendFormat(CultureInfo.InvariantCulture, " --router.copper_to_edge_clearance_um={0:F1}", CopperToEdgeClearanceUm.Value);
            }

            if (!string.IsNullOrEmpty(ExtraArgs))
            {
                sb.Append(" ");
                sb.Append(ExtraArgs);
            }

            return sb.ToString();
        }
    }

    public class FreeroutingProcessRunner : IDisposable
    {
        private Process _process;
        private readonly object _lock = new object();
        private bool _isCancelled = false;
        private readonly StringBuilder _capturedOutput = new StringBuilder();

        public event EventHandler<ProcessOutputEventArgs> OutputReceived;
        public string CapturedOutput { get { return _capturedOutput.ToString(); } }
        public bool IsCancelled { get { return _isCancelled; } }

        public int Run(RoutingProcessSettings settings, int timeoutMs = 600000)
        {
            if (settings == null) throw new ArgumentNullException("settings");

            string javaExe = ResolveJavaExecutable();
            string jarPath = ResolveJarPath(settings.JarPath);
            string args = settings.BuildCommandLineArguments();

            if (string.IsNullOrEmpty(jarPath) || !File.Exists(jarPath))
            {
                throw new FileNotFoundException(
                    "Freerouting executable JAR not found.\n\n" +
                    "Please configure the path to 'freerouting-current-executable.jar' in Route > Freerouting Auto-Router > Settings... or set the FREEROUTING_JAR environment variable.\n\n" +
                    "Checked path: " + (string.IsNullOrEmpty(jarPath) ? "(empty)" : jarPath));
            }

            var psi = new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = string.Format("-jar \"{0}\" {1}", jarPath, args),
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };

            lock (_lock)
            {
                _isCancelled = false;
                _capturedOutput.Length = 0;
                _process = new Process { StartInfo = psi };
            }

            _process.OutputDataReceived += OnOutputDataReceived;
            _process.ErrorDataReceived += OnErrorDataReceived;

            try
            {
                bool started = _process.Start();
                if (!started) throw new InvalidOperationException("Failed to start Freerouting process: " + psi.FileName);

                _process.BeginOutputReadLine();
                _process.BeginErrorReadLine();

                bool finished = _process.WaitForExit(timeoutMs);
                if (!finished)
                {
                    Cancel();
                    throw new TimeoutException(string.Format("Freerouting process timed out after {0} ms.", timeoutMs));
                }

                if (_isCancelled)
                {
                    return -1;
                }

                return _process.ExitCode;
            }
            finally
            {
                lock (_lock)
                {
                    if (_process != null)
                    {
                        _process.OutputDataReceived -= OnOutputDataReceived;
                        _process.ErrorDataReceived -= OnErrorDataReceived;
                    }
                }
            }
        }

        public void Cancel()
        {
            lock (_lock)
            {
                _isCancelled = true;
                if (_process != null && !_process.HasExited)
                {
                    try
                    {
                        _process.Kill();
                    }
                    catch { }
                }
            }
        }

        private void OnOutputDataReceived(object sender, DataReceivedEventArgs e)
        {
            if (e.Data == null) return;

            lock (_capturedOutput)
            {
                _capturedOutput.AppendLine(e.Data);
            }

            var handler = OutputReceived;
            if (handler != null)
            {
                handler(this, new ProcessOutputEventArgs(e.Data, false));
            }
        }

        private void OnErrorDataReceived(object sender, DataReceivedEventArgs e)
        {
            if (e.Data == null) return;

            lock (_capturedOutput)
            {
                _capturedOutput.AppendLine(e.Data);
            }

            var handler = OutputReceived;
            if (handler != null)
            {
                handler(this, new ProcessOutputEventArgs(e.Data, true));
            }
        }

        private static string _cachedJavaExe = null;
        private static string _cachedJarPath = null;

        public static string ResolveJavaExecutable()
        {
            if (!string.IsNullOrEmpty(_cachedJavaExe)) return _cachedJavaExe;

            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string candidate = Path.Combine(javaHome, "bin", "java.exe");
                if (File.Exists(candidate))
                {
                    _cachedJavaExe = candidate;
                    return candidate;
                }
            }
            _cachedJavaExe = "java";
            return "java";
        }

        public static string ResolveJarPath(string explicitJarPath)
        {
            if (!string.IsNullOrEmpty(explicitJarPath) && File.Exists(explicitJarPath))
            {
                return explicitJarPath;
            }

            if (!string.IsNullOrEmpty(_cachedJarPath) && File.Exists(_cachedJarPath))
            {
                return _cachedJarPath;
            }

            string envJar = Environment.GetEnvironmentVariable("FREEROUTING_JAR");
            if (!string.IsNullOrEmpty(envJar) && File.Exists(envJar))
            {
                _cachedJarPath = envJar;
                return envJar;
            }

            // Comprehensive search across local, workspace, and standard system paths
            string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string baseDir = AppDomain.CurrentDomain.BaseDirectory ?? "";

            string[] searchPaths = new string[]
            {
                // Local Altium directory
                Path.Combine(baseDir, "freerouting-current-executable.jar"),
                Path.Combine(baseDir, "freerouting-executable.jar"),
                Path.Combine(baseDir, "System", "freerouting-current-executable.jar"),
                Path.Combine(baseDir, "System", "freerouting-executable.jar"),
                // Standard Application Data
                Path.Combine(appData, "Freerouting", "freerouting-current-executable.jar"),
                Path.Combine(appData, "Freerouting", "freerouting-executable.jar"),
                Path.Combine(localAppData, "Freerouting", "freerouting-current-executable.jar"),
                Path.Combine(localAppData, "Freerouting", "freerouting-executable.jar"),
                @"C:\Program Files\Freerouting\freerouting-executable.jar",
                @"C:\Program Files\Freerouting\freerouting-current-executable.jar"
            };

            foreach (var cand in searchPaths)
            {
                if (!string.IsNullOrEmpty(cand) && File.Exists(cand))
                {
                    _cachedJarPath = cand;
                    return cand;
                }
            }

            return null;
        }

        public void Dispose()
        {
            lock (_lock)
            {
                if (_process != null)
                {
                    try
                    {
                        if (!_process.HasExited) _process.Kill();
                    }
                    catch { }
                    _process.Dispose();
                    _process = null;
                }
            }
        }
    }
}
