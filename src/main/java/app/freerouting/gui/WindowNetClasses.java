package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel.Printable;
import app.freerouting.board.RoutingBoard;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.Nets;
import app.freerouting.rules.ViaRule;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;

/** Edit window for the table of net rules. */
public class WindowNetClasses extends BoardSavableSubWindow {

  private static final int TEXTFIELD_HEIGHT = 16;
  private static final int TEXTFIELD_WIDTH = 100;
  private static final int WINDOW_OFFSET = 30;
  private final BoardFrame boardFrame;
  private final JPanel mainPanel;

  /** The subwindows created inside this window */
  private final Collection<JFrame> subwindows = new LinkedList<>();

  private final JComboBox<String> clClassComboBox;
  private final JComboBox<String> viaRuleComboBox;
  private JPanel centerPanel;
  private NetClassTable table;
  private NetClassTableModel tableModel;

  /** Creates a new instance of NetClassesWindow */
  public WindowNetClasses(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("title"));

    this.boardFrame = p_board_frame;

    this.mainPanel = new JPanel();
    this.mainPanel.setLayout(new BorderLayout());

    BasicBoard routingBoard = p_board_frame.boardPanel.boardHandling.get_routing_board();

    this.clClassComboBox = new JComboBox<>();
    this.viaRuleComboBox = new JComboBox<>();
    add_combobox_items();

    add_table();

    JPanel netClassButtonPanel = new JPanel();
    netClassButtonPanel.setLayout(new FlowLayout());
    this.mainPanel.add(netClassButtonPanel, BorderLayout.SOUTH);

    final JButton rulesNetclassesAddClassButton = new JButton(tm.getText("add"));
    rulesNetclassesAddClassButton.setToolTipText(tm.getText("add_tooltip"));
    rulesNetclassesAddClassButton.addActionListener(new AddNetClassListener());
    rulesNetclassesAddClassButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetclassesAddClassButton", rulesNetclassesAddClassButton.getText()));
    netClassButtonPanel.add(rulesNetclassesAddClassButton);

    final JButton rulesNetclassesRemoveClassButton = new JButton(tm.getText("remove"));
    rulesNetclassesRemoveClassButton.setToolTipText(tm.getText("remove_tooltip"));
    rulesNetclassesRemoveClassButton.addActionListener(new RemoveNetClassListener());
    rulesNetclassesRemoveClassButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetclassesRemoveClassButton", rulesNetclassesRemoveClassButton.getText()));
    netClassButtonPanel.add(rulesNetclassesRemoveClassButton);

    final JButton rulesNetclassesAssignButton = new JButton(tm.getText("assign"));
    rulesNetclassesAssignButton.setToolTipText(tm.getText("assign_tooltip"));
    rulesNetclassesAssignButton.addActionListener(new AssignClassesListener());
    rulesNetclassesAssignButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetclassesAssignButton", rulesNetclassesAssignButton.getText()));
    netClassButtonPanel.add(rulesNetclassesAssignButton);

    final JButton rulesNetclassesSelectButton = new JButton(tm.getText("select"));
    rulesNetclassesSelectButton.setToolTipText(tm.getText("select_tooltip"));
    rulesNetclassesSelectButton.addActionListener(new SelectClassesListener());
    rulesNetclassesSelectButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetclassesSelectButton", rulesNetclassesSelectButton.getText()));
    netClassButtonPanel.add(rulesNetclassesSelectButton);

