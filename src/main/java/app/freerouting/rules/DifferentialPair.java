package app.freerouting.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable representation of a differential signal pair consisting of a positive and a negative
 * net.
 *
 * <p>A differential pair links two complementary conductors (e.g. {@code USB_P} and {@code USB_N})
 * that should be routed together with a specified coupled spacing.
 *
 * @param positiveNet the positive signal net (e.g. {@code D+} or {@code CLK_P})
 * @param negativeNet the negative signal net (e.g. {@code D-} or {@code CLK_N})
 * @param baseName the common logical base name without polarity suffixes (e.g. {@code USB})
 * @param coupledSpacing target edge-to-edge or center-to-center spacing in internal board units, or
 *     {@code <= 0} if unconstrained
 * @param maxSkew maximum allowable trace length difference between the two nets in internal board
 *     units, or {@code <= 0} if unconstrained
 */
public record DifferentialPair(
    Net positiveNet, Net negativeNet, String baseName, double coupledSpacing, double maxSkew)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Compact constructor enforcing non-null nets, base name, and non-negative constraints. */
  public DifferentialPair {
    Objects.requireNonNull(positiveNet, "positiveNet cannot be null");
    Objects.requireNonNull(negativeNet, "negativeNet cannot be null");
    Objects.requireNonNull(baseName, "baseName cannot be null");
    if (positiveNet.equals(negativeNet)) {
      throw new IllegalArgumentException(
          "positiveNet and negativeNet cannot be the same net: " + positiveNet.name);
    }
    if (coupledSpacing < 0.0) {
      coupledSpacing = 0.0;
    }
    if (maxSkew < 0.0) {
      maxSkew = 0.0;
    }
  }

  /**
   * Constructs an unconstrained DifferentialPair with the given nets and base name.
   *
   * @param positiveNet positive net
   * @param negativeNet negative net
   * @param baseName logical base name
   */
  public DifferentialPair(Net positiveNet, Net negativeNet, String baseName) {
    this(positiveNet, negativeNet, baseName, 0.0, 0.0);
  }

  /**
   * Returns true if the given net is a member of this differential pair.
   *
   * @param net the net to check
   * @return true if {@code net} is either the positive or negative net
   */
  public boolean contains(Net net) {
    if (net == null) {
      return false;
    }
    return this.positiveNet.equals(net)
        || this.negativeNet.equals(net)
        || this.positiveNet.name.equalsIgnoreCase(net.name)
        || this.negativeNet.name.equalsIgnoreCase(net.name);
  }

  /**
   * Returns the partner net of the given net within this differential pair.
   *
   * @param net the net whose partner is requested
   * @return the complementary net, or null if {@code net} is not part of this pair
   */
  public Net getPartner(Net net) {
    if (net == null) {
      return null;
    }
    if (this.positiveNet.equals(net) || this.positiveNet.name.equalsIgnoreCase(net.name)) {
      return this.negativeNet;
    }
    if (this.negativeNet.equals(net) || this.negativeNet.name.equalsIgnoreCase(net.name)) {
      return this.positiveNet;
    }
    return null;
  }

  /**
   * Returns true if the given net is the positive member of this pair.
   *
   * @param net the net to check
   * @return true if {@code net} is positive
   */
  public boolean isPositive(Net net) {
    return net != null
        && (this.positiveNet.equals(net) || this.positiveNet.name.equalsIgnoreCase(net.name));
  }

  /**
   * Returns true if the given net is the negative member of this pair.
   *
   * @param net the net to check
   * @return true if {@code net} is negative
   */
  public boolean isNegative(Net net) {
    return net != null
        && (this.negativeNet.equals(net) || this.negativeNet.name.equalsIgnoreCase(net.name));
  }

  /** Returns true if a specific coupled spacing constraint is configured for this pair. */
  public boolean hasCoupledSpacing() {
    return this.coupledSpacing > 0.0;
  }

  /** Returns true if a maximum allowable intra-pair skew constraint is configured. */
  public boolean hasMaxSkew() {
    return this.maxSkew > 0.0;
  }

  /**
   * Returns a copy of this differential pair with updated coupled spacing.
   *
   * @param newSpacing target spacing in board units
   * @return updated DifferentialPair
   */
  public DifferentialPair withCoupledSpacing(double newSpacing) {
    return new DifferentialPair(
        this.positiveNet, this.negativeNet, this.baseName, newSpacing, this.maxSkew);
  }

  /**
   * Returns a copy of this differential pair with updated maximum skew.
   *
   * @param newMaxSkew maximum skew in board units
   * @return updated DifferentialPair
   */
  public DifferentialPair withMaxSkew(double newMaxSkew) {
    return new DifferentialPair(
        this.positiveNet, this.negativeNet, this.baseName, this.coupledSpacing, newMaxSkew);
  }
}
