package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Collection;

/** An expansion room, whose shape is not yet completely calculated. */
public class IncompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom {

  /** A shape which should be contained in the completed shape. */
  private TileShape containedShape;

  /**
   * Creates a new instance of IncompleteFreeSpaceExpansionRoom. If p_shape == null means p_shape is
   * the whole plane.
   */
  public IncompleteFreeSpaceExpansionRoom(
      TileShape p_shape, int p_layer, TileShape p_contained_shape) {
    super(p_shape, p_layer);
    containedShape = p_contained_shape;
  }

  public TileShape getContainedShape() {
    return this.containedShape;
  }

  public void setContainedShape(TileShape p_shape) {
    this.containedShape = p_shape;
  }

  public Collection<TargetItemExpansionDoor> getTargetDoors() {
    return new ArrayList<>();
  }

  @Override
  public int getIdNo() {
    // Stable hash of shape and layer
    return 31 * getShape().getIdNo() + getLayer();
  }
}
