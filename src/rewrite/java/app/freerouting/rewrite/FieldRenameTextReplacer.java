package app.freerouting.rewrite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Applies field renames with patterns that avoid method calls and bare local-variable names.
 *
 * <p>Patterns:
 *
 * <ul>
 *   <li>{@code .oldName} when not followed by {@code (} or another name character
 *   <li>{@code this.oldName} with the same boundary rule
 *   <li>unqualified {@code oldName.} — inherited field starting a member-select chain
 * </ul>
 */
final class FieldRenameTextReplacer {

  private FieldRenameTextReplacer() {}

  static String apply(String source, Map<String, String> renames) {
    if (renames.isEmpty() || source.isEmpty()) {
      return source;
    }
    List<String> sortedOldNames = new ArrayList<>(renames.keySet());
    sortedOldNames.sort(Comparator.comparingInt(String::length).reversed());

    String updated = source;
    for (String oldName : sortedOldNames) {
      String newName = renames.get(oldName);
      if (newName == null || oldName.equals(newName)) {
        continue;
      }
      String escaped = Pattern.quote(oldName);
      updated =
          Pattern.compile("\\." + escaped + "(?!\\s*\\()(?![A-Za-z0-9_])")
              .matcher(updated)
              .replaceAll("." + newName);
      updated =
          Pattern.compile("(?<![.\\w])this\\." + escaped + "\\b(?!\\s*\\()(?![A-Za-z0-9_])")
              .matcher(updated)
              .replaceAll("this." + newName);
      updated =
          Pattern.compile("(?<![.\\w])" + escaped + "(?=\\.)")
              .matcher(updated)
              .replaceAll(newName);
    }
    return updated;
  }
}
