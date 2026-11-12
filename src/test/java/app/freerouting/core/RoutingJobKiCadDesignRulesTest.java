package app.freerouting.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.logger.AllowErrorLogs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which .kicad_dru a routing job uses (-de, or explicit argument only). */
class RoutingJobKiCadDesignRulesTest {

  private static final String RULES =
      "(version 1)\n"
          + "(rule hv (condition \"A.NetClass == 'HV'\") (constraint clearance (min 3mm)))\n";

  @TempDir Path dir;

  private RoutingJob job() throws Exception {
    Path dsn = Files.writeString(dir.resolve("board.dsn"), "(pcb board (parser) (structure))");
    RoutingJob job = new RoutingJob(UUID.randomUUID());
    job.setInput(dsn.toFile());
    return job;
  }

  @Test
  void doesNotAutomaticallyLoadRulesFileNextToTheDesign() throws Exception {
    Files.writeString(dir.resolve("board.kicad_dru"), RULES);
    RoutingJob job = job();

    assertTrue(job.loadKiCadDesignRules(null));
    assertNull(
        job.kicadDesignRules, "Must not auto-load adjacent .kicad_dru without explicit input");
  }

  @Test
  @AllowErrorLogs("An explicit .kicad_dru that cannot be read is reported as an error")
  void anExplicitFileIsLoadedAndMustBeReadable() throws Exception {
    Path file = Files.writeString(dir.resolve("project.kicad_dru"), RULES);
    RoutingJob job = job();

    assertTrue(job.loadKiCadDesignRules(file.toString()));
    assertNotNull(job.kicadDesignRules);
    assertEquals("project.kicad_dru", job.kicadDesignRules.sourceName());
    assertEquals(1, job.kicadDesignRules.rules().size());

    RoutingJob missing = job();
    assertFalse(missing.loadKiCadDesignRules(dir.resolve("missing.kicad_dru").toString()));
    assertNull(missing.kicadDesignRules);
  }

  @Test
  void noRulesFileNoRules() throws Exception {
    RoutingJob job = job();

    assertTrue(job.loadKiCadDesignRules(null));
    assertNull(job.kicadDesignRules);
  }
}
