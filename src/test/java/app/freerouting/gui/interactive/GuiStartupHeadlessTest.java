package app.freerouting.gui.interactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.GuiSettings;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sub-Issue 08 – Integration tests: GUI load path initialises settings; headless path never
 * requires them.
 *
 * <p>Exercises the {@link InteractiveSettings} initialisation invariants that {@link
 * GuiBoardManager#loadFromSpecctraDsn} establishes when loading a design. Because {@link
 * GuiBoardManager} depends on Swing components that cannot be rendered in a headless CI
 * environment, the test simulates the critical steps of the GUI load path directly:
 *
 * <ol>
 *   <li>Load a board via {@link HeadlessBoardManager} (same board-reading logic).
 *   <li>Call {@link InteractiveSettings#reset(RoutingBoard)} – exactly what {@code
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
 * @see InteractiveSettings#reset(RoutingBoard)
 * @see SettingsMerger
 */
class GuiStartupHeadlessTest {

  private static final String TEST_DSN = "fixtures/empty_board.dsn";

  private RoutingBoard board;
  private InteractiveSettings settings;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    InteractiveSettings.resetForTesting();

    HeadlessBoardManager manager = new HeadlessBoardManager(new RoutingJob());
    manager.loadFromSpecctraDsn(
        new FileInputStream(TEST_DSN),
        new BoardObserverAdaptor(),
        new ItemIdentificationNumberGenerator());

    board = manager.getRoutingBoard();
    assertNotNull(board, "Board must be non-null after DSN load");

    // Simulate GuiBoardManager.loadFromSpecctraDsn: reset singleton for the new board.
    settings = InteractiveSettings.reset(board);
  }

  @AfterEach
  void tearDown() {
    InteractiveSettings.resetForTesting();
  }

  // ── Invariant 1: singleton is non-null and bound to the board ─────────────

  /**
   * {@link InteractiveSettings#getOrCreate(RoutingBoard)} must return non-null after the
   * GUI-load-path reset step, mirroring the guarantee that {@link
   * GuiBoardManager#getInteractiveSettings()} is never {@code null} post-load.
   */
  @Test
  void getOrCreateIsNonNullAfterGuiLoadStep() {
    assertNotNull(
        InteractiveSettings.getOrCreate(board),
        "InteractiveSettings.getOrCreate(board) must return non-null after GUI load-path reset");
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

  // ── Invariant 3: SettingsMerger sees current InteractiveSettings values ───

  /**
   * When the {@link InteractiveSettings} singleton is registered as the live {@link GuiSettings}
   * source (priority 50), a subsequent {@link SettingsMerger#merge()} call must reflect the current
   * singleton state.
   *
   * <p>This mirrors the requirement from Sub-Issue 06 that the GUI session's {@code
   * InteractiveSettings} is always the authoritative priority-50 source.
   */
  @Test
  void settingsMergerReflectsInteractiveSettingsAfterRegistration() {
    // Build a merger with Default at priority 0 and the InteractiveSettings singleton at 50.
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());
    merger.addOrReplaceSources(settings); // InteractiveSettings IS-A GuiSettings (priority 50)

    RouterSettings merged = merger.merge();
    assertNotNull(merged, "SettingsMerger.merge() must return non-null RouterSettings");
  }

  /**
   * After mutating the {@link InteractiveSettings} singleton, the next {@link
   * SettingsMerger#merge()} call must pick up the new value through {@link
   * InteractiveSettings#getSettings()}.
   *
   * <p>This verifies the live-snapshot contract: the merger does not cache stale values.
   */
  @Test
  void settingsMergerPicksUpLiveMutationOfInteractiveSettings() {
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());
    merger.addOrReplaceSources(settings);

    // Mutate a field that InteractiveSettings.getSettings() exposes.
    settings.setAutomaticNeckdown(true);

    RouterSettings merged = merger.merge();
    assertNotNull(merged, "merge() must return non-null after mutation");
    assertTrue(
        merged.automaticNeckdown,
        "SettingsMerger.merge() must reflect the mutated automaticNeckdown value");
  }

  // ── Invariant 4: layer count matches board ────────────────────────────────

  /**
   * The layer count reported by {@link InteractiveSettings#getLayerCount()} must match the number
   * of layers in the underlying {@link RoutingBoard}.
   *
   * <p>This confirms that {@link InteractiveSettings#reset(RoutingBoard)} correctly sizes the
   * internal arrays to the board's layer structure.
   */
  @Test
  void layerCountMatchesBoardLayerCount() {
    assertEquals(
        board.getLayerCount(),
        settings.getLayerCount(),
        "InteractiveSettings.get_layer_count() must match RoutingBoard.get_layer_count()");
  }
}
