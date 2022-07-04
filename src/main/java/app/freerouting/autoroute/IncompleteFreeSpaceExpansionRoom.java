package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;

/** An expansion room, whose shape is not yet completely calculated. */
public class IncompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom {

  /** A shape which should be contained in the completed shape. */
  private TileShape contained_shape;

  /**
   * Creates a new instance of IncompleteFreeSpaceExpansionRoom. If p_shape == null means p_shape is
   * the whole plane.
   */
  public IncompleteFreeSpaceExpansionRoom(
      TileShape p_shape, int p_layer, TileShape p_contained_shape) {
    super(p_shape, p_layer);
    contained_shape = p_contained_shape;
  }

  public TileShape get_contained_shape() {
    return this.contained_shape;
  }

  public void set_contained_shape(TileShape p_shape) {
    this.contained_shape = p_shape;
  }

  public Collection<TargetItemExpansionDoor> get_target_doors() {
    return new java.util.LinkedList<TargetItemExpansionDoor>();
  }
}
