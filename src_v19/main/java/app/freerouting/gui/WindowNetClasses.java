package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel.Printable;
import app.freerouting.board.RoutingBoard;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.Nets;
import app.freerouting.rules.ViaRule;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

/** Edit window for the table of net rules. */
public class WindowNetClasses extends BoardSavableSubWindow {

  private static final int TEXTFIELD_HEIGHT = 16;
  private static final int TEXTFIELD_WIDTH = 100;
  private static final int WINDOW_OFFSET = 30;
  private final BoardFrame board_frame;
  private final JPanel main_panel;
  private final ComboBoxLayer layer_combo_box;
  private final ResourceBundle resources;
  /** The subwindows created inside this window */
  private final Collection<JFrame> subwindows =
      new LinkedList<>();
  private JPanel center_panel;
  private NetClassTable table;
  private NetClassTableModel table_model;
  private final JComboBox<String> cl_class_combo_box;
  private final JComboBox<String> via_rule_combo_box;
  /** Creates a new instance of NetClassesWindow */
  public WindowNetClasses(BoardFrame p_board_frame) {
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowNetClasses", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    this.board_frame = p_board_frame;

    this.main_panel = new JPanel();
    this.main_panel.setLayout(new BorderLayout());

    BasicBoard routing_board =
        p_board_frame.board_panel.board_handling.get_routing_board();

    this.cl_class_combo_box = new JComboBox<>();
    this.via_rule_combo_box = new JComboBox<>();
    this.layer_combo_box =
        new ComboBoxLayer(routing_board.layer_structure, p_board_frame.get_locale());
    add_combobox_items();

    add_table();

    JPanel net_class_button_panel = new JPanel();
    net_class_button_panel.setLayout(new FlowLayout());
    this.main_panel.add(net_class_button_panel, BorderLayout.SOUTH);

    final JButton rules_netclasses_add_class_button =
        new JButton(resources.getString("add"));
    rules_netclasses_add_class_button.setToolTipText(resources.getString("add_tooltip"));
    rules_netclasses_add_class_button.addActionListener(new AddNetClassListener());
    rules_netclasses_add_class_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_netclasses_add_class_button", rules_netclasses_add_class_button.getText()));
    net_class_button_panel.add(rules_netclasses_add_class_button);

    final JButton rules_netclasses_remove_class_button = new JButton(resources.getString("remove"));
    rules_netclasses_remove_class_button.setToolTipText(resources.getString("remove_tooltip"));
    rules_netclasses_remove_class_button.addActionListener(new RemoveNetClassListener());
    rules_netclasses_remove_class_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_netclasses_remove_class_button", rules_netclasses_remove_class_button.getText()));
    net_class_button_panel.add(rules_netclasses_remove_class_button);

    final JButton rules_netclasses_assign_button = new JButton(resources.getString("assign"));
    rules_netclasses_assign_button.setToolTipText(resources.getString("assign_tooltip"));
    rules_netclasses_assign_button.addActionListener(new AssignClassesListener());
    rules_netclasses_assign_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_netclasses_assign_button", rules_netclasses_assign_button.getText()));
    net_class_button_panel.add(rules_netclasses_assign_button);

    final JButton rules_netclasses_select_button = new JButton(resources.getString("select"));
    rules_netclasses_select_button.setToolTipText(resources.getString("select_tooltip"));
    rules_netclasses_select_button.addActionListener(new SelectClassesListener());
    rules_netclasses_select_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_netclasses_select_button", rules_netclasses_select_button.getText()));
    net_class_button_panel.add(rules_netclasses_select_button);

    final JButton rules_netclasses_contained_nets_button = new JButton(resources.getString("show_nets"));
    net_class_button_panel.add(rules_netclasses_contained_nets_button);
    rules_netclasses_contained_nets_button.setToolTipText(resources.getString("show_nets_tooltip"));
    rules_netclasses_contained_nets_button.addActionListener(new ContainedNetsListener());
    rules_netclasses_contained_nets_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_netclasses_contained_nets_button", rules_netclasses_contained_nets_button.getText()));

    final JButton rules_netclasses_filter_incompletes_button = new JButton(resources.getString("filter_incompletes"));
    net_class_button_panel.add(rules_netclasses_filter_incompletes_button);
    rules_netclasses_filter_incompletes_button.setToolTipText(resources.getString("filter_incompletes_tooltip"));
    rules_netclasses_filter_incompletes_button.addActionListener(new FilterIncompletesListener());
    rules_netclasses_filter_incompletes_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_netclasses_filter_incompletes_button", rules_netclasses_filter_incompletes_button.getText()));

    p_board_frame.set_context_sensitive_help(this, "WindowNetClasses");

    this.add(main_panel);
    this.pack();
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  }

