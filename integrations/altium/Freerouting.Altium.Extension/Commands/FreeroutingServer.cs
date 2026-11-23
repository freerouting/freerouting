using System;
using System.IO;
using System.Text;
using System.Windows.Forms;
using DXP;
using PCB;
using RT_ClientServerInterface;
using RT_PCB;
using Freerouting.Altium.Core.Dsn;
using Freerouting.Altium.Core.Ses;
using Freerouting.Altium.Extension.BackAnnotation;
using Freerouting.Altium.Extension.Execution;
using Freerouting.Altium.Extension.Extraction;
using Freerouting.Altium.Extension.UI;

namespace CSharpPlugin
{
    public class PluginFactory
    {
        public object InvokePluginFactory(IClient client)
        {
            Freerouting.Altium.Extension.Commands.FreeroutingServerModule.LogTrace("InvokePluginFactory called by Altium DXP host.");
            return new Freerouting.Altium.Extension.Commands.FreeroutingServerModule(client);
        }
    }
}

namespace Freerouting.Altium.Extension.Commands
{
    public class FreeroutingServerModule : ServerModule
    {
        private static readonly string TraceLogPath = Path.Combine(Path.GetTempPath(), "freerouting_dxp_trace.txt");
        private static readonly string ErrorLogPath = Path.Combine(Path.GetTempPath(), "freerouting_dxp_error.txt");

        public static IClient HostClient { get; private set; }

        private readonly IClient _client;
        private readonly Lazy<FreeroutingServer> _server = new Lazy<FreeroutingServer>(() => new FreeroutingServer());

        public FreeroutingServerModule(IClient client) : base("Freerouting")
        {
            _client = client;
            HostClient = client;
            LogTrace("FreeroutingServerModule instantiated successfully with IClient.");
        }

        public static void LogTrace(string message)
        {
            try
            {
                File.AppendAllText(TraceLogPath, string.Format("[{0:yyyy-MM-dd HH:mm:ss.fff}] {1}\r\n", DateTime.Now, message));
            }
            catch { }
        }

        public static void LogError(string message, Exception ex)
        {
            try
            {
                File.AppendAllText(ErrorLogPath, string.Format("[{0:yyyy-MM-dd HH:mm:ss.fff}] {1}\r\n{2}\r\n", DateTime.Now, message, ex));
            }
            catch { }
        }

        protected override IServerDocument NewDocumentInstance(string kind, string fileName)
        {
            return null;
        }

        protected override void InitializeCommands()
        {
            base.InitializeCommands();
            LogTrace("InitializeCommands: Registering Freerouting commands with CommandLauncher...");

            var launcher = CommandLauncher as DXP.CommandLauncher;
            if (launcher != null)
            {
                launcher.RegisterCommand("AutoRoute", Command_AutoRoute, GetCommandState_Default);
                launcher.RegisterCommand("ExportDsn", Command_ExportDsn, GetCommandState_Default);
                launcher.RegisterCommand("ImportSes", Command_ImportSes, GetCommandState_Default);
                launcher.RegisterCommand("Settings", Command_Settings, GetCommandState_Default);
                LogTrace("Freerouting commands registered successfully.");
            }
            else
            {
                LogError("InitializeCommands: CommandLauncher is null or not DXP.CommandLauncher.", null);
            }
        }

        private void GetCommandState_Default(IServerDocumentView argContext, StringBuilder argParameters, ref bool enabled, ref bool isChecked, ref bool visible, StringBuilder caption, StringBuilder imageFile)
        {
            enabled = true;
            visible = true;
            isChecked = false;
        }

        private void Command_AutoRoute(IServerDocumentView argContext, StringBuilder argParameters)
        {
            LogTrace("Command_AutoRoute invoked.");
            RunCommand("AutoRoute", argParameters);
        }

        private void Command_ExportDsn(IServerDocumentView argContext, StringBuilder argParameters)
        {
            LogTrace("Command_ExportDsn invoked.");
            RunCommand("ExportDsn", argParameters);
        }

