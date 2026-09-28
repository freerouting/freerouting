package app.freerouting.gui.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;

/** Describes the toolbar of the board frame, when it is in the inspected item state. */
public class BoardToolbarInspectedItem extends JToolBar {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardToolbarInspectedItem. */
  public BoardToolbarInspectedItem(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;

    this.tm = new TextManager(this.getClass(), boardFrame.getLocale());

    JButton toolbarCancelButton = new JButton();
    toolbarCancelButton.setText(tm.getText("cancel"));
    toolbarCancelButton.setToolTipText(tm.getText("cancel_tooltip"));
    toolbarCancelButton.addActionListener(_ -> boardFrame.boardPanel.boardHandling.cancelState());
    toolbarCancelButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarCancelButton", toolbarCancelButton.getText()));

    this.add(toolbarCancelButton);

    JButton toolbarInfoButton = new JButton();
    toolbarInfoButton.setText(tm.getText("info"));
    toolbarInfoButton.setToolTipText(tm.getText("info_tooltip"));
    toolbarInfoButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.displaySelectedItemInfo());
    toolbarInfoButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarInfoButton", toolbarInfoButton.getText()));

    this.add(toolbarInfoButton);

    JLabel separatorAfterInfo = new JLabel();
    separatorAfterInfo.setMaximumSize(new Dimension(10, 10));
    separatorAfterInfo.setPreferredSize(new Dimension(10, 10));
    this.add(separatorAfterInfo);

    JButton toolbarWholeNetsButton = new JButton();
    toolbarWholeNetsButton.setText(tm.getText("nets"));
    toolbarWholeNetsButton.setToolTipText(tm.getText("nets_tooltip"));
    toolbarWholeNetsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extendSelectionToWholeNets());
    toolbarWholeNetsButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarWholeNetsButton", toolbarWholeNetsButton.getText()));

    this.add(toolbarWholeNetsButton);

    JButton toolbarWholeConnectedSetsButton = new JButton();
    toolbarWholeConnectedSetsButton.setText(tm.getText("conn_sets"));
    toolbarWholeConnectedSetsButton.setToolTipText(tm.getText("conn_sets_tooltip"));
    toolbarWholeConnectedSetsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extendSelectionToWholeConnectedSets());
    toolbarWholeConnectedSetsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarWholeConnectedSetsButton", toolbarWholeConnectedSetsButton.getText()));

    this.add(toolbarWholeConnectedSetsButton);

    JButton toolbarWholeConnectionsButton = new JButton();
    toolbarWholeConnectionsButton.setText(tm.getText("connections"));
    toolbarWholeConnectionsButton.setToolTipText(tm.getText("connections_tooltip"));
    toolbarWholeConnectionsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extendSelectionToWholeConnections());
    toolbarWholeConnectionsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarWholeConnectionsButton", toolbarWholeConnectionsButton.getText()));

    this.add(toolbarWholeConnectionsButton);

    JButton toolbarWholeGroupsButton = new JButton();
    toolbarWholeGroupsButton.setText(tm.getText("components"));
    toolbarWholeGroupsButton.setToolTipText(tm.getText("components_tooltip"));
    toolbarWholeGroupsButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.extendSelectionToWholeComponents());
    toolbarWholeGroupsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarWholeGroupsButton", toolbarWholeGroupsButton.getText()));

    this.add(toolbarWholeGroupsButton);

    JLabel separatorAfterGroups = new JLabel();
    separatorAfterGroups.setMaximumSize(new Dimension(10, 10));
    separatorAfterGroups.setPreferredSize(new Dimension(10, 10));
    this.add(separatorAfterGroups);

    JButton toolbarViolationButton = new JButton();
    toolbarViolationButton.setText(tm.getText("violations"));
    toolbarViolationButton.setToolTipText(tm.getText("violations_tooltip"));
    toolbarViolationButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.toggleSelectedItemViolations());
    toolbarViolationButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("toolbarViolationButton", toolbarViolationButton.getText()));

    this.add(toolbarViolationButton);

    JLabel separatorAfterViolations = new JLabel();
    separatorAfterViolations.setMaximumSize(new Dimension(10, 10));
    separatorAfterViolations.setPreferredSize(new Dimension(10, 10));
    this.add(separatorAfterViolations);

    JButton toolbarDisplaySelectionButton = new JButton();
    toolbarDisplaySelectionButton.setText(tm.getText("zoom_selection"));
    toolbarDisplaySelectionButton.setToolTipText(tm.getText("zoom_selection_tooltip"));
    toolbarDisplaySelectionButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.zoomSelection());
    toolbarDisplaySelectionButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplaySelectionButton", toolbarDisplaySelectionButton.getText()));
    this.add(toolbarDisplaySelectionButton);

    JButton toolbarDisplayAllButton = new JButton();
    toolbarDisplayAllButton.setText(tm.getText("zoom_all"));
    toolbarDisplayAllButton.setToolTipText(tm.getText("zoom_all_tooltip"));
    toolbarDisplayAllButton.addActionListener(_ -> boardFrame.zoomAll());
    toolbarDisplayAllButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayAllButton", toolbarDisplayAllButton.getText()));
    this.add(toolbarDisplayAllButton);

    JButton toolbarDisplayRegionButton = new JButton();
    toolbarDisplayRegionButton.setText(tm.getText("zoom_region"));
    toolbarDisplayRegionButton.setToolTipText(tm.getText("zoom_region_tooltip"));
    toolbarDisplayRegionButton.addActionListener(
        _ -> boardFrame.boardPanel.boardHandling.zoomRegion());
    toolbarDisplayRegionButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "toolbarDisplayRegionButton", toolbarDisplayRegionButton.getText()));

    this.add(toolbarDisplayRegionButton);
  }
}
