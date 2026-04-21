package app.freerouting;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreeroutingCliTest
{
  @BeforeEach
  void setUp()
  {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void applyCliRouterSettingsUsesConfiguredMaxPasses() throws IOException
  {
    RoutingJob routingJob = new RoutingJob();
    routingJob.setInput(findTestFile("Issue313-FastTest.dsn"));
    Freerouting.globalSettings.routerSettings.maxPasses = 4;

    Freerouting.applyCliRouterSettings(routingJob, Freerouting.globalSettings);

    assertEquals(1, routingJob.routerSettings.get_start_pass_no());
    assertEquals(4, routingJob.routerSettings.get_stop_pass_no());
  }

  private File findTestFile(String filename)
  {
    Path directory = Path.of(".").toAbsolutePath();
    while (directory != null)
    {
      File candidate = directory.resolve("tests").resolve(filename).toFile();
      if (candidate.exists())
      {
        return candidate;
      }
      directory = directory.getParent();
    }
    throw new IllegalArgumentException("Test fixture not found: " + filename);
  }
}