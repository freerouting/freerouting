package app.freerouting.board.physics;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.settings.PhysicsSettings;

/**
 * DRC-guarded, deterministic post-route physics optimization pass operating on a thread-confined
 * {@link RoutingBoard}.
 */
public final class PhysicsOptimizationPass {

  /** Default maximum relaxation iterations when none is configured. */
  public static final int DEFAULT_MAX_ITERATIONS = 8;

  /** Structured execution metrics returned by {@link #run}. */
  public record Result(
      double initialEnergy,
      double finalEnergy,
      int iterations,
      int triedMoves,
      int acceptedMoves,
      long elapsedNanos) {}

  private PhysicsOptimizationPass() {}

  /**
   * Executes the physics pass on {@code board} using {@link IdentityPhysicsRule}.
   *
   * <p>Execution is deterministic and never branches algorithmic decisions on elapsed wall-clock
   * time. Cancellation is checked via {@code stoppable} and thread interruption.
   *
   * @param board thread-confined routing board
   * @param settings physics settings (nullable)
   * @param stoppable cooperative cancellation handle (nullable)
   * @return execution metrics
   */
  public static Result run(RoutingBoard board, PhysicsSettings settings, Stoppable stoppable) {
    double identityWeight =
        (settings != null && settings.identityWeight != null) ? settings.identityWeight : 1.0;
    return run(board, settings, PhysicsEvaluator.identity(identityWeight), stoppable);
  }

  /**
   * Executes the physics pass on {@code board} with a caller-supplied {@link PhysicsEvaluator}.
   *
   * @param board thread-confined routing board
   * @param settings physics settings (nullable)
   * @param evaluator stateless scalar energy oracle
   * @param stoppable cooperative cancellation handle (nullable)
   * @return execution metrics
   */
  public static Result run(
      RoutingBoard board,
      PhysicsSettings settings,
      PhysicsEvaluator evaluator,
      Stoppable stoppable) {
    long startNanos = System.nanoTime();
    if (board == null || evaluator == null) {
      return new Result(0.0, 0.0, 0, 0, 0, System.nanoTime() - startNanos);
    }

    double resolutionMm =
        (settings != null && settings.gridResolutionMm != null)
            ? settings.gridResolutionMm
            : BoardFieldContext.DEFAULT_RESOLUTION_MM;
    int maxIterations =
        (settings != null && settings.maxIterations != null && settings.maxIterations >= 0)
            ? settings.maxIterations
            : DEFAULT_MAX_ITERATIONS;

    BoardFieldContext context = BoardFieldContext.fromBoard(board, resolutionMm);
    context.clearFields();

    double initialEnergy = evaluator.evaluateBoard(board, context);
    double currentEnergy = initialEnergy;
    int iterations = 0;
    int triedMoves = 0;
    int acceptedMoves = 0;

    for (int iter = 0; iter < maxIterations; iter++) {
      if ((stoppable != null && stoppable.isStopRequested())
          || Thread.currentThread().isInterrupted()) {
        break;
      }
      iterations++;
      // Infrastructure pass: geometric relaxation rules will propose candidate moves here,
      // validated against ShapeSearchTree / checkTraceShape before committing.
      double nextEnergy = evaluator.evaluateBoard(board, context);
      if (nextEnergy >= currentEnergy) {
        break;
      }
      currentEnergy = nextEnergy;
    }

    return new Result(
        initialEnergy,
        currentEnergy,
        iterations,
        triedMoves,
        acceptedMoves,
        System.nanoTime() - startNanos);
  }
}
