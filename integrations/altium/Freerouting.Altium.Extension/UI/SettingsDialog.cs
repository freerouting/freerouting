using System;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.Windows.Forms;
using Freerouting.Altium.Extension.Execution;

namespace Freerouting.Altium.Extension.UI
{
    public class SettingsDialog : Form
    {
        private TextBox _txtJarPath;
        private NumericUpDown _numMaxPasses;
        private NumericUpDown _numMaxThreads;
        private NumericUpDown _numCopperToEdge;
        private TextBox _txtExtraArgs;
        private AltiumPluginConfig _config;

        public SettingsDialog()
        {
            InitializeComponent();
            LoadSettings();
        }

        private void InitializeComponent()
        {
            this.Text = "Freerouting Settings";
            this.ClientSize = new Size(640, 480);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.MinimizeBox = false;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.Font = new Font("Segoe UI", 9F, FontStyle.Regular, GraphicsUnit.Point, 0);
            this.AutoScaleMode = AutoScaleMode.Dpi;

            // 1. Engine Group
            var grpEngine = new GroupBox
            {
                Text = "Engine Configuration",
                Location = new Point(16, 12),
                Size = new Size(608, 105)
            };

            var lblJar = new Label
            {
                Text = "Freerouting Executable JAR:",
                Location = new Point(16, 25),
                AutoSize = true
            };
            _txtJarPath = new TextBox
            {
                Location = new Point(16, 46),
                Size = new Size(400, 24)
            };
            var btnBrowse = new Button
            {
                Text = "Browse...",
                Location = new Point(424, 44),
                Size = new Size(90, 28)
            };
            btnBrowse.Click += OnBrowseJar;

            var btnDetect = new Button
            {
                Text = "Detect",
                Location = new Point(520, 44),
                Size = new Size(72, 28)
            };
            btnDetect.Click += OnDetectJar;

            var lblJarHint = new Label
            {
                Text = "Leave empty to use automatic PATH / FREEROUTING_JAR detection.",
                Location = new Point(16, 75),
                ForeColor = Color.DimGray,
                AutoSize = true
            };

            grpEngine.Controls.Add(lblJar);
            grpEngine.Controls.Add(_txtJarPath);
            grpEngine.Controls.Add(btnBrowse);
            grpEngine.Controls.Add(btnDetect);
            grpEngine.Controls.Add(lblJarHint);

            // 2. Routing Parameters Group
            var grpParams = new GroupBox
            {
                Text = "Auto-Routing Parameters",
                Location = new Point(16, 125),
                Size = new Size(608, 270)
            };

            var lblPasses = new Label
            {
                Text = "Max Passes (--router.autorouter.max_passes):",
                Location = new Point(16, 30),
                AutoSize = true
            };
            _numMaxPasses = new NumericUpDown
            {
                Location = new Point(440, 27),
                Size = new Size(150, 24),
                Minimum = 1,
                Maximum = 9999,
                Value = 20
            };

            var lblThreads = new Label
            {
                Text = "Concurrency Threads (--router.max_threads):",
                Location = new Point(16, 65),
                AutoSize = true
            };
            _numMaxThreads = new NumericUpDown
            {
                Location = new Point(440, 62),
                Size = new Size(150, 24),
                Minimum = 1,
                Maximum = 64,
                Value = 8
            };

            var lblCopper = new Label
            {
                Text = "Copper-to-Edge Clearance (\u00B5m):",
                Location = new Point(16, 100),
                AutoSize = true
            };
            _numCopperToEdge = new NumericUpDown
            {
                Location = new Point(440, 97),
                Size = new Size(150, 24),
                Minimum = 0,
                Maximum = 50000,
                DecimalPlaces = 1,
                Increment = 10,
                Value = 250
            };

            var lblExtra = new Label
            {
                Text = "Extra CLI Arguments:",
                Location = new Point(16, 135),
                Size = new Size(350, 20),
                AutoSize = false
            };
            _txtExtraArgs = new TextBox
            {
                Location = new Point(16, 158),
                Size = new Size(574, 24)
            };

            var lblExtraHint = new Label
            {
                Text = "Optional additional flags (e.g. --router.plane_as_obstacle=true).",
                Location = new Point(16, 190),
                ForeColor = Color.DimGray,
                AutoSize = true
            };

            var lnkCliDocs = new LinkLabel
            {
                Text = "Freerouting CLI Parameters & Flags Documentation",
                Location = new Point(16, 222),
                AutoSize = true,
                LinkColor = Color.FromArgb(0, 102, 204),
                ActiveLinkColor = Color.FromArgb(0, 60, 150)
            };
            lnkCliDocs.LinkClicked += (sender, e) =>
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("https://github.com/freerouting/freerouting/blob/master/docs/command_line_arguments.md")
                    {
                        UseShellExecute = true
                    });
                }
                catch { }
            };

            grpParams.Controls.Add(lblPasses);
            grpParams.Controls.Add(_numMaxPasses);
            grpParams.Controls.Add(lblThreads);
            grpParams.Controls.Add(_numMaxThreads);
            grpParams.Controls.Add(lblCopper);
            grpParams.Controls.Add(_numCopperToEdge);
            grpParams.Controls.Add(lblExtra);
            grpParams.Controls.Add(_txtExtraArgs);
            grpParams.Controls.Add(lblExtraHint);
            grpParams.Controls.Add(lnkCliDocs);

            // 3. Action Buttons & Project Link
            var lnkProject = new LinkLabel
            {
                Text = "freerouting.app",
                Location = new Point(16, 420),
                AutoSize = true,
                LinkColor = Color.DimGray,
                ActiveLinkColor = Color.FromArgb(0, 102, 204)
            };
            lnkProject.LinkClicked += (sender, e) =>
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("https://www.freerouting.app")
                    {
                        UseShellExecute = true
                    });
                }
                catch { }
            };

            var btnSave = new Button
            {
                Text = "Save",
                DialogResult = DialogResult.OK,
                Location = new Point(410, 412),
                Size = new Size(98, 32)
            };
            btnSave.Click += OnSave;

            var btnCancel = new Button
            {
                Text = "Cancel",
                DialogResult = DialogResult.Cancel,
                Location = new Point(518, 412),
                Size = new Size(98, 32)
            };

            this.Controls.Add(grpEngine);
            this.Controls.Add(grpParams);
            this.Controls.Add(lnkProject);
            this.Controls.Add(btnSave);
            this.Controls.Add(btnCancel);
            this.AcceptButton = btnSave;
            this.CancelButton = btnCancel;
        }

        private void LoadSettings()
        {
            _config = AltiumPluginConfig.Load();
            _txtJarPath.Text = _config.JarPath ?? "";
            _numMaxPasses.Value = Math.Max(1, Math.Min(9999, _config.MaxPasses));
            _numMaxThreads.Value = Math.Max(1, Math.Min(64, _config.MaxThreads));
            _numCopperToEdge.Value = (decimal)Math.Max(0.0, Math.Min(50000.0, _config.CopperToEdgeClearanceUm));
            _txtExtraArgs.Text = _config.ExtraArgs ?? "";
        }

        private void OnBrowseJar(object sender, EventArgs e)
        {
            using (var ofd = new OpenFileDialog())
            {
                ofd.Title = "Select Freerouting Executable JAR";
                ofd.Filter = "Java Archive (*.jar)|*.jar|All Files (*.*)|*.*";
                if (!string.IsNullOrEmpty(_txtJarPath.Text) && File.Exists(_txtJarPath.Text))
                {
                    ofd.InitialDirectory = Path.GetDirectoryName(_txtJarPath.Text);
                    ofd.FileName = Path.GetFileName(_txtJarPath.Text);
                }

                if (ofd.ShowDialog(this) == DialogResult.OK)
                {
                    _txtJarPath.Text = ofd.FileName;
                }
            }
        }

        private void OnDetectJar(object sender, EventArgs e)
        {
            string detected = FreeroutingProcessRunner.ResolveJarPath(null);
            if (!string.IsNullOrEmpty(detected) && File.Exists(detected))
            {
                _txtJarPath.Text = detected;
                MessageBox.Show("Found Freerouting JAR:\n" + detected, "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            else
            {
                MessageBox.Show("No Freerouting executable JAR detected automatically in standard locations.\n\nPlease click Browse... and select 'freerouting-current-executable.jar'.", "Freerouting", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }

        private void OnSave(object sender, EventArgs e)
        {
            _config.JarPath = _txtJarPath.Text.Trim();
            _config.MaxPasses = (int)_numMaxPasses.Value;
            _config.MaxThreads = (int)_numMaxThreads.Value;
            _config.CopperToEdgeClearanceUm = (double)_numCopperToEdge.Value;
            _config.ExtraArgs = _txtExtraArgs.Text.Trim();
            _config.Save();
            this.Close();
        }
    }
}
