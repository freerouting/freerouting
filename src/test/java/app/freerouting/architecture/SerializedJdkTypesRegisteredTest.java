package app.freerouting.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
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
 * (including JDK collection types held in domain fields, such as the EnumMap of meander
 * constraints) must be registered "serializable" in reachability-metadata.json. The static ArchUnit
 * test only sees declared domain classes, not the runtime types behind Map and Set fields.
 */
class SerializedJdkTypesRegisteredTest extends RoutingFixtureTest {

  private static final String SMOKE_FIXTURE = "Issue957-smoke-ldo-led.dsn";
  private static final String GOLDEN_WORKLOAD_FIXTURE = "Issue508-DAC2020_bm01.dsn";
  private static final String METADATA = "META-INF/native-image/reachability-metadata.json";

  /** ObjectOutputStream that records every class descriptor it writes. */
  private static final class RecordingStream extends ObjectOutputStream {
    private final Set<String> written = new TreeSet<>();

    RecordingStream(OutputStream out) throws IOException {
      super(out);
      enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
      if (obj != null) {
        recordHierarchy(obj.getClass());
      }
      return super.replaceObject(obj);
    }

    private void recordHierarchy(Class<?> clazz) {
      Class<?> current = clazz;
      while (current != null && java.io.Serializable.class.isAssignableFrom(current)) {
        if (!current.isArray() && !current.isPrimitive() && !current.isEnum()) {
          written.add(current.getName());
        }
        current = current.getSuperclass();
      }
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
      Class<?> writtenClass = desc.forClass();
      if (writtenClass != null
          && !writtenClass.isArray()
          && !writtenClass.isPrimitive()
          && !writtenClass.isEnum()) {
        written.add(writtenClass.getName());
      }
      super.writeClassDescriptor(desc);
    }
  }

  @Test
  @DisplayName("Issue 957 snapshot classes are registered for native serialization")
  void smokeBoardSnapshotClassesAreRegistered() throws Exception {
    assertSnapshotClassesAreRegistered(SMOKE_FIXTURE, null);
  }

  @Test
  @DisplayName("Golden-workload snapshot classes are registered for native serialization")
  void goldenWorkloadSnapshotClassesAreRegistered() throws Exception {
    // Same board as the native PGO profile step. Two items are enough: the snapshot still writes
    // the whole loaded item list, which is where a board-specific JDK type would show up.
    assertSnapshotClassesAreRegistered(GOLDEN_WORKLOAD_FIXTURE, 2);
  }

  private void assertSnapshotClassesAreRegistered(String fixture, Integer maxItems)
      throws Exception {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:02:00");
    if (maxItems != null) {
      settings.setMaxItems(maxItems);
    }
    RoutingJob job = getRoutingJob(fixture, settings);
    assertNotNull(job);
    assertNotNull(job.routerSettings);
    assertNotNull(job.routerSettings.autorouter);
    // getRoutingJob calls TestingSettings.setMaxPasses(100), which keeps the first non-null
    // value. The scheduler reads the merged RouterSettings, so the bound has to be assigned here.
    job.routerSettings.setMaxPasses(1);
    job.routerSettings.jobTimeoutString = "00:02:00";
    if (maxItems != null) {
      job.routerSettings.autorouter.maxItems = maxItems;
    }

    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    BasicBoard board = job.board;
    assertNotNull(board);
    assertNotNull(board.getHash(), "Trace-profile snapshot must serialize on the JVM");
    assertNotNull(board.clone(), "Full-board snapshot must serialize on the JVM");

    Set<String> missing = classesWrittenByBothSnapshots(board);
    missing.removeAll(registeredSerializableTypes());
    assertTrue(missing.isEmpty(), "Serialized but not registered for native image: " + missing);
  }

  /**
   * Writes the same object graphs as {@code BoardSnapshotManager.serialize}: the full board, then
   * the trace-profile roots (traces, vias, item list). The second graph is what failed on {@code
   * java.lang.Number} after the full-board {@code EnumMap} failure.
   */
  private static Set<String> classesWrittenByBothSnapshots(BasicBoard board) throws IOException {
    RecordingStream stream = new RecordingStream(new ByteArrayOutputStream());
    stream.writeObject(board);
    stream.writeObject(board.getTraces());
    stream.writeObject(board.getVias());
    stream.writeObject(board.itemList);
    stream.close();

    Set<String> allClasses = new TreeSet<>(stream.written);
    allClasses.addAll(collectSerializableRuntimeClasses(board));
    allClasses.addAll(collectSerializableRuntimeClasses(board.getTraces()));
    allClasses.addAll(collectSerializableRuntimeClasses(board.getVias()));
    allClasses.addAll(collectSerializableRuntimeClasses(board.itemList));
    return allClasses;
  }

  private static Set<String> collectSerializableRuntimeClasses(Object root) {
    Set<String> classes = new TreeSet<>();
    Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    java.util.Deque<Object> queue = new java.util.ArrayDeque<>();
    if (root != null) {
      queue.add(root);
    }
    while (!queue.isEmpty()) {
      Object current = queue.poll();
      if (current == null || !visited.add(current)) {
        continue;
      }
      Class<?> cl = current.getClass();
      if (cl.isArray()) {
        if (!cl.getComponentType().isPrimitive()) {
          int length = java.lang.reflect.Array.getLength(current);
          for (int i = 0; i < length; i++) {
            Object element = java.lang.reflect.Array.get(current, i);
            if (element != null) {
              queue.add(element);
            }
          }
        }
        continue;
      }
      if (current instanceof Class || current instanceof ClassLoader) {
        continue;
      }
      Class<?> c = cl;
      while (c != null && java.io.Serializable.class.isAssignableFrom(c)) {
        if (!c.isEnum() && !c.isPrimitive() && !c.isArray()) {
          classes.add(c.getName());
        }
        c = c.getSuperclass();
      }
      for (Class<?> h = cl;
          h != null && java.io.Serializable.class.isAssignableFrom(h);
          h = h.getSuperclass()) {
        for (java.lang.reflect.Field f : h.getDeclaredFields()) {
          if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
              || java.lang.reflect.Modifier.isTransient(f.getModifiers())) {
            continue;
          }
          if (f.getType().isPrimitive()) {
            continue;
          }
          try {
            f.setAccessible(true);
            Object val = f.get(current);
            if (val != null) {
              queue.add(val);
            }
          } catch (Exception ignored) {
            // Inaccessible reflective fields are skipped safely
          }
        }
      }
      if (current instanceof java.util.Collection<?> col) {
        for (Object elem : col) {
          if (elem != null) {
            queue.add(elem);
          }
        }
      } else if (current instanceof java.util.Map<?, ?> map) {
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
          if (entry.getKey() != null) {
            queue.add(entry.getKey());
          }
          if (entry.getValue() != null) {
            queue.add(entry.getValue());
          }
        }
      }
    }
    return classes;
  }

  private static Set<String> registeredSerializableTypes() throws IOException {
    Set<String> registered = new TreeSet<>();
    try (InputStream in =
        SerializedJdkTypesRegisteredTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      assertNotNull(in, "Reachability metadata resource not found: " + METADATA);
      JsonObject root =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
      for (JsonElement element : root.getAsJsonArray("reflection")) {
        JsonObject entry = element.getAsJsonObject();
        if (entry.has("serializable") && entry.get("serializable").getAsBoolean()) {
          registered.add(entry.get("type").getAsString());
        }
      }
    }
    return registered;
  }
}
