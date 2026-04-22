package app.freerouting.io.specctra;

import app.freerouting.board.RoutingBoard;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Utility methods for loading {@link RoutingBoard} instances from DSN fixture files
 * inside the {@code fixtures/} directory. Intended for use in unit tests only.
 */
public final class DsnTestFixtures {

  private DsnTestFixtures() {
  }

  /**
   * Opens a DSN fixture file from the {@code fixtures/} directory as a stream.
   * Throws {@link UncheckedIOException} if the file cannot be found, so tests can
   * call this without a {@code throws} clause.
   *
   * @param filename the filename (e.g. {@code "Issue143-rpi_splitter.dsn"})
   */
  public static InputStream openResource(String filename) {
    try {
      return openFixtureStream(filename);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot open DSN fixture: " + filename, e);
    }
  }

  /**
   * Loads a board from a named DSN fixture file in the {@code fixtures/} directory.
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

  static InputStream openFixtureStream(String filename) throws IOException {
    Path searchDir = Path.of(".").toAbsolutePath();
    File candidate = Path.of(searchDir.toString(), "fixtures", filename).toFile();
    while (!candidate.exists()) {
      searchDir = searchDir.getParent();
      if (searchDir == null) {
        throw new IOException("Cannot find DSN fixture: " + filename
            + " — searched all ancestors of " + Path.of(".").toAbsolutePath());
      }
      candidate = Path.of(searchDir.toString(), "fixtures", filename).toFile();
    }
    return new FileInputStream(candidate);
  }

  private static RoutingBoard loadBoardFromStream(InputStream stream) throws IOException {
    DsnReadResult result = DsnReader.readBoard(stream, null, null);
    return switch (result) {
      case DsnReadResult.Success s -> (RoutingBoard) s.board();
      case DsnReadResult.OutlineMissing o -> (RoutingBoard) o.board();
      case DsnReadResult.ParseError e ->
          throw new IOException("DSN parse error at '" + e.location() + "': " + e.detail());
      case DsnReadResult.IoError io -> throw new IOException("DSN I/O error", io.cause());
    };
  }
}
