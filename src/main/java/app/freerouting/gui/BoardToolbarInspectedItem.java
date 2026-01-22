package app.freerouting.gui;

import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;

/**
 * Describes the toolbar of the board frame, when it is in the inspected item
 * state.
 */
class BoardToolbarInspectedItem extends JToolBar {

    private final BoardFrame board_frame;
    private final TextManager tm;

    /**
     * Creates a new instance of BoardToolbarInspectedItem.
     */
    BoardToolbarInspectedItem(BoardFrame p_board_frame) {
        this.board_frame = p_board_frame;

        this.tm = new TextManager(this.getClass(), p_board_frame.get_locale());

        JButton toolbar_cancel_button = new JButton();
        toolbar_cancel_button.setText(tm.getText("cancel"));
        toolbar_cancel_button.setToolTipText(tm.getText("cancel_tooltip"));
        toolbar_cancel_button.addActionListener(_ -> board_frame.board_panel.board_handling.cancel_state());
        toolbar_cancel_button
                .addActionListener(
                        _ -> FRAnalytics.buttonClicked("toolbar_cancel_button", toolbar_cancel_button.getText()));

        this.add(toolbar_cancel_button);

        JButton toolbar_info_button = new JButton();
        toolbar_info_button.setText(tm.getText("info"));
        toolbar_info_button.setToolTipText(tm.getText("info_tooltip"));
        toolbar_info_button.addActionListener(_ -> board_frame.board_panel.board_handling.display_selected_item_info());
        toolbar_info_button
                .addActionListener(
                        _ -> FRAnalytics.buttonClicked("toolbar_info_button", toolbar_info_button.getText()));

        this.add(toolbar_info_button);

        JLabel jLabel5 = new JLabel();
        jLabel5.setMaximumSize(new Dimension(10, 10));
        jLabel5.setPreferredSize(new Dimension(10, 10));
        this.add(jLabel5);

        JButton toolbar_whole_nets_button = new JButton();
        toolbar_whole_nets_button.setText(tm.getText("nets"));
        toolbar_whole_nets_button.setToolTipText(tm.getText("nets_tooltip"));
        toolbar_whole_nets_button
                .addActionListener(_ -> board_frame.board_panel.board_handling.extend_selection_to_whole_nets());
        toolbar_whole_nets_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_whole_nets_button", toolbar_whole_nets_button.getText()));

        this.add(toolbar_whole_nets_button);

        JButton toolbar_whole_connected_sets_button = new JButton();
        toolbar_whole_connected_sets_button.setText(tm.getText("conn_sets"));
        toolbar_whole_connected_sets_button.setToolTipText(tm.getText("conn_sets_tooltip"));
        toolbar_whole_connected_sets_button
                .addActionListener(
                        _ -> board_frame.board_panel.board_handling.extend_selection_to_whole_connected_sets());
        toolbar_whole_connected_sets_button.addActionListener(_ -> FRAnalytics
                .buttonClicked("toolbar_whole_connected_sets_button", toolbar_whole_connected_sets_button.getText()));

        this.add(toolbar_whole_connected_sets_button);

        JButton toolbar_whole_connections_button = new JButton();
        toolbar_whole_connections_button.setText(tm.getText("connections"));
        toolbar_whole_connections_button.setToolTipText(tm.getText("connections_tooltip"));
        toolbar_whole_connections_button
                .addActionListener(_ -> board_frame.board_panel.board_handling.extend_selection_to_whole_connections());
        toolbar_whole_connections_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_whole_connections_button",
                        toolbar_whole_connections_button.getText()));

        this.add(toolbar_whole_connections_button);

        JButton toolbar_whole_groups_button = new JButton();
        toolbar_whole_groups_button.setText(tm.getText("components"));
        toolbar_whole_groups_button.setToolTipText(tm.getText("components_tooltip"));
        toolbar_whole_groups_button
                .addActionListener(_ -> board_frame.board_panel.board_handling.extend_selection_to_whole_components());
        toolbar_whole_groups_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_whole_groups_button", toolbar_whole_groups_button.getText()));

        this.add(toolbar_whole_groups_button);

        JLabel jLabel6 = new JLabel();
        jLabel6.setMaximumSize(new Dimension(10, 10));
        jLabel6.setPreferredSize(new Dimension(10, 10));
        this.add(jLabel6);

        JButton toolbar_violation_button = new JButton();
        toolbar_violation_button.setText(tm.getText("violations"));
        toolbar_violation_button.setToolTipText(tm.getText("violations_tooltip"));
        toolbar_violation_button
                .addActionListener(_ -> board_frame.board_panel.board_handling.toggle_selected_item_violations());
        toolbar_violation_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_violation_button", toolbar_violation_button.getText()));

        this.add(toolbar_violation_button);

        JLabel jLabel7 = new JLabel();
        jLabel7.setMaximumSize(new Dimension(10, 10));
        jLabel7.setPreferredSize(new Dimension(10, 10));
        this.add(jLabel7);

        JButton toolbar_display_selection_button = new JButton();
        toolbar_display_selection_button.setText(tm.getText("zoom_selection"));
        toolbar_display_selection_button.setToolTipText(tm.getText("zoom_selection_tooltip"));
        toolbar_display_selection_button
                .addActionListener(_ -> board_frame.board_panel.board_handling.zoom_selection());
        toolbar_display_selection_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_display_selection_button",
                        toolbar_display_selection_button.getText()));
        this.add(toolbar_display_selection_button);

        JButton toolbar_display_all_button = new JButton();
        toolbar_display_all_button.setText(tm.getText("zoom_all"));
        toolbar_display_all_button.setToolTipText(tm.getText("zoom_all_tooltip"));
        toolbar_display_all_button.addActionListener(_ -> board_frame.zoom_all());
        toolbar_display_all_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_display_all_button", toolbar_display_all_button.getText()));
        this.add(toolbar_display_all_button);

        JButton toolbar_display_region_button = new JButton();
        toolbar_display_region_button.setText(tm.getText("zoom_region"));
        toolbar_display_region_button.setToolTipText(tm.getText("zoom_region_tooltip"));
        toolbar_display_region_button.addActionListener(_ -> board_frame.board_panel.board_handling.zoom_region());
        toolbar_display_region_button.addActionListener(
                _ -> FRAnalytics.buttonClicked("toolbar_display_region_button",
                        toolbar_display_region_button.getText()));

        this.add(toolbar_display_region_button);
    }

}