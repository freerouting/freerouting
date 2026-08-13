package app.freerouting.board;

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
      int componentNo,
      int pinNo,
      int[] netNoArr,
      int clearanceType,
      int idNo,
      FixedState fixedState,
      BasicBoard board) {
    super(null, netNoArr, clearanceType, idNo, componentNo, fixedState, board);

    this.pinNo = pinNo;
  }

  /** Calculates the relative location of this pin to its component. */
  public Vector relativeLocation() {
    Component component = board.components.get(this.getComponentNo());
    Package libPackage = component.getPackage();
    Package.Pin packagePin = libPackage.getPin(this.pinNo);
    Vector relLocation = packagePin.relativeLocation;
    double componentRotation = component.getRotationInDegree();
    if (!component.placedOnFront() && !board.components.getFlipStyleRotateFirst()) {
      relLocation = packagePin.relativeLocation.mirrorAtYAxis();
    }
    if (componentRotation % 90 == 0) {
      int componentNinetyDegreeFactor = ((int) componentRotation) / 90;
      if (componentNinetyDegreeFactor != 0) {
        relLocation = relLocation.turn90Degree(componentNinetyDegreeFactor);
      }
    } else {
      // rotation may be not exact
      FloatPoint locationApprox = relLocation.toFloat();
      locationApprox = locationApprox.rotate(Math.toRadians(componentRotation), FloatPoint.ZERO);
      relLocation = locationApprox.round().differenceBy(Point.ZERO);
    }
    if (!component.placedOnFront() && board.components.getFlipStyleRotateFirst()) {
      relLocation = relLocation.mirrorAtYAxis();
    }
    return relLocation;
  }

  @Override
  public Point getCenter() {
    Point pinCenter = super.getCenter();
    if (pinCenter == null) {

      // Calculate the pin center.
      Component component = board.components.get(this.getComponentNo());
      pinCenter = component.getLocation().translateBy(this.relativeLocation());

      // check that the pin center is inside the pin shape and correct it eventually

      Padstack padstack = getPadstack();
      int fromLayer = padstack.fromLayer();
      int toLayer = padstack.toLayer();
      Shape currShape = null;
      for (int i = 0; i < toLayer - fromLayer + 1; i++) {
        currShape = this.getShape(i);
        if (currShape != null) {
          break;
        }
      }
      if (currShape == null) {
        FRLogger.warn("Pin: At least 1 shape != null expected");
      } else if (!currShape.containsInside(pinCenter)) {
        pinCenter = currShape.centreOfGravity().round();
      }
      this.setCenter(pinCenter);
    }
    return pinCenter;
  }

  @Override
  public Padstack getPadstack() {
    Component component = board.components.get(getComponentNo());
    if (component == null) {
      FRLogger.warn("Pin.get_padstack; component not found");
      return null;
    }
    int padstackNo = component.getPackage().getPin(pinNo).padstackNo;
    return board.library.padstacks.get(padstackNo);
  }

  @Override
  public Item copy(int idNo) {
    int[] currNetNoArr = new int[this.netCount()];
    for (int i = 0; i < currNetNoArr.length; i++) {
      currNetNoArr[i] = getNetNo(i);
    }
    return new Pin(
        getComponentNo(),
        this.pinNo,
        currNetNoArr,
        clearanceClassNo(),
        idNo,
        getFixedState(),
        board);
  }

  /** Return the name of this pin in the package of this component. */
  public String name() {
    Component component = board.components.get(this.getComponentNo());
    if (component == null) {
      FRLogger.warn("Pin.name: component not found");
      return null;
    }
    return component.getPackage().getPin(pinNo).name;
  }

  /** Gets index of this pin in the library package of the pins component. */
  public int getIndexInPackage() {
    return pinNo;
  }

  @Override
  public Shape getShape(int index) {
    Padstack padstack = getPadstack();
    if (this.precalculatedShapes == null) {
      // all shapes have to be calculated  at once, because otherwise calculation
      // of fromLayer and toLayer may not be correct
      this.precalculatedShapes = new Shape[padstack.toLayer() - padstack.fromLayer() + 1];

      Component component = board.components.get(this.getComponentNo());
      if (component == null) {
        FRLogger.warn("Pin.get_shape: component not found");
        return null;
      }
      Package libPackage = component.getPackage();
      if (libPackage == null) {
        FRLogger.warn("Pin.get_shape: package not found");
        return null;
      }
      Package.Pin packagePin = libPackage.getPin(this.pinNo);
      if (packagePin == null) {
        FRLogger.warn("Pin.get_shape: pinNo out of range");
        return null;
      }
      Vector relLocation = packagePin.relativeLocation;
      double componentRotation = component.getRotationInDegree();

      boolean mirrorOnYaxis =
          !component.placedOnFront() && !board.components.getFlipStyleRotateFirst();

      if (mirrorOnYaxis) {
        relLocation = packagePin.relativeLocation.mirrorAtYAxis();
      }

      Vector componentTranslation = component.getLocation().differenceBy(Point.ZERO);

      for (int shapeIndex = 0; shapeIndex < this.precalculatedShapes.length; shapeIndex++) {

        int padstackLayer = getPadstackLayer(shapeIndex);

        ConvexShape currShape = padstack.getShape(padstackLayer);
        if (currShape == null) {
          continue;
        }
        double pinRotation = packagePin.rotationInDegree;
        if (pinRotation % 90 == 0) {
          int pinNinetyDegreeFactor = ((int) pinRotation) / 90;
          if (pinNinetyDegreeFactor != 0) {
            currShape = (ConvexShape) currShape.turn90Degree(pinNinetyDegreeFactor, Point.ZERO);
          }
        } else {
          currShape =
              (ConvexShape) currShape.rotateApprox(Math.toRadians(pinRotation), FloatPoint.ZERO);
        }

        if (mirrorOnYaxis) {
          currShape = (ConvexShape) currShape.mirrorVertical(Point.ZERO);
        }

        // translate the shape first relative to the component
        ConvexShape translatedShape = (ConvexShape) currShape.translateBy(relLocation);

        if (componentRotation % 90 == 0) {
          int componentNinetyDegreeFactor = ((int) componentRotation) / 90;
          if (componentNinetyDegreeFactor != 0) {
            translatedShape =
                (ConvexShape) translatedShape.turn90Degree(componentNinetyDegreeFactor, Point.ZERO);
          }
        } else {
          translatedShape =
              (ConvexShape)
                  translatedShape.rotateApprox(Math.toRadians(componentRotation), FloatPoint.ZERO);
        }
        if (!component.placedOnFront() && board.components.getFlipStyleRotateFirst()) {
          translatedShape = (ConvexShape) translatedShape.mirrorVertical(Point.ZERO);
        }
        this.precalculatedShapes[shapeIndex] =
            (ConvexShape) translatedShape.translateBy(componentTranslation);
      }
    }
    return this.precalculatedShapes[index];
  }

  /** Returns the layer of the padstack shape corresponding to the shape with index p_index. */
  int getPadstackLayer(int index) {
    Padstack padstack = getPadstack();
    Component component = board.components.get(this.getComponentNo());
    int padstackLayer;
    if (component.placedOnFront() || padstack.placedAbsolute) {
      padstackLayer = index + this.firstLayer();
    } else {
      padstackLayer = padstack.boardLayerCount() - index - this.firstLayer() - 1;
    }
    return padstackLayer;
  }

  /**
   * Calculates the allowed trace exit directions of the shape of this padstack on layer p_layer
   * together with the minimal trace line lengths into their directions. Currently implemented only
   * for box shapes, where traces are allowed to exit the pad only on the small sides.
   */
  public Collection<TraceExitRestriction> getTraceExitRestrictions(int layer) {
    Collection<TraceExitRestriction> result = new LinkedList<>();
    int padstackLayer = this.getPadstackLayer(layer - this.firstLayer());
    double padXyFactor = 1.5;
    // setting 1.5 to a higher factor may hinder the shove algorithm of the autorouter between
    // the pins of SMD components, because the channels can get blocked by the shoveFixed stubs.

    Component component = board.components.get(this.getComponentNo());
    if (component != null) {
      if (component.getPackage().pinCount() <= 3) {
        padXyFactor *= 2; // allow connection to the longer side also for shorter pads.
      }
    }

    Collection<Direction> padstackExitDirections =
        this.getPadstack().getTraceExitDirections(padstackLayer, padXyFactor);
    if (padstackExitDirections.isEmpty()) {
      return result;
    }

    if (component == null) {
      return result;
    }
    Shape currShape = this.getShape(layer - this.firstLayer());
    if (!(currShape instanceof TileShape padShape)) {
      return result;
    }
    double componentRotation = component.getRotationInDegree();
    Point pinCenter = this.getCenter();
    FloatPoint centerApprox = pinCenter.toFloat();

    for (Direction currPadstackExitDirection : padstackExitDirections) {

      Package libPackage = component.getPackage();
      if (libPackage == null) {
        continue;
      }
      Package.Pin packagePin = libPackage.getPin(this.pinNo);
      if (packagePin == null) {
        continue;
      }
      double currRotationInDegree = componentRotation + packagePin.rotationInDegree;
      Direction currExitDirection;
      if (currRotationInDegree % 45 == 0) {
        int fortyfiveDegreeFactor = ((int) currRotationInDegree) / 45;
        currExitDirection = currPadstackExitDirection.turn45Degree(fortyfiveDegreeFactor);
      } else {
        double currAngleInRadian =
            Math.toRadians(currRotationInDegree) + currPadstackExitDirection.angleApprox();
        currExitDirection = Direction.getInstanceApprox(currAngleInRadian);
      }
      // calculate the minimum line length from the pin center into currExitDirection
      int intersectingBorderLineNo =
          padShape.intersectingBorderLineNo(pinCenter, currExitDirection);
      if (intersectingBorderLineNo < 0) {
        FRLogger.warn("Pin.get_trace_exit_restrictions: border line not found");
        continue;
      }
      Line currExitLine = new Line(pinCenter, currExitDirection);
      FloatPoint nearestBorderPoint =
          currExitLine.intersectionApprox(padShape.borderLine(intersectingBorderLineNo));
      TraceExitRestriction currExitRestriction =
          new TraceExitRestriction(currExitDirection, centerApprox.distance(nearestBorderPoint));
      result.add(currExitRestriction);
    }
    return result;
  }

  /** Returns true, if this pin has exit restrictions on some kayer. */
  public boolean hasTraceExitRestrictions() {
    for (int i = this.firstLayer(); i <= this.lastLayer(); i++) {
      Collection<TraceExitRestriction> currExitRestrictions = getTraceExitRestrictions(i);
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
  public boolean drillAllowed() {
    return this.firstLayer() == this.lastLayer();
  }

  @Override
  public boolean isObstacle(Item other) {
    if (other == this || other instanceof ObstacleArea) {
      return false;
    }
    if (!other.sharesNet(this)) {
      return true;
    }
    if (other instanceof Trace) {
      return false;
    }
    // Same-net vias must be allowed to contact SMD pins during fanout.
    return !this.drillAllowed() || !(other instanceof Via);
  }

  @Override
  public void turn90Degree(int factor, IntPoint pole) {
    this.setCenter(null);
    clearDerivedData();
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    this.setCenter(null);
    this.clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    this.setCenter(null);
    this.clearDerivedData();
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.precalculatedShapes = null;
  }

  /** Return all Pins, that can be swapped with this pin. */
  public Set<Pin> getSwappablePins() {
    Set<Pin> result = new TreeSet<>();
    Component component = this.board.components.get(this.getComponentNo());
    if (component == null) {
      return result;
    }
    LogicalPart logicalPart = component.getLogicalPart();
    if (logicalPart == null) {
      return result;
    }
    LogicalPart.PartPin thisPartPin = logicalPart.getPin(this.pinNo);
    if (thisPartPin == null) {
      return result;
    }
    if (thisPartPin.gatePinSwapCode <= 0) {
      return result;
    }
    // look up all part pins with the same gateName and the same gatePinSwapCode
    for (int i = 0; i < logicalPart.pinCount(); i++) {
      if (i == this.pinNo) {
        continue;
      }
      LogicalPart.PartPin currPartPin = logicalPart.getPin(i);
      if (currPartPin != null
          && currPartPin.gatePinSwapCode == thisPartPin.gatePinSwapCode
          && currPartPin.gateName.equals(thisPartPin.gateName)) {
        Pin currSwappeblePin = this.board.getPin(this.getComponentNo(), currPartPin.pinNo);
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
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.PINS);
  }

  /** Swaps the nets of this pin and p_other. Returns false on error. */
  public boolean swap(Pin other) {
    if (this.netCount() > 1 || other.netCount() > 1) {
      FRLogger.warn("Pin.swap not yet implemented for pins belonging to more than 1 net ");
      return false;
    }
    int thisNetNo;
    if (this.netCount() > 0) {
      thisNetNo = this.getNetNo(0);
    } else {
      thisNetNo = 0;
    }
    int otherNetNo;
    if (other.netCount() > 0) {
      otherNetNo = other.getNetNo(0);
    } else {
      otherNetNo = 0;
    }
    this.assignNetNo(otherNetNo);
    other.assignNetNo(thisNetNo);
    Pin tmp = this.changedTo;
    this.changedTo = other.changedTo;
    other.changedTo = tmp;
    return true;
  }

  /**
   * Returns the pin, this pin was changed to by pin swapping, or this pin, if it was not swapped.
   */
  public Pin getChangedTo() {
    return changedTo;
  }

  @Override
  public boolean write(ObjectOutputStream stream) {
    try {
      stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }

  /** False, if this drillitem is places on the back side of the board. */
  @Override
  public boolean isPlacedOnFront() {
    boolean result = true;
    Component component = board.components.get(this.getComponentNo());
    if (component != null) {
      result = component.placedOnFront();
    }
    return result;
  }

  /** Returns the smallest width of the pin shape on layer p_layer. */
  public double getMinWidth(int layer) {
    int padstackLayer = getPadstackLayer(layer - this.firstLayer());
    Shape padstackShape = this.getPadstack().getShape(padstackLayer);
    if (padstackShape == null) {
      FRLogger.warn("Pin.get_min_width: padstackShape is null");
      return 0;
    }
    IntBox padstackBoundingBox = padstackShape.boundingBox();
    if (padstackBoundingBox == null) {
      FRLogger.warn("Pin.get_min_width: padstackBoundingBox is null");
      return 0;
    }
    return padstackBoundingBox.minWidth();
  }

  /**
   * Returns the neckdown half width for traces on p_layer. The neckdown width is used, when the pin
   * width is smaller than the trace width to enter or leave the pin with a trace.
   */
  public int getTraceNeckdownHalfwidth(int layer) {
    double result = Math.max(0.5 * this.getMinWidth(layer) - 1, 1);
    return (int) result;
  }

  /** Returns the largest width of the pin shape on layer p_layer. */
  public double getMaxWidth(int layer) {
    int padstackLayer = getPadstackLayer(layer - this.firstLayer());
    Shape padstackShape = this.getPadstack().getShape(padstackLayer);
    if (padstackShape == null) {
      FRLogger.warn("Pin.get_max_width: padstackShape is null");
      return 0;
    }
    IntBox padstackBoundingBox = padstackShape.boundingBox();
    if (padstackBoundingBox == null) {
      FRLogger.warn("Pin.get_max_width: padstackBoundingBox is null");
      return 0;
    }
    return padstackBoundingBox.maxWidth();
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("pin") + ": ");
    window.append(tm.getText("component_2") + " ");
    Component component = board.components.get(this.getComponentNo());
    window.append(component.name, tm.getText("component_info"), component);
    window.append(", " + tm.getText("pin_2") + " ");
    window.append(component.getPackage().getPin(this.pinNo).name);
    window.append(", " + tm.getText("padstack") + " ");
    Padstack padstack = this.getPadstack();
    window.append(padstack.name, tm.getText("padstack_info"), padstack);
    window.append(" " + tm.getText("at") + " ");
    window.append(this.getCenter().toFloat());
    this.printConnectableItemInfo(window, locale);
    window.newline();
  }

  @Override
  public String getHoverInfo(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    Component component = board.components.get(this.getComponentNo());
    Padstack padstack = this.getPadstack();
    String componentName = component.name;
    String pinName = component.getPackage().getPin(this.pinNo).name;
    String padstackName = padstack.name;
    String connInfo = this.getConnectableItemHoverInfo(locale);

    return tm.getText("pin_hover_info", componentName, pinName, padstackName, connInfo);
  }

  /**
   * Calculates the nearest exit restriction direction for changing p_trace_polyline.
   * p_trace_polyline is assumed to start at the pin center. Returns null, if there is no matching
   * exit restrictions.
   */
  Direction calcNearestExitRestrictionDirection(
      Polyline tracePolyline, int traceHalfWidth, int layer) {
    Collection<Pin.TraceExitRestriction> traceExitRestrictions =
        this.getTraceExitRestrictions(layer);
    if (traceExitRestrictions.isEmpty()) {
      return null;
    }
    Shape pinShape = this.getShape(layer - this.firstLayer());
    if (!(pinShape instanceof TileShape)) {
      return null;
    }
    final double edgeToTurnDist = this.board.rules.getPinEdgeToTurnDist();
    if (edgeToTurnDist < 0) {
      return null;
    }
    TileShape offsetPinShape =
        (TileShape) ((TileShape) pinShape).offset(edgeToTurnDist + traceHalfWidth);
    int[][] entries = offsetPinShape.entrancePoints(tracePolyline);
    if (entries.length == 0) {
      return null;
    }
    int[] latestEntryTuple = entries[entries.length - 1];
    FloatPoint traceEntryLocationApprox =
        tracePolyline.arr[latestEntryTuple[0]].intersectionApprox(
            offsetPinShape.borderLine(latestEntryTuple[1]));
    // calculate the nearest legal pin exit point to traceEntryLocationApprox
    double minExitCornerDistance = Double.MAX_VALUE;
    FloatPoint nearestExitCorner = null;
    Direction pinExitDirection = null;
    final double tolerance = 1;
    Point pinCenter = this.getCenter();
    for (Pin.TraceExitRestriction currExitRestriction : traceExitRestrictions) {
      int currIntersectingBorderLineNo =
          offsetPinShape.intersectingBorderLineNo(pinCenter, currExitRestriction.direction);
      Line currPinExitRay = new Line(pinCenter, currExitRestriction.direction);
      FloatPoint currExitCorner =
          currPinExitRay.intersectionApprox(
              offsetPinShape.borderLine(currIntersectingBorderLineNo));
      double currExitCornerDistance = currExitCorner.distanceSquare(traceEntryLocationApprox);
      boolean newNearestCornerFound = false;
      if (currExitCornerDistance + tolerance < minExitCornerDistance) {
        newNearestCornerFound = true;
      } else if (currExitCornerDistance < minExitCornerDistance + tolerance) {
        // the distances are near equal, compare to the previous corners of p_trace_polyline
        for (int i = 1; i < tracePolyline.cornerCount(); i++) {
          FloatPoint currTraceCorner = tracePolyline.cornerApprox(i);
          double currTraceCornerDistance = currTraceCorner.distanceSquare(currExitCorner);
          double oldTraceCornerDistance = currTraceCorner.distanceSquare(nearestExitCorner);
          if (currTraceCornerDistance + tolerance < oldTraceCornerDistance) {
            newNearestCornerFound = true;
            break;
          } else if (currTraceCornerDistance > oldTraceCornerDistance + tolerance) {
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
  public FloatPoint nearestTraceExitCorner(FloatPoint fromPoint, int traceHalfWidth, int layer) {
    Collection<Pin.TraceExitRestriction> traceExitRestrictions =
        this.getTraceExitRestrictions(layer);
    if (traceExitRestrictions.isEmpty()) {
      return null;
    }
    Shape pinShape = this.getShape(layer - this.firstLayer());
    Point pinCenter = this.getCenter();
    if (!(pinShape instanceof TileShape)) {
      return null;
    }
    final double edgeToTurnDist = this.board.rules.getPinEdgeToTurnDist();
    if (edgeToTurnDist < 0) {
      return null;
    }
    TileShape offsetPinShape =
        (TileShape) ((TileShape) pinShape).offset(edgeToTurnDist + traceHalfWidth);

    // calculate the nearest legal pin exit point to traceEntryLocationApprox
    double minExitCornerDistance = Double.MAX_VALUE;
    FloatPoint nearestExitCorner = null;
    for (Pin.TraceExitRestriction currExitRestriction : traceExitRestrictions) {
      int currIntersectingBorderLineNo =
          offsetPinShape.intersectingBorderLineNo(pinCenter, currExitRestriction.direction);
      Line currPinExitRay = new Line(pinCenter, currExitRestriction.direction);
      FloatPoint currExitCorner =
          currPinExitRay.intersectionApprox(
              offsetPinShape.borderLine(currIntersectingBorderLineNo));
      double currExitCornerDistance = currExitCorner.distanceSquare(fromPoint);
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

    /** Creates a new instance of TraceExitRestriction. */
    public TraceExitRestriction(Direction direction, double minLength) {
      this.direction = direction;
      this.minLength = minLength;
    }
  }
}
