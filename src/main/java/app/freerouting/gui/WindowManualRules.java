package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaRule;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
import java.util.ResourceBundle;

/** Used for manual choice of trace widths in interactive routing. */
public class WindowManualRules extends BoardSavableSubWindow {

  private static final int max_slider_value = 15000;
  private static final double scale_factor = 1;
  private final BoardHandling board_handling;
  private final ComboBoxLayer settings_routing_manual_rule_selection_layer_combo_box;
  private final ComboBoxClearance settings_routing_manual_rule_selection_clearance_combo_box;
  private final JComboBox<ViaRule> settings_routing_manual_rule_selection_via_rule_combo_box;
  private final JFormattedTextField trace_width_field;
  private boolean key_input_completed = true;
  /** Creates a new instance of TraceWidthWindow */
  public WindowManualRules(BoardFrame p_board_frame) {
    this.board_handling = p_board_frame.board_panel.board_handling;
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowManualRule", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    // create main panel

    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag_constraints.anchor = GridBagConstraints.WEST;

    JLabel via_rule_label = new JLabel(resources.getString("via_rule"));
    gridbag_constraints.gridwidth = 2;
    gridbag.setConstraints(via_rule_label, gridbag_constraints);
    main_panel.add(via_rule_label);

    RoutingBoard routing_board = this.board_handling.get_routing_board();
    settings_routing_manual_rule_selection_via_rule_combo_box = new JComboBox<>(routing_board.rules.via_rules);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(this.settings_routing_manual_rule_selection_via_rule_combo_box, gridbag_constraints);
    main_panel.add(this.settings_routing_manual_rule_selection_via_rule_combo_box);
    settings_routing_manual_rule_selection_via_rule_combo_box.addActionListener(new ViaRuleComboBoxListener());
    settings_routing_manual_rule_selection_via_rule_combo_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_manual_rule_selection_via_rule_combo_box", settings_routing_manual_rule_selection_via_rule_combo_box.getSelectedItem().toString()));

    JLabel class_label =
        new JLabel(resources.getString("trace_clearance_class"));
    gridbag_constraints.gridwidth = 2;
    gridbag.setConstraints(class_label, gridbag_constraints);
    main_panel.add(class_label);

    settings_routing_manual_rule_selection_clearance_combo_box = new ComboBoxClearance(routing_board.rules.clearance_matrix);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(this.settings_routing_manual_rule_selection_clearance_combo_box, gridbag_constraints);
    main_panel.add(this.settings_routing_manual_rule_selection_clearance_combo_box);
    settings_routing_manual_rule_selection_clearance_combo_box.addActionListener(new ClearanceComboBoxListener());
    settings_routing_manual_rule_selection_clearance_combo_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_manual_rule_selection_clearance_combo_box", settings_routing_manual_rule_selection_clearance_combo_box.getSelectedItem().toString()));

    JLabel separator =
        new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    JLabel width_label = new JLabel(resources.getString("trace_width"));
    gridbag_constraints.gridwidth = 2;
    gridbag.setConstraints(width_label, gridbag_constraints);
    main_panel.add(width_label);
    NumberFormat number_format =
        NumberFormat.getInstance(p_board_frame.get_locale());
    number_format.setMaximumFractionDigits(7);
    this.trace_width_field = new JFormattedTextField(number_format);
    this.trace_width_field.setColumns(7);
    int curr_half_width = this.board_handling.settings.get_manual_trace_half_width(0);
    this.set_trace_width_field(curr_half_width);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(trace_width_field, gridbag_constraints);
    main_panel.add(trace_width_field);
    trace_width_field.addKeyListener(new TraceWidthFieldKeyListener());
    trace_width_field.addFocusListener(new TraceWidthFieldFocusListener());

    JLabel layer_label = new JLabel(resources.getString("on_layer"));
    gridbag_constraints.gridwidth = 2;
    gridbag.setConstraints(layer_label, gridbag_constraints);
    main_panel.add(layer_label);

    settings_routing_manual_rule_selection_layer_combo_box = new ComboBoxLayer(this.board_handling.get_routing_board().layer_structure, p_board_frame.get_locale());
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(this.settings_routing_manual_rule_selection_layer_combo_box, gridbag_constraints);
    main_panel.add(this.settings_routing_manual_rule_selection_layer_combo_box);
    settings_routing_manual_rule_selection_layer_combo_box.addActionListener(new LayerComboBoxListener());
    settings_routing_manual_rule_selection_layer_combo_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_manual_rule_selection_layer_combo_box", settings_routing_manual_rule_selection_layer_combo_box.getSelectedItem().toString()));

    JLabel empty_label = new JLabel();
    gridbag.setConstraints(empty_label, gridbag_constraints);
    main_panel.add(empty_label);

    p_board_frame.set_context_sensitive_help(this, "WindowManualRules");

    this.pack();
    this.setResizable(false);
  }

