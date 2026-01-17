package app.freerouting.gui;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.settings.RouterSettings;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Window handling parameters of the automatic routing.
 */
public class WindowAutorouteParameter extends BoardSavableSubWindow {

  private final GuiBoardManager board_handling;
  private final JLabel[] layer_name_arr;
  private final JLabel[] signal_layer_name_arr;
  private final JCheckBox[] settings_autorouter_layer_active_arr;
  private final List<JComboBox<String>> settings_autorouter_combo_box_arr;
  private final JCheckBox settings_autorouter_vias_allowed;
  private final JCheckBox settings_autorouter_fanout_pass_button;
  private final JCheckBox settings_autorouter_autoroute_pass_button;
  private final JCheckBox settings_autorouter_postroute_pass_button;
  private final String horizontal;
  private final String vertical;
  private final JFormattedTextField via_cost_field;
  private final JFormattedTextField plane_via_cost_field;
  private final JFormattedTextField start_ripup_costs;
  private final JFormattedTextField max_passes_field;
  private final JFormattedTextField job_timeout_field;
  private final JFormattedTextField max_threads_field;
  private final JComboBox<String> settings_autorouter_algorithm_combo_box;
  private final String algorithm_current;
  private final String algorithm_v19;
  private final JFormattedTextField[] preferred_direction_trace_cost_arr;
  private final JFormattedTextField[] against_preferred_direction_trace_cost_arr;
  private final boolean[] preferred_direction_trace_costs_input_completed;
  private final boolean[] against_preferred_direction_trace_costs_input_completed;
  private boolean via_cost_input_completed = true;
  private boolean plane_via_cost_input_completed = true;
  private boolean start_ripup_cost_input_completed = true;
  private boolean max_passes_input_completed = true;
  private boolean job_timeout_input_completed = true;
  private boolean max_threads_input_completed = true;

  /**
   * Creates a new instance of WindowAutorouteParameter
   */
  public WindowAutorouteParameter(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());

    this.board_handling = p_board_frame.board_panel.board_handling;
    this.setTitle(tm.getText("title"));

    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    // create main panel

    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.anchor = GridBagConstraints.WEST;
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    gridbag_constraints.gridwidth = 3;
    JLabel layer_label = new JLabel(tm.getText("layer"));
    gridbag.setConstraints(layer_label, gridbag_constraints);
    main_panel.add(layer_label);

    JLabel active_label = new JLabel(tm.getText("active"));
    gridbag.setConstraints(active_label, gridbag_constraints);
    main_panel.add(active_label);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    JLabel preferred_direction_label = new JLabel(tm.getText("preferred_direction"));
    gridbag.setConstraints(preferred_direction_label, gridbag_constraints);
    main_panel.add(preferred_direction_label);

    this.horizontal = tm.getText("horizontal");
    this.vertical = tm.getText("vertical");

    // create the layer list
    LayerStructure layer_structure = board_handling.get_routing_board().layer_structure;
    int layer_count = layer_structure.arr.length;

    // every layer is a row in the gridbag and has 3 columns: name, active,
    // preferred direction
    layer_name_arr = new JLabel[layer_count];
    settings_autorouter_layer_active_arr = new JCheckBox[layer_count];
    settings_autorouter_combo_box_arr = new ArrayList<>(layer_count);

    for (int i = 0; i < layer_count; i++) {
      gridbag_constraints.gridwidth = 3;
      Layer curr_layer = layer_structure.arr[i];

      // set the name
      layer_name_arr[i] = new JLabel();
      layer_name_arr[i].setText(curr_layer.name);
      gridbag.setConstraints(layer_name_arr[i], gridbag_constraints);
      main_panel.add(layer_name_arr[i]);

      // set the active checkbox
      settings_autorouter_layer_active_arr[i] = new JCheckBox();
      settings_autorouter_layer_active_arr[i].addActionListener(new LayerActiveListener(i));
      settings_autorouter_layer_active_arr[i]
          .addActionListener(_ -> FRAnalytics.buttonClicked("settings_autorouter_layer_active_arr", null));
      board_handling.settings.autoroute_settings.set_layer_active(i, curr_layer.is_signal);
      settings_autorouter_layer_active_arr[i].setEnabled(curr_layer.is_signal);
      gridbag.setConstraints(settings_autorouter_layer_active_arr[i], gridbag_constraints);
      main_panel.add(settings_autorouter_layer_active_arr[i]);

      // set the preferred direction combobox
      settings_autorouter_combo_box_arr.add(new JComboBox<>());
      settings_autorouter_combo_box_arr
          .get(i)
          .addItem(this.horizontal);
      settings_autorouter_combo_box_arr
          .get(i)
          .addItem(this.vertical);
      settings_autorouter_combo_box_arr
          .get(i)
          .addActionListener(new PreferredDirectionListener(i));
      // settings_autorouter_combo_box_arr.get(i).addActionListener(evt ->
      // FRAnalytics.buttonClicked("settings_autorouter_combo_box_arr", null));
      gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
      gridbag.setConstraints(settings_autorouter_combo_box_arr.get(i), gridbag_constraints);
      main_panel.add(settings_autorouter_combo_box_arr.get(i));
    }

