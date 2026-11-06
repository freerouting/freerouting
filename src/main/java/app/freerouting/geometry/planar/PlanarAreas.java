package app.freerouting.geometry.planar;

import java.util.List;

/**
 * Utility functions for constructive solid geometry operations on collections of {@link PlanarArea}
 * instances.
 *
 * <p>Replaces {@code AwtAreas} to decouple geometric clipping from {@code java.desktop}.
 */
public final class PlanarAreas {

  private PlanarAreas() {}

  /**
   * Computes the geometric union of all given planar areas.
   *
   * @param areas collection of planar areas to union
   * @return the merged planar area
   */
  public static PlanarArea unionAll(List<PlanarArea> areas) {
    return PlanarArea.unionAll(areas);
  }

  /**
   * Subtracts all given cutouts from the target planar area.
   *
   * @param target the target area to cut into
   * @param cutouts collection of areas to subtract
   * @return the updated planar area with cutouts removed
   */
  public static PlanarArea subtractAll(PlanarArea target, List<PlanarArea> cutouts) {
    if (target == null) {
      return PlanarArea.empty();
    }
    return target.subtractAll(cutouts);
  }
}
