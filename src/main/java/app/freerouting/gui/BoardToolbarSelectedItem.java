package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.ClearanceMatrix;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import java.awt.Dimension;
import java.util.ResourceBundle;

/** Describes the toolbar of the board frame, when it is in the selected item state. */
class BoardToolbarSelectedItem extends JToolBar {

  private final BoardFrame board_frame;
  private final ResourceBundle resources;

  /**
   * Creates a new instance of BoardSelectedItemToolbar. If p_extended, some additional buttons are
   * generated.
   */
  BoardToolbarSelectedItem(BoardFrame p_board_frame, boolean p_extended) {
    this.board_frame = p_board_frame;

    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardToolbarSelectedItem", p_board_frame.get_locale());

    JButton toolbar_cancel_button = new JButton();
    toolbar_cancel_button.setText(resources.getString("cancel"));
    toolbar_cancel_button.setToolTipText(resources.getString("cancel_tooltip"));
    toolbar_cancel_button.addActionListener(evt -> board_frame.board_panel.board_handling.cancel_state());
    toolbar_cancel_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_cancel_button", toolbar_cancel_button.getText()));

    this.add(toolbar_cancel_button);

    JButton toolbar_info_button = new JButton();
    toolbar_info_button.setText(resources.getString("info"));
    toolbar_info_button.setToolTipText(resources.getString("info_tooltip"));
    toolbar_info_button.addActionListener(evt -> board_frame.board_panel.board_handling.display_selected_item_info());
    toolbar_info_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_info_button", toolbar_info_button.getText()));

    this.add(toolbar_info_button);

    JButton toolbar_delete_button = new JButton();
    toolbar_delete_button.setText(resources.getString("delete"));
    toolbar_delete_button.setToolTipText(resources.getString("delete_tooltip"));
    toolbar_delete_button.addActionListener(evt -> board_frame.board_panel.board_handling.delete_selected_items());
    toolbar_delete_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_delete_button", toolbar_delete_button.getText()));

    this.add(toolbar_delete_button);

    JButton toolbar_cutout_button = new JButton();
    toolbar_cutout_button.setText(resources.getString("cutout"));
    toolbar_cutout_button.setToolTipText(resources.getString("cutout_tooltip"));
    toolbar_cutout_button.addActionListener(evt -> board_frame.board_panel.board_handling.cutout_selected_items());
    toolbar_cutout_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_cutout_button", toolbar_cutout_button.getText()));

    this.add(toolbar_cutout_button);

    JButton toolbar_fix_button = new JButton();
    toolbar_fix_button.setText(resources.getString("fix"));
    toolbar_fix_button.setToolTipText(resources.getString("fix_tooltip"));
    toolbar_fix_button.addActionListener(evt -> board_frame.board_panel.board_handling.fix_selected_items());
    toolbar_fix_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_fix_button", toolbar_fix_button.getText()));

    this.add(toolbar_fix_button);

    JButton toolbar_unfix_button = new JButton();
    toolbar_unfix_button.setText(resources.getString("unfix"));
    toolbar_unfix_button.setToolTipText(resources.getString("unfix_tooltip"));
    toolbar_unfix_button.addActionListener(evt -> board_frame.board_panel.board_handling.unfix_selected_items());
    toolbar_unfix_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_unfix_button", toolbar_unfix_button.getText()));

    this.add(toolbar_unfix_button);

    JButton toolbar_autoroute_button = new JButton();
    toolbar_autoroute_button.setText(resources.getString("autoroute"));
    toolbar_autoroute_button.setToolTipText(resources.getString("autoroute_tooltip"));
    toolbar_autoroute_button.addActionListener(evt -> board_frame.board_panel.board_handling.autoroute_selected_items());
    toolbar_autoroute_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_autoroute_button", toolbar_autoroute_button.getText()));
    this.add(toolbar_autoroute_button);

    JButton toolbar_tidy_button = new JButton();
    toolbar_tidy_button.setText(resources.getString("pull_tight"));
    toolbar_tidy_button.setToolTipText(resources.getString("pull_tight_tooltip"));
    toolbar_tidy_button.addActionListener(evt -> board_frame.board_panel.board_handling.optimize_selected_items());
    toolbar_tidy_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_tidy_button", toolbar_tidy_button.getText()));

    this.add(toolbar_tidy_button);

    JButton toolbar_clearance_class_button = new JButton();
    toolbar_clearance_class_button.setText(resources.getString("spacing"));
    toolbar_clearance_class_button.setToolTipText(resources.getString("spacing_tooltip"));
    toolbar_clearance_class_button.addActionListener(evt -> assign_clearance_class());
    toolbar_clearance_class_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_clearance_class_button", toolbar_clearance_class_button.getText()));

    JButton toolbar_fanout_button = new JButton();
    toolbar_fanout_button.setText(resources.getString("fanout"));
    toolbar_fanout_button.setToolTipText(resources.getString("fanout_tooltip"));
    toolbar_fanout_button.addActionListener(evt -> board_frame.board_panel.board_handling.fanout_selected_items());
    toolbar_fanout_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_fanout_button", toolbar_fanout_button.getText()));
    this.add(toolbar_fanout_button);

    this.add(toolbar_clearance_class_button);

    JLabel jLabel5 = new JLabel();
    jLabel5.setMaximumSize(new Dimension(10, 10));
    jLabel5.setPreferredSize(new Dimension(10, 10));
    this.add(jLabel5);

    JButton toolbar_whole_nets_button = new JButton();
    toolbar_whole_nets_button.setText(resources.getString("nets"));
    toolbar_whole_nets_button.setToolTipText(resources.getString("nets_tooltip"));
    toolbar_whole_nets_button.addActionListener(evt -> board_frame.board_panel.board_handling.extend_selection_to_whole_nets());
    toolbar_whole_nets_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_whole_nets_button", toolbar_whole_nets_button.getText()));

    this.add(toolbar_whole_nets_button);

    JButton toolbar_whole_connected_sets_button = new JButton();
    toolbar_whole_connected_sets_button.setText(resources.getString("conn_sets"));
    toolbar_whole_connected_sets_button.setToolTipText(resources.getString("conn_sets_tooltip"));
    toolbar_whole_connected_sets_button.addActionListener(evt -> board_frame.board_panel.board_handling.extend_selection_to_whole_connected_sets());
    toolbar_whole_connected_sets_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_whole_connected_sets_button", toolbar_whole_connected_sets_button.getText()));

    this.add(toolbar_whole_connected_sets_button);

    JButton toolbar_whole_connections_button = new JButton();
    toolbar_whole_connections_button.setText(resources.getString("connections"));
    toolbar_whole_connections_button.setToolTipText(resources.getString("connections_tooltip"));
    toolbar_whole_connections_button.addActionListener(evt -> board_frame.board_panel.board_handling.extend_selection_to_whole_connections());
    toolbar_whole_connections_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_whole_connections_button", toolbar_whole_connections_button.getText()));

    this.add(toolbar_whole_connections_button);

    JButton toolbar_whole_groups_button = new JButton();
    toolbar_whole_groups_button.setText(resources.getString("components"));
    toolbar_whole_groups_button.setToolTipText(resources.getString("components_tooltip"));
    toolbar_whole_groups_button.addActionListener(evt -> board_frame.board_panel.board_handling.extend_selection_to_whole_components());
    toolbar_whole_groups_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_whole_groups_button", toolbar_whole_groups_button.getText()));

    this.add(toolbar_whole_groups_button);

    if (p_extended) {
      JButton toolbar_new_net_button = new JButton();
      toolbar_new_net_button.setText(resources.getString("new_net"));
      toolbar_new_net_button.setToolTipText(resources.getString("new_net_tooltip"));
      toolbar_new_net_button.addActionListener(evt -> board_frame.board_panel.board_handling.assign_selected_to_new_net());
      toolbar_new_net_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_new_net_button", toolbar_new_net_button.getText()));

      this.add(toolbar_new_net_button);

      JButton toolbar_new_group_button = new JButton();
      toolbar_new_group_button.setText(resources.getString("new_component"));
      toolbar_new_group_button.setToolTipText(resources.getString("new_component_tooltip"));
      toolbar_new_group_button.addActionListener(evt -> board_frame.board_panel.board_handling.assign_selected_to_new_group());
      toolbar_new_group_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_new_group_button", toolbar_new_group_button.getText()));

      this.add(toolbar_new_group_button);
    }

    JLabel jLabel6 = new JLabel();
    jLabel6.setMaximumSize(new Dimension(10, 10));
    jLabel6.setPreferredSize(new Dimension(10, 10));
    this.add(jLabel6);

    JButton toolbar_violation_button = new JButton();
    toolbar_violation_button.setText(resources.getString("violations"));
    toolbar_violation_button.setToolTipText(resources.getString("violations_tooltip"));
    toolbar_violation_button.addActionListener(evt -> board_frame.board_panel.board_handling.toggle_selected_item_violations());
    toolbar_violation_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_violation_button", toolbar_violation_button.getText()));

    this.add(toolbar_violation_button);

    JLabel jLabel7 = new JLabel();
    jLabel7.setMaximumSize(new Dimension(10, 10));
    jLabel7.setPreferredSize(new Dimension(10, 10));
    this.add(jLabel7);

    JButton toolbar_display_selection_button = new JButton();
    toolbar_display_selection_button.setText(resources.getString("zoom_selection"));
    toolbar_display_selection_button.setToolTipText(resources.getString("zoom_selection_tooltip"));
    toolbar_display_selection_button.addActionListener(evt -> board_frame.board_panel.board_handling.zoom_selection());
    toolbar_display_selection_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_selection_button", toolbar_display_selection_button.getText()));
    this.add(toolbar_display_selection_button);

    JButton toolbar_display_all_button = new JButton();
    toolbar_display_all_button.setText(resources.getString("zoom_all"));
    toolbar_display_all_button.setToolTipText(resources.getString("zoom_all_tooltip"));
    toolbar_display_all_button.addActionListener(evt -> board_frame.zoom_all());
    toolbar_display_all_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_all_button", toolbar_display_all_button.getText()));
    this.add(toolbar_display_all_button);

    JButton toolbar_display_region_button = new JButton();
    toolbar_display_region_button.setText(resources.getString("zoom_region"));
    toolbar_display_region_button.setToolTipText(resources.getString("zoom_region_tooltip"));
    toolbar_display_region_button.addActionListener(evt -> board_frame.board_panel.board_handling.zoom_region());
    toolbar_display_region_button.addActionListener(evt -> FRAnalytics.buttonClicked("toolbar_display_region_button", toolbar_display_region_button.getText()));

    this.add(toolbar_display_region_button);
  }

  private void assign_clearance_class() {
    if (board_frame.board_panel.board_handling.is_board_read_only()) {
      return;
    }
    ClearanceMatrix clearance_matrix =
        board_frame.board_panel.board_handling.get_routing_board().rules.clearance_matrix;
    Object[] class_name_arr = new Object[clearance_matrix.get_class_count()];
    for (int i = 0; i < class_name_arr.length; ++i) {
      class_name_arr[i] = clearance_matrix.get_name(i);
    }
    Object assign_clearance_class_dialog = JOptionPane.showInputDialog(
            null,
            resources.getString("select_clearance_class"),
            resources.getString("assign_clearance_class"),
            JOptionPane.INFORMATION_MESSAGE,
            null,
            class_name_arr,
            class_name_arr[0]);
    if (!(assign_clearance_class_dialog instanceof String)) {
      return;
    }
    int class_index = clearance_matrix.get_no((String) assign_clearance_class_dialog);
    if (class_index < 0) {
      return;
    }
    board_frame.board_panel.board_handling.assign_clearance_classs_to_selected_items(class_index);
  }
}
