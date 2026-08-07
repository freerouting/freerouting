package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;

/** Describes the toolbar of the board frame, when it is in the inspected item state. */
class BoardToolbarInspectedItem extends JToolBar {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardToolbarInspectedItem. */
  BoardToolbarInspectedItem(BoardFrame p_board_frame) {
    this.boardFrame = p_board_frame;

    this.tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JButton toolbarCancelButton = new JButton();
    toolbarCancelButton.setText(tm.getText("cancel"));
    toolbarCancelButton.setToolTipText(tm.getText("cancel_tooltip"));
    toolbarCancelButton.addActionListener(_ -> boardFrame.boardPanel.boardHandling.cancel_state());
    toolbarCancelButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarCancelButton", toolbarCancelButton.getText()));

    this.add(toolbarCancelButton);

    JButton toolbarInfoButton = new JButton();
    toolbarInfoButton.setText(tm.getText("info"));
    toolbarInfoButton.setToolTipText(tm.getText("info_tooltip"));
    toolbarInfoButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.display_selected_item_info());
    toolbarInfoButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarInfoButton", toolbarInfoButton.getText()));

    this.add(toolbarInfoButton);

    JLabel jLabel5 = new JLabel();
    jLabel5.setMaximumSize(new Dimension(10, 10));
    jLabel5.setPreferredSize(new Dimension(10, 10));
    this.add(jLabel5);

    JButton toolbarWholeNetsButton = new JButton();
    toolbarWholeNetsButton.setText(tm.getText("nets"));
    toolbarWholeNetsButton.setToolTipText(tm.getText("nets_tooltip"));
    toolbarWholeNetsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extend_selection_to_whole_nets());
    toolbarWholeNetsButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarWholeNetsButton", toolbarWholeNetsButton.getText()));

    this.add(toolbarWholeNetsButton);

    JButton toolbarWholeConnectedSetsButton = new JButton();
    toolbarWholeConnectedSetsButton.setText(tm.getText("conn_sets"));
    toolbarWholeConnectedSetsButton.setToolTipText(tm.getText("conn_sets_tooltip"));
    toolbarWholeConnectedSetsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extend_selection_to_whole_connected_sets());
    toolbarWholeConnectedSetsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarWholeConnectedSetsButton", toolbarWholeConnectedSetsButton.getText()));

    this.add(toolbarWholeConnectedSetsButton);

    JButton toolbarWholeConnectionsButton = new JButton();
    toolbarWholeConnectionsButton.setText(tm.getText("connections"));
    toolbarWholeConnectionsButton.setToolTipText(tm.getText("connections_tooltip"));
    toolbarWholeConnectionsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extend_selection_to_whole_connections());
    toolbarWholeConnectionsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarWholeConnectionsButton", toolbarWholeConnectionsButton.getText()));

    this.add(toolbarWholeConnectionsButton);

    JButton toolbarWholeGroupsButton = new JButton();
    toolbarWholeGroupsButton.setText(tm.getText("components"));
    toolbarWholeGroupsButton.setToolTipText(tm.getText("components_tooltip"));
    toolbarWholeGroupsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extend_selection_to_whole_components());
    toolbarWholeGroupsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarWholeGroupsButton", toolbarWholeGroupsButton.getText()));

    this.add(toolbarWholeGroupsButton);

    JLabel jLabel6 = new JLabel();
    jLabel6.setMaximumSize(new Dimension(10, 10));
    jLabel6.setPreferredSize(new Dimension(10, 10));
    this.add(jLabel6);

    JButton toolbarViolationButton = new JButton();
    toolbarViolationButton.setText(tm.getText("violations"));
    toolbarViolationButton.setToolTipText(tm.getText("violations_tooltip"));
    toolbarViolationButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.toggle_selected_item_violations());
    toolbarViolationButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarViolationButton", toolbarViolationButton.getText()));

    this.add(toolbarViolationButton);

    JLabel jLabel7 = new JLabel();
    jLabel7.setMaximumSize(new Dimension(10, 10));
    jLabel7.setPreferredSize(new Dimension(10, 10));
    this.add(jLabel7);

    JButton toolbarDisplaySelectionButton = new JButton();
    toolbarDisplaySelectionButton.setText(tm.getText("zoom_selection"));
    toolbarDisplaySelectionButton.setToolTipText(tm.getText("zoom_selection_tooltip"));
    toolbarDisplaySelectionButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.zoom_selection());
    toolbarDisplaySelectionButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplaySelectionButton", toolbarDisplaySelectionButton.getText()));
    this.add(toolbarDisplaySelectionButton);

    JButton toolbarDisplayAllButton = new JButton();
    toolbarDisplayAllButton.setText(tm.getText("zoom_all"));
    toolbarDisplayAllButton.setToolTipText(tm.getText("zoom_all_tooltip"));
    toolbarDisplayAllButton.addActionListener(_ -> boardFrame.zoom_all());
    toolbarDisplayAllButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayAllButton", toolbarDisplayAllButton.getText()));
    this.add(toolbarDisplayAllButton);

    JButton toolbarDisplayRegionButton = new JButton();
    toolbarDisplayRegionButton.setText(tm.getText("zoom_region"));
    toolbarDisplayRegionButton.setToolTipText(tm.getText("zoom_region_tooltip"));
    toolbarDisplayRegionButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.zoom_region());
    toolbarDisplayRegionButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayRegionButton", toolbarDisplayRegionButton.getText()));

    this.add(toolbarDisplayRegionButton);
  }
}
