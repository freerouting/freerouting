package app.freerouting.gui;

import app.freerouting.interactive.InteractiveSettings;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Trace;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.BoardRules;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.util.Collection;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Window handling parameters of the interactive routing.
 */
public class WindowRouteParameter extends BoardSavableSubWindow {

  private static final int c_region_max_slider_value = 999;
  private static final int c_region_scale_factor = 200;
  private static final int c_accuracy_max_slider_value = 100;
  private static final int c_accuracy_scale_factor = 20;
  final WindowManualRules manual_rule_window;
  private final GuiBoardManager guiBoardManager;
  private final JSlider region_slider;
  private final JFormattedTextField region_width_field;
  private final JFormattedTextField edge_to_turn_dist_field;
  private final JLabel region_percent_label;
  private final JLabel accuracy_percent_label;
  private final JRadioButton settings_routing_snap_angle_90_button;
  private final JRadioButton settings_routing_snap_angle_45_button;
  private final JRadioButton settings_routing_snap_angle_none_button;
  private final JRadioButton settings_routing_dynamic_button;
  private final JRadioButton settings_routing_stitch_button;
  private final JRadioButton settings_routing_automatic_button;
  private final JRadioButton settings_routing_manual_button;
  private final JCheckBox settings_routing_shove_check_box;
  private final JCheckBox settings_routing_drag_component_check_box;
  private final JCheckBox settings_routing_ignore_conduction_check_box;
  private final JCheckBox settings_routing_via_snap_to_smd_center_check_box;
  private final JCheckBox settings_routing_hilight_routing_obstacle_check_box;
  private final JCheckBox settings_routing_neckdown_check_box;
  private final JCheckBox settings_routing_restrict_pin_exit_directions_check_box;
  private final ManualTraceWidthListener manual_trace_width_listener;
  private final JSlider accuracy_slider;
  private final JFormattedTextField accuracy_value_field;
  private final JCheckBox clearance_compensation_check_box;
  private final JCheckBox route_detail_outline_keepout_check_box;
  private boolean key_input_completed = true;

