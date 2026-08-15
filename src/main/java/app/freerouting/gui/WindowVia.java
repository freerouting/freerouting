package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.core.library.BoardLibrary;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaInfos;
import app.freerouting.rules.ViaRule;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Window for interactive editing of via rules. */
public class WindowVia extends BoardSavableSubWindow {

  private static final int WINDOW_OFFSET = 30;
  private final BoardFrame boardFrame;
  private final JList<ViaRule> ruleList;
  private final DefaultListModel<ViaRule> ruleListModel;
  private final JPanel mainPanel;

  /** The subwindows with information about selected object. */
  private final Collection<JFrame> subwindows = new LinkedList<>();

  /** Creates a new instance of ViaWindow. */
  public WindowVia(BoardFrame boardFrame) {
    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("title"));

    this.boardFrame = boardFrame;

    this.mainPanel = new JPanel();
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    mainPanel.setLayout(new BorderLayout());

    JPanel northPanel = new JPanel();
    mainPanel.add(northPanel, BorderLayout.NORTH);
    GridBagLayout gridbag = new GridBagLayout();
    northPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;

    JLabel availableViaPadstackLabel = new JLabel(tm.getText("available_via_padstacks"));
    availableViaPadstackLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
    gridbag.setConstraints(availableViaPadstackLabel, gridbagConstraints);
    northPanel.add(availableViaPadstackLabel, gridbagConstraints);

    JPanel padstackButtonPanel = new JPanel();
    padstackButtonPanel.setLayout(new FlowLayout());
    gridbag.setConstraints(padstackButtonPanel, gridbagConstraints);
    northPanel.add(padstackButtonPanel, gridbagConstraints);

    final JButton rulesViasPadstacksInfoButton = new JButton(tm.getText("info"));
    rulesViasPadstacksInfoButton.setToolTipText(tm.getText("info_tooltip"));
    rulesViasPadstacksInfoButton.addActionListener(new ShowPadstacksListener());
    rulesViasPadstacksInfoButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasPadstacksInfoButton", rulesViasPadstacksInfoButton.getText()));
    padstackButtonPanel.add(rulesViasPadstacksInfoButton);

    final JButton rulesViasPadstacksCreateButton = new JButton(tm.getText("create"));
    rulesViasPadstacksCreateButton.setToolTipText(tm.getText("create_tooltip"));
    rulesViasPadstacksCreateButton.addActionListener(new AddPadstackListener());
    rulesViasPadstacksCreateButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasPadstacksCreateButton", rulesViasPadstacksCreateButton.getText()));
    padstackButtonPanel.add(rulesViasPadstacksCreateButton);

    final JButton rulesViasPadstacksRemoveButton = new JButton(tm.getText("remove"));
    rulesViasPadstacksRemoveButton.setToolTipText(tm.getText("remove_tooltip"));
    rulesViasPadstacksRemoveButton.addActionListener(new RemovePadstackListener());
    rulesViasPadstacksRemoveButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasPadstacksRemoveButton", rulesViasPadstacksRemoveButton.getText()));
    padstackButtonPanel.add(rulesViasPadstacksRemoveButton);

    JLabel separatorLabel = new JLabel("–––––––––––––––––––––––––––––––––––––––––––––––––––––––––");
    separatorLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
    gridbag.setConstraints(separatorLabel, gridbagConstraints);
    northPanel.add(separatorLabel, gridbagConstraints);

    JLabel availableViasLabel = new JLabel(tm.getText("available_vias"));
    availableViasLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
    gridbag.setConstraints(availableViasLabel, gridbagConstraints);
    northPanel.add(availableViasLabel, gridbagConstraints);

    JPanel viaButtonPanel = new JPanel();
    viaButtonPanel.setLayout(new FlowLayout());
    gridbag.setConstraints(viaButtonPanel, gridbagConstraints);
    northPanel.add(viaButtonPanel, gridbagConstraints);

    final JButton rulesViasViasInfoButton = new JButton(tm.getText("info"));
    rulesViasViasInfoButton.setToolTipText(tm.getText("info_tooltip_2"));
    rulesViasViasInfoButton.addActionListener(new ShowViasListener());
    rulesViasViasInfoButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasViasInfoButton", rulesViasViasInfoButton.getText()));
    viaButtonPanel.add(rulesViasViasInfoButton);

    final JButton rulesViasViasEditButton = new JButton(tm.getText("edit"));
    rulesViasViasEditButton.setToolTipText(tm.getText("edit_tooltip"));
    rulesViasViasEditButton.addActionListener(new EditViasListener());
    rulesViasViasEditButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasViasEditButton", rulesViasViasEditButton.getText()));
    viaButtonPanel.add(rulesViasViasEditButton);

