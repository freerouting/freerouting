package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.Padstack;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaInfos;
import app.freerouting.rules.ViaRule;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/** Edit window for the table of available vias. */
public class WindowEditVias extends BoardSavableSubWindow {

  private static final int TEXTFIELD_HEIGHT = 16;
  private static final int TEXTFIELD_WIDTH = 100;
  private final BoardFrame boardFrame;
  private final JPanel mainPanel;
  private final JComboBox<String> clClassComboBox;
  private final JComboBox<String> padstackComboBox;
  private JScrollPane scrollPane;
  private JTable table;
  private ViaTableModel tableModel;

  /** Creates a new instance of ViaTablePanel */
  public WindowEditVias(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());
    this.setTitle(tm.getText("title"));

    this.boardFrame = p_board_frame;

    this.mainPanel = new JPanel();
    this.mainPanel.setLayout(new BorderLayout());

    this.clClassComboBox = new JComboBox<>();
    this.padstackComboBox = new JComboBox<>();
    add_combobox_items();

    add_table();

    JPanel viaInfoButtonPanel = new JPanel();
    viaInfoButtonPanel.setLayout(new FlowLayout());
    this.mainPanel.add(viaInfoButtonPanel, BorderLayout.SOUTH);
    final JButton rulesViasViasEditAddButton = new JButton(tm.getText("add"));
    rulesViasViasEditAddButton.setToolTipText(tm.getText("add_tooltip"));
    rulesViasViasEditAddButton.addActionListener(new AddViaListener());
    rulesViasViasEditAddButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasViasEditAddButton", rulesViasViasEditAddButton.getText()));
    viaInfoButtonPanel.add(rulesViasViasEditAddButton);
    final JButton rulesViasViasEditRemoveButton = new JButton(tm.getText("remove"));
    rulesViasViasEditRemoveButton.setToolTipText(tm.getText("remove_tooltip"));
    rulesViasViasEditRemoveButton.addActionListener(new RemoveViaListener());
    rulesViasViasEditRemoveButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasViasEditRemoveButton", rulesViasViasEditRemoveButton.getText()));
    viaInfoButtonPanel.add(rulesViasViasEditRemoveButton);

