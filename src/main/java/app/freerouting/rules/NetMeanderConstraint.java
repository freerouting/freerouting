package app.freerouting.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable representation of trace length tuning meander (accordion) constraints.
 *
 * <p>Dimensional values (minAmplitude, maxAmplitude, spacing) are expressed in internal board
 * coordinate units.
 *
 * @param minAmplitude the minimum allowed excursion amplitude, or {@code <= 0} if unconstrained
 * @param maxAmplitude the maximum allowed excursion amplitude, or {@code <= 0} if unconstrained
 * @param spacing the gap/spacing between parallel meander wire segments, or {@code <= 0} if
 *     unconstrained
 * @param cornerStyle the geometric style of meander turns (fillet or chamfer)
 * @param radiusPercentage the corner rounding/chamfer radius percentage (0.0 to 100.0)
 * @param singleSided whether meanders should be restricted to one side of the baseline
 */
public record NetMeanderConstraint(
    double minAmplitude,
    double maxAmplitude,
    double spacing,
    CornerStyle cornerStyle,
    double radiusPercentage,
    boolean singleSided)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Default minimum amplitude in millimeters (0.2 mm). */
  public static final double DEFAULT_MIN_AMPLITUDE_MM = 0.2;

  /** Default maximum amplitude in millimeters (1.0 mm). */
  public static final double DEFAULT_MAX_AMPLITUDE_MM = 1.0;

  /** Default spacing for single track tuning in millimeters (0.6 mm). */
  public static final double DEFAULT_SPACING_SINGLE_TRACK_MM = 0.6;

  /** Default spacing for differential pairs in millimeters (1.0 mm). */
  public static final double DEFAULT_SPACING_DIFF_PAIR_MM = 1.0;

  /** Default spacing for differential pair skews in millimeters (0.6 mm). */
  public static final double DEFAULT_SPACING_DIFF_PAIR_SKEW_MM = 0.6;

  /** Default corner style (Fillet). */
  public static final CornerStyle DEFAULT_CORNER_STYLE = CornerStyle.FILLET;

  /** Default radius percentage (80%). */
  public static final double DEFAULT_RADIUS_PERCENTAGE = 80.0;

  /** Default single-sided option (off / false). */
  public static final boolean DEFAULT_SINGLE_SIDED = false;

  /** Sentinel instance representing an unconstrained meander specification. */
  public static final NetMeanderConstraint UNCONSTRAINED =
      new NetMeanderConstraint(
          0.0, 0.0, 0.0, DEFAULT_CORNER_STYLE, DEFAULT_RADIUS_PERCENTAGE, DEFAULT_SINGLE_SIDED);

  /** Compact constructor enforcing non-null fields and sanity bounds. */
  public NetMeanderConstraint {
    Objects.requireNonNull(cornerStyle, "cornerStyle cannot be null");
    if (minAmplitude < 0.0) {
      minAmplitude = 0.0;
    }
    if (maxAmplitude < 0.0) {
      maxAmplitude = 0.0;
    }
    if (spacing < 0.0) {
      spacing = 0.0;
    }
    if (radiusPercentage < 0.0) {
      radiusPercentage = 0.0;
    } else if (radiusPercentage > 100.0) {
      radiusPercentage = 100.0;
    }
  }

  /**
   * Factory method to create a constraint in internal board units converted from millimeters.
   *
   * @param minAmpMm minimum amplitude in mm
   * @param maxAmpMm maximum amplitude in mm
   * @param spacingMm spacing in mm
   * @param cornerStyle corner style
   * @param radiusPercentage radius percentage (0-100)
   * @param singleSided single sided flag
   * @param mmToBoardScale multiplier to convert millimeters into board units
   * @return new NetMeanderConstraint in board coordinate units
   */
  public static NetMeanderConstraint ofMm(
      double minAmpMm,
      double maxAmpMm,
      double spacingMm,
      CornerStyle cornerStyle,
      double radiusPercentage,
      boolean singleSided,
      double mmToBoardScale) {
    double scale = (mmToBoardScale > 0) ? mmToBoardScale : 1.0;
    return new NetMeanderConstraint(
        minAmpMm * scale,
        maxAmpMm * scale,
        spacingMm * scale,
        cornerStyle,
        radiusPercentage,
        singleSided);
  }

  /**
   * Factory method creating a default preset for the specified pattern type using the provided mm
   * scale factor.
   *
   * @param type target pattern type
   * @param mmToBoardScale multiplier to convert millimeters into board units
   * @return default preset for the pattern type
   */
  public static NetMeanderConstraint createDefaultPreset(
      MeanderPatternType type, double mmToBoardScale) {
    double spacingMm =
        switch (type) {
          case DIFF_PAIR -> DEFAULT_SPACING_DIFF_PAIR_MM;
          case DIFF_PAIR_SKEW -> DEFAULT_SPACING_DIFF_PAIR_SKEW_MM;
          case SINGLE_TRACK -> DEFAULT_SPACING_SINGLE_TRACK_MM;
        };
    return ofMm(
        DEFAULT_MIN_AMPLITUDE_MM,
        DEFAULT_MAX_AMPLITUDE_MM,
        spacingMm,
        DEFAULT_CORNER_STYLE,
        DEFAULT_RADIUS_PERCENTAGE,
        DEFAULT_SINGLE_SIDED,
        mmToBoardScale);
  }

  /**
   * Factory method to construct a NetMeanderConstraint from optional DSN values and a fallback.
   *
   * @param minAmp minimum amplitude in DSN units, or null/negative for fallback
   * @param maxAmp maximum amplitude in DSN units, or null/negative for fallback
   * @param spacing spacing in DSN units, or null/negative for fallback
   * @param cornerStyle corner style, or null for fallback
   * @param radiusPct radius percentage, or null for fallback
   * @param singleSided single-sided flag, or null for fallback
   * @param transform coordinate transform to convert DSN dimensions to board units
   * @param fallback fallback constraint for unset values (if null, UNCONSTRAINED is used)
   * @return resolved NetMeanderConstraint in board units
   */
  public static NetMeanderConstraint fromDsn(
      Double minAmp,
      Double maxAmp,
      Double spacing,
      CornerStyle cornerStyle,
      Double radiusPct,
      Boolean singleSided,
      app.freerouting.io.CoordinateTransform transform,
      NetMeanderConstraint fallback) {
    NetMeanderConstraint base = (fallback != null) ? fallback : UNCONSTRAINED;

    double resolvedMin;
    double resolvedMax;

    if (maxAmp != null && maxAmp >= 0) {
      resolvedMax = (transform != null) ? transform.dsnToBoard(maxAmp) : maxAmp;
    } else {
      resolvedMax = base.maxAmplitude();
    }

    if (minAmp != null && minAmp >= 0) {
      resolvedMin = (transform != null) ? transform.dsnToBoard(minAmp) : minAmp;
    } else {
      resolvedMin = base.minAmplitude();
    }

    if (resolvedMax > 0 && resolvedMin > resolvedMax) {
      double temp = resolvedMin;
      resolvedMin = resolvedMax;
      resolvedMax = temp;
    }

    double resolvedSpacing =
        (spacing != null && spacing >= 0)
            ? ((transform != null) ? transform.dsnToBoard(spacing) : spacing)
            : base.spacing();

    CornerStyle resolvedCorner = (cornerStyle != null) ? cornerStyle : base.cornerStyle();
    double resolvedRadius =
        (radiusPct != null && radiusPct >= 0) ? radiusPct : base.radiusPercentage();
    boolean resolvedSingleSided = (singleSided != null) ? singleSided : base.singleSided();

    return new NetMeanderConstraint(
        resolvedMin,
        resolvedMax,
        resolvedSpacing,
        resolvedCorner,
        resolvedRadius,
        resolvedSingleSided);
  }

  /** Returns true if a maximum amplitude constraint is defined. */
  public boolean hasAmplitude() {
    return this.maxAmplitude > 0.0;
  }

  /** Returns true if a spacing constraint is defined. */
  public boolean hasSpacing() {
    return this.spacing > 0.0;
  }

  /** Returns true if this meander constraint has active geometric restrictions. */
  public boolean isConstrained() {
    return hasAmplitude() || hasSpacing();
  }

  /**
   * Validates whether the constraint parameters are geometrically consistent.
   *
   * @return true if valid, false if inconsistent (e.g. min > max when max > 0)
   */
  public boolean isValid() {
    return !(this.maxAmplitude > 0.0 && this.minAmplitude > this.maxAmplitude);
  }

  /** Returns a copy of this constraint with updated amplitude bounds. */
  public NetMeanderConstraint withAmplitude(double newMin, double newMax) {
    return new NetMeanderConstraint(
        newMin, newMax, this.spacing, this.cornerStyle, this.radiusPercentage, this.singleSided);
  }

  /** Returns a copy of this constraint with updated spacing. */
  public NetMeanderConstraint withSpacing(double newSpacing) {
    return new NetMeanderConstraint(
        this.minAmplitude,
        this.maxAmplitude,
        newSpacing,
        this.cornerStyle,
        this.radiusPercentage,
        this.singleSided);
  }

  /** Returns a copy of this constraint with updated corner style. */
  public NetMeanderConstraint withCornerStyle(CornerStyle newStyle) {
    return new NetMeanderConstraint(
        this.minAmplitude,
        this.maxAmplitude,
        this.spacing,
        newStyle,
        this.radiusPercentage,
        this.singleSided);
  }

  /** Returns a copy of this constraint with updated radius percentage. */
  public NetMeanderConstraint withRadiusPercentage(double newRadius) {
    return new NetMeanderConstraint(
        this.minAmplitude,
        this.maxAmplitude,
        this.spacing,
        this.cornerStyle,
        newRadius,
        this.singleSided);
  }

  /** Returns a copy of this constraint with updated single-sided option. */
  public NetMeanderConstraint withSingleSided(boolean newSingleSided) {
    return new NetMeanderConstraint(
        this.minAmplitude,
        this.maxAmplitude,
        this.spacing,
        this.cornerStyle,
        this.radiusPercentage,
        newSingleSided);
  }
}
