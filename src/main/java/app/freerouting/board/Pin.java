package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.LogicalPart;
import app.freerouting.core.Package;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class describing the functionality of an electrical Item on the board with a shape on 1 or
 * several layers.
 */
public class Pin extends DrillItem implements Serializable {

  /** The number of this pin in its component (starting with 0). */
  public final int pinNo;

  /** The pin, this pin was changed to by swapping or this pin, if no pin swap occurred. */
  private Pin changedTo = this;

  private transient Shape[] precalculatedShapes;

  /**
   * Creates a new instance of Pin with the input parameters. (p_to_layer - p_from_layer + 1) shapes
   * must be provided. p_pin_no is the number of the pin in its component (starting with 0).
   */
  Pin(
      int p_component_no,
      int p_pin_no,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(null, p_net_no_arr, p_clearance_type, p_id_no, p_component_no, p_fixed_state, p_board);

    this.pinNo = p_pin_no;
  }

  /** Calculates the relative location of this pin to its component. */
  public Vector relative_location() {
    Component component = board.components.get(this.get_component_no());
    Package libPackage = component.get_package();
    Package.Pin packagePin = libPackage.get_pin(this.pinNo);
    Vector relLocation = packagePin.relativeLocation;
    double componentRotation = component.get_rotation_in_degree();
    if (!component.placed_on_front() && !board.components.get_flip_style_rotate_first()) {
      relLocation = packagePin.relativeLocation.mirror_at_y_axis();
    }
    if (componentRotation % 90 == 0) {
      int componentNinetyDegreeFactor = ((int) componentRotation) / 90;
      if (componentNinetyDegreeFactor != 0) {
        relLocation = relLocation.turn_90_degree(componentNinetyDegreeFactor);
      }
    } else {
      // rotation may be not exact
      FloatPoint locationApprox = relLocation.to_float();
      locationApprox = locationApprox.rotate(Math.toRadians(componentRotation), FloatPoint.ZERO);
      relLocation = locationApprox.round().difference_by(Point.ZERO);
    }
    if (!component.placed_on_front() && board.components.get_flip_style_rotate_first()) {
      relLocation = relLocation.mirror_at_y_axis();
    }
    return relLocation;
  }

  @Override
  public Point get_center() {
    Point pinCenter = super.get_center();
    if (pinCenter == null) {

      // Calculate the pin center.
      Component component = board.components.get(this.get_component_no());
      pinCenter = component.get_location().translate_by(this.relative_location());

      // check that the pin center is inside the pin shape and correct it eventually

      Padstack padstack = get_padstack();
      int fromLayer = padstack.from_layer();
      int toLayer = padstack.to_layer();
      Shape currShape = null;
      for (int i = 0; i < toLayer - fromLayer + 1; i++) {
        currShape = this.get_shape(i);
        if (currShape != null) {
          break;
        }
      }
      if (currShape == null) {
        FRLogger.warn("Pin: At least 1 shape != null expected");
      } else if (!currShape.contains_inside(pinCenter)) {
        pinCenter = currShape.centre_of_gravity().round();
      }
      this.set_center(pinCenter);
    }
    return pinCenter;
  }

  @Override
  public Padstack get_padstack() {
    Component component = board.components.get(get_component_no());
    if (component == null) {
      FRLogger.warn("Pin.get_padstack; component not found");
      return null;
    }
    int padstackNo = component.get_package().get_pin(pinNo).padstackNo;
    return board.library.padstacks.get(padstackNo);
  }

  @Override
  public Item copy(int p_id_no) {
    int[] currNetNoArr = new int[this.net_count()];
    for (int i = 0; i < currNetNoArr.length; i++) {
      currNetNoArr[i] = get_net_no(i);
    }
    return new Pin(
        get_component_no(),
        this.pinNo,
        currNetNoArr,
        clearance_class_no(),
        p_id_no,
        get_fixed_state(),
        board);
  }

  /** Return the name of this pin in the package of this component. */
  public String name() {
    Component component = board.components.get(this.get_component_no());
    if (component == null) {
      FRLogger.warn("Pin.name: component not found");
      return null;
    }
    return component.get_package().get_pin(pinNo).name;
  }

