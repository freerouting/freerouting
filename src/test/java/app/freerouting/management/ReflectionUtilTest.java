package app.freerouting.management;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for {@link ReflectionUtil#copyFields(Object, Object)}.
 *
 * <p>These tests cover the array-length-mismatch bug that caused
 * {@code AutorouteSettings.get_layer_active: p_layer=N out of range [0..1]}
 * warnings on multi-layer boards. The original code in {@code copyFields}
 * only copied a primitive/string array to the target when the target field
 * was {@code null} or zero-length, silently discarding the source array
 * when both arrays were non-empty but of different lengths. In particular,
 * when a 4-layer DSN's per-layer settings (e.g. {@code RouterSettings.isLayerActive})
 * were merged onto a default 2-layer target, the 4-element array was dropped.
 */
class ReflectionUtilTest {

  /**
   * Small public holder used as both source and target for {@code copyFields}.
   * {@code copyFields} only walks public, non-static fields, so this mirrors
   * {@code RouterSettings.isLayerActive}'s public layout.
   */
  public static class PrimitiveArrayHolder {
    public boolean[] flags;
    public int[] counts;
    public String[] names;
  }

  @Test
  void copyFieldsReplacesPrimitiveArrayWhenLengthsDiffer() {
    PrimitiveArrayHolder source = new PrimitiveArrayHolder();
    source.flags = new boolean[]{true, false, true, false};
    source.counts = new int[]{10, 20, 30, 40};
    source.names = new String[]{"a", "b", "c", "d"};

    PrimitiveArrayHolder target = new PrimitiveArrayHolder();
    target.flags = new boolean[]{true, true};
    target.counts = new int[]{0, 0};
    target.names = new String[]{"x", "y"};

    ReflectionUtil.copyFields(source, target);

    assertEquals(4, target.flags.length, "boolean[] length must match source");
    assertArrayEquals(new boolean[]{true, false, true, false}, target.flags);
    assertEquals(4, target.counts.length, "int[] length must match source");
    assertArrayEquals(new int[]{10, 20, 30, 40}, target.counts);
    assertEquals(4, target.names.length, "String[] length must match source");
    assertArrayEquals(new String[]{"a", "b", "c", "d"}, target.names);
  }

  @Test
  void copyFieldsCopiesPrimitiveArrayWhenTargetIsNull() {
    PrimitiveArrayHolder source = new PrimitiveArrayHolder();
    source.flags = new boolean[]{true, false, true, false};

    PrimitiveArrayHolder target = new PrimitiveArrayHolder();
    target.flags = null;

    ReflectionUtil.copyFields(source, target);

    assertArrayEquals(new boolean[]{true, false, true, false}, target.flags);
  }

  @Test
  void copyFieldsCopiesPrimitiveArrayWhenTargetIsEmpty() {
    PrimitiveArrayHolder source = new PrimitiveArrayHolder();
    source.flags = new boolean[]{true, false, true, false};

    PrimitiveArrayHolder target = new PrimitiveArrayHolder();
    target.flags = new boolean[0];

    ReflectionUtil.copyFields(source, target);

    assertArrayEquals(new boolean[]{true, false, true, false}, target.flags);
  }
}
