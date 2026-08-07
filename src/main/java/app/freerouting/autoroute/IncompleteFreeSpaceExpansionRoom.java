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

  public TileShape get_contained_shape() {
    return this.containedShape;
  }

  public void set_contained_shape(TileShape p_shape) {
    this.containedShape = p_shape;
  }

  public Collection<TargetItemExpansionDoor> get_target_doors() {
    return new ArrayList<>();
  }

  @Override
  public int get_id_no() {
    // Stable hash of shape and layer
    return 31 * get_shape().get_id_no() + get_layer();
  }
}