    JLabel separator = new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    gridbag_constraints.gridwidth = 2;
    JLabel vias_allowed_label = new JLabel(tm.getText("vias_allowed"));
    gridbag.setConstraints(vias_allowed_label, gridbag_constraints);
    main_panel.add(vias_allowed_label);

    settings_autorouter_vias_allowed = new JCheckBox();
    settings_autorouter_vias_allowed.addActionListener(new ViasAllowedListener());
    settings_autorouter_vias_allowed.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_autorouter_vias_allowed", settings_autorouter_vias_allowed.getText()));

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(settings_autorouter_vias_allowed, gridbag_constraints);
    main_panel.add(settings_autorouter_vias_allowed);

    separator = new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    JLabel passes_label = new JLabel(tm.getText("passes"));

    gridbag_constraints.gridwidth = 2;
    gridbag_constraints.gridheight = 3;
    gridbag.setConstraints(passes_label, gridbag_constraints);
    main_panel.add(passes_label);

    this.settings_autorouter_fanout_pass_button = new JCheckBox(tm.getText("fanout"));
    this.settings_autorouter_fanout_pass_button.setToolTipText(tm.getText("fanout_tooltip"));
    this.settings_autorouter_autoroute_pass_button = new JCheckBox(tm.getText("autoroute"));
    this.settings_autorouter_autoroute_pass_button.setToolTipText(tm.getText("autoroute_tooltip"));
    this.settings_autorouter_postroute_pass_button = new JCheckBox(tm.getText("postroute"));
    this.settings_autorouter_postroute_pass_button.setToolTipText(tm.getText("postroute_tooltip"));

    settings_autorouter_fanout_pass_button.addActionListener(new FanoutListener());
    settings_autorouter_fanout_pass_button.addActionListener(_ -> FRAnalytics
        .buttonClicked("settings_autorouter_fanout_pass_button", settings_autorouter_fanout_pass_button.getText()));
    settings_autorouter_autoroute_pass_button.addActionListener(new AutorouteListener());
    settings_autorouter_autoroute_pass_button
        .addActionListener(_ -> FRAnalytics.buttonClicked("settings_autorouter_autoroute_pass_button",
            settings_autorouter_autoroute_pass_button.getText()));
    settings_autorouter_postroute_pass_button.addActionListener(new PostrouteListener());
    settings_autorouter_postroute_pass_button
        .addActionListener(_ -> FRAnalytics.buttonClicked("settings_autorouter_postroute_pass_button",
            settings_autorouter_postroute_pass_button.getText()));

    settings_autorouter_fanout_pass_button.setSelected(false);
    settings_autorouter_autoroute_pass_button.setSelected(true);
    settings_autorouter_postroute_pass_button.setSelected(false);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(settings_autorouter_fanout_pass_button, gridbag_constraints);
    main_panel.add(settings_autorouter_fanout_pass_button, gridbag_constraints);

    gridbag.setConstraints(settings_autorouter_autoroute_pass_button, gridbag_constraints);
    main_panel.add(settings_autorouter_autoroute_pass_button, gridbag_constraints);
    gridbag.setConstraints(settings_autorouter_postroute_pass_button, gridbag_constraints);
    main_panel.add(settings_autorouter_postroute_pass_button, gridbag_constraints);

    separator = new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and number field for the via costs.

    gridbag_constraints.gridwidth = 2;
    JLabel via_cost_label = new JLabel(tm.getText("via_costs"));
    gridbag.setConstraints(via_cost_label, gridbag_constraints);
    main_panel.add(via_cost_label);

