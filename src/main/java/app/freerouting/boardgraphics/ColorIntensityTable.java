package app.freerouting.boardgraphics;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * The color intensities for each item type. The values are between 0 (invisible) and 1 (full intensity).
 */
public class ColorIntensityTable implements Serializable {

  private final double[] arr;
  private transient boolean missing_serialized_data_logged;

  /**
   * Creates a new instance of ColorIntensityTable. The elements of p_intensities are expected between 0 and 1.
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
    arr[ObjectNames.HILIGHT.ordinal()] = 0.8;
    arr[ObjectNames.INCOMPLETES.ordinal()] = 1.0;
    arr[ObjectNames.LENGTH_MATCHING_AREAS.ordinal()] = 0.1;
    arr[ObjectNames.DRILL_HOLES.ordinal()] = 1.0;
  }

  /**
   * Copy constructor.
   */
  public ColorIntensityTable(ColorIntensityTable p_color_intensity_table) {
    this.arr = p_color_intensity_table.arr.clone();
  }

  public double get_value(int p_no) {
    if (p_no < 0 || p_no >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.get_value: p_no out of range");
      return 0;
    }
    if (p_no >= arr.length) {
      log_missing_serialized_data_once("get_value", p_no);
      return get_default_value(p_no);
    }
    return arr[p_no];
  }

  public void set_value(int p_no, double p_value) {
    if (p_no < 0 || p_no >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.set_value: p_no out of range");
      return;
    }
    if (p_no >= arr.length) {
      log_missing_serialized_data_once("set_value", p_no);
      return;
    }
    arr[p_no] = p_value;
  }

  private void log_missing_serialized_data_once(String methodName, int p_no) {
    if (!missing_serialized_data_logged) {
      FRLogger.warn("ColorIntensityTable." + methodName + ": p_no " + p_no + " missing in serialized data");
      missing_serialized_data_logged = true;
    }
  }

  private double get_default_value(int p_no) {
    if (p_no == ObjectNames.DRILL_HOLES.ordinal()) {
      return 1.0;
    }
    return 0;
  }

  public enum ObjectNames {
    TRACES, VIAS, PINS, CONDUCTION_AREAS, KEEPOUTS, VIA_KEEPOUTS, PLACE_KEEPOUTS, COMPONENT_OUTLINES, HILIGHT, INCOMPLETES, LENGTH_MATCHING_AREAS, DRILL_HOLES
  }
}