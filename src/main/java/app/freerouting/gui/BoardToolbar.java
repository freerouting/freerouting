package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.gui.interactive.DragMenuState;
import app.freerouting.gui.interactive.InspectMenuState;
import app.freerouting.gui.interactive.InteractiveActionThread;
import app.freerouting.gui.interactive.InteractiveState;
import app.freerouting.gui.interactive.RouteMenuState;
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

    TextManager tm = new TextManager(this.getClass(), boardFrame.get_locale());

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
    settingsButton.addActionListener(_ -> boardFrame.autorouteParameterWindow.setVisible(true));
    settingsButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("settingsButton", settingsButton.getText()));
    middleToolbar.add(settingsButton);

    // Add "Autoroute" button to the toolbar
    toolbarAutorouteButton = new JButton();
    tm.setText(toolbarAutorouteButton, "autoroute_button");
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
            var interactiveSettings = boardFrame.boardPanel.boardHandling.getInteractiveSettings();
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
    cancelButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.stopAutorouterAndRouteOptimizer());
    cancelButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("cancelButton", cancelButton.getText()));
    cancelButton.setEnabled(false);
    middleToolbar.add(cancelButton);

    // Add "Delete All Tracks and Vias" button to the toolbar
    deleteAllTracksButton = new JButton();
    tm.setText(deleteAllTracksButton, "deleteAllTracksButton");
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
    toolbarIncompletesButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.toggleRatsnest());
    toolbarIncompletesButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarIncompletesButton", toolbarIncompletesButton.getText()));

    middleToolbar.add(toolbarIncompletesButton);

    toolbarViolationButton = new JButton();
    tm.setText(toolbarViolationButton, "violations_button");
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
    toolbarDisplayRegionButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.zoomRegion());
    toolbarDisplayRegionButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayRegionButton", toolbarDisplayRegionButton.getText()));
    middleToolbar.add(toolbarDisplayRegionButton);

    toolbarDisplayAllButton = new JButton();
    tm.setText(toolbarDisplayAllButton, "display_all_button");
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
  void setModeSelectionPanelValue(InteractiveState interactiveState) {
    if (interactiveState instanceof RouteMenuState) {
      this.modeSelectionPanel.setSelectedValue("route_button");
    } else if (interactiveState instanceof DragMenuState) {
      this.modeSelectionPanel.setSelectedValue("drag_button");
    } else if (interactiveState instanceof InspectMenuState) {
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

    TextManager tm = new TextManager(this.getClass(), boardFrame.get_locale());
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
