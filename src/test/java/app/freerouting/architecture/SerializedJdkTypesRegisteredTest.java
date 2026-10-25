package app.freerouting.architecture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.settings.sources.TestingSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for native-image serialization: every class actually written by a board snapshot
 * (including JDK collection types held in domain fields, e.g. EnumMap/EnumSet added to BoardRules
 * for meander constraints) must be registered "serializable" in reachability-metadata.json. The
 * static ArchUnit test only sees declared domain classes, not the runtime types behind Map/Set
 * fields.
 */
class SerializedJdkTypesRegisteredTest extends RoutingFixtureTest {

  private static final String METADATA = "META-INF/native-image/reachability-metadata.json";

  /** ObjectOutputStream that records every class descriptor it writes. */
  private static final class RecordingStream extends ObjectOutputStream {
    final Set<String> written = new TreeSet<>();

    RecordingStream(OutputStream out) throws IOException {
      super(out);
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
      Class<?> c = desc.forClass();
      if (c != null && !c.isArray() && !c.isPrimitive() && !c.isEnum()) {
        written.add(c.getName());
      }
      super.writeClassDescriptor(desc);
    }
  }

  @Test
  @DisplayName("All classes written by a board snapshot are registered for native serialization")
  void snapshotClassesAreRegistered() throws Exception {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob("Issue957-smoke-ldo-led.dsn", settings);
    assertNotNull(job);
    runRoutingJob(job); // loads the board (same pattern as Issue957SmokeRoutingTest)
    assertNotNull(job.board);
    RecordingStream stream = new RecordingStream(new ByteArrayOutputStream());
    stream.writeObject(job.board);
    stream.close();

    Set<String> registered = new TreeSet<>();
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(METADATA)) {
      JsonObject root =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
      for (JsonElement e : root.getAsJsonArray("reflection")) {
        JsonObject o = e.getAsJsonObject();
        if (o.has("serializable") && o.get("serializable").getAsBoolean()) {
          registered.add(o.get("type").getAsString());
        }
      }
    }
    Set<String> missing = new TreeSet<>(stream.written);
    missing.removeAll(registered);
    assertTrue(missing.isEmpty(), "Serialized but not registered for native image: " + missing);
  }
}

