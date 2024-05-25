package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.interactive.*;
import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;

/**
 * Implements the toolbar panel of the board frame.
 */
class BoardToolbar extends JPanel
{
  private final float ICON_FONT_SIZE = 26.0f;
  private final SegmentedButtons modeSelectionPanel;
  private final JButton settings_button;
  private final JButton toolbar_autoroute_button;
  private final JButton cancel_button;
  private final JButton toolbar_undo_button;
  private final JButton toolbar_redo_button;
  private final JButton toolbar_incompletes_button;
  private final JButton toolbar_violation_button;
  private final JButton toolbar_display_region_button;
  private final JButton toolbar_display_all_button;
  private final SegmentedButtons unitSelectionPanel;
  private final JButton delete_all_tracks_button;
  private final BoardFrame board_frame;

  /**
   * Creates a new instance of BoardToolbarPanel
   */
  BoardToolbar(BoardFrame p_board_frame, boolean p_disable_select_mode)
  {
    this.board_frame = p_board_frame;

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    this.setLayout(new BorderLayout());

    // create the left toolbar

    final JToolBar left_toolbar = new JToolBar();

    left_toolbar.setMaximumSize(new Dimension(1200, 30));

    if (!p_disable_select_mode)
    {
      modeSelectionPanel = new SegmentedButtons(tm, "Mode", "select_button", "route_button", "drag_button");
    }
    else
    {
      modeSelectionPanel = new SegmentedButtons(tm, "Mode", "route_button", "drag_button");
    }
    modeSelectionPanel.addValueChangedEventListener((String value) ->
    {
      switch (value)
      {
        case "select_button":
          board_frame.board_panel.board_handling.set_select_menu_state();
          break;
        case "route_button":
          board_frame.board_panel.board_handling.set_route_menu_state();
          break;
        case "drag_button":
          board_frame.board_panel.board_handling.set_drag_menu_state();
          break;
      }
    });
    modeSelectionPanel.addValueChangedEventListener((String value) -> FRAnalytics.buttonClicked("modeSelectionPanel", value));
    left_toolbar.add(modeSelectionPanel, BorderLayout.CENTER);

    this.add(left_toolbar, BorderLayout.WEST);

    // create the middle toolbar

    final JToolBar middle_toolbar = new JToolBar();

    // Add "Settings" button to the toolbar
    settings_button = new JButton();
    tm.setText(settings_button, "settings_button");
    settings_button.addActionListener(evt ->
    {
      board_frame.autoroute_parameter_window.setVisible(true);
    });
    settings_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_button", settings_button.getText()));
    middle_toolbar.add(settings_button);

    // Add "Autoroute" button to the toolbar
    toolbar_autoroute_button = new JButton();
    tm.setText(toolbar_autoroute_button, "autoroute_button");
    toolbar_autoroute_button.setDefaultCapable(true);
    Font currentFont = toolbar_autoroute_button.getFont();
    Font boldFont = new Font(currentFont.getFontName(), Font.BOLD, currentFont.getSize());
    toolbar_autoroute_button.setFont(boldFont);
    toolbar_autoroute_button.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
    // Set padding (top, left, bottom, right)
    toolbar_autoroute_button.setBorder(BorderFactory.createCompoundBorder(toolbar_autoroute_button.getBorder(), BorderFactory.createEmptyBorder(2, 5, 2, 5)));
    toolbar_autoroute_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    toolbar_autoroute_button.addActionListener(evt ->
    {
      InteractiveActionThread thread = board_frame.board_panel.board_handling.start_autorouter_and_route_optimizer();

      if ((thread != null) && (board_frame.board_panel.board_handling.autorouter_listener != null))
      {
        // Add the auto-router listener to save the design file when the auto-router is running
        thread.addListener(board_frame.board_panel.board_handling.autorouter_listener);
      }
    });
    toolbar_autoroute_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_autoroute_button", toolbar_autoroute_button.getText()));
    middle_toolbar.add(toolbar_autoroute_button);

    // Add "Cancel" button to the toolbar
    cancel_button = new JButton();
    tm.setText(cancel_button, "cancel_button");
    cancel_button.addActionListener(evt ->
    {
      board_frame.board_panel.board_handling.stop_autorouter_and_route_optimizer();
    });
    cancel_button.addActionListener(evt -> FRAnalytics.buttonClicked("cancel_button", cancel_button.getText()));
    cancel_button.setEnabled(false);
    middle_toolbar.add(cancel_button);

    // Add "Delete All Tracks and Vias" button to the toolbar
    delete_all_tracks_button = new JButton();
    tm.setText(delete_all_tracks_button, "delete_all_tracks_button");
    delete_all_tracks_button.addActionListener(evt ->
    {
      RoutingBoard board = board_frame.board_panel.board_handling.get_routing_board();
      // delete all tracks and vias
      board.delete_all_tracks_and_vias();
      // update the board
      board_frame.board_panel.board_handling.update_routing_board(board);
      // create a deep copy of the routing board
      board = board_frame.board_panel.board_handling.deep_copy_routing_board();
      // update the board again
      board_frame.board_panel.board_handling.update_routing_board(board);
      // create ratsnest
      board_frame.board_panel.board_handling.create_ratsnest();
      // redraw the board
      board_frame.board_panel.board_handling.repaint();
    });
    delete_all_tracks_button.addActionListener(evt -> FRAnalytics.buttonClicked("delete_all_tracks_button", delete_all_tracks_button.getText()));
    middle_toolbar.add(delete_all_tracks_button);

    final JLabel separator_2 = new JLabel();
    separator_2.setMaximumSize(new Dimension(10, 10));
    separator_2.setPreferredSize(new Dimension(10, 10));
    separator_2.setRequestFocusEnabled(false);
    middle_toolbar.add(separator_2);

    toolbar_undo_button = new JButton();
    tm.setText(toolbar_undo_button, "undo_button");
    toolbar_undo_button.addActionListener(evt ->
    {
      board_frame.board_panel.board_handling.cancel_state();
      board_frame.board_panel.board_handling.undo();
      board_frame.refresh_windows();
    });
    toolbar_undo_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_undo_button", toolbar_undo_button.getText()));

    middle_toolbar.add(toolbar_undo_button);

    toolbar_redo_button = new JButton();
    tm.setText(toolbar_redo_button, "redo_button");
    toolbar_redo_button.addActionListener(evt -> board_frame.board_panel.board_handling.redo());
    toolbar_redo_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_redo_button", toolbar_redo_button.getText()));

    middle_toolbar.add(toolbar_redo_button);

    final JLabel separator_1 = new JLabel();
    separator_1.setMaximumSize(new Dimension(10, 10));
    separator_1.setPreferredSize(new Dimension(10, 10));
    middle_toolbar.add(separator_1);

    toolbar_incompletes_button = new JButton();
    tm.setText(toolbar_incompletes_button, "incompletes_button");
    toolbar_incompletes_button.addActionListener(evt -> board_frame.board_panel.board_handling.toggle_ratsnest());
    toolbar_incompletes_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_incompletes_button", toolbar_incompletes_button.getText()));

    middle_toolbar.add(toolbar_incompletes_button);

    toolbar_violation_button = new JButton();
    tm.setText(toolbar_violation_button, "violations_button");
    toolbar_violation_button.addActionListener(evt -> board_frame.board_panel.board_handling.toggle_clearance_violations());
    toolbar_violation_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_violation_button", toolbar_violation_button.getText()));

    middle_toolbar.add(toolbar_violation_button);

    final JLabel separator_3 = new JLabel();
    separator_3.setMaximumSize(new Dimension(10, 10));
    separator_3.setPreferredSize(new Dimension(10, 10));
    separator_3.setRequestFocusEnabled(false);
    middle_toolbar.add(separator_3);

    toolbar_display_region_button = new JButton();
    tm.setText(toolbar_display_region_button, "display_region_button");
    toolbar_display_region_button.addActionListener(evt -> board_frame.board_panel.board_handling.zoom_region());
    toolbar_display_region_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_region_button", toolbar_display_region_button.getText()));
    middle_toolbar.add(toolbar_display_region_button);

    toolbar_display_all_button = new JButton();
    tm.setText(toolbar_display_all_button, "display_all_button");
    toolbar_display_all_button.addActionListener(evt -> board_frame.zoom_all());
    toolbar_display_all_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_all_button", toolbar_display_all_button.getText()));
    middle_toolbar.add(toolbar_display_all_button);

    this.add(middle_toolbar, BorderLayout.CENTER);

    // create the right toolbar

    final JToolBar right_toolbar = new JToolBar();
    right_toolbar.setAutoscrolls(true);


    unitSelectionPanel = new SegmentedButtons(tm, "Unit", "unit_mil", "unit_inch", "unit_mm", "unit_um");
    unitSelectionPanel.addValueChangedEventListener((String value) ->
    {
      switch (value)
      {
        case "unit_mil":
          board_frame.board_panel.board_handling.change_user_unit(Unit.MIL);
          break;
        case "unit_inch":
          board_frame.board_panel.board_handling.change_user_unit(Unit.INCH);
          break;
        case "unit_mm":
          board_frame.board_panel.board_handling.change_user_unit(Unit.MM);
          break;
        case "unit_um":
          board_frame.board_panel.board_handling.change_user_unit(Unit.UM);
          break;
      }
      board_frame.refresh_windows();
    });
    unitSelectionPanel.addValueChangedEventListener((String value) -> FRAnalytics.buttonClicked("unitSelectionPanel", value));
    right_toolbar.add(unitSelectionPanel);


    this.add(right_toolbar, BorderLayout.EAST);

    // Set the font size for the toolbar icons
    changeToolbarFontSize(middle_toolbar, ICON_FONT_SIZE);

    // Add listeners to enable/disable buttons based on the board read-only state
    board_frame.addBoardLoadedEventListener((RoutingBoard board) ->
    {
      if ((board == null) || (board.components.count() == 0))
      {
        // disable all buttons if the board is empty
        setEnabled(false);
      }

      board_frame.board_panel.board_handling.addReadOnlyEventListener((Boolean isBoardReadOnly) ->
      {
        setEnabled(!isBoardReadOnly);
        cancel_button.setEnabled(isBoardReadOnly);
      });
    });
  }