  /** Gets index of this pin in the library package of the pins component. */
  public int get_index_in_package() {
    return pinNo;
  }

  @Override
  public Shape get_shape(int p_index) {
    Padstack padstack = get_padstack();
    if (this.precalculatedShapes == null) {
      // all shapes have to be calculated  at once, because otherwise calculation
      // of fromLayer and toLayer may not be correct
      this.precalculatedShapes = new Shape[padstack.to_layer() - padstack.from_layer() + 1];

      Component component = board.components.get(this.get_component_no());
      if (component == null) {
        FRLogger.warn("Pin.get_shape: component not found");
        return null;
      }
      Package libPackage = component.get_package();
      if (libPackage == null) {
        FRLogger.warn("Pin.get_shape: package not found");
        return null;
      }
      Package.Pin packagePin = libPackage.get_pin(this.pinNo);
      if (packagePin == null) {
        FRLogger.warn("Pin.get_shape: pinNo out of range");
        return null;
      }
      Vector relLocation = packagePin.relativeLocation;
      double componentRotation = component.get_rotation_in_degree();

      boolean mirrorAtYAxis =
          !component.placed_on_front() && !board.components.get_flip_style_rotate_first();

      if (mirrorAtYAxis) {
        relLocation = packagePin.relativeLocation.mirror_at_y_axis();
      }

      Vector componentTranslation = component.get_location().difference_by(Point.ZERO);

      for (int index = 0; index < this.precalculatedShapes.length; index++) {

        int padstackLayer = get_padstack_layer(index);

        ConvexShape currShape = padstack.get_shape(padstackLayer);
        if (currShape == null) {
          continue;
        }
        double pinRotation = packagePin.rotationInDegree;
        if (pinRotation % 90 == 0) {
          int pinNinetyDegreeFactor = ((int) pinRotation) / 90;
          if (pinNinetyDegreeFactor != 0) {
            currShape = (ConvexShape) currShape.turn_90_degree(pinNinetyDegreeFactor, Point.ZERO);
          }
        } else {
          currShape =
              (ConvexShape) currShape.rotate_approx(Math.toRadians(pinRotation), FloatPoint.ZERO);
        }

        if (mirrorAtYAxis) {
          currShape = (ConvexShape) currShape.mirror_vertical(Point.ZERO);
        }

        // translate the shape first relative to the component
        ConvexShape translatedShape = (ConvexShape) currShape.translate_by(relLocation);

        if (componentRotation % 90 == 0) {
          int componentNinetyDegreeFactor = ((int) componentRotation) / 90;
          if (componentNinetyDegreeFactor != 0) {
            translatedShape =
                (ConvexShape)
                    translatedShape.turn_90_degree(componentNinetyDegreeFactor, Point.ZERO);
          }
        } else {
          translatedShape =
              (ConvexShape)
                  translatedShape.rotate_approx(Math.toRadians(componentRotation), FloatPoint.ZERO);
        }
        if (!component.placed_on_front() && board.components.get_flip_style_rotate_first()) {
          translatedShape = (ConvexShape) translatedShape.mirror_vertical(Point.ZERO);
        }
        this.precalculatedShapes[index] =
            (ConvexShape) translatedShape.translate_by(componentTranslation);
      }
    }
    return this.precalculatedShapes[p_index];
  }

  /** Returns the layer of the padstack shape corresponding to the shape with index p_index. */
  int get_padstack_layer(int p_index) {
    Padstack padstack = get_padstack();
    Component component = board.components.get(this.get_component_no());
    int padstackLayer;
    if (component.placed_on_front() || padstack.placedAbsolute) {
      padstackLayer = p_index + this.first_layer();
    } else {
      padstackLayer = padstack.board_layer_count() - p_index - this.first_layer() - 1;
    }
    return padstackLayer;
  }

