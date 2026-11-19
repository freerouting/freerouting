package app.freerouting.board.model.items;

import app.freerouting.autoroute.plane.FastPlaneConnectivityGrid;
import app.freerouting.autoroute.plane.PlaneOutlineSmoothing;
import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** A ObstacleArea, which can be electrically connected to other items. */
public class ConductionArea extends ObstacleArea implements Connectable {

  private static final long serialVersionUID = 9006217382369120553L;

  private static final double PLANE_HATCH_OPACITY = 0.85;

  private boolean isObstacle;
  private boolean isFilled = true;

  /** The clearance the fill of the zone keeps to foreign copper, in board units; 0 if unknown. */
  private int fillClearance;

  /** The narrowest neck the fill of the zone keeps, in board units; 0 if unknown. */
  private int fillMinWidth;

  /** How the fill connects the pads of the net; null if unknown. */
  private PadPlaneConnection.Mode padConnection;

  /** The gap of the thermal reliefs of the fill, in board units. */
  private int thermalGap;

  /** The width of the spokes of the thermal reliefs of the fill, in board units. */
  private int thermalSpokeWidth;

  /** The distance the fill keeps to the edge of the board, in board units; 0 if unknown. */
  private int fillEdgeClearance;

  /** Whether the corners of the outline are rounded before the fill (fillets). */
  private boolean cornerFillets;

  /**
   * The radius of the fillets, or the distance of the chamfers, of the corners of the outline
   * before the fill, in board units; 0 for sharp corners.
   */
  private int cornerSmoothing;

  /** The outline with its corners smoothed, built on first use. */
  private transient Area smoothedArea;

  /**
   * Sets how the host CAD system fills this area, when the design file does not say it: the
   * clearance the fill keeps to the copper of other nets, if larger than the clearance of the
   * clearance classes, and the minimum width below which a neck of the fill is removed. Both are in
   * board units; 0 means unknown. They only change where this area counts as fractured.
   */
  public void setFillRules(int clearance, int minWidth) {
    this.fillClearance = Math.max(0, clearance);
    this.fillMinWidth = Math.max(0, minWidth);
    this.clearDerivedData();
  }

  /** The clearance set by {@link #setFillRules(int, int)}, in board units; 0 if unknown. */
  public int getFillClearance() {
    return this.fillClearance;
  }

  /** The minimum width set by {@link #setFillRules(int, int)}, in board units; 0 if unknown. */
  public int getFillMinWidth() {
    return this.fillMinWidth;
  }

  /**
   * Sets how the host CAD system connects the pads of the net to the fill of this area, when the
   * design file does not say it: the kind of connection, and for thermal reliefs their gap and the
   * width of their spokes, in board units. A pad can override them ({@link
   * Pin#setPlaneConnection}). They only change on which island of this area a pad counts.
   */
  public void setPadConnectionRules(
      PadPlaneConnection.Mode padConnection, int thermalGap, int thermalSpokeWidth) {
    this.padConnection = padConnection;
    this.thermalGap = Math.max(0, thermalGap);
    this.thermalSpokeWidth = Math.max(0, thermalSpokeWidth);
    this.clearDerivedData();
  }

  /**
   * Sets the distance the fill of the host CAD system keeps to the edge of the board, in board
   * units, when it is not the clearance of the fill to other copper.
   */
  public void setFillEdgeClearance(int edgeClearance) {
    this.fillEdgeClearance = Math.max(0, edgeClearance);
    this.clearDerivedData();
  }

  /** The distance set by {@link #setFillEdgeClearance}, in board units; 0 if unknown. */
  public int getFillEdgeClearance() {
    return this.fillEdgeClearance;
  }

  /**
   * Sets how the host CAD system smooths the corners of the outline before it fills it: fillets of
   * this radius, or chamfers at this distance from each corner, in board units; 0 for sharp
   * corners.
   */
  public void setFillCornerSmoothing(boolean fillets, int distance) {
    this.cornerFillets = fillets;
    this.cornerSmoothing = Math.max(0, distance);
    this.smoothedArea = null;
    this.clearDerivedData();
  }

  /** Whether the corners are rounded, by {@link #setFillCornerSmoothing}. */
  public boolean hasCornerFillets() {
    return this.cornerFillets;
  }

  /** The distance set by {@link #setFillCornerSmoothing}, in board units; 0 for sharp corners. */
  public int getCornerSmoothing() {
    return this.cornerSmoothing;
  }

