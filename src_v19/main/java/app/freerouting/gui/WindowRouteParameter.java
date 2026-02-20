package app.freerouting.gui;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.TestLevel;
import app.freerouting.board.Trace;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.BoardRules;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
import java.util.Locale;
import java.util.ResourceBundle;

/** Window handling parameters of the interactive routing. */
public class WindowRouteParameter extends BoardSavableSubWindow {

  private static final int c_max_slider_value = 999;
  private static final int c_region_scale_factor = 200;
  final WindowManualRules manual_rule_window;
  final WindowRouteDetail detail_window;
  private final BoardHandling board_handling;
  private final Locale current_locale;
  private final JSlider region_slider;
  private final JFormattedTextField region_width_field;
  private final JFormattedTextField edge_to_turn_dist_field;
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
  private final DetailListener detail_listener;
  private final ManualTraceWidthListener manual_trace_width_listener;
  private boolean key_input_completed = true;
  /** Creates a new instance of RouteParameterWindow */
  public WindowRouteParameter(BoardFrame p_board_frame) {
    this.board_handling = p_board_frame.board_panel.board_handling;
    this.current_locale = p_board_frame.get_locale();
    this.detail_window = new WindowRouteDetail(p_board_frame);
    this.manual_rule_window = new WindowManualRules(p_board_frame);

    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowRouteParameter", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

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

    JLabel snap_angle_label = new JLabel(resources.getString("snap_angle"));
    snap_angle_label.setToolTipText(resources.getString("snap_angle_tooltip"));

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 3;
    gridbag.setConstraints(snap_angle_label, gridbag_constraints);
    main_panel.add(snap_angle_label);

    settings_routing_snap_angle_90_button = new JRadioButton(resources.getString("90_degree"));
    settings_routing_snap_angle_45_button = new JRadioButton(resources.getString("45_degree"));
    settings_routing_snap_angle_none_button = new JRadioButton(resources.getString("none"));

    settings_routing_snap_angle_90_button.addActionListener(new SnapAngle90Listener());
    settings_routing_snap_angle_90_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_snap_angle_90_button", settings_routing_snap_angle_90_button.getText()));
    settings_routing_snap_angle_45_button.addActionListener(new SnapAngle45Listener());
    settings_routing_snap_angle_45_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_snap_angle_45_button", settings_routing_snap_angle_45_button.getText()));
    settings_routing_snap_angle_none_button.addActionListener(new SnapAngleNoneListener());
    settings_routing_snap_angle_none_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_snap_angle_none_button", settings_routing_snap_angle_none_button.getText()));

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
    JLabel separator =
        new JLabel("   –––––––––––––––––––––––––––––––––––––––  ");

    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and button group for the route mode.

    JLabel route_mode_label = new JLabel(resources.getString("route_mode"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 2;
    gridbag.setConstraints(route_mode_label, gridbag_constraints);
    main_panel.add(route_mode_label);

    this.settings_routing_dynamic_button = new JRadioButton(resources.getString("dynamic"));
    this.settings_routing_stitch_button = new JRadioButton(resources.getString("stitching"));

    settings_routing_dynamic_button.addActionListener(new DynamicRouteListener());
    settings_routing_dynamic_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_dynamic_button", settings_routing_dynamic_button.getText()));
    settings_routing_stitch_button.addActionListener(new StitchRouteListener());
    settings_routing_stitch_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_stitch_button", settings_routing_stitch_button.getText()));

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

    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and buttongroup for automatic or manual trace width selection.

    JLabel trace_widths_label =
        new JLabel(resources.getString("rule_selection"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 2;
    gridbag.setConstraints(trace_widths_label, gridbag_constraints);
    main_panel.add(trace_widths_label);

    settings_routing_automatic_button = new JRadioButton(resources.getString("automatic"));
    settings_routing_manual_button = new JRadioButton(resources.getString("manual"));

    settings_routing_automatic_button.addActionListener(new AutomaticTraceWidthListener());
    settings_routing_automatic_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_automatic_button", settings_routing_automatic_button.getText()));
    manual_trace_width_listener = new ManualTraceWidthListener();
    settings_routing_manual_button.addActionListener(manual_trace_width_listener);
    settings_routing_manual_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_manual_button", settings_routing_manual_button.getText()));

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
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add check box for push enabled

    settings_routing_shove_check_box = new JCheckBox(resources.getString("push&shove_enabled"));
    settings_routing_shove_check_box.addActionListener(new ShoveListener());
    settings_routing_shove_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_shove_check_box", settings_routing_shove_check_box.getText()));
    gridbag.setConstraints(settings_routing_shove_check_box, gridbag_constraints);
    settings_routing_shove_check_box.setToolTipText(resources.getString("push&shove_enabled_tooltip"));
    main_panel.add(settings_routing_shove_check_box, gridbag_constraints);

    // add check box for drag components enabled

    settings_routing_drag_component_check_box = new JCheckBox(resources.getString("drag_components_enabled"));
    settings_routing_drag_component_check_box.addActionListener(new DragComponentListener());
    settings_routing_drag_component_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_drag_component_check_box", settings_routing_drag_component_check_box.getText()));
    gridbag.setConstraints(settings_routing_drag_component_check_box, gridbag_constraints);
    settings_routing_drag_component_check_box.setToolTipText(resources.getString("drag_components_enabled_tooltip"));
    main_panel.add(settings_routing_drag_component_check_box, gridbag_constraints);

    // add check box for via snap to smd center

    settings_routing_via_snap_to_smd_center_check_box = new JCheckBox(resources.getString("via_snap_to_smd_center"));
    settings_routing_via_snap_to_smd_center_check_box.addActionListener(new ViaSnapToSMDCenterListener());
    settings_routing_via_snap_to_smd_center_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_via_snap_to_smd_center_check_box", settings_routing_via_snap_to_smd_center_check_box.getText()));
    gridbag.setConstraints(settings_routing_via_snap_to_smd_center_check_box, gridbag_constraints);
    settings_routing_via_snap_to_smd_center_check_box.setToolTipText(resources.getString("via_snap_to_smd_center_tooltip"));
    main_panel.add(settings_routing_via_snap_to_smd_center_check_box, gridbag_constraints);

    // add check box for highlighting the routing obstacle

    settings_routing_hilight_routing_obstacle_check_box = new JCheckBox(resources.getString("hilight_routing_obstacle"));
    settings_routing_hilight_routing_obstacle_check_box.addActionListener(new HilightObstacleListener());
    settings_routing_hilight_routing_obstacle_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_hilight_routing_obstacle_check_box", settings_routing_hilight_routing_obstacle_check_box.getText()));
    gridbag.setConstraints(settings_routing_hilight_routing_obstacle_check_box, gridbag_constraints);
    settings_routing_hilight_routing_obstacle_check_box.setToolTipText(resources.getString("hilight_routing_obstacle_tooltip"));
    main_panel.add(settings_routing_hilight_routing_obstacle_check_box, gridbag_constraints);

    // add check box for ignore_conduction_areas

    settings_routing_ignore_conduction_check_box = new JCheckBox(resources.getString("ignore_conduction_areas"));
    settings_routing_ignore_conduction_check_box.addActionListener(new IgnoreConductionListener());
    settings_routing_ignore_conduction_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_ignore_conduction_check_box", settings_routing_ignore_conduction_check_box.getText()));
    gridbag.setConstraints(settings_routing_ignore_conduction_check_box, gridbag_constraints);
    settings_routing_ignore_conduction_check_box.setToolTipText(resources.getString("ignore_conduction_areas_tooltip"));
    main_panel.add(settings_routing_ignore_conduction_check_box, gridbag_constraints);

    // add check box for automatic neckdown

    settings_routing_neckdown_check_box = new JCheckBox(resources.getString("automatic_neckdown"));
    settings_routing_neckdown_check_box.addActionListener(new NeckDownListener());
    settings_routing_neckdown_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_neckdown_check_box", settings_routing_neckdown_check_box.getText()));
    gridbag.setConstraints(settings_routing_neckdown_check_box, gridbag_constraints);
    settings_routing_neckdown_check_box.setToolTipText(resources.getString("automatic_neckdown_tooltip"));
    main_panel.add(settings_routing_neckdown_check_box, gridbag_constraints);

    // add labels and text field for restricting pin exit directions
    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    settings_routing_restrict_pin_exit_directions_check_box = new JCheckBox(resources.getString("restrict_pin_exit_directions"));
    settings_routing_restrict_pin_exit_directions_check_box.addActionListener(new RestrictPinExitDirectionsListener());
    settings_routing_restrict_pin_exit_directions_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_restrict_pin_exit_directions_check_box", settings_routing_restrict_pin_exit_directions_check_box.getText()));
    gridbag.setConstraints(settings_routing_restrict_pin_exit_directions_check_box, gridbag_constraints);
    settings_routing_restrict_pin_exit_directions_check_box.setToolTipText(resources.getString("restrict_pin_exit_directions_tooltip"));
    main_panel.add(settings_routing_restrict_pin_exit_directions_check_box, gridbag_constraints);

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    JLabel pin_exit_edge_to_turn_label = new JLabel(resources.getString("pin_pad_to_turn_gap"));
    pin_exit_edge_to_turn_label.setToolTipText("pin_pad_to_turn_gap_tooltip");
    gridbag.setConstraints(pin_exit_edge_to_turn_label, gridbag_constraints);
    main_panel.add(pin_exit_edge_to_turn_label);
    NumberFormat number_format = NumberFormat.getInstance(p_board_frame.get_locale());
    number_format.setMaximumFractionDigits(7);
    this.edge_to_turn_dist_field = new JFormattedTextField(number_format);
    this.edge_to_turn_dist_field.setColumns(5);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(edge_to_turn_dist_field, gridbag_constraints);
    main_panel.add(edge_to_turn_dist_field);
    edge_to_turn_dist_field.addKeyListener(new EdgeToTurnDistFieldKeyListener());
    edge_to_turn_dist_field.addFocusListener(new EdgeToTurnDistFieldFocusListener());

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    separator = new JLabel("–––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and slider for the pull tight region around the cursor.

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    JLabel pull_tight_region_label =
        new JLabel(resources.getString("pull_tight_region"));
    pull_tight_region_label.setToolTipText(resources.getString("pull_tight_region_tooltip"));
    gridbag.setConstraints(pull_tight_region_label, gridbag_constraints);
    main_panel.add(pull_tight_region_label);

    this.region_width_field = new JFormattedTextField(number_format);
    this.region_width_field.setColumns(3);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(region_width_field, gridbag_constraints);
    main_panel.add(region_width_field);
    region_width_field.addKeyListener(new RegionWidthFieldKeyListener());
    region_width_field.addFocusListener(new RegionWidthFieldFocusListener());

    this.region_slider = new JSlider();
    region_slider.setMaximum(c_max_slider_value);
    region_slider.addChangeListener(new SliderChangeListener());
    gridbag.setConstraints(region_slider, gridbag_constraints);
    main_panel.add(region_slider);

    separator = new JLabel("–––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    JButton settings_routing_detail_button = new JButton(resources.getString("detail_parameter"));
    this.detail_listener = new DetailListener();
    settings_routing_detail_button.addActionListener(detail_listener);
    settings_routing_detail_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_detail_button", settings_routing_detail_button.getText()));
    gridbag.setConstraints(settings_routing_detail_button, gridbag_constraints);
    if (this.board_handling.get_routing_board().get_test_level()
        != TestLevel.RELEASE_VERSION) {
      main_panel.add(settings_routing_detail_button);
    }

    p_board_frame.set_context_sensitive_help(this, "WindowRouteParameter");

    this.refresh();
    this.pack();
    this.setResizable(false);
  }

  @Override
  public void dispose() {
    detail_window.dispose();
    manual_rule_window.dispose();
    super.dispose();
  }

  /** Reads the data of this frame from disk. Returns false, if the reading failed. */
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
    read_ok = detail_window.read(p_object_stream);
    if (!read_ok) {
      return false;
    }
    this.manual_trace_width_listener.first_time = false;
    this.detail_listener.first_time = false;
    this.refresh();
    return true;
  }

  /** Saves this frame to disk. */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    super.save(p_object_stream);
    manual_rule_window.save(p_object_stream);
    detail_window.save(p_object_stream);
  }

  /** Recalculates all displayed values */
  @Override
  public void refresh() {
    AngleRestriction snap_angle =
        this.board_handling.get_routing_board().rules.get_trace_angle_restriction();

    if (snap_angle == AngleRestriction.NINETY_DEGREE) {
      settings_routing_snap_angle_90_button.setSelected(true);
    } else if (snap_angle == AngleRestriction.FORTYFIVE_DEGREE) {
      settings_routing_snap_angle_45_button.setSelected(true);
    } else {
      settings_routing_snap_angle_none_button.setSelected(true);
    }

    if (this.board_handling.settings.get_is_stitch_route()) {
      settings_routing_stitch_button.setSelected(true);
    } else {
      settings_routing_dynamic_button.setSelected(true);
    }

    if (this.board_handling.settings.get_manual_rule_selection()) {
      settings_routing_manual_button.setSelected(true);
      if (this.manual_rule_window != null) {
        this.manual_rule_window.setVisible(true);
      }
    } else {
      settings_routing_automatic_button.setSelected(true);
    }

    this.settings_routing_shove_check_box.setSelected(this.board_handling.settings.get_push_enabled());
    this.settings_routing_drag_component_check_box.setSelected(
        this.board_handling.settings.get_drag_components_enabled());
    this.settings_routing_via_snap_to_smd_center_check_box.setSelected(
        this.board_handling.settings.get_via_snap_to_smd_center());
    this.settings_routing_ignore_conduction_check_box.setSelected(
        this.board_handling.get_routing_board().rules.get_ignore_conduction());
    this.settings_routing_hilight_routing_obstacle_check_box.setSelected(
        this.board_handling.settings.get_hilight_routing_obstacle());
    this.settings_routing_neckdown_check_box.setSelected(this.board_handling.settings.get_automatic_neckdown());

    double edge_to_turn_dist =
        this.board_handling.get_routing_board().rules.get_pin_edge_to_turn_dist();
    edge_to_turn_dist = this.board_handling.coordinate_transform.board_to_user(edge_to_turn_dist);
    this.edge_to_turn_dist_field.setValue(edge_to_turn_dist);
    this.settings_routing_restrict_pin_exit_directions_check_box.setSelected(edge_to_turn_dist > 0);

    int region_slider_value =
        this.board_handling.settings.get_trace_pull_tight_region_width() / c_region_scale_factor;
    region_slider_value = Math.min(region_slider_value, c_max_slider_value);
    region_slider.setValue(region_slider_value);
    region_width_field.setValue(region_slider_value);

    if (this.manual_rule_window != null) {
      this.manual_rule_window.refresh();
    }
    if (this.detail_window != null) {
      this.detail_window.refresh();
    }
  }

  @Override
  public void parent_iconified() {
    manual_rule_window.parent_iconified();
    detail_window.parent_iconified();
    super.parent_iconified();
  }

  @Override
  public void parent_deiconified() {
    manual_rule_window.parent_deiconified();
    detail_window.parent_deiconified();
    super.parent_deiconified();
  }

  private void set_pull_tight_region_width(int p_slider_value) {
    int slider_value = Math.max(p_slider_value, 0);
    slider_value = Math.min(p_slider_value, c_max_slider_value);
    int new_tidy_width;
    if (slider_value >= 0.9 * c_max_slider_value) {
      p_slider_value = c_max_slider_value;
      new_tidy_width = Integer.MAX_VALUE;
    } else {
      new_tidy_width = slider_value * c_region_scale_factor;
    }
    region_slider.setValue(slider_value);
    region_width_field.setValue(slider_value);
    board_handling.settings.set_current_pull_tight_region_width(new_tidy_width);
  }

  private class SnapAngle90Listener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (board_handling.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.NINETY_DEGREE) {
        return;
      }
      Collection<Trace> trace_list =
          board_handling.get_routing_board().get_traces();
      boolean free_angle_traces_found = false;
      for (Trace curr_trace : trace_list) {
        if (curr_trace instanceof PolylineTrace) {
          if (!((PolylineTrace) curr_trace).polyline().is_orthogonal()) {
            free_angle_traces_found = true;
            break;
          }
        }
      }
      if (free_angle_traces_found) {
        ResourceBundle resources =
            ResourceBundle.getBundle(
                "app.freerouting.gui.WindowRouteParameter", current_locale);
        String curr_message = resources.getString("change_snap_angle_90");
        if (!WindowMessage.confirm(curr_message)) {
          refresh();
          return;
        }
      }
      board_handling.set_current_snap_angle(AngleRestriction.NINETY_DEGREE);
    }
  }

  private class SnapAngle45Listener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (board_handling.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.FORTYFIVE_DEGREE) {
        return;
      }
      Collection<Trace> trace_list =
          board_handling.get_routing_board().get_traces();
      boolean free_angle_traces_found = false;
      for (Trace curr_trace : trace_list) {
        if (curr_trace instanceof PolylineTrace) {
          if (!((PolylineTrace) curr_trace)
              .polyline()
              .is_multiple_of_45_degree()) {
            free_angle_traces_found = true;
            break;
          }
        }
      }
      if (free_angle_traces_found) {
        ResourceBundle resources =
            ResourceBundle.getBundle(
                "app.freerouting.gui.WindowRouteParameter", current_locale);
        String curr_message = resources.getString("change_snap_angle_45");
        if (!WindowMessage.confirm(curr_message)) {
          refresh();
          return;
        }
      }
      board_handling.set_current_snap_angle(
          AngleRestriction.FORTYFIVE_DEGREE);
    }
  }

