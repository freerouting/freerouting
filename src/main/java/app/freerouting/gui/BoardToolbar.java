package app.freerouting.gui;

import app.freerouting.board.Unit;
import app.freerouting.interactive.DragMenuState;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.RouteMenuState;
import app.freerouting.interactive.SelectMenuState;

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

/** Implements the toolbar panel of the board frame. */
class BoardToolbar extends JPanel {

  final JFormattedTextField unit_factor_field;
  final JComboBox<Unit> unit_combo_box;
  private final BoardFrame board_frame;
  private final JToggleButton select_button;
  private final JToggleButton route_button;
  private final JToggleButton drag_button;
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
    this.select_button = new JToggleButton();
    this.route_button = new JToggleButton();
    this.drag_button = new JToggleButton();
    final JLabel jLabel1 = new JLabel();

    left_toolbar.setMaximumSize(new Dimension(1200, 30));
    toolbar_button_group.add(select_button);
    select_button.setSelected(true);
    select_button.setText(resources.getString("select_button"));
    select_button.setToolTipText(resources.getString("select_button_tooltip"));
    select_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.set_select_menu_state());

    left_toolbar.add(select_button);

    toolbar_button_group.add(route_button);
    route_button.setText(resources.getString("route_button"));
    route_button.setToolTipText(resources.getString("route_button_tooltip"));
    route_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.set_route_menu_state());

    left_toolbar.add(route_button);

    toolbar_button_group.add(drag_button);
    drag_button.setText(resources.getString("drag_button"));
    drag_button.setToolTipText(resources.getString("drag_button_tooltip"));
    drag_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.set_drag_menu_state());

    left_toolbar.add(drag_button);

    jLabel1.setMaximumSize(new Dimension(30, 10));
    jLabel1.setMinimumSize(new Dimension(3, 10));
    jLabel1.setPreferredSize(new Dimension(30, 10));
    left_toolbar.add(jLabel1);

    this.add(left_toolbar, BorderLayout.WEST);

    // create the middle toolbar

    final JToolBar middle_toolbar = new JToolBar();

    final JButton autoroute_button = new JButton();
    autoroute_button.setText(resources.getString("autoroute_button"));
    autoroute_button.setToolTipText(resources.getString("autoroute_button_tooltip"));
    autoroute_button.addActionListener(
        evt -> {
          InteractiveActionThread thread = board_frame.board_panel.board_handling.start_batch_autorouter();

          if (board_frame.board_panel.board_handling.autorouter_listener != null) {
            // Add the autorouter listener to save the design file when the autorouter is running
            thread.addListener(board_frame.board_panel.board_handling.autorouter_listener);
          }
        });

    middle_toolbar.add(autoroute_button);

    final JLabel separator_2 = new JLabel();
    separator_2.setMaximumSize(new Dimension(10, 10));
    separator_2.setPreferredSize(new Dimension(10, 10));
    separator_2.setRequestFocusEnabled(false);
    middle_toolbar.add(separator_2);

    final JButton undo_button = new JButton();
    undo_button.setText(resources.getString("undo_button"));
    undo_button.setToolTipText(resources.getString("undo_button_tooltip"));
    undo_button.addActionListener(
        evt -> {
          board_frame.board_panel.board_handling.cancel_state();
          board_frame.board_panel.board_handling.undo();
          board_frame.refresh_windows();
        });

    middle_toolbar.add(undo_button);

    final JButton redo_button = new JButton();
    redo_button.setText(resources.getString("redo_button"));
    redo_button.setToolTipText(resources.getString("redo_button_tooltip"));
    redo_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.redo());

    middle_toolbar.add(redo_button);

    final JLabel separator_1 = new JLabel();
    separator_1.setMaximumSize(new Dimension(10, 10));
    separator_1.setPreferredSize(new Dimension(10, 10));
    middle_toolbar.add(separator_1);

    final JButton incompletes_button = new JButton();
    incompletes_button.setText(resources.getString("incompletes_button"));
    incompletes_button.setToolTipText(resources.getString("incompletes_button_tooltip"));
    incompletes_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.toggle_ratsnest());

    middle_toolbar.add(incompletes_button);

    final JButton violation_button = new JButton();
    violation_button.setText(resources.getString("violations_button"));
    violation_button.setToolTipText(resources.getString("violations_button_tooltip"));
    violation_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.toggle_clearance_violations());

    middle_toolbar.add(violation_button);

    final JLabel separator_3 = new JLabel();
    separator_3.setMaximumSize(new Dimension(10, 10));
    separator_3.setPreferredSize(new Dimension(10, 10));
    separator_3.setRequestFocusEnabled(false);
    middle_toolbar.add(separator_3);

    final JButton display_all_button = new JButton();
    display_all_button.setText(resources.getString("display_all_button"));
    display_all_button.setToolTipText(resources.getString("display_all_button_tooltip"));
    display_all_button.addActionListener(evt -> board_frame.zoom_all());

    middle_toolbar.add(display_all_button);

    final JButton display_region_button = new JButton();
    display_region_button.setText(resources.getString("display_region_button"));
    display_region_button.setToolTipText(resources.getString("display_region_button_tooltip"));
    display_region_button.addActionListener(
        evt -> board_frame.board_panel.board_handling.zoom_region());

    middle_toolbar.add(display_region_button);

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
    unit_label.setMaximumSize(new Dimension(100, 30));
    unit_label.setPreferredSize(new Dimension(65, 30));
    right_toolbar.add(unit_label);

    unit_factor_field.setHorizontalAlignment(JTextField.CENTER);
    unit_factor_field.setValue(1);
    unit_factor_field.setMaximumSize(new Dimension(10, 30));
    unit_factor_field.setMinimumSize(new Dimension(20, 30));
    unit_factor_field.setPreferredSize(new Dimension(40, 30));
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

    unit_combo_box = new JComboBox<>();
    unit_combo_box.setModel(new DefaultComboBoxModel<>(Unit.values()));
    unit_combo_box.setFocusTraversalPolicyProvider(true);
    unit_combo_box.setInheritsPopupMenu(true);
    unit_combo_box.setMaximumSize(new Dimension(80, 30));
    unit_combo_box.setMinimumSize(new Dimension(80, 30));
    unit_combo_box.setOpaque(false);
    unit_combo_box.setPreferredSize(new Dimension(80, 30));
    unit_combo_box.addActionListener(
        evt -> {
          Unit new_unit =
              (Unit) unit_combo_box.getSelectedItem();
          board_frame.board_panel.board_handling.change_user_unit(new_unit);
          board_frame.refresh_windows();
        });

    right_toolbar.add(unit_combo_box);

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
      this.route_button.setSelected(true);
    } else if (interactive_state instanceof DragMenuState) {
      this.drag_button.setSelected(true);
    } else if (interactive_state instanceof SelectMenuState) {
      this.select_button.setSelected(true);
    }
  }
}
