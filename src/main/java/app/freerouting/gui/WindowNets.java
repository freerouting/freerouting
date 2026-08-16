package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.board.PrintableShape;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.gui.workspace.RatsNest;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.NetClasses;
import app.freerouting.rules.Nets;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/** Displays and filters the nets on the current board. */
public class WindowNets extends WindowObjectListWithFilter {

  private final JLabel netCountLabel;
  private final JCheckBox filterIncompletesCheckbox;
  private final NetInfoTextPane infoPane;

  /** Creates a new instance of NetsWindow. */
  public WindowNets(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.getLocale());

    this.setTitle(tm.getText("title"));

    // Net count and explanation label at the top
    this.netCountLabel = new JLabel();
    this.netCountLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.add(this.netCountLabel, BorderLayout.NORTH);

    JPanel filterControlPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
    if (this.inputPanel != null) {
      this.southPanel.remove(this.inputPanel);
      filterControlPanel.add(this.inputPanel);
    }

    // Filter incompletes checkbox instead of button
    this.filterIncompletesCheckbox = new JCheckBox(tm.getText("filter_incompletes"));
    this.filterIncompletesCheckbox.setToolTipText(tm.getText("filter_incompletes_tooltip"));
    this.filterIncompletesCheckbox.addActionListener(_ -> recalculate());
    filterControlPanel.add(this.filterIncompletesCheckbox);

    headerPanel.add(filterControlPanel, BorderLayout.CENTER);
    this.mainPanel.add(headerPanel, BorderLayout.NORTH);

    // Selected Net Info Pane
    this.infoPane = new NetInfoTextPane();
    JScrollPane infoScrollPane = new JScrollPane(this.infoPane);
    infoScrollPane.setPreferredSize(new Dimension(150, 80));
    this.centerPanel.add(infoScrollPane, BorderLayout.SOUTH);

    JPanel currentButtonPanel = new JPanel();
    this.southPanel.add(currentButtonPanel, BorderLayout.NORTH);