  private class SnapAngleNoneListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.set_current_snap_angle(AngleRestriction.NONE);
    }
  }

  private class DynamicRouteListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_stitch_route(false);
    }
  }

  private class StitchRouteListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_stitch_route(true);
    }
  }

  private class DetailListener implements ActionListener {
    private boolean first_time = true;

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (first_time) {
        Point location = getLocation();
        detail_window.setLocation((int) location.getX() + 200, (int) location.getY() + 300);
        first_time = false;
      }
      detail_window.setVisible(true);
    }
  }

  private class AutomaticTraceWidthListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      manual_rule_window.setVisible(false);
      board_handling.settings.set_manual_tracewidth_selection(false);
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
      board_handling.settings.set_manual_tracewidth_selection(true);
    }
  }

  private class ShoveListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_push_enabled(settings_routing_shove_check_box.isSelected());
      refresh();
    }
  }

  private class ViaSnapToSMDCenterListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_via_snap_to_smd_center(
          settings_routing_via_snap_to_smd_center_check_box.isSelected());
    }
  }

  private class IgnoreConductionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.set_ignore_conduction(settings_routing_ignore_conduction_check_box.isSelected());
    }
  }

  private class HilightObstacleListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_hilight_routing_obstacle(
          settings_routing_hilight_routing_obstacle_check_box.isSelected());
    }
  }

  private class DragComponentListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_drag_components_enabled(settings_routing_drag_component_check_box.isSelected());
      refresh();
    }
  }

  private class NeckDownListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_automatic_neckdown(settings_routing_neckdown_check_box.isSelected());
    }
  }

  private class RestrictPinExitDirectionsListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (settings_routing_restrict_pin_exit_directions_check_box.isSelected()) {
        BoardRules board_rules = board_handling.get_routing_board().rules;
        double edge_to_turn_dist =
            board_handling.coordinate_transform.board_to_user(
                board_rules.get_min_trace_half_width());
        board_handling.set_pin_edge_to_turn_dist(edge_to_turn_dist);
      } else {
        board_handling.set_pin_edge_to_turn_dist(0);
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
        board_handling.set_pin_edge_to_turn_dist(input_value);
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
        double edge_to_turn_dist =
            board_handling.get_routing_board().rules.get_pin_edge_to_turn_dist();
        edge_to_turn_dist = board_handling.coordinate_transform.board_to_user(edge_to_turn_dist);
        edge_to_turn_dist_field.setValue(edge_to_turn_dist);
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
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
        int input_value = ((Number) input).intValue();
        set_pull_tight_region_width(input_value);
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
        region_width_field.setValue(region_slider.getValue());
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class SliderChangeListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent evt) {
      set_pull_tight_region_width(region_slider.getValue());
    }
  }
}
