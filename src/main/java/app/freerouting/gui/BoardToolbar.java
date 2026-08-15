package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.workspace.EditorStateHandle;
import app.freerouting.gui.workspace.EditorStateKind;
import app.freerouting.gui.workspace.InteractiveActionThread;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import app.freerouting.util.gson.GsonProvider;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

/** Implements the toolbar panel of the board frame. */
class BoardToolbar extends JPanel {

  private final float iconFontSize = 26.0F;
  private final SegmentedButtons modeSelectionPanel;
  private final JButton settingsButton;
  private final JButton toolbarAutorouteButton;
  private final JButton cancelButton;
  private final JButton toolbarUndoButton;
  private final JButton toolbarRedoButton;
  private final JButton toolbarIncompletesButton;
  private final JButton toolbarViolationButton;
  private final JButton toolbarDisplayRegionButton;
  private final JButton toolbarDisplayAllButton;
  private final SegmentedButtons unitSelectionPanel;
  private final JButton deleteAllTracksButton;
  private final BoardFrame boardFrame;

  // Debug controls
  private JButton varsPlayButton;
  private JButton varsPauseButton;
  private JButton varsNextButton;
  private JButton varsPreviousButton;

  private boolean isShiftDown;

  /** Creates a new instance of BoardToolbarPanel. */
  BoardToolbar(BoardFrame boardFrame, boolean disableSelectMode) {
    this.boardFrame = boardFrame;

    // Listen for Shift key globally to update icons
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(
            new KeyEventDispatcher() {
              @Override
              public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SHIFT) {
                  if (!isShiftDown) {
                    isShiftDown = true;
                    updateDebugIcons();
                  }
                } else if (e.getID() == KeyEvent.KEY_RELEASED
                    && e.getKeyCode() == KeyEvent.VK_SHIFT) {
                  if (isShiftDown) {
                    isShiftDown = false;
                    updateDebugIcons();
                  }
                }
                return false;
              }
            });

    // Setup Global Keyboard Shortcuts for Arrows
    setupKeyboardShortcuts();

    // Register listener for debug state changes
    app.freerouting.debug.DebugControl.getInstance()
        .addDebugStateListener(
            isPaused -> SwingUtilities.invokeLater(this::updateDebugButtonsState));

    GuiTextManager tm = new GuiTextManager(this.getClass(), boardFrame.get_locale());

    this.setLayout(new BorderLayout());

    // create the left toolbar

    final JToolBar leftToolbar = new JToolBar();

    leftToolbar.setMaximumSize(new Dimension(1200, 30));

    if (!disableSelectMode) {
      modeSelectionPanel =
          new SegmentedButtons(
              tm, tm.getText("mode_heading"), "inspect_button", "route_button", "drag_button");
    } else {
      modeSelectionPanel =
          new SegmentedButtons(tm, tm.getText("mode_heading"), "route_button", "drag_button");
    }
    A11y.tag(modeSelectionPanel, GuiLocators.TOOLBAR_MODE_SELECT);
    A11y.describe(modeSelectionPanel, tm.getText("mode_heading"), null);
    tagSegmentButton(modeSelectionPanel, "inspect_button", GuiLocators.TOOLBAR_MODE_INSPECT, null);
    tagSegmentButton(modeSelectionPanel, "route_button", GuiLocators.TOOLBAR_MODE_ROUTE, null);
    tagSegmentButton(modeSelectionPanel, "drag_button", GuiLocators.TOOLBAR_MODE_DRAG, null);
    modeSelectionPanel.addValueChangedEventListener(
        (String value) -> {
          switch (value) {
            case "inspect_button":
              boardFrame.boardPanel.boardHandling.setInspectMenuState();
              break;
            case "route_button":
              boardFrame.boardPanel.boardHandling.setRouteMenuState();
              break;
            case "drag_button":
              boardFrame.boardPanel.boardHandling.setDragMenuState();
              break;
            default:
              break;
          }
        });
    modeSelectionPanel.addValueChangedEventListener(
        (String value) -> FRAnalytics.buttonClicked("modeSelectionPanel", value));
    leftToolbar.add(modeSelectionPanel, BorderLayout.CENTER);

    this.add(leftToolbar, BorderLayout.WEST);

    // create the middle toolbar

    final JToolBar middleToolbar = new JToolBar();

    // Add "Settings" button to the toolbar
    settingsButton = new JButton();
    tm.setText(settingsButton, "settingsButton");
    tagToolbarButton(settingsButton, GuiLocators.TOOLBAR_SETTINGS);
    settingsButton.addActionListener(_ -> boardFrame.autorouteParameterWindow.setVisible(true));
    settingsButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("settingsButton", settingsButton.getText()));
    middleToolbar.add(settingsButton);

    // Add "Autoroute" button to the toolbar
    toolbarAutorouteButton = new JButton();
    tm.setText(toolbarAutorouteButton, "autoroute_button");
    tagToolbarButton(toolbarAutorouteButton, GuiLocators.TOOLBAR_AUTOROUTE);
    toolbarAutorouteButton.setDefaultCapable(true);
    Font currentFont = toolbarAutorouteButton.getFont();
    Font boldFont = new Font(currentFont.getFontName(), Font.BOLD, currentFont.getSize());
    toolbarAutorouteButton.setFont(boldFont);
    toolbarAutorouteButton.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
    // Set padding (top, left, bottom, right)
    toolbarAutorouteButton.setBorder(
        BorderFactory.createCompoundBorder(
            toolbarAutorouteButton.getBorder(), BorderFactory.createEmptyBorder(2, 5, 2, 5)));
    toolbarAutorouteButton.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    toolbarAutorouteButton.addActionListener(
        _ -> {
          var routingJobs =
              RoutingJobScheduler.getInstance()
                  .listJobs(SessionManager.getInstance().getPrimarySession().id.toString());
          if (routingJobs.length == 0) {
            FRLogger.warn("No routing job found for the current session");
            return;
          }

          var guiRoutingJob = Arrays.stream(routingJobs).findFirst().get();
          if (guiRoutingJob.input == null) {
            FRLogger.warn(tm.getText("warn_no_input_file"));
            return;
          }
          var merger = boardFrame.boardPanel.boardHandling.settingsMerger;
          if (merger != null) {
            var mergedSettings = merger.merge();
            guiRoutingJob.setSettings(mergedSettings);
            var interactiveSettings = boardFrame.boardPanel.boardHandling.getWorkspaceSettings();
            if (interactiveSettings != null) {
              interactiveSettings.setSettings(guiRoutingJob.routerSettings);
            }
          }
          // The GUI-path settingsMerger does not include DsnFileSettings, so the merged
          // RouterSettings has layers == null (layer count 0).  Re-apply board-
          // specific optimisations so the layer arrays are populated from the actual board
          // before the autorouter reads them (fixes Issue #676 / "get_layer_active out of
          // range [0..-1]" warnings and MazeSearchAlgo exceptions on LibrePCB DSN files).
          app.freerouting.board.RoutingBoard routingBoard =
              boardFrame.boardPanel.boardHandling.getRoutingBoard();
          if (routingBoard != null) {
            guiRoutingJob.routerSettings.applyBoardSpecificOptimizationsIfNeeded(routingBoard);
          }
          InteractiveActionThread thread =
              boardFrame.boardPanel.boardHandling.startAutorouterAndRouteOptimizer(guiRoutingJob);

          if ((thread != null)
              && (boardFrame.boardPanel.boardHandling.autorouterListener != null)) {
            // Add the auto-router listener to save the design file when the auto-router is
            // running
            thread.addListener(boardFrame.boardPanel.boardHandling.autorouterListener);
          }

          if (Freerouting.globalSettings.debugSettings.singleStepExecution) {
            app.freerouting.debug.DebugControl.getInstance().reset();
            app.freerouting.debug.DebugControl.getInstance().resetDebugState();

            // Since we reset(), it defaults to PAUSED if singleStep enabled.
            // So Enable Play/Next/Prev, Disable Pause
            if (varsPlayButton != null) {
              varsPlayButton.setEnabled(true);
            }
            if (varsNextButton != null) {
              varsNextButton.setEnabled(true);
            }
            if (varsPreviousButton != null) {
              varsPreviousButton.setEnabled(true);
            }
            if (varsPauseButton != null) {
              varsPauseButton.setEnabled(false);
            }
          }
        });
    toolbarAutorouteButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarAutorouteButton", GsonProvider.GSON.toJson(globalSettings)));
    middleToolbar.add(toolbarAutorouteButton);

    // Add "Cancel" button to the toolbar
    cancelButton = new JButton();
    tm.setText(cancelButton, "cancelButton");
    tagToolbarButton(cancelButton, GuiLocators.TOOLBAR_CANCEL);
    cancelButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.stopAutorouterAndRouteOptimizer());
    cancelButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("cancelButton", cancelButton.getText()));
    cancelButton.setEnabled(false);
    middleToolbar.add(cancelButton);

    // Add "Delete All Tracks and Vias" button to the toolbar
    deleteAllTracksButton = new JButton();
    tm.setText(deleteAllTracksButton, "deleteAllTracksButton");
    tagToolbarButton(deleteAllTracksButton, GuiLocators.TOOLBAR_DELETE_TRACKS);
    deleteAllTracksButton.addActionListener(
        _ -> {
          RoutingBoard board = boardFrame.boardPanel.boardHandling.getRoutingBoard();
          // delete all tracks and vias
          board.deleteAllTracksAndVias();
          // unfill conduction areas
          board.unfillConductionAreas();
          // update the board
          boardFrame.boardPanel.boardHandling.replaceRoutingBoard(board);
          // create a deep copy of the routing board
          board = boardFrame.boardPanel.boardHandling.getRoutingBoard().deepCopy();
          // update the board again
          boardFrame.boardPanel.boardHandling.replaceRoutingBoard(board);
          // create ratsnest
          boardFrame.boardPanel.boardHandling.createRatsnest();
          // redraw the board
          boardFrame.boardPanel.boardHandling.repaint();
          // update the board frame
          BoardStatistics boardStatistics = board.getStatistics();
          boardFrame.screenMessages.setBoardScore(
              boardStatistics.getNormalizedScore(boardFrame.routingJob.routerSettings.scoring),
              boardStatistics.connections.incompleteCount,
              boardStatistics.clearanceViolations.totalCount);
        });
    deleteAllTracksButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("deleteAllTracksButton", deleteAllTracksButton.getText()));
    middleToolbar.add(deleteAllTracksButton);

    final JLabel separator2 = new JLabel();
    separator2.setMaximumSize(new Dimension(10, 10));
    separator2.setPreferredSize(new Dimension(10, 10));
    separator2.setRequestFocusEnabled(false);
    middleToolbar.add(separator2);

    toolbarUndoButton = new JButton();
    tm.setText(toolbarUndoButton, "undo_button");
    tagToolbarButton(toolbarUndoButton, GuiLocators.TOOLBAR_UNDO);
    toolbarUndoButton.addActionListener(
        _ -> {
          boardFrame.boardPanel.boardHandling.cancelState();
          boardFrame.boardPanel.boardHandling.undo();
          boardFrame.refreshWindows();
        });
    toolbarUndoButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarUndoButton", toolbarUndoButton.getText()));

    middleToolbar.add(toolbarUndoButton);

    toolbarRedoButton = new JButton();
    tm.setText(toolbarRedoButton, "redo_button");
    tagToolbarButton(toolbarRedoButton, GuiLocators.TOOLBAR_REDO);
    toolbarRedoButton.addActionListener(_ -> boardFrame.boardPanel.boardHandling.redo());
    toolbarRedoButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarRedoButton", toolbarRedoButton.getText()));

    middleToolbar.add(toolbarRedoButton);

    final JLabel separator1 = new JLabel();
    separator1.setMaximumSize(new Dimension(10, 10));
    separator1.setPreferredSize(new Dimension(10, 10));
    middleToolbar.add(separator1);

    toolbarIncompletesButton = new JButton();
    tm.setText(toolbarIncompletesButton, "incompletes_button");
    tagToolbarButton(toolbarIncompletesButton, GuiLocators.TOOLBAR_INCOMPLETES);
    toolbarIncompletesButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.toggleRatsnest());
    toolbarIncompletesButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarIncompletesButton", toolbarIncompletesButton.getText()));

    middleToolbar.add(toolbarIncompletesButton);

    toolbarViolationButton = new JButton();
    tm.setText(toolbarViolationButton, "violations_button");
    tagToolbarButton(toolbarViolationButton, GuiLocators.TOOLBAR_VIOLATIONS);
    toolbarViolationButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.toggleClearanceViolations());
    toolbarViolationButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarViolationButton", toolbarViolationButton.getText()));

    middleToolbar.add(toolbarViolationButton);

    final JLabel separator3 = new JLabel();
    separator3.setMaximumSize(new Dimension(10, 10));
    separator3.setPreferredSize(new Dimension(10, 10));
    separator3.setRequestFocusEnabled(false);
    middleToolbar.add(separator3);

    toolbarDisplayRegionButton = new JButton();
    tm.setText(toolbarDisplayRegionButton, "display_region_button");
    tagToolbarButton(toolbarDisplayRegionButton, GuiLocators.TOOLBAR_DISPLAY_REGION);
    toolbarDisplayRegionButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.zoomRegion());
    toolbarDisplayRegionButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayRegionButton", toolbarDisplayRegionButton.getText()));
    middleToolbar.add(toolbarDisplayRegionButton);

    toolbarDisplayAllButton = new JButton();
    tm.setText(toolbarDisplayAllButton, "display_all_button");
    tagToolbarButton(toolbarDisplayAllButton, GuiLocators.TOOLBAR_DISPLAY_ALL);
    toolbarDisplayAllButton.addActionListener(_ -> boardFrame.zoomAll());
    toolbarDisplayAllButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayAllButton", toolbarDisplayAllButton.getText()));
    middleToolbar.add(toolbarDisplayAllButton);

    this.add(middleToolbar, BorderLayout.CENTER);

    // Add Debug Controls if enabled
    if (Freerouting.globalSettings.debugSettings.singleStepExecution) {
      varsPlayButton = new JButton();
      varsPauseButton = new JButton();
      varsNextButton = new JButton();
      varsPreviousButton = new JButton();

      middleToolbar.addSeparator();

      // Previous Button
      tm.setText(varsPreviousButton, "debug_previous");
      if (varsPreviousButton.getText().startsWith("!")) {
        varsPreviousButton.setText("previous"); // Fallback
      }
      varsPreviousButton.addActionListener(_ -> handlePreviousStep());
      varsPreviousButton.setEnabled(false); // Initially disabled
      middleToolbar.add(varsPreviousButton);

      // Play Button
      tm.setText(varsPlayButton, "debug_play");
      if (varsPlayButton.getText().startsWith("!")) {
        varsPlayButton.setText("Play"); // Fallback
      }
      varsPlayButton.addActionListener(
          _ -> {
            app.freerouting.debug.DebugControl.getInstance().resume();
            varsPauseButton.setEnabled(true);
            varsPlayButton.setEnabled(false);
            varsNextButton.setEnabled(false);
            varsPreviousButton.setEnabled(false);
          });
      varsPlayButton.setEnabled(false); // Initially disabled
      middleToolbar.add(varsPlayButton);

      // Pause Button
      tm.setText(varsPauseButton, "debug_pause"); // Key needs to exist or fallback
      if (varsPauseButton.getText().startsWith("!")) {
        varsPauseButton.setText("Pause"); // Fallback
      }
      varsPauseButton.addActionListener(
          _ -> {
            app.freerouting.debug.DebugControl.getInstance().pause();
            updateDebugButtonsState();
          });
      varsPauseButton.setEnabled(false); // Initially disabled
      middleToolbar.add(varsPauseButton);

      // Next Button
      tm.setText(varsNextButton, "debug_next");
      if (varsNextButton.getText().startsWith("!")) {
        varsNextButton.setText("Next"); // Fallback
      }
      varsNextButton.addActionListener(_ -> handleNextStep());
      varsNextButton.setEnabled(false); // Initially disabled
      middleToolbar.add(varsNextButton);

      // Logic:
      // Running: Pause Enabled, Play Disabled, Next Disabled.
      // Paused: Pause Disabled, Play Enabled, Next Enabled.

      middleToolbar.addSeparator();
    }

    // create the right toolbar

    final JToolBar rightToolbar = new JToolBar();
    rightToolbar.setAutoscrolls(true);

    unitSelectionPanel =
        new SegmentedButtons(
            tm, tm.getText("unit_heading"), "unit_mil", "unit_inch", "unit_mm", "unit_um");
    A11y.tag(unitSelectionPanel, GuiLocators.TOOLBAR_UNIT_SELECT);
    A11y.describe(unitSelectionPanel, tm.getText("unit_heading"), null);
    tagSegmentButton(unitSelectionPanel, "unit_mil", GuiLocators.TOOLBAR_UNIT_MIL, tm);
    tagSegmentButton(unitSelectionPanel, "unit_inch", GuiLocators.TOOLBAR_UNIT_INCH, tm);
    tagSegmentButton(unitSelectionPanel, "unit_mm", GuiLocators.TOOLBAR_UNIT_MM, tm);
    tagSegmentButton(unitSelectionPanel, "unit_um", GuiLocators.TOOLBAR_UNIT_UM, tm);
    unitSelectionPanel.addValueChangedEventListener(
        (String value) -> {
          switch (value) {
            case "unit_mil":
              boardFrame.boardPanel.boardHandling.changeUserUnit(Unit.MIL);
              break;
            case "unit_inch":
              boardFrame.boardPanel.boardHandling.changeUserUnit(Unit.INCH);
              break;
            case "unit_mm":
              boardFrame.boardPanel.boardHandling.changeUserUnit(Unit.MM);
              break;
            case "unit_um":
              boardFrame.boardPanel.boardHandling.changeUserUnit(Unit.UM);
              break;
            default:
              break;
          }
          boardFrame.refreshWindows();
        });
    unitSelectionPanel.addValueChangedEventListener(
        (String value) -> FRAnalytics.buttonClicked("unitSelectionPanel", value));
    rightToolbar.add(unitSelectionPanel);

    this.add(rightToolbar, BorderLayout.EAST);

    // Set the font size for the toolbar icons
    changeToolbarFontSize(middleToolbar, iconFontSize);

    // Add listeners to enable/disable buttons based on the board read-only state
    boardFrame.addBoardLoadedEventListener(
        (RoutingBoard board) -> {
          if ((board == null) || (board.components.count() == 0)) {
            // disable all buttons if the board is empty
            setEnabled(false);
          }

          boardFrame.boardPanel.boardHandling.addReadOnlyEventListener(
              (Boolean isBoardReadOnly) -> {
                setEnabled(!isBoardReadOnly);
                cancelButton.setEnabled(isBoardReadOnly);
              });
        });
  }

  /**
   * Builds a component-only toolbar model for accessibility tests and headless embedders.
   *
   * <p>No board, frame, scheduler, or window is touched. Actions report their stable locator (or
   * segmented-button value) to the supplied listener, which makes keyboard/action paths observable
   * without coupling a test to GUI session state.
   *
   * @param locale locale for translated names and descriptions
   * @param includeInspectMode whether the Inspect mode button is included
   * @param actionListener receives the locator or value of an invoked control
   * @return a reusable toolbar component
   */
  static JPanel createComponentOnly(
      Locale locale, boolean includeInspectMode, Consumer<String> actionListener) {
    final Consumer<String> listener = actionListener == null ? _ -> {} : actionListener;
    TextManager tm = new TextManager(BoardToolbar.class, locale);
    JPanel toolbar = new JPanel(new BorderLayout());
    A11y.tag(toolbar, GuiLocators.TOOLBAR_ROOT);
    A11y.describe(toolbar, tm.getText("toolbar_accessible_name"), null);

    String[] modeValues =
        includeInspectMode
            ? new String[] {"inspect_button", "route_button", "drag_button"}
            : new String[] {"route_button", "drag_button"};
    SegmentedButtons mode = new SegmentedButtons(tm, tm.getText("mode_heading"), modeValues);
    A11y.tag(mode, GuiLocators.TOOLBAR_MODE_SELECT);
    A11y.describe(mode, tm.getText("mode_heading"), null);
    tagSegmentButton(mode, "inspect_button", GuiLocators.TOOLBAR_MODE_INSPECT, tm);
    tagSegmentButton(mode, "route_button", GuiLocators.TOOLBAR_MODE_ROUTE, tm);
    tagSegmentButton(mode, "drag_button", GuiLocators.TOOLBAR_MODE_DRAG, tm);
    mode.addValueChangedEventListener(listener);

    JPanel west = new JPanel();
    west.add(mode);
    toolbar.add(west, BorderLayout.WEST);

    JPanel center = new JPanel();
    addComponentOnlyButton(center, tm, "settingsButton", GuiLocators.TOOLBAR_SETTINGS, listener);
    addComponentOnlyButton(center, tm, "autoroute_button", GuiLocators.TOOLBAR_AUTOROUTE, listener);
    addComponentOnlyButton(center, tm, "cancelButton", GuiLocators.TOOLBAR_CANCEL, listener);
    addComponentOnlyButton(center, tm, "undo_button", GuiLocators.TOOLBAR_UNDO, listener);
    addComponentOnlyButton(center, tm, "redo_button", GuiLocators.TOOLBAR_REDO, listener);
    addComponentOnlyButton(
        center, tm, "incompletes_button", GuiLocators.TOOLBAR_INCOMPLETES, listener);
    addComponentOnlyButton(
        center, tm, "violations_button", GuiLocators.TOOLBAR_VIOLATIONS, listener);
    addComponentOnlyButton(
        center, tm, "display_region_button", GuiLocators.TOOLBAR_DISPLAY_REGION, listener);
    addComponentOnlyButton(
        center, tm, "display_all_button", GuiLocators.TOOLBAR_DISPLAY_ALL, listener);
    addComponentOnlyButton(
        center, tm, "delete_all_tracks_button", GuiLocators.TOOLBAR_DELETE_TRACKS, listener);
    toolbar.add(center, BorderLayout.CENTER);

    String[] unitValues = {"unit_mil", "unit_inch", "unit_mm", "unit_um"};
    SegmentedButtons units = new SegmentedButtons(tm, tm.getText("unit_heading"), unitValues);
    A11y.tag(units, GuiLocators.TOOLBAR_UNIT_SELECT);
    A11y.describe(units, tm.getText("unit_heading"), null);
    tagSegmentButton(units, "unit_mil", GuiLocators.TOOLBAR_UNIT_MIL, tm);
    tagSegmentButton(units, "unit_inch", GuiLocators.TOOLBAR_UNIT_INCH, tm);
    tagSegmentButton(units, "unit_mm", GuiLocators.TOOLBAR_UNIT_MM, tm);
    tagSegmentButton(units, "unit_um", GuiLocators.TOOLBAR_UNIT_UM, tm);
    units.addValueChangedEventListener(listener);
    JPanel east = new JPanel();
    east.add(units);
    toolbar.add(east, BorderLayout.EAST);
    return toolbar;
  }

  /**
   * Applies board-style enablement to every control in a component-only toolbar.
   *
   * <p>The cancel action follows the production toolbar convention and is enabled while the regular
   * controls are disabled.
   */
  static void setComponentOnlyEnabled(Container toolbar, boolean enabled) {
    String[] locators = {
      GuiLocators.TOOLBAR_MODE_SELECT,
      GuiLocators.TOOLBAR_MODE_INSPECT,
      GuiLocators.TOOLBAR_MODE_ROUTE,
      GuiLocators.TOOLBAR_MODE_DRAG,
      GuiLocators.TOOLBAR_UNIT_SELECT,
      GuiLocators.TOOLBAR_UNIT_MIL,
      GuiLocators.TOOLBAR_UNIT_INCH,
      GuiLocators.TOOLBAR_UNIT_MM,
      GuiLocators.TOOLBAR_UNIT_UM,
      GuiLocators.TOOLBAR_SETTINGS,
      GuiLocators.TOOLBAR_AUTOROUTE,
      GuiLocators.TOOLBAR_CANCEL,
      GuiLocators.TOOLBAR_UNDO,
      GuiLocators.TOOLBAR_REDO,
      GuiLocators.TOOLBAR_INCOMPLETES,
      GuiLocators.TOOLBAR_VIOLATIONS,
      GuiLocators.TOOLBAR_DISPLAY_REGION,
      GuiLocators.TOOLBAR_DISPLAY_ALL,
      GuiLocators.TOOLBAR_DELETE_TRACKS
    };
    for (String locator : locators) {
      Component component = A11y.findByLocator(toolbar, locator);
      if (component != null) {
        component.setEnabled(locator.equals(GuiLocators.TOOLBAR_CANCEL) ? !enabled : enabled);
      }
    }
  }

  private static void addComponentOnlyButton(
      Container parent, TextManager tm, String textKey, String locator, Consumer<String> listener) {
    JButton button = new JButton(tm.getText(textKey));
    String tooltip = tm.getText(textKey + "_tooltip");
    if (tooltip != null && !tooltip.isBlank() && !tooltip.equals("!" + textKey + "_tooltip!")) {
      button.setToolTipText(tooltip);
    }
    A11y.tag(button, locator);
    A11y.describe(
        button,
        button.getToolTipText() == null ? button.getText() : button.getToolTipText(),
        button.getToolTipText());
    button.addActionListener(_ -> listener.accept(locator));
    parent.add(button);
  }

  private static void tagSegmentButton(
      SegmentedButtons panel, String value, String locator, TextManager tm) {
    var button = panel.getButtonForValue(value);
    if (button == null) {
      return;
    }
    String tooltip = tm == null ? button.getToolTipText() : tm.getText(value + "_tooltip");
    A11y.tag(button, locator);
    A11y.describe(
        button,
        tooltip == null || tooltip.isBlank() || tooltip.equals("!" + value + "_tooltip!")
            ? button.getText()
            : tooltip,
        tooltip);
  }

  private static void tagToolbarButton(JButton button, String locator) {
    A11y.tag(button, locator);
    String accessibleName =
        button.getToolTipText() == null || button.getToolTipText().isBlank()
            ? button.getText()
            : button.getToolTipText();
    A11y.describe(button, accessibleName, button.getToolTipText());
  }

  private static void changeToolbarFontSize(JToolBar toolBar, float newSize) {
    for (Component comp : toolBar.getComponents()) {
      Font font = comp.getFont();
      // Create a new font based on the current font but with the new size
      Font newFont = font.deriveFont(newSize);
      comp.setFont(newFont);

      // If the component is a container, update its child components recursively
      if (comp instanceof Container container) {
        updateContainerFont(container, newFont);
      }
    }
  }

  private static void updateContainerFont(Container container, Font font) {
    for (Component child : container.getComponents()) {
      child.setFont(font);
      if (child instanceof Container container1) {
        updateContainerFont(container1, font);
      }
    }
  }

  public void setEnabled(boolean enabled) {
    modeSelectionPanel.setEnabled(enabled);
    settingsButton.setEnabled(enabled);
    toolbarAutorouteButton.setEnabled(enabled);
    cancelButton.setEnabled(!enabled);
    toolbarUndoButton.setEnabled(enabled);
    toolbarRedoButton.setEnabled(enabled);
    toolbarIncompletesButton.setEnabled(enabled);
    toolbarViolationButton.setEnabled(enabled);
    toolbarDisplayRegionButton.setEnabled(enabled);
    toolbarDisplayAllButton.setEnabled(enabled);
    unitSelectionPanel.setEnabled(enabled);
    deleteAllTracksButton.setEnabled(enabled);
  }

  /** Sets the selected button in the menu button group. */
  void setModeSelectionPanelValue(EditorStateHandle editorState) {
    if (editorState == null) {
      return;
    }
    if (editorState.kind() == EditorStateKind.MENU || editorState.kind() == EditorStateKind.ROUTE) {
      this.modeSelectionPanel.setSelectedValue("route_button");
    } else if (editorState.kind() == EditorStateKind.DRAG) {
      this.modeSelectionPanel.setSelectedValue("drag_button");
    } else if (editorState.kind() == EditorStateKind.INSPECT) {
      this.modeSelectionPanel.setSelectedValue("inspect_button");
    }
  }

  public void setUnitSelectionPanelValue(Unit unit) {
    switch (unit) {
      case MIL:
        this.unitSelectionPanel.setSelectedValue("unit_mil");
        break;
      case INCH:
        this.unitSelectionPanel.setSelectedValue("unit_inch");
        break;
      case MM:
        this.unitSelectionPanel.setSelectedValue("unit_mm");
        break;
      case UM:
        this.unitSelectionPanel.setSelectedValue("unit_um");
        break;
      default:
        break;
    }
  }

  private void setupKeyboardShortcuts() {
    this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "debugNext");
    this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "debugPrevious");
    this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK),
            "debugFastForward");
    this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK), "debugRewind");

    this.getActionMap()
        .put(
            "debugNext",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                if (varsNextButton != null && varsNextButton.isEnabled()) {
                  handleNextStep();
                }
              }
            });
    this.getActionMap()
        .put(
            "debugPrevious",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                if (varsPreviousButton != null && varsPreviousButton.isEnabled()) {
                  handlePreviousStep();
                }
              }
            });
    // Map Shift+Arrow to same handlers, the handlers check the Shift state or we
    // can pass a flag.
    // Actually the handler checks isShiftDown global flag or we can just call the
    // shifted logic directly.
    this.getActionMap()
        .put(
            "debugFastForward",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                if (varsNextButton != null && varsNextButton.isEnabled()) {
                  // Ensure we force "shift" behavior even if key dispatcher missed something
                  // (redundancy)
                  isShiftDown = true;
                  try {
                    handleNextStep();
                  } finally {
                    // We don't want to permanently set it true if the user actually held it,
                    // but if this action triggered, Shift IS down.
                    // The KeyDispatcher handles synchronization, but for the action context let's
                    // be safe.
                  }
                }
              }
            });
    this.getActionMap()
        .put(
            "debugRewind",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                if (varsPreviousButton != null && varsPreviousButton.isEnabled()) {
                  isShiftDown = true;
                  handlePreviousStep();
                }
              }
            });
  }

  private void handleNextStep() {
    if (isShiftDown) {
      app.freerouting.debug.DebugControl.getInstance().convertToFastForward();
      updateDebugButtonsState();
    } else {
      app.freerouting.debug.DebugControl.getInstance().next();
    }
  }

  private void handlePreviousStep() {
    var debugControl = app.freerouting.debug.DebugControl.getInstance();
    if (isShiftDown) {
      int targetNet = debugControl.peekLastStepNet();
      // Rewind while the net is the same
      while (debugControl.shouldContinueRewind(targetNet)) {
        boardFrame.boardPanel.boardHandling.cancelState();
        boardFrame.boardPanel.boardHandling.undo();
        debugControl.popLastStepNet();
        // Update stats
      }
      boardFrame.refreshWindows();
    } else {
      // Single Step Back
      boardFrame.boardPanel.boardHandling.cancelState();
      boardFrame.boardPanel.boardHandling.undo();
      debugControl.popLastStepNet();
      boardFrame.refreshWindows();
    }
  }

  private void updateDebugButtonsState() {
    boolean isPaused = app.freerouting.debug.DebugControl.getInstance().isPaused();
    if (varsPauseButton != null) {
      varsPauseButton.setEnabled(!isPaused);
    }
    if (varsPlayButton != null) {
      varsPlayButton.setEnabled(isPaused);
    }
    if (varsNextButton != null) {
      varsNextButton.setEnabled(isPaused);
    }
    if (varsPreviousButton != null) {
      varsPreviousButton.setEnabled(isPaused);
    }
  }

  private void updateDebugIcons() {
    if (varsNextButton == null || varsPreviousButton == null) {
      return;
    }

    GuiTextManager tm = new GuiTextManager(this.getClass(), boardFrame.get_locale());
    if (isShiftDown) {
      tm.setText(varsNextButton, "debug_fast_forward");
      tm.setText(varsPreviousButton, "debug_rewind");
    } else {
      tm.setText(varsNextButton, "debug_next");
      if (varsNextButton.getText().startsWith("!")) {
        varsNextButton.setText("Next");
      }

      tm.setText(varsPreviousButton, "debug_previous");
      if (varsPreviousButton.getText().startsWith("!")) {
        varsPreviousButton.setText("Previous");
      }
    }

    // Reset fonts to the icon size AFTER TextManager (which scales them)
    // to avoid them being larger than the rest of the toolbar
    Font nextFont = varsNextButton.getFont().deriveFont(iconFontSize);
    varsNextButton.setFont(nextFont);

    Font prevFont = varsPreviousButton.getFont().deriveFont(iconFontSize);
    varsPreviousButton.setFont(prevFont);
  }
}
