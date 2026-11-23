package app.freerouting.io.kicad;

import java.util.ArrayList;
import java.util.List;

/**
 * A drawing without net on a copper layer of a KiCad board: a line, an arc, a circle, a rectangle,
 * a polygon or a curve of the board or of a footprint ({@code gr_*}, {@code fp_*}), or a visible
 * text. KiCad keeps every net away from it, but its Specctra export leaves it out of the design.
 *
 * <p>The drawing is reduced to a chain of corners, closed or not, stroked with a width and filled
 * or not. Arcs and circles become chains whose segments stay outside the true curve once stroked:
 * the width is increased by the sagitta of a segment. A text becomes a filled rectangle that
 * encloses its strokes, from the widths of the characters of the stroke font of KiCad; a TrueType
 * font is measured the same way.
 *
 * @param owner the reference of the footprint of the drawing, or an empty string for the board
 * @param kind {@code line}, {@code arc}, {@code circle}, {@code rect}, {@code poly}, {@code curve}
 *     or {@code text}, for messages
 * @param layers the copper layers of the drawing, as named in the layer table of the board
 * @param widthUm the width of the stroke along the chain, in micrometres; 0 for none
 * @param filled whether the inside of the closed chain is copper
 * @param closed whether the last corner joins the first one
 * @param xUm the x coordinates of the corners, absolute, in micrometres
 * @param yUm the y coordinates of the corners, absolute, in the frame of KiCad (the y axis points
 *     down)
 */
