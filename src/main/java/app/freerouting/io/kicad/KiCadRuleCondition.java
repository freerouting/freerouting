package app.freerouting.io.kicad;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The condition of a KiCad custom design rule ({@code (condition "A.NetClass == 'HV'")}), reduced
 * to what a Specctra board knows about its items.
 *
 * <p>The evaluation follows KiCad 10 ({@code pcbexpr_evaluator.cpp}, {@code libeval_compiler.cpp},
 * {@code pcbexpr_functions.cpp}): string comparisons ignore case and accept the {@code *} and
 * {@code ?} wildcards, {@code A.NetClass == 'X'} also matches a composite class such as {@code
 * X,Default}, a property the item does not have makes both {@code ==} and {@code !=} false, and a
 * condition holds if it holds for (A, B) or for (B, A).
 *
 * <p>The result has three values. A part of the condition that Freerouting cannot evaluate (a
 * geometric function, an unknown property, a numeric comparison of a property...) is {@link
 * Tri#UNKNOWN}, and {@link #unsupportedParts()} names it, so that the caller can apply the rule
 * wherever it may match and warn about it.
 */
public final class KiCadRuleCondition {

  /** A truth value which may be unknown (Kleene logic). */
  public enum Tri {
    TRUE,
    FALSE,
    UNKNOWN;

    static Tri of(boolean value) {
      return value ? TRUE : FALSE;
    }

    Tri and(Tri other) {
      if (this == FALSE || other == FALSE) {
        return FALSE;
      }
      return this == TRUE && other == TRUE ? TRUE : UNKNOWN;
    }

    Tri or(Tri other) {
      if (this == TRUE || other == TRUE) {
        return TRUE;
      }
      return this == FALSE && other == FALSE ? FALSE : UNKNOWN;
    }

    Tri not() {
      return this == UNKNOWN ? UNKNOWN : of(this == FALSE);
    }
  }

  /** The kind of a board item, as the KiCad {@code Type} property names it. */
  public enum ItemType {
    PAD("Pad"),
    TRACK("Track"),
    VIA("Via"),
    ZONE("Zone");

    final String kicadName;

    ItemType(String kicadName) {
      this.kicadName = kicadName;
    }
  }

  /**
   * What a condition may ask about a copper item of the board.
   *
   * @param type the kind of the item
   * @param netName the name of its net, empty for an item without net
   * @param netClass the KiCad name of the class of its net, {@code Default} without net; a
   *     composite class is named by its constituents joined with commas ({@code HV,Default})
   * @param footprint the reference of the footprint of a pad, else null
   * @param padNumber the number of a pad, else null
   * @param smd for a pad, true if it has copper on a single layer (KiCad {@code SMD} pad; an edge
   *     connector pad cannot be told apart in a Specctra design and is seen as one too)
   */
  public record Item(
      ItemType type,
      String netName,
      String netClass,
      String footprint,
      String padNumber,
      boolean smd) {}

  private static final Set<String> PROPERTIES =
      Set.of("netclass", "netname", "type", "pad_type", "reference", "parent", "pad_number");
  private static final Set<String> FUNCTIONS =
      Set.of("memberoffootprint", "hasnetclass", "isplated");

  private final String expression;
  private final Node root;
  private final List<String> unsupportedParts = new ArrayList<>();

  private KiCadRuleCondition(String expression) {
    this.expression = expression;
    Node parsed;
    try {
      Parser parser = new Parser(expression);
      parsed = parser.parseExpression();
      if (!parser.isFinished()) {
        throw new IllegalArgumentException("unexpected '" + parser.rest() + "'");
      }
    } catch (IllegalArgumentException e) {
      unsupportedParts.add("unparsable condition (" + e.getMessage() + ")");
      parsed = new Unknown();
    }
    this.root = parsed;
    root.collectUnsupported(unsupportedParts);
  }

  /** Parses a condition; a condition Freerouting cannot read evaluates to {@link Tri#UNKNOWN}. */
  public static KiCadRuleCondition parse(String expression) {
    return new KiCadRuleCondition(expression == null ? "" : expression.trim());
  }

  /** The text of the condition. */
  public String expression() {
    return expression;
  }

  /** The parts of the condition that always evaluate to {@link Tri#UNKNOWN}, for warnings. */
  public List<String> unsupportedParts() {
    return List.copyOf(unsupportedParts);
  }

  /** Evaluates the condition for a pair of items in both orders, as KiCad does. */
  public Tri evaluate(Item a, Item b) {
    if (expression.isEmpty()) {
      return Tri.TRUE;
    }
    return truth(root.eval(a, b)).or(truth(root.eval(b, a)));
  }

  /**
   * A key such that two items with the same key give the same result in every evaluation of this
   * condition, whatever the other item is.
   */
  public String signature(Item item) {
    StringBuilder result = new StringBuilder();
    root.signature(item, result);
    return result.toString();
  }

  // ------------------------------------------------------------------------------------------
  // Values

  private sealed interface Value permits Bool, Str, Num, Prop, Undefined {}

  private record Bool(Tri value) implements Value {}

  private record Str(String value) implements Value {}

  private record Num(double value) implements Value {}

  /** A property of an item, compared with KiCad's own rules for that property. */
  private record Prop(String name, Item item) implements Value {}

  /** A value Freerouting does not know. */
  private record Undefined() implements Value {}

  private static Tri truth(Value value) {
    return switch (value) {
      case Bool b -> b.value();
      case Num n -> Tri.of(n.value() != 0);
      default -> Tri.UNKNOWN;
    };
  }

  /** The value of a property for an item; null if the item does not have the property. */
  private static String propertyText(String name, Item item) {
    boolean pad = item.type() == ItemType.PAD;
    return switch (name) {
      case "netclass" -> item.netClass();
      case "netname" -> item.netName();
      case "type" -> item.type().kicadName;
      case "pad_type" -> pad ? (item.smd() ? "SMD" : "Through-hole") : null;
      case "reference", "pad_number" ->
          pad ? ("reference".equals(name) ? item.footprint() : item.padNumber()) : null;
      // the parent of a track is the board, named by its UUID: it never equals a reference
      case "parent" -> pad ? item.footprint() : "\u0000board";
      default -> null;
    };
  }

  /** The compiled wildcard patterns, which come from rules files and are few. */
  private static final Map<String, Pattern> WILDCARDS = new ConcurrentHashMap<>();

  private static boolean matches(String pattern, String text) {
    if (pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) {
      return pattern.equalsIgnoreCase(text);
    }
    return WILDCARDS.computeIfAbsent(pattern, KiCadRuleCondition::wildcard).matcher(text).matches();
  }

  /** A KiCad wildcard ({@code *} and {@code ?}) as a case-insensitive regular expression. */
  static Pattern wildcard(String glob) {
    StringBuilder regex = new StringBuilder();
    for (char c : glob.toCharArray()) {
      switch (c) {
        case '*' -> regex.append(".*");
        case '?' -> regex.append('.');
        default -> regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  }

  private static List<String> constituents(String netClass) {
    List<String> result = new ArrayList<>();
    for (String part : netClass.split(",")) {
      result.add(part.trim());
    }
    return result;
  }

  /** {@code property == literal}, or null if the item does not have the property. */
  private static Boolean propertyEquals(Prop prop, String literal) {
    String text = propertyText(prop.name(), prop.item());
    if (text == null) {
      return null;
    }
    if ("netclass".equals(prop.name())) {
      for (String constituent : constituents(text)) {
        if (matches(literal, constituent)) {
          return true;
        }
      }
    }
    return matches(literal, text);
  }

  private static Tri compare(String op, Value left, Value right) {
    if (left instanceof Str && right instanceof Prop) {
      return compare(op, right, left);
    }
    boolean equality = "==".equals(op) || "!=".equals(op);
    if (left instanceof Prop p && right instanceof Str s && equality) {
      Boolean equal = propertyEquals(p, s.value());
      if (equal == null) {
        return Tri.FALSE; // KiCad: an undefined value is neither equal nor unequal
      }
      return Tri.of(equal == "==".equals(op));
    }
    if (left instanceof Prop p && right instanceof Prop q && equality) {
      String a = propertyText(p.name(), p.item());
      String b = propertyText(q.name(), q.item());
      if (a == null || b == null) {
        return Tri.FALSE;
      }
      return Tri.of(a.equalsIgnoreCase(b) == "==".equals(op));
    }
    if (left instanceof Str a && right instanceof Str b && equality) {
      return Tri.of(matches(b.value(), a.value()) == "==".equals(op));
    }
    if (left instanceof Num a && right instanceof Num b) {
      return Tri.of(
          switch (op) {
            case "==" -> a.value() == b.value();
            case "!=" -> a.value() != b.value();
            case "<" -> a.value() < b.value();
            case ">" -> a.value() > b.value();
            case "<=" -> a.value() <= b.value();
            default -> a.value() >= b.value();
          });
    }
    return Tri.UNKNOWN;
  }

  // ------------------------------------------------------------------------------------------
  // Syntax tree

  private sealed interface Node permits Or, And, Not, Compare, Member, Call, Literal, Unknown {
    Value eval(Item a, Item b);

    default void collectUnsupported(List<String> out) {}

    default void signature(Item item, StringBuilder out) {}
  }

  private record Or(Node left, Node right) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      return new Bool(truth(left.eval(a, b)).or(truth(right.eval(a, b))));
    }

    @Override
    public void collectUnsupported(List<String> out) {
      left.collectUnsupported(out);
      right.collectUnsupported(out);
    }

    @Override
    public void signature(Item item, StringBuilder out) {
      left.signature(item, out);
      right.signature(item, out);
    }
  }

  private record And(Node left, Node right) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      return new Bool(truth(left.eval(a, b)).and(truth(right.eval(a, b))));
    }

    @Override
    public void collectUnsupported(List<String> out) {
      left.collectUnsupported(out);
      right.collectUnsupported(out);
    }

    @Override
    public void signature(Item item, StringBuilder out) {
      left.signature(item, out);
      right.signature(item, out);
    }
  }

  private record Not(Node operand) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      return new Bool(truth(operand.eval(a, b)).not());
    }

    @Override
    public void collectUnsupported(List<String> out) {
      operand.collectUnsupported(out);
    }

    @Override
    public void signature(Item item, StringBuilder out) {
      operand.signature(item, out);
    }
  }

  private record Compare(String op, Node left, Node right, String text) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      return new Bool(compare(op, left.eval(a, b), right.eval(a, b)));
    }

    @Override
    public void collectUnsupported(List<String> out) {
      int before = out.size();
      left.collectUnsupported(out);
      right.collectUnsupported(out);
      boolean equality = "==".equals(op) || "!=".equals(op);
      boolean textual = isText(left) && isText(right);
      boolean numeric = left instanceof Literal(Num _) && right instanceof Literal(Num _);
      if (out.size() == before && !(equality && textual) && !numeric) {
        out.add("'" + text + "' (comparison not known to Freerouting)");
      }
    }

    private static boolean isText(Node node) {
      return node instanceof Member || node instanceof Literal(Str _);
    }

    @Override
    public void signature(Item item, StringBuilder out) {
      // A property compared with a literal gives one result per item; a property compared with
      // a property of the other item needs the value itself.
      if (left instanceof Member m && right instanceof Member n) {
        out.append(m.text(item)).append('\u0001').append(n.text(item)).append('\u0001');
      } else {
        out.append(truth(eval(item, item)).ordinal());
        left.signature(item, out);
        right.signature(item, out);
      }
    }
  }

  /** {@code A.Property} or {@code B.Property}. */
  private record Member(boolean sideA, String name, String text) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      if (!PROPERTIES.contains(name)) {
        return new Undefined();
      }
      return new Prop(name, sideA ? a : b);
    }

    String text(Item item) {
      String value = PROPERTIES.contains(name) ? propertyText(name, item) : null;
      return value == null ? "\u0000" : value.toLowerCase(Locale.ROOT);
    }

    @Override
    public void collectUnsupported(List<String> out) {
      if (!PROPERTIES.contains(name)) {
        out.add("'" + text + "' (property not known to Freerouting)");
      }
    }
  }

  /** {@code A.function('argument')}. */
  private record Call(boolean sideA, String name, List<String> args, String text) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      Item item = sideA ? a : b;
      boolean pad = item.type() == ItemType.PAD;
      String arg = args.isEmpty() ? "" : args.getFirst();
      return new Bool(
          switch (name) {
            case "memberoffootprint" -> {
              if (arg.startsWith("${")) {
                yield Tri.UNKNOWN; // component class selector
              }
              yield Tri.of(pad && item.footprint() != null && matches(arg, item.footprint()));
            }
            case "hasnetclass" -> {
              boolean found = item.netClass().equalsIgnoreCase(arg);
              for (String constituent : constituents(item.netClass())) {
                found |= constituent.equalsIgnoreCase(arg);
              }
              yield Tri.of(found);
            }
            case "isplated" -> Tri.of(item.type() == ItemType.VIA || (pad && !item.smd()));
            default -> Tri.UNKNOWN;
          });
    }

    @Override
    public void collectUnsupported(List<String> out) {
      if (!FUNCTIONS.contains(name)) {
        out.add("'" + text + "' (function not known to Freerouting)");
      } else if ("memberoffootprint".equals(name)
          && !args.isEmpty()
          && args.getFirst().startsWith("${")) {
        out.add("'" + text + "' (component class selector)");
      }
    }

    @Override
    public void signature(Item item, StringBuilder out) {
      out.append(truth(eval(item, item)).ordinal());
    }
  }

  private record Literal(Value value) implements Node {
    @Override
    public Value eval(Item a, Item b) {
      return value;
    }
  }

  /** A part of the expression Freerouting does not evaluate. */
  private record Unknown() implements Node {
    @Override
    public Value eval(Item a, Item b) {
      return new Undefined();
    }
  }

  // ------------------------------------------------------------------------------------------
  // Parser

  /** Parses a numeric value with a KiCad unit into micrometres; null if it is not one. */
  static Double parseLength(String text, List<String> warnings) {
    Parser parser = new Parser(text);
    try {
      Node node = parser.parseExpression();
      if (parser.isFinished() && node.eval(null, null) instanceof Num n) {
        if (!parser.sawUnit) {
          warnings.add("value '" + text + "' has no unit and is read as millimetres");
        }
        return n.value();
      }
    } catch (IllegalArgumentException | NullPointerException e) {
      // not a constant length
    }
    return null;
  }

  private static final class Parser {
    private final String text;
    private int pos;
    boolean sawUnit;

    Parser(String text) {
      this.text = text;
    }

    boolean isFinished() {
      skipSpaces();
      return pos >= text.length();
    }

    String rest() {
      return text.substring(pos);
    }

    private void skipSpaces() {
      while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
        pos++;
      }
    }

    private boolean accept(String token) {
      skipSpaces();
      if (text.startsWith(token, pos)) {
        pos += token.length();
        return true;
      }
      return false;
    }

    Node parseExpression() {
      Node left = parseAnd();
      while (accept("||")) {
        left = new Or(left, parseAnd());
      }
      return left;
    }

    private Node parseAnd() {
      Node left = parseUnary();
      while (accept("&&")) {
        left = new And(left, parseUnary());
      }
      return left;
    }

    private Node parseUnary() {
      skipSpaces();
      if (pos < text.length() && text.charAt(pos) == '!' && !text.startsWith("!=", pos)) {
        pos++;
        return new Not(parseUnary());
      }
      return parseComparison();
    }

    private Node parseComparison() {
      int start = pos;
      Node left = parseSum();
      for (String op : List.of("==", "!=", "<=", ">=", "<", ">")) {
        if (accept(op)) {
          Node right = parseSum();
          return new Compare(op, left, right, text.substring(start, pos).trim());
        }
      }
      return left;
    }

    private Node parseSum() {
      Node left = parseProduct();
      while (true) {
        skipSpaces();
        if (accept("+")) {
          left = arithmetic(left, parseProduct(), '+');
        } else if (pos < text.length() && text.charAt(pos) == '-') {
          pos++;
          left = arithmetic(left, parseProduct(), '-');
        } else {
          return left;
        }
      }
    }

    private Node parseProduct() {
      Node left = parsePrimary();
      while (true) {
        if (accept("*")) {
          left = arithmetic(left, parsePrimary(), '*');
        } else if (accept("/")) {
          left = arithmetic(left, parsePrimary(), '/');
        } else {
          return left;
        }
      }
    }

    /** Folds arithmetic on constants; anything else is unknown. */
    private static Node arithmetic(Node left, Node right, char op) {
      if (left instanceof Literal(Num a) && right instanceof Literal(Num b)) {
        double value =
            switch (op) {
              case '+' -> a.value() + b.value();
              case '-' -> a.value() - b.value();
              case '*' -> a.value() * b.value();
              default -> a.value() / b.value();
            };
        return new Literal(new Num(value));
      }
      return new Unknown();
    }

    private Node parsePrimary() {
      skipSpaces();
      if (pos >= text.length()) {
        throw new IllegalArgumentException("unexpected end");
      }
      char c = text.charAt(pos);
      if (c == '(') {
        pos++;
        Node inner = parseExpression();
        if (!accept(")")) {
          throw new IllegalArgumentException("missing ')'");
        }
        return inner;
      }
      if (c == '\'' || c == '"') {
        return new Literal(new Str(parseString()));
      }
      if (c == '-') {
        pos++;
        Node operand = parsePrimary();
        return arithmetic(new Literal(new Num(0)), operand, '-');
      }
      if (Character.isDigit(c) || c == '.') {
        return parseNumber();
      }
      if (Character.isLetter(c) || c == '_') {
        int start = pos;
        String object = parseIdentifier();
        if (!accept(".")) {
          return new Unknown(); // a bare identifier (e.g. a layer name)
        }
        String member = parseIdentifier();
        boolean sideA = "A".equals(object) || "AB".equals(object);
        boolean known = sideA || "B".equals(object);
        if (accept("(")) {
          List<String> args = new ArrayList<>();
          if (!accept(")")) {
            do {
              skipSpaces();
              if (pos < text.length() && (text.charAt(pos) == '\'' || text.charAt(pos) == '"')) {
                args.add(parseString());
              } else {
                parseSum();
                args.add("");
              }
            } while (accept(","));
            if (!accept(")")) {
              throw new IllegalArgumentException("missing ')'");
            }
          }
          String callText = text.substring(start, pos);
          String name = known ? member.toLowerCase(Locale.ROOT) : "?" + object;
          return new Call(sideA, name, args, callText);
        }
        String memberText = text.substring(start, pos);
        return new Member(
            sideA, known ? member.toLowerCase(Locale.ROOT) : "?" + object, memberText);
      }
      throw new IllegalArgumentException("unexpected '" + c + "'");
    }

    private String parseIdentifier() {
      skipSpaces();
      int start = pos;
      while (pos < text.length()
          && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
        pos++;
      }
      if (start == pos) {
        throw new IllegalArgumentException("identifier expected");
      }
      return text.substring(start, pos);
    }

    private String parseString() {
      char quote = text.charAt(pos++);
      StringBuilder result = new StringBuilder();
      while (pos < text.length() && text.charAt(pos) != quote) {
        result.append(text.charAt(pos++));
      }
      if (pos >= text.length()) {
        throw new IllegalArgumentException("unterminated string");
      }
      pos++;
      return result.toString();
    }

    /** A number with an optional KiCad length unit, in micrometres (millimetres if none). */
    private Node parseNumber() {
      int start = pos;
      while (pos < text.length()
          && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
        pos++;
      }
      double value;
      try {
        value = Double.parseDouble(text.substring(start, pos));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("bad number");
      }
      skipSpaces();
      int unitStart = pos;
      while (pos < text.length() && Character.isLetter(text.charAt(pos))) {
        pos++;
      }
      String unit = text.substring(unitStart, pos).toLowerCase(Locale.ROOT);
      double scale =
          switch (unit) {
            case "", "mm" -> 1000.0;
            case "mil", "mils", "thou" -> 25.4;
            case "in", "inch" -> 25400.0;
            case "um" -> 1.0;
            default -> Double.NaN;
          };
      if (Double.isNaN(scale)) {
        return new Unknown(); // deg, ps, fs...: not a length
      }
      sawUnit |= !unit.isEmpty();
      return new Literal(new Num(value * scale));
    }
  }

  /** The names of the supported properties and functions, for documentation and tests. */
  static Set<String> supportedNames() {
    Set<String> result = new LinkedHashSet<>(PROPERTIES);
    result.addAll(FUNCTIONS);
    return result;
  }
}
