package app.freerouting.gui.session;

import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.gui.BoardFrame;
import app.freerouting.settings.RouterSettings;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * EDT-bound adapter for the session port.
 *
 * <p>Background code only sees {@link GuiSessionPort}; all Swing-facing mutations are centralized
 * here and are generation-checked at execution time.
 */
public final class GuiSessionPortAdapter implements GuiSessionPort {

  private final Supplier<GuiBoardManager> managerSupplier;
  private final Supplier<BoardFrame> frameSupplier;
  private final EdtExecutor edtExecutor;
  private final AtomicLong generationCounter = new AtomicLong();

  private volatile LoadGeneration activeLoad;
  private volatile RunGeneration activeRun;
  private volatile boolean savedReadOnly;
  private volatile boolean ratsNestWasHidden;

  /** Creates an adapter with suppliers for the current manager and owning frame. */
  public GuiSessionPortAdapter(
      Supplier<GuiBoardManager> managerSupplier,
      Supplier<BoardFrame> frameSupplier,
      EdtExecutor edtExecutor) {
    this.managerSupplier = Objects.requireNonNull(managerSupplier, "managerSupplier");
    this.frameSupplier = Objects.requireNonNull(frameSupplier, "frameSupplier");
    this.edtExecutor = Objects.requireNonNull(edtExecutor, "edtExecutor");
  }

  @Override
  public LoadGeneration beginBoardLoad() {
    GuiBoardManager manager = managerSupplier.get();
    if (manager != null) {
      manager.requestStopFromSessionPort();
    }
    activeRun = null;
    LoadGeneration generation = new LoadGeneration(generationCounter.incrementAndGet());
    activeLoad = generation;
    return generation;
  }

  @Override
  public boolean isCurrent(LoadGeneration generation) {
    return generation != null && generation.equals(activeLoad);
  }

  @Override
  public void replaceBoard(BoardReplacement replacement) {
    Objects.requireNonNull(replacement, "replacement");
    runOnEdt(
        () -> {
          if (!isCurrentGeneration(replacement.generation())) {
            return;
          }
          GuiBoardManager manager = managerSupplier.get();
          if (manager != null) {
            manager.replaceRoutingBoard(replacement.board());
            manager.repaint();
          }
        });
  }

  @Override
  public RouterSettingsSnapshot settingsSnapshot() {
    GuiBoardManager manager = managerSupplier.get();
    RouterSettings settings;
    if (manager == null) {
      settings = new RouterSettings();
    } else if (manager.settingsMerger != null) {
      settings = manager.settingsMerger.merge();
    } else {
      RoutingJob job = manager.getCurrentRoutingJob();
      settings = job == null ? new RouterSettings() : job.routerSettings;
    }
    return new RouterSettingsSnapshot(settings);
  }

  @Override
  public RunGeneration beginRoute(RoutingJob job) {
    Objects.requireNonNull(job, "job");
    RunGeneration generation = new RunGeneration(generationCounter.incrementAndGet());
    activeRun = generation;
    activeLoad = null;
    GuiBoardManager manager = managerSupplier.get();
    if (manager != null) {
      savedReadOnly = manager.isBoardReadOnly();
      ratsNestWasHidden = manager.getRatsnest() != null && manager.getRatsnest().isHidden();
    }
    runOnEdt(
        () -> {
          if (!generation.equals(activeRun)) {
            return;
          }
          GuiBoardManager current = managerSupplier.get();
          if (current != null) {
            current.setNumThreads(job.routerSettings.maxThreads);
            current.setBoardReadOnly(true);
            if (current.getRatsnest() != null && !ratsNestWasHidden) {
              current.getRatsnest().hide();
            }
          }
        });
    return generation;
  }

  @Override
  public void requestStop(RunGeneration generation) {
    if (generation != null && generation.equals(activeRun)) {
      GuiBoardManager manager = managerSupplier.get();
      if (manager != null) {
        manager.requestStopFromSessionPort();
      }
    }
  }

  /** Requests the currently active run to stop. */
  public void requestStopCurrent() {
    requestStop(activeRun);
  }

