package app.freerouting.gui.rendering;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * The color intensities for each item type. The values are between 0 (invisible) and 1 (full
 * intensity).
 */
public class ColorIntensityTable implements Serializable {

  private final double[] intensities;
  private transient boolean missingSerializedDataLogged;

  /**
   * Creates a new instance of ColorIntensityTable. The elements of intensities are expected between
   * 0 and 1.
   */
  public ColorIntensityTable() {
    intensities = new double[ObjectNames.values().length];
    intensities[ObjectNames.TRACES.ordinal()] = 1.0;
    intensities[ObjectNames.VIAS.ordinal()] = 1.0;
    intensities[ObjectNames.PINS.ordinal()] = 1.0;
    intensities[ObjectNames.CONDUCTION_AREAS.ordinal()] = 0.2;
    intensities[ObjectNames.KEEPOUTS.ordinal()] = 0.2;
    intensities[ObjectNames.VIA_KEEPOUTS.ordinal()] = 0.2;
    intensities[ObjectNames.PLACE_KEEPOUTS.ordinal()] = 0.2;
    intensities[ObjectNames.COMPONENT_OUTLINES.ordinal()] = 1.0;
    intensities[ObjectNames.HIGHLIGHT.ordinal()] = 0.8;
    intensities[ObjectNames.INCOMPLETES.ordinal()] = 1.0;
    intensities[ObjectNames.LENGTH_MATCHING_AREAS.ordinal()] = 0.1;
    intensities[ObjectNames.DRILL_HOLES.ordinal()] = 1.0;
  }

  /** Copy constructor. */
  public ColorIntensityTable(ColorIntensityTable colorIntensityTable) {
    this.intensities = colorIntensityTable.intensities.clone();
  }

  /** Returns the intensity value for the given object type index. */
  public double getValue(int no) {
    if (no < 0 || no >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.get_value: no out of range");
      return 0;
    }
    if (no >= intensities.length) {
      logMissingSerializedDataOnce("get_value", no);
      return getDefaultValue(no);
    }
    return intensities[no];
  }

  /** Sets the intensity value for the given object type index. */
  public void setValue(int no, double value) {
    if (no < 0 || no >= ObjectNames.values().length) {
      FRLogger.warn("ColorIntensityTable.set_value: no out of range");
      return;
    }
    if (no >= intensities.length) {
      logMissingSerializedDataOnce("set_value", no);
      return;
    }
    intensities[no] = value;
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