  @Override
  public void refresh() {
    this.cl_class_combo_box.removeAllItems();
    this.via_rule_combo_box.removeAllItems();
    add_combobox_items();
    this.table_model.set_values();
    int table_height = TEXTFIELD_HEIGHT * this.table_model.getRowCount();
    int table_width = TEXTFIELD_WIDTH * this.table_model.getColumnCount();
    this.table.setPreferredScrollableViewportSize(
        new Dimension(table_width, table_height));
    // reinsert the scroll to display the correct table size if the table size has changed.
    this.main_panel.remove(this.center_panel);
    this.main_panel.add(center_panel, BorderLayout.CENTER);
    this.pack();

    // Dispose all subwindows because they may be no longer up-to-date.
    Iterator<JFrame> it = this.subwindows.iterator();
    while (it.hasNext()) {
      JFrame curr_subwindow = it.next();
      if (curr_subwindow != null) {

        curr_subwindow.dispose();
      }
      it.remove();
    }
  }

  @Override
  public void dispose() {
    for (JFrame curr_subwindow : this.subwindows) {
      if (curr_subwindow != null) {
        curr_subwindow.dispose();
      }
    }
    super.dispose();
  }

  private void add_table() {
    this.table_model = new NetClassTableModel();
    this.table = new NetClassTable(this.table_model);
    JScrollPane scroll_pane = new JScrollPane(this.table);
    int table_height = TEXTFIELD_HEIGHT * this.table_model.getRowCount();
    int table_width = TEXTFIELD_WIDTH * this.table_model.getColumnCount();
    this.table.setPreferredScrollableViewportSize(
        new Dimension(table_width, table_height));
    this.center_panel = new JPanel();
    this.center_panel.setLayout(new BorderLayout());

    this.center_panel.add(scroll_pane, BorderLayout.CENTER);

    // add message for german localisation bug
    if (board_frame.get_locale().getLanguage().equalsIgnoreCase("de")) {
      JLabel bug_label =
          new JLabel(
              "Wegen eines Java-System-Bugs muss das Dezimalkomma in dieser Tabelle zur Zeit als Punkt eingegeben werden!");
      this.center_panel.add(bug_label, BorderLayout.SOUTH);
    }
    this.main_panel.add(center_panel, BorderLayout.CENTER);

    this.table
        .getColumnModel()
        .getColumn(ColumnName.CLEARANCE_CLASS.ordinal())
        .setCellEditor(new DefaultCellEditor(cl_class_combo_box));

    this.table
        .getColumnModel()
        .getColumn(ColumnName.VIA_RULE.ordinal())
        .setCellEditor(new DefaultCellEditor(via_rule_combo_box));
    this.table
        .getColumnModel()
        .getColumn(ColumnName.ON_LAYER.ordinal())
        .setCellEditor(new DefaultCellEditor(layer_combo_box));
  }

  private void add_combobox_items() {
    RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    for (int i = 0; i < routing_board.rules.clearance_matrix.get_class_count(); ++i) {
      cl_class_combo_box.addItem(routing_board.rules.clearance_matrix.get_name(i));
    }
    for (ViaRule curr_rule : routing_board.rules.via_rules) {
      via_rule_combo_box.addItem(curr_rule.name);
    }
  }

