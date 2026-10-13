package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.DesignBlocks;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * When the pass loop stops with open connections, the pipeline re-routes the input board under
 * named setting changes and keeps the best result.
 *
 * <p>Ported from freerouting.net (Erik Gavriluk, Experimental Television Center). Attempts are not
 * skipped because of elapsed time: a timeout may stop the job, and it does not choose which
 * strategy runs.
 */
public final class RoutingFallback {

  private RoutingFallback() {}

  /** A named settings change applied to a private copy of the input or incumbent board. */
  public static final class Strategy {
    public final String name;
    public final boolean needsFanout;
    public final boolean continueBest;
    public final boolean fanoutOnly;
    private final Consumer<RouterSettings> apply;

    public Strategy(
        String name,
        boolean needsFanout,
        Consumer<RouterSettings> apply,
        boolean continueBest,
        boolean fanoutOnly) {
      this.name = name;
      this.needsFanout = needsFanout;
      this.apply = apply;
      this.continueBest = continueBest;
      this.fanoutOnly = fanoutOnly;
    }

    public void apply(RouterSettings settings) {
      this.apply.accept(settings);
    }
  }

  /** The strategies a settings value may name, in catalog order. */
  public static final List<Strategy> KNOWN =
      List.of(
          new Strategy("no-neck", false, settings -> settings.setNeckWidthUm(0), false, false),
          new Strategy(
              "continue-fine-neck",
              false,
              settings -> settings.setNeckWidthUm(Math.min(settings.getNeckWidthUm(), 100)),
              true,
              false),
          new Strategy(
              "fanout-retry",
              true,
              settings -> settings.fanout.retryWithoutEscapeWindow = true,
              false,
              true),
          new Strategy(
              "short-escape",
              true,
              settings -> settings.fanout.minEscapeLengthMm = 0.0,
              false,
              true),
          new Strategy(
              "open-escape",
              true,
              settings -> {
                settings.fanout.minEscapeLengthMm = 0.0;
                settings.fanout.maxEscapeLengthMm = 1000.0;
                settings.autorouter.smdNetViaCostFactor = 1.0;
              },
              false,
              false),
          new Strategy(
              "direct-first",
              true,
              settings -> settings.fanout.strategy = "direct_first",
              false,
              false),
          new Strategy(
              "no-fanout", true, settings -> settings.setFanoutEnabled(false), false, false),
          new Strategy("continue", false, settings -> {}, false, false),
          new Strategy(
              "continue-no-neck", false, settings -> settings.setNeckWidthUm(0), false, false));

  /**
   * Strategies that can change this job. Fanout strategies need fanout and at least one SMD pin.
   * Neck strategies apply only while neck-down is on, and the fine neck only while the neck is
   * wider than 100 um.
   */
  public static List<Strategy> applicable(
      List<Strategy> strategies, RouterSettings settings, boolean fanoutRuns) {
    boolean necking = settings.getNeckWidthUm() > 0;
    List<Strategy> result = new ArrayList<>();
    for (Strategy strategy : strategies) {
      if (strategy.needsFanout && !fanoutRuns) {
        continue;
      }
      if ("no-neck".equals(strategy.name) && !necking) {
        continue;
      }
      if ("continue-fine-neck".equals(strategy.name) && settings.getNeckWidthUm() <= 100) {
        continue;
      }
      if ("continue-no-neck".equals(strategy.name) && !necking) {
        continue;
      }
      result.add(strategy);
    }
    return result;
  }

  /**
   * The strategies the settings name, in order and without repeats. Unknown names are reported and
   * skipped. {@code none} anywhere in the list disables the fallback.
   */
  public static List<Strategy> parse(RouterSettings settings, Consumer<String> unknown) {
    List<Strategy> result = new ArrayList<>();
    String spec = settings.autorouter != null ? settings.autorouter.fallbackStrategies : null;
    if (spec == null || spec.isBlank()) {
      return result;
    }
    for (String raw : spec.split(",")) {
      String name = raw.trim();
      if (name.isEmpty()) {
        continue;
      }
      if ("none".equalsIgnoreCase(name)) {
        return new ArrayList<>();
      }
      Strategy known = find(name);
      if (known == null) {
        if (unknown != null) {
          unknown.accept(name);
        }
      } else if (!result.contains(known)) {
        result.add(known);
      }
    }
    return result;
  }

  public static Strategy find(String name) {
    for (Strategy strategy : KNOWN) {
      if (strategy.name.equalsIgnoreCase(name)) {
        return strategy;
      }
    }
    return null;
  }

  /** Whether a pass loop that stopped for this reason may be followed by another attempt. */
  public static boolean isRetryable(BatchAutorouter.StopReason reason) {
    return reason == BatchAutorouter.StopReason.STAGNATION
        || reason == BatchAutorouter.StopReason.NO_IMPROVEMENT
        || reason == BatchAutorouter.StopReason.COMPLETED;
  }

