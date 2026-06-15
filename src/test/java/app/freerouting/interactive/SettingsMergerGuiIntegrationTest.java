package app.freerouting.interactive;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.management.HeadlessBoardManager;
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
     * <p><strong>Architectural note:</strong> {@link app.freerouting.util.ReflectionUtil#copyFields}
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

    /**
     * Reproduces the issue where CLI settings override GUI modifications at startup,
     * but the user should be able to override them on the GUI.
     */
    @Test
    void settingsMerge_cliOverridesGuiAtStartupButGuiShouldOverrideCliLater() {
        // Prepare base settings where first layer is routable, second layer is routable
        RouterSettings baseSettings = new RouterSettings();
        baseSettings.setLayerCount(2);
        baseSettings.layers[0].routable = true;
        baseSettings.layers[1].routable = true;

        // CLI Source disables the first layer: --router.layers.routable=false,true
        RouterSettings cliSettings = new RouterSettings();
        cliSettings.setLayerCount(2);
        cliSettings.layers[0].routable = false;
        cliSettings.layers[1].routable = true;
        app.freerouting.settings.SettingsSource cliSource = new app.freerouting.settings.sources.CliSettings(new String[]{"--router.layers.routable=false,true"});

        // Gui Settings initially contains the active settings (which got the merged startup settings from CLI)
        RouterSettings guiActiveSettings = new RouterSettings();
        guiActiveSettings.setLayerCount(2);
        guiActiveSettings.layers[0].routable = false;
        guiActiveSettings.layers[1].routable = true;
        InteractiveSettings interactiveSettings = InteractiveSettings.reset(board, guiActiveSettings);

        // Merger setup: defaults (0), GuiSettings (65), CliSettings (60)
        SettingsMerger merger = new SettingsMerger(new DefaultSettings(), cliSource, interactiveSettings);

        // Assert startup state matches CLI: first layer is disabled
        RouterSettings startupMerged = merger.merge();
        assertFalse(startupMerged.layers[0].routable, "At startup, first layer must be disabled (CLI wins)");

        // Now, user overrides the first layer checkbox on GUI: changes it to enabled (true)
        guiActiveSettings.layers[0].routable = true;

        // Perform merge before starting router
        RouterSettings postGuiChangeMerged = merger.merge();

        // The merged settings should respect the GUI change (routable = true), overriding the CLI setting
        assertTrue(postGuiChangeMerged.layers[0].routable, "GUI change must override the CLI setting");
    }

    /**
     * Verifies that when InteractiveSettings and the jobSettings share the same reference
     * (the fixed behavior), user GUI edits correctly override startup CLI settings.
     */
    @Test
    void settingsMerge_guiOverridesCliCorrectlyWithSharedReference() {
        // 1. Setup job settings (like routingJob.routerSettings at startup, layers is null)
        RouterSettings jobSettings = new RouterSettings();

        // 2. CLI Source disables the first layer: --router.layers.routable=false,true
        RouterSettings cliSettings = new RouterSettings();
        cliSettings.setLayerCount(2);
        cliSettings.layers[0].routable = false;
        cliSettings.layers[1].routable = true;
        app.freerouting.settings.SettingsSource cliSource = new app.freerouting.settings.sources.CliSettings(new String[]{"--router.layers.routable=false,true"});

        // 3. InteractiveSettings initially created with jobSettings (contains null layers)
        InteractiveSettings interactiveSettings = InteractiveSettings.reset(board, jobSettings);

        // 4. Merger setup
        SettingsMerger merger = new SettingsMerger(new DefaultSettings(), cliSource, interactiveSettings);

        // 5. BoardFrame startup load logic:
        RouterSettings startupMerged = merger.merge();
        assertFalse(startupMerged.layers[0].routable, "At startup, first layer must be disabled (CLI wins)");

        // jobSettings (routingJob.routerSettings) is initialized to match board layer count,
        // and mutated in-place by copying fields from startupMerged
        jobSettings.setLayerCount(2);
        jobSettings.applyNewValuesFrom(startupMerged);
        // interactiveSettings.settings is set to jobSettings (fixed/shared reference!)
        interactiveSettings.setSettings(jobSettings);

        // 6. User modifies settings in the GUI: mutates jobSettings (routingJob.routerSettings)
        jobSettings.layers[0].routable = true;

        // 7. Merger merges again when starting routing run:
        RouterSettings runMerged = merger.merge();

        // 8. Assert that the merged settings for the run correctly respect the GUI change (routable = true)
        assertTrue(runMerged.layers[0].routable, "GUI change must be respected on the first run, overriding CLI settings");
    }
}
