package app.freerouting.board.model.items;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.LogicalPart;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Padstack;
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

  /** The index of this pin in its component (starting with 0). */
  public final int pinIndex;

  /** The pin, this pin was changed to by swapping or this pin, if no pin swap occurred. */
  private Pin changedTo = this;

  private transient Shape[] precalculatedShapes;

  /**
   * Creates a new instance of Pin with the input parameters. (toLayer - fromLayer + 1) shapes must
   * be provided. pinIndex is the index of the pin in its component (starting with 0).
   */
  public Pin(
      int componentId,
      int pinIndex,
      int[] netNumbers,
      int clearanceClassIndex,
      int id,
      FixedState fixedState,
      BasicBoard board) {
    super(null, netNumbers, clearanceClassIndex, id, componentId, fixedState, board);

    this.pinIndex = pinIndex;
  }

  /** Calculates the relative location of this pin to its component. */
  public Vector relativeLocation() {
    Component component = board.components.get(this.getComponentId());
    Package libPackage = component.getPackage();
    Package.Pin packagePin = libPackage.getPin(this.pinIndex);
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
      Component component = board.components.get(this.getComponentId());
      pinCenter = component.getLocation().translateBy(this.relativeLocation());

      // check that the pin center is inside the pin shape and correct it eventually

      Padstack padstack = getPadstack();
      int fromLayer = padstack.fromLayer();
      int toLayer = padstack.toLayer();
      Shape currentShape = null;
      for (int i = 0; i < toLayer - fromLayer + 1; i++) {
        currentShape = this.getShape(i);
        if (currentShape != null) {
          break;
        }
      }
      if (currentShape == null) {
        FRLogger.warn("Pin: At least 1 shape != null expected");
      } else if (!currentShape.containsInside(pinCenter)) {
        pinCenter = currentShape.centreOfGravity().round();
      }
      this.setCenter(pinCenter);
    }
    return pinCenter;
  }

  @Override
  public Padstack getPadstack() {
    Component component = board.components.get(getComponentId());
    if (component == null) {
      FRLogger.warn("Pin.get_padstack; component not found");
      return null;
    }
    int padstackId = component.getPackage().getPin(pinIndex).padstackId;
    return board.library.padstacks.get(padstackId);
  }

  @Override
  public Item copy(int id) {
    int[] currentNetNumbers = new int[this.netCount()];
    for (int i = 0; i < currentNetNumbers.length; i++) {
      currentNetNumbers[i] = getNetNumber(i);
    }
    return new Pin(
        getComponentId(),
        this.pinIndex,
        currentNetNumbers,
        clearanceClassIndex(),
        id,
        getFixedState(),
        board);
  }

  /** Return the name of this pin in the package of this component. */
  public String name() {
    Component component = board.components.get(this.getComponentId());
    if (component == null) {
      FRLogger.warn("Pin.name: component not found");
      return null;
    }
    return component.getPackage().getPin(pinIndex).name;
  }

  /** Gets index of this pin in the library package of the pins component. */
  public int getPinIndex() {
    return pinIndex;
  }

  @Override
  public Shape getShape(int index) {
    Padstack padstack = getPadstack();
    if (this.precalculatedShapes == null) {
      // all shapes have to be calculated  at once, because otherwise calculation
      // of fromLayer and toLayer may not be correct
      this.precalculatedShapes = new Shape[padstack.toLayer() - padstack.fromLayer() + 1];

      Component component = board.components.get(this.getComponentId());
      if (component == null) {
        FRLogger.warn("Pin.get_shape: component not found");
        return null;
      }
      Package libPackage = component.getPackage();
      if (libPackage == null) {
        FRLogger.warn("Pin.get_shape: package not found");
        return null;
      }
      Package.Pin packagePin = libPackage.getPin(this.getPinIndex());
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

        ConvexShape currentShape = padstack.getShape(padstackLayer);
        if (currentShape == null) {
          continue;
        }
        double pinRotation = packagePin.rotationInDegree;
        if (pinRotation % 90 == 0) {
          int pinNinetyDegreeFactor = ((int) pinRotation) / 90;
          if (pinNinetyDegreeFactor != 0) {
            currentShape =
                (ConvexShape) currentShape.turn90Degree(pinNinetyDegreeFactor, Point.ZERO);
          }
        } else {
          currentShape =
              (ConvexShape) currentShape.rotateApprox(Math.toRadians(pinRotation), FloatPoint.ZERO);
        }

        if (mirrorOnYaxis) {
          currentShape = (ConvexShape) currentShape.mirrorVertical(Point.ZERO);
        }

        // translate the shape first relative to the component
        ConvexShape translatedShape = (ConvexShape) currentShape.translateBy(relLocation);

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

  /** Returns the layer of the padstack shape corresponding to the shape with index index. */
  int getPadstackLayer(int index) {
    Padstack padstack = getPadstack();
    Component component = board.components.get(this.getComponentId());
    int padstackLayer;
    if (component.placedOnFront() || padstack.placedAbsolute) {
      padstackLayer = index + this.firstLayer();
    } else {
      padstackLayer = padstack.boardLayerCount() - index - this.firstLayer() - 1;
    }
    return padstackLayer;
  }

  /**
   * Calculates the allowed trace exit directions of the shape of this padstack on layer layer
   * together with the minimal trace line lengths into their directions. Currently implemented only
   * for box shapes, where traces are allowed to exit the pad only on the small sides.
   */
  public Collection<TraceExitRestriction> getTraceExitRestrictions(int layer) {
    Collection<TraceExitRestriction> result = new LinkedList<>();
    int padstackLayer = this.getPadstackLayer(layer - this.firstLayer());
    double padXyFactor = 1.5;
    // setting 1.5 to a higher factor may hinder the shove algorithm of the autorouter between
    // the pins of SMD components, because the channels can get blocked by the shoveFixed stubs.

    Component component = board.components.get(this.getComponentId());
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
    Shape currentShape = this.getShape(layer - this.firstLayer());
    if (!(currentShape instanceof TileShape padShape)) {
      return result;
    }
    double componentRotation = component.getRotationInDegree();
    Point pinCenter = this.getCenter();
    FloatPoint centerApprox = pinCenter.toFloat();

    for (Direction currentPadstackExitDirection : padstackExitDirections) {

      Package libPackage = component.getPackage();
      if (libPackage == null) {
        continue;
      }
      Package.Pin packagePin = libPackage.getPin(this.pinIndex);
      if (packagePin == null) {
        continue;
      }
      double currentRotationInDegree = componentRotation + packagePin.rotationInDegree;
      Direction currentExitDirection;
      if (currentRotationInDegree % 45 == 0) {
        int fortyfiveDegreeFactor = ((int) currentRotationInDegree) / 45;
        currentExitDirection = currentPadstackExitDirection.turn45Degree(fortyfiveDegreeFactor);
      } else {
        double currentAngleInRadian =
            Math.toRadians(currentRotationInDegree) + currentPadstackExitDirection.angleApprox();
        currentExitDirection = Direction.getInstanceApprox(currentAngleInRadian);
      }
      // calculate the minimum line length from the pin center into currentExitDirection
      int intersectingBorderLineNo =
          padShape.intersectingBorderLineNo(pinCenter, currentExitDirection);
      if (intersectingBorderLineNo < 0) {
        FRLogger.warn("Pin.get_trace_exit_restrictions: border line not found");
        continue;
      }
      Line currentExitLine = new Line(pinCenter, currentExitDirection);
      FloatPoint nearestBorderPoint =
          currentExitLine.intersectionApprox(padShape.borderLine(intersectingBorderLineNo));
      TraceExitRestriction currentExitRestriction =
          new TraceExitRestriction(currentExitDirection, centerApprox.distance(nearestBorderPoint));
      result.add(currentExitRestriction);
    }
    return result;
  }

  /** Returns true, if this pin has exit restrictions on some kayer. */
  public boolean hasTraceExitRestrictions() {
    for (int i = this.firstLayer(); i <= this.lastLayer(); i++) {
      Collection<TraceExitRestriction> currentExitRestrictions = getTraceExitRestrictions(i);
      if (!currentExitRestrictions.isEmpty()) {
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
    Component component = this.board.components.get(this.getComponentId());
    if (component == null) {
      return result;
    }
    LogicalPart logicalPart = component.getLogicalPart();
    if (logicalPart == null) {
      return result;
    }
    LogicalPart.PartPin thisPartPin = logicalPart.getPin(this.pinIndex);
    if (thisPartPin == null) {
      return result;
    }
    if (thisPartPin.gatePinSwapCode <= 0) {
      return result;
    }
    // look up all part pins with the same gateName and the same gatePinSwapCode
    for (int i = 0; i < logicalPart.pinCount(); i++) {
      if (i == this.pinIndex) {
        continue;
      }
      LogicalPart.PartPin currentPartPin = logicalPart.getPin(i);
      if (currentPartPin != null
          && currentPartPin.gatePinSwapCode == thisPartPin.gatePinSwapCode
          && currentPartPin.gateName.equals(thisPartPin.gateName)) {
        Pin currentSwappeblePin = this.board.getPin(this.getComponentId(), currentPartPin.pinIndex);
        if (currentSwappeblePin != null) {
          result.add(currentSwappeblePin);
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

  /** Swaps the nets of this pin and other. Returns false on error. */
  public boolean swap(Pin other) {
    if (this.netCount() > 1 || other.netCount() > 1) {
      FRLogger.warn("Pin.swap not yet implemented for pins belonging to more than 1 net ");
      return false;
    }
    int thisNetNo;
    if (this.netCount() > 0) {
      thisNetNo = this.getNetNumber(0);
    } else {
      thisNetNo = 0;
    }
    int otherNetNo;
    if (other.netCount() > 0) {
      otherNetNo = other.getNetNumber(0);
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
    Component component = board.components.get(this.getComponentId());
    if (component != null) {
      result = component.placedOnFront();
    }
    return result;
  }

  /** Returns the smallest width of the pin shape on layer layer. */
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
   * Returns the neckdown half width for traces on layer. The neckdown width is used, when the pin
   * width is smaller than the trace width to enter or leave the pin with a trace.
   */
  public int getTraceNeckdownHalfwidth(int layer) {
    double result = Math.max(0.5 * this.getMinWidth(layer) - 1, 1);
    return (int) result;
  }

  /** Returns the largest width of the pin shape on layer layer. */
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
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("pin") + ": ");
    printer.append(tm.getText("component_2") + " ");
    Component component = board.components.get(this.getComponentId());
    printer.append(component.name, tm.getText("component_info"), component);
    printer.append(", " + tm.getText("pin_2") + " ");
    printer.append(component.getPackage().getPin(this.pinIndex).name);
    printer.append(", " + tm.getText("padstack") + " ");
    Padstack padstack = this.getPadstack();
    printer.append(padstack.name, tm.getText("padstack_info"), padstack);
    printer.append(" " + tm.getText("at") + " ");
    printer.append(this.getCenter().toFloat());
    this.printConnectableItemInfo(printer, locale);
    printer.newline();
  }

  @Override
  public String getHoverInfo(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    Component component = board.components.get(this.getComponentId());
    Padstack padstack = this.getPadstack();
    String componentName = component.name;
    String pinName = component.getPackage().getPin(this.pinIndex).name;
    String padstackName = padstack.name;
    String connInfo = this.getConnectableItemHoverInfo(locale);

    return tm.getText("pin_hover_info", componentName, pinName, padstackName, connInfo);
  }

  /**
   * Calculates the nearest exit restriction direction for changing tracePolyline. tracePolyline is
   * assumed to start at the pin center. Returns null, if there is no matching exit restrictions.
   */
  public Direction calcNearestExitRestrictionDirection(
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
        tracePolyline.lines[latestEntryTuple[0]].intersectionApprox(
            offsetPinShape.borderLine(latestEntryTuple[1]));
    // calculate the nearest legal pin exit point to traceEntryLocationApprox
    double minExitCornerDistance = Double.MAX_VALUE;
    FloatPoint nearestExitCorner = null;
    Direction pinExitDirection = null;
    final double tolerance = 1;
    Point pinCenter = this.getCenter();
    for (Pin.TraceExitRestriction currentExitRestriction : traceExitRestrictions) {
      int currentIntersectingBorderLineNo =
          offsetPinShape.intersectingBorderLineNo(pinCenter, currentExitRestriction.direction);
      Line currentPinExitRay = new Line(pinCenter, currentExitRestriction.direction);
      FloatPoint currentExitCorner =
          currentPinExitRay.intersectionApprox(
              offsetPinShape.borderLine(currentIntersectingBorderLineNo));
      double currentExitCornerDistance = currentExitCorner.distanceSquare(traceEntryLocationApprox);
      boolean newNearestCornerFound = false;
      if (currentExitCornerDistance + tolerance < minExitCornerDistance) {
        newNearestCornerFound = true;
      } else if (currentExitCornerDistance < minExitCornerDistance + tolerance) {
        // the distances are near equal, compare to the previous corners of tracePolyline
        for (int i = 1; i < tracePolyline.cornerCount(); i++) {
          FloatPoint currentTraceCorner = tracePolyline.cornerApprox(i);
          double currentTraceCornerDistance = currentTraceCorner.distanceSquare(currentExitCorner);
          double oldTraceCornerDistance = currentTraceCorner.distanceSquare(nearestExitCorner);
          if (currentTraceCornerDistance + tolerance < oldTraceCornerDistance) {
            newNearestCornerFound = true;
            break;
          } else if (currentTraceCornerDistance > oldTraceCornerDistance + tolerance) {
            break;
          }
        }
      }
      if (newNearestCornerFound) {
        minExitCornerDistance = currentExitCornerDistance;
        pinExitDirection = currentExitRestriction.direction;
        nearestExitCorner = currentExitCorner;
      }
    }
    return pinExitDirection;
  }

  /**
   * Calculates the nearest trace exit point of the pin on layer. Returns null, if the pin has no
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
    for (Pin.TraceExitRestriction currentExitRestriction : traceExitRestrictions) {
      int currentIntersectingBorderLineNo =
          offsetPinShape.intersectingBorderLineNo(pinCenter, currentExitRestriction.direction);
      Line currentPinExitRay = new Line(pinCenter, currentExitRestriction.direction);
      FloatPoint currentExitCorner =
          currentPinExitRay.intersectionApprox(
              offsetPinShape.borderLine(currentIntersectingBorderLineNo));
      double currentExitCornerDistance = currentExitCorner.distanceSquare(fromPoint);
      if (currentExitCornerDistance < minExitCornerDistance) {
        minExitCornerDistance = currentExitCornerDistance;
        nearestExitCorner = currentExitCorner;
      }
    }
    return nearestExitCorner;
  }

  @Override
  public String toString() {
    StringBuilder simpleName = new StringBuilder();

    simpleName.append(this.getClass().getSimpleName().toLowerCase());

    if (pinIndex > 0) {
      simpleName.append(" #");
      simpleName.append(pinIndex);
    }

    if (componentId > 0) {
      simpleName.append(" of component #");
      simpleName.append(componentId);
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
