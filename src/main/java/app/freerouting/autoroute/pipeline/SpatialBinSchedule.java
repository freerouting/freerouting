package app.freerouting.autoroute.pipeline;

import app.freerouting.geometry.planar.IntBox;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups autoroute items into bins whose halos do not touch. Membership is a pure function of the
 * halo array, so one thread and many threads build the same groups. A null halo is a serial
 * barrier: the route may leave every bin.
 */
final class SpatialBinSchedule {

  final int[] componentOf;
  final List<int[]> components;

  private SpatialBinSchedule(int[] componentOf, List<int[]> components) {
    this.componentOf = componentOf;
    this.components = components;
  }

  static SpatialBinSchedule assign(IntBox[] halos) {
    int count = halos.length;
    int[] parent = new int[count];
    for (int index = 0; index < count; index++) {
      parent[index] = index;
    }
    for (int left = 0; left < count; left++) {
      if (halos[left] == null) {
        continue;
      }
      for (int right = left + 1; right < count; right++) {
        if (halos[right] != null && halos[left].intersects(halos[right])) {
          union(parent, left, right);
        }
      }
    }
    int[] componentOf = new int[count];
    List<int[]> components = new ArrayList<>();
    int[] built = new int[count];
    java.util.Arrays.fill(built, -1);
    for (int index = 0; index < count; index++) {
      int root = find(parent, index);
      componentOf[index] = root;
      if (built[root] < 0) {
        int members = 0;
        for (int other = index; other < count; other++) {
          if (find(parent, other) == root) {
            members++;
          }
        }
        int[] component = new int[members];
        int cursor = 0;
        for (int other = 0; other < count; other++) {
          if (find(parent, other) == root) {
            component[cursor++] = other;
          }
        }
        built[root] = components.size();
        components.add(component);
      }
    }
    return new SpatialBinSchedule(componentOf, components);
  }

  /** True when {@code inner} lies inside {@code outer}, including on the boundary. */
  static boolean covers(IntBox outer, IntBox inner) {
    if (outer == null || inner == null) {
      return false;
    }
    return inner.ll.x >= outer.ll.x
        && inner.ll.y >= outer.ll.y
        && inner.ur.x <= outer.ur.x
        && inner.ur.y <= outer.ur.y;
  }

  private static void union(int[] parent, int left, int right) {
    int leftRoot = find(parent, left);
    int rightRoot = find(parent, right);
    if (leftRoot == rightRoot) {
      return;
    }
    if (leftRoot < rightRoot) {
      parent[rightRoot] = leftRoot;
    } else {
      parent[leftRoot] = rightRoot;
    }
  }

  private static int find(int[] parent, int index) {
    int root = index;
    while (parent[root] != root) {
      root = parent[root];
    }
    while (parent[index] != root) {
      int next = parent[index];
      parent[index] = root;
      index = next;
    }
    return root;
  }
}
