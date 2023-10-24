package app.freerouting.gui;

import app.freerouting.board.Unit;
import app.freerouting.interactive.DragMenuState;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.RouteMenuState;
import app.freerouting.interactive.SelectMenuState;

import app.freerouting.management.FRAnalytics;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ResourceBundle;
import javax.swing.border.BevelBorder;

/** Implements the toolbar panel of the board frame. */
class BoardToolbar extends JPanel {

  final JFormattedTextField unit_factor_field;
  final JComboBox<Unit> toolbar_unit_combo_box;
  private final BoardFrame board_frame;
  private final JToggleButton toolbar_select_button;
  private final JToggleButton toolbar_route_button;
  private final JToggleButton toolbar_drag_button;
  /** Creates a new instance of BoardToolbarPanel */
  BoardToolbar(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;

    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardToolbar", p_board_frame.get_locale());

    this.setLayout(new BorderLayout());

    // create the left toolbar

    final JToolBar left_toolbar = new JToolBar();
    final ButtonGroup toolbar_button_group = new ButtonGroup();
    this.toolbar_select_button = new JToggleButton();
    this.toolbar_route_button = new JToggleButton();
    this.toolbar_drag_button = new JToggleButton();
    final JLabel jLabel1 = new JLabel();

    left_toolbar.setMaximumSize(new Dimension(1200, 30));
    toolbar_button_group.add(toolbar_select_button);
    toolbar_select_button.setSelected(true);
    toolbar_select_button.setText(resources.getString("select_button"));
    toolbar_select_button.setToolTipText(resources.getString("select_button_tooltip"));
    toolbar_select_button.addActionListener(evt -> board_frame.board_panel.board_handling.set_select_menu_state());
    toolbar_select_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_select_button", toolbar_select_button.getText()));

    left_toolbar.add(toolbar_select_button);

    toolbar_button_group.add(toolbar_route_button);
    toolbar_route_button.setText(resources.getString("route_button"));
    toolbar_route_button.setToolTipText(resources.getString("route_button_tooltip"));
    toolbar_route_button.addActionListener(evt -> board_frame.board_panel.board_handling.set_route_menu_state());
    toolbar_route_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_route_button", toolbar_route_button.getText()));

    left_toolbar.add(toolbar_route_button);

    toolbar_button_group.add(toolbar_drag_button);
    toolbar_drag_button.setText(resources.getString("drag_button"));
    toolbar_drag_button.setToolTipText(resources.getString("drag_button_tooltip"));
    toolbar_drag_button.addActionListener(evt -> board_frame.board_panel.board_handling.set_drag_menu_state());
    toolbar_drag_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_drag_button", toolbar_drag_button.getText()));

    left_toolbar.add(toolbar_drag_button);

    jLabel1.setMaximumSize(new Dimension(30, 10));
    jLabel1.setMinimumSize(new Dimension(3, 10));
    jLabel1.setPreferredSize(new Dimension(30, 10));
    left_toolbar.add(jLabel1);

    this.add(left_toolbar, BorderLayout.WEST);

    // create the middle toolbar

    final JToolBar middle_toolbar = new JToolBar();

    final JButton toolbar_autoroute_button = new JButton();
    toolbar_autoroute_button.setText(resources.getString("autoroute_button"));
    toolbar_autoroute_button.setToolTipText(resources.getString("autoroute_button_tooltip"));
    toolbar_autoroute_button.setDefaultCapable(true);
    Font currentFont = toolbar_autoroute_button.getFont();
    Font boldFont = new Font(currentFont.getFontName(), Font.BOLD, currentFont.getSize());
    toolbar_autoroute_button.setFont(boldFont);
    toolbar_autoroute_button.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
    // Set padding (top, left, bottom, right)
    toolbar_autoroute_button.setBorder(BorderFactory.createCompoundBorder(
        toolbar_autoroute_button.getBorder(),
        BorderFactory.createEmptyBorder(2, 5, 2, 5)
    ));
    toolbar_autoroute_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    toolbar_autoroute_button.addActionListener(
        evt -> {
          InteractiveActionThread thread = board_frame.board_panel.board_handling.start_batch_autorouter();

          if (board_frame.board_panel.board_handling.autorouter_listener != null) {
            // Add the auto-router listener to save the design file when the auto-router is running
            thread.addListener(board_frame.board_panel.board_handling.autorouter_listener);
          }
        });
    toolbar_autoroute_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_autoroute_button", toolbar_autoroute_button.getText()));

    middle_toolbar.add(toolbar_autoroute_button);

    final JLabel separator_2 = new JLabel();
    separator_2.setMaximumSize(new Dimension(10, 10));
    separator_2.setPreferredSize(new Dimension(10, 10));
    separator_2.setRequestFocusEnabled(false);
    middle_toolbar.add(separator_2);

    final JButton toolbar_undo_button = new JButton();
    toolbar_undo_button.setText(resources.getString("undo_button"));
    toolbar_undo_button.setToolTipText(resources.getString("undo_button_tooltip"));
    toolbar_undo_button.addActionListener(
        evt -> {
          board_frame.board_panel.board_handling.cancel_state();
          board_frame.board_panel.board_handling.undo();
          board_frame.refresh_windows();
        });
    toolbar_undo_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_undo_button", toolbar_undo_button.getText()));

    middle_toolbar.add(toolbar_undo_button);

    final JButton toolbar_redo_button = new JButton();
    toolbar_redo_button.setText(resources.getString("redo_button"));
    toolbar_redo_button.setToolTipText(resources.getString("redo_button_tooltip"));
    toolbar_redo_button.addActionListener(evt -> board_frame.board_panel.board_handling.redo());
    toolbar_redo_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_redo_button", toolbar_redo_button.getText()));

    middle_toolbar.add(toolbar_redo_button);

    final JLabel separator_1 = new JLabel();
    separator_1.setMaximumSize(new Dimension(10, 10));
    separator_1.setPreferredSize(new Dimension(10, 10));
    middle_toolbar.add(separator_1);

    final JButton toolbar_incompletes_button = new JButton();
    toolbar_incompletes_button.setText(resources.getString("incompletes_button"));
    toolbar_incompletes_button.setToolTipText(resources.getString("incompletes_button_tooltip"));
    toolbar_incompletes_button.addActionListener(evt -> board_frame.board_panel.board_handling.toggle_ratsnest());
    toolbar_incompletes_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_incompletes_button", toolbar_incompletes_button.getText()));

    middle_toolbar.add(toolbar_incompletes_button);

    final JButton toolbar_violation_button = new JButton();
    toolbar_violation_button.setText(resources.getString("violations_button"));
    toolbar_violation_button.setToolTipText(resources.getString("violations_button_tooltip"));
    toolbar_violation_button.addActionListener(evt -> board_frame.board_panel.board_handling.toggle_clearance_violations());
    toolbar_violation_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_violation_button", toolbar_violation_button.getText()));

    middle_toolbar.add(toolbar_violation_button);

    final JLabel separator_3 = new JLabel();
    separator_3.setMaximumSize(new Dimension(10, 10));
    separator_3.setPreferredSize(new Dimension(10, 10));
    separator_3.setRequestFocusEnabled(false);
    middle_toolbar.add(separator_3);

    final JButton toolbar_display_all_button = new JButton();
    toolbar_display_all_button.setText(resources.getString("display_all_button"));
    toolbar_display_all_button.setToolTipText(resources.getString("display_all_button_tooltip"));
    toolbar_display_all_button.addActionListener(evt -> board_frame.zoom_all());
    toolbar_display_all_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_all_button", toolbar_display_all_button.getText()));

    middle_toolbar.add(toolbar_display_all_button);

    final JButton toolbar_display_region_button = new JButton();
    toolbar_display_region_button.setText(resources.getString("display_region_button"));
    toolbar_display_region_button.setToolTipText(resources.getString("display_region_button_tooltip"));
    toolbar_display_region_button.addActionListener(evt -> board_frame.board_panel.board_handling.zoom_region());
    toolbar_display_region_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_region_button", toolbar_display_region_button.getText()));

    middle_toolbar.add(toolbar_display_region_button);

    this.add(middle_toolbar, BorderLayout.CENTER);

    // create the right toolbar

    final JToolBar right_toolbar = new JToolBar();
    final JLabel unit_label = new JLabel();
    NumberFormat number_format =
        NumberFormat.getInstance(p_board_frame.get_locale());
    number_format.setMaximumFractionDigits(7);
    this.unit_factor_field = new JFormattedTextField(number_format);
    final JLabel jLabel4 = new JLabel();

    right_toolbar.setAutoscrolls(true);
    unit_label.setText(resources.getString("unit_button"));
    right_toolbar.add(unit_label);

    unit_factor_field.setHorizontalAlignment(JTextField.CENTER);
    unit_factor_field.setValue(1);
    unit_factor_field.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent evt) {
            if (evt.getKeyChar() == '\n') {
              Object input = unit_factor_field.getValue();
              if (input instanceof Number) {
                double input_value = ((Number) input).doubleValue();
                if (input_value > 0) {
                  board_frame.board_panel.board_handling.change_user_unit_factor(input_value);
                }
              }
              double unit_factor =
                  board_frame.board_panel.board_handling.coordinate_transform.user_unit_factor;
              unit_factor_field.setValue(unit_factor);

              board_frame.refresh_windows();
            }
          }
        });

    right_toolbar.add(unit_factor_field);

    toolbar_unit_combo_box = new JComboBox<>();
    toolbar_unit_combo_box.setModel(new DefaultComboBoxModel<>(Unit.values()));
    toolbar_unit_combo_box.setFocusTraversalPolicyProvider(true);
    toolbar_unit_combo_box.setInheritsPopupMenu(true);
    toolbar_unit_combo_box.setOpaque(false);
    toolbar_unit_combo_box.addActionListener(
        evt -> {
          Unit new_unit =
              (Unit) toolbar_unit_combo_box.getSelectedItem();
          board_frame.board_panel.board_handling.change_user_unit(new_unit);
          board_frame.refresh_windows();
        });
    toolbar_unit_combo_box.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_unit_combo_box", ((Unit) toolbar_unit_combo_box.getSelectedItem()).name()));

    right_toolbar.add(toolbar_unit_combo_box);

    jLabel4.setMaximumSize(new Dimension(30, 14));
    jLabel4.setPreferredSize(new Dimension(30, 14));
    right_toolbar.add(jLabel4);

    this.add(right_toolbar, BorderLayout.EAST);
  }

  /** Sets the selected button in the menu button group */
  void hilight_selected_button() {
    InteractiveState interactive_state =
        this.board_frame.board_panel.board_handling.get_interactive_state();
    if (interactive_state instanceof RouteMenuState) {
      this.toolbar_route_button.setSelected(true);
    } else if (interactive_state instanceof DragMenuState) {
      this.toolbar_drag_button.setSelected(true);
    } else if (interactive_state instanceof SelectMenuState) {
      this.toolbar_select_button.setSelected(true);
    }
  }
}
