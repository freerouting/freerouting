package app.freerouting.fixtures;
import app.freerouting.settings.sources.TestingSettings;
import app.freerouting.core.RoutingJob;
import org.junit.jupiter.api.Test;
public class Issue676FullPassTest extends RoutingFixtureTest {
    private static final String FIXTURE = "Issue676-ch32v-tx118s.dsn";
    @Test
    void issue676_full_routing_3passes() {
        TestingSettings ts = new TestingSettings();
        ts.setMaxPasses(3);
        ts.setJobTimeoutString("00:02:00");
        RoutingJob job = GetRoutingJob(FIXTURE, ts);
        RunRoutingJob(job);
    }
}
