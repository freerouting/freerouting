package app.freerouting.autoroute.scope;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable, primitive-backed scope mask for selective component-scoped routing.
 *
 * <p>All hot-loop membership queries compile to bounds-checked bit shifts on flat {@code long[]}
 * bitsets with zero object allocation.
 */
public final class RoutingScopeMask implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Singleton representing unscoped (full board) routing. */
  public static final RoutingScopeMask UNSCOPED =
      new RoutingScopeMask(
          false,
          new long[0],
          new long[0],
          new long[0],
          new int[0],
          MultiPinScopePolicy.ANCHOR_TO_NEAREST,
          ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP,
          10.0,
          2);

  private final boolean active;
  private final long[] selectedComponentsBits;
  private final long[] anchorPinIdsBits;
  private final long[] scopedNetsBits;
  private final int[] scopedNetNumbers;
  private final MultiPinScopePolicy multiPinPolicy;
  private final ForeignTracePolicy foreignTracePolicy;
  private final double foreignRipupCostMultiplier;
  private final int nudgeOnlyPasses;

  public RoutingScopeMask(
      boolean active,
      long[] selectedComponentsBits,
      long[] anchorPinIdsBits,
      long[] scopedNetsBits,
      int[] scopedNetNumbers,
      MultiPinScopePolicy multiPinPolicy,
      ForeignTracePolicy foreignTracePolicy,
      double foreignRipupCostMultiplier,
      int nudgeOnlyPasses) {
    this.active = active;
    this.selectedComponentsBits =
        selectedComponentsBits != null
            ? Arrays.copyOf(selectedComponentsBits, selectedComponentsBits.length)
            : new long[0];
    this.anchorPinIdsBits =
        anchorPinIdsBits != null
            ? Arrays.copyOf(anchorPinIdsBits, anchorPinIdsBits.length)
            : new long[0];
    this.scopedNetsBits =
        scopedNetsBits != null ? Arrays.copyOf(scopedNetsBits, scopedNetsBits.length) : new long[0];
    this.scopedNetNumbers =
        scopedNetNumbers != null
            ? Arrays.copyOf(scopedNetNumbers, scopedNetNumbers.length)
            : new int[0];
    this.multiPinPolicy =
        multiPinPolicy != null ? multiPinPolicy : MultiPinScopePolicy.ANCHOR_TO_NEAREST;
    this.foreignTracePolicy =
        foreignTracePolicy != null
            ? foreignTracePolicy
            : ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP;
    this.foreignRipupCostMultiplier = Math.max(1.0, foreignRipupCostMultiplier);
    this.nudgeOnlyPasses = Math.max(0, nudgeOnlyPasses);
  }

  public boolean isActive() {
    return this.active;
  }

  /** Checks whether the component with the specified ID is selected in scope. */
  public boolean isComponentSelected(int componentId) {
    if (!this.active) {
      return true;
    }
    int wordIdx = componentId >>> 6;
    return wordIdx >= 0
        && wordIdx < this.selectedComponentsBits.length
        && (this.selectedComponentsBits[wordIdx] & (1L << (componentId & 63))) != 0L;
  }

  /** Checks whether the pin item with the specified ID is an anchor pin of a selected component. */
  public boolean isAnchorPinId(int itemId) {
    if (!this.active) {
      return true;
    }
    int wordIdx = itemId >>> 6;
    return wordIdx >= 0
        && wordIdx < this.anchorPinIdsBits.length
        && (this.anchorPinIdsBits[wordIdx] & (1L << (itemId & 63))) != 0L;
  }

  /** Checks whether the net with the specified net number is touched by a selected component. */
  public boolean isNetInScope(int netNo) {
    if (!this.active) {
      return true;
    }
    int wordIdx = netNo >>> 6;
    return wordIdx >= 0
        && wordIdx < this.scopedNetsBits.length
        && (this.scopedNetsBits[wordIdx] & (1L << (netNo & 63))) != 0L;
  }

  /** Returns the sorted array of net numbers in scope. */
  public int[] getScopedNetNumbers() {
    return Arrays.copyOf(this.scopedNetNumbers, this.scopedNetNumbers.length);
  }

  public MultiPinScopePolicy getMultiPinPolicy() {
    return this.multiPinPolicy;
  }

  public ForeignTracePolicy getForeignTracePolicy() {
    return this.foreignTracePolicy;
  }

  public double getForeignRipupCostMultiplier() {
    return this.foreignRipupCostMultiplier;
  }

  public int getNudgeOnlyPasses() {
    return this.nudgeOnlyPasses;
  }
}
