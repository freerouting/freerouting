package app.freerouting.gui.rendering;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * The color intensities for each item type. The values are between 0 (invisible) and 1 (full
 * intensity).
 */
public class ColorIntensityTable implements Serializable {

  private final double[] arr;
  private transient boolean missingSerializedDataLogged;

  /**
   * Creates a new instance of ColorIntensityTable. The elements of intensities are expected between
   * 0 and 1.
   */
  public ColorIntensityTable() {
    arr = new double[ObjectNames.values().length];
    arr[ObjectNames.TRACES.ordinal()] = 1.0;
    arr[ObjectNames.VIAS.ordinal()] = 1.0;
    arr[ObjectNames.PINS.ordinal()] = 1.0;
    arr[ObjectNames.CONDUCTION_AREAS.ordinal()] = 0.2;
    arr[ObjectNames.KEEPOUTS.ordinal()] = 0.2;
    arr[ObjectNames.VIA_KEEPOUTS.ordinal()] = 0.2;
    arr[ObjectNames.PLACE_KEEPOUTS.ordinal()] = 0.2;
    arr[ObjectNames.COMPONENT_OUTLINES.ordinal()] = 1.0;
    arr[ObjectNames.HIGHLIGHT.ordinal()] = 0.8;
    arr[ObjectNames.INCOMPLETES.ordinal()] = 1.0;
    arr[ObjectNames.LENGTH_MATCHING_AREAS.ordinal()] = 0.1;
    arr[ObjectNames.DRILL_HOLES.ordinal()] = 1.0;
  }

  /** Copy constructor. */
  public ColorIntensityTable(ColorIntensityTable colorIntensityTable) {
    this.arr = colorIntensityTable.arr.clone();
  }

  /** Returns the intensity value for the given object type index. */
  public double getValue(int no) {
    if (no < 0 || no >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.get_value: no out of range");
      return 0;
    }
    if (no >= arr.length) {
      logMissingSerializedDataOnce("get_value", no);
      return getDefaultValue(no);
    }
    return arr[no];
  }

  /** Sets the intensity value for the given object type index. */
  public void setValue(int no, double value) {
    if (no < 0 || no >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.set_value: no out of range");
      return;
    }
    if (no >= arr.length) {
      logMissingSerializedDataOnce("set_value", no);
      return;
    }
    arr[no] = value;
  }

  private void logMissingSerializedDataOnce(String methodName, int no) {
    if (!missingSerializedDataLogged) {
      FRLogger.warn(
          "ColorIntensityTable." + methodName + ": no " + no + " missing in serialized data");
      missingSerializedDataLogged = true;
    }
  }

  private double getDefaultValue(int no) {
    if (no == ObjectNames.DRILL_HOLES.ordinal()) {
      return 1.0;
    }
    return 0;
  }

  /** Object type names indexed in the intensity table. */
  public enum ObjectNames {
    TRACES,
    VIAS,
    PINS,
    CONDUCTION_AREAS,
    KEEPOUTS,
    VIA_KEEPOUTS,
    PLACE_KEEPOUTS,
    COMPONENT_OUTLINES,
    HIGHLIGHT,
    INCOMPLETES,
    LENGTH_MATCHING_AREAS,
    DRILL_HOLES
  }
}
