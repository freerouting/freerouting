package app.freerouting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves paths under the repository {@code fixtures/} directory for unit tests.
 *
 * <p>Gradle parallel test forks may run with a working directory outside the project root on
 * Windows (for example {@code C:\}). Walk-up from {@code user.dir} alone is therefore unreliable.
 * The {@code test} task injects {@code freerouting.test.fixtures.dir} pointing at the absolute
 * project fixtures path; this class prefers that property when present.
 */
public final class TestFixtures {

  private static final String FIXTURES_DIR_PROPERTY = "freerouting.test.fixtures.dir";

  private TestFixtures() {}

  /** Resolves a fixture path under the repository fixtures directory. */
  public static Path resolvePath(String filename) throws IOException {
    Path fromProperty = resolveFromSystemProperty(filename);
    if (fromProperty != null) {
      return fromProperty;
    }

    Path dir = Path.of(".").toAbsolutePath().normalize();
    while (dir != null) {
      Path candidate = dir.resolve("fixtures").resolve(filename);
      if (Files.exists(candidate)) {
        return candidate;
      }
      dir = dir.getParent();
    }

    throw new IOException(
        "Cannot find fixture: "
            + filename
            + " (user.dir="
            + Path.of(".").toAbsolutePath().normalize()
            + ")");
  }

  /** Resolves a fixture file under the repository fixtures directory. */
  public static File resolveFile(String filename) throws IOException {
    return resolvePath(filename).toFile();
  }

  private static Path resolveFromSystemProperty(String filename) throws IOException {
    String fixturesDir = System.getProperty(FIXTURES_DIR_PROPERTY);
    if (fixturesDir == null || fixturesDir.isBlank()) {
      return null;
    }
    Path candidate = Path.of(fixturesDir).resolve(filename).normalize();
    if (!Files.exists(candidate)) {
      throw new IOException("Fixture not found under " + fixturesDir + ": " + filename);
    }
    return candidate;
  }
}
