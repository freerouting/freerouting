package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaInfos;
import app.freerouting.rules.ViaRule;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Window for editing a single via rule. */
public class WindowViaRule extends WindowBase {

  private final ViaRule via_rule;
  /** the list of possible vias in a rule */
  private final ViaInfos via_list;
  private final JPanel main_panel;
  private final JList<ViaInfo> rule_list;
  private final DefaultListModel<ViaInfo> rule_list_model;
  private final ResourceBundle resources;
  /** Creates a new instance of ViaRuleWindow */
  public WindowViaRule(ViaRule p_via_rule, ViaInfos p_via_list, BoardFrame p_board_frame) {
    super(300, 150);

    this.via_rule = p_via_rule;
    this.via_list = p_via_list;

    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowViaRule", p_board_frame.get_locale());
    this.setTitle(resources.getString("title") + " " + p_via_rule.name);

    this.main_panel = new JPanel();
    main_panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    main_panel.setLayout(new BorderLayout());

    this.rule_list_model = new DefaultListModel<>();
    this.rule_list = new JList<>(this.rule_list_model);

    this.rule_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.rule_list.setVisibleRowCount(10);
    JScrollPane list_scroll_pane = new JScrollPane(this.rule_list);
    list_scroll_pane.setPreferredSize(new Dimension(200, 100));
    this.main_panel.add(list_scroll_pane, BorderLayout.CENTER);

    // fill the list
    for (int i = 0; i < p_via_rule.via_count(); ++i) {
      this.rule_list_model.addElement(p_via_rule.get_via(i));
    }

    // Add a panel with buttons for editing the via list.

    JPanel button_panel = new JPanel();
    this.main_panel.add(button_panel, BorderLayout.SOUTH);
    GridBagLayout gridbag = new GridBagLayout();
    button_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();

    final JButton rules_vias_rules_edit_append_button = new JButton(resources.getString("append"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(rules_vias_rules_edit_append_button, gridbag_constraints);
    rules_vias_rules_edit_append_button.setToolTipText(resources.getString("append_tooltip"));
    rules_vias_rules_edit_append_button.addActionListener(new AppendListener());
    rules_vias_rules_edit_append_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_rules_edit_append_button", rules_vias_rules_edit_append_button.getText()));
    button_panel.add(rules_vias_rules_edit_append_button);

    final JButton rules_vias_rules_edit_remove_button =
        new JButton(resources.getString("remove"));
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(rules_vias_rules_edit_remove_button, gridbag_constraints);
    rules_vias_rules_edit_remove_button.setToolTipText(resources.getString("remove_tooltip"));
    rules_vias_rules_edit_remove_button.addActionListener(new DeleteListener());
    rules_vias_rules_edit_remove_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_rules_edit_remove_button", rules_vias_rules_edit_remove_button.getText()));
    button_panel.add(rules_vias_rules_edit_remove_button);

    final JButton rules_vias_rules_edit_move_up_button =
        new JButton(resources.getString("move_up"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(rules_vias_rules_edit_move_up_button, gridbag_constraints);
    rules_vias_rules_edit_move_up_button.setToolTipText(resources.getString("move_up_tooltip"));
    rules_vias_rules_edit_move_up_button.addActionListener(new MoveUpListener());
    rules_vias_rules_edit_move_up_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_rules_edit_move_up_button", rules_vias_rules_edit_move_up_button.getText()));
    button_panel.add(rules_vias_rules_edit_move_up_button);

    final JButton rules_vias_rules_edit_move_down_button =
        new JButton(resources.getString("move_down"));
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(rules_vias_rules_edit_move_down_button, gridbag_constraints);
    rules_vias_rules_edit_move_down_button.setToolTipText(resources.getString("move_down_tooltip"));
    rules_vias_rules_edit_move_down_button.addActionListener(new MoveDownListener());
    rules_vias_rules_edit_move_down_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_rules_edit_move_down_button", rules_vias_rules_edit_move_down_button.getText()));
    button_panel.add(rules_vias_rules_edit_move_down_button);

    p_board_frame.set_context_sensitive_help(this, "WindowVia_EditViaRule");

    this.add(main_panel);
    this.pack();
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    this.setVisible(true);
  }

  /** Swaps the position of the vias with index p_1 and p_2. */
  private void swap_position(int p_1, int p_2) {
    ViaInfo via_1 = this.rule_list_model.get(p_1);
    ViaInfo via_2 = this.rule_list_model.get(p_2);
    if (via_1 == null || via_2 == null) {
      return;
    }
    this.rule_list_model.set(p_1, via_2);
    this.rule_list_model.set(p_2, via_1);
    this.via_rule.swap(via_1, via_2);
  }

  private class AppendListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      Object[] possible_values = new Object[via_list.count() - via_rule.via_count()];
      if (possible_values.length == 0) {
        return;
      }
      int curr_index = 0;
      for (int i = 0; i < via_list.count(); ++i) {
        ViaInfo curr_via = via_list.get(i);
        if (!via_rule.contains(curr_via)) {
          if (curr_index >= possible_values.length) {
            FRLogger.warn("ViaRuleWindow.AppendListener.actionPerformed: index inconsistent");
            break;
          }
          possible_values[curr_index] = curr_via;
          ++curr_index;
        }
      }
      assert (curr_index == possible_values.length);
      Object selected_value =
          JOptionPane.showInputDialog(
              null,
              resources.getString("choose_via_to_append"),
              resources.getString("append_via_to_rule"),
              JOptionPane.INFORMATION_MESSAGE,
              null,
              possible_values,
              possible_values[0]);
      if (selected_value != null) {
        ViaInfo selected_via = (ViaInfo) selected_value;
        via_rule.append_via(selected_via);
        rule_list_model.addElement(selected_via);
      }
    }
  }

  private class DeleteListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      ViaInfo selected_via = rule_list.getSelectedValue();
      if (selected_via != null) {
        String message =
            resources.getString("remove_2")
                + " "
                + selected_via.get_name()
                + " "
                + resources.getString("from_the_rule")
                + " "
                + via_rule.name
                + "?";
        if (WindowMessage.confirm(message)) {
          rule_list_model.removeElement(selected_via);
          via_rule.remove_via(selected_via);
        }
      }
    }
  }

  private class MoveUpListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int selected_index = rule_list.getSelectedIndex();
      if (selected_index <= 0) {
        return;
      }
      swap_position(selected_index - 1, selected_index);
      rule_list.setSelectedIndex(selected_index - 1);
    }
  }

  private class MoveDownListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int selected_index = rule_list.getSelectedIndex();
      if (selected_index < 0 || selected_index >= rule_list_model.getSize() - 1) {
        return;
      }
      swap_position(selected_index, selected_index + 1);
      rule_list.setSelectedIndex(selected_index + 1);
    }
  }
}