  /** Recalculates the values in the trace width fields. */
  @Override
  public void refresh() {
    RoutingBoard routing_board = board_handling.get_routing_board();
    ComboBoxModel<ViaRule> new_model =
        new DefaultComboBoxModel<>(routing_board.rules.via_rules);
    this.settings_routing_manual_rule_selection_via_rule_combo_box.setModel(new_model);
    ClearanceMatrix clearance_matrix =
        board_handling.get_routing_board().rules.clearance_matrix;
    if (this.settings_routing_manual_rule_selection_clearance_combo_box.get_class_count()
        != routing_board.rules.clearance_matrix.get_class_count()) {
      this.settings_routing_manual_rule_selection_clearance_combo_box.adjust(clearance_matrix);
    }
    this.settings_routing_manual_rule_selection_clearance_combo_box.setSelectedIndex(
        board_handling.settings.get_manual_trace_clearance_class());
    int via_rule_index = board_handling.settings.get_manual_via_rule_index();
    if (via_rule_index < this.settings_routing_manual_rule_selection_via_rule_combo_box.getItemCount()) {
      this.settings_routing_manual_rule_selection_via_rule_combo_box.setSelectedIndex(board_handling.settings.get_manual_via_rule_index());
    }
    this.set_selected_layer(this.settings_routing_manual_rule_selection_layer_combo_box.get_selected_layer());
    this.repaint();
  }

  public void set_trace_width_field(int p_half_width) {
    if (p_half_width < 0) {
      this.trace_width_field.setText("");
    } else {
      Float trace_width =
          (float) board_handling.coordinate_transform.board_to_user(2 * p_half_width);
      this.trace_width_field.setValue(trace_width);
    }
  }

  /** Sets the selected layer to p_layer. */
  private void set_selected_layer(ComboBoxLayer.Layer p_layer) {
    int curr_half_width;
    if (p_layer.index == ComboBoxLayer.ALL_LAYER_INDEX) {
      // check if the half width is layer_dependent.
      boolean trace_widths_layer_dependent = false;
      int first_half_width = this.board_handling.settings.get_manual_trace_half_width(0);
      for (int i = 1; i < this.board_handling.get_layer_count(); ++i) {
        if (this.board_handling.settings.get_manual_trace_half_width(i) != first_half_width) {
          trace_widths_layer_dependent = true;
          break;
        }
      }
      if (trace_widths_layer_dependent) {
        curr_half_width = -1;
      } else {
        curr_half_width = first_half_width;
      }
    } else if (p_layer.index == ComboBoxLayer.INNER_LAYER_INDEX) {
      // check if the half width is layer_dependent on the inner layers.
      boolean trace_widths_layer_dependent = false;
      int first_half_width = this.board_handling.settings.get_manual_trace_half_width(1);
      for (int i = 2; i < this.board_handling.get_layer_count() - 1; ++i) {
        if (this.board_handling.settings.get_manual_trace_half_width(i) != first_half_width) {
          trace_widths_layer_dependent = true;
          break;
        }
      }
      if (trace_widths_layer_dependent) {
        curr_half_width = -1;
      } else {
        curr_half_width = first_half_width;
      }
    } else {
      curr_half_width = this.board_handling.settings.get_manual_trace_half_width(p_layer.index);
    }
    set_trace_width_field(curr_half_width);
  }

  private class LayerComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ComboBoxLayer.Layer new_selected_layer = settings_routing_manual_rule_selection_layer_combo_box.get_selected_layer();
      set_selected_layer(new_selected_layer);
    }
  }

  private class ClearanceComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int new_index = settings_routing_manual_rule_selection_clearance_combo_box.get_selected_class_index();
      board_handling.settings.set_manual_trace_clearance_class(new_index);
    }
  }

  private class ViaRuleComboBoxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      int new_index = settings_routing_manual_rule_selection_via_rule_combo_box.getSelectedIndex();
      board_handling.settings.set_manual_via_rule_index(new_index);
    }
  }

  private class TraceWidthFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        key_input_completed = true;
        Object input = trace_width_field.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double input_value = ((Number) input).doubleValue();
        if (input_value <= 0) {
          return;
        }
        double board_value = board_handling.coordinate_transform.user_to_board(input_value);
        int new_half_width = (int) Math.round(0.5 * board_value);
        board_handling.set_manual_trace_half_width(
            settings_routing_manual_rule_selection_layer_combo_box.get_selected_layer().index, new_half_width);
        set_trace_width_field(new_half_width);
      } else {
        key_input_completed = false;
      }
    }
  }

  private class TraceWidthFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!key_input_completed) {
        // restore the text field.
        set_selected_layer(settings_routing_manual_rule_selection_layer_combo_box.get_selected_layer());
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }
}
