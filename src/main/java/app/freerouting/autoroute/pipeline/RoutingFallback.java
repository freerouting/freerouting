package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Multi-strategy fallback routing: when the pass loop gives up with unrouted connections or new
 * clearance violations, the pipeline re-routes the input board with alternative settings and keeps
 * the best result.
 */
public final class RoutingFallback {

  private RoutingFallback() {}

  /** The incumbent's immutable violation identities; a clean board needs no additional scan. */
  public static ClearanceBaseline captureClearance(RoutingBoard board, int violations) {
    return violations == 0
        ? null
        : new ClearanceBaseline(new DesignRulesChecker(board, null).getAllClearanceViolations());
  }

  /** Violations absent from, or changed since, the incumbent. Null denotes a clean incumbent. */
  public static int newOrChangedClearance(
      RoutingBoard candidate, int violations, ClearanceBaseline incumbent) {
    return violations == 0
        ? 0
        : (incumbent != null
            ? incumbent.countNewOrChanged(
                new DesignRulesChecker(candidate, null).getAllClearanceViolations())
            : violations);
  }

  /** A named settings change applied to a private input-board copy. */
  public static final class Strategy {
    public final String name;
    public final boolean needsFanout;
    public final Consumer<RouterSettings> apply;
    public final boolean continueBest;

    public Strategy(String name, boolean needsFanout, Consumer<RouterSettings> apply) {
      this(name, needsFanout, apply, false);
    }

    public Strategy(
        String name, boolean needsFanout, Consumer<RouterSettings> apply, boolean continueBest) {
      this.name = name;
      this.needsFanout = needsFanout;
      this.apply = apply;
      this.continueBest = continueBest;
    }

    public String getName() {
      return name;
    }

    public boolean isNeedsFanout() {
      return needsFanout;
    }

    public boolean isContinueBest() {
      return continueBest;
    }

    public void apply(RouterSettings settings) {
      this.apply.accept(settings);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Strategy other)) {
        return false;
      }
      return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  /** The strategies a settings value may name. */
  public static final List<Strategy> KNOWN =
      List.of(
          new Strategy("no-neck", false, s -> s.neckWidthUm = 0.0),
          new Strategy(
              "continue-fine-neck",
              false,
              s -> s.neckWidthUm = Math.min(s.getNeckWidthUm(), 100.0),
              true),
          new Strategy(
              "continue-via-in-pad",
              false,
              s -> {
                if (s.autorouter != null) {
                  s.autorouter.viaInPad = true;
                  s.autorouter.viaCentreOfGravity = true;
                }
              },
              true),
          new Strategy(
              "fanout-retry",
              true,
              s -> {
                if (s.fanout != null) {
                  s.fanout.retryWithoutEscapeWindow = true;
                }
              }),
          new Strategy(
              "short-escape",
              true,
              s -> {
                if (s.fanout != null) {
                  s.fanout.minEscapeLengthMm = 0.0;
                }
              }),
          new Strategy(
              "open-escape",
              true,
              s -> {
                if (s.fanout != null) {
                  s.fanout.minEscapeLengthMm = 0.0;
                  s.fanout.maxEscapeLengthMm = 1000.0;
                }
                if (s.autorouter != null) {
                  s.autorouter.smdNetViaCostFactor = 1.0;
                }
              }),
          new Strategy(
              "no-fanout",
              true,
              s -> {
                if (s.fanout != null) {
                  s.fanout.enabled = false;
                }
              }));

  /**
   * The strategies that can change this job's result. A fanout-only strategy needs the fanout stage
   * to run: fanout enabled and a board with SMD pins to escape.
   */
  public static List<Strategy> applicable(
      Iterable<Strategy> strategies, RouterSettings settings, RoutingBoard board) {
    boolean fanoutRuns = settings.isFanoutEnabled() && !board.getSmdPins().isEmpty();
    boolean necking = settings.getNeckWidthUm() > 0;
    List<Strategy> result = new ArrayList<>();
    for (Strategy s : strategies) {
      if ((!s.needsFanout || fanoutRuns)
          && (!"no-neck".equals(s.name) || necking)
          && (!"continue-fine-neck".equals(s.name) || settings.getNeckWidthUm() > 100)) {
        result.add(s);
      }
    }
    return result;
  }

