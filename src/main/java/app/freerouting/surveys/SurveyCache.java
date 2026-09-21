package app.freerouting.surveys;

import app.freerouting.logger.FRLogger;
import app.freerouting.util.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Local, privacy-friendly record of micro-surveys this installation has already answered or
 * dismissed ("ask once" guarantee).
 *
 * <p>The cache is stored as {@code surveys.json} inside {@link
 * app.freerouting.settings.AppPaths#resolveDataDirectory(String, String, java.util.Map)} —
 * deliberately not in {@code WorkspaceSettings}, which resets on every board load. A corrupted or
 * unreadable file never crashes the client: it is backed up as {@code surveys.json.bad} and the
 * cache restarts empty, so at worst a user sees a survey they already answered once more.
 */
public class SurveyCache {

  private static final String FILE_NAME = "surveys.json";

  private final Path file;
  private State state;

  /** Serialized on-disk format of the cache. */
  private static class State {
    @SerializedName("schema_version")
    int schemaVersion = 1;

    @SerializedName("answered")
    Set<String> answered = new HashSet<>();

    @SerializedName("dismissed")
    Set<String> dismissed = new HashSet<>();
  }

  /** Creates a cache backed by {@code dataDirectory/surveys.json}; the file is loaded lazily. */
  public SurveyCache(Path dataDirectory) {
    this.file = dataDirectory == null ? null : dataDirectory.resolve(FILE_NAME);
  }

  /** Returns {@code true} if the survey was already answered or dismissed. */
  public synchronized boolean isHandled(String surveyId) {
    if (surveyId == null || file == null) {
      return false;
    }
    ensureLoaded();
    return state.answered.contains(surveyId) || state.dismissed.contains(surveyId);
  }

  /** Marks the survey as answered and persists the cache. */
  public synchronized void markAnswered(String surveyId) {
    mark(surveyId, true);
  }

  /** Marks the survey as dismissed (closed without answering) and persists the cache. */
  public synchronized void markDismissed(String surveyId) {
    mark(surveyId, false);
  }

  private void mark(String surveyId, boolean answered) {
    if (surveyId == null || surveyId.isBlank() || file == null) {
      return;
    }
    ensureLoaded();
    boolean changed = answered ? state.answered.add(surveyId) : state.dismissed.add(surveyId);
    if (changed) {
      save();
    }
  }

  private void ensureLoaded() {
    if (state != null) {
      return;
    }
    state = loadState();
  }

  private State loadState() {
    if (file == null || !Files.isRegularFile(file)) {
      return new State();
    }
    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      State loaded = GsonProvider.GSON.fromJson(json, State.class);
      if (loaded == null) {
        return new State();
      }
      if (loaded.answered == null) {
        loaded.answered = new HashSet<>();
      }
      if (loaded.dismissed == null) {
        loaded.dismissed = new HashSet<>();
      }
      return loaded;
    } catch (Exception e) {
      // Corrupted cache: keep a forensic copy and start clean rather than crashing.
      FRLogger.warn("Survey cache at '" + file + "' is unreadable, starting a clean one: " + e);
      quarantineCorruptFile(file);
      return new State();
    }
  }

  private static void quarantineCorruptFile(Path file) {
    try {
      Files.move(
          file,
          file.resolveSibling(file.getFileName() + ".bad"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ignored) {
      // Best effort only — the fresh in-memory cache still works.
    }
  }

  private void save() {
    if (file == null) {
      return;
    }
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(tmp, GsonProvider.GSON.toJson(state), StandardCharsets.UTF_8);
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      FRLogger.warn("Could not persist the survey cache to '" + file + "': " + e);
    }
  }
}