        private void Command_ImportSes(IServerDocumentView argContext, StringBuilder argParameters)
        {
            LogTrace("Command_ImportSes invoked.");
            RunCommand("ImportSes", argParameters);
        }

        private void Command_Settings(IServerDocumentView argContext, StringBuilder argParameters)
        {
            LogTrace("Command_Settings invoked.");
            _server.Value.ExecuteCommand(null, "Settings", "");
        }

        private void RunCommand(string commandName, StringBuilder argParameters)
        {
            try
            {
                IPCB_ServerInterface pcbServer = PCB.GlobalVars.PCBServer;
                string parameters = argParameters != null ? argParameters.ToString() : "";
                _server.Value.ExecuteCommand(pcbServer, commandName, parameters);
            }
            catch (Exception ex)
            {
                LogError("RunCommand failed for " + commandName, ex);
                MessageBox.Show("Freerouting error: " + ex.Message, "Freerouting Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }

    public class FreeroutingServer
    {
        private readonly PcbDocExtractor _extractor = new PcbDocExtractor();
        private readonly PcbDocBackAnnotator _backAnnotator = new PcbDocBackAnnotator();
        private readonly DsnStreamWriter _dsnWriter = new DsnStreamWriter();
        private readonly SesStreamParser _sesParser = new SesStreamParser();

        public bool SuppressDialogs { get; set; }

        public void ExecuteCommand(IPCB_ServerInterface pcbServer, string commandName, string parameters)
        {
            FreeroutingServerModule.LogTrace(string.Format("ExecuteCommand called: '{0}' (parameters='{1}')", commandName, parameters));

            if (string.Equals(commandName, "Settings", StringComparison.OrdinalIgnoreCase))
            {
                HandleSettings();
                return;
            }

            IPCB_Board board = null;
            if (pcbServer != null)
            {
                try
                {
                    board = pcbServer.GetCurrentPCBBoard();
                    FreeroutingServerModule.LogTrace("pcbServer.GetCurrentPCBBoard() returned: " + (board != null ? "non-null" : "null"));
                }
                catch (Exception ex)
                {
                    FreeroutingServerModule.LogError("Error calling pcbServer.GetCurrentPCBBoard()", ex);
                }
            }

            if (board == null)
            {
                FreeroutingServerModule.LogTrace("ExecuteCommand: board is null.");
                if (!SuppressDialogs)
                {
                    MessageBox.Show("No active PCB document found in Altium Designer.\n\nPlease open a .PcbDoc board file before launching Freerouting.", "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
                return;
            }

            try
            {
                if (string.Equals(commandName, "AutoRoute", StringComparison.OrdinalIgnoreCase))
                {
                    HandleAutoRoute(pcbServer, board);
                }
                else if (string.Equals(commandName, "ExportDsn", StringComparison.OrdinalIgnoreCase))
                {
                    HandleExportDsn(board);
                }
                else if (string.Equals(commandName, "ImportSes", StringComparison.OrdinalIgnoreCase))
                {
                    HandleImportSes(pcbServer, board);
                }
                else
                {
                    if (!SuppressDialogs)
                    {
                        MessageBox.Show("Unrecognized Freerouting command: " + commandName, "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    }
                }
            }
            catch (Exception ex)
            {
                FreeroutingServerModule.LogError("ExecuteCommand exception", ex);
                if (!SuppressDialogs)
                {
                    MessageBox.Show("Freerouting error: " + ex.Message + "\n\n" + ex.StackTrace, "Freerouting Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
        }

        private void HandleAutoRoute(IPCB_ServerInterface pcbServer, IPCB_Board board)
        {
            string tempDir = Path.Combine(Path.GetTempPath(), "Freerouting_" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(tempDir);

            string dsnPath = Path.Combine(tempDir, "board.dsn");
            string sesPath = Path.Combine(tempDir, "board.ses");

            try
            {
                // 1. Extract board model
                var model = _extractor.Extract(board);

                // 2. Export DSN
                using (var sw = new StreamWriter(dsnPath, false, System.Text.Encoding.ASCII))
                {
                    _dsnWriter.Write(model, sw);
                }

                // 3. Launch Freerouting Process with live progress dialog
                var config = AltiumPluginConfig.Load();
                var processSettings = new RoutingProcessSettings(dsnPath, sesPath);
                processSettings.JarPath = config.JarPath;
                processSettings.MaxPasses = config.MaxPasses;
                processSettings.MaxThreads = config.MaxThreads;
                processSettings.ExtraArgs = config.ExtraArgs;
                if (model.Rules != null && model.Rules.BoardOutlineClearance > 0)
                {
                    processSettings.CopperToEdgeClearanceUm = (model.Rules.BoardOutlineClearance * 25.4) / 10000.0;
                }
                else
                {
                    processSettings.CopperToEdgeClearanceUm = config.CopperToEdgeClearanceUm;
                }

                using (var runner = new FreeroutingProcessRunner())
                using (var dlg = new RoutingProgressDialog(runner, processSettings))
                {
                    var dlgResult = dlg.ShowDialog();
                    if (dlgResult != DialogResult.OK || !File.Exists(sesPath))
                    {
                        if (dlg.ExecutionError != null)
                        {
                            MessageBox.Show("Freerouting execution failed: " + dlg.ExecutionError.Message, "Freerouting Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                        }
                        else if (runner.IsCancelled)
                        {
                            MessageBox.Show("Routing cancelled by user.", "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Information);
                        }
                        else
                        {
                            MessageBox.Show("Freerouting execution did not generate a session file.", "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                        }
                        return;
                    }
                }

                // 4. Parse SES
                SesResult sesResult;
                using (var sr = new StreamReader(sesPath))
                {
                    sesResult = _sesParser.Parse(sr);
                }

                // 5. Back-annotate
                _backAnnotator.BackAnnotate(pcbServer, board, sesResult);

                MessageBox.Show(string.Format("Freerouting complete!\n\nBack-annotated:\n- {0} tracks\n- {1} vias",
                    sesResult.Wires.Count, sesResult.Vias.Count), "Freerouting Success", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            finally
            {
                try
                {
                    if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
                }
                catch { }
            }
        }

        private void HandleExportDsn(IPCB_Board board)
        {
            using (var sfd = new SaveFileDialog())
            {
                sfd.Title = "Export Specctra DSN";
                sfd.Filter = "Specctra DSN Files (*.dsn)|*.dsn|All Files (*.*)|*.*";
                string fileName = PcbDocExtractor.ResolveBoardFileName(board);
                string defName = !string.IsNullOrEmpty(fileName)
                    ? Path.GetFileNameWithoutExtension(fileName) + ".dsn"
                    : "board.dsn";
                sfd.FileName = defName;

                if (sfd.ShowDialog() == DialogResult.OK)
                {
                    var model = _extractor.Extract(board);
                    using (var sw = new StreamWriter(sfd.FileName, false, System.Text.Encoding.ASCII))
                    {
                        _dsnWriter.Write(model, sw);
                    }
                    MessageBox.Show("Specctra DSN exported successfully to:\n" + sfd.FileName, "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            }
        }

        private void HandleImportSes(IPCB_ServerInterface pcbServer, IPCB_Board board)
        {
            using (var ofd = new OpenFileDialog())
            {
                ofd.Title = "Import Specctra SES";
                ofd.Filter = "Specctra SES Files (*.ses)|*.ses|All Files (*.*)|*.*";

                if (ofd.ShowDialog() == DialogResult.OK)
                {
                    SesResult sesResult;
                    using (var sr = new StreamReader(ofd.FileName))
                    {
                        sesResult = _sesParser.Parse(sr);
                    }

                    _backAnnotator.BackAnnotate(pcbServer, board, sesResult);
                    MessageBox.Show(string.Format("Specctra SES imported successfully!\n\nBack-annotated:\n- {0} tracks\n- {1} vias",
                        sesResult.Wires.Count, sesResult.Vias.Count), "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            }
        }

        private void HandleSettings()
        {
            if (!SuppressDialogs)
            {
                using (var dlg = new SettingsDialog())
                {
                    dlg.ShowDialog();
                }
            }
        }
    }
}
