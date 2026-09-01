package app.freerouting.gui.windows.routing;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.windows.board.WindowBase;
import app.freerouting.gui.windows.board.WindowMessage;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaInfos;
import app.freerouting.rules.ViaRule;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Window for editing a single via rule. */
public class WindowViaRule extends WindowBase {

  private final ViaRule viaRule;

  /** The list of possible vias in a rule. */
  private final ViaInfos viaList;

  private final JPanel mainPanel;
  private final JList<ViaInfo> ruleList;
  private final DefaultListModel<ViaInfo> ruleListModel;

  /** Creates a new instance of ViaRuleWindow. */
  public WindowViaRule(ViaRule viaRule, ViaInfos viaList, BoardFrame boardFrame) {
    super(300, 150);

    this.viaRule = viaRule;
    this.viaList = viaList;

    setLanguage(boardFrame.getLocale());

    this.setTitle(tm.getText("title") + " " + viaRule.name);

    this.mainPanel = new JPanel();
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    mainPanel.setLayout(new BorderLayout());

    this.ruleListModel = new DefaultListModel<>();
    this.ruleList = new JList<>(this.ruleListModel);

    this.ruleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.ruleList.setVisibleRowCount(10);
    JScrollPane listScrollPane = new JScrollPane(this.ruleList);
    listScrollPane.setPreferredSize(new Dimension(200, 100));
    this.mainPanel.add(listScrollPane, BorderLayout.CENTER);

    // fill the list
    for (int i = 0; i < viaRule.viaCount(); i++) {
      this.ruleListModel.addElement(viaRule.getVia(i));
    }

    // Add a panel with buttons for editing the via list.

    JPanel buttonPanel = new JPanel();
    this.mainPanel.add(buttonPanel, BorderLayout.SOUTH);
    GridBagLayout gridbag = new GridBagLayout();
    buttonPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();

    final JButton rulesViasRulesEditAppendButton = new JButton(tm.getText("append"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(rulesViasRulesEditAppendButton, gridbagConstraints);
    rulesViasRulesEditAppendButton.setToolTipText(tm.getText("append_tooltip"));
    rulesViasRulesEditAppendButton.addActionListener(new AppendListener());
    rulesViasRulesEditAppendButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesEditAppendButton", rulesViasRulesEditAppendButton.getText()));
    buttonPanel.add(rulesViasRulesEditAppendButton);

    final JButton rulesViasRulesEditRemoveButton = new JButton(tm.getText("remove"));
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(rulesViasRulesEditRemoveButton, gridbagConstraints);
    rulesViasRulesEditRemoveButton.setToolTipText(tm.getText("remove_tooltip"));
    rulesViasRulesEditRemoveButton.addActionListener(new DeleteListener());
    rulesViasRulesEditRemoveButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesEditRemoveButton", rulesViasRulesEditRemoveButton.getText()));
    buttonPanel.add(rulesViasRulesEditRemoveButton);

    final JButton rulesViasRulesEditMoveUpButton = new JButton(tm.getText("move_up"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(rulesViasRulesEditMoveUpButton, gridbagConstraints);
    rulesViasRulesEditMoveUpButton.setToolTipText(tm.getText("move_up_tooltip"));
    rulesViasRulesEditMoveUpButton.addActionListener(new MoveUpListener());
    rulesViasRulesEditMoveUpButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesEditMoveUpButton", rulesViasRulesEditMoveUpButton.getText()));
    buttonPanel.add(rulesViasRulesEditMoveUpButton);

    final JButton rulesViasRulesEditMoveDownButton = new JButton(tm.getText("move_down"));
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(rulesViasRulesEditMoveDownButton, gridbagConstraints);
    rulesViasRulesEditMoveDownButton.setToolTipText(tm.getText("move_down_tooltip"));
    rulesViasRulesEditMoveDownButton.addActionListener(new MoveDownListener());
    rulesViasRulesEditMoveDownButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesEditMoveDownButton", rulesViasRulesEditMoveDownButton.getText()));
    buttonPanel.add(rulesViasRulesEditMoveDownButton);

    this.add(mainPanel);
    this.pack();
    clampWindowHeight(this, boardFrame);
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    this.setVisible(true);
  }

  /** Swaps the position of the vias with index 1 and 2. */
  private void swapPosition(int p1, int p2) {
    ViaInfo via1 = this.ruleListModel.get(p1);
    ViaInfo via2 = this.ruleListModel.get(p2);
    if (via1 == null || via2 == null) {
      return;
    }
    this.ruleListModel.set(p1, via2);
    this.ruleListModel.set(p2, via1);
    this.viaRule.swap(via1, via2);
  }

  private class AppendListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      Object[] possibleValues = new Object[viaList.count() - viaRule.viaCount()];
      if (possibleValues.length == 0) {
        return;
      }
      int currentIndex = 0;
      for (int i = 0; i < viaList.count(); i++) {
        ViaInfo currentVia = viaList.get(i);
        if (!viaRule.contains(currentVia)) {
          if (currentIndex >= possibleValues.length) {
            FRLogger.warn("ViaRuleWindow.AppendListener.actionPerformed: index inconsistent");
            break;
          }
          possibleValues[currentIndex] = currentVia;
          ++currentIndex;
        }
      }
      assert (currentIndex == possibleValues.length);
      Object selectedValue =
          JOptionPane.showInputDialog(
              null,
              tm.getText("choose_via_to_append"),
              tm.getText("append_via_to_rule"),
              JOptionPane.INFORMATION_MESSAGE,
              null,
              possibleValues,
              possibleValues[0]);
      if (selectedValue != null) {
        ViaInfo selectedVia = (ViaInfo) selectedValue;
        viaRule.appendVia(selectedVia);
        ruleListModel.addElement(selectedVia);
      }
    }
  }

  private class DeleteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ViaInfo selectedVia = ruleList.getSelectedValue();
      if (selectedVia != null) {
        if (WindowMessage.confirm(
            tm.getText("remove_via_from_rule_confirm", selectedVia.getName(), viaRule.name))) {
          ruleListModel.removeElement(selectedVia);
          viaRule.removeVia(selectedVia);
        }
      }
    }
  }

  private class MoveUpListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int selectedIndex = ruleList.getSelectedIndex();
      if (selectedIndex <= 0) {
        return;
      }
      swapPosition(selectedIndex - 1, selectedIndex);
      ruleList.setSelectedIndex(selectedIndex - 1);
    }
  }

  private class MoveDownListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int selectedIndex = ruleList.getSelectedIndex();
      if (selectedIndex < 0 || selectedIndex >= ruleListModel.getSize() - 1) {
        return;
      }
      swapPosition(selectedIndex, selectedIndex + 1);
      ruleList.setSelectedIndex(selectedIndex + 1);
    }
  }
}