    NumberFormat number_format = NumberFormat.getIntegerInstance(p_board_frame.get_locale());
    this.via_cost_field = new JFormattedTextField(number_format);
    this.via_cost_field.setColumns(3);
    this.via_cost_field.addKeyListener(new WindowAutorouteParameter.ViaCostFieldKeyListener());
    this.via_cost_field.addFocusListener(new WindowAutorouteParameter.ViaCostFieldFocusListener());
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(via_cost_field, gridbag_constraints);
    main_panel.add(via_cost_field);

    this.plane_via_cost_field = new JFormattedTextField(number_format);
    this.plane_via_cost_field.setColumns(3);
    this.plane_via_cost_field.addKeyListener(new WindowAutorouteParameter.PlaneViaCostFieldKeyListener());
    this.plane_via_cost_field.addFocusListener(new WindowAutorouteParameter.PlaneViaCostFieldFocusListener());

    gridbag_constraints.gridwidth = 2;
    JLabel plane_via_cost_label = new JLabel(tm.getText("plane_via_costs"));
    gridbag.setConstraints(plane_via_cost_label, gridbag_constraints);
    main_panel.add(plane_via_cost_label);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(plane_via_cost_field, gridbag_constraints);
    main_panel.add(plane_via_cost_field);

    // add label and number field for the start ripup costs.

    gridbag_constraints.gridwidth = 2;
    JLabel start_ripup_costs_label = new JLabel();
    start_ripup_costs_label.setText(tm.getText("start_ripup_costs"));
    gridbag.setConstraints(start_ripup_costs_label, gridbag_constraints);
    main_panel.add(start_ripup_costs_label);

    start_ripup_costs = new JFormattedTextField(number_format);
    start_ripup_costs.setColumns(3);
    this.start_ripup_costs.addKeyListener(new WindowAutorouteParameter.StartRipupCostFieldKeyListener());
    this.start_ripup_costs.addFocusListener(new WindowAutorouteParameter.StartRipupCostFieldFocusListener());
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(start_ripup_costs, gridbag_constraints);
    main_panel.add(start_ripup_costs);

    // add label and number field for max passes

    gridbag_constraints.gridwidth = 2;
    JLabel max_passes_label = new JLabel(tm.getText("max_passes"));
    gridbag.setConstraints(max_passes_label, gridbag_constraints);
    main_panel.add(max_passes_label);

    max_passes_field = new JFormattedTextField(number_format);
    max_passes_field.setColumns(5);
    this.max_passes_field.addKeyListener(new WindowAutorouteParameter.MaxPassesFieldKeyListener());
    this.max_passes_field.addFocusListener(new WindowAutorouteParameter.MaxPassesFieldFocusListener());
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(max_passes_field, gridbag_constraints);
    main_panel.add(max_passes_field);

    // add label and text field for job timeout

    gridbag_constraints.gridwidth = 2;
    JLabel job_timeout_label = new JLabel(tm.getText("job_timeout"));
    gridbag.setConstraints(job_timeout_label, gridbag_constraints);
    main_panel.add(job_timeout_label);

    job_timeout_field = new JFormattedTextField();
    job_timeout_field.setColumns(10);
    this.job_timeout_field.addKeyListener(new WindowAutorouteParameter.JobTimeoutFieldKeyListener());
    this.job_timeout_field.addFocusListener(new WindowAutorouteParameter.JobTimeoutFieldFocusListener());
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(job_timeout_field, gridbag_constraints);
    main_panel.add(job_timeout_field);

    // add label and number field for max threads

    gridbag_constraints.gridwidth = 2;
    JLabel max_threads_label = new JLabel(tm.getText("max_threads"));
    gridbag.setConstraints(max_threads_label, gridbag_constraints);
    main_panel.add(max_threads_label);

    max_threads_field = new JFormattedTextField(number_format);
    max_threads_field.setColumns(3);
    this.max_threads_field.addKeyListener(new WindowAutorouteParameter.MaxThreadsFieldKeyListener());
    this.max_threads_field.addFocusListener(new WindowAutorouteParameter.MaxThreadsFieldFocusListener());
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(max_threads_field, gridbag_constraints);
    main_panel.add(max_threads_field);

