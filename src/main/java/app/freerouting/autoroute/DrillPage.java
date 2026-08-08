package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.TileShape;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

class DrillPage implements ExpandableObject {

  /** The shape of the page */
  final IntBox shape;

  private final MazeSearchElement[] mazeSearchInfoArr;
  private final RoutingBoard board;

  /** The list of expansion drills on this page. Null, if not yet calculated. */
  private Collection<ExpansionDrill> drills;

  /** The number of the net, for which the drills are calculated */
  private int netNo = -1;

  /** Creates a new instance of DrillPage */
  public DrillPage(IntBox pShape, RoutingBoard pBoard) {
    shape = pShape;
    board = pBoard;
    mazeSearchInfoArr = new MazeSearchElement[pBoard.getLayerCount()];
    for (int i = 0; i < mazeSearchInfoArr.length; i++) {
      mazeSearchInfoArr[i] = new MazeSearchElement();
    }
  }

  /**
   * Looks if p_drill_shape contains the center of a drillable Pin on p_layer. Returns null, if no
   * such Pin was found.
   */
  private static Point calcPinCenterInDrill(
      TileShape pDrillShape, int pLayer, RoutingBoard pBoard) {
    Collection<Item> overlappingItems = pBoard.overlappingItems(pDrillShape, pLayer);
    Point result = null;
    for (Item currItem : overlappingItems) {
      if (currItem instanceof Pin currPin) {
        if (currPin.drillAllowed() && pDrillShape.containsInside(currPin.getCenter())) {
          result = currPin.getCenter();
        }
      }
    }
    return result;
  }

  /** Returns the drills on this page. If p_attach_smd, drilling to smd pins is allowed. */
  public Collection<ExpansionDrill> getDrills(
      AutorouteEngine pAutorouteEngine, boolean pAttachSmd) {
    if (this.drills == null || pAutorouteEngine.getNetNo() != this.netNo) {
      this.netNo = pAutorouteEngine.getNetNo();
      this.drills = new LinkedList<>();
      ShapeSearchTree searchTree = pAutorouteEngine.autorouteSearchTree;
      Collection<TreeEntry> overlaps = new LinkedList<>();
      searchTree.overlappingTreeEntries(this.shape, -1, overlaps);
      Collection<TileShape> cutoutShapes = new LinkedList<>();
      // drills on top of existing vias are used in the ripup algorithm
      TileShape prevObstacleShape = IntBox.EMPTY;
      for (TreeEntry currentEntry : overlaps) {
        if (!(currentEntry.object instanceof Item currItem)) {
          continue;
        }
        if (currItem.isDrillable(this.netNo)) {
          continue;
        }
        if (currItem instanceof Pin pin) {
          if (pAttachSmd && pin.drillAllowed()) {
            continue;
          }
        }
        TileShape currObstacleShape =
            currItem.getTreeShape(searchTree, currentEntry.shapeIndexInObject);
        if (!prevObstacleShape.contains(currObstacleShape)) {
          // Checked to avoid multiple cutout for example for vias with the same shape on all
          // layers.
          TileShape currCutoutShape = currObstacleShape.intersection(this.shape);
          if (currCutoutShape.dimension() == 2) {
            cutoutShapes.add(currCutoutShape);
          }
        }
        prevObstacleShape = currObstacleShape;
      }
      TileShape[] holes = new TileShape[cutoutShapes.size()];
      Iterator<TileShape> it = cutoutShapes.iterator();
      for (int i = 0; i < holes.length; i++) {
        holes[i] = it.next();
      }
      PolylineArea shapeWithHoles = new PolylineArea(this.shape, holes);
      TileShape[] drillShapes = shapeWithHoles.splitToConvex(pAutorouteEngine.stoppableThread);

      // Use the center points of these drill shapes to try making a via.
      int drillFirstLayer = 0;
      int drillLastLayer = this.board.getLayerCount() - 1;
      for (int i = 0; i < drillShapes.length; i++) {
        TileShape currDrillShape = drillShapes[i];
        Point currDrillLocation = null;
        if (pAttachSmd) {
          currDrillLocation =
              calcPinCenterInDrill(currDrillShape, drillFirstLayer, pAutorouteEngine.board);
          if (currDrillLocation == null) {
            currDrillLocation =
                calcPinCenterInDrill(currDrillShape, drillLastLayer, pAutorouteEngine.board);
          }
        }
        if (currDrillLocation == null) {
          currDrillLocation = currDrillShape.centreOfGravity().round();
        }
        ExpansionDrill newDrill =
            new ExpansionDrill(currDrillShape, currDrillLocation, drillFirstLayer, drillLastLayer);
        if (newDrill.calculateExpansionRooms(pAutorouteEngine)) {
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
    return this.mazeSearchInfoArr.length;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int pNo) {
    return this.mazeSearchInfoArr[pNo];
  }

  /** Resets all drills of this page for autorouting the next connection. */
  @Override
  public void reset() {
    if (this.drills != null) {
      for (ExpansionDrill currDrill : this.drills) {
        currDrill.reset();
      }
    }
    for (MazeSearchElement currInfo : mazeSearchInfoArr) {
      currInfo.reset();
    }
  }

  /**
   * Invalidates the drills of this page so that they are recalculated at the next call of
   * get_drills().
   */
  public void invalidate() {
    this.drills = null;
  }

  /*
   * Test draw of the drills on this page.
   */
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext, double pIntensity) {
    if (true) {
      return;
    }
    for (ExpansionDrill currDrill : drills) {
      currDrill.draw(pGraphics, pGraphicsContext, pIntensity);
    }
  }

  @Override
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom pRoom) {
    return null;
  }

  @Override
  public int getIdNo() {
    // Stable hash of shape and netNo
    return 31 * shape.getIdNo() + netNo;
  }
}
