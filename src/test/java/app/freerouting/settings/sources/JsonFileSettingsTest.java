package app.freerouting.settings.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.freerouting.settings.RouterSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileSettingsTest {

  @TempDir Path tempDir;

  /** Helper: create a temp JSON file with the given content. */
  private Path write(String filename, String content) throws IOException {
    Path file = tempDir.resolve(filename);
    Files.writeString(file, content);
    return file;
  }

  // -------------------------------------------------------------------------
  // 1. Valid file containing { "router": { ... } }
  // -------------------------------------------------------------------------

  @Test
  void loadsMaxPassesFromValidRouterSection() throws Exception {
    Path file =
        write(
            "valid.json",
            """
        {
          "router": {
            "max_passes": 42
          }
        }
        """);

    JsonFileSettings sut = new JsonFileSettings(file);
    RouterSettings settings = sut.getSettings();

    assertNotNull(settings, "getSettings() must not return null for a valid file");
    assertNotNull(settings.maxPasses, "maxPasses should have been parsed from the router section");
    assertEquals(42, settings.maxPasses, "maxPasses should match the value in the JSON file");
  }

  @Test
  void returnsEmptySettingsWhenRouterSectionIsMissing() throws Exception {
    Path file =
        write(
            "no_router.json",
            """
        {
          "gui": {
            "enabled": true
          }
        }
        """);

    JsonFileSettings sut = new JsonFileSettings(file);
    RouterSettings settings = sut.getSettings();

    assertNotNull(
        settings, "getSettings() must not return null even when router section is absent");
    assertNull(settings.maxPasses, "maxPasses should be null (no router section in JSON)");
  }

  @Test
  void returnsEmptySettingsWhenRouterSectionIsNotAnObject() throws Exception {
    Path file =
        write(
            "router_not_object.json",
            """
        {
          "router": "not-an-object"
        }
        """);

    JsonFileSettings sut = new JsonFileSettings(file);
    RouterSettings settings = sut.getSettings();

    assertNotNull(
        settings, "getSettings() must not return null when router value is not an object");
    assertNull(settings.maxPasses, "maxPasses should be null when router is not a JSON object");
  }

  // -------------------------------------------------------------------------
  // 2. Missing file
  // -------------------------------------------------------------------------

  @Test
  void returnsEmptySettingsForMissingFile() {
    Path nonExistent = tempDir.resolve("does_not_exist.json");

    JsonFileSettings sut = new JsonFileSettings(nonExistent);
    RouterSettings settings = sut.getSettings();

    assertNotNull(settings, "getSettings() must not return null for a missing file");
    assertNull(settings.maxPasses, "maxPasses should be null when file does not exist");
  }

  // -------------------------------------------------------------------------
  // 3. Malformed JSON
  // -------------------------------------------------------------------------

  @Test
  void returnsEmptySettingsForMalformedJson() throws Exception {
    Path file = write("malformed.json", "{ this is not valid json !! }");

    JsonFileSettings sut = new JsonFileSettings(file);
    RouterSettings settings = sut.getSettings();

    assertNotNull(settings, "getSettings() must not return null for malformed JSON");
    assertNull(settings.maxPasses, "maxPasses should be null when JSON cannot be parsed");
  }

  // -------------------------------------------------------------------------
  // Metadata
  // -------------------------------------------------------------------------

  @Test
  void priorityIs10() {
    JsonFileSettings sut = new JsonFileSettings(tempDir.resolve("irrelevant.json"));
    assertEquals(10, sut.getPriority(), "JsonFileSettings must have priority 10");
  }

  @Test
  void sourceNameIsFreroutingJson() {
    JsonFileSettings sut = new JsonFileSettings(tempDir.resolve("irrelevant.json"));
    assertEquals("freerouting.json", sut.getSourceName());
  }
}
