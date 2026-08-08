package app.freerouting.autoroute;

import app.freerouting.board.SearchTreeObject;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;

/** Layer change expansion object in the maze search algorithm. */
public class ExpansionDrill implements ExpandableObject {

  /** The location, where the drill is checked. */
  public final Point location;

  /** The first layer of the drill */
  public final int firstLayer;

  /** The last layer of the drill */
  public final int lastLayer;

  /** Array of dimension lastLayer - firstLayer + 1. */
  public final CompleteExpansionRoom[] roomArr;

  private final MazeSearchElement[] mazeSearchInfoArr;

  /** The shape of the drill. */
  private final TileShape shape;

  /** Creates a new instance of Drill */
  public ExpansionDrill(TileShape p_shape, Point p_location, int p_first_layer, int p_last_layer) {
    shape = p_shape;
    location = p_location;
    firstLayer = p_first_layer;
    lastLayer = p_last_layer;
    int layerCount = p_last_layer - p_first_layer + 1;
    roomArr = new CompleteExpansionRoom[layerCount];
    mazeSearchInfoArr = new MazeSearchElement[layerCount];
    for (int i = 0; i < mazeSearchInfoArr.length; i++) {
      mazeSearchInfoArr[i] = new MazeSearchElement();
    }
  }

  /**
   * Looks for the expansion room of this drill on each layer. Creates a
   * CompleteFreeSpaceExpansionRoom, if no expansion room is found. Returns false, if that was not
   * possible because of an obstacle at this.location on some layer in the compensated search tree.
   */
  public boolean calculateExpansionRooms(AutorouteEngine p_autoroute_engine) {
    TileShape searchShape = TileShape.getInstance(location);
    Collection<SearchTreeObject> overlaps =
        p_autoroute_engine.autorouteSearchTree.overlappingObjects(searchShape, -1);
    for (int i = this.firstLayer; i <= this.lastLayer; i++) {
      CompleteExpansionRoom foundRoom = null;
      Iterator<SearchTreeObject> it = overlaps.iterator();
      while (it.hasNext()) {
        SearchTreeObject currOb = it.next();
        if (!(currOb instanceof CompleteExpansionRoom currRoom)) {
          it.remove();
          continue;
        }
        if (currRoom.getLayer() == i) {
          foundRoom = currRoom;
          it.remove();
          break;
        }
      }
      if (foundRoom == null) {
        // create a new expansion room on this layer
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(null, i, searchShape);
        Collection<CompleteFreeSpaceExpansionRoom> newRooms =
            p_autoroute_engine.completeExpansionRoom(newIncompleteRoom);
        if (newRooms.size() != 1) {
          // the size may be 0 because of an obstacle in the compensated tree at this.location
          return false;
        }
        Iterator<CompleteFreeSpaceExpansionRoom> it2 = newRooms.iterator();
        if (it2.hasNext()) {
          foundRoom = it2.next();
        }
      }
      this.roomArr[i - firstLayer] = foundRoom;
    }
    return true;
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
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom p_room) {
    return null;
  }

  @Override
  public int mazeSearchElementCount() {
    return this.mazeSearchInfoArr.length;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int p_no) {
    return this.mazeSearchInfoArr[p_no];
  }

  @Override
  public void reset() {
    for (MazeSearchElement currInfo : mazeSearchInfoArr) {
      currInfo.reset();
    }
  }

  @Override
  public int getIdNo() {
    // Stable hash of location and layers
    return 31 * (31 * location.getIdNo() + firstLayer) + lastLayer;
  }

  /*
  * Test draw of the shape of this drill.

  */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity) {
    Color drawColor = p_graphics_context.getHilightColor();
    p_graphics_context.fillArea(this.shape, p_graphics, drawColor, p_intensity);
    p_graphics_context.drawBoundary(this.shape, 0, drawColor, p_graphics, 1);
  }
}
