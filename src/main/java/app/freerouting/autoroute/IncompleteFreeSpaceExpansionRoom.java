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
  public IncompleteFreeSpaceExpansionRoom(TileShape pShape, int pLayer, TileShape pContainedShape) {
    super(pShape, pLayer);
    containedShape = pContainedShape;
  }

  public TileShape getContainedShape() {
    return this.containedShape;
  }

  public void setContainedShape(TileShape pShape) {
    this.containedShape = pShape;
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
