package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.library.Padstack;
import app.freerouting.logger.FRLogger;
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

  /** Creates a new instance of ViaTablePanel. */
  public WindowEditVias(BoardFrame boardFrame) {
    setLanguage(boardFrame.get_locale());
    this.setTitle(tm.getText("title"));

    this.boardFrame = boardFrame;

    this.mainPanel = new JPanel();
    this.mainPanel.setLayout(new BorderLayout());

    this.clClassComboBox = new JComboBox<>();
    this.padstackComboBox = new JComboBox<>();
    addComboboxItems();

    addTable();

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

  /** Recalculates all values displayed in the parent window. */
  @Override
  public void refresh() {
    this.padstackComboBox.removeAllItems();
    this.clClassComboBox.removeAllItems();
    this.addComboboxItems();
    this.tableModel.setValues();
  }

  private void addTable() {
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

  private void addComboboxItems() {
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
    for (int i = 0; i < routingBoard.rules.clearanceMatrix.getClassCount(); i++) {
      clClassComboBox.addItem(routingBoard.rules.clearanceMatrix.getName(i));
    }
    for (int i = 0; i < routingBoard.library.viaPadstackCount(); i++) {
      padstackComboBox.addItem(routingBoard.library.getViaPadstack(i).name);
    }
  }

  /**
   * Adjusts the displayed window with the via table after the size of the table has been changed.
   */
  private void adjustTable() {
    this.tableModel = new ViaTableModel();
    this.table = new JTable(this.tableModel);
    this.mainPanel.remove(this.scrollPane);
    this.addTable();
    this.pack();
    this.boardFrame.refreshWindows();
  }

  private enum ColumnName {
    NAME,
    PADSTACK,
    CLEARANCE_CLASS,
    ATTACH_SMD
  }

  private class AddViaListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
      ViaInfos viaInfos = routingBoard.rules.viaInfos;
      int no = 1;
      String newName;
      final String nameStart = tm.getText("newVia");
      for (; ; ) {
        newName = nameStart + no;
        if (!viaInfos.nameExists(newName)) {
          break;
        }
        ++no;
      }
      NetClass defaultNetClass = routingBoard.rules.getDefaultNetClass();
      ViaInfo newVia =
          new ViaInfo(
              newName,
              routingBoard.library.getViaPadstack(0),
              defaultNetClass.defaultItemClearanceClasses.get(
                  DefaultItemClearanceClasses.ItemClass.VIA),
              false,
              routingBoard.rules);
      viaInfos.add(newVia);
      adjustTable();
    }
  }

  private class RemoveViaListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      if (tableModel.getRowCount() <= 1) {
        boardFrame.screenMessages.setStatusMessage(tm.getText("last_via_not_removed"));
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
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      ViaInfo viaInfo = boardRules.viaInfos.get((String) viaName);
      // Check, if viaInfo is used in a via rule.
      for (ViaRule currentRule : boardRules.viaRules) {
        if (currentRule.contains(viaInfo)) {
          boardFrame.screenMessages.setStatusMessage(
              tm.getText("via_not_removed_in_rule_message", currentRule.name));
          return;
        }
      }
      if (boardRules.viaInfos.remove(viaInfo)) {
        adjustTable();
        boardFrame.screenMessages.setStatusMessage(
            tm.getText("via_removed_message", viaInfo.getName()));
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
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      data = new Object[boardRules.viaInfos.count()][];
      for (int i = 0; i < data.length; i++) {
        this.data[i] = new Object[ColumnName.values().length];
      }
      setValues();
    }

    /** Calculates the values in this table. */
    public void setValues() {
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      for (int i = 0; i < data.length; i++) {
        ViaInfo currentVia = boardRules.viaInfos.get(i);
        this.data[i][ColumnName.NAME.ordinal()] = currentVia.getName();
        this.data[i][ColumnName.PADSTACK.ordinal()] = currentVia.getPadstack().name;
        this.data[i][ColumnName.CLEARANCE_CLASS.ordinal()] =
            boardRules.clearanceMatrix.getName(currentVia.getClearanceClass());
        this.data[i][ColumnName.ATTACH_SMD.ordinal()] = currentVia.attachSmdAllowed();
      }
    }

    @Override
    public String getColumnName(int col) {
      return columnNames[col];
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
    public Object getValueAt(int row, int col) {
      return data[row][col];
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
      BoardRules boardRules = routingBoard.rules;
      Object viaName = getValueAt(row, ColumnName.NAME.ordinal());
      if (!(viaName instanceof String)) {
        FRLogger.warn("ViaVindow.setValueAt: String expected");
        return;
      }
      ViaInfo viaInfo = boardRules.viaInfos.get((String) viaName);
      if (viaInfo == null) {
        FRLogger.warn("ViaVindow.setValueAt: viaInfo not found");
        return;
      }

      if (col == ColumnName.NAME.ordinal()) {
        if (!(value instanceof String newName)) {
          return;
        }
        if (boardRules.viaInfos.nameExists(newName)) {
          return;
        }
        viaInfo.setName(newName);
        boardFrame.viaWindow.refresh();
      } else if (col == ColumnName.PADSTACK.ordinal()) {
        if (!(value instanceof String newName)) {
          return;
        }
        Padstack newPadstack = routingBoard.library.getViaPadstack(newName);
        if (newPadstack == null) {
          FRLogger.warn("ViaVindow.setValueAt: via padstack not found");
          return;
        }
        viaInfo.setPadstack(newPadstack);
      } else if (col == ColumnName.CLEARANCE_CLASS.ordinal()) {
        if (!(value instanceof String newName)) {
          return;
        }
        int newClClassIndex = boardRules.clearanceMatrix.getNo(newName);
        {
          if (newClClassIndex < 0) {
            FRLogger.warn("ViaVindow.setValueAt: clearance class not found");
            return;
          }
        }
        viaInfo.setClearanceClass(newClClassIndex);
      } else if (col == ColumnName.ATTACH_SMD.ordinal()) {
        if (!(value instanceof Boolean attachSmd)) {
          FRLogger.warn("ViaVindow.setValueAt: Boolean expected");
          return;
        }
        viaInfo.setAttachSmdAllowed(attachSmd);
      }
      this.data[row][col] = value;
      fireTableCellUpdated(row, col);
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return true;
    }

    @Override
    public Class<?> getColumnClass(int col) {
      return getValueAt(0, col).getClass();
    }
  }
}
