package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.Padstack;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edit window for the table of available vias.
 */
public class WindowEditVias extends BoardSavableSubWindow
{

  private static final int TEXTFIELD_HEIGHT = 16;
  private static final int TEXTFIELD_WIDTH = 100;
  private final BoardFrame board_frame;
  private final JPanel main_panel;
  private final JComboBox<String> cl_class_combo_box;
  private final JComboBox<String> padstack_combo_box;
  private JScrollPane scroll_pane;
  private JTable table;
  private ViaTableModel table_model;

  /**
   * Creates a new instance of ViaTablePanel
   */
  public WindowEditVias(BoardFrame p_board_frame)
  {
    setLanguage(p_board_frame.get_locale());
    this.setTitle(tm.getText("title"));

    this.board_frame = p_board_frame;

    this.main_panel = new JPanel();
    this.main_panel.setLayout(new BorderLayout());

    this.cl_class_combo_box = new JComboBox<>();
    this.padstack_combo_box = new JComboBox<>();
    add_combobox_items();

    add_table();

    JPanel via_info_button_panel = new JPanel();
    via_info_button_panel.setLayout(new FlowLayout());
    this.main_panel.add(via_info_button_panel, BorderLayout.SOUTH);
    final JButton rules_vias_vias_edit_add_button = new JButton(tm.getText("add"));
    rules_vias_vias_edit_add_button.setToolTipText(tm.getText("add_tooltip"));
    rules_vias_vias_edit_add_button.addActionListener(new AddViaListener());
    rules_vias_vias_edit_add_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_vias_edit_add_button", rules_vias_vias_edit_add_button.getText()));
    via_info_button_panel.add(rules_vias_vias_edit_add_button);
    final JButton rules_vias_vias_edit_remove_button = new JButton(tm.getText("remove"));
    rules_vias_vias_edit_remove_button.setToolTipText(tm.getText("remove_tooltip"));
    rules_vias_vias_edit_remove_button.addActionListener(new RemoveViaListener());
    rules_vias_vias_edit_remove_button.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_vias_edit_remove_button", rules_vias_vias_edit_remove_button.getText()));
    via_info_button_panel.add(rules_vias_vias_edit_remove_button);

    this.add(main_panel);
    this.pack();
  }

  /**
   * Recalculates all values displayed in the parent window
   */
  @Override
  public void refresh()
  {
    this.padstack_combo_box.removeAllItems();
    this.cl_class_combo_box.removeAllItems();
    this.add_combobox_items();
    this.table_model.set_values();
  }

  private void add_table()
  {
    this.table_model = new ViaTableModel();
    this.table = new JTable(this.table_model);
    this.scroll_pane = new JScrollPane(this.table);
    int table_height = TEXTFIELD_HEIGHT * this.table_model.getRowCount();
    int table_width = TEXTFIELD_WIDTH * this.table_model.getColumnCount();
    this.table.setPreferredScrollableViewportSize(new Dimension(table_width, table_height));
    this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.main_panel.add(scroll_pane, BorderLayout.CENTER);

    this.table
        .getColumnModel()
        .getColumn(ColumnName.CLEARANCE_CLASS.ordinal())
        .setCellEditor(new DefaultCellEditor(cl_class_combo_box));

    this.table
        .getColumnModel()
        .getColumn(ColumnName.PADSTACK.ordinal())
        .setCellEditor(new DefaultCellEditor(padstack_combo_box));
  }

  private void add_combobox_items()
  {
    RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
    for (int i = 0; i < routing_board.rules.clearance_matrix.get_class_count(); ++i)
    {
      cl_class_combo_box.addItem(routing_board.rules.clearance_matrix.get_name(i));
    }
    for (int i = 0; i < routing_board.library.via_padstack_count(); ++i)
    {
      padstack_combo_box.addItem(routing_board.library.get_via_padstack(i).name);
    }
  }

  /**
   * Adjusts the displayed window with the via table after the size of the table has been changed.
   */
  private void adjust_table()
  {
    this.table_model = new ViaTableModel();
    this.table = new JTable(this.table_model);
    this.main_panel.remove(this.scroll_pane);
    this.add_table();
    this.pack();
    this.board_frame.refresh_windows();
  }

  private enum ColumnName
  {
    NAME, PADSTACK, CLEARANCE_CLASS, ATTACH_SMD
  }

