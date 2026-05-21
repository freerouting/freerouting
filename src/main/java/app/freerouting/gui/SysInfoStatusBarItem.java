package app.freerouting.gui;

import com.sun.management.OperatingSystemMXBean;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class SysInfoStatusBarItem extends JPanel {
    private final JLabel lblMetrics;
    private final OperatingSystemMXBean osBean;
    private final ResourceBundle i18n;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollingTask;
    
    // Dynamic Metrics
    private volatile double cpuLoad = 0.0;
    private volatile double ramUsedGB = 0.0;
    private volatile double ramTotalGB = 0.0;
    private volatile double jvmUsedMB = 0.0;
    private volatile double jvmMaxMB = 0.0;
    private volatile double ssdFreeGB = 0.0;
    private volatile double ssdTotalGB = 0.0;
    private volatile String cpuTempStr = "N/A";
    private volatile String gpuUsageStr = "N/A";
    
    // Hardware Cache
    private static String gpuName = "Fetching...";
    private static String cpuName = "Fetching...";
    private static boolean staticDataFetched = false;
    private volatile boolean isDialogOpen = false;

    public SysInfoStatusBarItem(Locale locale) {
        this.i18n = loadResourceBundle(locale);

        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(280, 14));
        setToolTipText(getString("sysinfo_tooltip", "Task Manager & Hardware Diagnostics"));

        lblMetrics = new JLabel("CPU: --% | RAM: --% | SSD: --");
        lblMetrics.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        lblMetrics.setHorizontalAlignment(SwingConstants.RIGHT);
        add(lblMetrics, BorderLayout.CENTER);

        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        
        if (!staticDataFetched) {
            fetchStaticHardwareInfoAsync();
        }
        
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    startMonitoring();
                } else {
                    stopMonitoring();
                }
            }
        });

        setupClickAction();
    }

    /**
     * Smart resource loader that scans both package local paths and resource roots
     */
    private ResourceBundle loadResourceBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle("app.freerouting.gui.Sysinfo", locale);
        } catch (MissingResourceException e1) {
            try {
                return ResourceBundle.getBundle("Sysinfo", locale);
            } catch (MissingResourceException e2) {
                System.err.println("Warning: Sysinfo.properties base file not resolved. Using code fallback defaults.");
                return null;
            }
        }
    }

    private String getString(String key, String defaultStr) {
        if (i18n == null) return defaultStr;
        try {
            return i18n.getString(key);
        } catch (MissingResourceException e) {
            return defaultStr;
        }
    }

    private void fetchStaticHardwareInfoAsync() {
        staticDataFetched = true;
        Thread staticFetcher = new Thread(() -> {
            String os = System.getProperty("os.name").toLowerCase();
            try {
                if (os.contains("win")) {
                    gpuName = fetchWindowsGPURealHardware();
                    cpuName = runCmdAndGetFirstLine(new String[]{"wmic", "cpu", "get", "name"}, "Name");
                } else if (os.contains("nix") || os.contains("nux")) {
                    gpuName = runCmdAndGetFirstLine(new String[]{"sh", "-c", "lspci | grep VGA | cut -d ':' -f3"}, "");
                    cpuName = runCmdAndGetFirstLine(new String[]{"sh", "-c", "cat /proc/cpuinfo | grep 'model name' | head -n 1 | cut -d ':' -f2"}, "");
                } else if (os.contains("mac")) {
                    gpuName = "Apple Silicon Graphics Engine";
                    cpuName = runCmdAndGetFirstLine(new String[]{"sysctl", "-n", "machdep.cpu.brand_string"}, "");
                }
            } catch (Exception e) {
                gpuName = "Generic Graphics Device";
                cpuName = "Multi-Core Processor";
            }
        });
        staticFetcher.setDaemon(true);
        staticFetcher.setPriority(Thread.MIN_PRIORITY);
        staticFetcher.start();
    }

    private String fetchWindowsGPURealHardware() {
        try {
            Process p = new ProcessBuilder("wmic", "path", "win32_VideoController", "get", "name").start();
            List<String> devices = new ArrayList<>();
            try (BufferedReader ri = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = ri.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equalsIgnoreCase("Name")) devices.add(line);
                }
            }
            for (String device : devices) {
                String lower = device.toLowerCase();
                if (lower.contains("intel") || lower.contains("nvidia") || lower.contains("amd") || lower.contains("radeon") || lower.contains("geforce")) {
                    return device; 
                }
            }
            return !devices.isEmpty() ? devices.get(0) : "Generic Display Adaptor";
        } catch (Exception e) {
            return "Unknown GPU Engine";
        }
    }

    private synchronized void startMonitoring() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Freerouting-SysInfo");
                t.setDaemon(true);
                return t;
            });
        }
        adjustPollingRate(4000); 
    }

    private synchronized void adjustPollingRate(int delayMs) {
        if (pollingTask != null) pollingTask.cancel(false);
        pollingTask = scheduler.scheduleAtFixedRate(this::pollMetrics, 0, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void pollMetrics() {
        try {
            cpuLoad = osBean.getCpuLoad() * 100.0;
            
            long totalPhysicalMemory = osBean.getTotalMemorySize();
            long freePhysicalMemory = osBean.getFreeMemorySize();
            ramTotalGB = totalPhysicalMemory / (1024.0 * 1024.0 * 1024.0);
            ramUsedGB = (totalPhysicalMemory - freePhysicalMemory) / (1024.0 * 1024.0 * 1024.0);
            double ramPercent = (ramUsedGB / ramTotalGB) * 100.0;

            Runtime runtime = Runtime.getRuntime();
            jvmMaxMB = runtime.maxMemory() / (1024.0 * 1024.0);
            jvmUsedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
            
            File root = File.listRoots()[0];
            ssdTotalGB = root.getTotalSpace() / (1024.0 * 1024.0 * 1024.0);
            ssdFreeGB = root.getUsableSpace() / (1024.0 * 1024.0 * 1024.0);
            
            // CRITICAL OPTIMIZATION: Only fetch slow OS processes if dialog panel is actually open
            if (isDialogOpen) {
                cpuTempStr = fetchCPUTemperature();
                gpuUsageStr = fetchGPUUsage();
            }

            SwingUtilities.invokeLater(() -> {
                if (cpuLoad >= 0) {
                    lblMetrics.setText(String.format("CPU: %.0f%% | RAM: %.0f%% | SSD: %.0fG Free", cpuLoad, ramPercent, ssdFreeGB));
                    String tooltipFmt = getString("sysinfo_hover_tooltip", "CPU: %.0f%% | RAM Used: %.1f GB / %.1f GB | SSD Free: %.0f GB");
                    setToolTipText(String.format(tooltipFmt, cpuLoad, ramUsedGB, ramTotalGB, ssdFreeGB));
                }
            });
        } catch (Exception e) {}
    }

    private String fetchGPUUsage() {
        try {
            Process p = new ProcessBuilder("nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits").start();
            if (p.waitFor(150, TimeUnit.MILLISECONDS)) {
                try (BufferedReader ri = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = ri.readLine();
                    if (line != null && !line.isEmpty()) return line.trim() + "%";
                }
            }
        } catch (Exception e) {}
        return "N/A (Intel/AMD iGPU active)";
    }

    private String fetchCPUTemperature() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                Process p = new ProcessBuilder("wmic", "/namespace:\\\\root\\wmi", "PATH", "MSAcpi_ThermalZoneTemperatureGet", "get", "CurrentTemperature").start();
                if (p.waitFor(150, TimeUnit.MILLISECONDS)) {
                    try (BufferedReader ri = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = ri.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty() && !line.equalsIgnoreCase("CurrentTemperature")) {
                                double celsius = (Double.parseDouble(line) / 10.0) - 273.15;
                                if (celsius > 10 && celsius < 115 && celsius != 27.8) return String.format("%.0f°C", celsius);
                            }
                        }
                    }
                }
            } else if (os.contains("nix") || os.contains("nux")) {
                Process p = new ProcessBuilder("cat", "/sys/class/thermal/thermal_zone0/temp").start();
                if (p.waitFor(150, TimeUnit.MILLISECONDS)) {
                    try (BufferedReader ri = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line = ri.readLine();
                        if (line != null) return String.format("%.0f°C", Double.parseDouble(line.trim()) / 1000.0);
                    }
                }
            }
        } catch (Exception e) {}
        return "N/A (Locked BIOS)";
    }

    private String runCmdAndGetFirstLine(String[] cmd, String ignoreHeader) {
        try {
            Process p = new ProcessBuilder(cmd).start();
            if (p.waitFor(600, TimeUnit.MILLISECONDS)) {
                try (BufferedReader ri = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = ri.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.equalsIgnoreCase(ignoreHeader)) return line;
                    }
                }
            }
        } catch (Exception e) {}
        return "Unknown Processor Hardware";
    }

    private void setupClickAction() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { showDetailedDialog(); }
            @Override
            public void mouseEntered(MouseEvent e) { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); }
            @Override
            public void mouseExited(MouseEvent e) { setCursor(Cursor.getDefaultCursor()); }
        });
    }

    private void showDetailedDialog() {
        if (isDialogOpen) return;
        isDialogOpen = true;
        adjustPollingRate(1000); 

        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parentWindow, getString("sysinfo_dialog_title", "System Task Manager"), JDialog.ModalityType.MODELESS);
        dialog.setSize(520, 380); 
        dialog.setLocationRelativeTo(parentWindow);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel contentPanel = new JPanel(new GridLayout(10, 2, 8, 8));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        contentPanel.add(new JLabel(getString("sysinfo_cpu_label", "Processor (CPU):")));
        contentPanel.add(new JLabel(cpuName));
        
        contentPanel.add(new JLabel(getString("sysinfo_threads_label", "Available CPU Threads:")));
        contentPanel.add(new JLabel(String.valueOf(osBean.getAvailableProcessors())));

        contentPanel.add(new JLabel(getString("sysinfo_gpu_label", "Graphics Processor (GPU):")));
        contentPanel.add(new JLabel(gpuName));

        contentPanel.add(new JLabel(getString("sysinfo_cpu_load_label", "Total CPU Load:")));
        JLabel dlgCpu = new JLabel(String.format("%.2f %%", cpuLoad));
        contentPanel.add(dlgCpu);
        
        contentPanel.add(new JLabel(getString("sysinfo_gpu_util_label", "GPU Utilization:")));
        JLabel dlgGpu = new JLabel(gpuUsageStr);
        contentPanel.add(dlgGpu);

        contentPanel.add(new JLabel(getString("sysinfo_temp_label", "CPU Core Temperature:")));
        JLabel dlgTemp = new JLabel(cpuTempStr);
        contentPanel.add(dlgTemp);

        contentPanel.add(new JLabel(getString("sysinfo_ram_label", "System Physical RAM:")));
        JLabel dlgRam = new JLabel(String.format("%.2f GB / %.2f GB", ramUsedGB, ramTotalGB));
        contentPanel.add(dlgRam);
        
        contentPanel.add(new JLabel(getString("sysinfo_ssd_label", "Primary Disk Storage:")));
        JLabel dlgSsd = new JLabel(String.format("%.1f GB free / %.1f GB total", ssdFreeGB, ssdTotalGB));
        contentPanel.add(dlgSsd);

        contentPanel.add(new JLabel(getString("sysinfo_jvm_label", "Freerouting Heap Allocation:")));
        JLabel dlgJvm = new JLabel(String.format("%.1f MB / %.1f MB", jvmUsedMB, jvmMaxMB));
        contentPanel.add(dlgJvm);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JButton btnAdvanced = new JButton(getString("sysinfo_btn_advanced", "Advanced System Info..."));
        btnAdvanced.addActionListener(evt -> showAdvancedMetricsDialog(dialog));
        bottomPanel.add(btnAdvanced, BorderLayout.EAST);

        javax.swing.Timer dialogRefresher = new javax.swing.Timer(1000, evt -> {
            if (!dialog.isShowing()) {
                ((javax.swing.Timer) evt.getSource()).stop();
                isDialogOpen = false;
                adjustPollingRate(4000); 
                return;
            }
            dlgCpu.setText(String.format("%.2f %%", cpuLoad));
            dlgGpu.setText(gpuUsageStr);
            dlgTemp.setText(cpuTempStr);
            dlgRam.setText(String.format("%.2f GB / %.2f GB", ramUsedGB, ramTotalGB));
            dlgSsd.setText(String.format("%.1f GB free / %.1f GB total", ssdFreeGB, ssdTotalGB));
            dlgJvm.setText(String.format("%.1f MB / %.1f MB", jvmUsedMB, jvmMaxMB));
        });
        
        dialogRefresher.start();
        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showAdvancedMetricsDialog(JDialog parent) {
        JDialog advDialog = new JDialog(parent, getString("sysinfo_adv_title", "Advanced Multi-CPU & System Diagnostics"), JDialog.ModalityType.APPLICATION_MODAL);
        advDialog.setSize(620, 460);
        advDialog.setLocationRelativeTo(parent);
        
        JTextArea txtOutput = new JTextArea("Querying advanced internal processor telemetry...\n");
        txtOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtOutput.setEditable(false);
        txtOutput.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        advDialog.add(new JScrollPane(txtOutput));
        
        Thread advancedFetcher = new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=================================================================\n");
            sb.append("             JVM RUNTIME INDIVIDUAL THREAD DIAGNOSTICS           \n");
            sb.append("=================================================================\n");
            
            // Querying advanced active multi-threading environment allocations
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            sb.append(String.format(" Current Active Threads:    %d\n", threadBean.getThreadCount()));
            sb.append(String.format(" Peak Thread Allocation:     %d\n", threadBean.getPeakThreadCount()));
            sb.append(String.format(" Total Threads Initialized:  %d\n", threadBean.getTotalStartedThreadCount()));
            sb.append(String.format(" Background Daemon Threads:  %d\n", threadBean.getDaemonThreadCount()));
            
            sb.append("\n=================================================================\n");
            sb.append("             MULTI-CORE / SOCKET ENVIRONMENT PROFILES            \n");
            sb.append("=================================================================\n");
            
            String os = System.getProperty("os.name").toLowerCase();
            try {
                if (os.contains("win")) {
                    sb.append(getCmdOutput(new String[]{"wmic", "cpu", "get", "Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed", "/format:list"}));
                    sb.append("\n=================================================================\n");
                    sb.append("                   EXTENDED VIDEO CONTROLLER TOPOLOGY            \n");
                    sb.append("=================================================================\n");
                    sb.append(getCmdOutput(new String[]{"wmic", "path", "win32_VideoController", "get", "Name,DriverVersion,VideoProcessor", "/format:list"}));
                } else if (os.contains("nix") || os.contains("nux")) {
                    sb.append(getCmdOutput(new String[]{"sh", "-c", "lscpu | grep -E 'Socket|Core|Thread|MHz'"}));
                } else {
                    sb.append("Advanced system profiling commands not supported on this OS distribution platform.");
                }
            } catch (Exception e) {
                sb.append("Error parsing advanced system instrumentation hardware nodes.");
            }
            
            SwingUtilities.invokeLater(() -> txtOutput.setText(sb.toString()));
        });
        advancedFetcher.start();
        advDialog.setVisible(true);
    }

    private String getCmdOutput(String[] cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader ri = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = ri.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    sb.append(" ").append(line.trim()).append("\n");
                }
            }
        }
        return sb.toString();
    }
}