  private static void changeToolbarFontSize(JToolBar toolBar, float newSize)
  {
    for (Component comp : toolBar.getComponents())
    {
      Font font = comp.getFont();
      // Create a new font based on the current font but with the new size
      Font newFont = font.deriveFont(newSize);
      comp.setFont(newFont);

      // If the component is a container, update its child components recursively
      if (comp instanceof Container)
      {
        updateContainerFont((Container) comp, newFont);
      }
    }
  }

  private static void updateContainerFont(Container container, Font font)
  {
    for (Component child : container.getComponents())
    {
      child.setFont(font);
      if (child instanceof Container)
      {
        updateContainerFont((Container) child, font);
      }
    }
  }

  public void setEnabled(boolean enabled)
  {
    modeSelectionPanel.setEnabled(enabled);
    settings_button.setEnabled(enabled);
    toolbar_autoroute_button.setEnabled(enabled);
    cancel_button.setEnabled(!enabled);
    toolbar_undo_button.setEnabled(enabled);
    toolbar_redo_button.setEnabled(enabled);
    toolbar_incompletes_button.setEnabled(enabled);
    toolbar_violation_button.setEnabled(enabled);
    toolbar_display_region_button.setEnabled(enabled);
    toolbar_display_all_button.setEnabled(enabled);
    unitSelectionPanel.setEnabled(enabled);
    delete_all_tracks_button.setEnabled(enabled);
  }

  /**
   * Sets the selected button in the menu button group
   */
  void setModeSelectionPanelValue(InteractiveState interactive_state)
  {
    if (interactive_state instanceof RouteMenuState)
    {
      this.modeSelectionPanel.setSelectedValue("route_button");
    }
    else if (interactive_state instanceof DragMenuState)
    {
      this.modeSelectionPanel.setSelectedValue("drag_button");
    }
    else if (interactive_state instanceof SelectMenuState)
    {
      this.modeSelectionPanel.setSelectedValue("select_button");
    }
  }

  public void setUnitSelectionPanelValue(Unit unit)
  {
    switch (unit)
    {
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
    }
  }
}