    // add label and combo box for the router algorithm selection
    this.algorithm_current = tm.getText("algorithm_current");
    this.algorithm_v19 = tm.getText("algorithm_v19");
    settings_autorouter_algorithm_combo_box = new JComboBox<>();
    settings_autorouter_algorithm_combo_box.addItem(this.algorithm_current);
    settings_autorouter_algorithm_combo_box.addItem(this.algorithm_v19);
    settings_autorouter_algorithm_combo_box.addActionListener(new WindowAutorouteParameter.AlgorithmListener());
    settings_autorouter_algorithm_combo_box
        .addActionListener(_ -> FRAnalytics.buttonClicked("settings_autorouter_algorithm_combo_box",
            settings_autorouter_algorithm_combo_box
                .getSelectedItem()
                .toString()));

    gridbag_constraints.gridwidth = 2;
    JLabel algorithm_label = new JLabel();
    algorithm_label.setText(tm.getText("algorithm"));
    gridbag.setConstraints(algorithm_label, gridbag_constraints);
    main_panel.add(algorithm_label);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(settings_autorouter_algorithm_combo_box, gridbag_constraints);
    main_panel.add(settings_autorouter_algorithm_combo_box);

    JLabel separator2 = new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator2, gridbag_constraints);
    main_panel.add(separator2, gridbag_constraints);

    // add label and number field for the trace costs on each layer.

    gridbag_constraints.gridwidth = 3;
    JLabel trace_costs_on_layer = new JLabel(tm.getText("trace_costs_on_layer"));
    gridbag.setConstraints(trace_costs_on_layer, gridbag_constraints);
    main_panel.add(trace_costs_on_layer);

    JLabel pref_dir_label = new JLabel(tm.getText("in_preferred_direction"));
    gridbag.setConstraints(pref_dir_label, gridbag_constraints);
    main_panel.add(pref_dir_label);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    JLabel against_pref_dir_label = new JLabel(tm.getText("against_preferred_direction"));
    gridbag.setConstraints(against_pref_dir_label, gridbag_constraints);
    main_panel.add(against_pref_dir_label);

    int signal_layer_count = layer_structure.signal_layer_count();
    signal_layer_name_arr = new JLabel[signal_layer_count];
    preferred_direction_trace_cost_arr = new JFormattedTextField[signal_layer_count];
    against_preferred_direction_trace_cost_arr = new JFormattedTextField[signal_layer_count];
    preferred_direction_trace_costs_input_completed = new boolean[signal_layer_count];
    against_preferred_direction_trace_costs_input_completed = new boolean[signal_layer_count];
    number_format = NumberFormat.getInstance(p_board_frame.get_locale());
    number_format.setMaximumFractionDigits(2);
    final int TEXT_FIELD_LENGTH = 2;
    NumberFormat float_number_format = new DecimalFormat("0.0");
    for (int i = 0; i < signal_layer_count; i++) {
      signal_layer_name_arr[i] = new JLabel();
      Layer curr_signal_layer = layer_structure.get_signal_layer(i);
      signal_layer_name_arr[i].setText(curr_signal_layer.name);
      gridbag_constraints.gridwidth = 3;
      gridbag.setConstraints(signal_layer_name_arr[i], gridbag_constraints);
      main_panel.add(signal_layer_name_arr[i]);
      preferred_direction_trace_cost_arr[i] = new JFormattedTextField(float_number_format);
      preferred_direction_trace_cost_arr[i].setColumns(TEXT_FIELD_LENGTH);
      preferred_direction_trace_cost_arr[i]
          .addKeyListener(new WindowAutorouteParameter.PreferredDirectionTraceCostKeyListener(i));
      preferred_direction_trace_cost_arr[i]
          .addFocusListener(new WindowAutorouteParameter.PreferredDirectionTraceCostFocusListener(i));
      gridbag.setConstraints(preferred_direction_trace_cost_arr[i], gridbag_constraints);
      main_panel.add(preferred_direction_trace_cost_arr[i]);
      against_preferred_direction_trace_cost_arr[i] = new JFormattedTextField(float_number_format);
      against_preferred_direction_trace_cost_arr[i].setColumns(TEXT_FIELD_LENGTH);
      against_preferred_direction_trace_cost_arr[i]
          .addKeyListener(new WindowAutorouteParameter.AgainstPreferredDirectionTraceCostKeyListener(i));
      against_preferred_direction_trace_cost_arr[i]
          .addFocusListener(new WindowAutorouteParameter.AgainstPreferredDirectionTraceCostFocusListener(i));
      gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
      gridbag.setConstraints(against_preferred_direction_trace_cost_arr[i], gridbag_constraints);
      main_panel.add(against_preferred_direction_trace_cost_arr[i]);
      preferred_direction_trace_costs_input_completed[i] = true;
      against_preferred_direction_trace_costs_input_completed[i] = true;
    }

    JLabel applyValuesNote = new JLabel(tm.getText("apply_values_note"));
    gridbag.setConstraints(applyValuesNote, gridbag_constraints);
    main_panel.add(applyValuesNote, gridbag_constraints);

    this.refresh();
    this.pack();
    this.setResizable(false);
  }

  /**
   * Recalculates all displayed values
   */
  @Override
  public void refresh() {
    RouterSettings settings = this.board_handling.settings.autoroute_settings;
    LayerStructure layer_structure = this.board_handling.get_routing_board().layer_structure;

    this.settings_autorouter_vias_allowed.setSelected(settings.get_vias_allowed());
    this.settings_autorouter_fanout_pass_button.setSelected(settings.getRunFanout());
    this.settings_autorouter_autoroute_pass_button.setSelected(settings.getRunRouter());
    this.settings_autorouter_postroute_pass_button.setSelected(settings.getRunOptimizer());

    for (int i = 0; i < settings_autorouter_layer_active_arr.length; i++) {
      this.settings_autorouter_layer_active_arr[i].setSelected(settings.get_layer_active(i));
    }

    for (int i = 0; i < settings_autorouter_combo_box_arr.size(); i++) {
      if (settings.get_preferred_direction_is_horizontal(layer_structure.get_layer_no(i))) {
        this.settings_autorouter_combo_box_arr
            .get(i)
            .setSelectedItem(this.horizontal);
      } else {
        this.settings_autorouter_combo_box_arr
            .get(i)
            .setSelectedItem(this.vertical);
      }
    }

    this.via_cost_field.setValue(settings.get_via_costs());
    this.plane_via_cost_field.setValue(settings.get_plane_via_costs());
    this.start_ripup_costs.setValue(settings.get_start_ripup_costs());
    this.max_passes_field.setValue(settings.maxPasses);
    this.job_timeout_field.setValue(settings.jobTimeoutString);
    this.max_threads_field.setValue(settings.maxThreads);
    for (int i = 0; i < preferred_direction_trace_cost_arr.length; i++) {
      this.preferred_direction_trace_cost_arr[i]
          .setValue(settings.get_preferred_direction_trace_costs(layer_structure.get_layer_no(i)));
    }
    for (int i = 0; i < against_preferred_direction_trace_cost_arr.length; i++) {
      this.against_preferred_direction_trace_cost_arr[i]
          .setValue(settings.get_against_preferred_direction_trace_costs(layer_structure.get_layer_no(i)));
    }

    // Set algorithm selection
    if (RouterSettings.ALGORITHM_V19.equals(settings.algorithm)) {
      this.settings_autorouter_algorithm_combo_box.setSelectedItem(this.algorithm_v19);
    } else {
      this.settings_autorouter_algorithm_combo_box.setSelectedItem(this.algorithm_current);
    }
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @Override
  public void parent_iconified() {
    super.parent_iconified();
  }

  @Override
  public void parent_deiconified() {
    super.parent_deiconified();
  }

  private class LayerActiveListener implements ActionListener {

    private final int signal_layer_no;

    public LayerActiveListener(int p_layer_no) {
      signal_layer_no = p_layer_no;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int curr_layer_no = this.signal_layer_no;
      board_handling.settings.autoroute_settings.set_layer_active(curr_layer_no,
          settings_autorouter_layer_active_arr[this.signal_layer_no].isSelected());
    }
  }

  private class PreferredDirectionListener implements ActionListener {

    private final int signal_layer_no;

    public PreferredDirectionListener(int p_layer_no) {
      signal_layer_no = p_layer_no;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
      board_handling.settings.autoroute_settings.set_preferred_direction_is_horizontal(curr_layer_no,
          settings_autorouter_combo_box_arr
              .get(signal_layer_no)
              .getSelectedItem() == horizontal);
    }
  }

  private class ViasAllowedListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.autoroute_settings.set_vias_allowed(settings_autorouter_vias_allowed.isSelected());
    }
  }

  private class FanoutListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      RouterSettings autoroute_settings = board_handling.settings.autoroute_settings;
      autoroute_settings.setRunFanout(settings_autorouter_fanout_pass_button.isSelected());
    }
  }

  private class AutorouteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      RouterSettings autoroute_settings = board_handling.settings.autoroute_settings;
      autoroute_settings.setRunRouter(settings_autorouter_autoroute_pass_button.isSelected());
    }
  }

  private class PostrouteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      RouterSettings autoroute_settings = board_handling.settings.autoroute_settings;
      autoroute_settings.setRunOptimizer(settings_autorouter_postroute_pass_button.isSelected());
    }
  }

  private class ViaCostFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int old_value = board_handling.settings.autoroute_settings.get_via_costs();
        Object input = via_cost_field.getValue();
        int input_value;
        if (input instanceof Number number) {
          input_value = number.intValue();
          if (input_value <= 0) {
            input_value = 1;
            via_cost_field.setValue(input_value);
          }
        } else {
          input_value = old_value;
          via_cost_field.setValue(old_value);
        }
        board_handling.settings.autoroute_settings.set_via_costs(input_value);
        via_cost_field.setValue(input_value);
        via_cost_input_completed = true;

      } else {
        via_cost_input_completed = false;
      }
    }
  }

  private class ViaCostFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!via_cost_input_completed) {
        via_cost_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class PlaneViaCostFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int old_value = board_handling.settings.autoroute_settings.get_plane_via_costs();
        Object input = plane_via_cost_field.getValue();
        int input_value;
        if (input instanceof Number number) {
          input_value = number.intValue();
          if (input_value <= 0) {
            input_value = 1;
            plane_via_cost_field.setValue(input_value);
          }
        } else {
          input_value = old_value;
          plane_via_cost_field.setValue(old_value);
        }
        board_handling.settings.autoroute_settings.set_plane_via_costs(input_value);
        plane_via_cost_field.setValue(input_value);
        plane_via_cost_input_completed = true;

      } else {
        plane_via_cost_input_completed = false;
      }
    }
  }

  private class PlaneViaCostFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!plane_via_cost_input_completed) {
        plane_via_cost_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class StartRipupCostFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int old_value = board_handling.settings.autoroute_settings.get_start_ripup_costs();
        Object input = start_ripup_costs.getValue();
        int input_value;
        if (input instanceof Number number) {
          input_value = number.intValue();
          if (input_value <= 0) {
            input_value = 1;
          }
        } else {
          input_value = old_value;
        }
        board_handling.settings.autoroute_settings.set_start_ripup_costs(input_value);
        start_ripup_costs.setValue(input_value);
        start_ripup_cost_input_completed = true;
      } else {
        start_ripup_cost_input_completed = false;
      }
    }
  }

  private class StartRipupCostFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!start_ripup_cost_input_completed) {
        start_ripup_cost_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class MaxPassesFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int old_value = board_handling.settings.autoroute_settings.maxPasses;
        Object input = max_passes_field.getValue();
        int input_value;
        if (input instanceof Number number) {
          input_value = number.intValue();
          if (input_value < 1) {
            input_value = 1;
          }
          if (input_value > 9999) {
            input_value = 9999;
          }
        } else {
          input_value = old_value;
        }
        board_handling.settings.autoroute_settings.maxPasses = input_value;
        max_passes_field.setValue(input_value);
        max_passes_input_completed = true;
      } else {
        max_passes_input_completed = false;
      }
    }
  }

  private class MaxPassesFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!max_passes_input_completed) {
        max_passes_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class JobTimeoutFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        String old_value = board_handling.settings.autoroute_settings.jobTimeoutString;
        Object input = job_timeout_field.getValue();
        String input_value;
        if (input instanceof String str) {
          input_value = str;
          // Basic validation: check if it matches timespan format (HH:MM:SS or
          // D.HH:MM:SS)
          if (!input_value.matches("^(\\d+\\.)?\\d{1,2}:\\d{2}:\\d{2}$")) {
            input_value = old_value;
          }
        } else {
          input_value = old_value;
        }
        board_handling.settings.autoroute_settings.jobTimeoutString = input_value;
        job_timeout_field.setValue(input_value);
        job_timeout_input_completed = true;
      } else {
        job_timeout_input_completed = false;
      }
    }
  }

  private class JobTimeoutFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!job_timeout_input_completed) {
        job_timeout_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class MaxThreadsFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int old_value = board_handling.settings.autoroute_settings.maxThreads;
        Object input = max_threads_field.getValue();
        int input_value;
        int max_available = Runtime.getRuntime().availableProcessors();
        if (input instanceof Number number) {
          input_value = number.intValue();
          if (input_value < 1) {
            input_value = 1;
          }
          if (input_value > max_available) {
            input_value = max_available;
          }
        } else {
          input_value = old_value;
        }
        board_handling.settings.autoroute_settings.maxThreads = input_value;
        max_threads_field.setValue(input_value);
        max_threads_input_completed = true;
      } else {
        max_threads_input_completed = false;
      }
    }
  }

  private class MaxThreadsFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!max_threads_input_completed) {
        max_threads_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class AlgorithmListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      String oldAlgorithm = board_handling.settings.autoroute_settings.algorithm;
      String newAlgorithm;
      if (settings_autorouter_algorithm_combo_box.getSelectedItem() == algorithm_v19) {
        newAlgorithm = RouterSettings.ALGORITHM_V19;
      } else {
        newAlgorithm = RouterSettings.ALGORITHM_CURRENT;
      }
      if (!oldAlgorithm.equals(newAlgorithm)) {
        board_handling.settings.autoroute_settings.algorithm = newAlgorithm;
      }
    }
  }

  private class PreferredDirectionTraceCostKeyListener extends KeyAdapter {

    private final int signal_layer_no;

    public PreferredDirectionTraceCostKeyListener(int p_layer_no) {
      this.signal_layer_no = p_layer_no;
    }

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
        double old_value = board_handling.settings.autoroute_settings
            .get_preferred_direction_trace_costs(curr_layer_no);
        Object input = preferred_direction_trace_cost_arr[this.signal_layer_no].getValue();
        double input_value;
        if (input instanceof Number number) {
          input_value = number.doubleValue();
          if (input_value <= 0) {
            input_value = old_value;
          }
        } else {
          input_value = old_value;
        }
        board_handling.settings.autoroute_settings.set_preferred_direction_trace_costs(curr_layer_no, input_value);
        preferred_direction_trace_cost_arr[this.signal_layer_no].setValue(input_value);
        preferred_direction_trace_costs_input_completed[this.signal_layer_no] = true;

      } else {
        preferred_direction_trace_costs_input_completed[this.signal_layer_no] = false;
      }
    }
  }

  private class PreferredDirectionTraceCostFocusListener implements FocusListener {

    private final int signal_layer_no;

    public PreferredDirectionTraceCostFocusListener(int p_layer_no) {
      this.signal_layer_no = p_layer_no;
    }

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!preferred_direction_trace_costs_input_completed[this.signal_layer_no]) {
        start_ripup_cost_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class AgainstPreferredDirectionTraceCostKeyListener extends KeyAdapter {

    private final int signal_layer_no;

    public AgainstPreferredDirectionTraceCostKeyListener(int p_layer_no) {
      this.signal_layer_no = p_layer_no;
    }

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
        double old_value = board_handling.settings.autoroute_settings
            .get_against_preferred_direction_trace_costs(curr_layer_no);
        Object input = against_preferred_direction_trace_cost_arr[this.signal_layer_no].getValue();
        double input_value;
        if (input instanceof Number number) {
          input_value = number.doubleValue();
          if (input_value <= 0) {
            input_value = old_value;
          }
        } else {
          input_value = old_value;
        }
        board_handling.settings.autoroute_settings.set_against_preferred_direction_trace_costs(curr_layer_no,
            input_value);
        against_preferred_direction_trace_cost_arr[this.signal_layer_no].setValue(input_value);
        against_preferred_direction_trace_costs_input_completed[this.signal_layer_no] = true;

      } else {
        against_preferred_direction_trace_costs_input_completed[this.signal_layer_no] = false;
      }
    }
  }

  private class AgainstPreferredDirectionTraceCostFocusListener implements FocusListener {

    private final int signal_layer_no;

    public AgainstPreferredDirectionTraceCostFocusListener(int p_layer_no) {
      this.signal_layer_no = p_layer_no;
    }

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!against_preferred_direction_trace_costs_input_completed[this.signal_layer_no]) {
        start_ripup_cost_input_completed = true;
        refresh();
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }
}