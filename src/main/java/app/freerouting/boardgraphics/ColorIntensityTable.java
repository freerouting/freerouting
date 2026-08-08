package app.freerouting.boardgraphics;

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
   * Creates a new instance of ColorIntensityTable. The elements of p_intensities are expected
   * between 0 and 1.
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
  public ColorIntensityTable(ColorIntensityTable pColorIntensityTable) {
    this.arr = pColorIntensityTable.arr.clone();
  }

  public double getValue(int pNo) {
    if (pNo < 0 || pNo >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.get_value: p_no out of range");
      return 0;
    }
    if (pNo >= arr.length) {
      logMissingSerializedDataOnce("get_value", pNo);
      return getDefaultValue(pNo);
    }
    return arr[pNo];
  }

  public void setValue(int pNo, double pValue) {
    if (pNo < 0 || pNo >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.set_value: p_no out of range");
      return;
    }
    if (pNo >= arr.length) {
      logMissingSerializedDataOnce("set_value", pNo);
      return;
    }
    arr[pNo] = pValue;
  }

  private void logMissingSerializedDataOnce(String methodName, int pNo) {
    if (!missingSerializedDataLogged) {
      FRLogger.warn(
          "ColorIntensityTable." + methodName + ": p_no " + pNo + " missing in serialized data");
      missingSerializedDataLogged = true;
    }
  }

  private double getDefaultValue(int pNo) {
    if (pNo == ObjectNames.DRILL_HOLES.ordinal()) {
      return 1.0;
    }
    return 0;
  }

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
