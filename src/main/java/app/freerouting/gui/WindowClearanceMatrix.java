package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.ClearanceMatrix;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

/** Window for interactive editing of the clearance Matrix. */
public class WindowClearanceMatrix extends BoardSavableSubWindow {

  /** Characters, which are not allowed in the name of a clearance class. */
  private static final String[] reserved_name_chars = {"(", ")", " ", "_"};

  private final BoardFrame boardFrame;
  private final JPanel mainPanel;
  private final ComboBoxLayer rulesClearanceLayerComboBox;
  private JPanel centerPanel;
  private JTable clearanceTable;
  private ClearanceTableModel clearanceTableModel;

  /** Creates a new instance of ClearanceMatrixWindow */
  public WindowClearanceMatrix(BoardFrame p_board_frame) {
    this.boardFrame = p_board_frame;
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("title"));

    this.mainPanel = new JPanel();
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    mainPanel.setLayout(new BorderLayout());

    // Add the layer combo box.

    final JPanel northPanel = new JPanel();
    northPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
    JLabel layerLabel = new JLabel(tm.getText("layer"));
    layerLabel.setToolTipText(tm.getText("layer_tooltip"));
    northPanel.add(layerLabel);

    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    rulesClearanceLayerComboBox =
        new ComboBoxLayer(
            boardHandling.get_routing_board().layerStructure, p_board_frame.get_locale());
    northPanel.add(this.rulesClearanceLayerComboBox);
    rulesClearanceLayerComboBox.addActionListener(new ComboBoxListener());
    rulesClearanceLayerComboBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesClearanceLayerComboBox",
                rulesClearanceLayerComboBox.getSelectedItem().toString()));

    mainPanel.add(northPanel, BorderLayout.NORTH);

    // Add the clearance table.

    this.centerPanel = add_clearance_table(p_board_frame);

    mainPanel.add(centerPanel, BorderLayout.CENTER);

    // Add panel with buttons.

    JPanel southPanel = new JPanel();
    southPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    southPanel.setLayout(new BorderLayout());
    this.add(southPanel);

    final JButton rulesClearanceAddClassButton = new JButton(tm.getText("add_class"));
    rulesClearanceAddClassButton.setToolTipText(tm.getText("add_class_tooltip"));
    rulesClearanceAddClassButton.addActionListener(new AddClassListener());
    rulesClearanceAddClassButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesClearanceAddClassButton", rulesClearanceAddClassButton.getText()));
    southPanel.add(rulesClearanceAddClassButton, BorderLayout.WEST);

    final JButton rulesClearancePruneButton = new JButton(tm.getText("prune"));
    rulesClearancePruneButton.setToolTipText(tm.getText("prune_tooltip"));
    rulesClearancePruneButton.addActionListener(new PruneListener());
    rulesClearancePruneButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesClearancePruneButton", rulesClearancePruneButton.getText()));
    southPanel.add(rulesClearancePruneButton, BorderLayout.EAST);

    mainPanel.add(southPanel, BorderLayout.SOUTH);

    this.add(mainPanel);
    this.pack();
  }

  /** Recalculates all displayed values */
  @Override
  public void refresh() {
    BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.get_routing_board();
    if (this.clearanceTableModel.getRowCount()
        != routingBoard.rules.clearanceMatrix.get_class_count()) {
      this.adjust_clearance_table();
    }
    this.clearanceTableModel.set_values(
        this.rulesClearanceLayerComboBox.get_selected_layer().index);
    this.repaint();
  }

  private JPanel add_clearance_table(BoardFrame p_board_frame) {
    this.clearanceTableModel = new ClearanceTableModel(p_board_frame.boardPanel.boardHandling);
    this.clearanceTable = new JTable(clearanceTableModel);

    // Put the clearance table into a scroll pane.
    final int textfieldHeight = 16;
    final int textfieldWidth = Math.max(6 * max_name_length(), 100);
    int tableHeight = textfieldHeight * (this.clearanceTableModel.getRowCount());
    int tableWidth = textfieldWidth * this.clearanceTableModel.getColumnCount();
    this.clearanceTable.setPreferredSize(new Dimension(tableWidth, tableHeight));
    this.clearanceTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    // Put a panel around the table and the header before putting the table into the scroll pane,
    // because otherwise there seems to be a redisplay bug in horizontal scrolling.
    JPanel scrollPanel = new JPanel();
    scrollPanel.setLayout(new BorderLayout());
    scrollPanel.add(this.clearanceTable.getTableHeader(), BorderLayout.NORTH);
    scrollPanel.add(this.clearanceTable, BorderLayout.CENTER);
    JScrollPane scrollPane =
        new JScrollPane(
            scrollPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    final int scrollBarWidth = 20;
    final int scrollPaneHeight =
        textfieldHeight * this.clearanceTableModel.getRowCount() + scrollBarWidth;
    final int scrollPaneWidth = Math.min(tableWidth + scrollBarWidth, 1200);
    scrollPane.setPreferredSize(new Dimension(scrollPaneWidth, scrollPaneHeight));
    // Change the background color of the header and the first column of the table.
    Color headerBackgroundColor = new Color(220, 220, 255);
    JTableHeader tableHeader = this.clearanceTable.getTableHeader();
    tableHeader.setBackground(headerBackgroundColor);

    TableColumn firstColumn = this.clearanceTable.getColumnModel().getColumn(0);
    DefaultTableCellRenderer firstColumnRenderer = new DefaultTableCellRenderer();
    firstColumnRenderer.setBackground(headerBackgroundColor);
    firstColumn.setCellRenderer(firstColumnRenderer);

    final JPanel result = new JPanel();
    result.setLayout(new BorderLayout());

    result.add(scrollPane, BorderLayout.CENTER);

    // add message for german localisation bug
    if ("de".equalsIgnoreCase(p_board_frame.get_locale().getLanguage())) {
      // Due to a Java system bug, the decimal comma in this table must be entered as a dot.
      JLabel bugLabel =
          new JLabel(
              "Wegen eines Java-System-Bugs muss das Dezimalkomma in dieser Tabelle als Punkt eingegeben werden!");
      result.add(bugLabel, BorderLayout.SOUTH);
    }
    return result;
  }

  /** Adds a new class to the clearance matrix. */
  private void add_class() {
    String newName;
    // Ask for the name of the new class.
    do {
      newName = JOptionPane.showInputDialog(tm.getText("newName"));
      if (newName == null) {
        return;
      }
      newName = newName.trim();
    } while (!is_legal_class_name(newName));

    final BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.get_routing_board();
    final ClearanceMatrix clearanceMatrix = routingBoard.rules.clearanceMatrix;

    // Check, if the name exists already.
    boolean nameExists = false;
    for (int i = 0; i < clearanceMatrix.get_class_count(); i++) {
      if (newName.equals(clearanceMatrix.get_name(i))) {
        nameExists = true;
        break;
      }
    }
    if (nameExists) {
      return;
    }
    clearanceMatrix.append_class(newName);

    // clearance compensation is only used, if there are only the clearance classes "default" and
    // "null".
    routingBoard.searchTreeManager.set_clearance_compensation_used(false);

    adjust_clearance_table();
  }

  /** Removes clearance class, whose clearance values are all equal to a previous class. */
  private void prune_clearance_matrix() {
    final BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.get_routing_board();
    ClearanceMatrix clearanceMatrix = routingBoard.rules.clearanceMatrix;
    for (int i = clearanceMatrix.get_class_count() - 1; i >= 2; i--) {
      for (int j = clearanceMatrix.get_class_count() - 1; j >= 0; j--) {
        if (i == j) {
          continue;
        }
        if (clearanceMatrix.is_equal(i, j)) {
          String message = tm.getText("confirm_remove_class_message", clearanceMatrix.get_name(i));
          int removeClearanceClassDialog =
              JOptionPane.showConfirmDialog(this, message, null, JOptionPane.YES_NO_OPTION);
          if (removeClearanceClassDialog == JOptionPane.YES_OPTION) {
            Collection<Item> boardItems = routingBoard.get_items();
            routingBoard.rules.change_clearance_class_no(i, j, boardItems);
            if (!routingBoard.rules.remove_clearance_class(i, boardItems)) {
              FRLogger.warn(
                  "WindowClearanceMatrix.prune_clearance_matrix error removing clearance class");
              return;
            }
            routingBoard.searchTreeManager.clearance_class_removed(i);
            adjust_clearance_table();
          }
          break;
        }
      }
    }
  }

  /**
   * Adjusts the displayed window with the clearance table after the size of the clearance matrix
   * has changed.
   */
  private void adjust_clearance_table() {
    this.clearanceTableModel = new ClearanceTableModel(this.boardFrame.boardPanel.boardHandling);
    this.clearanceTable = new JTable(clearanceTableModel);
    this.mainPanel.remove(this.centerPanel);
    this.centerPanel = add_clearance_table(this.boardFrame);
    this.mainPanel.add(this.centerPanel, BorderLayout.CENTER);
    this.pack();
    this.boardFrame.refresh_windows();
  }

  static boolean isLegalClassName(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    for (String reservedNameChar : reserved_name_chars) {
      if (value.contains(reservedNameChar)) {
        return false;
      }
    }
    return true;
  }

  static Float parseClearanceTableValue(Object value) {
    if (value instanceof Number number) {
      return number.floatValue();
    }
    if (value instanceof String stringValue) {
      try {
        return Float.parseFloat(stringValue);
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  static void applyClearanceValue(
      ClearanceMatrix matrix, int rowClassNo, int columnClassNo, int layerNo, int boardValue) {
    if (layerNo == ComboBoxLayer.ALL_LAYER_INDEX) {
      matrix.set_value(rowClassNo, columnClassNo, boardValue);
      matrix.set_value(columnClassNo, rowClassNo, boardValue);
    } else if (layerNo == ComboBoxLayer.INNER_LAYER_INDEX) {
      matrix.set_inner_value(rowClassNo, columnClassNo, boardValue);
      matrix.set_inner_value(columnClassNo, rowClassNo, boardValue);
    } else {
      matrix.set_value(rowClassNo, columnClassNo, layerNo, boardValue);
      matrix.set_value(columnClassNo, rowClassNo, layerNo, boardValue);
    }
  }

  /** Returns true, if p_string is a legal class name. */
  private boolean is_legal_class_name(String p_string) {
    return isLegalClassName(p_string);
  }

  private int max_name_length() {
    int result = 1;
    ClearanceMatrix clearanceMatrix =
        boardFrame.boardPanel.boardHandling.get_routing_board().rules.clearanceMatrix;
    for (int i = 0; i < clearanceMatrix.get_class_count(); i++) {
      result = Math.max(result, clearanceMatrix.get_name(i).length());
    }
    return result;
  }

  private class ComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      refresh();
    }
  }

  private class AddClassListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      add_class();
    }
  }

  private class PruneListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      prune_clearance_matrix();
    }
  }

  /** Table model of the clearance matrix. */
  private class ClearanceTableModel extends AbstractTableModel implements Serializable {

    private final Object[][] data;
    private final String[] columnNames;

    public ClearanceTableModel(GuiBoardManager p_board_handling) {
      ClearanceMatrix clearanceMatrix = p_board_handling.get_routing_board().rules.clearanceMatrix;

      columnNames = new String[clearanceMatrix.get_class_count() + 1];
      columnNames[0] = tm.getText("class");

      data = new Object[clearanceMatrix.get_class_count()][];
      for (int i = 0; i < clearanceMatrix.get_class_count(); i++) {
        this.columnNames[i + 1] = clearanceMatrix.get_name(i);
        this.data[i] = new Object[clearanceMatrix.get_class_count() + 1];
        this.data[i][0] = clearanceMatrix.get_name(i);
      }
      this.set_values(0);
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
      Float parsedValue = parseClearanceTableValue(p_value);
      if (parsedValue == null) {
        return;
      }
      Number numberValue = parsedValue;
      int currRow = p_row;
      int currColumn = p_col - 1;

      // check, if there are items on the board assigned to clearance class i or j.

      GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
      UndoableObjects itemList = boardHandling.get_routing_board().itemList;
      boolean itemsAlreadyAssignedRow = false;
      boolean itemsAlreadyAssignedColumn = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = itemList.start_read_object();
      for (; ; ) {
        Item currItem = (Item) itemList.read_object(it);
        if (currItem == null) {
          break;
        }
        int currItemClassNo = currItem.clearance_class_no();
        if (currItemClassNo == currRow) {
          itemsAlreadyAssignedRow = true;
        }
        if (currItemClassNo == currColumn) {
          itemsAlreadyAssignedColumn = true;
        }
      }
      ClearanceMatrix clearanceMatrix = boardHandling.get_routing_board().rules.clearanceMatrix;
      boolean itemsAlreadyAssigned = itemsAlreadyAssignedRow && itemsAlreadyAssignedColumn;
      if (itemsAlreadyAssigned) {
        String message;
        if (currRow == currColumn) {
          message =
              tm.getText(
                  "clearance_class_already_assigned_single", clearanceMatrix.get_name(currRow));
        } else {
          message =
              tm.getText(
                  "clearance_class_already_assigned_pair",
                  clearanceMatrix.get_name(currRow),
                  clearanceMatrix.get_name(currColumn));
        }
        int clearanceClassAlreadyAssignedDialog =
            JOptionPane.showConfirmDialog(
                boardFrame.clearanceMatrixWindow, message, null, JOptionPane.YES_NO_OPTION);
        if (clearanceClassAlreadyAssignedDialog != JOptionPane.YES_OPTION) {
          return;
        }
      }

      this.data[p_row][p_col] = numberValue;
      this.data[p_col - 1][p_row + 1] = numberValue;
      fireTableCellUpdated(p_row, p_col);
      fireTableCellUpdated(p_col - 1, p_row + 1);

      int boardValue =
          (int)
              Math.round(
                  boardHandling.coordinateTransform.user_to_board(numberValue.doubleValue()));
      int layerNo = rulesClearanceLayerComboBox.get_selected_layer().index;
      applyClearanceValue(clearanceMatrix, currRow, currColumn, layerNo, boardValue);
      if (itemsAlreadyAssigned) {
        // force reinserting all item into the searck tree, because their tree shapes may have
        // changed
        boardHandling.get_routing_board().searchTreeManager.clearance_value_changed();
      }
    }

    @Override
    public boolean isCellEditable(int p_row, int p_col) {
      return p_row > 0 && p_col > 1;
    }

    @Override
    public Class<?> getColumnClass(int p_col) {
      if (p_col == 0) {
        return String.class;
      }
      return Float.class;
    }

    /**
     * Sets the values of this clearance table to the values of the clearance matrix on the input
     * layer.
     */
    private void set_values(int p_layer) {
      GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
      ClearanceMatrix clearanceMatrix = boardHandling.get_routing_board().rules.clearanceMatrix;

      for (int i = 0; i < clearanceMatrix.get_class_count(); i++) {
        for (int j = 0; j < clearanceMatrix.get_class_count(); j++) {
          if (p_layer == ComboBoxLayer.ALL_LAYER_INDEX) {
            // all layers

            if (clearanceMatrix.is_layer_dependent(i, j)) {
              this.data[i][j + 1] = -1;
            } else {
              float currTableValue =
                  (float)
                      boardHandling.coordinateTransform.board_to_user(
                          clearanceMatrix.get_value(i, j, 0, false));
              this.data[i][j + 1] = currTableValue;
            }
          } else if (p_layer == ComboBoxLayer.INNER_LAYER_INDEX) {
            // all layers

            if (clearanceMatrix.is_inner_layer_dependent(i, j)) {
              this.data[i][j + 1] = -1;
            } else {
              float currTableValue =
                  (float)
                      boardHandling.coordinateTransform.board_to_user(
                          clearanceMatrix.get_value(i, j, 1, false));
              this.data[i][j + 1] = currTableValue;
            }
          } else {
            float currTableValue =
                (float)
                    boardHandling.coordinateTransform.board_to_user(
                        clearanceMatrix.get_value(i, j, p_layer, false));
            this.data[i][j + 1] = currTableValue;
          }
        }
      }
    }
  }
}
