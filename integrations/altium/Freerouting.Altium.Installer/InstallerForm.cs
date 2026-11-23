using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace Freerouting.Altium.Installer
{
    public class InstallerForm : Form
    {
        private CheckedListBox _clbInstalls;
        private Button _btnBrowse;
        private Button _btnInstall;
        private Button _btnUninstall;
        private Button _btnClose;
        private TextBox _txtLog;
        private CheckBox _chkAutoCloseAltium;

        public InstallerForm()
        {
            InitializeComponent();
            RefreshInstallations();
        }

        private void InitializeComponent()
        {
            this.Text = "Freerouting - Altium Designer Integration Setup";
            this.Size = new Size(580, 480);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.Font = new Font("Segoe UI", 9F, FontStyle.Regular, GraphicsUnit.Point);

            // Banner Panel
            Panel pnlBanner = new Panel
            {
                Dock = DockStyle.Top,
                Height = 65,
                BackColor = Color.FromArgb(24, 43, 73)
            };
            Label lblTitle = new Label
            {
                Text = "Freerouting Auto-Router for Altium Designer",
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 12F, FontStyle.Bold),
                Location = new Point(16, 12),
                AutoSize = true
            };
            Label lblSubtitle = new Label
            {
                Text = "Install the Specctra DSN/SES integration extension directly into Altium Designer.",
                ForeColor = Color.Gainsboro,
                Font = new Font("Segoe UI", 8.5F),
                Location = new Point(17, 36),
                AutoSize = true
            };
            pnlBanner.Controls.Add(lblTitle);
            pnlBanner.Controls.Add(lblSubtitle);
            this.Controls.Add(pnlBanner);

            // Detected Installations Label
            Label lblInstalls = new Label
            {
                Text = "Detected Altium Designer Installations:",
                Location = new Point(16, 75),
                AutoSize = true
            };
            this.Controls.Add(lblInstalls);

            // CheckedListBox for Installations
            _clbInstalls = new CheckedListBox
            {
                Location = new Point(16, 95),
                Size = new Size(440, 90),
                CheckOnClick = true
            };
            this.Controls.Add(_clbInstalls);

            // Browse Button
            _btnBrowse = new Button
            {
                Text = "Browse...",
                Location = new Point(466, 95),
                Size = new Size(85, 28)
            };
            _btnBrowse.Click += BtnBrowse_Click;
            this.Controls.Add(_btnBrowse);

            // Auto-Close Altium CheckBox
            _chkAutoCloseAltium = new CheckBox
            {
                Text = "Automatically close Altium Designer (X2.exe) if running",
                Location = new Point(16, 192),
                Size = new Size(440, 22),
                Checked = true
            };
            this.Controls.Add(_chkAutoCloseAltium);

            // Action Buttons
            _btnInstall = new Button
            {
                Text = "Install Extension",
                Location = new Point(16, 222),
                Size = new Size(130, 32),
                BackColor = Color.FromArgb(0, 122, 204),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9F, FontStyle.Bold)
            };
            _btnInstall.Click += BtnInstall_Click;
            this.Controls.Add(_btnInstall);

            _btnUninstall = new Button
            {
                Text = "Uninstall",
                Location = new Point(155, 222),
                Size = new Size(95, 32)
            };
            _btnUninstall.Click += BtnUninstall_Click;
            this.Controls.Add(_btnUninstall);

            _btnClose = new Button
            {
                Text = "Exit",
                Location = new Point(466, 222),
                Size = new Size(85, 32)
            };
            _btnClose.Click += (s, e) => this.Close();
            this.Controls.Add(_btnClose);

            // Log Label & TextBox
            Label lblLog = new Label
            {
                Text = "Activity Log:",
                Location = new Point(16, 262),
                AutoSize = true
            };
            this.Controls.Add(lblLog);

            _txtLog = new TextBox
            {
                Location = new Point(16, 282),
                Size = new Size(535, 145),
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                BackColor = Color.FromArgb(245, 245, 245),
                Font = new Font("Consolas", 8.5F)
            };
            this.Controls.Add(_txtLog);
        }

        private void RefreshInstallations()
        {
            _clbInstalls.Items.Clear();
            var installs = AltiumDetector.FindInstallations();
            foreach (var inst in installs)
            {
                int idx = _clbInstalls.Items.Add(inst);
                _clbInstalls.SetItemChecked(idx, true);
            }

            if (installs.Count == 0)
            {
                Log("No Altium installations detected automatically. Use 'Browse...' to select your Altium directory.");
            }
            else
            {
                Log(string.Format("Found {0} Altium installation(s).", installs.Count));
            }
        }

        private void BtnBrowse_Click(object sender, EventArgs e)
        {
            using (FolderBrowserDialog fbd = new FolderBrowserDialog())
            {
                fbd.Description = "Select Altium Designer installation folder (e.g. C:\\Program Files\\Altium\\AD26)";
                if (fbd.ShowDialog() == DialogResult.OK)
                {
                    string sys = Path.Combine(fbd.SelectedPath, "System");
                    var custom = new AltiumInstallation
                    {
                        DisplayName = Path.GetFileName(fbd.SelectedPath),
                        Version = "Custom",
                        InstallPath = fbd.SelectedPath,
                        SystemPath = Directory.Exists(sys) ? sys : fbd.SelectedPath,
                        IsExtensionInstalled = File.Exists(Path.Combine(sys, "Freerouting.Altium.Extension.dll"))
                    };
                    int idx = _clbInstalls.Items.Add(custom);
                    _clbInstalls.SetItemChecked(idx, true);
                    Log(string.Format("Added custom installation path: {0}", fbd.SelectedPath));
                }
            }
        }

        private void BtnInstall_Click(object sender, EventArgs e)
        {
            ExecuteAction(false);
        }

        private void BtnUninstall_Click(object sender, EventArgs e)
        {
            ExecuteAction(true);
        }

        private void ExecuteAction(bool uninstall)
        {
            var selected = new List<AltiumInstallation>();
            foreach (var item in _clbInstalls.CheckedItems)
            {
                AltiumInstallation inst = item as AltiumInstallation;
                if (inst != null)
                {
                    selected.Add(inst);
                }
            }

            if (selected.Count == 0)
            {
                MessageBox.Show("Please select at least one Altium Designer installation.", "Freerouting Setup", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            if (AltiumDetector.IsAltiumRunning())
            {
                if (_chkAutoCloseAltium.Checked)
                {
                    Log("Altium Designer is running. Closing X2.exe to release DLL file locks...");
                    if (!AltiumDetector.CloseAltium())
                    {
                        MessageBox.Show("Could not close Altium Designer. Please close it manually and retry.", "Freerouting Setup", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                        return;
                    }
                }
                else
                {
                    MessageBox.Show("Altium Designer (X2.exe) is currently running. Please close it before proceeding.", "Freerouting Setup", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return;
                }
            }

            foreach (var inst in selected)
            {
                try
                {
                    if (uninstall)
                    {
                        Log(string.Format("Uninstalling from {0}...", inst.SystemPath));
                        AltiumDetector.UninstallExtension(inst, Log);
                        Log(string.Format("Freerouting uninstalled successfully from {0}.", inst.DisplayName));
                    }
                    else
                    {
                        Log(string.Format("Installing into {0}...", inst.SystemPath));
                        AltiumDetector.InstallExtension(inst, Log);
                        Log(string.Format("Freerouting installed successfully for {0}!", inst.DisplayName));
                    }
                }
                catch (Exception ex)
                {
                    Log(string.Format("Error for {0}: {1}", inst.DisplayName, ex.Message));
                    MessageBox.Show("Operation failed: " + ex.Message, "Freerouting Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }

            RefreshInstallations();
            if (!uninstall)
            {
                MessageBox.Show("Installation complete!\n\nOpen Altium Designer, load a PCB document, and access the menu:\nRoute -> Freerouting Auto-Router", "Freerouting Setup", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            else
            {
                MessageBox.Show("Uninstallation complete.", "Freerouting Setup", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }

        private void Log(string msg)
        {
            _txtLog.AppendText(string.Format("[{0:HH:mm:ss}] {1}\r\n", DateTime.Now, msg));
        }
    }
}