    separatorLabel = new JLabel("–––––––––––––––––––––––––––––––––––––––––––––––––––––––––");
    separatorLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
    gridbag.setConstraints(separatorLabel, gridbagConstraints);
    northPanel.add(separatorLabel, gridbagConstraints);

    JLabel viaRuleListName = new JLabel(tm.getText("viaRules"));
    viaRuleListName.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
    gridbag.setConstraints(viaRuleListName, gridbagConstraints);
    northPanel.add(viaRuleListName, gridbagConstraints);
    northPanel.add(viaRuleListName, gridbagConstraints);

    this.ruleListModel = new DefaultListModel<>();
    this.ruleList = new JList<>(this.ruleListModel);

    this.ruleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.ruleList.setSelectedIndex(0);
    this.ruleList.setVisibleRowCount(5);
    JScrollPane listScrollPane = new JScrollPane(this.ruleList);
    listScrollPane.setPreferredSize(new Dimension(200, 100));
    this.mainPanel.add(listScrollPane, BorderLayout.CENTER);

    // fill the list
    BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
    for (ViaRule currRule : boardRules.viaRules) {
      this.ruleListModel.addElement(currRule);
    }

    // Add buttons to edit the via rules.
    JPanel viaRuleButtonPanel = new JPanel();
    viaRuleButtonPanel.setLayout(new FlowLayout());
    this.add(viaRuleButtonPanel, BorderLayout.SOUTH);

    final JButton rulesViasRulesInfoButton = new JButton(tm.getText("info"));
    rulesViasRulesInfoButton.setToolTipText(tm.getText("info_tooltip_3"));
    rulesViasRulesInfoButton.addActionListener(new ShowViaRuleListener());
    rulesViasRulesInfoButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesInfoButton", rulesViasRulesInfoButton.getText()));
    viaRuleButtonPanel.add(rulesViasRulesInfoButton);

    final JButton rulesViasRulesCreateButton = new JButton(tm.getText("create"));
    rulesViasRulesCreateButton.setToolTipText(tm.getText("create_tooltip_2"));
    rulesViasRulesCreateButton.addActionListener(new AddViaRuleListener());
    rulesViasRulesCreateButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesCreateButton", rulesViasRulesCreateButton.getText()));
    viaRuleButtonPanel.add(rulesViasRulesCreateButton);

    final JButton rulesViasRulesEditButton = new JButton(tm.getText("edit"));
    rulesViasRulesEditButton.setToolTipText(tm.getText("edit_tooltip_2"));
    rulesViasRulesEditButton.addActionListener(new EditViaRuleListener());
    rulesViasRulesEditButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesEditButton", rulesViasRulesEditButton.getText()));
    viaRuleButtonPanel.add(rulesViasRulesEditButton);

