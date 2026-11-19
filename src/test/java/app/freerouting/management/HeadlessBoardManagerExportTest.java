package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The export methods publish routing snapshots while a job runs. They must write the same bytes as
 * the save methods, without recording a saved state (which serialises the whole board again as DSN
 * for its checksum).
 */
class HeadlessBoardManagerExportTest {
  private static final long UNSAVED = -1L;
  private static final String DESIGN = "export-regression.dsn";

  private HeadlessBoardManager manager;

  @BeforeEach
  void setUp() throws Exception {
    GlobalSettings.setCurrent(new GlobalSettings());
    FRAnalytics.setEnabled(false);
    manager = new HeadlessBoardManager(new RoutingJob());
    manager.replaceRoutingBoard(DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn"));
    manager.originalBoardChecksum = UNSAVED;
  }

  @Test
  void sessionExportWritesTheSavedBytesWithoutRecordingASavedState() {
    ByteArrayOutputStream exported = new ByteArrayOutputStream();
    assertTrue(manager.exportSpecctraSessionSes(exported, DESIGN));
    assertEquals(UNSAVED, manager.originalBoardChecksum, "An export is not a save");

    ByteArrayOutputStream saved = new ByteArrayOutputStream();
    assertTrue(manager.saveAsSpecctraSessionSes(saved, DESIGN));
    assertNotEquals(UNSAVED, manager.originalBoardChecksum, "A save records the saved state");
    assertTrue(exported.size() > 0);
    assertArrayEquals(saved.toByteArray(), exported.toByteArray());
  }

  @Test
  void fusionScriptExportWritesTheSavedBytesWithoutRecordingASavedState() {
    ByteArrayOutputStream exported = new ByteArrayOutputStream();
    assertTrue(manager.exportFusionScriptScr(exported, DESIGN));
    assertEquals(UNSAVED, manager.originalBoardChecksum, "An export is not a save");

    ByteArrayOutputStream saved = new ByteArrayOutputStream();
    assertTrue(manager.saveAsFusionScriptScr(saved, DESIGN));
    assertNotEquals(UNSAVED, manager.originalBoardChecksum, "A save records the saved state");
    assertTrue(exported.size() > 0);
    assertArrayEquals(saved.toByteArray(), exported.toByteArray());
  }
}
