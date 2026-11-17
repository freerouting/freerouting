package app.freerouting.autoroute.scope;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.rules.Net;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Thread-confined coordinator managing selective component-scoped routing state on a board.
 *
 * <p>Coordinates membership masking, trace provenance inheritance across push-and-shove, multi-pin
 * destination set filtering, and post-route optimization guards.
 */
public class RoutingScopeContext implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Unscoped singleton used for standard full-board routing. */
  public static final RoutingScopeContext UNSCOPED =
      new RoutingScopeContext(RoutingScopeMask.UNSCOPED, false, new long[0], new int[0]);

  /** No-op singleton used when requested scope designators resolve to zero routable pins. */
  public static final RoutingScopeContext EMPTY_NO_OP =
      new RoutingScopeContext(RoutingScopeMask.UNSCOPED, true, new long[0], new int[0]);

  private final RoutingScopeMask mask;
  private final boolean noOp;
  private long[] foreignBaselineItemBits;
  private final int[] baselineConnectedSetCounts;
  private long[] activeAnchorShieldBits;
  private boolean inForeignRecoverySubPass;

  public RoutingScopeContext(
      RoutingScopeMask mask,
      boolean noOp,
      long[] foreignBaselineItemBits,
      int[] baselineConnectedSetCounts) {
    this.mask = mask != null ? mask : RoutingScopeMask.UNSCOPED;
    this.noOp = noOp;
    this.foreignBaselineItemBits =
        foreignBaselineItemBits != null
            ? Arrays.copyOf(foreignBaselineItemBits, foreignBaselineItemBits.length)
            : new long[0];
    this.baselineConnectedSetCounts =
        baselineConnectedSetCounts != null
            ? Arrays.copyOf(baselineConnectedSetCounts, baselineConnectedSetCounts.length)
            : new int[0];
    this.activeAnchorShieldBits = new long[0];
    this.inForeignRecoverySubPass = false;
  }

  public boolean isActive() {
    return this.mask.isActive() && !this.noOp;
  }

  public boolean isNoOp() {
    return this.noOp;
  }

  public RoutingScopeMask getMask() {
    return this.mask;
  }

  public boolean isComponentSelected(int componentId) {
    return this.mask.isComponentSelected(componentId);
  }

  public boolean isAnchorPinId(int itemId) {
    return this.mask.isAnchorPinId(itemId);
  }

  public boolean isNetInScope(int netNo) {
    return this.mask.isNetInScope(netNo);
  }

  public int[] getScopedNetNumbers() {
    return this.mask.getScopedNetNumbers();
  }

  public MultiPinScopePolicy getMultiPinPolicy() {
    return this.mask.getMultiPinPolicy();
  }

  public ForeignTracePolicy getForeignTracePolicy() {
    return this.mask.getForeignTracePolicy();
  }

  public double getForeignRipupCostMultiplier() {
    return this.mask.getForeignRipupCostMultiplier();
  }

  public int getNudgeOnlyPasses() {
    return this.mask.getNudgeOnlyPasses();
  }

  public int getBaselineConnectedSetCount(int netNo) {
    if (netNo >= 0 && netNo < this.baselineConnectedSetCounts.length) {
      return this.baselineConnectedSetCounts[netNo];
    }
    return 0;
  }

  public boolean isInForeignRecoverySubPass() {
    return this.inForeignRecoverySubPass;
  }

  public void setInForeignRecoverySubPass(boolean inRecovery) {
    this.inForeignRecoverySubPass = inRecovery;
  }

  /**
   * Propagates trace provenance when a trace is split, shoved, or combined.
   *
   * <p>If {@code oldIdNo} was marked as foreign baseline copper, {@code newIdNo} inherits the mark,
   * ensuring that nudged foreign traces never lose Tier 1/Tier 2 protection.
   */
  public synchronized void inheritProvenance(int oldIdNo, int newIdNo) {
    if (!this.isActive() || oldIdNo <= 0 || newIdNo <= 0) {
      return;
    }
    int oldWord = oldIdNo >>> 6;
    if (oldWord < this.foreignBaselineItemBits.length
        && (this.foreignBaselineItemBits[oldWord] & (1L << (oldIdNo & 63))) != 0L) {
      int newWord = newIdNo >>> 6;
      if (newWord >= this.foreignBaselineItemBits.length) {
        this.foreignBaselineItemBits =
            Arrays.copyOf(
                this.foreignBaselineItemBits,
                Math.max(newWord + 1, this.foreignBaselineItemBits.length * 2));
      }
      this.foreignBaselineItemBits[newWord] |= (1L << (newIdNo & 63));
    }
  }

  /** Captures a snapshot of the current foreign baseline bits array. */
  public synchronized long[] snapshotForeignBaselineBits() {
    return Arrays.copyOf(this.foreignBaselineItemBits, this.foreignBaselineItemBits.length);
  }

  /** Restores the foreign baseline bits array from a snapshot. */
  public synchronized void restoreForeignBaselineBits(long[] snapshot) {
    this.foreignBaselineItemBits =
        snapshot != null ? Arrays.copyOf(snapshot, snapshot.length) : new long[0];
  }

  /**
   * Checks whether the specified item is a protected foreign item (either belonging to a net
   * outside the active scope, or recorded in baseline copper on a shared net).
   */
  public boolean isForeignProtectedItem(Item item) {
    if (!this.isActive() || item == null) {
      return false;
    }
    int netNo = item.netCount() > 0 ? item.getNetNumber(0) : 0;
    if (netNo <= 0) {
      return false;
    }
    if (!this.mask.isNetInScope(netNo)) {
      return true;
    }
    int id = item.getId();
    int wordIdx = id >>> 6;
    return wordIdx >= 0
        && wordIdx < this.foreignBaselineItemBits.length
        && (this.foreignBaselineItemBits[wordIdx] & (1L << (id & 63))) != 0L;
  }

  /** Checks whether the specified item is currently shielded as an active anchor trace. */
  public boolean isAnchorShielded(int itemId) {
    if (!this.isActive() || itemId <= 0) {
      return false;
    }
    int wordIdx = itemId >>> 6;
    return wordIdx >= 0
        && wordIdx < this.activeAnchorShieldBits.length
        && (this.activeAnchorShieldBits[wordIdx] & (1L << (itemId & 63))) != 0L;
  }

  /** Shields newly inserted anchor traces during foreign net recovery sub-passes. */
  public synchronized void shieldAnchorItem(int itemId) {
    if (itemId <= 0) {
      return;
    }
    int wordIdx = itemId >>> 6;
    if (wordIdx >= this.activeAnchorShieldBits.length) {
      this.activeAnchorShieldBits =
          Arrays.copyOf(
              this.activeAnchorShieldBits,
              Math.max(wordIdx + 1, this.activeAnchorShieldBits.length * 2));
    }
    this.activeAnchorShieldBits[wordIdx] |= (1L << (itemId & 63));
  }

  /** Clears temporary anchor shields and recovery sub-pass mode. */
  public synchronized void clearAnchorShieldAndRecoveryMode() {
    Arrays.fill(this.activeAnchorShieldBits, 0L);
    this.inForeignRecoverySubPass = false;
  }

  /** Determines whether an item should be enqueued into the autorouter work queue. */
  public boolean shouldEnqueueItem(BasicBoard board, int netNo, Item item) {
    if (!this.isActive()) {
      return true;
    }
    if (!this.mask.isNetInScope(netNo)) {
      return false;
    }

    Net net = board.rules.nets.get(netNo);
    boolean containsPlane = net != null && net.containsPlane();

    Collection<Item> connectedSet = item.getConnectedSet(netNo);
    boolean containsAnchorPin = false;
    boolean touchesPlane = false;

    for (Item member : connectedSet) {
      if (member instanceof Pin pin && this.mask.isAnchorPinId(pin.getId())) {
        containsAnchorPin = true;
      }
      if (member instanceof ConductionArea) {
        touchesPlane = true;
      }
    }

    // Power/ground plane net: only route pins belonging to selected components,
    // and stop as soon as the connected set reaches the plane.
    if (containsPlane) {
      return containsAnchorPin && !touchesPlane;
    }

    // Signal net:
    MultiPinScopePolicy policy = this.mask.getMultiPinPolicy();
    if (policy == MultiPinScopePolicy.FULL_NET) {
      return true;
    }

    if (!containsAnchorPin) {
      return false;
    }

    if (policy == MultiPinScopePolicy.MUTUAL_ONLY) {
      boolean otherAnchorExists = false;
      for (Collection<Item> otherSet : board.getConnectedSets(netNo)) {
        if (otherSet != connectedSet) {
          for (Item member : otherSet) {
            if (member instanceof Pin pin && this.mask.isAnchorPinId(pin.getId())) {
              otherAnchorExists = true;
              break;
            }
          }
        }
      }
      return otherAnchorExists;
    }

    // ANCHOR_TO_NEAREST:
    boolean containsExternal = false;
    int anchorCountInNet = 0;
    int anchorsInThisSet = 0;
    for (Item member : connectedSet) {
      if (member instanceof Pin pin && this.mask.isAnchorPinId(pin.getId())) {
        anchorsInThisSet++;
      } else {
        containsExternal = true;
      }
    }
    for (Collection<Item> anySet : board.getConnectedSets(netNo)) {
      for (Item member : anySet) {
        if (member instanceof Pin pin && this.mask.isAnchorPinId(pin.getId())) {
          anchorCountInNet++;
        }
      }
    }
    if (anchorCountInNet <= 1 && containsExternal) {
      return false;
    }
    if (anchorCountInNet > 1 && anchorsInThisSet == anchorCountInNet) {
      return false;
    }
    return true;
  }

  /** Filters the destination items for a connection attempt based on the active scoping policy. */
  public Collection<Item> filterDestinationSet(
      BasicBoard board, int netNo, Collection<Item> startSet, Collection<Item> destSet) {
    if (!this.isActive() || destSet == null || destSet.isEmpty()) {
      return destSet;
    }

    Net net = board.rules.nets.get(netNo);
    boolean containsPlane = net != null && net.containsPlane();
    if (containsPlane) {
      // Connect to the plane or any plane-connected copper
      List<Item> planeTargets = new ArrayList<>();
      for (Item item : destSet) {
        if (item instanceof ConductionArea) {
          planeTargets.add(item);
        }
      }
      return !planeTargets.isEmpty() ? planeTargets : destSet;
    }

    MultiPinScopePolicy policy = this.mask.getMultiPinPolicy();
    if (policy == MultiPinScopePolicy.MUTUAL_ONLY) {
      // Only target other anchor pins or copper attached to other anchor pins
      List<Item> anchorTargets = new ArrayList<>();
      for (Item dest : destSet) {
        for (Item member : dest.getConnectedSet(netNo)) {
          if (member instanceof Pin pin && this.mask.isAnchorPinId(pin.getId())) {
            anchorTargets.add(dest);
            break;
          }
        }
      }
      return anchorTargets;
    }

    // ANCHOR_TO_NEAREST / FULL_NET: all valid unconnected destinations are eligible
    return destSet;
  }

  /** Checks whether an item is eligible for post-route optimization rip-up. */
  public boolean isItemOptimizable(Item item) {
    if (!this.isActive()) {
      return item != null && !item.isUserFixed() && !item.isShoveFixed();
    }
    if (item == null || item.isUserFixed() || item.isShoveFixed()) {
      return false;
    }
    int netNo = item.netCount() > 0 ? item.getNetNumber(0) : 0;
    if (netNo <= 0 || !this.mask.isNetInScope(netNo)) {
      return false;
    }
    return !this.isForeignProtectedItem(item);
  }

  /** Counts unsatisfied scoped connections on the board for the optimizer pre-flight guard. */
  public int countUnsatisfiedScopedConnections(RoutingBoard board) {
    if (!this.isActive()) {
      return board.routingLedger().incompleteCount();
    }
    int unsatisfied = 0;
    for (int netNo : this.mask.getScopedNetNumbers()) {
      Collection<Collection<Item>> connectedSets = board.getConnectedSets(netNo);
      if (connectedSets.size() <= 1) {
        continue;
      }
      Net net = board.rules.nets.get(netNo);
      boolean containsPlane = net != null && net.containsPlane();

      for (Collection<Item> set : connectedSets) {
        boolean hasAnchor = false;
        boolean hasPlane = false;
        for (Item item : set) {
          if (item instanceof Pin pin && this.mask.isAnchorPinId(pin.getId())) {
            hasAnchor = true;
          }
          if (item instanceof ConductionArea) {
            hasPlane = true;
          }
        }
        if (hasAnchor) {
          if (containsPlane) {
            if (!hasPlane) {
              unsatisfied++;
            }
          } else {
            unsatisfied++;
          }
        }
      }
    }
    return unsatisfied;
  }

  /** Creates a thread-confined deep copy for multi-threaded optimizer runs. */
  public RoutingScopeContext deepCopy() {
    if (!this.isActive()) {
      return this;
    }
    RoutingScopeContext copy =
        new RoutingScopeContext(
            this.mask,
            this.noOp,
            Arrays.copyOf(this.foreignBaselineItemBits, this.foreignBaselineItemBits.length),
            Arrays.copyOf(this.baselineConnectedSetCounts, this.baselineConnectedSetCounts.length));
    copy.activeAnchorShieldBits =
        Arrays.copyOf(this.activeAnchorShieldBits, this.activeAnchorShieldBits.length);
    copy.inForeignRecoverySubPass = this.inForeignRecoverySubPass;
    return copy;
  }
}
