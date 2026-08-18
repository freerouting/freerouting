package app.freerouting.autoroute.expansion;

import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Collection;

/** An expansion room, whose shape is not yet completely calculated. */
public class IncompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom {

  /** A shape which should be contained in the completed shape. */
  private TileShape containedShape;

  /**
   * Creates a new instance of IncompleteFreeSpaceExpansionRoom. If shape == null, it means shape is
   * the whole plane.
   */
  public IncompleteFreeSpaceExpansionRoom(TileShape shape, int layer, TileShape containedShape) {
    super(shape, layer);
    this.containedShape = containedShape;
  }

  /** Gets the shape that is contained within this room. */
  public TileShape getContainedShape() {
    return this.containedShape;
  }

  /** Sets the contained shape for this room. */
  public void setContainedShape(TileShape shape) {
    this.containedShape = shape;
  }

  /** Returns an empty list of target doors for incomplete rooms. */
  public Collection<TargetItemExpansionDoor> getTargetDoors() {
    return new ArrayList<>();
  }

  @Override
  public int getId() {
    // Stable hash of shape and layer
    return 31 * getShape().getId() + getLayer();
  }
}