    final JButton rulesViasRulesRemoveButton = new JButton(tm.getText("remove"));
    rulesViasRulesRemoveButton.setToolTipText(tm.getText("remove_tooltip_2"));
    rulesViasRulesRemoveButton.addActionListener(new RemoveViaRuleListener());
    rulesViasRulesRemoveButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "rulesViasRulesRemoveButton", rulesViasRulesRemoveButton.getText()));
    viaRuleButtonPanel.add(rulesViasRulesRemoveButton);

    this.add(mainPanel);
    this.pack();
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  }

  @Override
  public void refresh() {
    // reinsert the elements in the rule list
    this.ruleListModel.removeAllElements();
    BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
    for (ViaRule currRule : boardRules.viaRules) {
      this.ruleListModel.addElement(currRule);
    }

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
    if (boardFrame.editViasWindow != null) {
      boardFrame.editViasWindow.dispose();
    }
    super.dispose();
  }

  private class ShowPadstacksListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      Collection<WindowObjectInfo.Printable> objectList = new LinkedList<>();
      BoardLibrary boardLibrary = boardFrame.boardPanel.boardHandling.getRoutingBoard().library;
      for (int i = 0; i < boardLibrary.viaPadstackCount(); i++) {
        objectList.add(boardLibrary.getViaPadstack(i));
      }
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      WindowObjectInfo newWindow =
          WindowObjectInfo.display(
              tm.getText("available_via_padstacks"), objectList, boardFrame, coordinateTransform);
      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }

  private class AddPadstackListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      BasicBoard pcb = boardFrame.boardPanel.boardHandling.getRoutingBoard();
      if (pcb.layerStructure.arr.length <= 1) {
        return;
      }
      String padstackName = JOptionPane.showInputDialog(tm.getText("prompt_new_padstack_name"));
      if (padstackName == null) {
        return;
      }
      while (pcb.library.padstacks.get(padstackName) != null) {
        padstackName =
            JOptionPane.showInputDialog(tm.getText("padstack_name_exists"), padstackName);
        if (padstackName == null) {
          return;
        }
      }
      Layer startLayer = pcb.layerStructure.arr[0];
      Layer endLayer = pcb.layerStructure.arr[pcb.layerStructure.arr.length - 1];
      boolean layersSelected = false;
      if (pcb.layerStructure.arr.length == 2) {
        layersSelected = true;
      } else {
        Layer[] possibleStartLayers =
            Arrays.copyOf(pcb.layerStructure.arr, pcb.layerStructure.arr.length - 1);
        Object selectedValue =
            JOptionPane.showInputDialog(
                null,
                tm.getText("select_start_layer"),
                tm.getText("start_layer_selection"),
                JOptionPane.INFORMATION_MESSAGE,
                null,
                possibleStartLayers,
                possibleStartLayers[0]);
        if (selectedValue == null) {
          return;
        }
        startLayer = (Layer) selectedValue;
        if (startLayer == possibleStartLayers[possibleStartLayers.length - 1]) {
          layersSelected = true;
        }
      }
      if (!layersSelected) {
        int firstPossibleEndLayerNo = pcb.layerStructure.getNo(startLayer) + 1;
        Layer[] possibleEndLayers =
            Arrays.copyOfRange(
                pcb.layerStructure.arr, firstPossibleEndLayerNo, pcb.layerStructure.arr.length);
        Object selectedValue =
            JOptionPane.showInputDialog(
                null,
                tm.getText("select_end_layer"),
                tm.getText("end_layer_selection"),
                JOptionPane.INFORMATION_MESSAGE,
                null,
                possibleEndLayers,
                possibleEndLayers[possibleEndLayers.length - 1]);
        if (selectedValue == null) {
          return;
        }
        endLayer = (Layer) selectedValue;
      }
      // ask for the default radius

      JPanel defaultRadiusInputPanel = new JPanel();
      defaultRadiusInputPanel.add(new JLabel(tm.getText("prompt_default_radius")));
      NumberFormat numberFormat = NumberFormat.getInstance(boardFrame.get_locale());
      numberFormat.setMaximumFractionDigits(7);
      JFormattedTextField defaultRadiusInputField = new JFormattedTextField(numberFormat);
      defaultRadiusInputField.setColumns(7);
      defaultRadiusInputPanel.add(defaultRadiusInputField);
      JOptionPane.showMessageDialog(
          boardFrame, defaultRadiusInputPanel, null, JOptionPane.PLAIN_MESSAGE);
      double defaultRadius = 100.0;
      Object inputValue = defaultRadiusInputField.getValue();
      if (inputValue instanceof Number number) {
        defaultRadius = number.doubleValue();
      }

      // input panel  to make the default radius layer-dependent

      PadstackInputPanel padstackInputPanel =
          new PadstackInputPanel(startLayer, endLayer, defaultRadius);
      JOptionPane.showMessageDialog(
          boardFrame, padstackInputPanel, tm.getText("adjust_circles"), JOptionPane.PLAIN_MESSAGE);
      int fromLayerNo = pcb.layerStructure.getNo(startLayer);
      int toLayerNo = pcb.layerStructure.getNo(endLayer);
      ConvexShape[] padstackShapes = new ConvexShape[pcb.layerStructure.arr.length];
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      boolean shapeExists = false;
      for (int i = fromLayerNo; i <= toLayerNo; i++) {
        Object input = padstackInputPanel.circleRadius[i - fromLayerNo].getValue();
        double radius = defaultRadius;
        if (input instanceof Number number) {
          radius = number.doubleValue();
        }
        int circleRadius = (int) Math.round(coordinateTransform.userToBoard(radius));
        if (circleRadius > 0) {
          padstackShapes[i] = new Circle(app.freerouting.geometry.planar.Point.ZERO, circleRadius);
          shapeExists = true;
        }
      }
      if (!shapeExists) {
        return;
      }
      Padstack newPadstack = pcb.library.padstacks.add(padstackName, padstackShapes, true, true);
      pcb.library.addViaPadstack(newPadstack);
    }
  }

  /** Internal class used in AddPadstackListener. */
  private class PadstackInputPanel extends JPanel {

    private final JLabel[] layerNames;
    private final JFormattedTextField[] circleRadius;

    PadstackInputPanel(Layer fromLayer, Layer toLayer, Double defaultRadius) {
      GridBagLayout gridbag = new GridBagLayout();
      this.setLayout(gridbag);
      GridBagConstraints gridbagConstraints = new GridBagConstraints();

      LayerStructure layerStructure =
          boardFrame.boardPanel.boardHandling.getRoutingBoard().layerStructure;
      int fromLayerNo = layerStructure.getNo(fromLayer);
      int toLayerNo = layerStructure.getNo(toLayer);
      int layerCount = toLayerNo - fromLayerNo + 1;
      layerNames = new JLabel[layerCount];
      circleRadius = new JFormattedTextField[layerCount];
      for (int i = 0; i < layerCount; i++) {
        String labelString =
            tm.getText("radius_on_layer_label", layerStructure.arr[fromLayerNo + i].name);
        layerNames[i] = new JLabel(labelString);
        NumberFormat numberFormat = NumberFormat.getInstance(boardFrame.get_locale());
        numberFormat.setMaximumFractionDigits(7);
        circleRadius[i] = new JFormattedTextField(numberFormat);
        circleRadius[i].setColumns(7);
        circleRadius[i].setValue(defaultRadius);
        gridbag.setConstraints(layerNames[i], gridbagConstraints);
        gridbagConstraints.gridwidth = 2;
        this.add(layerNames[i], gridbagConstraints);
        gridbag.setConstraints(circleRadius[i], gridbagConstraints);
        gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
        this.add(circleRadius[i], gridbagConstraints);
      }
    }
  }

  private class RemovePadstackListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      BasicBoard pcb = boardFrame.boardPanel.boardHandling.getRoutingBoard();
      Padstack[] viaPadstacks = pcb.library.getViaPadstacks();
      Object selectedValue =
          JOptionPane.showInputDialog(
              null,
              tm.getText("choose_padstack_to_remove"),
              tm.getText("remove_via_padstack"),
              JOptionPane.INFORMATION_MESSAGE,
              null,
              viaPadstacks,
              viaPadstacks[0]);
      if (selectedValue == null) {
        return;
      }
      Padstack selectedPadstack = (Padstack) selectedValue;
      ViaInfo viaWithSelectedPadstack = null;
      for (int i = 0; i < pcb.rules.viaInfos.count(); i++) {
        if (pcb.rules.viaInfos.get(i).getPadstack() == selectedPadstack) {
          viaWithSelectedPadstack = pcb.rules.viaInfos.get(i);
          break;
        }
      }
      if (viaWithSelectedPadstack != null) {
        boardFrame.screenMessages.setStatusMessage(
            tm.getText("padstack_not_removed_in_use_message", viaWithSelectedPadstack.getName()));
        return;
      }
      pcb.library.removeViaPadstack(selectedPadstack, pcb);
    }
  }

  private class ShowViasListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      Collection<WindowObjectInfo.Printable> objectList = new LinkedList<>();
      ViaInfos viaInfos = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules.viaInfos;
      for (int i = 0; i < viaInfos.count(); i++) {
        objectList.add(viaInfos.get(i));
      }
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      WindowObjectInfo newWindow =
          WindowObjectInfo.display(
              tm.getText("available_vias"), objectList, boardFrame, coordinateTransform);
      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }

  private class EditViasListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      boardFrame.editViasWindow.setVisible(true);
    }
  }

  private class ShowViaRuleListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      List<ViaRule> selectedObjects = ruleList.getSelectedValuesList();
      if (selectedObjects.isEmpty()) {
        return;
      }
      Collection<WindowObjectInfo.Printable> objectList = new LinkedList<>(selectedObjects);
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      WindowObjectInfo newWindow =
          WindowObjectInfo.display(
              tm.getText("selectedRule"), objectList, boardFrame, coordinateTransform);
      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }

  private class EditViaRuleListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ViaRule selectedObject = ruleList.getSelectedValue();
      if (selectedObject == null) {
        return;
      }
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      WindowViaRule newWindow = new WindowViaRule(selectedObject, boardRules.viaInfos, boardFrame);
      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }

  private class AddViaRuleListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      String newName = JOptionPane.showInputDialog(tm.getText("prompt_new_via_rule_name"));
      if (newName == null) {
        return;
      }
      newName = newName.trim();
      if (newName.isEmpty()) {
        return;
      }
      ViaRule newViaRule = new ViaRule(newName);
      BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
      boardRules.viaRules.add(newViaRule);
      ruleListModel.addElement(newViaRule);
      boardFrame.refreshWindows();
    }
  }

  private class RemoveViaRuleListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ViaRule selectedObject = ruleList.getSelectedValue();
      if (selectedObject == null) {
        return;
      }
      ViaRule selectedRule = selectedObject;
      if (WindowMessage.confirm(tm.getText("remove_via_rule_confirm", selectedRule.name))) {
        BoardRules boardRules = boardFrame.boardPanel.boardHandling.getRoutingBoard().rules;
        boardRules.viaRules.remove(selectedRule);
        ruleListModel.removeElement(selectedRule);
      }
    }
  }
}