  private class AddViaListener implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent p_evt)
    {
      RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
      ViaInfos via_infos = routing_board.rules.via_infos;
      int no = 1;
      String new_name;
      final String name_start = tm.getText("new_via");
      for (; ; )
      {
        new_name = name_start + no;
        if (!via_infos.name_exists(new_name))
        {
          break;
        }
        ++no;
      }
      NetClass default_net_class = routing_board.rules.get_default_net_class();
      ViaInfo new_via = new ViaInfo(new_name, routing_board.library.get_via_padstack(0), default_net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA), false, routing_board.rules);
      via_infos.add(new_via);
      adjust_table();
    }
  }

  private class RemoveViaListener implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent p_evt)
    {
      if (table_model.getRowCount() <= 1)
      {
        board_frame.screen_messages.set_status_message(tm.getText("message_1"));
        return;
      }
      int selected_row = table.getSelectedRow();
      if (selected_row < 0)
      {
        return;
      }
      Object via_name = table_model.getValueAt(selected_row, ColumnName.NAME.ordinal());
      if (!(via_name instanceof String))
      {
        return;
      }
      BoardRules board_rules = board_frame.board_panel.board_handling.get_routing_board().rules;
      ViaInfo via_info = board_rules.via_infos.get((String) via_name);
      // Check, if via_info is used in a via rule.
      for (ViaRule curr_rule : board_rules.via_rules)
      {
        if (curr_rule.contains(via_info))
        {
          String message = tm.getText("message_2") + " " + curr_rule.name;
          board_frame.screen_messages.set_status_message(message);
          return;
        }
      }
      if (board_rules.via_infos.remove(via_info))
      {
        adjust_table();
        String message = tm.getText("via") + " " + via_info.get_name() + " " + tm.getText("removed");
        board_frame.screen_messages.set_status_message(message);
      }
    }
  }

  /**
   * Table model of the via table.
   */
  private class ViaTableModel extends AbstractTableModel
  {
    private final Object[][] data;
    private final String[] column_names;

    public ViaTableModel()
    {
      column_names = new String[ColumnName.values().length];

      for (int i = 0; i < column_names.length; ++i)
      {
        column_names[i] = tm.getText((ColumnName.values()[i]).toString());
      }
      BoardRules board_rules = board_frame.board_panel.board_handling.get_routing_board().rules;
      data = new Object[board_rules.via_infos.count()][];
      for (int i = 0; i < data.length; ++i)
      {
        this.data[i] = new Object[ColumnName.values().length];
      }
      set_values();
    }

    /**
     * Calculates the values in this table
     */
    public void set_values()
    {
      BoardRules board_rules = board_frame.board_panel.board_handling.get_routing_board().rules;
      for (int i = 0; i < data.length; ++i)
      {
        ViaInfo curr_via = board_rules.via_infos.get(i);
        this.data[i][ColumnName.NAME.ordinal()] = curr_via.get_name();
        this.data[i][ColumnName.PADSTACK.ordinal()] = curr_via.get_padstack().name;
        this.data[i][ColumnName.CLEARANCE_CLASS.ordinal()] = board_rules.clearance_matrix.get_name(curr_via.get_clearance_class());
        this.data[i][ColumnName.ATTACH_SMD.ordinal()] = curr_via.attach_smd_allowed();
      }
    }

    @Override
    public String getColumnName(int p_col)
    {
      return column_names[p_col];
    }

    @Override
    public int getRowCount()
    {
      return data.length;
    }

    @Override
    public int getColumnCount()
    {
      return column_names.length;
    }

    @Override
    public Object getValueAt(int p_row, int p_col)
    {
      return data[p_row][p_col];
    }

    @Override
    public void setValueAt(Object p_value, int p_row, int p_col)
    {
      RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
      BoardRules board_rules = routing_board.rules;
      Object via_name = getValueAt(p_row, ColumnName.NAME.ordinal());
      if (!(via_name instanceof String))
      {
        FRLogger.warn("ViaVindow.setValueAt: String expected");
        return;
      }
      ViaInfo via_info = board_rules.via_infos.get((String) via_name);
      if (via_info == null)
      {
        FRLogger.warn("ViaVindow.setValueAt: via_info not found");
        return;
      }

      if (p_col == ColumnName.NAME.ordinal())
      {
        if (!(p_value instanceof String new_name))
        {
          return;
        }
        if (board_rules.via_infos.name_exists(new_name))
        {
          return;
        }
        via_info.set_name(new_name);
        board_frame.via_window.refresh();
      }
      else if (p_col == ColumnName.PADSTACK.ordinal())
      {
        if (!(p_value instanceof String new_name))
        {
          return;
        }
        Padstack new_padstack = routing_board.library.get_via_padstack(new_name);
        if (new_padstack == null)
        {
          FRLogger.warn("ViaVindow.setValueAt: via padstack not found");
          return;
        }
        via_info.set_padstack(new_padstack);
      }
      else if (p_col == ColumnName.CLEARANCE_CLASS.ordinal())
      {
        if (!(p_value instanceof String new_name))
        {
          return;
        }
        int new_cl_class_index = board_rules.clearance_matrix.get_no(new_name);
        {
          if (new_cl_class_index < 0)
          {
            FRLogger.warn("ViaVindow.setValueAt: clearance class not found");
            return;
          }
        }
        via_info.set_clearance_class(new_cl_class_index);
      }
      else if (p_col == ColumnName.ATTACH_SMD.ordinal())
      {
        if (!(p_value instanceof Boolean attach_smd))
        {
          FRLogger.warn("ViaVindow.setValueAt: Boolean expected");
          return;
        }
        via_info.set_attach_smd_allowed(attach_smd);
      }
      this.data[p_row][p_col] = p_value;
      fireTableCellUpdated(p_row, p_col);
    }

    @Override
    public boolean isCellEditable(int p_row, int p_col)
    {
      return true;
    }

    @Override
    public Class<?> getColumnClass(int p_col)
    {
      return getValueAt(0, p_col).getClass();
    }
  }
}