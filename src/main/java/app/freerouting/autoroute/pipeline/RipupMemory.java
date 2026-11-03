package app.freerouting.autoroute.pipeline;

import app.freerouting.board.model.items.Item;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Remembers which net removed which other net's traces and vias during the autoroute passes, and
 * why: the net that was being routed when the rip-up happened.
 *
 * <p>Without this memory two nets that want the same space can take it from each other pass after
 * pass: net A rips net B to route, B rips A back on its next attempt, and A rips B again; or a
 * board restore replays the same rip-up. Every rip-up is only recorded, which changes nothing. When
 * the same rip-up (A removes B, in a later pass) happens a second time, the memory steps in: B is
 * kept away from A, so its search may not rip A and B is routed after A in the pass. B has to find
 * another way, for example a via to the other layer. Boards that route without such a repeat are
 * routed exactly as before.
 *
 * <p>Sometimes the net that was moved has no other way while the one that moved it has. When B
 * fails {@link #MAX_FAILURES_WHILE_KEPT_AWAY} times while kept away from A, the relation flips: A
 * is kept away from B, and the caller removes A's copper in the way of B's open connections, so B
 * routes first and A looks for the other way. When the flipped relation fails as well, the pair
 * retires and is never restricted again, so the memory cannot hold a net back forever.
 *
 * <p>The memory survives board restores within one autoroute stage, since a restore replays the
 * same contest. Everything is keyed by net number and iterated in net order, so the routing stays
 * deterministic.
 */
final class RipupMemory {

  /**
   * Passes in which a net failed while kept away from another one, before the relation ends. A net
   * with several open pins can fail more than once in a pass; that counts once.
   */
  static final int MAX_FAILURES_WHILE_KEPT_AWAY = 2;

  /** How a relation ended after a routing attempt of the net it keeps away. */
  enum Outcome {
    /** Nothing changed: no relation, or a failure below the limit. */
    NONE,
    /** The kept-away net routed: the relation is done. */
    RESOLVED,
    /** The kept-away net failed too often: the other net is now kept away from it instead. */
    FLIPPED,
    /** The flipped relation failed too: the pair is free again for good. */
    RETIRED
  }

  /** One relation: {@code keptAwayNet} may not rip {@code keptFromNet}. */
  static final class Entry {
    final int keptFromNet;
    final int keptAwayNet;
    final int firstPass;
    final boolean flipped;
    int lastPass;
    int ripupCount;
    int failuresWhileKeptAway;
    int lastFailurePass = -1;

    Entry(int keptFromNet, int keptAwayNet, int pass, boolean flipped) {
      this.keptFromNet = keptFromNet;
      this.keptAwayNet = keptAwayNet;
      this.firstPass = pass;
      this.lastPass = pass;
      this.flipped = flipped;
    }
  }

  /** Active relations: kept-away net -> kept-from net -> entry. */
  private final Map<Integer, Map<Integer, Entry>> active = new TreeMap<>();

  /** Rip-ups seen so far, active or not: ripper net -> ripped net -> pass of the last one. */
  private final Map<Integer, Map<Integer, Integer>> seen = new TreeMap<>();

  /** Unordered pairs (lower net number first) whose relation has flipped once already. */
  private final Set<Long> flippedPairs = new TreeSet<>();

  /** Unordered pairs that may not be restricted again. */
  private final Set<Long> retiredPairs = new TreeSet<>();

  /**
   * Records the items {@code ripperNet} removed while it was routed in pass {@code pass}. A net
   * that {@code ripperNet} already removed in an earlier pass is kept away from it from now on.
   * Returns the nets for which a new relation started, in net order.
   */
  Set<Integer> recordRipup(int ripperNet, Collection<Item> rippedItems, int pass) {
    Set<Integer> rippedNets = new TreeSet<>();
    for (Item item : rippedItems) {
      for (int i = 0; i < item.netCount(); i++) {
        rippedNets.add(item.getNetNumber(i));
      }
    }
    return recordRipupOfNets(ripperNet, rippedNets, pass);
  }

  /** As {@link #recordRipup}, with the nets of the removed items already collected. */
  Set<Integer> recordRipupOfNets(int ripperNet, Collection<Integer> rippedNets, int pass) {
    Set<Integer> started = new TreeSet<>();
    for (int rippedNet : new TreeSet<>(rippedNets)) {
      if (rippedNet == ripperNet || rippedNet <= 0) {
        continue;
      }
      long pair = pairKey(ripperNet, rippedNet);
      if (retiredPairs.contains(pair) || flippedPairs.contains(pair)) {
        continue;
      }
      Map<Integer, Integer> byRipped = seen.computeIfAbsent(ripperNet, k -> new TreeMap<>());
      Integer previousPass = byRipped.put(rippedNet, pass);
      Map<Integer, Entry> byKeptFrom = active.get(rippedNet);
      Entry entry = byKeptFrom == null ? null : byKeptFrom.get(ripperNet);
      if (entry == null) {
        if (previousPass == null || previousPass == pass) {
          // the first rip-up of B by A is only remembered; more items in the same pass are the same
          continue;
        }
        byKeptFrom = active.computeIfAbsent(rippedNet, k -> new TreeMap<>());
        entry = new Entry(ripperNet, rippedNet, pass, false);
        byKeptFrom.put(ripperNet, entry);
        started.add(rippedNet);
      }
      entry.lastPass = pass;
      entry.ripupCount++;
    }
    return started;
  }

  /** Nets whose traces and vias {@code net} may not rip, in net order. */
  Set<Integer> netsNotToRip(int net) {
    Map<Integer, Entry> byKeptFrom = active.get(net);
    if (byKeptFrom == null || byKeptFrom.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(new TreeSet<>(byKeptFrom.keySet()));
  }

  /** True if {@code net} is kept away from at least one other net. */
  boolean isKeptAway(int net) {
    Map<Integer, Entry> byKeptFrom = active.get(net);
    return byKeptFrom != null && !byKeptFrom.isEmpty();
  }

  /**
   * Records the outcome of routing {@code net} while it was kept away from {@code keptFromNet}, in
   * pass {@code pass}. A routed net has found its own way and the relation is done. A failure
   * counts against the relation, at most once per pass; at {@link #MAX_FAILURES_WHILE_KEPT_AWAY} an
   * original relation flips, so that {@code keptFromNet} is kept away from {@code net}, and a
   * flipped one retires.
   */
  Outcome recordOutcome(int net, int keptFromNet, boolean routed, int pass) {
    Map<Integer, Entry> byKeptFrom = active.get(net);
    Entry entry = byKeptFrom == null ? null : byKeptFrom.get(keptFromNet);
    if (entry == null) {
      return Outcome.NONE;
    }
    if (routed) {
      remove(net, keptFromNet);
      return Outcome.RESOLVED;
    }
    if (entry.lastFailurePass == pass) {
      return Outcome.NONE;
    }
    entry.lastFailurePass = pass;
    entry.failuresWhileKeptAway++;
    if (entry.failuresWhileKeptAway < MAX_FAILURES_WHILE_KEPT_AWAY) {
      return Outcome.NONE;
    }
    remove(net, keptFromNet);
    long pair = pairKey(net, keptFromNet);
    if (entry.flipped) {
      retiredPairs.add(pair);
      return Outcome.RETIRED;
    }
    flippedPairs.add(pair);
    active
        .computeIfAbsent(keptFromNet, k -> new TreeMap<>())
        .put(net, new Entry(net, keptFromNet, pass, true));
    return Outcome.FLIPPED;
  }

  boolean isRetired(int netA, int netB) {
    return retiredPairs.contains(pairKey(netA, netB));
  }

  int activeCount() {
    int count = 0;
    for (Map<Integer, Entry> byKeptFrom : active.values()) {
      count += byKeptFrom.size();
    }
    return count;
  }

  private void remove(int keptAwayNet, int keptFromNet) {
    Map<Integer, Entry> byKeptFrom = active.get(keptAwayNet);
    if (byKeptFrom == null) {
      return;
    }
    byKeptFrom.remove(keptFromNet);
    if (byKeptFrom.isEmpty()) {
      active.remove(keptAwayNet);
    }
  }

  private static long pairKey(int netA, int netB) {
    int low = Math.min(netA, netB);
    int high = Math.max(netA, netB);
    return ((long) low << 32) | (high & 0xffffffffL);
  }
}
