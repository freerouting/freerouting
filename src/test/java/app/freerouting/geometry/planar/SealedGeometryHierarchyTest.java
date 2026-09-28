package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Verifies that the closed geometry hierarchies remain sealed and complete. */
class SealedGeometryHierarchyTest {

  @Test
  void pointHierarchyHasOnlyFiniteAndRationalImplementations() {
    assertTrue(Point.class.isSealed());
    assertEquals(Set.of(IntPoint.class, RationalPoint.class), permittedClasses(Point.class));
  }

  @Test
  void tileShapeHierarchyHasOnlyRegularAndSimplexBranches() {
    assertTrue(TileShape.class.isSealed());
    assertEquals(Set.of(RegularTileShape.class, Simplex.class), permittedClasses(TileShape.class));
    assertEquals(Set.of(IntBox.class, IntOctagon.class), permittedClasses(RegularTileShape.class));
  }

  private static Set<Class<?>> permittedClasses(Class<?> type) {
    return Arrays.stream(type.getPermittedSubclasses()).collect(Collectors.toUnmodifiableSet());
  }
}