  /**
   * Calculates the allowed trace exit directions of the shape of this padstack on layer p_layer
   * together with the minimal trace line lengths into their directions. Currently implemented only
   * for box shapes, where traces are allowed to exit the pad only on the small sides.
   */
  public Collection<TraceExitRestriction> get_trace_exit_restrictions(int p_layer) {
    Collection<TraceExitRestriction> result = new LinkedList<>();
    int padstackLayer = this.get_padstack_layer(p_layer - this.first_layer());
    double padXyFactor = 1.5;
    // setting 1.5 to a higher factor may hinder the shove algorithm of the autorouter between
    // the pins of SMD components, because the channels can get blocked by the shoveFixed stubs.

    Component component = board.components.get(this.get_component_no());
    if (component != null) {
      if (component.get_package().pin_count() <= 3) {
        padXyFactor *= 2; // allow connection to the longer side also for shorter pads.
      }
    }

    Collection<Direction> padstackExitDirections =
        this.get_padstack().get_trace_exit_directions(padstackLayer, padXyFactor);
    if (padstackExitDirections.isEmpty()) {
      return result;
    }

    if (component == null) {
      return result;
    }
    Shape currShape = this.get_shape(p_layer - this.first_layer());
    if (!(currShape instanceof TileShape padShape)) {
      return result;
    }
    double componentRotation = component.get_rotation_in_degree();
    Point pinCenter = this.get_center();
    FloatPoint centerApprox = pinCenter.to_float();

    for (Direction curr_padstack_exit_direction : padstackExitDirections) {

      Package libPackage = component.get_package();
      if (libPackage == null) {
        continue;
      }
      Package.Pin packagePin = libPackage.get_pin(this.pinNo);
      if (packagePin == null) {
        continue;
      }
      double currRotationInDegree = componentRotation + packagePin.rotationInDegree;
      Direction currExitDirection;
      if (currRotationInDegree % 45 == 0) {
        int fortyfiveDegreeFactor = ((int) currRotationInDegree) / 45;
        currExitDirection = curr_padstack_exit_direction.turn_45_degree(fortyfiveDegreeFactor);
      } else {
        double currAngleInRadian =
            Math.toRadians(currRotationInDegree) + curr_padstack_exit_direction.angle_approx();
        currExitDirection = Direction.get_instance_approx(currAngleInRadian);
      }
      // calculate the minimum line length from the pin center into currExitDirection
      int intersectingBorderLineNo =
          padShape.intersecting_border_line_no(pinCenter, currExitDirection);
      if (intersectingBorderLineNo < 0) {
        FRLogger.warn("Pin.get_trace_exit_restrictions: border line not found");
        continue;
      }
      Line currExitLine = new Line(pinCenter, currExitDirection);
      FloatPoint nearestBorderPoint =
          currExitLine.intersection_approx(padShape.border_line(intersectingBorderLineNo));
      TraceExitRestriction currExitRestriction =
          new TraceExitRestriction(currExitDirection, centerApprox.distance(nearestBorderPoint));
      result.add(currExitRestriction);
    }
    return result;
  }

