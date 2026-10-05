package app.freerouting.rules;

import app.freerouting.board.model.structure.AngleRestriction;
import java.io.Serializable;

/**
 * Geometric constraints and waveform configuration for trace length tuning via serpentine meanders.
 *
 * <p>Supports single-ended traces and differential pairs using accordion (symmetric) or trombone
 * (single-sided) patterns, with configurable amplitude, spacing, and corner styling.
 *
 * @param maxAmplitude maximum peak-to-peak excursion (height) of meander waves from the baseline.
 *     0.0 strictly prohibits meanders; negative or unspecified means unconstrained.
 * @param minAmplitude minimum excursion threshold. 0.0 or negative means unconstrained.
 * @param gap minimum clear spacing between adjacent parallel meander folds. 0.0 or negative
 *     triggers the Specctra 3W rule (3x trace width) fallback.
 * @param singleSided true if meanders are restricted to one side (trombone/U-loop); false for
 *     symmetric dual-sided accordions.
 * @param cornerStyle corner geometry style (auto, 45-degree chamfer, rounded fillet, 90-degree).
 * @param cornerRadiusPercentage corner radius percentage for rounded or mitered turns (0-100%).
 */
public record NetMeanderConstraint(
    double maxAmplitude,
    double minAmplitude,
    double gap,
    boolean singleSided,
    CornerStyle cornerStyle,
    int cornerRadiusPercentage)
    implements Serializable {

  public static final double UNSPECIFIED = -1.0;
  public static final int DEFAULT_CORNER_RADIUS_PERCENT = 80;

  /** Corner geometry style for meander fold crests and turns. */
  public enum CornerStyle {
    AUTO,
    CHAMFERED_45,
    FILLETED_ROUND,
    ORTHOGONAL_90
  }

  /**
   * Convenience constructor for standard Specctra amplitude and gap constraints, defaulting to
   * dual-sided accordion, automatic corner style, and 80% corner radius.
   *
   * @param maxAmplitude maximum excursion height (0 = prohibited, &lt;= 0 = unspecified)
   * @param minAmplitude minimum excursion height (&lt;= 0 = unspecified)
   * @param gap spacing between adjacent folds (&lt;= 0 = Specctra 3W fallback)
   */
  public NetMeanderConstraint(double maxAmplitude, double minAmplitude, double gap) {
    this(maxAmplitude, minAmplitude, gap, false, CornerStyle.AUTO, DEFAULT_CORNER_RADIUS_PERCENT);
  }

  /**
   * Returns true if meanders are permitted on this net or class. Per the Specctra specification,
   * setting max_amplitude to 0 explicitly prohibits accordion patterns.
   */
  public boolean isMeanderAllowed() {
    return maxAmplitude != 0.0;
  }

  /** Returns true if a positive maximum amplitude bound is defined. */
  public boolean hasMaxAmplitude() {
    return maxAmplitude > 0;
  }

  /** Returns true if a positive minimum amplitude bound is defined. */
  public boolean hasMinAmplitude() {
    return minAmplitude > 0;
  }

  /** Returns true if a positive gap / spacing rule is defined. */
  public boolean hasGap() {
    return gap > 0;
  }

  /**
   * Resolves the effective gap adhering to the Specctra 3W rule: Returns {@code gap} if {@code gap
   * >= 3 * traceWidth}; otherwise returns {@code Math.max(3.0 * traceWidth, traceWidth +
   * traceClearance)}.
   *
   * @param traceWidth the width of the trace in board units
   * @param traceClearance the clearance requirement in board units
   * @return the resolved physical gap between meander folds in board units
   */
  public double resolveEffectiveGap(double traceWidth, double traceClearance) {
    double minAllowed = Math.max(3.0 * traceWidth, traceWidth + traceClearance);
    if (gap > 0) {
      return Math.max(gap, minAllowed);
    }
    return minAllowed;
  }

  /**
   * Resolves the actual corner style respecting the active {@link AngleRestriction}.
   *
   * @param snapAngle active board or session angle restriction
   * @return the resolved corner style (CHAMFERED_45, ORTHOGONAL_90, etc.)
   */
  public CornerStyle resolveCornerStyle(AngleRestriction snapAngle) {
    if (cornerStyle != CornerStyle.AUTO) {
      return cornerStyle;
    }
    if (snapAngle == AngleRestriction.NINETY_DEGREE) {
      return CornerStyle.ORTHOGONAL_90;
    }
    return CornerStyle.CHAMFERED_45;
  }

  /**
   * Merges this constraint with fallback values for any unspecified fields.
   *
   * @param fallback fallback constraint (e.g. from NetClass or BoardRules)
   * @return a new merged constraint
   */
  public NetMeanderConstraint mergeWith(NetMeanderConstraint fallback) {
    if (fallback == null) {
      return this;
    }
    double resolvedMax =
        (hasMaxAmplitude() || maxAmplitude == 0.0) ? this.maxAmplitude : fallback.maxAmplitude;
    double resolvedMin = hasMinAmplitude() ? this.minAmplitude : fallback.minAmplitude;
    double resolvedGap = hasGap() ? this.gap : fallback.gap;
    boolean resolvedSingleSided = this.singleSided || fallback.singleSided;
    CornerStyle resolvedCorner =
        (this.cornerStyle != CornerStyle.AUTO) ? this.cornerStyle : fallback.cornerStyle;
    int resolvedRadius =
        (this.cornerStyle != CornerStyle.AUTO && this.cornerRadiusPercentage > 0)
            ? this.cornerRadiusPercentage
            : fallback.cornerRadiusPercentage;

    return new NetMeanderConstraint(
        resolvedMax, resolvedMin, resolvedGap, resolvedSingleSided, resolvedCorner, resolvedRadius);
  }

  /** Returns a copy of this constraint with updated amplitude bounds. */
  public NetMeanderConstraint withAmplitude(double max, double min) {
    return new NetMeanderConstraint(
        max, min, this.gap, this.singleSided, this.cornerStyle, this.cornerRadiusPercentage);
  }

  /** Returns a copy of this constraint with an updated gap value. */
  public NetMeanderConstraint withGap(double newGap) {
    return new NetMeanderConstraint(
        this.maxAmplitude,
        this.minAmplitude,
        newGap,
        this.singleSided,
        this.cornerStyle,
        this.cornerRadiusPercentage);
  }

  /** Returns a copy of this constraint with an updated singleSided flag. */
  public NetMeanderConstraint withSingleSided(boolean newSingleSided) {
    return new NetMeanderConstraint(
        this.maxAmplitude,
        this.minAmplitude,
        this.gap,
        newSingleSided,
        this.cornerStyle,
        this.cornerRadiusPercentage);
  }

  /** Returns a copy of this constraint with updated corner style settings. */
  public NetMeanderConstraint withCornerStyle(CornerStyle newStyle, int radiusPercent) {
    return new NetMeanderConstraint(
        this.maxAmplitude, this.minAmplitude, this.gap, this.singleSided, newStyle, radiusPercent);
  }
}