  /**
   * The outline that the fill of the host CAD system follows: the area with its corners smoothed as
   * {@link #setFillCornerSmoothing} says, or the area itself.
   */
  public Area getFillOutline() {
    Area area = getArea();
    if (this.cornerSmoothing <= 0 || area == null) {
      return area;
    }
    if (this.smoothedArea == null) {
      this.smoothedArea =
          PlaneOutlineSmoothing.smooth(area, this.cornerFillets, this.cornerSmoothing);
    }
    return this.smoothedArea;
  }

  /** The connection set by {@link #setPadConnectionRules}; null if unknown. */
  public PadPlaneConnection.Mode getPadConnection() {
    return this.padConnection;
  }

  /** The thermal gap set by {@link #setPadConnectionRules}, in board units. */
  public int getThermalGap() {
    return this.thermalGap;
  }

  /** The spoke width set by {@link #setPadConnectionRules}, in board units. */
  public int getThermalSpokeWidth() {
    return this.thermalSpokeWidth;
  }

  /**
   * The distance from the copper of a foreign item within which the fill of this area carries no
   * current: the larger of {@code clearance} (the value of the clearance classes) and the clearance
   * of the fill, plus half the minimum width of the fill. Two obstacles closer than twice this
   * distance leave a neck narrower than the minimum width, which the fill does not keep.
   */
  public double fillKeepout(double clearance) {
    return Math.max(clearance, this.fillClearance) + this.fillMinWidth / 2.0;
  }

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
  private transient int cachedFractureRevision = -1;
  private transient long cachedNetAssignmentStamp = -1;
  private transient FastPlaneConnectivityGrid.PlaneFractureResult cachedFractureResult;

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
    this.cachedFractureRevision = -1;
    this.cachedNetAssignmentStamp = -1;
    this.cachedFractureResult = null;
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
    ConductionArea result =
        new ConductionArea(
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
    result.fillClearance = this.fillClearance;
    result.fillMinWidth = this.fillMinWidth;
    result.padConnection = this.padConnection;
    result.thermalGap = this.thermalGap;
    result.thermalSpokeWidth = this.thermalSpokeWidth;
    result.fillEdgeClearance = this.fillEdgeClearance;
    result.cornerFillets = this.cornerFillets;
    result.cornerSmoothing = this.cornerSmoothing;
    return result;
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
    // tile, look up the tiles that contain the ends of the traces and the centres of the vias, and
    // that overlap the pins of the net.
    Set<SearchTreeObject> overlaps =
        defaultTree.overlappingObjectsWithPointIn(
            this, getLayer(), this::contactPoints, this::isPinCandidate);
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
   * object cannot be a contact or is a pin, looked up by its shape.
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
    return item instanceof Via via ? new Point[] {via.getCenter()} : null;
  }

  /**
   * Whether object is a pin of the net of this area, which is a contact when its copper overlaps a
   * tile. Its centre need not be in the area: a thermal relief of a filled zone leaves the centre
   * of the pad in a hole, the spokes covering only its edge. A via, placed by the router with its
   * centre in the area, keeps that rule.
   */
  private boolean isPinCandidate(SearchTreeObject object) {
    return object instanceof Pin pin && pin.sharesNet(this) && pin.sharesLayer(this);
  }

