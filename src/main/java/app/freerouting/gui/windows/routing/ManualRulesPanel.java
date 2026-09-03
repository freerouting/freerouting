package app.freerouting.gui.windows.routing;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.controls.ComboBoxClearance;
import app.freerouting.gui.controls.ComboBoxLayer;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaRule;
import app.freerouting.util.TextManager;
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
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Inline panel for the manual choice of trace widths, clearance class and via rule in interactive
 * routing. Replaces the former free-floating {@code WindowManualRules} popup; it is embedded in the
 * route parameter window below the "Manual" radio button and shown only while manual rule
 * selection is active.
 */
public class ManualRulesPanel extends JPanel {

  private final GuiBoardManager boardHandling;
  private final TextManager tm;
  private final ComboBoxLayer settingsRoutingManualRuleSelectionLayerComboBox;
  private final ComboBoxClearance settingsRoutingManualRuleSelectionClearanceComboBox;
  private final JComboBox<ViaRule> settingsRoutingManualRuleSelectionViaRuleComboBox;
  private final JFormattedTextField traceWidthField;
  private boolean keyInputCompleted = true;

  /** Creates the inline panel for the manual rule selection. */
  public ManualRulesPanel(BoardFrame boardFrame) {
    this.boardHandling = boardFrame.boardPanel.boardHandling;
    this.tm = new TextManager(ManualRulesPanel.class, boardFrame.getLocale());

    // Titled box so the manual options read as belonging to the "Manual" choice.
    this.setBorder(BorderFactory.createTitledBorder(tm.getText("title")));

    GridBagLayout gridbag = new GridBagLayout();
    this.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.insets = new Insets(1, 5, 1, 5);
    gridbagConstraints.anchor = GridBagConstraints.WEST;

    JLabel viaRuleLabel = new JLabel(tm.getText("via_rule"));
    viaRuleLabel.setToolTipText(tm.getText("via_rule_tooltip"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(viaRuleLabel, gridbagConstraints);
    this.add(viaRuleLabel);

    RoutingBoard routingBoard = this.boardHandling.getRoutingBoard();
    settingsRoutingManualRuleSelectionViaRuleComboBox =
        new JComboBox<>(routingBoard.rules.viaRules);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(
        this.settingsRoutingManualRuleSelectionViaRuleComboBox, gridbagConstraints);
    this.settingsRoutingManualRuleSelectionViaRuleComboBox.setToolTipText(
        tm.getText("via_rule_tooltip"));
    this.add(this.settingsRoutingManualRuleSelectionViaRuleComboBox);
    settingsRoutingManualRuleSelectionViaRuleComboBox.addActionListener(new ViaRuleComboBoxListener());

    JLabel classLabel = new JLabel(tm.getText("trace_clearance_class"));
    classLabel.setToolTipText(tm.getText("trace_clearance_class_tooltip"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(classLabel, gridbagConstraints);
    this.add(classLabel);

    settingsRoutingManualRuleSelectionClearanceComboBox =
        new ComboBoxClearance(routingBoard.rules.clearanceMatrix);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(
        this.settingsRoutingManualRuleSelectionClearanceComboBox, gridbagConstraints);
    this.settingsRoutingManualRuleSelectionClearanceComboBox.setToolTipText(
        tm.getText("trace_clearance_class_tooltip"));
    this.add(this.settingsRoutingManualRuleSelectionClearanceComboBox);
    settingsRoutingManualRuleSelectionClearanceComboBox.addActionListener(
        new ClearanceComboBoxListener());

    JLabel widthLabel = new JLabel(tm.getText("trace_width"));
    widthLabel.setToolTipText(tm.getText("trace_width_tooltip"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(widthLabel, gridbagConstraints);
    this.add(widthLabel);
    NumberFormat numberFormat = NumberFormat.getInstance(boardFrame.getLocale());
    numberFormat.setMaximumFractionDigits(7);
    this.traceWidthField = new JFormattedTextField(numberFormat);
    this.traceWidthField.setColumns(7);
    int currentHalfWidth = this.boardHandling.getWorkspaceSettings().getManualTraceHalfWidth(0);
    this.setTraceWidthField(currentHalfWidth);
    this.traceWidthField.setToolTipText(tm.getText("trace_width_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(traceWidthField, gridbagConstraints);
    this.add(traceWidthField);
    traceWidthField.addKeyListener(new TraceWidthFieldKeyListener());
    traceWidthField.addFocusListener(new TraceWidthFieldFocusListener());

    JLabel layerLabel = new JLabel(tm.getText("on_layer"));
    layerLabel.setToolTipText(tm.getText("on_layer_tooltip"));
    gridbagConstraints.gridwidth = 2;
    gridbag.setConstraints(layerLabel, gridbagConstraints);
    this.add(layerLabel);

    settingsRoutingManualRuleSelectionLayerComboBox =
        new ComboBoxLayer(
            this.boardHandling.getRoutingBoard().layerStructure, boardFrame.getLocale());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(
        this.settingsRoutingManualRuleSelectionLayerComboBox, gridbagConstraints);
    this.settingsRoutingManualRuleSelectionLayerComboBox.setToolTipText(
        tm.getText("on_layer_tooltip"));
    this.add(this.settingsRoutingManualRuleSelectionLayerComboBox);
    settingsRoutingManualRuleSelectionLayerComboBox.addActionListener(new LayerComboBoxListener());
    settingsRoutingManualRuleSelectionLayerComboBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingManualRuleSelectionLayerComboBox",
                settingsRoutingManualRuleSelectionLayerComboBox.getSelectedItem().toString()));

    // The panel is shown/hidden by the Manual/Automatic radio buttons in
    // WindowRouteParameter; the initial visibility is set by refresh().
    this.setVisible(false);
  }

  /** Recalculates the values in the trace width fields. */
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

  private void applyTraceWidthFromField() {
    try {
      traceWidthField.commitEdit();
    } catch (java.text.ParseException _) {
      setSelectedLayer(settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer());
      return;
    }
    Object input = traceWidthField.getValue();
    if (input instanceof Number number && number.doubleValue() > 0) {
      double inputValue = number.doubleValue();
      double boardValue = boardHandling.coordinateTransform.userToBoard(inputValue);
      int newHalfWidth = (int) Math.round(0.5 * boardValue);
      boardHandling.setManualTraceHalfWidth(
          settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer().index, newHalfWidth);
      setTraceWidthField(newHalfWidth);
    } else {
      setSelectedLayer(settingsRoutingManualRuleSelectionLayerComboBox.getSelectedLayer());
    }
  }

  private class TraceWidthFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        applyTraceWidthFromField();
        keyInputCompleted = true;
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class TraceWidthFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!keyInputCompleted) {
        applyTraceWidthFromField();
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }
}
