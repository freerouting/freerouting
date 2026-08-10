package app.freerouting.rewrite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

/**
 * Second pass for Phase 2a: textually applies collected field renames project-wide.
 *
 * <p>Runs after {@link RenameInstanceFieldsToCamelCase} in the same {@code rewriteRun} when the
 * static rename map or {@code src/rewrite/.field-renames.tsv} is populated.
 */
public class ApplyCollectedFieldRenames extends Recipe {

  private static final Path RENAME_MAP_PATH = Path.of("build", "field-renames.tsv");
  private static final Path RENAME_MAP_FALLBACK_PATH = Path.of("src/rewrite/.field-renames.tsv");

  @Override
  public String getDisplayName() {
    return "Apply collected instance field renames";
  }

  @Override
  public String getDescription() {
    return "Applies collected field renames to remaining references using word-boundary "
        + "replacement.";
  }

  @Override
  public Set<String> getTags() {
    return new LinkedHashSet<>(Arrays.asList("RSPEC-S116", "freerouting"));
  }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(1);
  }

  static Map<String, String> loadRenameMap() {
    for (Path path : List.of(RENAME_MAP_PATH, RENAME_MAP_FALLBACK_PATH)) {
      if (!Files.isRegularFile(path)) {
        continue;
      }
      try {
        Map<String, String> renames = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
          if (line.isBlank()) {
            continue;
          }
          String[] parts = line.split("\t", 2);
          if (parts.length == 2 && !parts[0].equals(parts[1])) {
            renames.put(parts[0], parts[1]);
          }
        }
        if (!renames.isEmpty()) {
          return renames;
        }
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read " + path, e);
      }
    }
    return Map.of();
  }

  static Map<String, String> collectedRenames() {
    if (!RenameInstanceFieldsToCamelCase.COLLECTED_RENAMES.isEmpty()) {
      return new LinkedHashMap<>(RenameInstanceFieldsToCamelCase.COLLECTED_RENAMES);
    }
    return loadRenameMap();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    Map<String, String> renames = collectedRenames();
    if (renames.isEmpty()) {
      return TreeVisitor.noop();
    }
    return new ApplyFieldRenamesTextually(renames);
  }
}