  @Override
  public void publishProgress(RouteProgress progress) {
    Objects.requireNonNull(progress, "progress");
    runOnEdt(
        () -> {
          if (!progress.generation().equals(activeRun)) {
            return;
          }
          GuiBoardManager manager = managerSupplier.get();
          if (manager == null) {
            return;
          }
          ScreenMessages messages = manager.screenMessages;
          if (progress.clearMessages()) {
            messages.clear();
          }
          if (progress.statusMessage() != null) {
            messages.setStatusMessage(progress.statusMessage());
          }
          if (progress.batchProgress() != null) {
            BatchProgress batch = progress.batchProgress();
            app.freerouting.core.RouterCounters counters =
                new app.freerouting.core.RouterCounters();
            counters.queuedToBeRoutedCount = batch.queuedToBeRoutedCount();
            counters.routedCount = batch.routedCount();
            counters.failedToBeRoutedCount = batch.failedToBeRoutedCount();
            counters.rippedCount = batch.rippedCount();
            counters.phase = batch.phase();
            counters.fanoutExtraViasCount = batch.fanoutExtraViasCount();
            messages.setBatchAutorouteInfo(counters);
          }
          if (progress.viaCount() != null && progress.traceLength() != null) {
            messages.setPostRouteInfo(progress.viaCount(), progress.traceLength(), progress.unit());
          }
          messages.setBoardScore(
              progress.boardScore(), progress.incompleteCount(), progress.violationCount());
          if (progress.repaint()) {
            manager.repaint();
          }
        });
  }

  @Override
  public void finishRoute(RouteCompletion completion) {
    Objects.requireNonNull(completion, "completion");
    runOnEdt(
        () -> {
          if (!completion.generation().equals(activeRun)) {
            return;
          }
          GuiBoardManager manager = managerSupplier.get();
          if (manager == null) {
            return;
          }
          manager.setBoardReadOnly(savedReadOnly);
          manager.updateRatsnest();
          if (!ratsNestWasHidden && manager.getRatsnest() != null) {
            manager.getRatsnest().show();
          }
          manager.screenMessages.clear();
          if (completion.statusMessage() != null) {
            manager.screenMessages.setStatusMessage(completion.statusMessage());
          }
          if (completion.refreshWindows()) {
            BoardFrame frame = frameSupplier.get();
            if (frame != null) {
              frame.refreshWindows();
            }
          }
          manager.repaint();
          activeRun = null;
        });
  }

  @Override
  public RoutingBoard routingBoard() {
    GuiBoardManager manager = managerSupplier.get();
    return manager == null ? null : manager.getRoutingBoard();
  }

  @Override
  public app.freerouting.gui.rendering.GraphicsContext graphicsContext() {
    GuiBoardManager manager = managerSupplier.get();
    return manager == null ? null : manager.graphicsContext;
  }

  @Override
  public Locale locale() {
    GuiBoardManager manager = managerSupplier.get();
    return manager == null ? Locale.getDefault() : manager.getLocale();
  }

  @Override
  public Unit displayUnit() {
    GuiBoardManager manager = managerSupplier.get();
    return manager == null || manager.coordinateTransform == null
        ? Unit.MM
        : manager.coordinateTransform.userUnit;
  }

  @Override
  public void repaint() {
    runOnEdt(
        () -> {
          GuiBoardManager manager = managerSupplier.get();
          if (manager != null) {
            manager.repaint();
          }
        });
  }

  @Override
  public void publishLogCounts(int errorsCount, int warningsCount) {
    runOnEdt(
        () -> {
          GuiBoardManager manager = managerSupplier.get();
          if (manager != null) {
            manager.screenMessages.setErrorAndWarningCount(errorsCount, warningsCount);
          }
        });
  }

  @Override
  public void showProfileDialog() {
    runOnEdt(
        () -> {
          BoardFrame frame = frameSupplier.get();
          if (frame != null) {
            frame.menubar.showProfileDialog();
          }
        });
  }

  /** Invalidates all callbacks owned by the current manager before it is disposed. */
  public void invalidateRun() {
    activeRun = null;
  }

  private void runOnEdt(Runnable action) {
    if (EdtExecutor.isEdt()) {
      action.run();
    } else {
      edtExecutor.execute(action);
    }
  }

  private boolean isCurrentGeneration(SessionGeneration generation) {
    return switch (generation) {
      case LoadGeneration loadGeneration -> isCurrent(loadGeneration);
      case RunGeneration runGeneration -> runGeneration.equals(activeRun);
    };
  }
}
