package app.freerouting.interactive;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.GuiSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Sub-Issue 06: verifies that the {@link InteractiveSettings} singleton,
 * once registered as the live {@link GuiSettings} source (priority 50) in the
 * {@link SettingsMerger}, causes subsequent {@link SettingsMerger#merge()} calls to reflect
 * the current GUI state.
 *
 * <p>Also verifies the fall-back behaviour: when no {@code GuiSettings} source is registered
 * (headless mode), the merger resolves router-specific fields from lower-priority defaults.
 *
 * @see InteractiveSettings#getSettings()
 * @see SettingsMerger#addOrReplaceSources(java.util.List)
 */
class SettingsMergerGuiIntegrationTest {

    private static final String TEST_DSN = "fixtures/empty_board.dsn";

    private RoutingBoard board;

    @BeforeEach
    void setUp() throws FileNotFoundException {
        InteractiveSettings.resetForTesting();
        // Load a real board so that InteractiveSettings can be properly constructed.
        RoutingJob job = new RoutingJob();
        HeadlessBoardManager manager = new HeadlessBoardManager(job);
        manager.loadFromSpecctraDsn(
                new FileInputStream(TEST_DSN),
                new BoardObserverAdaptor(),
                new ItemIdentificationNumberGenerator());
        board = manager.get_routing_board();
    }

    @AfterEach
    void tearDown() {
        InteractiveSettings.resetForTesting();
    }

    /**
     * Mutate the {@link InteractiveSettings} singleton and verify that {@link SettingsMerger#merge()}
     * reflects the new value in the returned {@link RouterSettings}.
     *
     * <p>Sub-Issue 06 specification:
     * <blockquote>
     * "mutate the {@code InteractiveSettings} singleton, call {@code merge()}, assert returned
     * {@code RouterSettings} reflects the new value."
     * </blockquote>
     */
    @Test
    void merge_usesLiveInteractiveSettingsValues() {
        InteractiveSettings interactiveSettings = InteractiveSettings.getOrCreate(board);

        // Build a merger with default + the live InteractiveSettings source at priority 50.
        SettingsMerger merger = new SettingsMerger(new DefaultSettings(), interactiveSettings);

        // Change trace_pull_tight_accuracy via the setter (fires PropertyChangeEvent).
        interactiveSettings.set_trace_pull_tight_accuracy(123);

        RouterSettings merged = merger.merge();

        assertEquals(123, merged.trace_pull_tight_accuracy,
                "merge() must reflect the live trace_pull_tight_accuracy from InteractiveSettings");
    }

    /**
     * Verifies that a second mutation is also picked up — i.e. {@code getSettings()} is called on
     * every {@code merge()} and is not cached.
     */
    @Test
    void merge_picksUpSubsequentMutations() {
        InteractiveSettings interactiveSettings = InteractiveSettings.getOrCreate(board);
        SettingsMerger merger = new SettingsMerger(new DefaultSettings(), interactiveSettings);

        interactiveSettings.set_trace_pull_tight_accuracy(300);
        assertEquals(300, merger.merge().trace_pull_tight_accuracy);

        interactiveSettings.set_trace_pull_tight_accuracy(750);
        assertEquals(750, merger.merge().trace_pull_tight_accuracy,
                "merge() must re-read getSettings() on every call — results must not be cached");
    }

    /**
     * Verifies that the {@link InteractiveSettings} singleton <em>replaces</em> a plain
     * {@link GuiSettings} placeholder that was added first. This mirrors the startup sequence in
     * {@code GuiManager} (placeholder) → {@code GuiBoardManager.loadFromSpecctraDsn} (live singleton).
     */
    @Test
    void addOrReplaceSources_interactiveSettingsReplacesGuiSettingsPlaceholder() {
        SettingsMerger merger = new SettingsMerger(new DefaultSettings());

        // Add a plain (static-snapshot) GuiSettings placeholder, as GuiManager does at startup.
        merger.addOrReplaceSources(new GuiSettings(null));

        // After board load, register the live singleton — it must replace the placeholder.
        InteractiveSettings interactiveSettings = InteractiveSettings.getOrCreate(board);
        interactiveSettings.set_trace_pull_tight_accuracy(999);
        merger.addOrReplaceSources(interactiveSettings);

        RouterSettings merged = merger.merge();
        assertEquals(999, merged.trace_pull_tight_accuracy,
                "InteractiveSettings must have replaced the plain GuiSettings placeholder");
    }

    /**
     * In headless mode (no {@code GuiSettings} source registered), the merger falls back to
     * lower-priority defaults for all fields — including those that {@code InteractiveSettings}
     * would otherwise supply.
     *
     * <p>Sub-Issue 06 specification:
     * <blockquote>
     * "in headless mode (no {@code GuiSettings} registered), merger falls back to lower-priority
     * defaults."
     * </blockquote>
     */
    @Test
    void merge_withNoGuiSource_usesDefaults() {
        // Merger with only defaults — no GuiSettings / InteractiveSettings registered.
        SettingsMerger merger = new SettingsMerger(new DefaultSettings());
        RouterSettings merged = merger.merge();

        // The default value for trace_pull_tight_accuracy is 500 (from DefaultSettings).
        assertEquals(500, merged.trace_pull_tight_accuracy,
                "Without a GuiSettings source, defaults must supply trace_pull_tight_accuracy");
    }

    /**
     * Verifies that the {@code automatic_neckdown} field — the second field mapped by
     * {@link InteractiveSettings#getSettings()} — flows through the merger when set to
     * {@code true}.
     *
     * <p><strong>Architectural note:</strong> {@link app.freerouting.management.ReflectionUtil#copyFields}
     * skips fields whose value equals the Java-language default for that type. For
     * {@code Boolean}, the skip-sentinel is {@code false}. This means
     * {@code InteractiveSettings.automatic_neckdown = false} <em>cannot</em> override a
     * higher-priority {@code true} coming from {@link DefaultSettings} through the current merger
     * architecture — the {@code false} value is silently ignored as "no opinion". Only
     * {@code Boolean.TRUE} (non-default) propagates through {@code copyFields}. This is the same
     * constraint documented in {@code AGENTS.md} for all {@code RouterSettings} fields.
     */
    @Test
    void merge_automaticNeckdownFlowsThroughMerger() {
        InteractiveSettings interactiveSettings = InteractiveSettings.getOrCreate(board);
        SettingsMerger merger = new SettingsMerger(new DefaultSettings(), interactiveSettings);

        // true is the non-default (Boolean.FALSE is the ReflectionUtil skip-sentinel),
        // so Boolean.TRUE propagates correctly through copyFields.
        interactiveSettings.set_automatic_neckdown(true);

        RouterSettings merged = merger.merge();
        assertTrue(merged.automatic_neckdown,
                "InteractiveSettings.automatic_neckdown=true must be reflected by merge()");
    }
}


