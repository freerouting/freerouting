package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.RoutingBoard;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaRule;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Used for manual choice of trace widths in interactive routing. */
public class WindowManualRules extends BoardSavableSubWindow {

  private static final int max_slider_value = 15000;
  private static final double scaleFactor = 1;
  private final GuiBoardManager boardHandling;
  private final ComboBoxLayer settingsRoutingManualRuleSelectionLayerComboBox;
  private final ComboBoxClearance settingsRoutingManualRuleSelectionClearanceComboBox;
  private final JComboBox<ViaRule> settingsRoutingManualRuleSelectionViaRuleComboBox;
  private final JFormattedTextField traceWidthField;
  private boolean keyInputCompleted = true;

  /** Creates a new instance of TraceWidthWindow. */
  public WindowManualRules(BoardFrame boardFrame) {
    setLanguage(boardFrame.getLocale());
    this.boardHandling = boardFrame.boardPanel.boardHandling;
    setLanguage(boardFrame.getLocale());

    this.setTitle(tm.getText("title"));

    // create main panel

    final JPanel mainPanel = new JPanel();
    getContentPane().add(mainPanel);
    GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.insets = new Insets(5, 10, 5, 10);
    gridbagConstraints.anchor = GridBagConstraints.WEST;

    JLabel viaRuleLabel = new JLabel(tm.getText("viaRule"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(viaRuleLabel, gridbagConstraints);
    mainPanel.add(viaRuleLabel);

    RoutingBoard routingBoard = this.boardHandling.getRoutingBoard();
    settingsRoutingManualRuleSelectionViaRuleComboBox =
        new JComboBox<>(routingBoard.rules.viaRules);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(
        this.settingsRoutingManualRuleSelectionViaRuleComboBox, gridbagConstraints);
    mainPanel.add(this.settingsRoutingManualRuleSelectionViaRuleComboBox);
    settingsRoutingManualRuleSelectionViaRuleComboBox.addActionListener(
        new ViaRuleComboBoxListener());
    // settingsRoutingManualRuleSelectionViaRuleComboBox.addActionListener(evt ->
    // FRAnalytics.buttonClicked("settingsRoutingManualRuleSelectionViaRuleComboBox",
    // settingsRoutingManualRuleSelectionViaRuleComboBox.getSelectedItem().toString()));

    JLabel classLabel = new JLabel(tm.getText("traceClearanceClass"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(classLabel, gridbagConstraints);
    mainPanel.add(classLabel);

    settingsRoutingManualRuleSelectionClearanceComboBox =
        new ComboBoxClearance(routingBoard.rules.clearanceMatrix);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(
        this.settingsRoutingManualRuleSelectionClearanceComboBox, gridbagConstraints);
    mainPanel.add(this.settingsRoutingManualRuleSelectionClearanceComboBox);
    settingsRoutingManualRuleSelectionClearanceComboBox.addActionListener(
        new ClearanceComboBoxListener());
    // settingsRoutingManualRuleSelectionClearanceComboBox.addActionListener(evt ->
    // FRAnalytics.buttonClicked("settingsRoutingManualRuleSelectionClearanceComboBox",
    // settingsRoutingManualRuleSelectionClearanceComboBox.getSelectedItem().toString()));

    JLabel separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);

    JLabel widthLabel = new JLabel(tm.getText("traceWidth"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(widthLabel, gridbagConstraints);
    mainPanel.add(widthLabel);
    NumberFormat numberFormat = NumberFormat.getInstance(boardFrame.getLocale());
    numberFormat.setMaximumFractionDigits(7);
    this.traceWidthField = new JFormattedTextField(numberFormat);
    this.traceWidthField.setColumns(7);
    int currentHalfWidth = this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(0);
    this.setTraceWidthField(currentHalfWidth);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(traceWidthField, gridbagConstraints);
    mainPanel.add(traceWidthField);
    traceWidthField.addKeyListener(new TraceWidthFieldKeyListener());
    traceWidthField.addFocusListener(new TraceWidthFieldFocusListener());

    JLabel layerLabel = new JLabel(tm.getText("on_layer"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(layerLabel, gridbagConstraints);
    mainPanel.add(layerLabel);

    settingsRoutingManualRuleSelectionLayerComboBox =
        new ComboBoxLayer(
            this.boardHandling.getRoutingBoard().layerStructure, boardFrame.getLocale());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(
        this.settingsRoutingManualRuleSelectionLayerComboBox, gridbagConstraints);
    mainPanel.add(this.settingsRoutingManualRuleSelectionLayerComboBox);
    settingsRoutingManualRuleSelectionLayerComboBox.addActionListener(new LayerComboBoxListener());
    settingsRoutingManualRuleSelectionLayerComboBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingManualRuleSelectionLayerComboBox",
                settingsRoutingManualRuleSelectionLayerComboBox.getSelectedItem().toString()));

    JLabel emptyLabel = new JLabel();
    gridbag.setConstraints(emptyLabel, gridbagConstraints);
    mainPanel.add(emptyLabel);

    this.pack();
    this.setResizable(false);
  }

  /** Recalculates the values in the trace width fields. */
  @Override
  public void refresh() {
    RoutingBoard routingBoard = boardHandling.getRoutingBoard();
    ComboBoxModel<ViaRule> newModel = new DefaultComboBoxModel<>(routingBoard.rules.viaRules);
    this.settingsRoutingManualRuleSelectionViaRuleComboBox.setModel(newModel);
    ClearanceMatrix clearanceMatrix = boardHandling.getRoutingBoard().rules.clearanceMatrix;
    if (this.settingsRoutingManualRuleSelectionClearanceComboBox.getClassCount()
        != routingBoard.rules.clearanceMatrix.getClassCount()) {
      this.settingsRoutingManualRuleSelectionClearanceComboBox.adjust(clearanceMatrix);
    }
    this.settingsRoutingManualRuleSelectionClearanceComboBox.setSelectedIndex(
        boardHandling.getWorkspaceSettings().getManualTraceClearanceClass());
    int viaRuleIndex = boardHandling.getWorkspaceSettings().getManualViaRuleIndex();
    if (viaRuleIndex < this.settingsRoutingManualRuleSelectionViaRuleComboBox.getItemCount()) {
      this.settingsRoutingManualRuleSelectionViaRuleComboBox.setSelectedIndex(
          boardHandling.getWorkspaceSettings().getManualViaRuleIndex());
    }
    this.setSelectedLayer(this.settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer());
    this.repaint();
  }

  /**
   * Updates the trace-width field from a board-unit half-width.
   *
   * @param halfWidth trace half-width in board units
   */
  public void setTraceWidthField(int halfWidth) {
    if (halfWidth < 0) {
      this.traceWidthField.setText("");
    } else {
      Float traceWidth = (float) boardHandling.coordinateTransform.boardToUser(2 * halfWidth);
      this.traceWidthField.setValue(traceWidth);
    }
  }

  /** Sets the selected layer to layer. */
  private void setSelectedLayer(ComboBoxLayer.Layer layer) {
    int currentHalfWidth;
    if (layer.index == ComboBoxLayer.ALL_LAYER_INDEX) {
      // check if the half width is layer_dependent.
      boolean traceWidthsLayerDependent = false;
      int firstHalfWidth = this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(0);
      for (int i = 1; i < this.boardHandling.getLayerCount(); i++) {
        if (this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(i)
            != firstHalfWidth) {
          traceWidthsLayerDependent = true;
          break;
        }
      }
      if (traceWidthsLayerDependent) {
        currentHalfWidth = -1;
      } else {
        currentHalfWidth = firstHalfWidth;
      }
    } else if (layer.index == ComboBoxLayer.INNER_LAYER_INDEX) {
      // check if the half width is layer_dependent on the inner layers.
      boolean traceWidthsLayerDependent = false;
      int firstHalfWidth = this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(1);
      for (int i = 2; i < this.boardHandling.getLayerCount() - 1; i++) {
        if (this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(i)
            != firstHalfWidth) {
          traceWidthsLayerDependent = true;
          break;
        }
      }
      if (traceWidthsLayerDependent) {
        currentHalfWidth = -1;
      } else {
        currentHalfWidth = firstHalfWidth;
      }
    } else {
      currentHalfWidth =
          this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(layer.index);
    }
    setTraceWidthField(currentHalfWidth);
  }

  private class LayerComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ComboBoxLayer.Layer newSelectedLayer =
          settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer();
      setSelectedLayer(newSelectedLayer);
    }
  }

  private class ClearanceComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int newIndex = settingsRoutingManualRuleSelectionClearanceComboBox.getSelectedClassIndex();
      boardHandling.getWorkspaceSettings().setManualTraceClearanceClass(newIndex);
    }
  }

  private class ViaRuleComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int newIndex = settingsRoutingManualRuleSelectionViaRuleComboBox.getSelectedIndex();
      boardHandling.getWorkspaceSettings().setManualViaRuleIndex(newIndex);
    }
  }

  private class TraceWidthFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        keyInputCompleted = true;
        Object input = traceWidthField.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double inputValue = ((Number) input).doubleValue();
        if (inputValue <= 0) {
          return;
        }
        double boardValue = boardHandling.coordinateTransform.userToBoard(inputValue);
        int newHalfWidth = (int) Math.round(0.5 * boardValue);
        boardHandling.setManualTraceHalfWidth(
            settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer().index, newHalfWidth);
        setTraceWidthField(newHalfWidth);
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class TraceWidthFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!keyInputCompleted) {
        // restore the text field.
        setSelectedLayer(settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer());
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }
}
