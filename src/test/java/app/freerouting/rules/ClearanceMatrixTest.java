package app.freerouting.rules;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClearanceMatrixTest {

  @Test
  public void testSetValue() {
    Layer[] layers = new Layer[]{new Layer("Top", true), new Layer("Bottom", true)};
    LayerStructure layerStructure = new LayerStructure(layers);
    String[] name_arr = new String[]{"default"};
    ClearanceMatrix matrix = new ClearanceMatrix(1, layerStructure, name_arr);

    // Test with an odd value
    matrix.set_value(0, 0, 0, 5);
    assertEquals(6, matrix.get_value(0, 0, 0, false));
    assertEquals(6, matrix.max_value(0, 0));
    assertEquals(6, matrix.max_value(0));

    // Test with a negative value
    matrix.set_value(0, 0, 0, -10);
    assertEquals(0, matrix.get_value(0, 0, 0, false));
    //The max_value should be 6, as it was set in the previous step and -10 is not greater than 6
    assertEquals(6, matrix.max_value(0, 0));
    assertEquals(6, matrix.max_value(0));

    // Test with Integer.MAX_VALUE
    matrix.set_value(0, 0, 0, Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE - 1, matrix.get_value(0, 0, 0, false));
    assertEquals(Integer.MAX_VALUE - 1, matrix.max_value(0, 0));
    assertEquals(Integer.MAX_VALUE - 1, matrix.max_value(0));
  }
}