  /**
   * Adjusts the displayed window with the net class table after the size of the table has been
   * changed.
   */
  private void adjust_table() {
    this.table_model = new NetClassTableModel();
    this.table = new NetClassTable(this.table_model);
    this.main_panel.remove(this.center_panel);
    this.add_table();
    this.pack();
    this.board_frame.refresh_windows();
  }

  private enum ColumnName {
    NAME,
    VIA_RULE,
    CLEARANCE_CLASS,
    TRACE_WIDTH,
    ON_LAYER,
    SHOVE_FIXED,
    CYCLES_WITH_AREAS,
    MIN_TRACE_LENGTH,
    MAX_TRACE_LENGTH,
    IGNORED_BY_AUTOROUTER
  }

  private class AddNetClassListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_frame
          .board_panel
          .board_handling
          .get_routing_board()
          .rules
          .append_net_class(board_frame.get_locale());
      adjust_table();
    }
  }

  private class RemoveNetClassListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (table_model.getRowCount() <= 1) {
        board_frame.screen_messages.set_status_message(resources.getString("message_1"));
        return;
      }
      int selected_row = table.getSelectedRow();
      if (selected_row < 0) {
        return;
      }
      Object net_class_name = table_model.getValueAt(selected_row, ColumnName.NAME.ordinal());
      if (!(net_class_name instanceof String)) {
        return;
      }
      BoardRules board_rules = board_frame.board_panel.board_handling.get_routing_board().rules;
      NetClass net_rule = board_rules.net_classes.get((String) net_class_name);
      // Check, if net_rule is used in a net of the net list
      for (int i = 1; i < board_rules.nets.max_net_no(); ++i) {
        Net curr_net = board_rules.nets.get(i);
        if (curr_net.get_class() == net_rule) {
          String message = resources.getString("message_2") + " " + curr_net.name;
          board_frame.screen_messages.set_status_message(message);
          return;
        }
      }
      if (board_rules.net_classes.remove(net_rule)) {
        adjust_table();
        String message =
            resources.getString("net_class")
                + " "
                + net_rule.get_name()
                + " "
                + resources.getString("removed");
        board_frame.screen_messages.set_status_message(message);
      }
    }
  }

  private class AssignClassesListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_frame.assign_net_classes_window.setVisible(true);
    }
  }

  private class SelectClassesListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int[] selected_rows = table.getSelectedRows();
      if (selected_rows.length == 0) {
        return;
      }
      RoutingBoard routing_board =
          board_frame.board_panel.board_handling.get_routing_board();
      NetClass[] selected_class_arr = new NetClass[selected_rows.length];
      for (int i = 0; i < selected_class_arr.length; ++i) {
        selected_class_arr[i] =
            routing_board.rules.net_classes.get(
                (String) table.getValueAt(selected_rows[i], ColumnName.NAME.ordinal()));
      }
      Nets nets = routing_board.rules.nets;
      Set<Item> selected_items =
          new TreeSet<>();
      Collection<Item> board_items = routing_board.get_items();
      for (Item curr_item : board_items) {
        boolean item_matches = false;
        for (int i = 0; i < curr_item.net_count(); ++i) {
          NetClass curr_net_class =
              nets.get(curr_item.get_net_no(i)).get_class();
          if (curr_net_class == null) {
            continue;
          }
          for (int j = 0; j < selected_class_arr.length; ++j) {
            if (curr_net_class == selected_class_arr[i]) {
              item_matches = true;
              break;
            }
          }
          if (item_matches) {
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
  }

  private class FilterIncompletesListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int[] selected_rows = table.getSelectedRows();
      if (selected_rows.length == 0) {
        return;
      }
      BoardHandling board_handling =
          board_frame.board_panel.board_handling;
      BoardRules board_rules = board_handling.get_routing_board().rules;
      NetClass[] selected_class_arr = new NetClass[selected_rows.length];
      for (int i = 0; i < selected_class_arr.length; ++i) {
        selected_class_arr[i] =
            board_rules.net_classes.get(
                (String) table.getValueAt(selected_rows[i], ColumnName.NAME.ordinal()));
      }
      int max_net_no = board_rules.nets.max_net_no();
      for (int i = 1; i <= max_net_no; ++i) {
        board_handling.set_incompletes_filter(i, true);
        NetClass curr_net_class = board_rules.nets.get(i).get_class();
        for (int j = 0; j < selected_class_arr.length; ++j) {
          if (curr_net_class == selected_class_arr[j]) {
            board_handling.set_incompletes_filter(i, false);
            break;
          }
        }
      }
      board_frame.board_panel.repaint();
    }
  }

  private class ContainedNetsListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int[] selected_rows = table.getSelectedRows();
      if (selected_rows.length == 0) {
        return;
      }
      BoardHandling board_handling =
          board_frame.board_panel.board_handling;
      BoardRules board_rules = board_handling.get_routing_board().rules;
      NetClass[] selected_class_arr = new NetClass[selected_rows.length];
      for (int i = 0; i < selected_class_arr.length; ++i) {
        selected_class_arr[i] =
            board_rules.net_classes.get(
                (String) table.getValueAt(selected_rows[i], ColumnName.NAME.ordinal()));
      }
      Collection<Printable> contained_nets = new LinkedList<>();
      int max_net_no = board_rules.nets.max_net_no();
      for (int i = 1; i <= max_net_no; ++i) {
        Net curr_net = board_rules.nets.get(i);
        NetClass curr_net_class = curr_net.get_class();
        for (int j = 0; j < selected_class_arr.length; ++j) {
          if (curr_net_class == selected_class_arr[j]) {
            contained_nets.add(curr_net);
            break;
          }
        }
      }
      CoordinateTransform coordinate_transform =
          board_frame.board_panel.board_handling.coordinate_transform;
      WindowObjectInfo new_window =
          WindowObjectInfo.display(
              resources.getString("contained_nets"),
              contained_nets,
              board_frame,
              coordinate_transform);
      Point loc = getLocation();
      Point new_window_location =
          new Point(
              (int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      new_window.setLocation(new_window_location);
      subwindows.add(new_window);
    }
  }

  private class NetClassTable extends JTable {
    private final String[] column_tool_tips;
    public NetClassTable(NetClassTableModel p_table_model) {
      super(p_table_model);
      column_tool_tips = new String[10];
      column_tool_tips[0] = null;
      column_tool_tips[1] = resources.getString("column_tool_tip_1");
      column_tool_tips[2] = resources.getString("column_tool_tip_2");
      column_tool_tips[3] = resources.getString("column_tool_tip_3");
      column_tool_tips[4] = resources.getString("column_tool_tip_4");
      column_tool_tips[5] = resources.getString("column_tool_tip_5");
      column_tool_tips[6] = resources.getString("column_tool_tip_6");
      column_tool_tips[7] = resources.getString("column_tool_tip_7");
      column_tool_tips[8] = resources.getString("column_tool_tip_8");
      column_tool_tips[9] = resources.getString("column_tool_tip_9");
    }

    // Implement table header tool tips.
    @Override
    protected JTableHeader createDefaultTableHeader() {
      return new JTableHeader(columnModel) {
        @Override
        public String getToolTipText(MouseEvent e) {
          Point p = e.getPoint();
          int index = columnModel.getColumnIndexAtX(p.x);
          int realIndex = columnModel.getColumn(index).getModelIndex();
          return column_tool_tips[realIndex];
        }
      };
    }
  }

  /** Table model of the net rule table. */
  private class NetClassTableModel extends AbstractTableModel {

    private Object[][] data;
    private String[] column_names;

    public NetClassTableModel() {
      column_names = new String[ColumnName.values().length];

      for (int i = 0; i < column_names.length; ++i) {
        column_names[i] = resources.getString(ColumnName.values()[i].toString());
      }
      set_values();
    }

    /** Calculates the values in this table */
    public void set_values() {
      BoardRules board_rules =
          board_frame.board_panel.board_handling.get_routing_board().rules;
      this.data = new Object[board_rules.net_classes.count()][];
      for (int i = 0; i < data.length; ++i) {
        this.data[i] = new Object[ColumnName.values().length];
      }
      for (int i = 0; i < data.length; ++i) {
        NetClass curr_net_class = board_rules.net_classes.get(i);
        this.data[i][ColumnName.NAME.ordinal()] = curr_net_class.get_name();
        if (curr_net_class.get_via_rule() != null) {
          this.data[i][ColumnName.VIA_RULE.ordinal()] = curr_net_class.get_via_rule().name;
        }
        this.data[i][ColumnName.SHOVE_FIXED.ordinal()] =
            curr_net_class.is_shove_fixed() || !curr_net_class.get_pull_tight();
        this.data[i][ColumnName.CYCLES_WITH_AREAS.ordinal()] =
            curr_net_class.get_ignore_cycles_with_areas();
        double min_trace_length =
            board_frame.board_panel.board_handling.coordinate_transform.board_to_user(
                curr_net_class.get_minimum_trace_length());
        if (min_trace_length <= 0) {
          min_trace_length = 0;
        }
        this.data[i][ColumnName.MIN_TRACE_LENGTH.ordinal()] = (float) min_trace_length;
        double max_trace_length =
            board_frame.board_panel.board_handling.coordinate_transform.board_to_user(
                curr_net_class.get_maximum_trace_length());
        if (max_trace_length <= 0) {
          max_trace_length = -1;
        }
        this.data[i][ColumnName.MAX_TRACE_LENGTH.ordinal()] = (float) max_trace_length;
        this.data[i][ColumnName.IGNORED_BY_AUTOROUTER.ordinal()] =
            curr_net_class.is_ignored_by_autorouter;
        this.data[i][ColumnName.CLEARANCE_CLASS.ordinal()] =
            board_rules.clearance_matrix.get_name(curr_net_class.get_trace_clearance_class());
        ComboBoxLayer.Layer combo_layer = layer_combo_box.get_selected_layer();
        set_trace_width_field(i, combo_layer);
        this.data[i][ColumnName.ON_LAYER.ordinal()] = combo_layer.name;
      }
    }

    void set_trace_width_field(int p_rule_no, ComboBoxLayer.Layer p_layer) {
      float trace_width;
      BoardHandling board_handling =
          board_frame.board_panel.board_handling;
      BoardRules board_rules = board_handling.get_routing_board().rules;
      NetClass curr_net_class = board_rules.net_classes.get(p_rule_no);
      if (p_layer.index == ComboBoxLayer.ALL_LAYER_INDEX) {
        // all layers
        if (curr_net_class.trace_width_is_layer_dependent()) {
          trace_width = (float) -1;
        } else {
          trace_width =
              (float)
                  board_handling.coordinate_transform.board_to_user(
                      2 * curr_net_class.get_trace_half_width(0));
        }

      } else if (p_layer.index == ComboBoxLayer.INNER_LAYER_INDEX) {
        // all inner layers

        if (curr_net_class.trace_width_is_inner_layer_dependent()) {
          trace_width = (float) -1;
        } else {
          int first_inner_signal_layer_no = 1;
          LayerStructure layer_structure =
              board_handling.get_routing_board().layer_structure;
          while (!layer_structure.arr[first_inner_signal_layer_no].is_signal) {
            ++first_inner_signal_layer_no;
          }
          if (first_inner_signal_layer_no < layer_structure.arr.length - 1) {

            trace_width =
                (float)
                    board_handling.coordinate_transform.board_to_user(
                        2 * curr_net_class.get_trace_half_width(first_inner_signal_layer_no));
          } else {
            trace_width = (float) 0;
          }
        }
      } else {
        trace_width =
            (float)
                board_handling.coordinate_transform.board_to_user(
                    2 * curr_net_class.get_trace_half_width(p_layer.index));
      }
      this.data[p_rule_no][ColumnName.TRACE_WIDTH.ordinal()] = trace_width;
      fireTableCellUpdated(p_rule_no, ColumnName.TRACE_WIDTH.ordinal());
    }

    @Override
    public String getColumnName(int p_col) {
      return column_names[p_col];
    }

    @Override
    public int getRowCount() {
      return data.length;
    }

    @Override
    public int getColumnCount() {
      return column_names.length;
    }

    @Override
    public Object getValueAt(int p_row, int p_col) {
      return data[p_row][p_col];
    }

    @Override
    public void setValueAt(Object p_value, int p_row, int p_col) {
      RoutingBoard routing_board =
          board_frame.board_panel.board_handling.get_routing_board();
      BoardRules board_rules = routing_board.rules;
      Object net_class_name = getValueAt(p_row, ColumnName.NAME.ordinal());
      if (!(net_class_name instanceof String)) {
        FRLogger.warn("EditNetRuLesVindow.setValueAt: String expected");
        return;
      }
      NetClass net_rule = board_rules.net_classes.get((String) net_class_name);
      if (net_rule == null) {
        FRLogger.warn("EditNetRuLesVindow.setValueAt: net_rule not found");
        return;
      }

      if (p_col == ColumnName.NAME.ordinal()) {
        if (!(p_value instanceof String)) {
          return;
        }
        String new_name = (String) p_value;
        if (board_rules.net_classes.get(new_name) != null) {
          return; // name exists already
        }
        net_rule.set_name(new_name);
        board_frame.via_window.refresh();
      } else if (p_col == ColumnName.VIA_RULE.ordinal()) {
        if (!(p_value instanceof String)) {
          return;
        }
        String new_name = (String) p_value;
        ViaRule new_via_rule = board_rules.get_via_rule(new_name);
        if (new_via_rule == null) {
          FRLogger.warn("EditNetRuLesVindow.setValueAt: via_rule not found");
          return;
        }
        net_rule.set_via_rule(new_via_rule);
      } else if (p_col == ColumnName.SHOVE_FIXED.ordinal()) {
        if (!(p_value instanceof Boolean)) {
          return;
        }
        boolean value = (Boolean) p_value;
        net_rule.set_shove_fixed(value);
        net_rule.set_pull_tight(!value);
      } else if (p_col == ColumnName.CYCLES_WITH_AREAS.ordinal()) {
        if (!(p_value instanceof Boolean)) {
          return;
        }
        boolean value = (Boolean) p_value;
        net_rule.set_ignore_cycles_with_areas(value);
      } else if (p_col == ColumnName.MIN_TRACE_LENGTH.ordinal()) {

        float curr_value = 0f;
        if (p_value instanceof Float) {
          curr_value = (Float) p_value;
        } else if (p_value instanceof String) {
          // Workaround because of a localisation Bug in Java
          // The numbers are always displayed in the English Format.

          try {
            curr_value = Float.parseFloat((String) p_value);
          } catch (Exception e) {
            curr_value = 0f;
          }
          p_value = String.valueOf(curr_value);
        }
        if (curr_value <= 0) {
          curr_value = (float) 0;
          p_value = curr_value;
        }
        double min_trace_length =
            Math.round(
                board_frame.board_panel.board_handling.coordinate_transform.user_to_board(
                    curr_value));
        net_rule.set_minimum_trace_length(min_trace_length);
        board_frame.board_panel.board_handling.recalculate_length_violations();
      } else if (p_col == ColumnName.MAX_TRACE_LENGTH.ordinal()) {
        float curr_value = 0f;
        if (p_value instanceof Float) {
          curr_value = (Float) p_value;
        } else if (p_value instanceof String) {
          // Workaround because of a localisation Bug in Java
          // The numbers are always displayed in the English Format.

          try {
            curr_value = Float.parseFloat((String) p_value);
          } catch (Exception e) {
            curr_value = 0f;
          }
          p_value = String.valueOf(curr_value);
        }
        if (curr_value <= 0) {
          curr_value = (float) 0;
          p_value = curr_value - 1;
        }

        double max_trace_length =
            Math.round(
                board_frame.board_panel.board_handling.coordinate_transform.user_to_board(
                    curr_value));
        net_rule.set_maximum_trace_length(max_trace_length);
        board_frame.board_panel.board_handling.recalculate_length_violations();
      } else if (p_col == ColumnName.IGNORED_BY_AUTOROUTER.ordinal()) {
        if (!(p_value instanceof Boolean)) {
          return;
        }
        boolean value = (Boolean) p_value;
        net_rule.is_ignored_by_autorouter = value;
      } else if (p_col == ColumnName.CLEARANCE_CLASS.ordinal()) {
        if (!(p_value instanceof String)) {
          return;
        }
        String new_name = (String) p_value;
        int new_cl_class_index = board_rules.clearance_matrix.get_no(new_name);
        {
          if (new_cl_class_index < 0) {
            FRLogger.warn("EditNetRuLesVindow.setValueAt: clearance class not found");
            return;
          }
        }
        net_rule.set_trace_clearance_class(new_cl_class_index);
      } else if (p_col == ColumnName.TRACE_WIDTH.ordinal()) {
        float curr_value = 0f;
        if (p_value instanceof Float) {
          curr_value = (Float) p_value;
        } else if (p_value instanceof String) {
          // Workaround because of a localisation Bug in Java
          // The numbers are always displayed in the English Format.

          try {
            curr_value = Float.parseFloat((String) p_value);
          } catch (Exception e) {
            curr_value = 0f;
          }
        }
        if (curr_value < 0) {
          return;
        }
        int curr_half_width;
        boolean is_active;
        if (curr_value == 0) {
          curr_half_width = 0;
          is_active = false;
        } else {
          curr_half_width =
              (int)
                  Math.round(
                      board_frame.board_panel.board_handling.coordinate_transform.user_to_board(
                          0.5 * curr_value));
          if (curr_half_width <= 0) {
            return;
          }
          is_active = true;
        }
        if (p_value instanceof String) {
          p_value = String.valueOf(curr_value);
        }
        int layer_index = layer_combo_box.get_selected_layer().index;
        NetClass curr_net_class = board_rules.net_classes.get(p_row);

        if (layer_index == ComboBoxLayer.ALL_LAYER_INDEX) {
          curr_net_class.set_trace_half_width(curr_half_width);
          curr_net_class.set_all_layers_active(is_active);
        } else if (layer_index == ComboBoxLayer.INNER_LAYER_INDEX) {
          curr_net_class.set_trace_half_width_on_inner(curr_half_width);
          curr_net_class.set_all_inner_layers_active(is_active);
        } else {
          curr_net_class.set_trace_half_width(layer_index, curr_half_width);
          curr_net_class.set_active_routing_layer(layer_index, is_active);
        }
      } else if (p_col == ColumnName.ON_LAYER.ordinal()) {
        if (!(p_value instanceof ComboBoxLayer.Layer)) {
          return;
        }
        set_trace_width_field(p_row, (ComboBoxLayer.Layer) p_value);
      }
      this.data[p_row][p_col] = p_value;
      fireTableCellUpdated(p_row, p_col);
    }

    @Override
    public boolean isCellEditable(int p_row, int p_col) {
      // the name of the default class is not editable
      return p_row > 0 || p_col > 0;
    }

    @Override
    public Class<?> getColumnClass(int p_col) {
      Object curr_entry = getValueAt(0, p_col);
      Class<?> curr_class = curr_entry.getClass();
      // changed because of a localisation bug in Java
      if (curr_entry instanceof Float) {
        curr_class = String.class;
      }
      return curr_class;
    }
  }
}
