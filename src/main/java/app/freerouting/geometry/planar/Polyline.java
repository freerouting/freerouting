package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Represents a sequence of connected line segments forming a path on a PCB.
 *
 * <p>A Polyline is a fundamental geometric primitive used to represent traces, board outlines,
 * and other linear features in PCB routing. It consists of a sequence of lines where no two
 * consecutive lines may be parallel.
 *
 * <p><strong>Mathematical Model:</strong>
 * <ul>
 *   <li>A polyline of <em>n</em> lines defines <em>n-1</em> corner points</li>
 *   <li>Each corner is the intersection of two consecutive lines</li>
 *   <li>Lines are defined by points with integer coordinates</li>
 *   <li>Intersections may require infinite precision rational coordinates</li>
 * </ul>
 *
 * <p><strong>Design Rationale:</strong>
 * We use polylines with integer-coordinate lines instead of polygons with rational-coordinate
 * corners because:
 * <ul>
 *   <li><strong>Performance:</strong> Integer arithmetic is faster than rational arithmetic</li>
 *   <li><strong>Robustness:</strong> Avoids floating-point precision errors</li>
 *   <li><strong>Simplicity:</strong> Line-based representation is easier to manipulate</li>
 *   <li><strong>Efficiency:</strong> Better performance in geometric calculations</li>
 * </ul>
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>No Parallel Lines:</strong> Consecutive lines must not be parallel</li>
 *   <li><strong>Corner Caching:</strong> Intersection points are lazily calculated and cached</li>
 *   <li><strong>Transformations:</strong> Support for translation, rotation, mirroring</li>
 *   <li><strong>Offset Shapes:</strong> Generate parallel offset curves for trace width</li>
 *   <li><strong>Bounding Shapes:</strong> Compute bounding boxes and octagons</li>
 * </ul>
 *
 * <p><strong>Common Use Cases:</strong>
 * <ul>
 *   <li>Representing PCB trace centerlines</li>
 *   <li>Defining board outlines and keepout areas</li>
 *   <li>Calculating trace shapes with width (offset shapes)</li>
 *   <li>Route optimization and smoothing</li>
 *   <li>Collision detection with other board features</li>
 * </ul>
 *
 * <p><strong>Constraints:</strong>
 * <ul>
 *   <li>Minimum 3 lines required for a valid polyline</li>
 *   <li>First and last lines are perpendicular end caps</li>
 *   <li>No consecutive parallel lines allowed</li>
 *   <li>Overlapping segments are automatically removed</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong>
 * <ul>
 *   <li>Corner points are lazily computed and cached</li>
 *   <li>Bounding boxes are cached for repeated queries</li>
 *   <li>Approximations using FloatPoint for performance-critical operations</li>
 * </ul>
 *
 * <p><strong>Typical Usage:</strong>
 * <pre>{@code
 * // Create from polygon
 * Polygon polygon = new Polygon(cornerPoints);
 * Polyline polyline = new Polyline(polygon);
 *
 * // Access corners
 * Point firstCorner = polyline.first_corner();
 * Point lastCorner = polyline.last_corner();
 *
 * // Generate offset shape for trace width
 * TileShape[] offsetShapes = polyline.offset_shapes(traceHalfWidth);
 * }</pre>
 *
 * @see Line
 * @see Polygon
 * @see Point
 * @see TileShape
 */
public class Polyline implements Serializable {

  /**
   * Flag controlling whether to use bounding octagons or boxes for offset shape optimization.
   *
   * <p>Bounding octagons provide tighter bounds than boxes for diagonal traces,
   * improving performance in offset shape calculations.
   */
  private static final boolean USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES = true;

  /**
   * The array of lines forming this polyline.
   *
   * <p><strong>Structure:</strong>
   * <ul>
   *   <li><strong>Index 0:</strong> Perpendicular end cap at start</li>
   *   <li><strong>Indices 1 to n-2:</strong> Actual line segments</li>
   *   <li><strong>Index n-1:</strong> Perpendicular end cap at end</li>
   * </ul>
   *
   * <p>The intersection of line[i] and line[i+1] defines corner point i.
   *
   * <p><strong>Invariant:</strong> No two consecutive lines may be parallel.
   */
  public final Line[] arr;

  /**
   * Cached floating-point approximations of corner points.
   *
   * <p>Lazily computed and cached for performance. Used when exact
   * rational coordinates are not required.
   */
  private transient FloatPoint[] precalculated_float_corners;

  /**
   * Cached exact corner points.
   *
   * <p>Lazily computed and cached. Computed using exact rational arithmetic
   * to avoid precision errors.
   */
  private transient Point[] precalculated_corners;

  /**
   * Cached bounding box containing all corners of this polyline.
   *
   * <p>Lazily computed on first access via {@link #bounding_box()}.
   */
  private transient IntBox precalculated_bounding_box;

  /**
   * Creates a polyline from a polygon by converting corners to line intersections.
   *
   * <p>Constructs a polyline with n+1 lines from a polygon with n corners, where each
   * corner of the polygon becomes the intersection point of two consecutive lines.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Create perpendicular end cap at start (line 0)</li>
   *   <li>Create lines connecting consecutive corners (lines 1 to n-1)</li>
   *   <li>Create perpendicular end cap at end (line n)</li>
   * </ol>
   *
   * <p><strong>End Caps:</strong>
   * The first and last lines are perpendicular to the polyline direction at the
   * endpoints, allowing the first and last polygon corners to be represented as
   * line intersections.
   *
   * <p><strong>Requirements:</strong>
   * The polygon must have at least 2 corners. If fewer corners are provided,
   * an empty polyline (zero lines) is created and a warning is logged.
   *
   * @param p_polygon the polygon to convert (must have at least 2 corners)
   *
   * @see Polygon#corner_array()
   * @see Direction#turn_45_degree(int)
   */
  public Polyline(Polygon p_polygon) {
    Point[] point_arr = p_polygon.corner_array();
    if (point_arr.length < 2) {
      FRLogger.warn("Polyline: must contain at least 2 different points");
      arr = new Line[0];
      return;
    }
    arr = new Line[point_arr.length + 1];
    for (int i = 1; i < point_arr.length; i++) {
      arr[i] = new Line(point_arr[i - 1], point_arr[i]);
    }
    // construct perpendicular lines at the start and at the end to represent
    // the first and the last point of point_arr as intersection of lines.

    Direction dir = Direction.get_instance(point_arr[0], point_arr[1]);
    arr[0] = Line.get_instance(point_arr[0], dir.turn_45_degree(2));

    dir = Direction.get_instance(point_arr[point_arr.length - 1], point_arr[point_arr.length - 2]);
    arr[point_arr.length] = Line.get_instance(point_arr[point_arr.length - 1], dir.turn_45_degree(2));
  }

  /**
   * Creates a polyline from an array of corner points.
   *
   * <p>Convenience constructor that first creates a polygon from the points,
   * then converts it to a polyline.
   *
   * @param p_points array of corner points (at least 2 required)
   *
   * @see #Polyline(Polygon)
   */
  public Polyline(Point[] p_points) {
    this(new Polygon(p_points));
  }

  /**
   * Creates a simple polyline connecting two corner points.
   *
   * <p>Constructs a minimal polyline consisting of exactly 3 lines:
   * <ol>
   *   <li>Perpendicular end cap at start corner</li>
   *   <li>Line connecting the two corners</li>
   *   <li>Perpendicular end cap at end corner</li>
   * </ol>
   *
   * <p><strong>Degenerate Case:</strong>
   * If the two corners are equal (same point), creates an empty polyline
   * with zero lines.
   *
   * @param p_from_corner the starting corner point
   * @param p_to_corner the ending corner point
   */
  public Polyline(Point p_from_corner, Point p_to_corner) {
    if (p_from_corner.equals(p_to_corner)) {
      arr = new Line[0];
      return;
    }
    arr = new Line[3];
    Direction dir = Direction.get_instance(p_from_corner, p_to_corner);
    arr[0] = Line.get_instance(p_from_corner, dir.turn_45_degree(2));
    arr[1] = new Line(p_from_corner, p_to_corner);
    dir = Direction.get_instance(p_from_corner, p_to_corner);
    arr[2] = Line.get_instance(p_to_corner, dir.turn_45_degree(2));
  }

