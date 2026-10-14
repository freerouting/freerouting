package app.freerouting.management.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingJobMonitorTest {
  @BeforeEach
  void setUp() {
    GlobalSettings.setCurrent(new GlobalSettings());
    FRAnalytics.setEnabled(false);
  }

  @Test
  void completedWorkersReleaseTheirMonitors() throws Exception {
    Set<Thread> existing = Thread.getAllStackTraces().keySet();
    Set<Thread> monitors = new HashSet<>();
    List<RoutingJob> jobs = new ArrayList<>();
    try {
      for (int attempt = 0; attempt < 3; attempt++) {
        RoutingJob job = idleJob();
        jobs.add(job);
        RoutingJobSchedulerActionThread action = new RoutingJobSchedulerActionThread(job);
        job.thread = action;
        action.start();
        action.join(10000);
        assertFalse(action.isAlive(), "The disabled-stage worker must finish promptly");
        assertEquals(RoutingJobState.COMPLETED, job.state);
        monitors.addAll(newMonitors(existing));
      }
      long deadline = System.nanoTime() + 2_000_000_000L;
      while (monitors.stream().anyMatch(Thread::isAlive) && System.nanoTime() < deadline) {
        Thread.sleep(25);
      }
      assertFalse(
          monitors.stream().anyMatch(Thread::isAlive),
          "Completed jobs must not retain sleeping monitors");
    } finally {
      // The failing baseline must not leak threads into other test cases.
      for (RoutingJob job : jobs) {
        job.thread = null;
      }
      for (Thread monitor : monitors) {
        monitor.join(1500);
      }
    }
  }

  private static Set<Thread> newMonitors(Set<Thread> existing) {
    Set<Thread> monitors = new HashSet<>();
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      if (existing.contains(entry.getKey())) {
        continue;
      }
      for (StackTraceElement frame : entry.getValue()) {
        if (frame.getClassName().equals(RoutingJobSchedulerActionThread.class.getName())
            && frame.getMethodName().startsWith("lambda$threadAction$")) {
          monitors.add(entry.getKey());
        }
      }
    }
    return monitors;
  }

  private static RoutingJob idleJob() {
    RoutingJob job = new RoutingJob();
    String json =
        """
        {"designName":"MonitorTest","unit":"MM","resolution":1000.0,
         "layers":[{"index":0,"name":"F.Cu","type":"signal"},
                   {"index":1,"name":"B.Cu","type":"signal"}],
         "outline":{"corners":[{"x":0,"y":0},{"x":20,"y":0},
                                  {"x":20,"y":20},{"x":0,"y":20}]}}
        """;
    HeadlessBoardManager manager = new HeadlessBoardManager(job);
    manager.loadFromKiCadJson(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), null, null);
    job.board = manager.getRoutingBoard();
    job.routerSettings = new SettingsMerger(new DefaultSettings()).merge();
    job.routerSettings.fanout.enabled = false;
    job.routerSettings.setRunRouter(false);
    job.routerSettings.setRunOptimizer(false);
    job.state = RoutingJobState.RUNNING;
    return job;
  }
}