  /** Returns true, if this pin has exit restrictions on some kayer. */
  public boolean has_trace_exit_restrictions() {
    for (int i = this.first_layer(); i <= this.last_layer(); i++) {
      Collection<TraceExitRestriction> currExitRestrictions = get_trace_exit_restrictions(i);
      if (!currExitRestrictions.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true, if vias throw the pads of this pins are allowed, false, otherwise. Currently,
   * drills are allowed to SMD-pins.
   */
  public boolean drill_allowed() {
    return this.first_layer() == this.last_layer();
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    if (p_other == this || p_other instanceof ObstacleArea) {
      return false;
    }
    if (!p_other.shares_net(this)) {
      return true;
    }
    if (p_other instanceof Trace) {
      return false;
    }
    // Same-net vias must be allowed to contact SMD pins during fanout.
    return !this.drill_allowed() || !(p_other instanceof Via);
  }

  @Override
  public void turn_90_degree(int p_factor, IntPoint p_pole) {
    this.set_center(null);
    clear_derived_data();
  }

  @Override
  public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole) {
    this.set_center(null);
    this.clear_derived_data();
  }

  @Override
  public void change_placement_side(IntPoint p_pole) {
    this.set_center(null);
    this.clear_derived_data();
  }

  @Override
  public void clear_derived_data() {
    super.clear_derived_data();
    this.precalculatedShapes = null;
  }

  /** Return all Pins, that can be swapped with this pin. */
  public Set<Pin> get_swappable_pins() {
    Set<Pin> result = new TreeSet<>();
    Component component = this.board.components.get(this.get_component_no());
    if (component == null) {
      return result;
    }
    LogicalPart logicalPart = component.get_logical_part();
    if (logicalPart == null) {
      return result;
    }
    LogicalPart.PartPin thisPartPin = logicalPart.get_pin(this.pinNo);
    if (thisPartPin == null) {
      return result;
    }
    if (thisPartPin.gatePinSwapCode <= 0) {
      return result;
    }
    // look up all part pins with the same gateName and the same gatePinSwapCode
    for (int i = 0; i < logicalPart.pin_count(); i++) {
      if (i == this.pinNo) {
        continue;
      }
      LogicalPart.PartPin currPartPin = logicalPart.get_pin(i);
      if (currPartPin != null
          && currPartPin.gatePinSwapCode == thisPartPin.gatePinSwapCode
          && currPartPin.gateName.equals(thisPartPin.gateName)) {
        Pin currSwappeblePin = this.board.get_pin(this.get_component_no(), currPartPin.pinNo);
        if (currSwappeblePin != null) {
          result.add(currSwappeblePin);
        } else {
          FRLogger.warn("Pin.get_swappable_pins: swappable pin not found");
        }
      }
    }
    return result;
  }

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.PINS);
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    Color[] result;
    if (this.net_count() > 0) {
      if (first_layer() != last_layer()) {
        result = p_graphics_context.get_trace_colors(this.is_user_fixed());
      } else {
        result = p_graphics_context.get_pin_colors();
      }
    } else {
      // display unconnected pins as obstacles
      result = p_graphics_context.get_obstacle_colors();
    }
    return result;
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_pin_color_intensity();
  }

  /** Swaps the nets of this pin and p_other. Returns false on error. */
  public boolean swap(Pin p_other) {
    if (this.net_count() > 1 || p_other.net_count() > 1) {
      FRLogger.warn("Pin.swap not yet implemented for pins belonging to more than 1 net ");
      return false;
    }
    int thisNetNo;
    if (this.net_count() > 0) {
      thisNetNo = this.get_net_no(0);
    } else {
      thisNetNo = 0;
    }
    int otherNetNo;
    if (p_other.net_count() > 0) {
      otherNetNo = p_other.get_net_no(0);
    } else {
      otherNetNo = 0;
    }
    this.assign_net_no(otherNetNo);
    p_other.assign_net_no(thisNetNo);
    Pin tmp = this.changedTo;
    this.changedTo = p_other.changedTo;
    p_other.changedTo = tmp;
    return true;
  }

  /**
   * Returns the pin, this pin was changed to by pin swapping, or this pin, if it was not swapped.
   */
  public Pin get_changed_to() {
    return changedTo;
  }