  /**
   * Creates a new instance of RouteParameterWindow
   */
  public WindowRouteParameter(BoardFrame p_board_frame) {
    this.guiBoardManager = p_board_frame.board_panel.board_handling;
    this.manual_rule_window = new WindowManualRules(p_board_frame);

    setLanguage(p_board_frame.get_locale());

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

    // add label and button group for the route snap angle.

    JLabel snap_angle_label = new JLabel(tm.getText("snap_angle"));
    snap_angle_label.setToolTipText(tm.getText("snap_angle_tooltip"));

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 3;
    gridbag.setConstraints(snap_angle_label, gridbag_constraints);
    main_panel.add(snap_angle_label);

    settings_routing_snap_angle_90_button = new JRadioButton(tm.getText("90_degree"));
    settings_routing_snap_angle_45_button = new JRadioButton(tm.getText("45_degree"));
    settings_routing_snap_angle_none_button = new JRadioButton(tm.getText("none"));

    settings_routing_snap_angle_90_button.addActionListener(new SnapAngle90Listener());
    settings_routing_snap_angle_90_button.addActionListener(_ -> FRAnalytics
        .buttonClicked("settings_routing_snap_angle_90_button", settings_routing_snap_angle_90_button.getText()));
    settings_routing_snap_angle_45_button.addActionListener(new SnapAngle45Listener());
    settings_routing_snap_angle_45_button.addActionListener(_ -> FRAnalytics
        .buttonClicked("settings_routing_snap_angle_45_button", settings_routing_snap_angle_45_button.getText()));
    settings_routing_snap_angle_none_button.addActionListener(new SnapAngleNoneListener());
    settings_routing_snap_angle_none_button.addActionListener(_ -> FRAnalytics
        .buttonClicked("settings_routing_snap_angle_none_button", settings_routing_snap_angle_none_button.getText()));

    ButtonGroup snap_angle_button_group = new ButtonGroup();
    snap_angle_button_group.add(settings_routing_snap_angle_90_button);
    snap_angle_button_group.add(settings_routing_snap_angle_45_button);
    snap_angle_button_group.add(settings_routing_snap_angle_none_button);
    settings_routing_snap_angle_none_button.setSelected(true);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(settings_routing_snap_angle_90_button, gridbag_constraints);
    main_panel.add(settings_routing_snap_angle_90_button, gridbag_constraints);

    gridbag.setConstraints(settings_routing_snap_angle_45_button, gridbag_constraints);
    main_panel.add(settings_routing_snap_angle_45_button, gridbag_constraints);
    gridbag.setConstraints(settings_routing_snap_angle_none_button, gridbag_constraints);
    main_panel.add(settings_routing_snap_angle_none_button, gridbag_constraints);
    JLabel separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");

    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and button group for the route mode.

    JLabel route_mode_label = new JLabel(tm.getText("route_mode"));
    route_mode_label.setToolTipText(tm.getText("route_mode_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 2;
    gridbag.setConstraints(route_mode_label, gridbag_constraints);
    main_panel.add(route_mode_label);

    this.settings_routing_dynamic_button = new JRadioButton(tm.getText("dynamic"));
    this.settings_routing_dynamic_button.setToolTipText(tm.getText("dynamic_tooltip"));
    this.settings_routing_stitch_button = new JRadioButton(tm.getText("stitching"));
    this.settings_routing_stitch_button.setToolTipText(tm.getText("stitching_tooltip"));

    settings_routing_dynamic_button.addActionListener(new DynamicRouteListener());
    settings_routing_dynamic_button.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_dynamic_button", settings_routing_dynamic_button.getText()));
    settings_routing_stitch_button.addActionListener(new StitchRouteListener());
    settings_routing_stitch_button.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_stitch_button", settings_routing_stitch_button.getText()));

    ButtonGroup route_mode_button_group = new ButtonGroup();
    route_mode_button_group.add(settings_routing_dynamic_button);
    route_mode_button_group.add(settings_routing_stitch_button);
    settings_routing_dynamic_button.setSelected(true);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(settings_routing_dynamic_button, gridbag_constraints);
    main_panel.add(settings_routing_dynamic_button, gridbag_constraints);
    gridbag.setConstraints(settings_routing_stitch_button, gridbag_constraints);
    main_panel.add(settings_routing_stitch_button, gridbag_constraints);
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    // add label and buttongroup for automatic or manual trace width selection.

    JLabel trace_widths_label = new JLabel(tm.getText("rule_selection"));
    trace_widths_label.setToolTipText(tm.getText("rule_selection_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 2;
    gridbag.setConstraints(trace_widths_label, gridbag_constraints);
    main_panel.add(trace_widths_label);

    settings_routing_automatic_button = new JRadioButton(tm.getText("automatic"));
    settings_routing_automatic_button.setToolTipText(tm.getText("automatic_tooltip"));
    settings_routing_manual_button = new JRadioButton(tm.getText("manual"));
    settings_routing_manual_button.setToolTipText(tm.getText("manual_tooltip"));

    settings_routing_automatic_button.addActionListener(new AutomaticTraceWidthListener());
    settings_routing_automatic_button.addActionListener(_ -> FRAnalytics
        .buttonClicked("settings_routing_automatic_button", settings_routing_automatic_button.getText()));
    manual_trace_width_listener = new ManualTraceWidthListener();
    settings_routing_manual_button.addActionListener(manual_trace_width_listener);
    settings_routing_manual_button.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_manual_button", settings_routing_manual_button.getText()));

    ButtonGroup trace_widths_button_group = new ButtonGroup();
    trace_widths_button_group.add(settings_routing_automatic_button);
    trace_widths_button_group.add(settings_routing_manual_button);
    settings_routing_automatic_button.setSelected(true);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(settings_routing_automatic_button, gridbag_constraints);
    main_panel.add(settings_routing_automatic_button, gridbag_constraints);
    gridbag.setConstraints(settings_routing_manual_button, gridbag_constraints);
    main_panel.add(settings_routing_manual_button, gridbag_constraints);
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    // add check box for push enabled

    settings_routing_shove_check_box = new JCheckBox(tm.getText("push&shove_enabled"));
    settings_routing_shove_check_box.addActionListener(new ShoveListener());
    settings_routing_shove_check_box.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_shove_check_box", settings_routing_shove_check_box.getText()));
    gridbag.setConstraints(settings_routing_shove_check_box, gridbag_constraints);
    settings_routing_shove_check_box.setToolTipText(tm.getText("push&shove_enabled_tooltip"));
    main_panel.add(settings_routing_shove_check_box, gridbag_constraints);

    // add check box for drag components enabled

    settings_routing_drag_component_check_box = new JCheckBox(tm.getText("drag_components_enabled"));
    settings_routing_drag_component_check_box.addActionListener(new DragComponentListener());
    settings_routing_drag_component_check_box
        .addActionListener(_ -> FRAnalytics.buttonClicked("settings_routing_drag_component_check_box",
            settings_routing_drag_component_check_box.getText()));
    gridbag.setConstraints(settings_routing_drag_component_check_box, gridbag_constraints);
    settings_routing_drag_component_check_box.setToolTipText(tm.getText("drag_components_enabled_tooltip"));
    main_panel.add(settings_routing_drag_component_check_box, gridbag_constraints);

    // add check box for via snap to smd center

    settings_routing_via_snap_to_smd_center_check_box = new JCheckBox(tm.getText("via_snap_to_smd_center"));
    settings_routing_via_snap_to_smd_center_check_box.addActionListener(new ViaSnapToSMDCenterListener());
    settings_routing_via_snap_to_smd_center_check_box.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_via_snap_to_smd_center_check_box",
            settings_routing_via_snap_to_smd_center_check_box.getText()));
    gridbag.setConstraints(settings_routing_via_snap_to_smd_center_check_box, gridbag_constraints);
    settings_routing_via_snap_to_smd_center_check_box.setToolTipText(tm.getText("via_snap_to_smd_center_tooltip"));
    main_panel.add(settings_routing_via_snap_to_smd_center_check_box, gridbag_constraints);

    // add check box for highlighting the routing obstacle

    settings_routing_hilight_routing_obstacle_check_box = new JCheckBox(tm.getText("hilight_routing_obstacle"));
    settings_routing_hilight_routing_obstacle_check_box.addActionListener(new HilightObstacleListener());
    settings_routing_hilight_routing_obstacle_check_box.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_hilight_routing_obstacle_check_box",
            settings_routing_hilight_routing_obstacle_check_box.getText()));
    gridbag.setConstraints(settings_routing_hilight_routing_obstacle_check_box, gridbag_constraints);
    settings_routing_hilight_routing_obstacle_check_box.setToolTipText(tm.getText("hilight_routing_obstacle_tooltip"));
    main_panel.add(settings_routing_hilight_routing_obstacle_check_box, gridbag_constraints);

    // add check box for ignore_conduction_areas

    settings_routing_ignore_conduction_check_box = new JCheckBox(tm.getText("ignore_conduction_areas"));
    settings_routing_ignore_conduction_check_box.addActionListener(new IgnoreConductionListener());
    settings_routing_ignore_conduction_check_box.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_ignore_conduction_check_box",
            settings_routing_ignore_conduction_check_box.getText()));
    gridbag.setConstraints(settings_routing_ignore_conduction_check_box, gridbag_constraints);
    settings_routing_ignore_conduction_check_box.setToolTipText(tm.getText("ignore_conduction_areas_tooltip"));
    main_panel.add(settings_routing_ignore_conduction_check_box, gridbag_constraints);

    // add check box for automatic neckdown

    settings_routing_neckdown_check_box = new JCheckBox(tm.getText("automatic_neckdown"));
    settings_routing_neckdown_check_box.addActionListener(new NeckDownListener());
    settings_routing_neckdown_check_box.addActionListener(_ -> FRAnalytics
        .buttonClicked("settings_routing_neckdown_check_box", settings_routing_neckdown_check_box.getText()));
    gridbag.setConstraints(settings_routing_neckdown_check_box, gridbag_constraints);
    settings_routing_neckdown_check_box.setToolTipText(tm.getText("automatic_neckdown_tooltip"));
    main_panel.add(settings_routing_neckdown_check_box, gridbag_constraints);

    // add labels and text field for restricting pin exit directions
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    settings_routing_restrict_pin_exit_directions_check_box = new JCheckBox(tm.getText("restrict_pin_exit_directions"));
    settings_routing_restrict_pin_exit_directions_check_box.addActionListener(new RestrictPinExitDirectionsListener());
    settings_routing_restrict_pin_exit_directions_check_box.addActionListener(
        _ -> FRAnalytics.buttonClicked("settings_routing_restrict_pin_exit_directions_check_box",
            settings_routing_restrict_pin_exit_directions_check_box.getText()));
    gridbag.setConstraints(settings_routing_restrict_pin_exit_directions_check_box, gridbag_constraints);
    settings_routing_restrict_pin_exit_directions_check_box
        .setToolTipText(tm.getText("restrict_pin_exit_directions_tooltip"));
    main_panel.add(settings_routing_restrict_pin_exit_directions_check_box, gridbag_constraints);

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    JLabel pin_exit_edge_to_turn_label = new JLabel(tm.getText("pin_pad_to_turn_gap"));
    pin_exit_edge_to_turn_label.setToolTipText(tm.getText("pin_pad_to_turn_gap_tooltip"));
    gridbag.setConstraints(pin_exit_edge_to_turn_label, gridbag_constraints);
    main_panel.add(pin_exit_edge_to_turn_label);
    NumberFormat number_format = NumberFormat.getInstance(p_board_frame.get_locale());
    number_format.setMaximumFractionDigits(7);
    this.edge_to_turn_dist_field = new JFormattedTextField(number_format);
    this.edge_to_turn_dist_field.setColumns(8);
    this.edge_to_turn_dist_field.setToolTipText(tm.getText("pin_pad_to_turn_gap_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    gridbag.setConstraints(edge_to_turn_dist_field, gridbag_constraints);
    main_panel.add(edge_to_turn_dist_field);
    edge_to_turn_dist_field.addKeyListener(new EdgeToTurnDistFieldKeyListener());
    edge_to_turn_dist_field.addFocusListener(new EdgeToTurnDistFieldFocusListener());

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    main_panel.add(separator, gridbag_constraints);

    // add label and slider for the pull tight region around the cursor.

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.insets = new Insets(3, 10, 3, 10);
    JLabel pull_tight_region_label = new JLabel(tm.getText("pull_tight_region"));
    pull_tight_region_label.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    gridbag.setConstraints(pull_tight_region_label, gridbag_constraints);
    main_panel.add(pull_tight_region_label);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    NumberFormat normalized_format = NumberFormat.getIntegerInstance(p_board_frame.get_locale());
    normalized_format.setGroupingUsed(false);

    this.region_slider = new JSlider();
    region_slider.setMaximum(c_region_max_slider_value);
    region_slider.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    region_slider.addChangeListener(new SliderChangeListener());
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.weightx = 1.0;
    gridbag_constraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(region_slider, gridbag_constraints);
    main_panel.add(region_slider);

    this.region_width_field = new JFormattedTextField(number_format);
    this.region_width_field.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(
      new javax.swing.text.NumberFormatter(normalized_format)));
    this.region_width_field.setColumns(4);
    this.region_width_field.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.insets = new Insets(1, 10, 1, 2);
    gridbag.setConstraints(region_width_field, gridbag_constraints);
    main_panel.add(region_width_field);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    region_width_field.addKeyListener(new RegionWidthFieldKeyListener());
    region_width_field.addFocusListener(new RegionWidthFieldFocusListener());

    this.region_percent_label = new JLabel("%");
    region_percent_label.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.insets = new Insets(1, 4, 1, 10);
    gridbag.setConstraints(region_percent_label, gridbag_constraints);
    main_panel.add(region_percent_label);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    main_panel.add(separator, gridbag_constraints);

    // add label and button group for the clearance compensation.

    JLabel clearance_compensation_label = new JLabel(tm.getText("clearance_compensation"));
    clearance_compensation_label.setToolTipText(tm.getText("clearance_compensation_tooltip"));

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(clearance_compensation_label, gridbag_constraints);
    main_panel.add(clearance_compensation_label);

    clearance_compensation_check_box = new JCheckBox(tm.getText("clearance_compensation_checkbox"));
    clearance_compensation_check_box.setSelected(false);
    clearance_compensation_check_box.setToolTipText(tm.getText("clearance_compensation_checkbox_tooltip"));
    clearance_compensation_check_box.addActionListener(new WindowRouteParameter.CompensationCheckboxListener());
    clearance_compensation_check_box.addActionListener(_ -> FRAnalytics.buttonClicked("clearance_compensation_checkbox", clearance_compensation_check_box.getText()));

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(clearance_compensation_check_box, gridbag_constraints);
    main_panel.add(clearance_compensation_check_box, gridbag_constraints);

    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    JLabel separator2 = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator2, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    main_panel.add(separator2, gridbag_constraints);

    // add label and slider for the pull tight accuracy.

    JLabel pull_tight_accuracy_label = new JLabel(tm.getText("pull_tight_accuracy"));
    pull_tight_accuracy_label.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.insets = new Insets(3, 10, 3, 10);
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag.setConstraints(pull_tight_accuracy_label, gridbag_constraints);
    main_panel.add(pull_tight_accuracy_label);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    this.accuracy_slider = new JSlider();
    accuracy_slider.setMaximum(c_accuracy_max_slider_value);
    accuracy_slider.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    accuracy_slider.addChangeListener(new WindowRouteParameter.AccuracySliderChangeListener());
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.weightx = 1.0;
    gridbag_constraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(accuracy_slider, gridbag_constraints);
    main_panel.add(accuracy_slider);

    this.accuracy_value_field = new JFormattedTextField(normalized_format);
    this.accuracy_value_field.setColumns(4);
    accuracy_value_field.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.insets = new Insets(1, 10, 1, 2);
    gridbag.setConstraints(accuracy_value_field, gridbag_constraints);
    main_panel.add(accuracy_value_field);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    accuracy_value_field.addKeyListener(new AccuracyFieldKeyListener());
    accuracy_value_field.addFocusListener(new AccuracyFieldFocusListener());

    this.accuracy_percent_label = new JLabel("%");
    accuracy_percent_label.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.insets = new Insets(1, 4, 1, 10);
    gridbag.setConstraints(accuracy_percent_label, gridbag_constraints);
    main_panel.add(accuracy_percent_label);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);
    main_panel.add(separator, gridbag_constraints);

    // add switch to define, if keepout is generated outside the outline.

    route_detail_outline_keepout_check_box = new JCheckBox(tm.getText("keepout_outside_outline"));
    route_detail_outline_keepout_check_box.setSelected(false);
    route_detail_outline_keepout_check_box.addActionListener(new WindowRouteParameter.OutLineKeepoutListener());
    route_detail_outline_keepout_check_box.addActionListener(_ -> FRAnalytics
        .buttonClicked("route_detail_outline_keepout_check_box", route_detail_outline_keepout_check_box.getText()));
    gridbag.setConstraints(route_detail_outline_keepout_check_box, gridbag_constraints);
    route_detail_outline_keepout_check_box.setToolTipText(tm.getText("keepout_outside_outline_tooltip"));
    main_panel.add(route_detail_outline_keepout_check_box, gridbag_constraints);

    this.refresh();
    this.pack();
    this.setResizable(false);

    // Subscribe to the InteractiveSettings singleton so this window stays in sync when
    // settings are changed programmatically (e.g. after a new design load).
    InteractiveSettings is = this.guiBoardManager.getInteractiveSettings();
    if (is != null) {
      is.addPropertyChangeListener(_ -> javax.swing.SwingUtilities.invokeLater(this::refresh));
    }
  }

  @Override
  public void dispose() {
    manual_rule_window.dispose();
    super.dispose();
  }

  /**
   * Reads the data of this frame from disk. Returns false, if the reading failed.
   */
  @Override
  public boolean read(ObjectInputStream p_object_stream) {

    boolean read_ok = super.read(p_object_stream);
    if (!read_ok) {
      return false;
    }
    read_ok = manual_rule_window.read(p_object_stream);
    if (!read_ok) {
      return false;
    }
    this.manual_trace_width_listener.first_time = false;
    this.refresh();
    return true;
  }

  /**
   * Saves this frame to disk.
   */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    super.save(p_object_stream);
    manual_rule_window.save(p_object_stream);
  }

  /**
   * Recalculates all displayed values
   */
  @Override
  public void refresh() {
    AngleRestriction snap_angle = this.guiBoardManager.get_routing_board().rules.get_trace_angle_restriction();

    if (snap_angle == AngleRestriction.NINETY_DEGREE) {
      settings_routing_snap_angle_90_button.setSelected(true);
    } else if (snap_angle == AngleRestriction.FORTYFIVE_DEGREE) {
      settings_routing_snap_angle_45_button.setSelected(true);
    } else {
      settings_routing_snap_angle_none_button.setSelected(true);
    }

    if (this.guiBoardManager.getInteractiveSettings().get_is_stitch_route()) {
      settings_routing_stitch_button.setSelected(true);
    } else {
      settings_routing_dynamic_button.setSelected(true);
    }

    if (this.guiBoardManager.getInteractiveSettings().get_manual_rule_selection()) {
      settings_routing_manual_button.setSelected(true);
      if (this.manual_rule_window != null) {
        this.manual_rule_window.setVisible(true);
      }
    } else {
      settings_routing_automatic_button.setSelected(true);
    }

    this.settings_routing_shove_check_box.setSelected(this.guiBoardManager.getInteractiveSettings().get_push_enabled());
    this.settings_routing_drag_component_check_box
        .setSelected(this.guiBoardManager.getInteractiveSettings().get_drag_components_enabled());
    this.settings_routing_via_snap_to_smd_center_check_box
        .setSelected(this.guiBoardManager.getInteractiveSettings().get_via_snap_to_smd_center());
    this.settings_routing_ignore_conduction_check_box
        .setSelected(this.guiBoardManager.get_routing_board().rules.get_ignore_conduction());
    this.settings_routing_hilight_routing_obstacle_check_box
        .setSelected(this.guiBoardManager.getInteractiveSettings().get_hilight_routing_obstacle());
    this.settings_routing_neckdown_check_box.setSelected(this.guiBoardManager.getInteractiveSettings().get_automatic_neckdown());

    double edge_to_turn_dist = this.guiBoardManager.get_routing_board().rules.get_pin_edge_to_turn_dist();
    edge_to_turn_dist = this.guiBoardManager.coordinate_transform.board_to_user(edge_to_turn_dist);
    this.edge_to_turn_dist_field.setValue(edge_to_turn_dist);
    this.settings_routing_restrict_pin_exit_directions_check_box.setSelected(edge_to_turn_dist > 0);

    int region_slider_value = this.guiBoardManager.getInteractiveSettings().get_trace_pull_tight_region_width() / c_region_scale_factor;
    region_slider_value = Math.min(region_slider_value, c_region_max_slider_value);
    region_slider.setValue(region_slider_value);
    region_width_field.setValue((int) Math.round(region_slider_value * 100.0 / c_region_max_slider_value));

    if (this.manual_rule_window != null) {
      this.manual_rule_window.refresh();
    }

    boolean compUsed = this.guiBoardManager.get_routing_board().search_tree_manager.is_clearance_compensation_used();
    this.clearance_compensation_check_box.setSelected(compUsed);
    BoardOutline outline = this.guiBoardManager
        .get_routing_board()
        .get_outline();
    if (outline != null) {
      this.route_detail_outline_keepout_check_box.setSelected(outline.keepout_outside_outline_generated());
    }
    int accuracy_slider_value = c_accuracy_max_slider_value
        - this.guiBoardManager.getInteractiveSettings().get_trace_pull_tight_accuracy() / c_accuracy_scale_factor + 1;
    accuracy_slider.setValue(accuracy_slider_value);
    accuracy_value_field.setValue(accuracy_slider_value);
  }

  @Override
  public void parent_iconified() {
    manual_rule_window.parent_iconified();
    super.parent_iconified();
  }

  @Override
  public void parent_deiconified() {
    manual_rule_window.parent_deiconified();
    super.parent_deiconified();
  }

  private void set_pull_tight_region_width(int p_slider_value) {
    int slider_value = Math.max(p_slider_value, 0);
    slider_value = Math.min(slider_value, c_region_max_slider_value);
    int new_tidy_width;
    if (slider_value >= c_region_max_slider_value) {
      slider_value = c_region_max_slider_value;
      new_tidy_width = Integer.MAX_VALUE;
    } else {
      new_tidy_width = slider_value * c_region_scale_factor;
    }
    region_slider.setValue(slider_value);
    region_width_field.setValue((int) Math.round(slider_value * 100.0 / c_region_max_slider_value));
    guiBoardManager.getInteractiveSettings().set_current_pull_tight_region_width(new_tidy_width);
  }

  static void applyStitchRouteSelection(InteractiveSettings settings, boolean stitchRoute) {
    settings.set_stitch_route(stitchRoute);
  }

  static void applyPushAndShoveSelection(InteractiveSettings settings, boolean pushEnabled) {
    settings.set_push_enabled(pushEnabled);
  }

  static void applyIgnoreConductionSelection(GuiBoardManager boardManager, boolean ignoreConduction) {
    boardManager.set_ignore_conduction(ignoreConduction);
  }

  static void applyClearanceCompensationSelection(GuiBoardManager boardManager, boolean compensationEnabled) {
    boardManager.set_clearance_compensation(compensationEnabled);
  }

  static void applyPinExitEdgeToTurnDistance(GuiBoardManager boardManager, float value) {
    boardManager.set_pin_edge_to_turn_dist(value);
  }

  private class SnapAngle90Listener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (guiBoardManager.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
        return;
      }
      Collection<Trace> trace_list = guiBoardManager
          .get_routing_board()
          .get_traces();
      boolean free_angle_traces_found = false;
      for (Trace curr_trace : trace_list) {
        if (curr_trace instanceof PolylineTrace trace) {
          if (!trace
              .polyline()
              .is_orthogonal()) {
            free_angle_traces_found = true;
            break;
          }
        }
      }
      if (free_angle_traces_found) {
        String curr_message = tm.getText("change_snap_angle_90");
        if (!WindowMessage.confirm(curr_message)) {
          refresh();
          return;
        }
      }
      guiBoardManager.set_current_snap_angle(AngleRestriction.NINETY_DEGREE);
    }
  }

  private class SnapAngle45Listener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (guiBoardManager.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE) {
        return;
      }
      Collection<Trace> trace_list = guiBoardManager
          .get_routing_board()
          .get_traces();
      boolean free_angle_traces_found = false;
      for (Trace curr_trace : trace_list) {
        if (curr_trace instanceof PolylineTrace trace) {
          if (!trace
              .polyline()
              .is_multiple_of_45_degree()) {
            free_angle_traces_found = true;
            break;
          }
        }
      }
      if (free_angle_traces_found) {
        String curr_message = tm.getText("change_snap_angle_45");
        if (!WindowMessage.confirm(curr_message)) {
          refresh();
          return;
        }
      }
      guiBoardManager.set_current_snap_angle(AngleRestriction.FORTYFIVE_DEGREE);
    }
  }

  private class SnapAngleNoneListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      guiBoardManager.set_current_snap_angle(AngleRestriction.NONE);
    }
  }

  private class DynamicRouteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      applyStitchRouteSelection(guiBoardManager.getInteractiveSettings(), false);
    }
  }

  private class StitchRouteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      applyStitchRouteSelection(guiBoardManager.getInteractiveSettings(), true);
    }
  }

  private class AutomaticTraceWidthListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      manual_rule_window.setVisible(false);
      guiBoardManager.getInteractiveSettings().set_manual_tracewidth_selection(false);
    }
  }

  private class ManualTraceWidthListener implements ActionListener {

    boolean first_time = true;

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (first_time) {
        Point location = getLocation();
        manual_rule_window.setLocation((int) location.getX() + 200, (int) location.getY() + 200);
        first_time = false;
      }
      manual_rule_window.setVisible(true);
      guiBoardManager.getInteractiveSettings().set_manual_tracewidth_selection(true);
    }
  }

  private class ShoveListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      applyPushAndShoveSelection(guiBoardManager.getInteractiveSettings(), settings_routing_shove_check_box.isSelected());
      refresh();
    }
  }

  private class ViaSnapToSMDCenterListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      guiBoardManager.getInteractiveSettings()
          .set_via_snap_to_smd_center(settings_routing_via_snap_to_smd_center_check_box.isSelected());
    }
  }

  private class IgnoreConductionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      applyIgnoreConductionSelection(guiBoardManager, settings_routing_ignore_conduction_check_box.isSelected());
    }
  }

  private class HilightObstacleListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      guiBoardManager.getInteractiveSettings()
          .set_hilight_routing_obstacle(settings_routing_hilight_routing_obstacle_check_box.isSelected());
    }
  }

  private class DragComponentListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      guiBoardManager.getInteractiveSettings().set_drag_components_enabled(settings_routing_drag_component_check_box.isSelected());
      refresh();
    }
  }

  private class NeckDownListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      guiBoardManager.getInteractiveSettings().set_automatic_neckdown(settings_routing_neckdown_check_box.isSelected());
    }
  }

  private class RestrictPinExitDirectionsListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (settings_routing_restrict_pin_exit_directions_check_box.isSelected()) {
        BoardRules board_rules = guiBoardManager.get_routing_board().rules;
        double edge_to_turn_dist = guiBoardManager.coordinate_transform
            .board_to_user(board_rules.get_min_trace_half_width());
        guiBoardManager.set_pin_edge_to_turn_dist(edge_to_turn_dist);
      } else {
        guiBoardManager.set_pin_edge_to_turn_dist(0);
      }
      refresh();
    }
  }

  private class EdgeToTurnDistFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        key_input_completed = true;
        Object input = edge_to_turn_dist_field.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        float input_value = ((Number) input).floatValue();
        applyPinExitEdgeToTurnDistance(guiBoardManager, input_value);
        settings_routing_restrict_pin_exit_directions_check_box.setSelected(input_value > 0);
        refresh();
      } else {
        key_input_completed = false;
      }
    }
  }

  private class EdgeToTurnDistFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!key_input_completed) {
        // restore the text field.
        double edge_to_turn_dist = guiBoardManager.get_routing_board().rules.get_pin_edge_to_turn_dist();
        edge_to_turn_dist = guiBoardManager.coordinate_transform.board_to_user(edge_to_turn_dist);
        edge_to_turn_dist_field.setValue(edge_to_turn_dist);
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class RegionWidthFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        key_input_completed = true;
        Object input = region_width_field.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double percent_value = ((Number) input).doubleValue();
        percent_value = Math.max(0.0, Math.min(100.0, percent_value));
        int slider_value = (int) Math.round(percent_value * c_region_max_slider_value / 100.0);
        set_pull_tight_region_width(slider_value);
      } else {
        key_input_completed = false;
      }
    }
  }

  private class RegionWidthFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!key_input_completed) {
        // restore the text field.
        region_width_field.setValue((int) Math.round(region_slider.getValue() * 100.0 / c_region_max_slider_value));
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class AccuracyFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        key_input_completed = true;
        Object input = accuracy_value_field.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double percent_value = ((Number) input).doubleValue();
        percent_value = Math.max(0.0, Math.min(100.0, percent_value));
        int slider_value = (int) Math.round(percent_value);
        accuracy_slider.setValue(slider_value);
      } else {
        key_input_completed = false;
      }
    }
  }

  private class AccuracyFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!key_input_completed) {
        accuracy_value_field.setValue(accuracy_slider.getValue());
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {
    }
  }

  private class SliderChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent evt) {
      int sliderValue = region_slider.getValue();
      region_width_field.setValue((int) Math.round(sliderValue * 100.0 / c_region_max_slider_value));
      set_pull_tight_region_width(sliderValue);
    }
  }

  private class CompensationOnListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      applyClearanceCompensationSelection(guiBoardManager, true);
    }
  }

  private class CompensationOffListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      applyClearanceCompensationSelection(guiBoardManager, false);
    }
  }

  private class CompensationCheckboxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      guiBoardManager.set_clearance_compensation(clearance_compensation_check_box.isSelected());
    }
  }

  private class AccuracySliderChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent evt) {
      int sliderValue = accuracy_slider.getValue();
      accuracy_value_field.setValue(sliderValue);
      int new_accuracy = (c_accuracy_max_slider_value - sliderValue + 1) * c_accuracy_scale_factor;
      guiBoardManager.getInteractiveSettings().set_trace_pull_tight_accuracy(new_accuracy);
    }
  }

  private class OutLineKeepoutListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (guiBoardManager.is_board_read_only()) {
        return;
      }
      BoardOutline outline = guiBoardManager
          .get_routing_board()
          .get_outline();
      if (outline != null) {
        outline.generate_keepout_outside(route_detail_outline_keepout_check_box.isSelected());
      }
    }
  }
}