    final JButton rulesNetsAssignClassButton = new JButton(tm.getText("assign_class"));
    currentButtonPanel.add(rulesNetsAssignClassButton);
    rulesNetsAssignClassButton.setToolTipText(tm.getText("assign_class_tooltip"));
    rulesNetsAssignClassButton.addActionListener(new AssignClassListener());
    rulesNetsAssignClassButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesNetsAssignClassButton", rulesNetsAssignClassButton.getText()));
  }

  @Override
  protected boolean showInfoButton() {
    return false;
  }

  @Override
  protected boolean showSelectButton() {
    return false;
  }

  @Override
  protected boolean showInvertButton() {
    return false;
  }

  @Override
  protected boolean showRecalculateButton() {
    return false;
  }

  @Override
  protected void addToList(Object object) {
    if (object instanceof Net net) {
      if (this.filterIncompletesCheckbox.isSelected()) {
        RatsNest ratsnest = boardFrame.boardPanel.boardHandling.getRatsnest();
        if (ratsnest.incompleteCount(net.netNumber) == 0) {
          return;
        }
      }
    }
    super.addToList(object);
  }

  @Override
  protected void recalculate() {
    super.recalculate();

    if (this.list != null) {
      // Set custom cell renderer to show Net ID, name, and currently assigned class
      this.list.setCellRenderer(
          new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                javax.swing.JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
              java.awt.Component c =
                  super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
              if (value instanceof Net net) {
                setText(
                    "Net #"
                        + net.netNumber
                        + " ("
                        + net.name
                        + ") - Class: "
                        + net.getNetClass().getName());
              }
              return c;
            }
          });

      // Clear/remove old listeners to avoid multiple registrations
      for (java.awt.event.ContainerListener cl : this.list.getContainerListeners()) {
        this.list.removeContainerListener(cl);
      }

      this.list.addListSelectionListener(
          e -> {
            if (!e.getValueIsAdjusting()) {
              updateSelectedNetInfo();
            }
          });

      updateSelectedNetInfo();
    }
  }

  private void updateSelectedNetInfo() {
    if (this.infoPane == null) {
      return;
    }
    this.infoPane.setText("");
    List<Object> selectedNets = this.list.getSelectedValuesList();
    if (selectedNets == null || selectedNets.isEmpty()) {
      return;
    }
    for (Object obj : selectedNets) {
      if (obj instanceof Net net) {
        net.printInfo(this.infoPane, boardFrame.getLocale());
      }
    }
    this.infoPane.setCaretPosition(0);
  }

  /** Fills the list with the nets in the net list. */
  @Override
  protected void fillList() {
    Nets nets = this.boardFrame.boardPanel.boardHandling.getRoutingBoard().rules.nets;
    List<Net> netList = new java.util.ArrayList<>();
    for (int i = 0; i < nets.maxNetNumber(); i++) {
      Net net = nets.get(i + 1);
      if (net != null) {
        netList.add(net);
      }
    }
    netList.sort(java.util.Comparator.comparingInt(n -> n.netNumber));
    for (Net net : netList) {
      this.addToList(net);
    }
    this.list.setVisibleRowCount(Math.min(netList.size(), DEFAULT_TABLE_SIZE));

    if (this.netCountLabel != null) {
      String explanation = tm.getText("net_explanation");
      String countSentence = tm.getText("netCount", String.valueOf(netList.size()));
      this.netCountLabel.setText(
          "<html>" + explanation.replace("\n", "<br>") + "<b>" + countSentence + "</b></html>");
    }
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedNets = list.getSelectedValuesList();
    if (selectedNets.isEmpty()) {
      return;
    }
    int[] selectedNetNumbers = new int[selectedNets.size()];
    for (int i = 0; i < selectedNets.size(); i++) {
      selectedNetNumbers[i] = ((Net) selectedNets.get(i)).netNumber;
    }
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
    Set<Item> selectedItems = new TreeSet<>();
    Collection<Item> boardItems = routingBoard.getItems();
    for (Item currentItem : boardItems) {
      boolean itemMatches = false;
      for (int currentNetNumber : selectedNetNumbers) {
        if (currentItem.containsNet(currentNetNumber)) {
          itemMatches = true;
          break;
        }
      }
      if (itemMatches) {
        selectedItems.add(currentItem);
      }
    }
    boardFrame.boardPanel.boardHandling.selectItems(selectedItems);
    boardFrame.boardPanel.boardHandling.zoomSelection();
  }

  private class AssignClassListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      List<Object> selectedNets = list.getSelectedValuesList();
      if (selectedNets.isEmpty()) {
        return;
      }
      NetClasses netClasses =
          boardFrame.boardPanel.boardHandling.getRoutingBoard().rules.netClasses;
      NetClass[] classArr = new NetClass[netClasses.count()];
      for (int i = 0; i < classArr.length; i++) {
        classArr[i] = netClasses.get(i);
      }
      Object selectedValue =
          JOptionPane.showInputDialog(
              null,
              tm.getText("assign_net_class_prompt"),
              tm.getText("assign_net_class_dialog_title"),
              JOptionPane.INFORMATION_MESSAGE,
              null,
              classArr,
              classArr[0]);
      if (!(selectedValue instanceof NetClass selectedClass)) {
        return;
      }
      for (int i = 0; i < selectedNets.size(); i++) {
        ((Net) selectedNets.get(i)).setClass(selectedClass);
      }
      boardFrame.refreshWindows();
    }
  }

  private class NetInfoTextPane extends JTextPane implements ItemInfoPrinter {
    private final NumberFormat numberFormat;

    public NetInfoTextPane() {
      this.setEditable(false);
      this.numberFormat = NumberFormat.getInstance(boardFrame.getLocale());
      this.numberFormat.setMaximumFractionDigits(4);

      StyledDocument document = this.getStyledDocument();
      Style defaultStyle =
          StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
      document.addStyle("normal", defaultStyle);
      Style boldStyle = document.addStyle("bold", defaultStyle);
      StyleConstants.setBold(boldStyle, true);
    }

    private boolean appendStyledText(String string, String style) {
      StyledDocument document = this.getStyledDocument();
      try {
        document.insertString(document.getLength(), string, document.getStyle(style));
      } catch (BadLocationException _) {
        return false;
      }
      return true;
    }

    @Override
    public boolean appendBold(String string) {
      return appendStyledText(string, "bold");
    }

    @Override
    public boolean appendWithoutTransforming(double value) {
      Float formattedValue = (float) value;
      return append(numberFormat.format(formattedValue));
    }

    @Override
    public boolean append(String string) {
      return appendStyledText(string, "normal");
    }

    @Override
    public boolean append(double value) {
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      Float userValue = (float) coordinateTransform.boardToUser(value);
      return append(numberFormat.format(userValue));
    }

    @Override
    public boolean append(FloatPoint point) {
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      FloatPoint transformedPoint = coordinateTransform.boardToUser(point);
      return append(transformedPoint.toString(boardFrame.getLocale()));
    }

    @Override
    public boolean append(Shape shape, Locale locale) {
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      PrintableShape transformedShape = coordinateTransform.boardToUser(shape, locale);
      if (transformedShape == null) {
        return false;
      }
      return append(transformedShape.toString());
    }

    @Override
    public boolean append(String buttonName, String windowTitle, ItemInfoPrinter.Printable object) {
      Collection<ItemInfoPrinter.Printable> objectList = new LinkedList<>();
      objectList.add(object);
      return appendObjects(buttonName, windowTitle, objectList);
    }

    @Override
    public boolean newline() {
      return append("\n");
    }

    @Override
    public boolean indent() {
      return append("       ");
    }

    @Override
    public boolean appendItems(String buttonName, String windowTitle, Collection<Item> items) {
      Collection<ItemInfoPrinter.Printable> objectList = new LinkedList<>(items);
      return appendObjects(buttonName, windowTitle, objectList);
    }

    @Override
    public boolean appendObjects(
        String buttonName, String windowTitle, Collection<ItemInfoPrinter.Printable> objects) {
      JButton objectInfoButton = new JButton();
      objectInfoButton.setText(buttonName);
      objectInfoButton.setBorderPainted(false);
      objectInfoButton.setContentAreaFilled(false);
      objectInfoButton.setMargin(new Insets(0, 0, 0, 0));
      objectInfoButton.setAlignmentY(0.75f);
      objectInfoButton.setForeground(Color.blue);

      objectInfoButton.addActionListener(
          e -> {
            Collection<WindowObjectInfo.Printable> infoObjects = new LinkedList<>();
            for (ItemInfoPrinter.Printable p : objects) {
              if (p instanceof WindowObjectInfo.Printable wp) {
                infoObjects.add(wp);
              }
            }
            CoordinateTransform coordinateTransform =
                boardFrame.boardPanel.boardHandling.coordinateTransform;
            WindowObjectInfo newWindow =
                WindowObjectInfo.display(windowTitle, infoObjects, boardFrame, coordinateTransform);
            Point loc = getLocation();
            Point newWindowLocation = new Point((int) (loc.getX() + 30), (int) (loc.getY() + 30));
            newWindow.setLocation(newWindowLocation);
            subwindows.add(newWindow);
          });
      objectInfoButton.addActionListener(
          _ -> FRAnalytics.buttonClicked("objectInfoButton", objectInfoButton.getText()));

      StyledDocument document = this.getStyledDocument();
      Style defaultStyle =
          StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
      Style buttonStyle = document.addStyle(buttonName, defaultStyle);
      StyleConstants.setAlignment(buttonStyle, StyleConstants.ALIGN_CENTER);
      StyleConstants.setComponent(buttonStyle, objectInfoButton);

      try {
        document.insertString(document.getLength(), buttonName, buttonStyle);
      } catch (BadLocationException _) {
        return false;
      }
      return true;
    }
  }
}
