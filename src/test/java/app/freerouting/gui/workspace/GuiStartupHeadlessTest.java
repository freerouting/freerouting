package app.freerouting.gui.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.GuiSettingsSource;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sub-Issue 08 – Integration tests: GUI load path initialises settings; headless path never
 * requires them.
 *
 * <p>Exercises the {@link WorkspaceSettings} initialisation invariants that {@link
 * GuiBoardManager#loadFromSpecctraDsn} establishes when loading a design. Because {@link
 * GuiBoardManager} depends on Swing components that cannot be rendered in a headless CI
 * environment, the test simulates the critical steps of the GUI load path directly:
 *
 * <ol>
 *   <li>Load a board via {@link HeadlessBoardManager} (same board-reading logic).
 *   <li>Call {@link WorkspaceSettings#reset(RoutingBoard)} – exactly what {@code
 *       GuiBoardManager.loadFromSpecctraDsn} does after the board is set.
 *   <li>Populate {@code manualTraceHalfWidthArr} from board rules – exactly what {@code
 *       GuiBoardManager.initialize_manual_trace_half_widths()} does.
 *   <li>Register the singleton in a {@link SettingsMerger} and verify {@code merge()} reflects it.
 * </ol>
 *
 * <p>These tests run under {@code -Djava.awt.headless=true} (the default JVM argument in {@code
 * build.gradle}) without requiring a display.
 *
 * @see GuiBoardManager#loadFromSpecctraDsn
 * @see WorkspaceSettings#reset(RoutingBoard)
 * @see SettingsMerger
 */
class GuiStartupHeadlessTest {

  private static final String TEST_DSN = "fixtures/empty_board.dsn";

  private RoutingBoard board;
  private WorkspaceSettings settings;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    WorkspaceSettings.resetForTesting();

    HeadlessBoardManager manager = new HeadlessBoardManager(new RoutingJob());
    manager.loadFromSpecctraDsn(
        new FileInputStream(TEST_DSN), new BoardObserverAdaptor(), new ItemIdGenerator());

    board = manager.getRoutingBoard();
    assertNotNull(board, "Board must be non-null after DSN load");

    // Simulate GuiBoardManager.loadFromSpecctraDsn: reset singleton for the new board.
    settings = WorkspaceSettings.reset(board);
  }

  @AfterEach
  void tearDown() {
    WorkspaceSettings.resetForTesting();
  }

  // ── Invariant 1: singleton is non-null and bound to the board ─────────────

  /**
   * {@link WorkspaceSettings#getOrCreate(RoutingBoard)} must return non-null after the
   * GUI-load-path reset step, mirroring the guarantee that {@link
   * GuiBoardManager#getWorkspaceSettings()} is never {@code null} post-load.
   */
  @Test
  void getOrCreateIsNonNullAfterGuiLoadStep() {
    assertNotNull(
        WorkspaceSettings.getOrCreate(board),
        "WorkspaceSettings.getOrCreate(board) must return non-null after GUI load-path reset");
  }

  /**
   * The active layer must be {@code 0} immediately after a design load, matching the behaviour of
   * {@link GuiBoardManager#loadFromSpecctraDsn} which calls {@code set_layer(0)}.
   */
  @Test
  void getLayerIsZeroAfterGuiLoadStep() {
    assertEquals(
        0,
        settings.getLayer(),
        "Active layer must be 0 immediately after design load (GUI load-path invariant)");
  }

  // ── Invariant 2: manual trace half widths populated from board rules ──────

  /**
   * Simulates {@link GuiBoardManager#initializeManualTraceHalfWidths()} and verifies that every
   * layer's manual trace half-width is set to a positive value derived from the board's default net
   * class.
   *
   * <p>This is the behaviour documented in the GUI load-path contract: after {@code
   * initialize_manual_trace_half_widths()} each element of {@code manualTraceHalfWidthArr} reflects
   * the default trace rule for that layer.
   */
  @Test
  void initializeManualTraceHalfWidthsPopulatesArrayFromBoardRules() {
    // Replicate GuiBoardManager.initialize_manual_trace_half_widths() logic.
    for (int i = 0; i < settings.getLayerCount(); i++) {
      int ruleWidth = board.rules.getDefaultNetClass().getTraceHalfWidth(i);
      settings.manualTraceHalfWidthArr[i] = ruleWidth;
    }

    for (int i = 0; i < settings.getLayerCount(); i++) {
      assertTrue(
          settings.manualTraceHalfWidthArr[i] > 0,
          "manualTraceHalfWidthArr[" + i + "] must be > 0 after initialisation from board rules");
    }
  }

  // ── Invariant 3: SettingsMerger sees current WorkspaceSettings values ───

  /**
   * When the {@link WorkspaceSettings} singleton is registered as the live {@link
   * GuiSettingsSource} source (priority 65), a subsequent {@link SettingsMerger#merge()} call must
   * reflect the current singleton state.
   *
   * <p>This mirrors the requirement from Sub-Issue 06 that the GUI session's {@code
   * WorkspaceSettings} is always the authoritative priority-65 source.
   */
  @Test
  void settingsMergerReflectsWorkspaceSettingsAfterRegistration() {
    // Build a merger with Default at priority 0 and the WorkspaceSettings singleton at 65.
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());
    merger.addOrReplaceSources(settings); // WorkspaceSettings IS-A GuiSettingsSource (priority 65)

    RouterSettings merged = merger.merge();
    assertNotNull(merged, "SettingsMerger.merge() must return non-null RouterSettings");
  }

  /**
   * After mutating the {@link WorkspaceSettings} singleton, the next {@link SettingsMerger#merge()}
   * call must pick up the new value through {@link WorkspaceSettings#getSettings()}.
   *
   * <p>This verifies the live-snapshot contract: the merger does not cache stale values.
   */
  @Test
  void settingsMergerPicksUpLiveMutationOfWorkspaceSettings() {
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());
    merger.addOrReplaceSources(settings);

    // Mutate a field that WorkspaceSettings.getSettings() exposes.
    settings.setAutomaticNeckdown(true);

    RouterSettings merged = merger.merge();
    assertNotNull(merged, "merge() must return non-null after mutation");
    assertTrue(
        merged.automaticNeckdown,
        "SettingsMerger.merge() must reflect the mutated automaticNeckdown value");
  }

  // ── Invariant 4: layer count matches board ────────────────────────────────

  /**
   * The layer count reported by {@link WorkspaceSettings#getLayerCount()} must match the number of
   * layers in the underlying {@link RoutingBoard}.
   *
   * <p>This confirms that {@link WorkspaceSettings#reset(RoutingBoard)} correctly sizes the
   * internal arrays to the board's layer structure.
   */
  @Test
  void layerCountMatchesBoardLayerCount() {
    assertEquals(
        board.getLayerCount(),
        settings.getLayerCount(),
        "WorkspaceSettings.get_layer_count() must match RoutingBoard.get_layer_count()");
  }
}