  /**
   * Creates a polyline from an array of lines with automatic cleanup and normalization.
   *
   * <p>This constructor processes the input lines to ensure a valid polyline:
   * <ol>
   *   <li><strong>Remove Parallel Lines:</strong> Consecutive parallel lines are merged</li>
   *   <li><strong>Remove Overlaps:</strong> Overlapping line segments are eliminated</li>
   *   <li><strong>Remove Artifacts:</strong> Very short segments are removed</li>
   *   <li><strong>Normalize Directions:</strong> Lines are oriented to intersect correctly</li>
   * </ol>
   *
   * <p><strong>Line Orientation:</strong>
   * Line directions are adjusted so that line[i] points from corner[i-1] to corner[i].
   * This ensures proper intersection calculation and corner ordering.
   *
   * <p><strong>Minimum Length:</strong>
   * If after cleanup fewer than 2 lines remain, creates an empty polyline (zero lines).
   *
   * <p><strong>Corner Caching:</strong>
   * Float corner approximations are pre-calculated during construction for performance.
   *
   * @param p_line_arr array of lines to form the polyline (may contain parallel or overlapping lines)
   *
   * @see #remove_consecutive_parallel_lines
   * @see #remove_overlaps
   * @see #remove_artifacts
   */
  public Polyline(Line[] p_line_arr) {
    Line[] lines = remove_consecutive_parallel_lines(p_line_arr);
    lines = remove_overlaps(lines);
    lines = remove_artifacts(lines);
    if (lines.length < 2) {
      arr = new Line[0];
      return;
    }
    precalculated_float_corners = new FloatPoint[lines.length - 1];

    // turn evtl the direction of the lines that they point always
    // from the previous corner to the next corner
    for (int i = 1; i < lines.length - 1; i++) {
      precalculated_float_corners[i] = lines[i].intersection_approx(lines[i + 1]);
      Side side_of_line = lines[i - 1].side_of(precalculated_float_corners[i]);
      if (side_of_line != Side.COLLINEAR) {
        Direction d0 = lines[i - 1].direction();
        Direction d1 = lines[i].direction();
        Side side1 = d0.side_of(d1);
        if (side1 != side_of_line) {
          lines[i] = lines[i].opposite();
        }
      }
    }
    arr = lines;
  }

  private static Line[] remove_consecutive_parallel_lines(Line[] p_line_arr) {
    if (p_line_arr.length < 3) {
      // polyline must have at least 3 lines
      return p_line_arr;
    }
    Line[] tmp_arr = new Line[p_line_arr.length];
    int new_length = 0;
    tmp_arr[0] = p_line_arr[0];
    for (int i = 1; i < p_line_arr.length; i++) {
      // skip multiple lines
      if (!tmp_arr[new_length].is_parallel(p_line_arr[i])) {
        ++new_length;
        tmp_arr[new_length] = p_line_arr[i];
      }
    }
    ++new_length;
    if (new_length == p_line_arr.length) {
      // nothing skipped
      return p_line_arr;
    }
    // at least 1 line is skipped, adjust the array
    if (new_length < 3) {
      return new Line[0];
    }
    Line[] result = new Line[new_length];
    System.arraycopy(tmp_arr, 0, result, 0, new_length);
    return result;
  }

  /**
   * Removes overlapping line segments from a polyline.
   *
   * <p>Detects and removes situations where a line backtracks over a previous line,
   * creating an overlap. For example, if line[i] and line[i+2] are equal or opposite,
   * the intermediate segment creates an overlap that should be removed.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Compare each line with the line two positions ahead</li>
   *   <li>If they are equal or opposite, skip both intermediate lines</li>
   *   <li>Special handling for first and last lines</li>
   * </ol>
   *
   * <p><strong>Example:</strong>
   * <pre>
   * Before: --→ ↓ ←-- (backtrack)
   * After:  --→ (overlap removed)
   * </pre>
   *
   * <p><strong>Minimum Length:</strong>
   * Returns empty array if fewer than 3 lines remain after cleanup.
   *
   * @param p_line_arr input line array (must have at least 4 lines to detect overlaps)
   * @return cleaned line array with overlaps removed
   */
  private static Line[] remove_overlaps(Line[] p_line_arr) {
    if (p_line_arr.length < 4) {
      return p_line_arr;
    }
    int new_length = 0;
    Line[] tmp_arr = new Line[p_line_arr.length];
    tmp_arr[0] = p_line_arr[0];
    if (!p_line_arr[0].is_equal_or_opposite(p_line_arr[2])) {
      ++new_length;
    }
    // else  skip the first line
    tmp_arr[new_length] = p_line_arr[1];
    ++new_length;
    for (int i = 2; i < p_line_arr.length - 2; i++) {
      if (tmp_arr[new_length - 1].is_equal_or_opposite(p_line_arr[i + 1])) {
        // skip 2 lines
        --new_length;
      } else {
        tmp_arr[new_length] = p_line_arr[i];
        ++new_length;
      }
    }
    tmp_arr[new_length] = p_line_arr[p_line_arr.length - 2];
    ++new_length;
    if (!p_line_arr[p_line_arr.length - 1].is_equal_or_opposite(tmp_arr[new_length - 2])) {
      tmp_arr[new_length] = p_line_arr[p_line_arr.length - 1];
      ++new_length;
    }
    // else skip the last line
    if (new_length == p_line_arr.length) {
      // nothing skipped
      return p_line_arr;
    }
    // at least 1 line is skipped, adjust the array
    if (new_length < 3) {
      return new Line[0];
    }
    Line[] result = new Line[new_length];
    System.arraycopy(tmp_arr, 0, result, 0, new_length);
    return result;
  }

  /**
   * Removes small artifacts like spikes and very short segments from the polyline.
   *
   * <p>Filters out line segments shorter than a threshold (1900 units), which typically
   * represent rounding errors, spikes, or other geometric artifacts that can cause
   * problems in routing algorithms.
   *
   * <p><strong>Threshold:</strong>
   * Lines with length ≤ 1900 units are considered artifacts and removed.
   *
   * <p><strong>Logging:</strong>
   * Each removed artifact is logged at TRACE level for debugging purposes,
   * including the polyline endpoints and the artifact length.
   *
   * @param lines input line array
   * @return cleaned line array with short segments removed
   */
  private Line[] remove_artifacts(Line[] lines) {
    Line[] tmp_arr = new Line[lines.length];
    int new_length = 0;
    for (int i = 0; i < lines.length; i++) {
      // skip small lines
      if (lines[i].length() > 1900) {
        tmp_arr[new_length] = lines[i];
        new_length++;
      } else
      {
        FRLogger.trace("Polyline.remove_artifacts", "remove_artifact",
            "A line with length of " + lines[i].length() + " was skipped in a polyline",
            this.toString(),
            new Point[] { this.first_corner(), this.last_corner() });
      }
    }

    Line[] result = new Line[new_length];
    System.arraycopy(tmp_arr, 0, result, 0, new_length);
    return result;
  }

  /**
   * Returns the number of corner points in this polyline.
   *
   * <p>A polyline with n lines has n-1 corner points (intersections of consecutive lines).
   *
   * @return the number of corners (line count minus 1)
   */
  public int corner_count() {
    return arr.length - 1;
  }

  /**
   * Checks if this polyline is empty (has insufficient lines to form corners).
   *
   * <p>A valid polyline requires at least 3 lines (forming 2 corners plus end caps).
   *
   * @return true if the polyline has fewer than 3 lines
   */
  public boolean is_empty() {
    return arr.length < 3;
  }

