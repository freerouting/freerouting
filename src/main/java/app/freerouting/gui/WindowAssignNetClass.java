package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;

public class WindowAssignNetClass extends BoardSavableSubWindow {

  private static final int TEXTFIELD_HEIGHT = 16;
  private static final int TEXTFIELD_WIDTH = 100;
  private final BoardFrame board_frame;
  private final javax.swing.JPanel main_panel;
  private final java.util.ResourceBundle resources;
  private final javax.swing.JScrollPane scroll_pane;
  private final AssignRuleTable table;
  private final AssignRuleTableModel table_model;

  private javax.swing.JComboBox<NetClass> net_rule_combo_box;

  /** Creates a new instance of AssignNetRulesWindow */
  public WindowAssignNetClass(BoardFrame p_board_frame) {
    this.resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.WindowAssignNetClass", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    this.board_frame = p_board_frame;

    this.main_panel = new javax.swing.JPanel();
    this.main_panel.setLayout(new java.awt.BorderLayout());

    this.table_model = new AssignRuleTableModel();
    this.table = new AssignRuleTable(this.table_model);
    this.table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    this.scroll_pane = new javax.swing.JScrollPane(this.table);
    int table_height = TEXTFIELD_HEIGHT * Math.min(this.table_model.getRowCount(), 20);
    int table_width = TEXTFIELD_WIDTH * this.table_model.getColumnCount();
    this.table.setPreferredScrollableViewportSize(
        new java.awt.Dimension(table_width, table_height));
    this.main_panel.add(scroll_pane, java.awt.BorderLayout.CENTER);
    add_net_class_combo_box();

    p_board_frame.set_context_sensitive_help(this, "WindowNetClasses_AssignNetClass");

    this.add(main_panel);
    this.pack();
  }

  private void add_net_class_combo_box() {
    this.net_rule_combo_box = new javax.swing.JComboBox<>();
    app.freerouting.board.RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    for (int i = 0; i < routing_board.rules.net_classes.count(); ++i) {
      net_rule_combo_box.addItem(routing_board.rules.net_classes.get(i));
    }
    this.table
        .getColumnModel()
        .getColumn(1)
        .setCellEditor(new javax.swing.DefaultCellEditor(net_rule_combo_box));
  }

  public void refresh() {
    // Reinsert the net class column.
    for (int i = 0; i < table_model.getRowCount(); ++i) {
      table_model.setValueAt(((Net) table_model.getValueAt(i, 0)).get_class(), i, 1);
    }

    // Reinsert the net rule combobox because a rule may have  been added or deleted.
    add_net_class_combo_box();
  }

  private class AssignRuleTable extends javax.swing.JTable {
    private final String[] column_tool_tips = {
      resources.getString("net_name_tooltip"), resources.getString("class_name_tooltip")
    };
    public AssignRuleTable(AssignRuleTableModel p_table_model) {
      super(p_table_model);
    }

    // Implement table header tool tips.
    protected javax.swing.table.JTableHeader createDefaultTableHeader() {
      return new javax.swing.table.JTableHeader(columnModel) {
        public String getToolTipText(java.awt.event.MouseEvent e) {
          java.awt.Point p = e.getPoint();
          int index = columnModel.getColumnIndexAtX(p.x);
          int realIndex = columnModel.getColumn(index).getModelIndex();
          return column_tool_tips[realIndex];
        }
      };
    }
  }

  /** Table model of the net rule table. */
  private class AssignRuleTableModel extends javax.swing.table.AbstractTableModel {
    private Object[][] data = null;
    private String[] column_names = null;

    public AssignRuleTableModel() {
      column_names = new String[2];

      column_names[0] = resources.getString("net_name");
      column_names[1] = resources.getString("class_name");

      app.freerouting.rules.BoardRules board_rules =
          board_frame.board_panel.board_handling.get_routing_board().rules;
      data = new Object[board_rules.nets.max_net_no()][];
      for (int i = 0; i < data.length; ++i) {
        this.data[i] = new Object[column_names.length];
      }
      set_values();
    }

    /** Calculates the the valus in this table */
    public void set_values() {
      app.freerouting.rules.BoardRules board_rules =
          board_frame.board_panel.board_handling.get_routing_board().rules;
      Net[] sorted_arr = new Net[this.getRowCount()];
      for (int i = 0; i < sorted_arr.length; ++i) {
        sorted_arr[i] = board_rules.nets.get(i + 1);
      }
      java.util.Arrays.sort(sorted_arr);
      for (int i = 0; i < data.length; ++i) {
        this.data[i][0] = sorted_arr[i];
        this.data[i][1] = sorted_arr[i].get_class();
      }
    }

    public String getColumnName(int p_col) {
      return column_names[p_col];
    }

    public int getRowCount() {
      return data.length;
    }

    public int getColumnCount() {
      return column_names.length;
    }

    public Object getValueAt(int p_row, int p_col) {
      return data[p_row][p_col];
    }

    public boolean isCellEditable(int p_row, int p_col) {
      return p_col > 0;
    }

    public void setValueAt(Object p_value, int p_row, int p_col) {
      if (p_col != 1 || !(p_value instanceof NetClass)) {
        return;
      }
      Object first_row_object = getValueAt(p_row, 0);
      if (!(first_row_object instanceof Net)) {
        FRLogger.warn("AssignNetRuLesVindow.setValueAt: Net expected");
        return;
      }
      Net curr_net = (Net) first_row_object;
      NetClass curr_net_rule = (NetClass) p_value;
      curr_net.set_class(curr_net_rule);

      this.data[p_row][p_col] = p_value;
      fireTableCellUpdated(p_row, p_col);
    }
  }
}
