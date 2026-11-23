using System;
using System.Drawing;
using System.Threading;
using System.Windows.Forms;
using Freerouting.Altium.Extension.Execution;

namespace Freerouting.Altium.Extension.UI
{
    public class RoutingProgressDialog : Form
    {
        private readonly FreeroutingProcessRunner _runner;
        private readonly RoutingProcessSettings _settings;
        private Label _lblStatus;
        private Label _lblDetails;
        private ProgressBar _progressBar;
        private TextBox _txtLog;
        private Button _btnCancel;
        private int _exitCode = -1;
        private Exception _executionError;

        public int ExitCode { get { return _exitCode; } }
        public Exception ExecutionError { get { return _executionError; } }

        public RoutingProgressDialog(FreeroutingProcessRunner runner, RoutingProcessSettings settings)
        {
            if (runner == null) throw new ArgumentNullException("runner");
            if (settings == null) throw new ArgumentNullException("settings");

            _runner = runner;
            _settings = settings;

            InitializeComponent();
        }

        private void InitializeComponent()
        {
            Text = "Freerouting - Autorouting in Progress";
            Size = new Size(580, 420);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ShowInTaskbar = true;

            var panelTop = new Panel { Dock = DockStyle.Top, Height = 90, Padding = new Padding(15) };

            _lblStatus = new Label
            {
                Text = "Starting Freerouting process...",
                Font = new Font("Segoe UI", 10F, FontStyle.Bold),
                Location = new Point(15, 12),
                AutoSize = true
            };

            _lblDetails = new Label
            {
                Text = string.Format("Board: {0} | Max Passes: {1} | Threads: {2}",
                    System.IO.Path.GetFileName(_settings.InputDsnPath), _settings.MaxPasses, _settings.MaxThreads),
                Font = new Font("Segoe UI", 8.5F, FontStyle.Regular),
                ForeColor = Color.DimGray,
                Location = new Point(15, 34),
                AutoSize = true
            };

            _progressBar = new ProgressBar
            {
                Style = ProgressBarStyle.Marquee,
                MarqueeAnimationSpeed = 30,
                Location = new Point(15, 58),
                Size = new Size(535, 18)
            };

            panelTop.Controls.Add(_lblStatus);
            panelTop.Controls.Add(_lblDetails);
            panelTop.Controls.Add(_progressBar);

            _txtLog = new TextBox
            {
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                Font = new Font("Consolas", 8.5F),
                BackColor = Color.White,
                Location = new Point(15, 95),
                Size = new Size(535, 230),
                Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right
            };

            var panelBottom = new Panel { Dock = DockStyle.Bottom, Height = 48 };
            _btnCancel = new Button
            {
                Text = "Cancel",
                Size = new Size(85, 28),
                Location = new Point(465, 8),
                Font = new Font("Segoe UI", 9F)
            };
            _btnCancel.Click += OnCancelClick;
            panelBottom.Controls.Add(_btnCancel);

            Controls.Add(_txtLog);
            Controls.Add(panelTop);
            Controls.Add(panelBottom);

            FormClosing += OnDialogClosing;
            Load += OnDialogLoad;
        }

        private void OnDialogLoad(object sender, EventArgs e)
        {
            _runner.OutputReceived += OnRunnerOutputReceived;

            var worker = new Thread(() =>
            {
                try
                {
                    _exitCode = _runner.Run(_settings);
                }
                catch (Exception ex)
                {
                    _executionError = ex;
                    _exitCode = -1;
                }

                if (!IsDisposed && IsHandleCreated)
                {
                    BeginInvoke(new Action(() =>
                    {
                        DialogResult = (_exitCode == 0) ? DialogResult.OK : DialogResult.Cancel;
                        Close();
                    }));
                }
            });

            worker.IsBackground = true;
            worker.Start();
        }

        private void OnRunnerOutputReceived(object sender, ProcessOutputEventArgs e)
        {
            if (IsDisposed || !IsHandleCreated) return;

            BeginInvoke(new Action(() =>
            {
                if (e.CurrentPass.HasValue)
                {
                    _lblStatus.Text = string.Format("Auto-routing Pass #{0} (Unrouted: {1}, Violations: {2})",
                        e.CurrentPass.Value,
                        e.UnroutedCount.HasValue ? e.UnroutedCount.Value.ToString() : "?",
                        e.ClearanceViolations.HasValue ? e.ClearanceViolations.Value.ToString() : "?");
                }
                else if (e.Line.Contains("Fanout pass #"))
                {
                    _lblStatus.Text = "Running SMD Fanout stage...";
                }
                else if (e.Line.Contains("Step slide"))
                {
                    _lblStatus.Text = "Running Post-Routing Optimizer (Step slide)...";
                }

                _txtLog.AppendText(e.Line + Environment.NewLine);
            }));
        }

        private void OnCancelClick(object sender, EventArgs e)
        {
            _btnCancel.Enabled = false;
            _lblStatus.Text = "Cancelling Freerouting process...";
            _runner.Cancel();
        }

        private void OnDialogClosing(object sender, FormClosingEventArgs e)
        {
            _runner.OutputReceived -= OnRunnerOutputReceived;
            if (_exitCode == -1 && !_runner.IsCancelled)
            {
                _runner.Cancel();
            }
        }
    }
}