  /**
   * Checks if this polyline degenerates to a single point.
   *
   * <p>A polyline is considered a point if:
   * <ul>
   *   <li>It's empty (fewer than 3 lines), OR</li>
   *   <li>All corner points are equal (same position)</li>
   * </ul>
   *
   * <p><strong>Use Case:</strong>
   * Useful for detecting degenerate traces or validating geometric operations.
   *
   * @return true if all corners are at the same point or polyline is empty
   */
  public boolean is_point() {
    if (arr.length < 3) {
      return true;
    }
    Point first_corner = this.corner(0);
    for (int i = 1; i < arr.length - 1; i++) {
      if (!this.corner(i).equals(first_corner)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if all lines in this polyline are orthogonal (horizontal or vertical).
   *
   * <p>Returns true only if every line is aligned with the X or Y axis.
   *
   * <p><strong>Use Case:</strong>
   * Important for Manhattan routing where only orthogonal traces are allowed.
   *
   * @return true if all lines are orthogonal
   *
   * @see Line#is_orthogonal()
   */
  public boolean is_orthogonal() {
    for (int i = 0; i < arr.length; i++) {
      if (!arr[i].is_orthogonal()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if all lines in this polyline are multiples of 45 degrees.
   *
   * <p>Returns true if every line is aligned at 0°, 45°, 90°, 135°, etc.
   *
   * <p><strong>Use Case:</strong>
   * Important for octilinear routing where only 45-degree angles are allowed.
   *
   * @return true if all lines are at 45-degree intervals
   *
   * @see Line#is_multiple_of_45_degree()
   */
  public boolean is_multiple_of_45_degree() {
    for (int i = 0; i < arr.length; i++) {
      if (!arr[i].is_multiple_of_45_degree()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the first corner point of this polyline.
   *
   * <p>This is the intersection of the first and second lines (line[0] and line[1]).
   * Equivalent to {@code corner(0)}.
   *
   * @return the first corner point with exact coordinates
   *
   * @see #corner(int)
   * @see #last_corner()
   */
  public Point first_corner() {
    return corner(0);
  }

  /**
   * Returns the last corner point of this polyline.
   *
   * <p>This is the intersection of the last two lines (line[n-2] and line[n-1]).
   * Equivalent to {@code corner(arr.length - 2)}.
   *
   * @return the last corner point with exact coordinates
   *
   * @see #corner(int)
   * @see #first_corner()
   */
  public Point last_corner() {
    return corner(arr.length - 2);
  }

  /**
   * Returns all corner points of this polyline with exact rational coordinates.
   *
   * <p>Corners are lazily computed and cached. Each corner is the exact intersection
   * of two consecutive lines, calculated using rational arithmetic to avoid
   * floating-point precision errors.
   *
   * <p><strong>Caching:</strong>
   * Corner points are computed on first access and stored in {@link #precalculated_corners}.
   * Subsequent calls return the cached values.
   *
   * <p><strong>Array Size:</strong>
   * For a polyline with n lines, returns an array of n-1 corner points.
   *
   * @return array of exact corner points (empty array if polyline has fewer than 2 lines)
   *
   * @see #corner_approx_arr()
   * @see Line#intersection(Line)
   */
  public Point[] corner_arr() {
    if (arr.length < 2) {
      return new Point[0];
    }
    if (precalculated_corners == null)
    // corner array is not yet allocated
    {
      precalculated_corners = new Point[arr.length - 1];
    }
    for (int i = 0; i < precalculated_corners.length; i++) {
      if (precalculated_corners[i] == null) {
        precalculated_corners[i] = arr[i].intersection(arr[i + 1]);
      }
    }
    return precalculated_corners;
  }

  /**
   * Returns all corner points approximated as floating-point coordinates.
   *
   * <p>Provides faster access than {@link #corner_arr()} by using floating-point
   * approximations instead of exact rational arithmetic. Suitable for visualization
   * and performance-critical operations where exact precision is not required.
   *
   * <p><strong>Precision Trade-off:</strong>
   * Float approximations may introduce small rounding errors but are significantly
   * faster to compute and use less memory than exact rational coordinates.
   *
   * <p><strong>Caching:</strong>
   * Approximations are computed on first access and stored in
   * {@link #precalculated_float_corners}. Subsequent calls return cached values.
   *
   * <p><strong>Array Size:</strong>
   * For a polyline with n lines, returns an array of n-1 corner points.
   *
   * @return array of approximate corner points (empty array if polyline has fewer than 2 lines)
   *
   * @see #corner_arr()
   * @see #corner_approx(int)
   * @see Line#intersection_approx(Line)
   */
  public FloatPoint[] corner_approx_arr() {
    if (arr.length < 2) {
      return new FloatPoint[0];
    }
    if (precalculated_float_corners == null)
    // corner array is not yet allocated
    {
      precalculated_float_corners = new FloatPoint[arr.length - 1];
    }
    for (int i = 0; i < precalculated_float_corners.length; i++) {
      if (precalculated_float_corners[i] == null) {
        precalculated_float_corners[i] = arr[i].intersection_approx(arr[i + 1]);
      }
    }
    return precalculated_float_corners;
  }

  /**
   * Returns an approximation of the intersection of the p_no-th with the (p_no - 1)-th line by a FloatPoint.
   */
  public FloatPoint corner_approx(int p_no) {
    int no;
    if (p_no < 0) {
      FRLogger.warn("Polyline.corner_approx: p_no is < 0");
      no = 0;
    } else if (p_no >= arr.length - 1) {
      FRLogger.warn("Polyline.corner_approx: p_no must be less than arr.length - 1");
      no = arr.length - 2;
    } else {
      no = p_no;
    }
    if (precalculated_float_corners == null)
    // corner array is not yet allocated
    {
      precalculated_float_corners = new FloatPoint[arr.length - 1];
    }
    if (precalculated_float_corners[no] == null)
    // corner is not yet calculated
    {
      precalculated_float_corners[no] = arr[no].intersection_approx(arr[no + 1]);
    }
    return precalculated_float_corners[no];
  }

  /**
   * Returns the intersection of the p_no-th with the (p_no - 1)-th edge line.
   */
  public Point corner(int p_no) {
    if (arr.length < 2) {
      FRLogger.warn("Polyline.corner: arr.length is < 2");
      return null;
    }
    int no;
    if (p_no < 0) {
      FRLogger.warn("Polyline.corner: p_no is < 0");
      no = 0;
    } else if (p_no >= arr.length - 1) {
      FRLogger.warn("Polyline.corner: p_no must be less than arr.length - 1");
      no = arr.length - 2;
    } else {
      no = p_no;
    }
    if (precalculated_corners == null)
    // corner array is not yet allocated
    {
      precalculated_corners = new Point[arr.length - 1];
    }
    if (precalculated_corners[no] == null)
    // corner is not yet calculated
    {
      precalculated_corners[no] = arr[no].intersection(arr[no + 1]);
    }
    return precalculated_corners[no];
  }

  /**
   * Returns a new polyline with lines in reversed order.
   *
   * <p>Creates a polyline that represents the same path but traversed in the
   * opposite direction. Each line is replaced with its opposite (same position
   * but opposite direction).
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Reversing trace direction for routing algorithms</li>
   *   <li>Creating return paths</li>
   *   <li>Path optimization that requires bidirectional analysis</li>
   * </ul>
   *
   * @return a new polyline with reversed line order
   *
   * @see Line#opposite()
   */
  public Polyline reverse() {
    Line[] reversed_lines = new Line[arr.length];
    for (int i = 0; i < arr.length; i++) {
      reversed_lines[i] = arr[arr.length - i - 1].opposite();
    }
    return new Polyline(reversed_lines);
  }

  /**
   * Calculates the approximate length of a polyline segment between two corners.
   *
   * <p>Computes the cumulative Euclidean distance between consecutive corners
   * from the starting corner to the ending corner using floating-point approximations.
   *
   * <p><strong>Corner Bounds:</strong>
   * Corner indices are automatically clamped to valid range [0, corner_count-1].
   *
   * <p><strong>Precision:</strong>
   * Uses {@link #corner_approx(int)} for fast floating-point calculations.
   * For exact measurements, consider using exact corner coordinates, though
   * this will be significantly slower.
   *
   * @param p_from_corner starting corner index (inclusive)
   * @param p_to_corner ending corner index (exclusive)
   * @return approximate length in board units
   *
   * @see #length_approx()
   * @see #corner_approx(int)
   */
  public double length_approx(int p_from_corner, int p_to_corner) {
    int from_corner = Math.max(p_from_corner, 0);
    int to_corner = Math.min(p_to_corner, arr.length - 2);
    double result = 0;
    for (int i = from_corner; i < to_corner; i++) {
      result += this.corner_approx(i + 1).distance(this.corner_approx(i));
    }
    return result;
  }

  /**
   * Calculates the total approximate length of this entire polyline.
   *
   * <p>Convenience method that computes the cumulative distance between all
   * consecutive corners from start to end.
   *
   * @return total approximate length in board units
   *
   * @see #length_approx(int, int)
   */
  public double length_approx() {
    return length_approx(0, arr.length - 2);
  }

  /**
   * Generates offset shapes representing this polyline with a specified width.
   *
   * <p>Creates an array of tile shapes that represent the area covered by this
   * polyline when given a half-width (trace width divided by 2). This is the
   * fundamental operation for converting centerline polylines into actual trace
   * shapes on the PCB.
   *
   * <p><strong>Algorithm:</strong>
   * For each line segment in the polyline:
   * <ol>
   *   <li>Create parallel offset lines at distance p_half_width on each side</li>
   *   <li>Handle corners by computing proper intersections or miters</li>
   *   <li>Cut "dog ears" (sharp protrusions) at acute corners</li>
   *   <li>Generate bounding shapes and intersect with offset region</li>
   * </ol>
   *
   * <p><strong>Corner Handling:</strong>
   * <ul>
   *   <li><strong>Convex corners:</strong> Simple miter join</li>
   *   <li><strong>Concave corners:</strong> May create dog ears that need cutting</li>
   *   <li><strong>Acute angles:</strong> Limited miter length to avoid excessive protrusions</li>
   * </ul>
   *
   * <p><strong>Return Value:</strong>
   * Array of {@link TileShape} objects, one for each internal line segment
   * (excluding end caps). For n lines, returns n-2 shapes.
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Converting trace centerlines to physical copper shapes</li>
   *   <li>Calculating actual board area used by traces</li>
   *   <li>Checking clearances and overlaps</li>
   *   <li>Generating manufacturing output</li>
   * </ul>
   *
   * @param p_half_width half the desired trace width (radius from centerline)
   * @return array of tile shapes representing the offset polyline
   *
   * @see #offset_shapes(int, int, int)
   * @see TileShape
   */
  public TileShape[] offset_shapes(int p_half_width) {
    return offset_shapes(p_half_width, 0, arr.length - 1);
  }

  /**
   * Generates offset shapes for a specified range of line segments.
   *
   * <p>Creates tile shapes representing the area covered by a portion of this polyline
   * with the specified half-width. This is the workhorse method called by
   * {@link #offset_shapes(int)}.
   *
   * <p><strong>Algorithm Overview:</strong>
   * For each line segment in the range [from_no+1, to_no-1]:
   * <ol>
   *   <li><strong>Create offset lines:</strong>
   *     <ul>
   *       <li>Translate current line right and left by half_width</li>
   *       <li>Handle corner transitions based on turn direction</li>
   *       <li>Use opposite lines for concave corners</li>
   *     </ul>
   *   </li>
   *   <li><strong>Handle corners:</strong>
   *     <ul>
   *       <li>Left turns: Use next right line</li>
   *       <li>Right turns: Use next left line in opposite direction</li>
   *       <li>Miters formed by line intersections</li>
   *     </ul>
   *   </li>
   *   <li><strong>Cut dog ears:</strong>
   *     <ul>
   *       <li>Detect sharp corners that create protrusions</li>
   *       <li>Add cutting lines to trim excessive miters</li>
   *       <li>Check distance threshold (2 × half_width²)</li>
   *     </ul>
   *   </li>
   *   <li><strong>Generate final shape:</strong>
   *     <ul>
   *       <li>Create bounding octagon or box</li>
   *       <li>Offset by half_width</li>
   *       <li>Intersect with offset shape and cutting lines</li>
   *     </ul>
   *   </li>
   * </ol>
   *
   * <p><strong>Dog Ear Cutting:</strong>
   * When consecutive turns create acute angles, the miter can extend far beyond
   * the trace width. The algorithm detects these "dog ears" and adds cutting lines
   * to limit the protrusion to a reasonable distance.
   *
   * <p><strong>Bounding Shape Selection:</strong>
   * Uses bounding octagons if {@link #USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES} is true,
   * otherwise uses bounding boxes. Octagons provide tighter bounds for diagonal traces.
   *
   * <p><strong>Range Parameters:</strong>
   * The from_no and to_no parameters are automatically clamped to valid ranges.
   * The method returns one shape for each interior line segment.
   *
   * @param p_half_width half the desired trace width (radius from centerline)
   * @param p_from_no starting line index (clamped to [0, arr.length-1])
   * @param p_to_no ending line index (clamped to [0, arr.length-1])
   * @return array of tile shapes, one per interior line segment
   *
   * @see #offset_shapes(int)
   * @see TileShape#intersection_with_simplify
   * @see IntOctagon
   * @see IntBox
   */
  public TileShape[] offset_shapes(int p_half_width, int p_from_no, int p_to_no) {
    int from_no = Math.max(p_from_no, 0);
    int to_no = Math.min(p_to_no, arr.length - 1);
    int shape_count = Math.max(to_no - from_no - 1, 0);
    TileShape[] shape_arr = new TileShape[shape_count];
    if (shape_count == 0) {
      return shape_arr;
    }
    Vector prev_dir = arr[from_no].direction().get_vector();
    Vector curr_dir = arr[from_no + 1].direction().get_vector();
    for (int i = from_no + 1; i < to_no; i++) {
      Vector next_dir = arr[i + 1].direction().get_vector();

      Line[] lines = new Line[4];

      lines[0] = arr[i].translate(-p_half_width);
      // current center line translated to the right

      // create the front line of the offset shape
      Side next_dir_from_curr_dir = next_dir.side_of(curr_dir);
      // left turn from curr_line to next_line
      if (next_dir_from_curr_dir == Side.ON_THE_LEFT) {
        lines[1] = arr[i + 1].translate(-p_half_width);
        // next right line
      } else {
        lines[1] = arr[i + 1].opposite().translate(-p_half_width);
        // next left line in opposite direction
      }

      lines[2] = arr[i].opposite().translate(-p_half_width);
      // current left line in opposite direction

      // create the back line of the offset shape
      Side curr_dir_from_prev_dir = curr_dir.side_of(prev_dir);
      // left turn from prev_line to curr_line
      if (curr_dir_from_prev_dir == Side.ON_THE_LEFT) {
        lines[3] = arr[i - 1].translate(-p_half_width);
        // previous line translated to the right
      } else {
        lines[3] = arr[i - 1].opposite().translate(-p_half_width);
        // previous left line in opposite direction
      }
      // cut off outstanding corners with following shapes
      FloatPoint corner_to_check = null;
      Line curr_line = lines[1];
      Line check_line;
      if (next_dir_from_curr_dir == Side.ON_THE_LEFT) {
        check_line = lines[2];
      } else {
        check_line = lines[0];
      }
      FloatPoint check_distance_corner = corner_approx(i);
      final double check_dist_square = 2.0 * p_half_width * p_half_width;
      Collection<Line> cut_dog_ear_lines = new LinkedList<>();
      Vector tmp_curr_dir = next_dir;
      boolean direction_changed = false;
      for (int j = i + 2; j < arr.length - 1; j++) {
        if (corner_approx(j - 1).distance_square(check_distance_corner) > check_dist_square) {
          break;
        }
        if (!direction_changed) {
          corner_to_check = curr_line.intersection_approx(check_line);
        }
        Vector tmp_next_dir = arr[j].direction().get_vector();
        Line next_border_line;
        Side tmp_next_dir_from_tmp_curr_dir = tmp_next_dir.side_of(tmp_curr_dir);
        direction_changed = tmp_next_dir_from_tmp_curr_dir != next_dir_from_curr_dir;
        if (!direction_changed) {
          if (tmp_next_dir_from_tmp_curr_dir == Side.ON_THE_LEFT) {
            next_border_line = arr[j].translate(-p_half_width);
          } else {
            next_border_line = arr[j].opposite().translate(-p_half_width);
          }

          if (next_border_line.side_of(corner_to_check) == Side.ON_THE_LEFT && next_border_line.side_of(this.corner(i)) == Side.ON_THE_RIGHT
              && next_border_line.side_of(this.corner(i - 1)) == Side.ON_THE_RIGHT)
          // an outstanding corner
          {
            cut_dog_ear_lines.add(next_border_line);
          }
          tmp_curr_dir = tmp_next_dir;
          curr_line = next_border_line;
        }
      }
      // cut off outstanding corners with previous shapes
      check_distance_corner = corner_approx(i - 1);
      if (curr_dir_from_prev_dir == Side.ON_THE_LEFT) {
        check_line = lines[2];
      } else {
        check_line = lines[0];
      }
      curr_line = lines[3];
      tmp_curr_dir = prev_dir;
      direction_changed = false;
      for (int j = i - 2; j >= 1; j--) {
        if (corner_approx(j).distance_square(check_distance_corner) > check_dist_square) {
          break;
        }
        if (!direction_changed) {
          corner_to_check = curr_line.intersection_approx(check_line);
        }
        Vector tmp_prev_dir = arr[j].direction().get_vector();
        Line prev_border_line;
        Side tmp_curr_dir_from_tmp_prev_dir = tmp_curr_dir.side_of(tmp_prev_dir);
        direction_changed = tmp_curr_dir_from_tmp_prev_dir != curr_dir_from_prev_dir;
        if (!direction_changed) {
          if (tmp_curr_dir.side_of(tmp_prev_dir) == Side.ON_THE_LEFT) {
            prev_border_line = arr[j].translate(-p_half_width);
          } else {
            prev_border_line = arr[j].opposite().translate(-p_half_width);
          }
          if (prev_border_line.side_of(corner_to_check) == Side.ON_THE_LEFT && prev_border_line.side_of(this.corner(i)) == Side.ON_THE_RIGHT
              && prev_border_line.side_of(this.corner(i - 1)) == Side.ON_THE_RIGHT)
          // an outstanding corner
          {
            cut_dog_ear_lines.add(prev_border_line);
          }
          tmp_curr_dir = tmp_prev_dir;
          curr_line = prev_border_line;
        }
      }
      TileShape s1 = TileShape.get_instance(lines);
      int cut_line_count = cut_dog_ear_lines.size();
      if (cut_line_count > 0) {
        Line[] cut_lines = new Line[cut_line_count];
        Iterator<Line> it = cut_dog_ear_lines.iterator();
        for (int j = 0; j < cut_line_count; j++) {
          cut_lines[j] = it.next();
        }
        s1 = s1.intersection(TileShape.get_instance(cut_lines));
      }
      int curr_shape_no = i - from_no - 1;
      TileShape bounding_shape;
      if (USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES)
      // intersect with the bounding octagon
      {
        IntOctagon surr_oct = bounding_octagon(i - 1, i);
        bounding_shape = surr_oct.offset(p_half_width);

      } else
      // intersect with the bounding box
      {
        IntBox surr_box = bounding_box(i - 1, i);
        IntBox offset_box = surr_box.offset(p_half_width);
        bounding_shape = offset_box.to_Simplex();
      }
      shape_arr[curr_shape_no] = bounding_shape.intersection_with_simplify(s1);
      if (shape_arr[curr_shape_no].is_empty()) {
        FRLogger.warn("offset_shapes: shape is empty");
      }

      prev_dir = curr_dir;
      curr_dir = next_dir;
    }
    return shape_arr;
  }

  /**
   * Calculates the offset shape for a single line segment.
   *
   * <p>Convenience method that computes the offset shape for one specific line
   * segment by calling {@link #offset_shapes(int, int, int)} with a range of one.
   *
   * <p><strong>Valid Range:</strong>
   * The segment index p_no must satisfy: 0 ≤ p_no ≤ arr.length - 3
   *
   * @param p_half_width half the desired trace width (radius from centerline)
   * @param p_no the line segment index (0-based, excluding end caps)
   * @return the offset shape for the specified segment, or null if index is invalid
   *
   * @see #offset_shapes(int, int, int)
   */
  public TileShape offset_shape(int p_half_width, int p_no) {
    if (p_no < 0 || p_no > arr.length - 3) {
      FRLogger.warn("Polyline.offset_shape: p_no out of range");
      return null;
    }
    TileShape[] result = offset_shapes(p_half_width, p_no, p_no + 2);
    return result[0];
  }

  /**
   * Calculates a simple rectangular bounding box around a line segment with offset.
   *
   * <p>Provides a faster but less accurate alternative to {@link #offset_shape(int, int)}
   * by using a simple axis-aligned bounding box instead of computing the exact
   * offset shape. Useful for quick collision detection or area estimation.
   *
   * <p><strong>Note:</strong>
   * The box will be larger than the actual trace shape, especially for diagonal
   * segments. For exact shapes, use {@link #offset_shape(int, int)}.
   *
   * <p><strong>Valid Range:</strong>
   * The segment index p_no must satisfy: 0 ≤ p_no ≤ arr.length - 3
   *
   * @param p_half_width half the desired trace width (radius from centerline)
   * @param p_no the line segment index (0-based, excluding end caps)
   * @return axis-aligned bounding box with offset
   *
   * @see #offset_shape(int, int)
   * @see LineSegment#bounding_box()
   */
  public IntBox offset_box(int p_half_width, int p_no) {
    LineSegment curr_line_segment = new LineSegment(this, p_no + 1);
    return curr_line_segment.bounding_box().offset(p_half_width);
  }

  /**
   * Returns a new polyline translated by the specified vector.
   *
   * <p>Creates a copy of this polyline shifted by the given vector. If the
   * vector is zero, returns this polyline unchanged (optimization).
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Moving traces during interactive editing</li>
   *   <li>Board transformations (moving components)</li>
   *   <li>Generating parallel traces</li>
   * </ul>
   *
   * @param p_vector the translation vector
   * @return translated polyline, or this if vector is zero
   *
   * @see Line#translate_by(Vector)
   */
  public Polyline translate_by(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    Line[] new_arr = new Line[arr.length];
    for (int i = 0; i < new_arr.length; i++) {
      new_arr[i] = arr[i].translate_by(p_vector);
    }
    return new Polyline(new_arr);
  }

  /**
   * Returns a new polyline rotated by multiples of 90 degrees around a pole point.
   *
   * <p>Performs exact rotation using integer arithmetic (no rounding errors).
   *
   * <p><strong>Rotation Angles:</strong>
   * <ul>
   *   <li>p_factor = 1: 90° counter-clockwise</li>
   *   <li>p_factor = 2: 180° rotation</li>
   *   <li>p_factor = 3: 270° counter-clockwise (90° clockwise)</li>
   *   <li>p_factor = 4: 360° (full rotation, returns to original)</li>
   * </ul>
   *
   * @param p_factor number of 90-degree rotations (can be negative)
   * @param p_pole the center point of rotation
   * @return rotated polyline
   *
   * @see Line#turn_90_degree(int, IntPoint)
   */
  public Polyline turn_90_degree(int p_factor, IntPoint p_pole) {
    Line[] new_arr = new Line[arr.length];
    for (int i = 0; i < new_arr.length; i++) {
      new_arr[i] = arr[i].turn_90_degree(p_factor, p_pole);
    }
    return new Polyline(new_arr);
  }

  /**
   * Returns a new polyline rotated by an arbitrary angle around a pole point.
   *
   * <p>Performs approximate rotation using floating-point arithmetic. Corner
   * coordinates are rounded to nearest integers after rotation.
   *
   * <p><strong>Note:</strong>
   * Due to rounding, small precision errors may accumulate. For 90-degree
   * rotations, prefer {@link #turn_90_degree(int, IntPoint)} which uses exact
   * integer arithmetic.
   *
   * <p><strong>Optimization:</strong>
   * If angle is zero, returns this polyline unchanged.
   *
   * @param p_angle rotation angle in radians
   * @param p_pole the center point of rotation
   * @return rotated polyline with rounded corners
   *
   * @see #turn_90_degree(int, IntPoint)
   * @see FloatPoint#rotate(double, FloatPoint)
   */
  public Polyline rotate_approx(double p_angle, FloatPoint p_pole) {
    if (p_angle == 0) {
      return this;
    }
    IntPoint[] new_corners = new IntPoint[this.corner_count()];
    for (int i = 0; i < new_corners.length; i++) {

      new_corners[i] = this.corner_approx(i).rotate(p_angle, p_pole).round();
    }
    return new Polyline(new_corners);
  }

  /**
   * Returns a new polyline mirrored at the vertical line through the pole point.
   *
   * <p>Performs vertical mirror transformation (flip left-right). The vertical
   * line passes through p_pole with infinite extent.
   *
   * <p><strong>Transformation:</strong>
   * Each point (x, y) is mirrored to (2×pole.x - x, y)
   *
   * @param p_pole point defining the vertical mirror axis
   * @return vertically mirrored polyline
   *
   * @see #mirror_horizontal(IntPoint)
   * @see Line#mirror_vertical(IntPoint)
   */
  public Polyline mirror_vertical(IntPoint p_pole) {
    Line[] new_arr = new Line[arr.length];
    for (int i = 0; i < new_arr.length; i++) {
      new_arr[i] = arr[i].mirror_vertical(p_pole);
    }
    return new Polyline(new_arr);
  }

  /**
   * Returns a new polyline mirrored at the horizontal line through the pole point.
   *
   * <p>Performs horizontal mirror transformation (flip top-bottom). The horizontal
   * line passes through p_pole with infinite extent.
   *
   * <p><strong>Transformation:</strong>
   * Each point (x, y) is mirrored to (x, 2×pole.y - y)
   *
   * @param p_pole point defining the horizontal mirror axis
   * @return horizontally mirrored polyline
   *
   * @see #mirror_vertical(IntPoint)
   * @see Line#mirror_horizontal(IntPoint)
   */
  public Polyline mirror_horizontal(IntPoint p_pole) {
    Line[] new_arr = new Line[arr.length];
    for (int i = 0; i < new_arr.length; i++) {
      new_arr[i] = arr[i].mirror_horizontal(p_pole);
    }
    return new Polyline(new_arr);
  }

  /**
   * Returns the smallest axis-aligned box containing a range of corners.
   *
   * <p>Computes the minimal bounding box that contains all corner points
   * from index p_from_corner_no to p_to_corner_no (inclusive).
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Find minimum and maximum x, y coordinates across all corners</li>
   *   <li>Floor the minimum coordinates (lower-left)</li>
   *   <li>Ceil the maximum coordinates (upper-right)</li>
   *   <li>Create box from these two points</li>
   * </ol>
   *
   * <p><strong>Corner Indices:</strong>
   * Indices are automatically clamped to valid range [0, corner_count-1].
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Quick collision detection</li>
   *   <li>Spatial indexing</li>
   *   <li>Area estimation</li>
   *   <li>Canvas clipping</li>
   * </ul>
   *
   * @param p_from_corner_no starting corner index (inclusive, clamped to valid range)
   * @param p_to_corner_no ending corner index (inclusive, clamped to valid range)
   * @return minimal axis-aligned box containing specified corners
   *
   * @see #bounding_box()
   * @see #bounding_octagon(int, int)
   */
  public IntBox bounding_box(int p_from_corner_no, int p_to_corner_no) {
    int from_corner_no = Math.max(p_from_corner_no, 0);
    int to_corner_no = Math.min(p_to_corner_no, arr.length - 2);
    double llx = Integer.MAX_VALUE;
    double lly = llx;
    double urx = Integer.MIN_VALUE;
    double ury = urx;
    for (int i = from_corner_no; i <= to_corner_no; i++) {
      FloatPoint curr_corner = corner_approx(i);
      llx = Math.min(llx, curr_corner.x);
      lly = Math.min(lly, curr_corner.y);
      urx = Math.max(urx, curr_corner.x);
      ury = Math.max(ury, curr_corner.y);
    }
    IntPoint lower_left = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
    IntPoint upper_right = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
    return new IntBox(lower_left, upper_right);
  }

  /**
   * Returns the smallest axis-aligned box containing all corners of this polyline.
   *
   * <p>Convenience method that computes the bounding box for the entire polyline
   * by calling {@link #bounding_box(int, int)} with the full corner range.
   *
   * <p><strong>Caching:</strong>
   * The result is cached in {@link #precalculated_bounding_box} for performance.
   * Subsequent calls return the cached value.
   *
   * @return minimal axis-aligned box containing all corners
   *
   * @see #bounding_box(int, int)
   */
  public IntBox bounding_box() {
    if (precalculated_bounding_box == null) {
      precalculated_bounding_box = bounding_box(0, corner_count() - 1);
    }
    return precalculated_bounding_box;
  }

  /**
   * Returns the smallest bounding octagon containing a range of corners.
   *
   * <p>Computes a minimal octagon-shaped bounding region that contains all corner
   * points from index p_from_corner_no to p_to_corner_no (inclusive). Octagons
   * provide tighter bounds than axis-aligned boxes, especially for diagonal traces.
   *
   * <p><strong>Octagon Definition:</strong>
   * An octagon is defined by 8 bounding lines:
   * <ul>
   *   <li><strong>Axis-aligned:</strong> left (lx), right (rx), bottom (ly), top (uy)</li>
   *   <li><strong>Diagonal:</strong> upper-left (ulx), lower-right (lrx), lower-left (llx), upper-right (urx)</li>
   * </ul>
   *
   * <p><strong>Algorithm:</strong>
   * For each corner point (x, y):
   * <ol>
   *   <li>Update axis-aligned bounds: lx, ly, rx, uy</li>
   *   <li>Update diagonal bounds using:
   *     <ul>
   *       <li>ulx, lrx from x - y</li>
   *       <li>llx, urx from x + y</li>
   *     </ul>
   *   </li>
   *   <li>Floor minimums, ceil maximums for integer coordinates</li>
   * </ol>
   *
   * <p><strong>Advantages over Boxes:</strong>
   * <ul>
   *   <li>~30% tighter bounds for 45° traces</li>
   *   <li>Better performance in offset shape calculations</li>
   *   <li>More accurate collision detection</li>
   * </ul>
   *
   * <p><strong>Corner Indices:</strong>
   * Indices are automatically clamped to valid range [0, corner_count-1].
   *
   * @param p_from_corner_no starting corner index (inclusive, clamped to valid range)
   * @param p_to_corner_no ending corner index (inclusive, clamped to valid range)
   * @return minimal octagon containing specified corners
   *
   * @see #bounding_box(int, int)
   * @see IntOctagon
   */
  public IntOctagon bounding_octagon(int p_from_corner_no, int p_to_corner_no) {
    int from_corner_no = Math.max(p_from_corner_no, 0);
    int to_corner_no = Math.min(p_to_corner_no, arr.length - 2);
    double lx = Integer.MAX_VALUE;
    double ly = Integer.MAX_VALUE;
    double rx = Integer.MIN_VALUE;
    double uy = Integer.MIN_VALUE;
    double ulx = Integer.MAX_VALUE;
    double lrx = Integer.MIN_VALUE;
    double llx = Integer.MAX_VALUE;
    double urx = Integer.MIN_VALUE;
    for (int i = from_corner_no; i <= to_corner_no; i++) {
      FloatPoint curr = corner_approx(i);
      lx = Math.min(lx, curr.x);
      ly = Math.min(ly, curr.y);
      rx = Math.max(rx, curr.x);
      uy = Math.max(uy, curr.y);
      double tmp = curr.x - curr.y;
      ulx = Math.min(ulx, tmp);
      lrx = Math.max(lrx, tmp);
      tmp = curr.x + curr.y;
      llx = Math.min(llx, tmp);
      urx = Math.max(urx, tmp);
    }
    IntOctagon surrounding_octagon = new IntOctagon((int) Math.floor(lx), (int) Math.floor(ly), (int) Math.ceil(rx), (int) Math.ceil(uy), (int) Math.floor(ulx), (int) Math.ceil(lrx),
        (int) Math.floor(llx), (int) Math.ceil(urx));
    return surrounding_octagon;
  }

  /**
   * Calculates an approximation of the nearest point on this polyline to a reference point.
   *
   * <p>Finds the point on this polyline that is closest to the given reference point,
   * checking both corner points and interior points along line segments.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li><strong>Check all corners:</strong>
   *     <ul>
   *       <li>Calculate distance from p_from_point to each corner</li>
   *       <li>Track minimum distance and corresponding corner</li>
   *     </ul>
   *   </li>
   *   <li><strong>Check segment projections:</strong>
   *     <ul>
   *       <li>For each line segment, project p_from_point onto the line</li>
   *       <li>Verify projection falls within segment bounds (with tolerance)</li>
   *       <li>Update nearest point if projection is closer</li>
   *     </ul>
   *   </li>
   * </ol>
   *
   * <p><strong>Projection Validation:</strong>
   * A projection is considered valid if:
   * <pre>
   * distance(proj, corner[i]) + distance(proj, corner[i-1]) ≤ segment_length + tolerance
   * </pre>
   * This ensures the projection lies between the two segment endpoints.
   *
   * <p><strong>Tolerance:</strong>
   * Uses a tolerance of 1.0 units to handle floating-point rounding errors
   * when checking if projections fall within segments.
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Snapping points to traces during interactive editing</li>
   *   <li>Finding closest connection points</li>
   *   <li>Proximity detection for selection</li>
   *   <li>Distance calculations</li>
   * </ul>
   *
   * @param p_from_point the reference point to measure from
   * @return the nearest point on this polyline (corner or interior point)
   *
   * @see #distance(FloatPoint)
   * @see FloatPoint#projection_approx(Line)
   */
  public FloatPoint nearest_point_approx(FloatPoint p_from_point) {
    double min_distance = Double.MAX_VALUE;
    FloatPoint nearest_point = null;
    // calculate the nearest corner point
    FloatPoint[] corners = corner_approx_arr();
    for (int i = 0; i < corners.length; i++) {
      double curr_distance = corners[i].distance(p_from_point);
      if (curr_distance < min_distance) {
        min_distance = curr_distance;
        nearest_point = corners[i];
      }
    }
    final double c_tolerance = 1;
    for (int i = 1; i < arr.length - 1; i++) {
      FloatPoint projection = p_from_point.projection_approx(arr[i]);
      double curr_distance = projection.distance(p_from_point);
      if (curr_distance < min_distance) {
        // look, if the projection is inside the segment
        double segment_length = corners[i].distance(corners[i - 1]);
        if (projection.distance(corners[i]) + projection.distance(corners[i - 1]) < segment_length + c_tolerance) {
          min_distance = curr_distance;
          nearest_point = projection;
        }
      }
    }
    return nearest_point;
  }

  /**
   * Calculates the minimum distance from a point to this polyline.
   *
   * <p>Convenience method that finds the nearest point on the polyline and
   * returns the distance to that point.
   *
   * @param p_from_point the reference point to measure from
   * @return minimum distance to any point on this polyline
   *
   * @see #nearest_point_approx(FloatPoint)
   */
  public double distance(FloatPoint p_from_point) {
    return p_from_point.distance(nearest_point_approx(p_from_point));
  }

  /**
   * Combines two polylines that share a common endpoint into a single polyline.
   *
   * <p>Attempts to merge this polyline with another polyline if they have a
   * common end corner (first or last corner). The line order of this polyline
   * is always preserved.
   *
   * <p><strong>Combination Rules:</strong>
   * <ul>
   *   <li><strong>Start of this → Start of other:</strong> p_other is reversed and prepended</li>
   *   <li><strong>Start of this → End of other:</strong> p_other is prepended</li>
   *   <li><strong>End of this → Start of other:</strong> p_other is appended</li>
   *   <li><strong>End of this → End of other:</strong> p_other is reversed and appended</li>
   *   <li><strong>No common endpoint:</strong> Returns this polyline unchanged</li>
   * </ul>
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Check if first or last corners match</li>
   *   <li>Determine which ends to combine (4 possible combinations)</li>
   *   <li>Create merged line array (omitting duplicate end caps)</li>
   *   <li>Reverse lines if needed to maintain proper direction</li>
   *   <li>Construct new polyline from combined lines</li>
   * </ol>
   *
   * <p><strong>Line Array Handling:</strong>
   * When combining, the overlapping end cap lines are removed to avoid
   * duplicate corners at the join point. The result has
   * {@code this.arr.length + p_other.arr.length - 2} lines.
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Merging trace segments during routing</li>
   *   <li>Combining paths for optimization</li>
   *   <li>Connecting partial routes</li>
   * </ul>
   *
   * @param p_other the polyline to combine with this one
   * @return combined polyline if endpoints match, otherwise this polyline
   *
   * @see #reverse()
   * @see #split(int, Line)
   */
  public Polyline combine(Polyline p_other) {
    if (p_other == null || arr.length < 3 || p_other.arr.length < 3) {
      return this;
    }
    boolean combine_at_start;
    boolean combine_other_at_start;
    if (first_corner().equals(p_other.first_corner())) {
      combine_at_start = true;
      combine_other_at_start = true;
    } else if (first_corner().equals(p_other.last_corner())) {
      combine_at_start = true;
      combine_other_at_start = false;
    } else if (last_corner().equals(p_other.first_corner())) {
      combine_at_start = false;
      combine_other_at_start = true;
    } else if (last_corner().equals(p_other.last_corner())) {
      combine_at_start = false;
      combine_other_at_start = false;
    } else {
      return this; // no common endpoint
    }
    Line[] line_arr = new Line[arr.length + p_other.arr.length - 2];
    if (combine_at_start) {
      // insert the lines of p_other in front
      if (combine_other_at_start) {
        // insert in reverse order, skip the first line of p_other
        for (int i = 0; i < p_other.arr.length - 1; i++) {
          line_arr[i] = p_other.arr[p_other.arr.length - i - 1].opposite();
        }
      } else {
        // skip the last line of p_other
        System.arraycopy(p_other.arr, 0, line_arr, 0, p_other.arr.length - 1);
      }
      // append the lines of this polyline, skip the first line
      System.arraycopy(arr, 1, line_arr, p_other.arr.length - 1, arr.length - 1);
    } else {
      // insert the lines of this polyline in front, skip the last line
      System.arraycopy(arr, 0, line_arr, 0, arr.length - 1);
      if (combine_other_at_start) {
        // skip the first line of p_other
        System.arraycopy(p_other.arr, 1, line_arr, arr.length - 1, p_other.arr.length - 1);
      } else {
        // insert in reverse order, skip the last line of p_other
        for (int i = 1; i < p_other.arr.length; i++) {
          line_arr[arr.length + i - 2] = p_other.arr[p_other.arr.length - i - 1].opposite();
        }
      }
    }
    return new Polyline(line_arr);
  }

  /**
   * Splits this polyline into two pieces at a specified line by inserting an end line.
   *
   * <p>Divides the polyline at line p_line_no by inserting p_end_line as:
   * <ul>
   *   <li>The concluding line of the first split piece</li>
   *   <li>The start line of the second split piece</li>
   * </ul>
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Calculate new corner at intersection of arr[p_line_no] and p_end_line</li>
   *   <li>Create first piece: lines [0..p_line_no-1] + p_end_line</li>
   *   <li>Create second piece: p_end_line + lines [p_line_no+1..end]</li>
   *   <li>Construct polylines from both pieces</li>
   * </ol>
   *
   * <p><strong>Requirements:</strong>
   * <ul>
   *   <li>1 ≤ p_line_no ≤ arr.length - 2 (cannot split at end caps)</li>
   *   <li>p_end_line must not be parallel to arr[p_line_no]</li>
   *   <li>p_end_line must intersect arr[p_line_no], not just touch endpoints</li>
   * </ul>
   *
   * <p><strong>No-Split Conditions:</strong>
   * Returns null if:
   * <ul>
   *   <li>p_line_no is out of valid range</li>
   *   <li>p_end_line is parallel to arr[p_line_no]</li>
   *   <li>Intersection point equals an existing endpoint (touching, not crossing)</li>
   * </ul>
   *
   * <p><strong>Result Array:</strong>
   * Returns array of 2 polylines: [first_piece, second_piece], or null if no split occurred.
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Dividing traces at intersection points</li>
   *   <li>Creating branch connections</li>
   *   <li>Trace editing and modification</li>
   * </ul>
   *
   * @param p_line_no the line index where splitting occurs (1 ≤ p_line_no ≤ arr.length-2)
   * @param p_end_line the line to insert at the split point (must not be parallel to arr[p_line_no])
   * @return array of two split polylines, or null if splitting is not possible
   *
   * @see #combine(Polyline)
   */
  public Polyline[] split(int p_line_no, Line p_end_line) {
    if (p_line_no < 1 || p_line_no > arr.length - 2) {
      FRLogger.warn("Polyline.split: p_line_no out of range");
      return null;
    }
    if (this.arr[p_line_no].is_parallel(p_end_line)) {
      return null;
    }
    Point new_end_corner = this.arr[p_line_no].intersection(p_end_line);
    if (p_line_no == 1 && new_end_corner.equals(this.first_corner()) || p_line_no >= arr.length - 2 && new_end_corner.equals(this.last_corner())) {
      // No split, if p_end_line does not intersect, but touches
      // only this Polyline at an end point.
      return null;
    }
    Line[] first_piece;
    if (this.corner(p_line_no - 1).equals(new_end_corner)) {
      // skip line segment of length 0 at the end of the first piece
      first_piece = new Line[p_line_no + 1];
      System.arraycopy(arr, 0, first_piece, 0, first_piece.length);

    } else {
      first_piece = new Line[p_line_no + 2];
      System.arraycopy(arr, 0, first_piece, 0, p_line_no + 1);
      first_piece[p_line_no + 1] = p_end_line;
    }
    Line[] second_piece;
    if (this.corner(p_line_no).equals(new_end_corner)) {
      // skip line segment of length 0 at the beginning of the second piece
      second_piece = new Line[arr.length - p_line_no];
      System.arraycopy(this.arr, p_line_no, second_piece, 0, second_piece.length);

    } else {
      second_piece = new Line[arr.length - p_line_no + 1];
      second_piece[0] = p_end_line;
      System.arraycopy(this.arr, p_line_no, second_piece, 1, second_piece.length - 1);
    }
    Polyline[] result = new Polyline[2];
    result[0] = new Polyline(first_piece);
    result[1] = new Polyline(second_piece);
    if (result[0].is_point() || result[1].is_point()) {
      return null;
    }
    return result;
  }

  /**
   * Creates a new polyline by removing a range of lines from this polyline.
   *
   * <p>Removes lines from index p_from_no to p_to_no (inclusive) and returns
   * a new polyline with the remaining lines. The original polyline is unchanged.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Copy lines [0..p_from_no-1] to new array</li>
   *   <li>Skip lines [p_from_no..p_to_no]</li>
   *   <li>Copy lines [p_to_no+1..end] to new array</li>
   *   <li>Construct new polyline from array</li>
   * </ol>
   *
   * <p><strong>Requirements:</strong>
   * <ul>
   *   <li>0 ≤ p_from_no ≤ p_to_no ≤ arr.length - 1</li>
   *   <li>Returns this polyline unchanged if indices are invalid</li>
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Removing problematic segments from traces</li>
   *   <li>Simplifying complex polylines</li>
   *   <li>Trace editing and cleanup</li>
   * </ul>
   *
   * @param p_from_no starting index of lines to skip (inclusive)
   * @param p_to_no ending index of lines to skip (inclusive)
   * @return new polyline with specified lines removed, or this if indices invalid
   */
  public Polyline skip_lines(int p_from_no, int p_to_no) {
    if (p_from_no < 0 || p_to_no > arr.length - 1 || p_from_no > p_to_no) {
      return this;
    }
    Line[] new_lines = new Line[arr.length - (p_to_no - p_from_no + 1)];
    System.arraycopy(arr, 0, new_lines, 0, p_from_no);
    System.arraycopy(arr, p_to_no + 1, new_lines, p_from_no, new_lines.length - p_from_no);
    return new Polyline(new_lines);
  }

  /**
   * Checks if a point lies exactly on any line segment of this polyline.
   *
   * <p>Tests whether the given point is contained within any of the line segments
   * (excluding end caps). A point is considered contained if it lies on the
   * line defined by a segment.
   *
   * <p><strong>Algorithm:</strong>
   * For each interior line segment (lines 1 to arr.length-2):
   * <ol>
   *   <li>Create a LineSegment from the polyline</li>
   *   <li>Check if point is contained in the segment</li>
   *   <li>Return true on first match</li>
   * </ol>
   *
   * <p><strong>Note:</strong>
   * This performs exact geometric containment testing, not approximate
   * distance checking. For proximity testing, use {@link #nearest_point_approx(FloatPoint)}.
   *
   * @param p_point the point to test for containment
   * @return true if the point lies on any line segment of this polyline
   *
   * @see LineSegment#contains(Point)
   * @see #nearest_point_approx(FloatPoint)
   */
  public boolean contains(Point p_point) {
    for (int i = 1; i < arr.length - 1; i++) {
      LineSegment curr_segment = new LineSegment(this, i);
      if (curr_segment.contains(p_point)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a perpendicular line segment from a point to the nearest line segment of this polyline.
   *
   * <p>Constructs a line segment that is perpendicular to the polyline and connects
   * p_from_point to the nearest intersection point on the polyline.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Find the line segment with minimum perpendicular distance</li>
   *   <li>Calculate perpendicular direction from point to line</li>
   *   <li>Verify projection falls within segment bounds</li>
   *   <li>Create line segment from point to projection</li>
   * </ol>
   *
   * <p><strong>Validation:</strong>
   * Returns null if:
   * <ul>
   *   <li>Perpendicular does not intersect the nearest segment within bounds</li>
   *   <li>The point is already contained in the polyline</li>
   *   <li>Projection point falls outside the segment</li>
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Creating branch connections to traces</li>
   *   <li>Finding optimal connection points</li>
   *   <li>Measuring clearance violations</li>
   * </ul>
   *
   * @param p_from_point the point to project from
   * @return perpendicular line segment to nearest polyline segment, or null if invalid
   *
   * @see #nearest_point_approx(FloatPoint)
   * @see #contains(Point)
   */
  public LineSegment projection_line(Point p_from_point) {
    FloatPoint from_point = p_from_point.to_float();
    double min_distance = Double.MAX_VALUE;
    Line result_line = null;
    Line nearest_line = null;
    for (int i = 1; i < arr.length - 1; i++) {
      FloatPoint projection = from_point.projection_approx(arr[i]);
      double curr_distance = projection.distance(from_point);
      if (curr_distance < min_distance) {
        Direction direction_towards_line = this.arr[i].perpendicular_direction(p_from_point);
        if (direction_towards_line == null) {
          continue;
        }
        Line curr_result_line = new Line(p_from_point, direction_towards_line);
        Point prev_corner = this.corner(i - 1);
        Point next_corner = this.corner(i);
        Side prev_corner_side = curr_result_line.side_of(prev_corner);
        Side next_corner_side = curr_result_line.side_of(next_corner);
        if (prev_corner_side == next_corner_side && prev_corner_side != Side.COLLINEAR) {
          // the projection point is outside the line segment
          continue;
        }
        nearest_line = this.arr[i];
        min_distance = curr_distance;
        result_line = curr_result_line;
      }
    }
    if (nearest_line == null) {
      return null;
    }
    Line start_line = new Line(p_from_point, nearest_line.direction());
    return new LineSegment(start_line, result_line, nearest_line);
  }

  /**
   * Shortens this polyline to a specified number of lines with adjusted last segment length.
   *
   * <p>Reduces the polyline to p_new_line_count lines and adjusts the length of the
   * final segment to approximately p_last_segment_length. The last corner of the result
   * will be an IntPoint (rounded from floating-point calculation).
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Get the last two corners at the shortened length</li>
   *   <li>Calculate new last corner by adjusting segment length</li>
   *   <li>If new corner equals existing corner, skip last line instead</li>
   *   <li>Copy first (p_new_line_count - 2) lines</li>
   *   <li>Create new second-to-last line to new corner</li>
   *   <li>Create new perpendicular end cap</li>
   * </ol>
   *
   * <p><strong>Last Segment Adjustment:</strong>
   * The final segment length is approximate because:
   * <ul>
   *   <li>Calculations use floating-point corner approximations</li>
   *   <li>Result is rounded to nearest integer point</li>
   *   <li>Actual length may differ slightly from p_last_segment_length</li>
   * </ul>
   *
   * <p><strong>End Cap:</strong>
   * The last line is a perpendicular end cap, created by turning the
   * second-to-last line's direction by 270° (6 × 45°).
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Trimming traces to specific lengths</li>
   *   <li>Shortening routes during optimization</li>
   *   <li>Adjusting trace endpoints during editing</li>
   * </ul>
   *
   * @param p_new_line_count the desired number of lines in the shortened polyline
   * @param p_last_segment_length the approximate desired length of the final segment
   * @return shortened polyline with adjusted last segment
   *
   * @see #skip_lines(int, int)
   * @see FloatPoint#change_length(FloatPoint, double)
   */
  public Polyline shorten(int p_new_line_count, double p_last_segment_length) {
    FloatPoint last_corner = this.corner_approx(p_new_line_count - 2);
    FloatPoint prev_last_corner = this.corner_approx(p_new_line_count - 3);
    IntPoint new_last_corner = prev_last_corner.change_length(last_corner, p_last_segment_length).round();
    if (new_last_corner.equals(this.corner(this.corner_count() - 2))) {
      // skip the last line
      return skip_lines(p_new_line_count - 1, p_new_line_count - 1);
    }
    Line[] new_lines = new Line[p_new_line_count];
    System.arraycopy(arr, 0, new_lines, 0, p_new_line_count - 2);
    // create the last 2 lines of the new polyline
    Point first_line_point = arr[p_new_line_count - 2].a;
    if (first_line_point.equals(new_last_corner)) {
      first_line_point = arr[p_new_line_count - 2].b;
    }
    Line new_prev_last_line = new Line(first_line_point, new_last_corner);
    new_lines[p_new_line_count - 2] = new_prev_last_line;
    new_lines[p_new_line_count - 1] = Line.get_instance(new_last_corner, new_prev_last_line.direction().turn_45_degree(6));
    return new Polyline(new_lines);
  }
}