  /** Human-readable stop reason for logs. */
  public static String describe(BatchAutorouter.StopReason reason) {
    if (reason == null) {
      return "an unknown stop";
    }
    return switch (reason) {
      case COMPLETED -> "completed";
      case MAX_PASSES -> "pass limit";
      case STAGNATION -> "stagnation";
      case NO_IMPROVEMENT -> "no improvement";
      case TIMED_OUT -> "timeout";
      case CANCELLED -> "cancelled";
      case REPEATED -> "a repeat of an earlier pass loop";
    };
  }

  /** One attempt, ranked by fewer opens, then fewer new violations, then the higher score. */
  public static final class Outcome {
    public final String strategy;
    public final int unrouted;
    public final int newOrChanged;
    public final int violations;
    public final float score;
    public final int designBlocked;

    public Outcome(
        String strategy,
        int unrouted,
        int newOrChanged,
        int violations,
        float score,
        int designBlocked) {
      this.strategy = strategy;
      this.unrouted = unrouted;
      this.newOrChanged = newOrChanged;
      this.violations = violations;
      this.score = score;
      this.designBlocked = designBlocked;
    }

    public boolean isCompleteAndClean() {
      return this.unrouted - this.designBlocked == 0 && this.newOrChanged == 0;
    }

    public static Outcome of(String strategy, RoutingBoard board, RouterSettings scoring) {
      return of(strategy, board, scoring, null);
    }

    /**
     * Measures the board. A null baseline counts every violation as new. Design-blocked airlines
     * are counted once here, not on every pass statistic.
     */
    public static Outcome of(
        String strategy, RoutingBoard board, RouterSettings scoring, ClearanceBaseline incumbent) {
      BoardStatistics statistics = new BoardStatistics(board);
      int incomplete =
          statistics.connections.incompleteCount != null
              ? statistics.connections.incompleteCount
              : 0;
      int blocked = 0;
      if (incomplete > 0) {
        blocked =
            DesignBlocks.blockedAirlines(
                    Arrays.asList(new DesignRulesChecker(board, null).getAllAirlines()))
                .size();
        statistics.connections.designBlockedCount = blocked > 0 ? blocked : null;
      }
      int violationCount =
          statistics.clearanceViolations.totalCount != null
              ? statistics.clearanceViolations.totalCount
              : 0;
      int changed =
          violationCount == 0
              ? 0
              : incumbent == null
                  ? violationCount
                  : incumbent.countNewOrChanged(violationsOf(board));
      statistics.clearanceViolations.newOrChangedCount = changed;
      return new Outcome(
          strategy,
          incomplete,
          changed,
          violationCount,
          statistics.getRouterScore(scoring),
          blocked);
    }

    /** Whether a better candidate preserves the incumbent's clearance legality. */
    public static boolean canReplace(
        Outcome candidate, Outcome incumbent, int changedSinceIncumbent) {
      return changedSinceIncumbent == 0
          && candidate.newOrChanged <= incumbent.newOrChanged
          && compare(candidate, incumbent) < 0;
    }

    /** Negative when {@code a} is better. */
    public static int compare(Outcome a, Outcome b) {
      int order = Integer.compare(a.unrouted, b.unrouted);
      if (order == 0) {
        order = Integer.compare(a.newOrChanged, b.newOrChanged);
      }
      if (order == 0) {
        order = Integer.compare(a.violations, b.violations);
      }
      if (order == 0) {
        order = Float.compare(b.score, a.score);
      }
      return order;
    }

    @Override
    public String toString() {
      String blocked =
          this.designBlocked > 0 ? " (" + this.designBlocked + " blocked by the design)" : "";
      return this.unrouted
          + " unrouted"
          + blocked
          + ", "
          + this.newOrChanged
          + " new violation"
          + (this.newOrChanged == 1 ? "" : "s")
          + " ("
          + this.violations
          + " total), score "
          + String.format(Locale.US, "%.2f", this.score);
    }
  }

  public static ClearanceBaseline captureClearance(RoutingBoard board, int violations) {
    if (violations == 0) {
      return null;
    }
    return new ClearanceBaseline(violationsOf(board));
  }

  public static int newOrChangedClearance(
      RoutingBoard candidate, int violations, ClearanceBaseline incumbent) {
    if (violations == 0) {
      return 0;
    }
    if (incumbent == null) {
      return violations;
    }
    return incumbent.countNewOrChanged(violationsOf(candidate));
  }

  private static Collection<ClearanceViolation> violationsOf(RoutingBoard board) {
    return new DesignRulesChecker(board, null).getAllClearanceViolations();
  }
}