    final JButton rulesNetclassesContainedNetsButton = new JButton(tm.getText("show_nets"));
    netClassButtonPanel.add(rulesNetclassesContainedNetsButton);
    rulesNetclassesContainedNetsButton.setToolTipText(tm.getText("show_nets_tooltip"));
    rulesNetclassesContainedNetsButton.addActionListener(new ContainedNetsListener());
    rulesNetclassesContainedNetsButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetclassesContainedNetsButton",
                rulesNetclassesContainedNetsButton.getText()));

    final JButton rulesNetclassesFilterIncompletesButton =
        new JButton(tm.getText("filter_incompletes"));
    netClassButtonPanel.add(rulesNetclassesFilterIncompletesButton);
    rulesNetclassesFilterIncompletesButton.setToolTipText(tm.getText("filter_incompletes_tooltip"));
    rulesNetclassesFilterIncompletesButton.addActionListener(new FilterIncompletesListener());
    rulesNetclassesFilterIncompletesButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetclassesFilterIncompletesButton",
                rulesNetclassesFilterIncompletesButton.getText()));

    this.add(mainPanel);
    this.pack();
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  }

  static boolean canRemoveNetClass(int rowCount, int selectedRow) {
    return rowCount > 1 && selectedRow >= 0;
  }

  static void applyShoveFixedSelection(NetClass netClass, boolean shoveFixed) {
    netClass.set_shove_fixed(shoveFixed);
    netClass.set_pull_tight(!shoveFixed);
  }

  static void applyAutorouterIgnoreSelection(NetClass netClass, boolean ignoredByAutorouter) {
    netClass.isIgnoredByAutorouter = ignoredByAutorouter;
  }

  @Override
  public void refresh() {
    this.clClassComboBox.removeAllItems();
    this.viaRuleComboBox.removeAllItems();
    add_combobox_items();
    this.tableModel.set_values();
    int tableHeight = TEXTFIELD_HEIGHT * this.tableModel.getRowCount();
    int tableWidth = TEXTFIELD_WIDTH * this.tableModel.getColumnCount();
    this.table.setPreferredScrollableViewportSize(new Dimension(tableWidth, tableHeight));
    // reinsert the scroll to display the correct table size if the table size has changed.
    this.mainPanel.remove(this.centerPanel);
    this.mainPanel.add(centerPanel, BorderLayout.CENTER);
    this.pack();

    // Dispose all subwindows because they may be no longer up-to-date.
    Iterator<JFrame> it = this.subwindows.iterator();
    while (it.hasNext()) {
      JFrame currSubwindow = it.next();
      if (currSubwindow != null) {

        currSubwindow.dispose();
      }
      it.remove();
    }
  }

  @Override
  public void dispose() {
    for (JFrame currSubwindow : this.subwindows) {
      if (currSubwindow != null) {
        currSubwindow.dispose();
      }
    }
    super.dispose();
  }

  private void add_table() {
    this.tableModel = new NetClassTableModel();
    this.table = new NetClassTable(this.tableModel);
    JScrollPane scrollPane = new JScrollPane(this.table);
    int tableHeight = TEXTFIELD_HEIGHT * this.tableModel.getRowCount();
    int tableWidth = TEXTFIELD_WIDTH * this.tableModel.getColumnCount();
    this.table.setPreferredScrollableViewportSize(new Dimension(tableWidth, tableHeight));
    this.centerPanel = new JPanel();
    this.centerPanel.setLayout(new BorderLayout());

    this.centerPanel.add(scrollPane, BorderLayout.CENTER);

    // add message for german localisation bug
    if ("de".equalsIgnoreCase(boardFrame.get_locale().getLanguage())) {
      // Due to a Java system bug, the decimal comma in this table must be entered as a dot.
      JLabel bugLabel =
          new JLabel(
              "Wegen eines Java-System-Bugs muss das Dezimalkomma in dieser Tabelle zur Zeit als Punkt eingegeben werden!");
      this.centerPanel.add(bugLabel, BorderLayout.SOUTH);
    }
    this.mainPanel.add(centerPanel, BorderLayout.CENTER);

    this.table
        .getColumnModel()
        .getColumn(ColumnName.CLEARANCE_CLASS.ordinal())
        .setCellEditor(new DefaultCellEditor(clClassComboBox));

    this.table
        .getColumnModel()
        .getColumn(ColumnName.VIA_RULE.ordinal())
        .setCellEditor(new DefaultCellEditor(viaRuleComboBox));

    LayerRulesCellEditor layerEditor = new LayerRulesCellEditor();
    this.table.getColumnModel().getColumn(ColumnName.ON_LAYER.ordinal()).setCellEditor(layerEditor);
  }

  private void add_combobox_items() {
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
    for (int i = 0; i < routingBoard.rules.clearanceMatrix.get_class_count(); i++) {
      clClassComboBox.addItem(routingBoard.rules.clearanceMatrix.get_name(i));
    }
    for (ViaRule currRule : routingBoard.rules.viaRules) {
      viaRuleComboBox.addItem(currRule.name);
    }
  }

  /**
   * Adjusts the displayed window with the net class table after the size of the table has been
   * changed.
   */
  private void adjust_table() {
    this.tableModel = new NetClassTableModel();
    this.table = new NetClassTable(this.tableModel);
    this.mainPanel.remove(this.centerPanel);
    this.add_table();
    this.pack();
    this.boardFrame.refresh_windows();
  }

  private String getLayerSummary(NetClass p_net_class) {
    RoutingBoard board = boardFrame.boardPanel.boardHandling.get_routing_board();
    LayerStructure ls = board.layerStructure;
    List<Integer> activeSignalLayers = new ArrayList<>();
    List<Integer> allSignalLayers = new ArrayList<>();
    List<String> activeLayerNames = new ArrayList<>();

    for (int i = 0; i < ls.arr.length; i++) {
      if (ls.arr[i].isSignal) {
        allSignalLayers.add(i);
        if (p_net_class.is_active_routing_layer(i)) {
          activeSignalLayers.add(i);
          activeLayerNames.add(ls.arr[i].name);
        }
      }
    }

    if (activeSignalLayers.isEmpty()) {
      return tm.getText("layers_none");
    }
    if (activeSignalLayers.size() == allSignalLayers.size()) {
      return tm.getText("layers_all");
    }

    if (activeSignalLayers.size() == 2
        && activeSignalLayers.get(0).equals(allSignalLayers.get(0))
        && activeSignalLayers.get(1).equals(allSignalLayers.get(allSignalLayers.size() - 1))) {
      return tm.getText("layers_outer");
    }

    if (activeSignalLayers.size() == allSignalLayers.size() - 2
        && !activeSignalLayers.contains(allSignalLayers.get(0))
        && !activeSignalLayers.contains(allSignalLayers.get(allSignalLayers.size() - 1))) {
      return tm.getText("layers_inner");
    }

    if (activeLayerNames.size() > 3) {
      return tm.getText("layers_custom", Integer.toString(activeLayerNames.size()));
    } else {
      return String.join(", ", activeLayerNames);
    }
  }

  private String getTraceWidthSummary(NetClass p_net_class) {
    RoutingBoard board = boardFrame.boardPanel.boardHandling.get_routing_board();
    LayerStructure ls = board.layerStructure;
    CoordinateTransform ct = boardFrame.boardPanel.boardHandling.coordinateTransform;
    Integer commonHalfWidth = null;
    boolean multiple = false;

    for (int i = 0; i < ls.arr.length; i++) {
      if (ls.arr[i].isSignal && p_net_class.is_active_routing_layer(i)) {
        int width = p_net_class.get_trace_half_width(i);
        if (commonHalfWidth == null) {
          commonHalfWidth = width;
        } else if (width != commonHalfWidth) {
          multiple = true;
          break;
        }
      }
    }

    if (commonHalfWidth == null) {
      return "0";
    }
    if (multiple) {
      return tm.getText("width_multiple");
    }
    return String.format(Locale.ENGLISH, "%.4f", ct.board_to_user(commonHalfWidth * 2));
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
      boardFrame.boardPanel.boardHandling.get_routing_board().rules.append_net_class();
      adjust_table();
    }
  }

  private class RemoveNetClassListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int selectedRow = table.getSelectedRow();
      if (!canRemoveNetClass(tableModel.getRowCount(), selectedRow)) {
        if (tableModel.getRowCount() <= 1) {
          boardFrame.screenMessages.set_status_message(tm.getText("default_net_class_not_removed"));
        }
        return;
      }
      Object netClassName = tableModel.getValueAt(selectedRow, ColumnName.NAME.ordinal());
      if (!(netClassName instanceof String)) {
        return;
      }
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.get_routing_board().rules;
      NetClass netRule = boardRules.netClasses.get((String) netClassName);
      // Check, if netRule is used in a net of the net list
      for (int i = 1; i < boardRules.nets.max_net_no(); i++) {
        Net currNet = boardRules.nets.get(i);
        if (currNet.get_class() == netRule) {
          boardFrame.screenMessages.set_status_message(
              tm.getText("net_class_not_removed_in_use_message", currNet.name));
          return;
        }
      }
      if (boardRules.netClasses.remove(netRule)) {
        adjust_table();
        boardFrame.screenMessages.set_status_message(
            tm.getText("net_class_removed_message", netRule.get_name()));
      }
    }
  }

  private class AssignClassesListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      boardFrame.assignNetClassesWindow.setVisible(true);
    }
  }

  private class SelectClassesListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int[] selectedRows = table.getSelectedRows();
      if (selectedRows.length == 0) {
        return;
      }
      RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
      NetClass[] selectedClassArr = new NetClass[selectedRows.length];
      for (int i = 0; i < selectedClassArr.length; i++) {
        selectedClassArr[i] =
            routingBoard.rules.netClasses.get(
                (String) table.getValueAt(selectedRows[i], ColumnName.NAME.ordinal()));
      }
      Nets nets = routingBoard.rules.nets;
      Set<Item> selectedItems = new TreeSet<>();
      Collection<Item> boardItems = routingBoard.get_items();
      for (Item currItem : boardItems) {
        boolean itemMatches = false;
        for (int i = 0; i < currItem.net_count(); i++) {
          NetClass currNetClass = nets.get(currItem.get_net_no(i)).get_class();
          if (currNetClass == null) {
            continue;
          }
          for (int j = 0; j < selectedClassArr.length; j++) {
            if (currNetClass == selectedClassArr[i]) {
              itemMatches = true;
              break;
            }
          }
          if (itemMatches) {
            break;
          }
        }
        if (itemMatches) {
          selectedItems.add(currItem);
        }
      }
      boardFrame.boardPanel.boardHandling.select_items(selectedItems);
      boardFrame.boardPanel.boardHandling.zoom_selection();
    }
  }

  private class FilterIncompletesListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int[] selectedRows = table.getSelectedRows();
      if (selectedRows.length == 0) {
        return;
      }
      GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
      BoardRules boardRules = boardHandling.get_routing_board().rules;
      NetClass[] selectedClassArr = new NetClass[selectedRows.length];
      for (int i = 0; i < selectedClassArr.length; i++) {
        selectedClassArr[i] =
            boardRules.netClasses.get(
                (String) table.getValueAt(selectedRows[i], ColumnName.NAME.ordinal()));
      }
      int maxNetNo = boardRules.nets.max_net_no();
      for (int i = 1; i <= maxNetNo; i++) {
        boardHandling.set_incompletes_filter(i, true);
        NetClass currNetClass = boardRules.nets.get(i).get_class();
        for (int j = 0; j < selectedClassArr.length; j++) {
          if (currNetClass == selectedClassArr[j]) {
            boardHandling.set_incompletes_filter(i, false);
            break;
          }
        }
      }
      boardFrame.boardPanel.repaint();
    }
  }

  private class ContainedNetsListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int[] selectedRows = table.getSelectedRows();
      if (selectedRows.length == 0) {
        return;
      }
      GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
      BoardRules boardRules = boardHandling.get_routing_board().rules;
      NetClass[] selectedClassArr = new NetClass[selectedRows.length];
      for (int i = 0; i < selectedClassArr.length; i++) {
        selectedClassArr[i] =
            boardRules.netClasses.get(
                (String) table.getValueAt(selectedRows[i], ColumnName.NAME.ordinal()));
      }
      Collection<Printable> containedNets = new LinkedList<>();
      int maxNetNo = boardRules.nets.max_net_no();
      for (int i = 1; i <= maxNetNo; i++) {
        Net currNet = boardRules.nets.get(i);
        NetClass currNetClass = currNet.get_class();
        for (int j = 0; j < selectedClassArr.length; j++) {
          if (currNetClass == selectedClassArr[j]) {
            containedNets.add(currNet);
            break;
          }
        }
      }
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      WindowObjectInfo newWindow =
          WindowObjectInfo.display(
              tm.getText("containedNets"), containedNets, boardFrame, coordinateTransform);
      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }

  private class NetClassTable extends JTable {

    private final String[] columnToolTips;

    public NetClassTable(NetClassTableModel p_table_model) {
      super(p_table_model);
      columnToolTips = new String[10];
      columnToolTips[0] = null;
      columnToolTips[1] = tm.getText("column_tool_tip_1");
      columnToolTips[2] = tm.getText("column_tool_tip_2");
      columnToolTips[3] = tm.getText("column_tool_tip_3");
      columnToolTips[4] = tm.getText("column_tool_tip_4");
      columnToolTips[5] = tm.getText("column_tool_tip_5");
      columnToolTips[6] = tm.getText("column_tool_tip_6");
      columnToolTips[7] = tm.getText("column_tool_tip_7");
      columnToolTips[8] = tm.getText("column_tool_tip_8");
      columnToolTips[9] = tm.getText("column_tool_tip_9");
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
  private class NetClassTableModel extends AbstractTableModel {

    private final String[] columnNames;
    private Object[][] data;

    public NetClassTableModel() {
      columnNames = new String[ColumnName.values().length];

      for (int i = 0; i < columnNames.length; i++) {
        columnNames[i] = tm.getText(ColumnName.values()[i].toString());
      }
      set_values();
    }

    /** Calculates the values in this table */
    public void set_values() {
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.get_routing_board().rules;
      this.data = new Object[boardRules.netClasses.count()][];
      for (int i = 0; i < data.length; i++) {
        this.data[i] = new Object[ColumnName.values().length];
      }
      for (int i = 0; i < data.length; i++) {
        NetClass currNetClass = boardRules.netClasses.get(i);
        this.data[i][ColumnName.NAME.ordinal()] = currNetClass.get_name();
        if (currNetClass.get_via_rule() != null) {
          this.data[i][ColumnName.VIA_RULE.ordinal()] = currNetClass.get_via_rule().name;
        }
        this.data[i][ColumnName.SHOVE_FIXED.ordinal()] =
            currNetClass.is_shove_fixed() || !currNetClass.get_pull_tight();
        this.data[i][ColumnName.CYCLES_WITH_AREAS.ordinal()] =
            currNetClass.get_ignore_cycles_with_areas();
        double minTraceLength =
            boardFrame.boardPanel.boardHandling.coordinateTransform.board_to_user(
                currNetClass.get_minimum_trace_length());
        if (minTraceLength <= 0) {
          minTraceLength = 0;
        }
        this.data[i][ColumnName.MIN_TRACE_LENGTH.ordinal()] = (float) minTraceLength;
        double maxTraceLength =
            boardFrame.boardPanel.boardHandling.coordinateTransform.board_to_user(
                currNetClass.get_maximum_trace_length());
        if (maxTraceLength <= 0) {
          maxTraceLength = -1;
        }
        this.data[i][ColumnName.MAX_TRACE_LENGTH.ordinal()] = (float) maxTraceLength;
        this.data[i][ColumnName.IGNORED_BY_AUTOROUTER.ordinal()] =
            currNetClass.isIgnoredByAutorouter;
        this.data[i][ColumnName.CLEARANCE_CLASS.ordinal()] =
            boardRules.clearanceMatrix.get_name(currNetClass.get_trace_clearance_class());
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
      NetClass currNetClass =
          boardFrame.boardPanel.boardHandling.get_routing_board().rules.netClasses.get(p_row);
      if (p_col == ColumnName.ON_LAYER.ordinal()) {
        return getLayerSummary(currNetClass);
      }
      if (p_col == ColumnName.TRACE_WIDTH.ordinal()) {
        return getTraceWidthSummary(currNetClass);
      }
      return data[p_row][p_col];
    }

    @Override
    public void setValueAt(Object p_value, int p_row, int p_col) {
      RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
      BoardRules boardRules = routingBoard.rules;
      if (p_col == ColumnName.ON_LAYER.ordinal() || p_col == ColumnName.TRACE_WIDTH.ordinal()) {
        return;
      }
      Object netClassName = getValueAt(p_row, ColumnName.NAME.ordinal());
      if (!(netClassName instanceof String)) {
        FRLogger.warn("EditNetRuLesVindow.setValueAt: String expected");
        return;
      }
      NetClass netRule = boardRules.netClasses.get((String) netClassName);
      if (netRule == null) {
        FRLogger.warn("EditNetRuLesVindow.setValueAt: netRule not found");
        return;
      }

      if (p_col == ColumnName.NAME.ordinal()) {
        if (!(p_value instanceof String newName)) {
          return;
        }
        if (boardRules.netClasses.get(newName) != null) {
          return; // name exists already
        }
        netRule.set_name(newName);
        boardFrame.viaWindow.refresh();
      } else if (p_col == ColumnName.VIA_RULE.ordinal()) {
        if (!(p_value instanceof String newName)) {
          return;
        }
        ViaRule newViaRule = boardRules.get_via_rule(newName);
        if (newViaRule == null) {
          FRLogger.warn("EditNetRuLesVindow.setValueAt: viaRule not found");
          return;
        }
        netRule.set_via_rule(newViaRule);
      } else if (p_col == ColumnName.SHOVE_FIXED.ordinal()) {
        if (!(p_value instanceof Boolean)) {
          return;
        }
        applyShoveFixedSelection(netRule, (Boolean) p_value);
      } else if (p_col == ColumnName.CYCLES_WITH_AREAS.ordinal()) {
        if (!(p_value instanceof Boolean)) {
          return;
        }
        boolean value = (Boolean) p_value;
        netRule.set_ignore_cycles_with_areas(value);
      } else if (p_col == ColumnName.MIN_TRACE_LENGTH.ordinal()) {

        float currValue = 0F;
        if (p_value instanceof Float float1) {
          currValue = float1;
        } else if (p_value instanceof String string) {
          // Workaround because of a localisation Bug in Java
          // The numbers are always displayed in the English Format.

          try {
            currValue = Float.parseFloat(string);
          } catch (Exception _) {
            currValue = 0f;
          }
          p_value = String.valueOf(currValue);
        }
        if (currValue <= 0) {
          currValue = (float) 0;
          p_value = currValue;
        }
        double minTraceLength =
            Math.round(
                boardFrame.boardPanel.boardHandling.coordinateTransform.user_to_board(currValue));
        netRule.set_minimum_trace_length(minTraceLength);
        boardFrame.boardPanel.boardHandling.recalculate_length_violations();
      } else if (p_col == ColumnName.MAX_TRACE_LENGTH.ordinal()) {
        float currValue = 0F;
        if (p_value instanceof Float float1) {
          currValue = float1;
        } else if (p_value instanceof String string) {
          // Workaround because of a localisation Bug in Java
          // The numbers are always displayed in the English Format.

          try {
            currValue = Float.parseFloat(string);
          } catch (Exception _) {
            currValue = 0f;
          }
          p_value = String.valueOf(currValue);
        }
        if (currValue <= 0) {
          currValue = (float) 0;
          p_value = currValue - 1;
        }

        double maxTraceLength =
            Math.round(
                boardFrame.boardPanel.boardHandling.coordinateTransform.user_to_board(currValue));
        netRule.set_maximum_trace_length(maxTraceLength);
        boardFrame.boardPanel.boardHandling.recalculate_length_violations();
      } else if (p_col == ColumnName.IGNORED_BY_AUTOROUTER.ordinal()) {
        if (!(p_value instanceof Boolean)) {
          return;
        }
        applyAutorouterIgnoreSelection(netRule, (Boolean) p_value);
      } else if (p_col == ColumnName.CLEARANCE_CLASS.ordinal()) {
        if (!(p_value instanceof String newName)) {
          return;
        }
        int newClClassIndex = boardRules.clearanceMatrix.get_no(newName);
        {
          if (newClClassIndex < 0) {
            FRLogger.warn("EditNetRuLesVindow.setValueAt: clearance class not found");
            return;
          }
        }
        netRule.set_trace_clearance_class(newClClassIndex);
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
      if (p_col == ColumnName.ON_LAYER.ordinal() || p_col == ColumnName.TRACE_WIDTH.ordinal()) {
        return String.class;
      }
      if (getRowCount() == 0) {
        return Object.class;
      }
      Object currEntry = getValueAt(0, p_col);
      if (currEntry == null) {
        return Object.class;
      }
      Class<?> currClass = currEntry.getClass();
      // changed because of a localisation bug in Java
      if (currEntry instanceof Float) {
        currClass = String.class;
      }
      return currClass;
    }
  }

  private class LayerRulesDialog extends JDialog {
    private final JCheckBox[] checkboxes;
    private final NetClass netClass;
    private final GuiBoardManager boardHandling;
    private final List<Integer> allSignalLayers = new ArrayList<>();

    public LayerRulesDialog(
        JFrame owner,
        NetClass p_netClass,
        GuiBoardManager p_boardHandling,
        app.freerouting.util.TextManager p_tm) {
      super(owner, p_tm.getText("dialog_layer_rules_title"), true);
      this.netClass = p_netClass;
      this.boardHandling = p_boardHandling;
      LayerStructure ls = boardHandling.get_routing_board().layerStructure;

      setLayout(new BorderLayout(10, 10));
      ((javax.swing.JComponent) getContentPane())
          .setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15));

      JPanel macroPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
      JButton btnAll = new JButton(p_tm.getText("btn_all"));
      JButton btnOuter = new JButton(p_tm.getText("btn_outer"));
      JButton btnInner = new JButton(p_tm.getText("btn_inner"));
      JButton btnClear = new JButton(p_tm.getText("btn_clear"));
      macroPanel.add(btnAll);
      macroPanel.add(btnOuter);
      macroPanel.add(btnInner);
      macroPanel.add(btnClear);

      JPanel checkGridPanel = new JPanel(new GridLayout(0, 2, 15, 8));
      checkGridPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
      checkboxes = new JCheckBox[ls.arr.length];

      for (int i = 0; i < ls.arr.length; i++) {
        if (ls.arr[i].isSignal) {
          allSignalLayers.add(i);
          checkboxes[i] = new JCheckBox(ls.arr[i].name);
          if (netClass.is_active_routing_layer(i)) {
            checkboxes[i].setSelected(true);
          }
          checkGridPanel.add(checkboxes[i]);
        }
      }

      int rows = (int) Math.ceil(allSignalLayers.size() / 2.0);
      int dynamicHeight = Math.min(300, Math.max(80, rows * 35 + 20));

      btnAll.addActionListener(
          _ -> {
            for (int idx : allSignalLayers) {
              checkboxes[idx].setSelected(true);
            }
          });
      btnOuter.addActionListener(
          _ -> {
            for (int idx : allSignalLayers) {
              checkboxes[idx].setSelected(false);
            }
            if (!allSignalLayers.isEmpty()) {
              checkboxes[allSignalLayers.get(0)].setSelected(true);
              checkboxes[allSignalLayers.get(allSignalLayers.size() - 1)].setSelected(true);
            }
          });
      btnInner.addActionListener(
          _ -> {
            for (int idx : allSignalLayers) {
              checkboxes[idx].setSelected(true);
            }
            if (allSignalLayers.size() >= 2) {
              checkboxes[allSignalLayers.get(0)].setSelected(false);
              checkboxes[allSignalLayers.get(allSignalLayers.size() - 1)].setSelected(false);
            }
          });
      btnClear.addActionListener(
          _ -> {
            for (int idx : allSignalLayers) {
              checkboxes[idx].setSelected(false);
            }
          });

      JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
      JButton btnOk = new JButton(p_tm.getText("button_ok"));
      btnOk.setPreferredSize(new Dimension(90, 28));
      btnOk.setFont(btnOk.getFont().deriveFont(Font.BOLD));
      btnOk.addActionListener(_ -> save());
      bottomPanel.add(btnOk);
      getRootPane().setDefaultButton(btnOk);

      add(macroPanel, BorderLayout.NORTH);

      JPanel gridWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
      gridWrapper.add(checkGridPanel);

      JPanel scrollContentHolder = new JPanel(new BorderLayout());
      scrollContentHolder.add(gridWrapper, BorderLayout.NORTH);
      JScrollPane scrollPane = new JScrollPane(scrollContentHolder);
      scrollPane.setPreferredSize(new Dimension(380, dynamicHeight));

      add(scrollPane, BorderLayout.CENTER);
      add(bottomPanel, BorderLayout.SOUTH);
    }

    private void save() {
      for (int i = 0; i < checkboxes.length; i++) {
        if (checkboxes[i] == null) {
          continue;
        }
        netClass.set_active_routing_layer(i, checkboxes[i].isSelected());
      }
      dispose();
    }
  }

  private class LayerRulesCellEditor extends javax.swing.AbstractCellEditor
      implements javax.swing.table.TableCellEditor, ActionListener {
    private final JButton button = new JButton();

    public LayerRulesCellEditor() {
      button.setBorderPainted(false);
      button.setContentAreaFilled(false);
      button.setHorizontalAlignment(SwingConstants.LEFT);
      button.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int row = table.getEditingRow();
      if (row < 0) {
        row = table.getSelectedRow();
      }
      if (row < 0) {
        return;
      }
      int modelRow = table.convertRowIndexToModel(row);
      NetClass nc =
          boardFrame.boardPanel.boardHandling.get_routing_board().rules.netClasses.get(modelRow);
      LayerRulesDialog dialog =
          new LayerRulesDialog(boardFrame, nc, boardFrame.boardPanel.boardHandling, tm);
      dialog.pack();
      dialog.setLocationRelativeTo(boardFrame);
      dialog.setResizable(false);
      dialog.setVisible(true);
      fireEditingStopped();
      tableModel.fireTableRowsUpdated(modelRow, modelRow);
      boardFrame.boardPanel.repaint();
    }

    @Override
    public Component getTableCellEditorComponent(JTable t, Object v, boolean s, int r, int c) {
      button.setText(Objects.toString(v, ""));
      return button;
    }

    @Override
    public Object getCellEditorValue() {
      return button.getText();
    }
  }
}
