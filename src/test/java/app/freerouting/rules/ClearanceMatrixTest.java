package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import org.junit.jupiter.api.Test;

public class ClearanceMatrixTest {

  @Test
  public void testSetValue() {
    Layer[] layers = new Layer[] {new Layer("Top", true), new Layer("Bottom", true)};
    LayerStructure layerStructure = new LayerStructure(layers);
    String[] nameArr = new String[] {"default"};
    ClearanceMatrix matrix = new ClearanceMatrix(1, layerStructure, nameArr);

    // Test with an odd value
    matrix.setValue(0, 0, 0, 5);
    assertEquals(6, matrix.getValue(0, 0, 0, false));
    assertEquals(6, matrix.maxValue(0, 0));
    assertEquals(6, matrix.maxValue(0));

    // Test with a negative value
    matrix.setValue(0, 0, 0, -10);
    assertEquals(0, matrix.getValue(0, 0, 0, false));
    // The maxValue should be 6, as it was set in the previous step and -10 is not greater than 6
    assertEquals(6, matrix.maxValue(0, 0));
    assertEquals(6, matrix.maxValue(0));

    // Test with Integer.MAX_VALUE
    matrix.setValue(0, 0, 0, Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE - 1, matrix.getValue(0, 0, 0, false));
    assertEquals(Integer.MAX_VALUE - 1, matrix.maxValue(0, 0));
    assertEquals(Integer.MAX_VALUE - 1, matrix.maxValue(0));
  }
}
