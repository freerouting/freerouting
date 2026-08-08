package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;

public class WindowAssignNetClass extends BoardSavableSubWindow {

  private static final int TEXTFIELD_HEIGHT = 16;
  private static final int TEXTFIELD_WIDTH = 100;
  private final BoardFrame boardFrame;
  private final JPanel mainPanel;
  private final JScrollPane scrollPane;
  private final AssignRuleTable table;
  private final AssignRuleTableModel tableModel;

  private JComboBox<NetClass> netRuleComboBox;

  /** Creates a new instance of AssignNetRulesWindow */
  public WindowAssignNetClass(BoardFrame pBoardFrame) {
    setLanguage(pBoardFrame.get_locale());

    this.setTitle(tm.getText("title"));

    this.boardFrame = pBoardFrame;

    this.mainPanel = new JPanel();
    this.mainPanel.setLayout(new BorderLayout());

    this.tableModel = new AssignRuleTableModel();
    this.table = new AssignRuleTable(this.tableModel);
    this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.scrollPane = new JScrollPane(this.table);
    int tableHeight = TEXTFIELD_HEIGHT * Math.min(this.tableModel.getRowCount(), 20);
    int tableWidth = TEXTFIELD_WIDTH * this.tableModel.getColumnCount();
    this.table.setPreferredScrollableViewportSize(new Dimension(tableWidth, tableHeight));
    this.mainPanel.add(scrollPane, BorderLayout.CENTER);
    addNetClassComboBox();

    this.add(mainPanel);
    this.pack();
  }

  private void addNetClassComboBox() {
    this.netRuleComboBox = new JComboBox<>();
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
    for (int i = 0; i < routingBoard.rules.netClasses.count(); i++) {
      netRuleComboBox.addItem(routingBoard.rules.netClasses.get(i));
    }
    this.table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(netRuleComboBox));
  }

  @Override
  public void refresh() {
    // Reinsert the net class column.
    for (int i = 0; i < tableModel.getRowCount(); i++) {
      tableModel.setValueAt(((Net) tableModel.getValueAt(i, 0)).getNetClass(), i, 1);
    }

    // Reinsert the net rule combobox because a rule may have  been added or deleted.
    addNetClassComboBox();
  }

  private class AssignRuleTable extends JTable {

    private final String[] columnToolTips = {
      tm.getText("net_name_tooltip"), tm.getText("class_name_tooltip")
    };

    public AssignRuleTable(AssignRuleTableModel pTableModel) {
      super(pTableModel);
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
          return columnToolTips[realIndex];
        }
      };
    }
  }

  /** Table model of the net rule table. */
  private class AssignRuleTableModel extends AbstractTableModel {

    private final Object[][] data;
    private final String[] columnNames;

    public AssignRuleTableModel() {
      columnNames = new String[2];

      columnNames[0] = tm.getText("netName");
      columnNames[1] = tm.getText("className");

      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      data = new Object[boardRules.nets.maxNetNo()][];
      for (int i = 0; i < data.length; i++) {
        this.data[i] = new Object[columnNames.length];
      }
      setValues();
    }

    /** Calculates the values in this table */
    public void setValues() {
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      Net[] sortedArr = new Net[this.getRowCount()];
      for (int i = 0; i < sortedArr.length; i++) {
        sortedArr[i] = boardRules.nets.get(i + 1);
      }
      Arrays.sort(sortedArr);
      for (int i = 0; i < data.length; i++) {
        this.data[i][0] = sortedArr[i];
        this.data[i][1] = sortedArr[i].getNetClass();
      }
    }

    @Override
    public String getColumnName(int pCol) {
      return columnNames[pCol];
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
    public Object getValueAt(int pRow, int pCol) {
      return data[pRow][pCol];
    }

    @Override
    public boolean isCellEditable(int pRow, int pCol) {
      return pCol > 0;
    }

    @Override
    public void setValueAt(Object pValue, int pRow, int pCol) {
      if (pCol != 1 || !(pValue instanceof NetClass curr_net_rule)) {
        return;
      }
      Object firstRowObject = getValueAt(pRow, 0);
      if (!(firstRowObject instanceof Net currentNet)) {
        FRLogger.warn("AssignNetRuLesVindow.setValueAt: Net expected");
        return;
      }
      currentNet.setClass(curr_net_rule);

      this.data[pRow][pCol] = pValue;
      fireTableCellUpdated(pRow, pCol);
    }
  }
}
