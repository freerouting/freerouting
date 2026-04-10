package app.freerouting.designforms.specctra.io;

import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Utility methods for loading {@link RoutingBoard} instances from DSN fixture files
 * inside the {@code tests/} directory. Intended for use in unit tests only.
 */
final class DsnTestFixtures {

  private DsnTestFixtures() {
  }

  /**
   * Loads a board from a named DSN fixture file in the {@code tests/} directory.
   *
   * @param filename the filename (e.g. {@code "Issue143-rpi_splitter.dsn"})
   * @return the loaded routing board
   * @throws IOException if the file cannot be found or the DSN is invalid
   */
  static RoutingBoard loadBoard(String filename) throws IOException {
    return loadBoardFromStream(openFixtureStream(filename));
  }

  /**
   * Loads a board from raw DSN bytes (e.g. produced by {@link DsnWriter#write}).
   *
   * @param bytes DSN file content
   * @return the loaded routing board
   * @throws IOException if the DSN content is invalid
   */
  static RoutingBoard loadBoard(byte[] bytes) throws IOException {
    return loadBoardFromStream(new ByteArrayInputStream(bytes));
  }

  // -------------------------------------------------------------------------

  private static InputStream openFixtureStream(String filename) throws IOException {
    Path searchDir = Path.of(".").toAbsolutePath();
    File candidate = Path.of(searchDir.toString(), "tests", filename).toFile();
    while (!candidate.exists()) {
      searchDir = searchDir.getParent();
      if (searchDir == null) {
        throw new IOException("Cannot find DSN fixture: " + filename
            + " — searched all ancestors of " + Path.of(".").toAbsolutePath());
      }
      candidate = Path.of(searchDir.toString(), "tests", filename).toFile();
    }
    return new FileInputStream(candidate);
  }

  private static RoutingBoard loadBoardFromStream(InputStream stream) throws IOException {
    RoutingJob job = new RoutingJob(UUID.randomUUID());
    // Use DefaultSettings so that RouterSettings.applyBoardSpecificOptimizations
    // has fully-initialised scoring fields (plain new RouterSettings() leaves them null).
    job.routerSettings = new SettingsMerger(new DefaultSettings()).merge();
    HeadlessBoardManager manager = new HeadlessBoardManager(job);
    DsnFile.ReadResult result = manager.loadFromSpecctraDsn(
        stream, null, new ItemIdentificationNumberGenerator());
    if (result == DsnFile.ReadResult.ERROR) {
      throw new IOException("DsnFile.read returned ERROR while loading board from stream");
    }
    return manager.get_routing_board();
  }
}

