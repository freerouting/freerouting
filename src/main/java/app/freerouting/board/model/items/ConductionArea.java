package app.freerouting.board.model.items;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** A ObstacleArea, which can be electrically connected to other items. */
public class ConductionArea extends ObstacleArea implements Connectable {

  private static final double PLANE_HATCH_OPACITY = 0.85;

  private boolean isObstacle;
  private boolean isFilled = true;

  public boolean getIsFilled() {
    return this.isFilled;
  }

  /** SetIsFilled. */
  public void setIsFilled(boolean value) {
    this.isFilled = value;
    this.clearDerivedData();
  }

  private transient int cachedBoardRevision = -1;
  private transient app.freerouting.geometry.planar.PlanarArea cachedBoardFillArea;

  /** Creates a new instance of ConductionArea. */
  public ConductionArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int[] netNumbers,
      int clearanceClassIndex,
      int id,
      int groupId,
      String name,
      boolean isObstacle,
      FixedState fixedState,
      BasicBoard board) {
    super(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        netNumbers,
        clearanceClassIndex,
        id,
        groupId,
        name,
        fixedState,
        board);
    this.isObstacle = isObstacle;
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.cachedBoardRevision = -1;
    this.cachedBoardFillArea = null;
  }

  /** Pre-computes detailed plane-fill geometry off the EDT so zoom-in paints stay responsive. */
  public void warmDetailedFillCache() {
    if (!this.isFilled) {
      return;
    }
    int layerIndex = this.getLayer();
    double maxClearanceLookupBoard = 2000.0 * this.board.communication.getResolution(Unit.UM);
    if (this.board.rules != null && this.board.rules.clearanceMatrix != null) {
      double maxMatrixClearance =
          this.board.rules.clearanceMatrix.maxValue(this.clearanceClassIndex(), layerIndex);
      maxClearanceLookupBoard =
          Math.max(
              maxClearanceLookupBoard,
              maxMatrixClearance + 100.0 * this.board.communication.getResolution(Unit.UM));
    }
    ensureDetailedFillCache(maxClearanceLookupBoard, layerIndex);
  }

  /**
   * Returns the detailed fill geometry for this conduction area, computing clearances against all
   * overlapping items if needed.
   */
  public app.freerouting.geometry.planar.PlanarArea getDetailedFillArea() {
    warmDetailedFillCache();
    return cachedBoardFillArea;
  }

  /**
   * Returns the cached detailed fill geometry for renderer-owned painting.
   *
   * <p>The cache remains owned by the conduction-area model because it depends on board revision
   * and clearance geometry. The renderer owns the AWT paint operation that consumes this geometry.
   */
  public app.freerouting.geometry.planar.PlanarArea getDetailedFillArea(
      double maxClearanceLookupBoard, int layerIndex) {
    ensureDetailedFillCache(maxClearanceLookupBoard, layerIndex);
    return cachedBoardFillArea;
  }

  private void ensureDetailedFillCache(double maxClearanceLookupBoard, int layerIndex) {
    boolean boardChanged = this.board.getRevision() != cachedBoardRevision;
    if (cachedBoardFillArea != null && !boardChanged) {
      return;
    }

    app.freerouting.geometry.planar.PlanarArea fillArea =
        app.freerouting.geometry.planar.PlanarArea.fromArea(this.getArea());
    if (fillArea == null) {
      fillArea = app.freerouting.geometry.planar.PlanarArea.empty();
    }
    if (!fillArea.isEmpty()) {
      IntBox bbox = this.boundingBox();
      double spokeWidth = 400.0 * this.board.communication.getResolution(Unit.UM);
      int maxCl = (int) Math.round(maxClearanceLookupBoard);
      IntBox inflatedBbox =
          new IntBox(
              new IntPoint(bbox.ll.x - maxCl, bbox.ll.y - maxCl),
              new IntPoint(bbox.ur.x + maxCl, bbox.ur.y + maxCl));

      java.util.List<app.freerouting.geometry.planar.PlanarArea> foreignClearances =
          new java.util.ArrayList<>();
      java.util.List<app.freerouting.geometry.planar.PlanarArea> sameNetClearances =
          new java.util.ArrayList<>();

      Set<SearchTreeObject> overlaps = this.board.overlappingObjects(inflatedBbox, layerIndex);
      for (SearchTreeObject ob : overlaps) {
        if (!(ob instanceof Item currentItem) || currentItem == this) {
          continue;
        }
        if (!currentItem.sharesLayer(this)) {
          continue;
        }

        if (currentItem instanceof Trace || currentItem instanceof ConductionArea) {
          if (currentItem.sharesNet(this)) {
            continue;
          }
        }

        int clClass1 = this.clearanceClassIndex();
        int clClass2 = currentItem.clearanceClassIndex();
        double clearanceDist = this.board.clearanceValue(clClass1, clClass2, layerIndex);

        if (currentItem.sharesNet(this)) {
          if (currentItem instanceof DrillItem drillItem) {
            FloatPoint center = drillItem.getCenter().toFloat();
            Shape shape = drillItem.getShapeOnLayer(layerIndex);
            if (shape == null) {
              continue;
            }

            Shape enlargedShape = shape.enlarge(clearanceDist);
            app.freerouting.geometry.planar.PlanarArea clearanceArea =
                app.freerouting.geometry.planar.PlanarArea.fromShape(enlargedShape);
            if (clearanceArea == null || clearanceArea.isEmpty()) {
              continue;
            }

            IntBox itemBbox = drillItem.boundingBox();
            double expansionRadiusBoard =
                Math.hypot(itemBbox.width() / 2.0, itemBbox.height() / 2.0)
                    + clearanceDist
                    + spokeWidth;
            double spokeLength = 2 * expansionRadiusBoard;

            app.freerouting.geometry.planar.PlanarArea spoke1 =
                app.freerouting.geometry.planar.PlanarArea.createRotatedSpoke(
                    center.x, center.y, spokeWidth, spokeLength, Math.PI / 4.0);
            app.freerouting.geometry.planar.PlanarArea spoke2 =
                app.freerouting.geometry.planar.PlanarArea.createRotatedSpoke(
                    center.x, center.y, spokeWidth, spokeLength, -Math.PI / 4.0);
            app.freerouting.geometry.planar.PlanarArea spokes = spoke1.union(spoke2);

            app.freerouting.geometry.planar.PlanarArea thermalCutout =
                clearanceArea.difference(spokes);
            if (!thermalCutout.isEmpty()) {
              sameNetClearances.add(thermalCutout);
            }
          }
        } else {
          if (currentItem instanceof DrillItem drillItem) {
            Shape shape = drillItem.getShapeOnLayer(layerIndex);
            if (shape != null) {
              Shape enlargedShape = shape.enlarge(clearanceDist);
              app.freerouting.geometry.planar.PlanarArea clearanceArea =
                  app.freerouting.geometry.planar.PlanarArea.fromShape(enlargedShape);
              if (clearanceArea != null && !clearanceArea.isEmpty()) {
                foreignClearances.add(clearanceArea);
              }
            }
          } else {
            int shapeCount = currentItem.tileShapeCount();
            for (int i = 0; i < shapeCount; i++) {
              if (currentItem.shapeLayer(i) == layerIndex) {
                TileShape tileShape = currentItem.getTileShape(i);
                if (tileShape != null) {
                  Shape enlargedShape = tileShape.enlarge(clearanceDist);
                  app.freerouting.geometry.planar.PlanarArea clearanceArea =
                      app.freerouting.geometry.planar.PlanarArea.fromShape(enlargedShape);
                  if (clearanceArea != null && !clearanceArea.isEmpty()) {
                    foreignClearances.add(clearanceArea);
                  }
                }
              }
            }
          }
        }
      }

      fillArea = fillArea.subtractAll(foreignClearances);
      fillArea = fillArea.subtractAll(sameNetClearances);
    }
    cachedBoardFillArea = fillArea;
    cachedBoardRevision = this.board.getRevision();
  }

  @Override
  public Item copy(int id) {
    return new ConductionArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        netNumbers,
        clearanceClassIndex(),
        id,
        getComponentId(),
        this.name,
        isObstacle,
        getFixedState(),
        board);
  }

  /**
   * Whether this area is dead copper: a piece of a fill smaller than 1 mm\u00b2 (the threshold of
   * {@code DesignRulesChecker.getZoneIslandViolations}), on a layer without traces, that touches
   * nothing of its net. A zone with no connection yet keeps all its islands when it is filled, so
   * an unrouted board can carry such pieces between the vias of another net: no via fits there, and
   * they are no incomplete connection.
   */
  public boolean isDeadCopper() {
    if (board == null || board.layerStructure.layers[getLayer()].isSignal) {
      return false;
    }
    double mm = board.communication.getResolution(Unit.MM);
    IntBox box = getArea().boundingBox();
    if ((double) box.width() * box.height() >= mm * mm) {
      return false;
    }
    return getNormalContacts().isEmpty();
  }

  @Override
  public Set<Item> getNormalContacts() {
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();
    // A plane spans most of its layer, often in thousands of tiles: rather than one tree query per
    // tile, look up the tiles that contain the ends of the items of the net.
    Set<SearchTreeObject> overlaps =
        defaultTree.overlappingObjectsWithPointIn(this, getLayer(), this::contactPoints);
    Set<Item> result = new TreeSet<>();
    if (overlaps == null) {
      // Not in the tree: query tile by tile.
      for (int i = 0; i < tileShapeCount(); i++) {
        TileShape tile = getTileShape(i);
        for (SearchTreeObject currentObject : board.overlappingObjects(tile, getLayer())) {
          if (isContactInTile(currentObject, tile)) {
            result.add((Item) currentObject);
          }
        }
      }
      return result;
    }
    for (SearchTreeObject currentObject : overlaps) {
      result.add((Item) currentObject);
    }
    return result;
  }

  /**
   * The points of object of which one must lie in a tile of this area for a contact, or null if
   * object cannot be a contact.
   */
  private Point[] contactPoints(SearchTreeObject object) {
    if (!(object instanceof Item item)
        || item == this
        || !item.sharesNet(this)
        || !item.sharesLayer(this)) {
      return null;
    }
    if (item instanceof Trace trace) {
      return new Point[] {trace.firstCorner(), trace.lastCorner()};
    }
    return item instanceof DrillItem drillItem ? new Point[] {drillItem.getCenter()} : null;
  }

  private boolean isContactInTile(SearchTreeObject object, TileShape tile) {
    if (!(object instanceof Item item)
        || item == this
        || !item.sharesNet(this)
        || !item.sharesLayer(this)) {
      return false;
    }
    if (item instanceof Trace trace) {
      return tile.contains(trace.firstCorner()) || tile.contains(trace.lastCorner());
    }
    return item instanceof DrillItem drillItem && tile.contains(drillItem.getCenter());
  }

  @Override
  public TileShape getTraceConnectionShape(ShapeSearchTree searchTree, int index) {
    if (index < 0 || index >= this.treeShapeCount(searchTree)) {
      FRLogger.warn("ConductionArea.get_trace_connection_shape index out of range");
      return null;
    }
    return this.getTreeShape(searchTree, index);
  }

  @Override
  public Point[] getRatsnestCorners() {
    Point[] result;
    FloatPoint[] corners = this.getArea().cornerApproxArr();
    result = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      result[i] = corners[i].round();
    }

    return result;
  }

  @Override
  public boolean isObstacle(Item other) {
    if (this.isObstacle) {
      return super.isObstacle(other);
    }
    return false;
  }

  /** Returns if this conduction area is regarded as obstacle to traces of foreign nets. */
  public boolean getIsObstacle() {
    return this.isObstacle;
  }

  /** Sets, if this conduction area is regarded as obstacle to traces and vias of foreign nets. */
  public void setIsObstacle(boolean value) {
    this.isObstacle = value;
  }

  @Override
  public boolean isTraceObstacle(int netNumber) {
    return this.isObstacle && !this.containsNet(netNumber);
  }

  @Override
  public boolean isDrillable(int netNumber) {
    return !this.isObstacle || this.containsNet(netNumber);
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.CONDUCTION);
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("conductionArea"));
    this.printShapeInfo(printer, locale);
    this.printConnectableItemInfo(printer, locale);
    printer.newline();
  }
}
