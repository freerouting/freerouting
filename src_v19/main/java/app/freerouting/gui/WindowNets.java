package app.freerouting.gui;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.NetClasses;
import app.freerouting.rules.Nets;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

public class WindowNets extends WindowObjectListWithFilter {

  private final ResourceBundle resources;

  /** Creates a new instance of NetsWindow */
  public WindowNets(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowNets", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    JPanel curr_button_panel = new JPanel();
    this.south_panel.add(curr_button_panel, BorderLayout.NORTH);

    final JButton rules_nets_assign_class_button = new JButton(resources.getString("assign_class"));
    curr_button_panel.add(rules_nets_assign_class_button);
    rules_nets_assign_class_button.setToolTipText(resources.getString("assign_class_tooltip"));
    rules_nets_assign_class_button.addActionListener(new AssignClassListener());
    rules_nets_assign_class_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_nets_assign_class_button", rules_nets_assign_class_button.getText()));

    final JButton rules_nets_filter_incompletes_button = new JButton(resources.getString("filter_incompletes"));
    curr_button_panel.add(rules_nets_filter_incompletes_button);
    rules_nets_filter_incompletes_button.setToolTipText(resources.getString("filter_incompletes_tooltip"));
    rules_nets_filter_incompletes_button.addActionListener(new FilterIncompletesListener());
    rules_nets_filter_incompletes_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_nets_filter_incompletes_button", rules_nets_filter_incompletes_button.getText()));

    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_Nets");
  }

  /** Fills the list with the nets in the net list. */
  @Override
  protected void fill_list() {
    Nets nets = this.board_frame.board_panel.board_handling.get_routing_board().rules.nets;
    Net[] sorted_arr = new Net[nets.max_net_no()];
    for (int i = 0; i < sorted_arr.length; ++i) {
      sorted_arr[i] = nets.get(i + 1);
    }
    Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i) {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(sorted_arr.length, DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selected_nets = list.getSelectedValuesList();
    if (selected_nets.isEmpty()) {
      return;
    }
    int[] selected_net_numbers = new int[selected_nets.size()];
    for (int i = 0; i < selected_nets.size(); ++i) {
      selected_net_numbers[i] = ((Net) selected_nets.get(i)).net_number;
    }
    RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    Set<Item> selected_items =
        new TreeSet<>();
    Collection<Item> board_items = routing_board.get_items();
    for (Item curr_item : board_items) {
      boolean item_matches = false;
      for (int curr_net_no : selected_net_numbers) {
        if (curr_item.contains_net(curr_net_no)) {
          item_matches = true;
          break;
        }
      }
      if (item_matches) {
        selected_items.add(curr_item);
      }
    }
    board_frame.board_panel.board_handling.select_items(selected_items);
    board_frame.board_panel.board_handling.zoom_selection();
  }

  private class AssignClassListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      List<Object> selected_nets = list.getSelectedValuesList();
      if (selected_nets.isEmpty()) {
        return;
      }
      NetClasses net_classes =
          board_frame.board_panel.board_handling.get_routing_board().rules.net_classes;
      NetClass[] class_arr =
          new NetClass[net_classes.count()];
      for (int i = 0; i < class_arr.length; ++i) {
        class_arr[i] = net_classes.get(i);
      }
      Object selected_value = JOptionPane.showInputDialog(
              null,
              resources.getString("message_1"),
              resources.getString("message_2"),
              JOptionPane.INFORMATION_MESSAGE,
              null,
              class_arr,
              class_arr[0]);
      if (!(selected_value instanceof NetClass)) {
        return;
      }
      NetClass selected_class =
          (NetClass) selected_value;
      for (int i = 0; i < selected_nets.size(); ++i) {
        ((Net) selected_nets.get(i)).set_class(selected_class);
      }
      board_frame.refresh_windows();
    }
  }

  private class FilterIncompletesListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      List<Object> selected_nets = list.getSelectedValuesList();
      if (selected_nets.isEmpty()) {
        return;
      }
      BoardHandling board_handling =
          board_frame.board_panel.board_handling;
      int max_net_no = board_handling.get_routing_board().rules.nets.max_net_no();
      for (int i = 1; i <= max_net_no; ++i) {
        board_handling.set_incompletes_filter(i, true);
      }
      for (int i = 0; i < selected_nets.size(); ++i) {
        board_handling.set_incompletes_filter(((Net) selected_nets.get(i)).net_number, false);
      }
      board_frame.board_panel.repaint();
    }
  }
}
