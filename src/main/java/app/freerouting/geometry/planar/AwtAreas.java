package app.freerouting.geometry.planar;

import java.util.ArrayList;
import java.util.List;

/** Boolean operations on many {@link java.awt.geom.Area} instances. */
public final class AwtAreas {

  private AwtAreas() {}

  /**
   * Returns the union of {@code areas}, merging them pairwise level by level.
   *
   * <p>Adding or subtracting shapes one by one to a growing area re-sweeps every accumulated edge
   * on each call, which is quadratic in the number of shapes. A balanced merge keeps each operation
   * small. The input areas may be modified and must not be reused by the caller.
   */
  public static java.awt.geom.Area unionAll(List<java.awt.geom.Area> areas) {
    if (areas.isEmpty()) {
      return new java.awt.geom.Area();
    }
    List<java.awt.geom.Area> level = new ArrayList<>(areas);
    while (level.size() > 1) {
      List<java.awt.geom.Area> next = new ArrayList<>((level.size() + 1) / 2);
      for (int i = 0; i + 1 < level.size(); i += 2) {
        java.awt.geom.Area merged = level.get(i);
        merged.add(level.get(i + 1));
        next.add(merged);
      }
      if (level.size() % 2 == 1) {
        next.add(level.get(level.size() - 1));
      }
      level = next;
    }
    return level.get(0);
  }

  /** Subtracts the union of {@code cutouts} from {@code area} with a single final subtraction. */
  public static void subtractAll(java.awt.geom.Area area, List<java.awt.geom.Area> cutouts) {
    if (!cutouts.isEmpty()) {
      area.subtract(unionAll(cutouts));
    }
  }
}