  @Override
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }

  /** False, if this drillitem is places on the back side of the board */
  @Override
  public boolean is_placed_on_front() {
    boolean result = true;
    Component component = board.components.get(this.get_component_no());
    if (component != null) {
      result = component.placed_on_front();
    }
    return result;
  }

  /** Returns the smallest width of the pin shape on layer p_layer. */
  public double get_min_width(int p_layer) {
    int padstackLayer = get_padstack_layer(p_layer - this.first_layer());
    Shape padstackShape = this.get_padstack().get_shape(padstackLayer);
    if (padstackShape == null) {
      FRLogger.warn("Pin.get_min_width: padstackShape is null");
      return 0;
    }
    IntBox padstackBoundingBox = padstackShape.bounding_box();
    if (padstackBoundingBox == null) {
      FRLogger.warn("Pin.get_min_width: padstackBoundingBox is null");
      return 0;
    }
    return padstackBoundingBox.min_width();
  }

  /**
   * Returns the neckdown half width for traces on p_layer. The neckdown width is used, when the pin
   * width is smaller than the trace width to enter or leave the pin with a trace.
   */
  public int get_trace_neckdown_halfwidth(int p_layer) {
    double result = Math.max(0.5 * this.get_min_width(p_layer) - 1, 1);
    return (int) result;
  }

  /** Returns the largest width of the pin shape on layer p_layer. */
  public double get_max_width(int p_layer) {
    int padstackLayer = get_padstack_layer(p_layer - this.first_layer());
    Shape padstackShape = this.get_padstack().get_shape(padstackLayer);
    if (padstackShape == null) {
      FRLogger.warn("Pin.get_max_width: padstackShape is null");
      return 0;
    }
    IntBox padstackBoundingBox = padstackShape.bounding_box();
    if (padstackBoundingBox == null) {
      FRLogger.warn("Pin.get_max_width: padstackBoundingBox is null");
      return 0;
    }
    return padstackBoundingBox.max_width();
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("pin") + ": ");
    p_window.append(tm.getText("component_2") + " ");
    Component component = board.components.get(this.get_component_no());
    p_window.append(component.name, tm.getText("component_info"), component);
    p_window.append(", " + tm.getText("pin_2") + " ");
    p_window.append(component.get_package().get_pin(this.pinNo).name);
    p_window.append(", " + tm.getText("padstack") + " ");
    Padstack padstack = this.get_padstack();
    p_window.append(padstack.name, tm.getText("padstack_info"), padstack);
    p_window.append(" " + tm.getText("at") + " ");
    p_window.append(this.get_center().to_float());
    this.print_connectable_item_info(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public String get_hover_info(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    Component component = board.components.get(this.get_component_no());
    Padstack padstack = this.get_padstack();
    String componentName = component.name;
    String pinName = component.get_package().get_pin(this.pinNo).name;
    String padstackName = padstack.name;
    String connInfo = this.get_connectable_item_hover_info(p_locale);

    return tm.getText("pin_hover_info", componentName, pinName, padstackName, connInfo);
  }

  /**
   * Calculates the nearest exit restriction direction for changing p_trace_polyline.
   * p_trace_polyline is assumed to start at the pin center. Returns null, if there is no matching
   * exit restrictions.
   */
  Direction calc_nearest_exit_restriction_direction(
      Polyline p_trace_polyline, int p_trace_half_width, int p_layer) {
    Collection<Pin.TraceExitRestriction> traceExitRestrictions =
        this.get_trace_exit_restrictions(p_layer);
    if (traceExitRestrictions.isEmpty()) {
      return null;
    }
    Shape pinShape = this.get_shape(p_layer - this.first_layer());
    Point pinCenter = this.get_center();
    if (!(pinShape instanceof TileShape)) {
      return null;
    }
    final double edgeToTurnDist = this.board.rules.get_pin_edge_to_turn_dist();
    if (edgeToTurnDist < 0) {
      return null;
    }
    TileShape offsetPinShape =
        (TileShape) ((TileShape) pinShape).offset(edgeToTurnDist + p_trace_half_width);
    int[][] entries = offsetPinShape.entrance_points(p_trace_polyline);
    if (entries.length == 0) {
      return null;
    }
    int[] latestEntryTuple = entries[entries.length - 1];
    FloatPoint traceEntryLocationApprox =
        p_trace_polyline.arr[latestEntryTuple[0]].intersection_approx(
            offsetPinShape.border_line(latestEntryTuple[1]));
    // calculate the nearest legal pin exit point to traceEntryLocationApprox
    double minExitCornerDistance = Double.MAX_VALUE;
    FloatPoint nearestExitCorner = null;
    Direction pinExitDirection = null;
    final double TOLERANCE = 1;
    for (Pin.TraceExitRestriction currExitRestriction : traceExitRestrictions) {
      int currIntersectingBorderLineNo =
          offsetPinShape.intersecting_border_line_no(pinCenter, currExitRestriction.direction);
      Line currPinExitRay = new Line(pinCenter, currExitRestriction.direction);
      FloatPoint currExitCorner =
          currPinExitRay.intersection_approx(
              offsetPinShape.border_line(currIntersectingBorderLineNo));
      double currExitCornerDistance = currExitCorner.distance_square(traceEntryLocationApprox);
      boolean newNearestCornerFound = false;
      if (currExitCornerDistance + TOLERANCE < minExitCornerDistance) {
        newNearestCornerFound = true;
      } else if (currExitCornerDistance < minExitCornerDistance + TOLERANCE) {
        // the distances are near equal, compare to the previous corners of p_trace_polyline
        for (int i = 1; i < p_trace_polyline.corner_count(); i++) {
          FloatPoint currTraceCorner = p_trace_polyline.corner_approx(i);
          double currTraceCornerDistance = currTraceCorner.distance_square(currExitCorner);
          double oldTraceCornerDistance = currTraceCorner.distance_square(nearestExitCorner);
          if (currTraceCornerDistance + TOLERANCE < oldTraceCornerDistance) {
            newNearestCornerFound = true;
            break;
          } else if (currTraceCornerDistance > oldTraceCornerDistance + TOLERANCE) {
            break;
          }
        }
      }
      if (newNearestCornerFound) {
        minExitCornerDistance = currExitCornerDistance;
        pinExitDirection = currExitRestriction.direction;
        nearestExitCorner = currExitCorner;
      }
    }
    return pinExitDirection;
  }

  /**
   * Calculates the nearest trace exit point of the pin on p_layer. Returns null, if the pin has no
   * trace exit restrictions.
   */
  public FloatPoint nearest_trace_exit_corner(
      FloatPoint p_from_point, int p_trace_half_width, int p_layer) {
    Collection<Pin.TraceExitRestriction> traceExitRestrictions =
        this.get_trace_exit_restrictions(p_layer);
    if (traceExitRestrictions.isEmpty()) {
      return null;
    }
    Shape pinShape = this.get_shape(p_layer - this.first_layer());
    Point pinCenter = this.get_center();
    if (!(pinShape instanceof TileShape)) {
      return null;
    }
    final double edgeToTurnDist = this.board.rules.get_pin_edge_to_turn_dist();
    if (edgeToTurnDist < 0) {
      return null;
    }
    TileShape offsetPinShape =
        (TileShape) ((TileShape) pinShape).offset(edgeToTurnDist + p_trace_half_width);

    // calculate the nearest legal pin exit point to traceEntryLocationApprox
    double minExitCornerDistance = Double.MAX_VALUE;
    FloatPoint nearestExitCorner = null;
    for (Pin.TraceExitRestriction currExitRestriction : traceExitRestrictions) {
      int currIntersectingBorderLineNo =
          offsetPinShape.intersecting_border_line_no(pinCenter, currExitRestriction.direction);
      Line currPinExitRay = new Line(pinCenter, currExitRestriction.direction);
      FloatPoint currExitCorner =
          currPinExitRay.intersection_approx(
              offsetPinShape.border_line(currIntersectingBorderLineNo));
      double currExitCornerDistance = currExitCorner.distance_square(p_from_point);
      if (currExitCornerDistance < minExitCornerDistance) {
        minExitCornerDistance = currExitCornerDistance;
        nearestExitCorner = currExitCorner;
      }
    }
    return nearestExitCorner;
  }

  @Override
  public String toString() {
    StringBuilder simpleName = new StringBuilder();

    simpleName.append(this.getClass().getSimpleName().toLowerCase());

    if (pinNo > 0) {
      simpleName.append(" #");
      simpleName.append(pinNo);
    }

    if (componentNo > 0) {
      simpleName.append(" of component #");
      simpleName.append(componentNo);
    }

    return simpleName.toString();
  }

  /** Describes an exit restriction from a trace from a pin pad. */
  public static class TraceExitRestriction {

    public final Direction direction;
    public final double minLength;

    /** Creates a new instance of TraceExitRestriction */
    public TraceExitRestriction(Direction p_direction, double p_min_length) {
      direction = p_direction;
      minLength = p_min_length;
    }
  }
}
