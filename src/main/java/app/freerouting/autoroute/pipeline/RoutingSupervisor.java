package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chooses exploration attempts after the opening fallback. Each kind is tried once, then the kind
 * that has closed the most opens per try. Later tries of a via kind multiply the via cost, and
 * later neighbourhood tries widen the rip.
 *
 * <p>Ported from freerouting.net (Erik Gavriluk, Experimental Television Center). Exploration runs
 * only when {@code router.autorouter.explore_patience} is above zero.
 */
public final class RoutingSupervisor {

  private final Map<String, Stats> attemptStats = new LinkedHashMap<>();
  private int attemptsWithoutGain;

  /** One exploration attempt. {@code tryNumber} starts at 1. */
  public record Attempt(String kind, String name, int seed, int tryNumber) {}

  public static boolean explores(RouterSettings settings) {
    Integer patience = settings.autorouter != null ? settings.autorouter.explorePatience : null;
    return patience != null && patience > 0;
  }

  public boolean explorationExhausted(RouterSettings settings) {
    int patience =
        settings.autorouter != null && settings.autorouter.explorePatience != null
            ? settings.autorouter.explorePatience
            : 0;
    return this.attemptsWithoutGain >= patience;
  }

  /** Via-cost multiplier of a kind's nth try: 2, then 4, then 8. */
  public static int viaFactor(int attempt) {
    int clamped = Math.min(Math.max(attempt, 1), 3);
    return 1 << clamped;
  }

  /**
   * Fallback strategies an exploration attempt runs: the job's strategies that {@code
   * explore_fallback_strategies} names, in the job's order. An empty or blank filter keeps the
   * job's list. A filter that names none of them disables fallback.
   */
  public static String explorationFallback(RouterSettings jobSettings) {
    String keep =
        jobSettings.autorouter != null ? jobSettings.autorouter.exploreFallbackStrategies : null;
    if (keep == null || keep.isBlank()) {
      return jobSettings.autorouter != null ? jobSettings.autorouter.fallbackStrategies : null;
    }
    List<String> names = new ArrayList<>();
    for (String raw : keep.split(",")) {
      String name = raw.trim();
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    List<String> kept = new ArrayList<>();
    for (RoutingFallback.Strategy strategy : RoutingFallback.parse(jobSettings, null)) {
      for (String name : names) {
        if (strategy.name.equalsIgnoreCase(name)) {
          kept.add(strategy.name);
          break;
        }
      }
    }
    return kept.isEmpty() ? "none" : String.join(",", kept);
  }

  /** The next attempt for this board. {@code round} is the attempt seed. */
  public Attempt nextAttempt(int round, RouterSettings jobSettings, BasicBoard board) {
    List<String> applicable = applicableKinds(jobSettings, board);
    String choice = null;
    for (String kind : applicable) {
      if (!this.attemptStats.containsKey(kind)) {
        choice = kind;
        break;
      }
    }
    if (choice == null) {
      int total = 0;
      for (Stats stats : this.attemptStats.values()) {
        total += stats.tries;
      }
      int totalTries = Math.max(2, total);
      double bestScore = Double.NEGATIVE_INFINITY;
      for (String kind : applicable) {
        Stats stats = this.attemptStats.get(kind);
        double score =
            (stats.closed + 0.5) / stats.tries + Math.sqrt(2 * Math.log(totalTries) / stats.tries);
        if (score > bestScore) {
          bestScore = score;
          choice = kind;
        }
      }
    }
    int attempt = this.attemptStats.getOrDefault(choice, new Stats(0, 0)).tries + 1;
    String name = choice + " (seed " + round + (attempt > 1 ? ", try " + attempt : "") + ")";
    return new Attempt(choice, name, round, attempt);
  }

  /** Applies the attempt to a clone of the job settings. Does not rip the board. */
  public static void apply(Attempt attempt, RouterSettings jobSettings, RouterSettings settings) {
    if (settings.autorouter != null) {
      settings.autorouter.itemOrderSeed = attempt.seed;
      settings.autorouter.explorePatience = 0;
      settings.autorouter.fallbackStrategies = explorationFallback(jobSettings);
    }
    switch (attempt.kind) {
      case "seed" -> {
        if (settings.fanout != null) {
          boolean fanout = attempt.seed % 2 == 1 && jobSettings.isFanoutEnabled();
          settings.fanout.enabled = fanout;
        }
      }
      case "neighbourhood" -> {
        // The rip is applied to the board copy by the caller, using the try number.
      }
      case "neck-100um" -> settings.setNeckWidthUm(100);
      case "vias-cheaper" ->
          settings.setViaCosts(
              Math.max(1, jobSettings.getViaCosts() / viaFactor(attempt.tryNumber)));
      case "vias-dearer" ->
          settings.setViaCosts(jobSettings.getViaCosts() * viaFactor(attempt.tryNumber));
      default -> {
        // retry-plan keeps the job settings plus the item-order seed.
      }
    }
  }

  public void attemptDone(Attempt attempt, int openBefore, int openAfter, boolean kept) {
    Stats stats = this.attemptStats.getOrDefault(attempt.kind, new Stats(0, 0));
    int gain = kept ? Math.max(0, openBefore - openAfter) : 0;
    this.attemptStats.put(attempt.kind, new Stats(stats.tries + 1, stats.closed + gain));
    this.attemptsWithoutGain = gain > 0 ? 0 : this.attemptsWithoutGain + 1;
  }

  public static boolean residual(RouterSettings settings, int open) {
    int maxOpen =
        settings.autorouter != null && settings.autorouter.residualRepairMaxOpen != null
            ? settings.autorouter.residualRepairMaxOpen
            : 0;
    return open > 0 && open <= maxOpen;
  }

  private static List<String> applicableKinds(RouterSettings jobSettings, BasicBoard board) {
    List<String> kinds = new ArrayList<>();
    kinds.add("seed");
    kinds.add("retry-plan");
    kinds.add("neighbourhood");
    if (neckApplies(jobSettings, board)) {
      kinds.add("neck-100um");
    }
    kinds.add("vias-cheaper");
    kinds.add("vias-dearer");
    return kinds;
  }

  private static boolean neckApplies(RouterSettings jobSettings, BasicBoard board) {
    if (jobSettings.getNeckWidthUm() <= 100 || board == null) {
      return false;
    }
    double halfUm = board.rules.getMinAllowedTraceHalfWidth();
    double resolution =
        board.communication.getResolution(app.freerouting.board.model.structure.Unit.UM);
    return halfUm <= 50.0 * resolution;
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "attemptsWithoutGain=%d", this.attemptsWithoutGain);
  }

  private record Stats(int tries, int closed) {}
}
