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
  public DrillPage(IntBox p_shape, RoutingBoard p_board) {
    shape = p_shape;
    board = p_board;
    mazeSearchInfoArr = new MazeSearchElement[p_board.get_layer_count()];
    for (int i = 0; i < mazeSearchInfoArr.length; i++) {
      mazeSearchInfoArr[i] = new MazeSearchElement();
    }
  }

  /**
   * Looks if p_drill_shape contains the center of a drillable Pin on p_layer. Returns null, if no
   * such Pin was found.
   */
  private static Point calc_pin_center_in_drill(
      TileShape p_drill_shape, int p_layer, RoutingBoard p_board) {
    Collection<Item> overlappingItems = p_board.overlapping_items(p_drill_shape, p_layer);
    Point result = null;
    for (Item currItem : overlappingItems) {
      if (currItem instanceof Pin currPin) {
        if (currPin.drill_allowed() && p_drill_shape.contains_inside(currPin.get_center())) {
          result = currPin.get_center();
        }
      }
    }
    return result;
  }

  /** Returns the drills on this page. If p_attach_smd, drilling to smd pins is allowed. */
  public Collection<ExpansionDrill> get_drills(
      AutorouteEngine p_autoroute_engine, boolean p_attach_smd) {
    if (this.drills == null || p_autoroute_engine.get_net_no() != this.netNo) {
      this.netNo = p_autoroute_engine.get_net_no();
      this.drills = new LinkedList<>();
      ShapeSearchTree searchTree = p_autoroute_engine.autorouteSearchTree;
      Collection<TreeEntry> overlaps = new LinkedList<>();
      searchTree.overlapping_tree_entries(this.shape, -1, overlaps);
      Collection<TileShape> cutoutShapes = new LinkedList<>();
      // drills on top of existing vias are used in the ripup algorithm
      TileShape prevObstacleShape = IntBox.EMPTY;
      for (TreeEntry currEntry : overlaps) {
        if (!(currEntry.object instanceof Item currItem)) {
          continue;
        }
        if (currItem.is_drillable(this.netNo)) {
          continue;
        }
        if (currItem instanceof Pin pin) {
          if (p_attach_smd && pin.drill_allowed()) {
            continue;
          }
        }
        TileShape currObstacleShape =
            currItem.get_tree_shape(searchTree, currEntry.shapeIndexInObject);
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
      TileShape[] drillShapes = shapeWithHoles.split_to_convex(p_autoroute_engine.stoppableThread);

      // Use the center points of these drill shapes to try making a via.
      int drillFirstLayer = 0;
      int drillLastLayer = this.board.get_layer_count() - 1;
      for (int i = 0; i < drillShapes.length; i++) {
        TileShape currDrillShape = drillShapes[i];
        Point currDrillLocation = null;
        if (p_attach_smd) {
          currDrillLocation =
              calc_pin_center_in_drill(currDrillShape, drillFirstLayer, p_autoroute_engine.board);
          if (currDrillLocation == null) {
            currDrillLocation =
                calc_pin_center_in_drill(currDrillShape, drillLastLayer, p_autoroute_engine.board);
          }
        }
        if (currDrillLocation == null) {
          currDrillLocation = currDrillShape.centre_of_gravity().round();
        }
        ExpansionDrill newDrill =
            new ExpansionDrill(currDrillShape, currDrillLocation, drillFirstLayer, drillLastLayer);
        if (newDrill.calculate_expansion_rooms(p_autoroute_engine)) {
          this.drills.add(newDrill);
        }
      }
    }
    return this.drills;
  }

  @Override
  public TileShape get_shape() {
    return this.shape;
  }

  @Override
  public int get_dimension() {
    return 2;
  }

  @Override
  public int maze_search_element_count() {
    return this.mazeSearchInfoArr.length;
  }

  @Override
  public MazeSearchElement get_maze_search_element(int p_no) {
    return this.mazeSearchInfoArr[p_no];
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
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity) {
    if (true) {
      return;
    }
    for (ExpansionDrill currDrill : drills) {
      currDrill.draw(p_graphics, p_graphics_context, p_intensity);
    }
  }

  @Override
  public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room) {
    return null;
  }

  @Override
  public int get_id_no() {
    // Stable hash of shape and netNo
    return 31 * shape.get_id_no() + netNo;
  }
}
