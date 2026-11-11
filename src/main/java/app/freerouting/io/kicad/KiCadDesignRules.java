package app.freerouting.io.kicad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The custom design rules of a KiCad board, read from its {@code .kicad_dru} file.
 *
 * <p>KiCad writes only the net class clearances into a Specctra design: the custom rules (a larger
 * clearance between a mains class and the rest of the board, a smaller one under a fine-pitch
 * part...) are lost on the way. This class reads them back; {@link KiCadDesignRulesTranslator}
 * turns their clearance constraints into clearance classes of the loaded board.
 *
 * <p>Only the rules matter here, in file order (a later matching rule overrides an earlier one, as
 * in KiCad). What is read but not translated (constraints other than a minimum clearance, the
 * severity) is kept, so that the translator can report it.
 */
public final class KiCadDesignRules {

  /**
   * One {@code (rule ...)} of the file.
   *
   * @param name the rule name
   * @param layer the argument of its {@code (layer ...)} clause, or null for all layers
   * @param condition its condition, or null if it has none (it then applies to every pair)
   * @param clearanceUm its minimum clearance in micrometres, or null if it sets none
   * @param ignored true for {@code (severity ignore)}: KiCad then reports nothing
   * @param otherConstraints the constraints Freerouting does not translate, as written
   * @param warnings what could not be read in the rule
   */
  public record Rule(
      String name,
      String layer,
      KiCadRuleCondition condition,
      Double clearanceUm,
      boolean ignored,
      List<String> otherConstraints,
      List<String> warnings) {}

  private final String sourceName;
  private final List<Rule> rules;
  private final List<String> warnings;

  private KiCadDesignRules(String sourceName, List<Rule> rules, List<String> warnings) {
    this.sourceName = sourceName;
    this.rules = List.copyOf(rules);
    this.warnings = List.copyOf(warnings);
  }

  /** The file name, for messages. */
  public String sourceName() {
    return sourceName;
  }

  /** The rules, in file order. */
  public List<Rule> rules() {
    return rules;
  }

  /** What could not be read outside of the rules. */
  public List<String> warnings() {
    return warnings;
  }

  /** Reads a {@code .kicad_dru} file. */
  public static KiCadDesignRules read(Path path) throws IOException {
    return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
  }

  /**
   * Parses the text of a {@code .kicad_dru} file.
   *
   * @throws IllegalArgumentException if the text is not a list of S-expressions
   */
  public static KiCadDesignRules parse(String text, String sourceName) {
    List<Object> expressions = new SexprReader(text).readAll();
    List<Rule> rules = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (Object expression : expressions) {
      if (!(expression instanceof List<?> list) || list.isEmpty()) {
        warnings.add("unexpected '" + expression + "' at top level, ignored");
        continue;
      }
      String head = String.valueOf(list.getFirst());
      if ("version".equals(head)) {
        continue;
      }
      if (!"rule".equals(head)) {
        warnings.add("'(" + head + " ...)' is not a rule and is not translated");
        continue;
      }
      rules.add(parseRule(list));
    }
    return new KiCadDesignRules(sourceName, rules, warnings);
  }

  private static Rule parseRule(List<?> list) {
    String name = list.size() > 1 ? String.valueOf(list.get(1)) : "";
    String layer = null;
    KiCadRuleCondition condition = null;
    Double clearance = null;
    boolean ignored = false;
    List<String> other = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (Object element : list.subList(Math.min(2, list.size()), list.size())) {
      if (!(element instanceof List<?> clause) || clause.isEmpty()) {
        warnings.add("unexpected '" + element + "'");
        continue;
      }
      String keyword = String.valueOf(clause.getFirst());
      switch (keyword) {
        case "condition" ->
            condition =
                KiCadRuleCondition.parse(clause.size() > 1 ? String.valueOf(clause.get(1)) : "");
        case "layer" -> layer = clause.size() > 1 ? String.valueOf(clause.get(1)) : null;
        case "severity" ->
            ignored = clause.size() > 1 && "ignore".equals(String.valueOf(clause.get(1)));
        case "constraint" -> {
          String type = clause.size() > 1 ? String.valueOf(clause.get(1)) : "";
          Double min = null;
          for (Object argument : clause.subList(Math.min(2, clause.size()), clause.size())) {
            if (argument instanceof List<?> arg
                && arg.size() > 1
                && "min".equals(String.valueOf(arg.getFirst()))) {
              min = KiCadRuleCondition.parseLength(String.valueOf(arg.get(1)), warnings);
              if (min == null) {
                warnings.add("minimum '" + arg.get(1) + "' of constraint " + type + " not read");
              }
            }
          }
          if ("clearance".equals(type) && min != null) {
            clearance = clearance == null ? min : Math.max(clearance, min);
          } else {
            other.add(type + (min != null ? " " + formatMm(min) : ""));
          }
        }
        default -> warnings.add("clause '" + keyword + "' not read");
      }
    }
    return new Rule(name, layer, condition, clearance, ignored, other, warnings);
  }

  /** A length in micrometres, written in millimetres for messages. */
  static String formatMm(double um) {
    return String.format(Locale.ROOT, "%.4g mm", um / 1000.0);
  }

  /** A minimal S-expression reader: lists, symbols and double-quoted strings, # comments. */
  private static final class SexprReader {
    private final String text;
    private int pos;

    SexprReader(String text) {
      this.text = text;
    }

    List<Object> readAll() {
      List<Object> result = new ArrayList<>();
      while (true) {
        skip();
        if (pos >= text.length()) {
          return result;
        }
        result.add(read());
      }
    }

    private void skip() {
      while (pos < text.length()) {
        char c = text.charAt(pos);
        if (Character.isWhitespace(c)) {
          pos++;
        } else if (c == '#') {
          while (pos < text.length() && text.charAt(pos) != '\n') {
            pos++;
          }
        } else {
          return;
        }
      }
    }

    private Object read() {
      skip();
      if (pos >= text.length()) {
        throw new IllegalArgumentException("unexpected end of the rules file");
      }
      char c = text.charAt(pos);
      if (c == '(') {
        pos++;
        List<Object> list = new ArrayList<>();
        while (true) {
          skip();
          if (pos >= text.length()) {
            throw new IllegalArgumentException("missing ')' in the rules file");
          }
          if (text.charAt(pos) == ')') {
            pos++;
            return list;
          }
          list.add(read());
        }
      }
      if (c == ')') {
        throw new IllegalArgumentException("unexpected ')' in the rules file");
      }
      if (c == '"') {
        pos++;
        StringBuilder result = new StringBuilder();
        while (pos < text.length() && text.charAt(pos) != '"') {
          char current = text.charAt(pos++);
          if (current == '\\' && pos < text.length()) {
            current = text.charAt(pos++);
          }
          result.append(current);
        }
        if (pos >= text.length()) {
          throw new IllegalArgumentException("unterminated string in the rules file");
        }
        pos++;
        return result.toString();
      }
      int start = pos;
      while (pos < text.length()
          && !Character.isWhitespace(text.charAt(pos))
          && text.charAt(pos) != '('
          && text.charAt(pos) != ')') {
        pos++;
      }
      return text.substring(start, pos);
    }
  }
}
