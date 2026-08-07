package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.analytics.FRAnalytics;
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

  /** Creates a new instance of TraceWidthWindow */
  public WindowManualRules(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());
    this.boardHandling = p_board_frame.boardPanel.boardHandling;
    setLanguage(p_board_frame.get_locale());

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

    RoutingBoard routingBoard = this.boardHandling.get_routing_board();
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
    NumberFormat numberFormat = NumberFormat.getInstance(p_board_frame.get_locale());
    numberFormat.setMaximumFractionDigits(7);
    this.traceWidthField = new JFormattedTextField(numberFormat);
    this.traceWidthField.setColumns(7);
    int currHalfWidth = this.boardHandling.getInteractiveSettings().get_manual_trace_half_width(0);
    this.set_trace_width_field(currHalfWidth);
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
            this.boardHandling.get_routing_board().layerStructure, p_board_frame.get_locale());
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
    RoutingBoard routingBoard = boardHandling.get_routing_board();
    ComboBoxModel<ViaRule> newModel = new DefaultComboBoxModel<>(routingBoard.rules.viaRules);
    this.settingsRoutingManualRuleSelectionViaRuleComboBox.setModel(newModel);
    ClearanceMatrix clearanceMatrix = boardHandling.get_routing_board().rules.clearanceMatrix;
    if (this.settingsRoutingManualRuleSelectionClearanceComboBox.get_class_count()
        != routingBoard.rules.clearanceMatrix.get_class_count()) {
      this.settingsRoutingManualRuleSelectionClearanceComboBox.adjust(clearanceMatrix);
    }
    this.settingsRoutingManualRuleSelectionClearanceComboBox.setSelectedIndex(
        boardHandling.getInteractiveSettings().get_manual_trace_clearance_class());
    int viaRuleIndex = boardHandling.getInteractiveSettings().get_manual_via_rule_index();
    if (viaRuleIndex < this.settingsRoutingManualRuleSelectionViaRuleComboBox.getItemCount()) {
      this.settingsRoutingManualRuleSelectionViaRuleComboBox.setSelectedIndex(
          boardHandling.getInteractiveSettings().get_manual_via_rule_index());
    }
    this.set_selected_layer(
        this.settingsRoutingManualRuleSelectionLayerComboBox.get_selected_layer());
    this.repaint();
  }

  public void set_trace_width_field(int p_half_width) {
    if (p_half_width < 0) {
      this.traceWidthField.setText("");
    } else {
      Float traceWidth = (float) boardHandling.coordinateTransform.board_to_user(2 * p_half_width);
      this.traceWidthField.setValue(traceWidth);
    }
  }

  /** Sets the selected layer to p_layer. */
  private void set_selected_layer(ComboBoxLayer.Layer p_layer) {
    int currHalfWidth;
    if (p_layer.index == ComboBoxLayer.ALL_LAYER_INDEX) {
      // check if the half width is layer_dependent.
      boolean traceWidthsLayerDependent = false;
      int firstHalfWidth =
          this.boardHandling.getInteractiveSettings().get_manual_trace_half_width(0);
      for (int i = 1; i < this.boardHandling.get_layer_count(); i++) {
        if (this.boardHandling.getInteractiveSettings().get_manual_trace_half_width(i)
            != firstHalfWidth) {
          traceWidthsLayerDependent = true;
          break;
        }
      }
      if (traceWidthsLayerDependent) {
        currHalfWidth = -1;
      } else {
        currHalfWidth = firstHalfWidth;
      }
    } else if (p_layer.index == ComboBoxLayer.INNER_LAYER_INDEX) {
      // check if the half width is layer_dependent on the inner layers.
      boolean traceWidthsLayerDependent = false;
      int firstHalfWidth =
          this.boardHandling.getInteractiveSettings().get_manual_trace_half_width(1);
      for (int i = 2; i < this.boardHandling.get_layer_count() - 1; i++) {
        if (this.boardHandling.getInteractiveSettings().get_manual_trace_half_width(i)
            != firstHalfWidth) {
          traceWidthsLayerDependent = true;
          break;
        }
      }
      if (traceWidthsLayerDependent) {
        currHalfWidth = -1;
      } else {
        currHalfWidth = firstHalfWidth;
      }
    } else {
      currHalfWidth =
          this.boardHandling.getInteractiveSettings().get_manual_trace_half_width(p_layer.index);
    }
    set_trace_width_field(currHalfWidth);
  }

  private class LayerComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ComboBoxLayer.Layer newSelectedLayer =
          settingsRoutingManualRuleSelectionLayerComboBox.get_selected_layer();
      set_selected_layer(newSelectedLayer);
    }
  }

  private class ClearanceComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int newIndex = settingsRoutingManualRuleSelectionClearanceComboBox.get_selected_class_index();
      boardHandling.getInteractiveSettings().set_manual_trace_clearance_class(newIndex);
    }
  }

  private class ViaRuleComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int newIndex = settingsRoutingManualRuleSelectionViaRuleComboBox.getSelectedIndex();
      boardHandling.getInteractiveSettings().set_manual_via_rule_index(newIndex);
    }
  }

  private class TraceWidthFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        keyInputCompleted = true;
        Object input = traceWidthField.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double inputValue = ((Number) input).doubleValue();
        if (inputValue <= 0) {
          return;
        }
        double boardValue = boardHandling.coordinateTransform.user_to_board(inputValue);
        int newHalfWidth = (int) Math.round(0.5 * boardValue);
        boardHandling.set_manual_trace_half_width(
            settingsRoutingManualRuleSelectionLayerComboBox.get_selected_layer().index,
            newHalfWidth);
        set_trace_width_field(newHalfWidth);
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class TraceWidthFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!keyInputCompleted) {
        // restore the text field.
        set_selected_layer(settingsRoutingManualRuleSelectionLayerComboBox.get_selected_layer());
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }
}