public record KiCadCopperGraphic(
    String owner,
    String kind,
    List<String> layers,
    double widthUm,
    boolean filled,
    boolean closed,
    double[] xUm,
    double[] yUm) {

  /** The width of the stroke of a drawing that sets none, as KiCad reads it: 0.1 mm. */
  static final double DEFAULT_WIDTH_UM = 100.0;

  /** The largest angle between two corners of a chain that stands for an arc. */
  private static final double MAX_ARC_STEP = Math.toRadians(10.0);

  /** The number of segments of a Bezier curve. */
  private static final int CURVE_SEGMENTS = 16;

  /** The height of a line of text, interline included, in text heights (KiCad: 1.61). */
  private static final double LINE_HEIGHT = 1.62;

  /**
   * The advance of the printable ASCII characters (32 to 126) in the stroke font of KiCad, in text
   * widths; 1.0 for the others.
   */
  private static final double[] ADVANCES = {
    0.762, 0.476, 0.762, 1.000, 0.952, 1.143, 1.238, 0.476, 0.667, 0.667, 0.762, 1.238, 0.476,
        1.238,
    0.476, 1.048, 0.952, 0.952, 0.952, 0.952, 0.952, 0.952, 0.952, 0.952, 0.952, 0.952, 0.476,
        0.476,
    1.238, 1.238, 1.238, 0.857, 1.286, 0.857, 1.000, 1.000, 1.000, 0.905, 0.857, 1.000, 1.048,
        0.476,
    0.762, 1.000, 0.810, 1.143, 1.048, 1.048, 1.000, 1.048, 1.000, 0.952, 0.762, 1.048, 0.857,
        1.143,
    0.952, 0.857, 0.952, 0.667, 0.667, 0.667, 0.571, 0.762, 0.381, 0.905, 0.905, 0.857, 0.905,
        0.857,
    0.571, 0.905, 0.905, 0.476, 0.476, 0.810, 0.524, 1.333, 0.905, 0.905, 0.905, 0.905, 0.619,
        0.810,
    0.571, 0.905, 0.762, 1.048, 0.810, 0.762, 0.810, 0.667, 0.952, 0.667, 0.714
  };

  /** The number of corners. */
  public int cornerCount() {
    return xUm.length;
  }

  /**
   * The reference of a footprint written by this item: {@code (fp_text reference "R1" ...)} up to
   * KiCad 7, {@code (property "Reference" "R1" ...)} since; null for any other item.
   */
  static String referenceOf(SexprNode item) {
    if (("fp_text".equals(item.keyword()) && "reference".equals(item.atom(0)))
        || ("property".equals(item.keyword()) && "Reference".equals(item.atom(0)))) {
      return item.atom(1);
    }
    return null;
  }

  /**
   * Reads a drawing or a text of the board or of a footprint. Returns null if it is not on a copper
   * layer, has a net, or is a hidden text.
   *
   * @param item the whole item, {@code (gr_line (start ...) (end ...) (layer F.Cu) ...)}
   * @param version the version of the file format
   * @param copperLayers the copper layers of the layer table, front to back; empty without a table
   */
  static List<Draft> drafts(SexprNode item, int version, List<String> copperLayers) {
    List<String> layers = copperLayersOf(item, copperLayers);
    if (layers.isEmpty() || hasNet(item)) {
      return List.of();
    }
    String keyword = item.keyword();
    if (keyword.endsWith("_text") || "property".equals(keyword)) {
      return textDrafts(item, layers);
    }
    double width = strokeWidth(item);
    Boolean fill = fillOf(item);
    String kind = keyword.substring(3);
    List<double[]> corners = new ArrayList<>();
    boolean closed = false;
    boolean filled = false;
    double sagitta = 0;
    switch (kind) {
      case "line" -> {
        corners.add(point(item.child("start")));
        corners.add(point(item.child("end")));
      }
      case "arc" -> sagitta = arcCorners(item, version, corners);
      case "circle" -> {
        double[] centre = point(item.child("center"));
        double[] end = point(item.child("end"));
        double radius = Math.hypot(end[0] - centre[0], end[1] - centre[1]);
        filled = fill != null ? fill : width <= 0;
        closed = true;
        sagitta = arc(centre, radius, 0, 2 * Math.PI, corners, filled);
      }
      case "rect", "text_box" -> {
        double[] start = point(item.child("start"));
        double[] end = point(item.child("end"));
        if (item.child("pts") != null) {
          readPoints(item.child("pts"), corners);
        } else {
          corners.add(start);
          corners.add(new double[] {end[0], start[1]});
          corners.add(end);
          corners.add(new double[] {start[0], end[1]});
        }
        closed = true;
        filled = "text_box".equals(kind) || (fill != null ? fill : width <= 0);
        kind = "rect";
      }
      case "poly" -> {
        readPoints(item.child("pts"), corners);
        closed = true;
        // polygons outside the board outline were always filled before the fill keyword
        filled = fill == null || fill;
      }
      case "curve" -> {
        List<double[]> controls = new ArrayList<>();
        readPoints(item.child("pts"), controls);
        if (controls.size() == 4) {
          for (int i = 0; i <= CURVE_SEGMENTS; i++) {
            corners.add(bezier(controls, (double) i / CURVE_SEGMENTS));
          }
        } else {
          corners.addAll(controls);
        }
      }
      default -> {
        return List.of();
      }
    }
    if (corners.isEmpty() || (!filled && width <= 0)) {
      width = DEFAULT_WIDTH_UM;
    }
    if (corners.isEmpty()) {
      return List.of();
    }
    double stroke = Math.max(0, width) + (width > 0 || !filled ? 2 * sagitta : 0);
    return List.of(new Draft(kind, layers, stroke, filled, closed, corners, null, 0));
  }

  /** The corners of an arc into corners; returns the sagitta of its segments. */
  private static double arcCorners(SexprNode item, int version, List<double[]> corners) {
    SexprNode mid = item.child("mid");
    if (mid == null) {
      // up to KiCad 5: (start <centre>) (end <first point>) (angle <degrees>)
      double[] centre = point(item.child("start"));
      double[] first = point(item.child("end"));
      SexprNode angle = item.child("angle");
      double sweep = Math.toRadians(angle == null ? 0 : angle.number(0, 0));
      double radius = Math.hypot(first[0] - centre[0], first[1] - centre[1]);
      double start = Math.atan2(first[1] - centre[1], first[0] - centre[0]);
      return arc(centre, radius, start, sweep, corners, false);
    }
    double[] a = point(item.child("start"));
    double[] m = point(mid);
    double[] b = point(item.child("end"));
    double[] centre = circumcentre(a, m, b);
    if (centre == null) {
      corners.add(a);
      corners.add(m);
      corners.add(b);
      return 0;
    }
    double radius = Math.hypot(a[0] - centre[0], a[1] - centre[1]);
    double start = Math.atan2(a[1] - centre[1], a[0] - centre[0]);
    double toEnd = positiveAngle(Math.atan2(b[1] - centre[1], b[0] - centre[0]) - start);
    double toMid = positiveAngle(Math.atan2(m[1] - centre[1], m[0] - centre[0]) - start);
    double sweep = toMid < toEnd ? toEnd : toEnd - 2 * Math.PI;
    return arc(centre, radius, start, sweep, corners, false);
  }

  /**
   * Adds the corners of an arc around centre from the angle start (radians, in the frame of the
   * file) over sweep. A filled shape gets corners outside the circle, so that its segments enclose
   * the arc; a stroked one gets corners on the circle and the sagitta is returned, to be added to
   * the width.
   */
  private static double arc(
      double[] centre,
      double radius,
      double start,
      double sweep,
      List<double[]> corners,
      boolean circumscribed) {
    int segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / MAX_ARC_STEP));
    double step = sweep / segments;
    double half = Math.abs(step) / 2;
    boolean full = Math.abs(Math.abs(sweep) - 2 * Math.PI) < 1e-9;
    double r = circumscribed ? radius / Math.cos(half) : radius;
    int last = full ? segments - 1 : segments;
    for (int i = 0; i <= last; i++) {
      double angle = start + i * step;
      corners.add(new double[] {centre[0] + r * Math.cos(angle), centre[1] + r * Math.sin(angle)});
    }
    return circumscribed ? 0 : radius * (1 - Math.cos(half));
  }

  private static double[] circumcentre(double[] a, double[] b, double[] c) {
    double d = 2 * (a[0] * (b[1] - c[1]) + b[0] * (c[1] - a[1]) + c[0] * (a[1] - b[1]));
    if (Math.abs(d) < 1e-9) {
      return null;
    }
    double a2 = a[0] * a[0] + a[1] * a[1];
    double b2 = b[0] * b[0] + b[1] * b[1];
    double c2 = c[0] * c[0] + c[1] * c[1];
    return new double[] {
      (a2 * (b[1] - c[1]) + b2 * (c[1] - a[1]) + c2 * (a[1] - b[1])) / d,
      (a2 * (c[0] - b[0]) + b2 * (a[0] - c[0]) + c2 * (b[0] - a[0])) / d
    };
  }

  private static double positiveAngle(double angle) {
    double result = angle % (2 * Math.PI);
    return result < 0 ? result + 2 * Math.PI : result;
  }

  private static double[] bezier(List<double[]> p, double t) {
    double u = 1 - t;
    double[] result = new double[2];
    for (int k = 0; k < 2; k++) {
      result[k] =
          u * u * u * p.get(0)[k]
              + 3 * u * u * t * p.get(1)[k]
              + 3 * u * t * t * p.get(2)[k]
              + t * t * t * p.get(3)[k];
    }
    return result;
  }

  /**
   * A text as the filled rectangle that encloses its strokes, before it is rotated: corners
   * relative to its anchor, in micrometres.
   */
  private static List<Draft> textDrafts(SexprNode item, List<String> layers) {
    if (item.hasAtom("hide") || isYes(item.child("hide"))) {
      return List.of();
    }
    SexprNode effects = item.child("effects");
    if (effects != null && (effects.hasAtom("hide") || isYes(effects.child("hide")))) {
      return List.of();
    }
    boolean property = "property".equals(item.keyword());
    String text = property ? item.atom(1) : item.atom(item.keyword().startsWith("fp") ? 1 : 0);
    if (text.isBlank()) {
      return List.of();
    }
    double height = 1000;
    double width = 1000;
    double thickness = 150;
    boolean left = false;
    boolean right = false;
    boolean top = false;
    boolean bottom = false;
    boolean mirror = false;
    if (effects != null) {
      SexprNode font = effects.child("font");
      if (font != null) {
        SexprNode size = font.child("size");
        if (size != null) {
          height = size.number(0, 1) * 1000;
          width = size.number(1, 1) * 1000;
        }
        SexprNode stroke = font.child("thickness");
        if (stroke != null) {
          thickness = stroke.number(0, 0.15) * 1000;
        }
      }
      SexprNode justify = effects.child("justify");
      if (justify != null) {
        mirror = justify.hasAtom("mirror");
        left = justify.hasAtom("left");
        right = justify.hasAtom("right");
        top = justify.hasAtom("top");
        bottom = justify.hasAtom("bottom");
      }
    }
    // one rectangle per character: its advance in the font of KiCad, plus half a stroke and a
    // tenth of the size all around. It encloses the strokes of every copper text of the 56
    // PCBench boards that have some. KiCad justifies each line on its own.
    double margin = thickness / 2 + 0.1 * Math.max(height, width);
    // KiCad ignores the line breaks at the end of a text
    String[] lines = text.stripTrailing().split("\n");
    double lineHeight = LINE_HEIGHT * height;
    double textHeight = lineHeight * lines.length;
    double blockTop = top ? 0 : bottom ? -textHeight : -textHeight / 2;
    // a text justified at the top or at the bottom reaches beyond its anchor by a descender
    double beyond = top || bottom ? 0.3 * height : 0;
    SexprNode at = item.child("at");
    double[] anchor = point(at);
    double angle = at == null ? 0 : at.number(2, 0);
    List<Draft> result = new ArrayList<>();
    for (int lineNo = 0; lineNo < lines.length; lineNo++) {
      String line = lines[lineNo];
      double lineAdvance = 0;
      for (int i = 0; i < line.length(); i++) {
        lineAdvance += advance(line.charAt(i));
      }
      double x = left ? 0 : right ? -lineAdvance * width : -lineAdvance * width / 2;
      double y0 = blockTop + lineNo * lineHeight - margin - (lineNo == 0 && top ? beyond : 0);
      double y1 =
          blockTop
              + (lineNo + 1) * lineHeight
              + margin
              + (lineNo == lines.length - 1 && bottom ? beyond : 0);
      for (int i = 0; i < line.length(); i++) {
        double cell = advance(line.charAt(i)) * width;
        if (!Character.isWhitespace(line.charAt(i))) {
          // a mirrored text (on the back) is laid out, then mirrored around its anchor
          double sign = mirror ? -1 : 1;
          List<double[]> corners = new ArrayList<>();
          corners.add(new double[] {sign * (x - margin), y0});
          corners.add(new double[] {sign * (x + cell + margin), y0});
          corners.add(new double[] {sign * (x + cell + margin), y1});
          corners.add(new double[] {sign * (x - margin), y1});
          result.add(new Draft("text", layers, 0, true, true, corners, anchor, angle));
        }
        x += cell;
      }
    }
    return result;
  }

  /** The advance of a character in the stroke font of KiCad, in text widths. */
  private static double advance(char c) {
    return c >= 32 && c <= 126 ? ADVANCES[c - 32] : 1.0;
  }

  private static boolean isYes(SexprNode node) {
    return node != null && !"no".equals(node.atom(0));
  }

  /** The copper layers of an item, wildcards resolved; empty if it has none. */
  private static List<String> copperLayersOf(SexprNode item, List<String> copperLayers) {
    List<String> written = new ArrayList<>();
    for (String key : new String[] {"layer", "layers"}) {
      SexprNode node = item.child(key);
      if (node != null) {
        // (layer "F.Cu" knockout): the keyword is not a layer
        for (String atom : node.atoms()) {
          if (!"knockout".equals(atom)) {
            written.add(atom);
          }
        }
      }
    }
    List<String> result = new ArrayList<>();
    for (String layer : KiCadBoardZones.resolveLayers(written, copperLayers)) {
      boolean copper =
          copperLayers.isEmpty() ? layer.endsWith(".Cu") : copperLayers.contains(layer);
      if (copper && !result.contains(layer)) {
        result.add(layer);
      }
    }
    return result;
  }

  /**
   * Whether the item is on a net: {@code (net 3)}, {@code (net 3 "GND")} or {@code (net "GND")}.
   */
  private static boolean hasNet(SexprNode item) {
    SexprNode net = item.child("net");
    if (net == null) {
      return false;
    }
    String value = net.atom(0);
    return !(value.isEmpty() || "0".equals(value));
  }

  private static double strokeWidth(SexprNode item) {
    SexprNode width = item.child("width");
    SexprNode stroke = item.child("stroke");
    if (width == null && stroke != null) {
      width = stroke.child("width");
    }
    return width == null ? 0 : width.number(0, 0) * 1000;
  }

  /** The fill of a drawing: true, false, or null if it writes none. */
  private static Boolean fillOf(SexprNode item) {
    SexprNode fill = item.child("fill");
    if (fill == null) {
      return null;
    }
    List<String> atoms = fill.atoms();
    SexprNode type = fill.child("type");
    String value = !atoms.isEmpty() ? atoms.get(0) : type != null ? type.atom(0) : "";
    return !("none".equals(value) || "no".equals(value));
  }

  /** Reads {@code (pts (xy 1 2) (arc (start ...) (mid ...) (end ...)) ...)}. */
  private static void readPoints(SexprNode pts, List<double[]> corners) {
    if (pts == null) {
      return;
    }
    for (Object child : pts.children()) {
      if (child instanceof SexprNode node) {
        if ("xy".equals(node.keyword())) {
          corners.add(point(node));
        } else if ("arc".equals(node.keyword())) {
          List<double[]> arc = new ArrayList<>();
          arcCorners(node, Integer.MAX_VALUE, arc);
          corners.addAll(arc);
        }
      }
    }
  }

  /** The first two numbers of a node, in micrometres; the origin if there is none. */
  private static double[] point(SexprNode node) {
    if (node == null) {
      return new double[2];
    }
    return new double[] {node.number(0, 0) * 1000, node.number(1, 0) * 1000};
  }

  /**
   * A drawing as written in the file, in the frame of its footprint if it has one.
   *
   * @param anchor the position of a text, its corners being relative to it and not yet turned; null
   *     for a drawing
   * @param angleDeg the absolute orientation of a text
   */
  record Draft(
      String kind,
      List<String> layers,
      double widthUm,
      boolean filled,
      boolean closed,
      List<double[]> corners,
      double[] anchor,
      double angleDeg) {

    /**
     * The drawing on the board, for a footprint at x, y turned by angle (degrees, KiCad: positive
     * counter-clockwise on screen); 0, 0, 0 for a drawing of the board.
     */
    KiCadCopperGraphic place(double x, double y, double angle, String owner) {
      int n = corners.size();
      double[] xs = new double[n];
      double[] ys = new double[n];
      double cos = Math.cos(Math.toRadians(angle));
      double sin = Math.sin(Math.toRadians(angle));
      if (anchor == null) {
        for (int i = 0; i < n; i++) {
          double[] p = corners.get(i);
          // RotatePoint of KiCad: the y axis points down
          xs[i] = x + p[0] * cos + p[1] * sin;
          ys[i] = y - p[0] * sin + p[1] * cos;
        }
      } else {
        // the position of a text is in the frame of its footprint, its angle is absolute
        double ax = x + anchor[0] * cos + anchor[1] * sin;
        double ay = y - anchor[0] * sin + anchor[1] * cos;
        double textCos = Math.cos(Math.toRadians(angleDeg));
        double textSin = Math.sin(Math.toRadians(angleDeg));
        for (int i = 0; i < n; i++) {
          double[] p = corners.get(i);
          xs[i] = ax + p[0] * textCos + p[1] * textSin;
          ys[i] = ay - p[0] * textSin + p[1] * textCos;
        }
      }
      return new KiCadCopperGraphic(owner, kind, layers, widthUm, filled, closed, xs, ys);
    }
  }
}