  /**
   * The strategies the settings name, in order and without repeats. Unknown names go to {@code
   * unknown} and are skipped; {@code none} anywhere in the list disables the fallback.
   */
  public static List<Strategy> parse(RouterSettings settings, Consumer<String> unknown) {
    List<Strategy> result = new ArrayList<>();
    String spec = settings.autorouter != null ? settings.autorouter.fallbackStrategies : null;
    if (spec == null || spec.trim().isEmpty()) {
      return result;
    }
    for (String token : spec.split(",")) {
      String name = token.trim();
      if (name.isEmpty()) {
        continue;
      }
      if ("none".equalsIgnoreCase(name)) {
        return Collections.emptyList();
      }
      Strategy match = null;
      for (Strategy known : KNOWN) {
        if (known.name.equalsIgnoreCase(name)) {
          match = known;
          break;
        }
      }
      if (match == null) {
        if (unknown != null) {
          unknown.accept(name);
        }
      } else if (!result.contains(match)) {
        result.add(match);
      }
    }
    return result;
  }

  public static List<Strategy> parse(RouterSettings settings) {
    return parse(settings, null);
  }

  public static String knownNames() {
    return KNOWN.stream().map(Strategy::getName).collect(Collectors.joining(", "));
  }

  /**
   * Whether a pass loop that ended for {@code reason} may be followed by another attempt: only when
   * the router itself gave up.
   */
  public static boolean isRetryable(BatchAutorouter.StopReason reason) {
    return reason == BatchAutorouter.StopReason.STAGNATION
        || reason == BatchAutorouter.StopReason.NO_IMPROVEMENT
        || reason == BatchAutorouter.StopReason.COMPLETED;
  }

  /** Human-readable stop reason for logs. */
  public static String describe(BatchAutorouter.StopReason reason) {
    if (reason == null) {
      return "null";
    }
    return switch (reason) {
      case COMPLETED -> "completed";
      case MAX_PASSES -> "pass limit";
      case STAGNATION -> "stagnation";
      case NO_IMPROVEMENT -> "no improvement";
      case TIMED_OUT -> "timeout";
      case WORK_LIMIT -> "work limit";
      case CANCELLED -> "cancelled";
    };
  }

  /** Manifest name of a stop reason. */
  public static String manifestName(BatchAutorouter.StopReason reason) {
    if (reason == null) {
      return "null";
    }
    return switch (reason) {
      case MAX_PASSES -> "max_passes";
      case NO_IMPROVEMENT -> "no_improvement";
      case TIMED_OUT -> "timed_out";
      case WORK_LIMIT -> "work_limit";
      default -> reason.name().toLowerCase(Locale.ROOT);
    };
  }

  /**
   * One attempt's result, ranked: fewest unrouted connections, then fewest new or changed clearance
   * violations, then fewest violations, then the higher router score.
   */
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

    public Outcome(String strategy, int unrouted, int newOrChanged, int violations, float score) {
      this(strategy, unrouted, newOrChanged, violations, score, 0);
    }

    /** Fully routed without new or changed clearance violations. */
    public boolean isCompleteAndClean() {
      return (unrouted - designBlocked == 0) && newOrChanged == 0;
    }

    /** Measures {@code board}. */
    public static Outcome of(String strategy, RoutingBoard board, RouterSettings scoring) {
      BoardStatistics statistics = new BoardStatistics(board);
      int violations =
          statistics.clearanceViolations.totalCount != null
              ? statistics.clearanceViolations.totalCount
              : 0;
      int unrouted =
          statistics.connections.incompleteCount != null
              ? statistics.connections.incompleteCount
              : 0;
      int newOrChanged =
          statistics.clearanceViolations.newOrChangedCount != null
              ? statistics.clearanceViolations.newOrChangedCount
              : violations;
      float score = statistics.getRouterScore(scoring);
      int designBlocked =
          statistics.connections.designBlockedCount != null
              ? statistics.connections.designBlockedCount
              : 0;
      return new Outcome(strategy, unrouted, newOrChanged, violations, score, designBlocked);
    }

    /** Whether an otherwise better candidate preserves the incumbent's clearance legality. */
    public static boolean canReplace(
        Outcome candidate, Outcome incumbent, int newOrChangedSinceIncumbent) {
      return newOrChangedSinceIncumbent == 0
          && candidate.newOrChanged <= incumbent.newOrChanged
          && compare(candidate, incumbent) < 0;
    }

    /** Ranks results after clearance admission: negative when {@code a} is better. */
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
      return unrouted
          + " unrouted"
          + (designBlocked > 0 ? " (" + designBlocked + " blocked by the design)" : "")
          + ", "
          + newOrChanged
          + " new violation"
          + (newOrChanged == 1 ? "" : "s")
          + " ("
          + violations
          + " total), score "
          + String.format(Locale.US, "%.2f", score);
    }
  }
}
