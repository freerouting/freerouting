package app.freerouting.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ensures all domain and geometric types that participate in board serialization and hashing are
 * registered for Java serialization in Native Image reachability metadata.
 *
 * <p>Regression test for Issue #957: prevents UnsupportedFeatureError during native CLI execution.
 */
class NativeImageSerializationMetadataArchTest {

  private static final String METADATA_RESOURCE =
      "META-INF/native-image/reachability-metadata.json";

  @Test
  @DisplayName(
      "ComponentOutline and other board item classes must be declared serializable in reachability metadata")
  void boardItemsAreRegisteredForSerialization() throws Exception {
    Set<String> serializableTypes = loadSerializableTypesFromMetadata();
    assertFalse(
        serializableTypes.isEmpty(), "Reachability metadata must contain serializable types");

    // Explicit check for the root cause of Issue #957
    assertTrue(
        serializableTypes.contains("app.freerouting.board.model.items.ComponentOutline"),
        "ComponentOutline must be registered as serializable for native CLI execution (Issue #957)");
    assertTrue(
        serializableTypes.contains("app.freerouting.board.model.items.ObstacleArea"),
        "ObstacleArea must be registered as serializable");
    assertTrue(
        serializableTypes.contains("app.freerouting.board.model.items.ComponentObstacleArea"),
        "ComponentObstacleArea must be registered as serializable");
    assertTrue(
        serializableTypes.contains("app.freerouting.board.model.items.ConductionArea"),
        "ConductionArea must be registered as serializable");
    assertTrue(
        serializableTypes.contains("app.freerouting.board.model.items.ViaObstacleArea"),
        "ViaObstacleArea must be registered as serializable");
  }

  @Test
  @DisplayName(
      "All domain Serializable classes in board, geometry, rules, and library must be registered")
  void allDomainSerializableClassesAreRegistered() throws Exception {
    Set<String> serializableTypes = loadSerializableTypesFromMetadata();

    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "app.freerouting.board.model.items",
                "app.freerouting.geometry.planar",
                "app.freerouting.rules",
                "app.freerouting.core.library");

    Set<String> missingClasses = new HashSet<>();
    for (JavaClass javaClass : classes) {
      if (javaClass.isAssignableTo(java.io.Serializable.class)
          && !javaClass.isInterface()
          && !javaClass.isAnonymousClass()) {
        String className = javaClass.getName();
        if (!serializableTypes.contains(className)) {
          missingClasses.add(className);
        }
      }
    }

    assertTrue(
        missingClasses.isEmpty(),
        "The following domain Serializable classes are missing 'serializable: true' "
            + "in reachability-metadata.json (causes native image crash): "
            + missingClasses);
  }

  private Set<String> loadSerializableTypesFromMetadata() throws Exception {
    InputStream stream = getClass().getClassLoader().getResourceAsStream(METADATA_RESOURCE);
    assertNotNull(stream, "Reachability metadata resource not found: " + METADATA_RESOURCE);

    Set<String> serializableTypes = new HashSet<>();
    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      JsonArray reflection = root.getAsJsonArray("reflection");
      if (reflection != null) {
        for (JsonElement element : reflection) {
          JsonObject entry = element.getAsJsonObject();
          if (entry.has("serializable") && entry.get("serializable").getAsBoolean()) {
            serializableTypes.add(entry.get("type").getAsString());
          }
        }
      }
    }
    return serializableTypes;
  }
}