    this.add(mainPanel);
    this.pack();
  }

  /** Recalculates all values displayed in the parent window */
  @Override
  public void refresh() {
    this.padstackComboBox.removeAllItems();
    this.clClassComboBox.removeAllItems();
    this.add_combobox_items();
    this.tableModel.set_values();
  }

  private void add_table() {
    this.tableModel = new ViaTableModel();
    this.table = new JTable(this.tableModel);
    this.scrollPane = new JScrollPane(this.table);
    int tableHeight = TEXTFIELD_HEIGHT * this.tableModel.getRowCount();
    int tableWidth = TEXTFIELD_WIDTH * this.tableModel.getColumnCount();
    this.table.setPreferredScrollableViewportSize(new Dimension(tableWidth, tableHeight));
    this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.mainPanel.add(scrollPane, BorderLayout.CENTER);

    this.table
        .getColumnModel()
        .getColumn(ColumnName.CLEARANCE_CLASS.ordinal())
        .setCellEditor(new DefaultCellEditor(clClassComboBox));

    this.table
        .getColumnModel()
        .getColumn(ColumnName.PADSTACK.ordinal())
        .setCellEditor(new DefaultCellEditor(padstackComboBox));
  }

  private void add_combobox_items() {
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
    for (int i = 0; i < routingBoard.rules.clearanceMatrix.get_class_count(); i++) {
      clClassComboBox.addItem(routingBoard.rules.clearanceMatrix.get_name(i));
    }
    for (int i = 0; i < routingBoard.library.via_padstack_count(); i++) {
      padstackComboBox.addItem(routingBoard.library.get_via_padstack(i).name);
    }
  }

  /**
   * Adjusts the displayed window with the via table after the size of the table has been changed.
   */
  private void adjust_table() {
    this.tableModel = new ViaTableModel();
    this.table = new JTable(this.tableModel);
    this.mainPanel.remove(this.scrollPane);
    this.add_table();
    this.pack();
    this.boardFrame.refresh_windows();
  }

  private enum ColumnName {
    NAME,
    PADSTACK,
    CLEARANCE_CLASS,
    ATTACH_SMD
  }

  private class AddViaListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
      ViaInfos viaInfos = routingBoard.rules.viaInfos;
      int no = 1;
      String newName;
      final String nameStart = tm.getText("newVia");
      for (; ; ) {
        newName = nameStart + no;
        if (!viaInfos.name_exists(newName)) {
          break;
        }
        ++no;
      }
      NetClass defaultNetClass = routingBoard.rules.get_default_net_class();
      ViaInfo newVia =
          new ViaInfo(
              newName,
              routingBoard.library.get_via_padstack(0),
              defaultNetClass.defaultItemClearanceClasses.get(
                  DefaultItemClearanceClasses.ItemClass.VIA),
              false,
              routingBoard.rules);
      viaInfos.add(newVia);
      adjust_table();
    }
  }

  private class RemoveViaListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (tableModel.getRowCount() <= 1) {
        boardFrame.screenMessages.set_status_message(tm.getText("last_via_not_removed"));
        return;
      }
      int selectedRow = table.getSelectedRow();
      if (selectedRow < 0) {
        return;
      }
      Object viaName = tableModel.getValueAt(selectedRow, ColumnName.NAME.ordinal());
      if (!(viaName instanceof String)) {
        return;
      }
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.get_routing_board().rules;
      ViaInfo viaInfo = boardRules.viaInfos.get((String) viaName);
      // Check, if viaInfo is used in a via rule.
      for (ViaRule currRule : boardRules.viaRules) {
        if (currRule.contains(viaInfo)) {
          boardFrame.screenMessages.set_status_message(
              tm.getText("via_not_removed_in_rule_message", currRule.name));
          return;
        }
      }
      if (boardRules.viaInfos.remove(viaInfo)) {
        adjust_table();
        boardFrame.screenMessages.set_status_message(
            tm.getText("via_removed_message", viaInfo.get_name()));
      }
    }
  }

  /** Table model of the via table. */
  private class ViaTableModel extends AbstractTableModel {

    private final Object[][] data;
    private final String[] columnNames;

    public ViaTableModel() {
      columnNames = new String[ColumnName.values().length];

      for (int i = 0; i < columnNames.length; i++) {
        columnNames[i] = tm.getText(ColumnName.values()[i].toString());
      }
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.get_routing_board().rules;
      data = new Object[boardRules.viaInfos.count()][];
      for (int i = 0; i < data.length; i++) {
        this.data[i] = new Object[ColumnName.values().length];
      }
      set_values();
    }

    /** Calculates the values in this table */
    public void set_values() {
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.get_routing_board().rules;
      for (int i = 0; i < data.length; i++) {
        ViaInfo currVia = boardRules.viaInfos.get(i);
        this.data[i][ColumnName.NAME.ordinal()] = currVia.get_name();
        this.data[i][ColumnName.PADSTACK.ordinal()] = currVia.get_padstack().name;
        this.data[i][ColumnName.CLEARANCE_CLASS.ordinal()] =
            boardRules.clearanceMatrix.get_name(currVia.get_clearance_class());
        this.data[i][ColumnName.ATTACH_SMD.ordinal()] = currVia.attach_smd_allowed();
      }
    }

    @Override
    public String getColumnName(int p_col) {
      return columnNames[p_col];
    }

    @Override
    public int getRowCount() {
      return data.length;
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public Object getValueAt(int p_row, int p_col) {
      return data[p_row][p_col];
    }

    @Override
    public void setValueAt(Object p_value, int p_row, int p_col) {
      RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
      BoardRules boardRules = routingBoard.rules;
      Object viaName = getValueAt(p_row, ColumnName.NAME.ordinal());
      if (!(viaName instanceof String)) {
        FRLogger.warn("ViaVindow.setValueAt: String expected");
        return;
      }
      ViaInfo viaInfo = boardRules.viaInfos.get((String) viaName);
      if (viaInfo == null) {
        FRLogger.warn("ViaVindow.setValueAt: viaInfo not found");
        return;
      }

      if (p_col == ColumnName.NAME.ordinal()) {
        if (!(p_value instanceof String newName)) {
          return;
        }
        if (boardRules.viaInfos.name_exists(newName)) {
          return;
        }
        viaInfo.set_name(newName);
        boardFrame.viaWindow.refresh();
      } else if (p_col == ColumnName.PADSTACK.ordinal()) {
        if (!(p_value instanceof String newName)) {
          return;
        }
        Padstack newPadstack = routingBoard.library.get_via_padstack(newName);
        if (newPadstack == null) {
          FRLogger.warn("ViaVindow.setValueAt: via padstack not found");
          return;
        }
        viaInfo.set_padstack(newPadstack);
      } else if (p_col == ColumnName.CLEARANCE_CLASS.ordinal()) {
        if (!(p_value instanceof String newName)) {
          return;
        }
        int newClClassIndex = boardRules.clearanceMatrix.get_no(newName);
        {
          if (newClClassIndex < 0) {
            FRLogger.warn("ViaVindow.setValueAt: clearance class not found");
            return;
          }
        }
        viaInfo.set_clearance_class(newClClassIndex);
      } else if (p_col == ColumnName.ATTACH_SMD.ordinal()) {
        if (!(p_value instanceof Boolean attach_smd)) {
          FRLogger.warn("ViaVindow.setValueAt: Boolean expected");
          return;
        }
        viaInfo.set_attach_smd_allowed(attach_smd);
      }
      this.data[p_row][p_col] = p_value;
      fireTableCellUpdated(p_row, p_col);
    }

    @Override
    public boolean isCellEditable(int p_row, int p_col) {
      return true;
    }

    @Override
    public Class<?> getColumnClass(int p_col) {
      return getValueAt(0, p_col).getClass();
    }
  }
}
