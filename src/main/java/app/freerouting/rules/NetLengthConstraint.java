package app.freerouting.rules;

import java.io.Serializable;

/**
 * Immutable representation of trace length constraints for a net or net class.
 *
 * <p>Length values are expressed in internal board coordinate units. A value of {@code <= 0}
 * indicates that the respective bound is unconstrained.
 *
 * @param minLength the minimum allowed trace length, or {@code <= 0} if unconstrained
 * @param maxLength the maximum allowed trace length, or {@code <= 0} if unconstrained
 */
public record NetLengthConstraint(double minLength, double maxLength) implements Serializable {

  /** Unconstrained length instance with no minimum and no maximum limit. */
  public static final NetLengthConstraint UNCONSTRAINED = new NetLengthConstraint(0.0, 0.0);

  /**
   * Returns true if a minimum trace length constraint is defined.
   *
   * @return true if {@code minLength > 0}
   */
  public boolean hasMin() {
    return this.minLength > 0.0;
  }

  /**
   * Returns true if a maximum trace length constraint is defined.
   *
   * @return true if {@code maxLength > 0}
   */
  public boolean hasMax() {
    return this.maxLength > 0.0;
  }

  /**
   * Returns true if at least one length constraint (minimum or maximum) is defined.
   *
   * @return true if either minimum or maximum length is restricted
   */
  public boolean isConstrained() {
    return hasMin() || hasMax();
  }

  /**
   * Calculates the target length for length tuning algorithms.
   *
   * <p>If both minimum and maximum are defined, returns the midpoint between them. If only minimum
   * is defined, returns the minimum. If only maximum is defined, returns the maximum. If
   * unconstrained, returns 0.0.
   *
   * @return the target length in board coordinate units
   */
  public double targetLength() {
    if (hasMin() && hasMax()) {
      return (this.minLength + this.maxLength) / 2.0;
    }
    if (hasMin()) {
      return this.minLength;
    }
    if (hasMax()) {
      return this.maxLength;
    }
    return 0.0;
  }

  /**
   * Checks whether the given routed trace length satisfies this constraint.
   *
   * @param currentLength the cumulative trace length to check
   * @return true if the length satisfies both minimum and maximum constraints
   */
  public boolean isSatisfied(double currentLength) {
    if (hasMin() && currentLength < this.minLength) {
      return false;
    }
    if (hasMax() && currentLength > this.maxLength) {
      return false;
    }
    return true;
  }
}
