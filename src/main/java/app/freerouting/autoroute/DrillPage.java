package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

class DrillPage implements ExpandableObject {

  /** The shape of the page. */
  final IntBox shape;

  private final MazeSearchElement[] mazeSearchElements;
  private final RoutingBoard board;

  /** The list of expansion drills on this page. Null, if not yet calculated. */
  private Collection<ExpansionDrill> drills;

  /** The number of the net, for which the drills are calculated. */
  private int netNumber = -1;

  /** Creates a new instance of DrillPage. */
  public DrillPage(IntBox shape, RoutingBoard board) {
    this.shape = shape;
    this.board = board;
    mazeSearchElements = new MazeSearchElement[board.getLayerCount()];
    for (int i = 0; i < mazeSearchElements.length; i++) {
      mazeSearchElements[i] = new MazeSearchElement();
    }
  }

  /**
   * Looks if drillShape contains the center of a drillable Pin on layer. Returns null if no. such
   * Pin was found.
   */
  private static Point calcPinCenterInDrill(TileShape drillShape, int layer, RoutingBoard board) {
    Collection<Item> overlappingItems = board.overlappingItems(drillShape, layer);
    Point result = null;
    for (Item currentItem : overlappingItems) {
      if (currentItem instanceof Pin currentPin) {
        if (currentPin.drillAllowed() && drillShape.containsInside(currentPin.getCenter())) {
          result = currentPin.getCenter();
        }
      }
    }
    return result;
  }

  /** Returns the drills on this page. If attachSmd, drilling to smd pins is allowed. */
  public Collection<ExpansionDrill> getDrills(AutorouteEngine autorouteEngine, boolean attachSmd) {
    if (this.drills == null || autorouteEngine.getNetNumber() != this.netNumber) {
      this.netNumber = autorouteEngine.getNetNumber();
      this.drills = new LinkedList<>();
      ShapeSearchTree searchTree = autorouteEngine.autorouteSearchTree;
      Collection<TreeEntry> overlaps = new LinkedList<>();
      searchTree.overlappingTreeEntries(this.shape, -1, overlaps);
      Collection<TileShape> cutoutShapes = new LinkedList<>();
      // drills on top of existing vias are used in the ripup algorithm
      TileShape prevObstacleShape = IntBox.EMPTY;
      for (TreeEntry currentEntry : overlaps) {
        if (!(currentEntry.object instanceof Item currentItem)) {
          continue;
        }
        if (currentItem.isDrillable(this.netNumber)) {
          continue;
        }
        if (currentItem instanceof Pin pin) {
          if (attachSmd && pin.drillAllowed()) {
            continue;
          }
        }
        TileShape currentObstacleShape =
            currentItem.getTreeShape(searchTree, currentEntry.shapeIndexInObject);
        if (!prevObstacleShape.contains(currentObstacleShape)) {
          // Checked to avoid multiple cutout for example for vias with the same shape on all
          // layers.
          TileShape currentCutoutShape = currentObstacleShape.intersection(this.shape);
          if (currentCutoutShape.dimension() == 2) {
            cutoutShapes.add(currentCutoutShape);
          }
        }
        prevObstacleShape = currentObstacleShape;
      }
      TileShape[] holes = new TileShape[cutoutShapes.size()];
      Iterator<TileShape> it = cutoutShapes.iterator();
      for (int i = 0; i < holes.length; i++) {
        holes[i] = it.next();
      }
      PolylineArea shapeWithHoles = new PolylineArea(this.shape, holes);
      TileShape[] drillShapes = shapeWithHoles.splitToConvex(autorouteEngine.stoppableThread);

      // Use the center points of these drill shapes to try making a via.
      int drillFirstLayer = 0;
      int drillLastLayer = this.board.getLayerCount() - 1;
      for (int i = 0; i < drillShapes.length; i++) {
        TileShape currentDrillShape = drillShapes[i];
        Point currentDrillLocation = null;
        if (attachSmd) {
          currentDrillLocation =
              calcPinCenterInDrill(currentDrillShape, drillFirstLayer, autorouteEngine.board);
          if (currentDrillLocation == null) {
            currentDrillLocation =
                calcPinCenterInDrill(currentDrillShape, drillLastLayer, autorouteEngine.board);
          }
        }
        if (currentDrillLocation == null) {
          currentDrillLocation = currentDrillShape.centreOfGravity().round();
        }
        ExpansionDrill newDrill =
            new ExpansionDrill(
                currentDrillShape, currentDrillLocation, drillFirstLayer, drillLastLayer);
        if (newDrill.calculateExpansionRooms(autorouteEngine)) {
          this.drills.add(newDrill);
        }
      }
    }
    return this.drills;
  }

  @Override
  public TileShape getShape() {
    return this.shape;
  }

  @Override
  public int getDimension() {
    return 2;
  }

  @Override
  public int mazeSearchElementCount() {
    return this.mazeSearchElements.length;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int index) {
    return this.mazeSearchElements[index];
  }

  /** Resets all drills of this page for autorouting the next connection. */
  @Override
  public void reset() {
    if (this.drills != null) {
      for (ExpansionDrill currentDrill : this.drills) {
        currentDrill.reset();
      }
    }
    for (MazeSearchElement currentInfo : mazeSearchElements) {
      currentInfo.reset();
    }
  }

  /**
   * Invalidates the drills of this page so that they are recalculated at the next call of
   * getDrills().
   */
  public void invalidate() {
    this.drills = null;
  }

  /** Emits optional diagnostics for the drills on this page. */
  public void emitDiagnostics(AutorouteDiagnostic.Sink sink, double intensity) {
    if (sink == null || intensity <= 0 || drills == null) {
      return;
    }
    for (ExpansionDrill currentDrill : drills) {
      currentDrill.emitDiagnostic(sink, intensity);
    }
  }

  @Override
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom room) {
    return null;
  }

  @Override
  public int getId() {
    // Stable hash of shape and netNumber
    return 31 * shape.getId() + netNumber;
  }
}