  private boolean isContactInTile(SearchTreeObject object, TileShape tile) {
    if (isPinCandidate(object)) {
      return overlapsTile((Pin) object, tile);
    }
    Point[] points = contactPoints(object);
    if (points != null) {
      for (Point point : points) {
        if (tile.contains(point)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Whether the copper of drillItem on the layer of this area overlaps tile with a positive area.
   */
  private boolean overlapsTile(DrillItem drillItem, TileShape tile) {
    TileShape drillShape =
        drillItem.getTreeShapeOnLayer(board.searchTreeManager.getDefaultTree(), getLayer());
    return drillShape != null && tile.intersection(drillShape).dimension() == 2;
  }

  /**
   * Whether drillItem, a pin of the net of this area, overlaps a tile of this area with a positive
   * area: the counterpart of {@link #getNormalContacts()} for {@link
   * DrillItem#getNormalContacts()}.
   */
  boolean overlapsCopperOf(DrillItem drillItem) {
    if (!isPinCandidate(drillItem)) {
      return false;
    }
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();
    TileShape drillShape = drillItem.getTreeShapeOnLayer(defaultTree, getLayer());
    if (drillShape == null) {
      return false;
    }
    if (getSearchTreeEntries(defaultTree) == null) {
      for (int i = 0; i < tileShapeCount(); i++) {
        if (getTileShape(i).intersection(drillShape).dimension() == 2) {
          return true;
        }
      }
      return false;
    }
    Collection<TreeEntry> entries = new ArrayList<>();
    defaultTree.overlappingTreeEntries(drillShape, getLayer(), entries);
    for (TreeEntry entry : entries) {
      if (entry.object == this
          && getTreeShape(defaultTree, entry.shapeIndexInObject)
                  .intersection(drillShape)
                  .dimension()
              == 2) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the connected set reaches an unfractured plane of the net, and no other
   * unfractured plane of the net that carries a pin is left in the unconnected set. A net can have
   * several disjoint planes (for example two zones of the same net on one layer): being on one of
   * them does not connect an item to the pads of the others, so the connection must still be
   * routed. A plane piece that no pin of the net reaches (a floating fill fragment) does not need a
   * connection: routing to it would only add an antenna, and a connected set without a pin that
   * reaches such a fragment is not routed either.
   *
   * @param connectedSet the connected set of the item to route
   * @param unconnectedSet the items of the same net outside {@code connectedSet}
   * @param netNo the net of the connection
   * @return true if the connection can be left to the planes the item already touches
   */
  public static boolean coversAllPlanesOfNet(
      Collection<Item> connectedSet, Collection<Item> unconnectedSet, int netNo) {
    boolean onPlane = false;
    boolean hasPin = false;
    for (Item item : connectedSet) {
      if (item instanceof ConductionArea ca && !ca.isFractured()) {
        onPlane = true;
      } else if (item instanceof Pin) {
        hasPin = true;
      }
    }
    if (!onPlane) {
      return false;
    }
    if (!hasPin) {
      // a floating fragment, or copper that only hangs on one: nothing needs it
      return true;
    }
    for (Item item : unconnectedSet) {
      if (item instanceof ConductionArea ca && !ca.isFloatingFragment(netNo)) {
        return false;
      }
    }
    for (Item item : connectedSet) {
      if (item instanceof ConductionArea ca && ca.hasUnreachedPinIsland(connectedSet)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if no pin of the net is reached from this conduction area: a fill piece that the
   * net needs no connection to (see {@link #coversAllPlanesOfNet}).
   */
  public boolean isFloatingFragment(int netNo) {
    if (isFractured()) {
      return !hasPinOnAnyIsland(netNo);
    }
    return !reachesPinOfNet(netNo);
  }

  /** Returns true if any island of this fractured conduction area contains a pin. */
  public boolean hasPinOnAnyIsland() {
    return hasPinOnAnyIsland(0);
  }

  /**
   * Returns true if any island of this fractured conduction area contains a pin belonging to {@code
   * netNo} (or any pin when {@code netNo <= 0}).
   */
  public boolean hasPinOnAnyIsland(int netNo) {
    FastPlaneConnectivityGrid.PlaneFractureResult res = getFractureResult();
    if (res == null || !res.isFractured) {
      return false;
    }
    for (List<Item> island : res.islandItems.values()) {
      for (Item item : island) {
        if (item instanceof Pin && (netNo <= 0 || item.containsNet(netNo))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns true if this fractured conduction area has an island containing a pin that is not in
   * {@code connectedSet}.
   */
  public boolean hasUnreachedPinIsland(Collection<Item> connectedSet) {
    FastPlaneConnectivityGrid.PlaneFractureResult res = getFractureResult();
    if (res == null || !res.isFractured) {
      return false;
    }
    for (List<Item> island : res.islandItems.values()) {
      for (Item item : island) {
        if (item instanceof Pin && !connectedSet.contains(item)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns true if a pin of the net can be reached from this conduction area through items of the
   * net. A plane piece without such a pin is a floating fragment.
   */
  public boolean reachesPinOfNet(int netNo) {
    for (Item item : getConnectedSet(netNo)) {
      if (item instanceof Pin) {
        return true;
      }
    }
    return false;
  }

  /**
   * Collects one representative {@link Pin} from each island of this fractured conduction area that
   * contains a pin.
   */
  public void collectPinsOnFracturedIslands(Collection<Pin> out) {
    FastPlaneConnectivityGrid.PlaneFractureResult res = getFractureResult();
    if (res == null || !res.isFractured) {
      return;
    }
    for (List<Item> island : res.islandItems.values()) {
      for (Item item : island) {
        if (item instanceof Pin pin) {
          out.add(pin);
          break;
        }
      }
    }
  }

  /**
   * Returns true if this conduction area is split into multiple isolated copper islands by
   * obstacles.
   */
  public boolean isFractured() {
    FastPlaneConnectivityGrid.PlaneFractureResult res = getFractureResult();
    return res != null && res.isFractured;
  }

  /** Returns the cached or freshly computed fracture analysis result for this conduction area. */
  public FastPlaneConnectivityGrid.PlaneFractureResult getFractureResult() {
    if (this.board == null) {
      return null;
    }
    int currentRevision = this.board.getRevision();
    long currentNetAssignmentStamp = this.board.getNetAssignmentChangeCount();
    if (this.cachedFractureResult != null
        && this.cachedNetAssignmentStamp == currentNetAssignmentStamp) {
      if (this.cachedFractureRevision == currentRevision) {
        return this.cachedFractureResult;
      }
      // the board changed, but nowhere the result depends on: it still holds
      IntBox dependencyBox = this.cachedFractureResult.dependencyBox;
      if (dependencyBox != null
          && !this.board.changedSince(this.cachedFractureRevision, getLayer(), dependencyBox)) {
        this.cachedFractureRevision = currentRevision;
        return this.cachedFractureResult;
      }
    }
    FastPlaneConnectivityGrid connectivityGrid = new FastPlaneConnectivityGrid();
    Collection<Item> contacts = getNormalContacts();
    this.cachedFractureResult = connectivityGrid.evaluateFracture(this, this.board, contacts);
    this.cachedFractureRevision = currentRevision;
    this.cachedNetAssignmentStamp = currentNetAssignmentStamp;
    return this.cachedFractureResult;
  }

  @Override
  void getConnectedSetRecu(Set<Item> result, int netNumber, boolean stopAtPlane) {
    if (this.board == null) {
      super.getConnectedSetRecu(result, netNumber, stopAtPlane);
      return;
    }

    FastPlaneConnectivityGrid.PlaneFractureResult fractureResult = getFractureResult();
    if (fractureResult == null || !fractureResult.isFractured) {
      super.getConnectedSetRecu(result, netNumber, stopAtPlane);
      return;
    }

    // Determine which islands have already been contacted by items in result. A trace whose ends
    // touch two islands is in both.
    Set<Integer> reachedIslands = new HashSet<>();
    for (Item resItem : result) {
      if (resItem != this && resItem.sharesNet(this) && resItem.sharesLayer(this)) {
        for (Map.Entry<Integer, List<Item>> entry : fractureResult.islandItems.entrySet()) {
          if (entry.getValue().contains(resItem)) {
            reachedIslands.add(entry.getKey());
          }
        }
      }
    }

    if (reachedIslands.isEmpty()) {
      // If this conduction area itself is the start item, only visit island 0 so severed islands
      // are not falsely bridged
      reachedIslands.add(0);
    }

    for (int islandId : reachedIslands) {
      List<Item> reachableItems = fractureResult.islandItems.get(islandId);
      if (reachableItems != null) {
        for (Item item : reachableItems) {
          if (item != this && (netNumber <= 0 || item.containsNet(netNumber))) {
            if (result.add(item)) {
              item.getConnectedSetRecu(result, netNumber, stopAtPlane);
            }
          }
        }
      }
    }
  }

  /**
   * Returns true if the first or last corner of a trace of the net touches the fill of this area:
   * false for an end in the clearance band of foreign copper, or in the relief of a pad.
   */
  public boolean touchesEndOf(Trace trace, boolean first) {
    FastPlaneConnectivityGrid.PlaneFractureResult fractureResult = getFractureResult();
    if (fractureResult == null) {
      return true;
    }
    int[] islands = fractureResult.endIslands(trace, first);
    return islands == null || islands.length > 0;
  }

  /**
   * Returns true if this area joins the two ends of a trace of its net, so that the trace closes a
   * cycle with it. An area in one piece joins any two points of it. A fractured area joins them
   * only if they touch a common island: a trace that bridges a cut of the area is no cycle.
   */
  public boolean joinsEndsOf(Trace trace) {
    FastPlaneConnectivityGrid.PlaneFractureResult fractureResult = getFractureResult();
    if (fractureResult == null || !fractureResult.isFractured) {
      return true;
    }
    int[] first = fractureResult.endIslands(trace, true);
    int[] last = fractureResult.endIslands(trace, false);
    if (first == null || last == null) {
      return true;
    }
    for (int a : first) {
      for (int b : last) {
        if (a == b) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Completes a connected set that passes through fractured conduction areas. Such an area opens
   * only the islands reached when the walk first meets it; an island reached later, through a via
   * and another layer, stays closed, so the result depends on the walk order. This adds, until
   * nothing changes, the items of every island of a fractured area in {@code result} that holds an
   * item of {@code result}.
   */
  static void addLaterReachedIslands(Set<Item> result, int netNumber, boolean stopAtPlane) {
    boolean changed = true;
    while (changed) {
      changed = false;
      List<ConductionArea> fractured = new ArrayList<>();
      for (Item item : result) {
        if (item instanceof ConductionArea ca && ca.board != null && ca.isFractured()) {
          fractured.add(ca);
        }
      }
      for (ConductionArea ca : fractured) {
        for (List<Item> island : ca.getFractureResult().islandItems.values()) {
          boolean reached = false;
          for (Item item : island) {
            if (item != ca && result.contains(item)) {
              reached = true;
              break;
            }
          }
          if (!reached) {
            continue;
          }
          for (Item item : island) {
            if (item != ca && (netNumber <= 0 || item.containsNet(netNumber)) && result.add(item)) {
              item.getConnectedSetRecu(result, netNumber, stopAtPlane);
              changed = true;
            }
          }
        }
      }
    }
  }

  @Override
  boolean isCycleRecu(
      Set<Item> visitedItems, Item searchItem, Item comeFromItem, boolean ignoreAreas) {
    if (ignoreAreas) {
      return false;
    }
    if (this.board == null) {
      return super.isCycleRecu(visitedItems, searchItem, comeFromItem, ignoreAreas);
    }
    FastPlaneConnectivityGrid.PlaneFractureResult fractureResult = getFractureResult();
    if (fractureResult == null || !fractureResult.isFractured) {
      return super.isCycleRecu(visitedItems, searchItem, comeFromItem, ignoreAreas);
    }

    // The islands the walk enters from comeFromItem. The walk for a cycle through a trace starts
    // at its first corner: from the trace itself, only the islands of that end are entered, and the
    // trace closes a cycle if its last corner touches one of them.
    Set<Integer> fromIslands = null;
    if (comeFromItem != null) {
      int[] startIslands =
          comeFromItem == searchItem ? fractureResult.endIslands(comeFromItem, true) : null;
      if (startIslands != null) {
        fromIslands = new HashSet<>();
        for (int island : startIslands) {
          fromIslands.add(island);
        }
        for (int island : fractureResult.endIslands(comeFromItem, false)) {
          if (fromIslands.contains(island)) {
            return true;
          }
        }
      } else {
        for (Map.Entry<Integer, List<Item>> entry : fractureResult.islandItems.entrySet()) {
          if (entry.getValue().contains(comeFromItem)) {
            if (fromIslands == null) {
              fromIslands = new HashSet<>();
            }
            fromIslands.add(entry.getKey());
          }
        }
        if (fromIslands == null && fractureResult.traceEndIslands.containsKey(comeFromItem)) {
          // a trace whose ends touch no fill of this area
          return false;
        }
      }
    }

    Collection<Item> contactList = getNormalContacts();
    if (contactList == null) {
      return false;
    }
    for (Item currentContact : contactList) {
      if (currentContact == comeFromItem) {
        continue;
      }
      if (fromIslands != null) {
        boolean onSameIsland = false;
        for (int island : fromIslands) {
          List<Item> islandMembers = fractureResult.islandItems.get(island);
          if (islandMembers != null && islandMembers.contains(currentContact)) {
            onSameIsland = true;
            break;
          }
        }
        if (!onSameIsland) {
          continue;
        }
      }
      if (currentContact == searchItem) {
        return true;
      }
      if (visitedItems.add(currentContact)) {
        if (currentContact.isCycleRecu(visitedItems, searchItem, this, ignoreAreas)) {
          return true;
        }
      }
    }
    return false;
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
    if (this.isObstacle != value) {
      this.isObstacle = value;
      this.clearDerivedData();
    }
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
