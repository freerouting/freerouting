package app.freerouting.gui;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Trace;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveSettings;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.BoardRules;
import java.awt.Dimension;
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
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Window handling parameters of the interactive routing. */
public class WindowRouteParameter extends BoardSavableSubWindow {

  private static final int c_region_max_slider_value = 999;
  private static final int c_region_scale_factor = 200;
  private static final int c_accuracy_max_slider_value = 100;
  private static final int c_accuracy_scale_factor = 20;
  final WindowManualRules manualRuleWindow;
  private final GuiBoardManager guiBoardManager;
  private final JSlider regionSlider;
  private final JFormattedTextField regionWidthField;
  private final JFormattedTextField edgeToTurnDistField;
  private final JLabel edgeToTurnSuffixLabel;
  private final JLabel regionSuffixLabel;
  private final JLabel accuracySuffixLabel;
  private final JRadioButton settingsRoutingSnapAngle90Button;
  private final JRadioButton settingsRoutingSnapAngle45Button;
  private final JRadioButton settingsRoutingSnapAngleNoneButton;
  private final JRadioButton settingsRoutingDynamicButton;
  private final JRadioButton settingsRoutingStitchButton;
  private final JRadioButton settingsRoutingAutomaticButton;
  private final JRadioButton settingsRoutingManualButton;
  private final JCheckBox settingsRoutingShoveCheckBox;
  private final JCheckBox settingsRoutingDragComponentCheckBox;
  private final JCheckBox settingsRoutingIgnoreConductionCheckBox;
  private final JCheckBox settingsRoutingViaSnapToSmdCenterCheckBox;
  private final JCheckBox settingsRoutingHilightRoutingObstacleCheckBox;
  private final JCheckBox settingsRoutingNeckdownCheckBox;
  private final JCheckBox settingsRoutingRestrictPinExitDirectionsCheckBox;
  private final ManualTraceWidthListener manualTraceWidthListener;
  private final JSlider accuracySlider;
  private final JFormattedTextField accuracyValueField;
  private final JCheckBox clearanceCompensationCheckBox;
  private final JFormattedTextField clearanceValueField;
  private final JLabel clearanceSuffixLabel;
  private final JCheckBox routeDetailOutlineKeepoutCheckBox;
  private boolean updatingControls;
  private boolean keyInputCompleted = true;

  /** Creates a new instance of RouteParameterWindow */
  public WindowRouteParameter(BoardFrame p_board_frame) {
    this.guiBoardManager = p_board_frame.boardPanel.boardHandling;
    this.manualRuleWindow = new WindowManualRules(p_board_frame);

    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("title"));

    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    // create main panel
    final JPanel mainPanel = new JPanel();
    getContentPane().add(mainPanel);
    GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.anchor = GridBagConstraints.WEST;
    gridbagConstraints.insets = new Insets(1, 10, 1, 10);
    gridbagConstraints.weightx = 0.0; // Prevents the window from expanding infinitely

    // add label and radio buttons for the route snap angle.
    JLabel snapAngleLabel = new JLabel(tm.getText("snapAngle"));
    snapAngleLabel.setToolTipText(tm.getText("snap_angle_tooltip"));

    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 3;
    gridbag.setConstraints(snapAngleLabel, gridbagConstraints);
    mainPanel.add(snapAngleLabel);

    settingsRoutingSnapAngle90Button = new JRadioButton(tm.getText("90_degree"));
    settingsRoutingSnapAngle45Button = new JRadioButton(tm.getText("45_degree"));
    settingsRoutingSnapAngleNoneButton = new JRadioButton(tm.getText("none"));

    settingsRoutingSnapAngle90Button.addActionListener(new SnapAngle90Listener());
    settingsRoutingSnapAngle90Button.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingSnapAngle90Button", settingsRoutingSnapAngle90Button.getText()));
    settingsRoutingSnapAngle45Button.addActionListener(new SnapAngle45Listener());
    settingsRoutingSnapAngle45Button.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingSnapAngle45Button", settingsRoutingSnapAngle45Button.getText()));
    settingsRoutingSnapAngleNoneButton.addActionListener(new SnapAngleNoneListener());
    settingsRoutingSnapAngleNoneButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingSnapAngleNoneButton",
                settingsRoutingSnapAngleNoneButton.getText()));

    ButtonGroup snapAngleButtonGroup = new ButtonGroup();
    snapAngleButtonGroup.add(settingsRoutingSnapAngle90Button);
    snapAngleButtonGroup.add(settingsRoutingSnapAngle45Button);
    snapAngleButtonGroup.add(settingsRoutingSnapAngleNoneButton);
    settingsRoutingSnapAngleNoneButton.setSelected(true);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;
    gridbag.setConstraints(settingsRoutingSnapAngle90Button, gridbagConstraints);
    mainPanel.add(settingsRoutingSnapAngle90Button);
    gridbag.setConstraints(settingsRoutingSnapAngle45Button, gridbagConstraints);
    mainPanel.add(settingsRoutingSnapAngle45Button);
    gridbag.setConstraints(settingsRoutingSnapAngleNoneButton, gridbagConstraints);
    mainPanel.add(settingsRoutingSnapAngleNoneButton);

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // add label and radio buttons for the route mode.
    JLabel routeModeLabel = new JLabel(tm.getText("route_mode"));
    routeModeLabel.setToolTipText(tm.getText("route_mode_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 2;
    gridbag.setConstraints(routeModeLabel, gridbagConstraints);
    mainPanel.add(routeModeLabel);

    this.settingsRoutingDynamicButton = new JRadioButton(tm.getText("dynamic"));
    this.settingsRoutingDynamicButton.setToolTipText(tm.getText("dynamic_tooltip"));
    this.settingsRoutingStitchButton = new JRadioButton(tm.getText("stitching"));
    this.settingsRoutingStitchButton.setToolTipText(tm.getText("stitching_tooltip"));

    settingsRoutingDynamicButton.addActionListener(new DynamicRouteListener());
    settingsRoutingDynamicButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingDynamicButton", settingsRoutingDynamicButton.getText()));
    settingsRoutingStitchButton.addActionListener(new StitchRouteListener());
    settingsRoutingStitchButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingStitchButton", settingsRoutingStitchButton.getText()));

    ButtonGroup routeModeButtonGroup = new ButtonGroup();
    routeModeButtonGroup.add(settingsRoutingDynamicButton);
    routeModeButtonGroup.add(settingsRoutingStitchButton);
    settingsRoutingDynamicButton.setSelected(true);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;
    gridbag.setConstraints(settingsRoutingDynamicButton, gridbagConstraints);
    mainPanel.add(settingsRoutingDynamicButton);
    gridbag.setConstraints(settingsRoutingStitchButton, gridbagConstraints);
    mainPanel.add(settingsRoutingStitchButton);

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // add label and radio buttons for automatic or manual trace width selection.
    JLabel traceWidthsLabel = new JLabel(tm.getText("rule_selection"));
    traceWidthsLabel.setToolTipText(tm.getText("rule_selection_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 2;
    gridbag.setConstraints(traceWidthsLabel, gridbagConstraints);
    mainPanel.add(traceWidthsLabel);

    this.settingsRoutingAutomaticButton = new JRadioButton(tm.getText("automatic"));
    this.settingsRoutingAutomaticButton.setToolTipText(tm.getText("automatic_tooltip"));
    settingsRoutingManualButton = new JRadioButton(tm.getText("manual"));
    settingsRoutingManualButton.setToolTipText(tm.getText("manual_tooltip"));

    settingsRoutingAutomaticButton.addActionListener(new AutomaticTraceWidthListener());
    settingsRoutingAutomaticButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingAutomaticButton", settingsRoutingAutomaticButton.getText()));
    manualTraceWidthListener = new ManualTraceWidthListener();
    settingsRoutingManualButton.addActionListener(manualTraceWidthListener);
    settingsRoutingManualButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingManualButton", settingsRoutingManualButton.getText()));

    ButtonGroup traceWidthsButtonGroup = new ButtonGroup();
    traceWidthsButtonGroup.add(settingsRoutingAutomaticButton);
    traceWidthsButtonGroup.add(settingsRoutingManualButton);
    settingsRoutingAutomaticButton.setSelected(true);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;
    gridbag.setConstraints(settingsRoutingAutomaticButton, gridbagConstraints);
    mainPanel.add(settingsRoutingAutomaticButton);
    gridbag.setConstraints(settingsRoutingManualButton, gridbagConstraints);
    mainPanel.add(settingsRoutingManualButton);

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // add check boxes
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;

    settingsRoutingShoveCheckBox = new JCheckBox(tm.getText("push&shoveEnabled"));
    settingsRoutingShoveCheckBox.addActionListener(new ShoveListener());
    settingsRoutingShoveCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingShoveCheckBox", settingsRoutingShoveCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingShoveCheckBox, gridbagConstraints);
    settingsRoutingShoveCheckBox.setToolTipText(tm.getText("push&shove_enabled_tooltip"));
    mainPanel.add(settingsRoutingShoveCheckBox, gridbagConstraints);

    settingsRoutingDragComponentCheckBox = new JCheckBox(tm.getText("dragComponentsEnabled"));
    settingsRoutingDragComponentCheckBox.addActionListener(new DragComponentListener());
    settingsRoutingDragComponentCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingDragComponentCheckBox",
                settingsRoutingDragComponentCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingDragComponentCheckBox, gridbagConstraints);
    settingsRoutingDragComponentCheckBox.setToolTipText(
        tm.getText("drag_components_enabled_tooltip"));
    mainPanel.add(settingsRoutingDragComponentCheckBox, gridbagConstraints);

    settingsRoutingViaSnapToSmdCenterCheckBox = new JCheckBox(tm.getText("viaSnapToSmdCenter"));
    settingsRoutingViaSnapToSmdCenterCheckBox.addActionListener(new ViaSnapToSMDCenterListener());
    settingsRoutingViaSnapToSmdCenterCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingViaSnapToSmdCenterCheckBox",
                settingsRoutingViaSnapToSmdCenterCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingViaSnapToSmdCenterCheckBox, gridbagConstraints);
    settingsRoutingViaSnapToSmdCenterCheckBox.setToolTipText(
        tm.getText("via_snap_to_smd_center_tooltip"));
    mainPanel.add(settingsRoutingViaSnapToSmdCenterCheckBox, gridbagConstraints);

    settingsRoutingHilightRoutingObstacleCheckBox =
        new JCheckBox(tm.getText("hilightRoutingObstacle"));
    settingsRoutingHilightRoutingObstacleCheckBox.addActionListener(new HilightObstacleListener());
    settingsRoutingHilightRoutingObstacleCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingHilightRoutingObstacleCheckBox",
                settingsRoutingHilightRoutingObstacleCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingHilightRoutingObstacleCheckBox, gridbagConstraints);
    settingsRoutingHilightRoutingObstacleCheckBox.setToolTipText(
        tm.getText("hilight_routing_obstacle_tooltip"));
    mainPanel.add(settingsRoutingHilightRoutingObstacleCheckBox, gridbagConstraints);

    settingsRoutingIgnoreConductionCheckBox = new JCheckBox(tm.getText("ignore_conduction_areas"));
    settingsRoutingIgnoreConductionCheckBox.addActionListener(new IgnoreConductionListener());
    settingsRoutingIgnoreConductionCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingIgnoreConductionCheckBox",
                settingsRoutingIgnoreConductionCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingIgnoreConductionCheckBox, gridbagConstraints);
    settingsRoutingIgnoreConductionCheckBox.setToolTipText(
        tm.getText("ignore_conduction_areas_tooltip"));
    mainPanel.add(settingsRoutingIgnoreConductionCheckBox, gridbagConstraints);

    settingsRoutingNeckdownCheckBox = new JCheckBox(tm.getText("automaticNeckdown"));
    settingsRoutingNeckdownCheckBox.addActionListener(new NeckDownListener());
    settingsRoutingNeckdownCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingNeckdownCheckBox", settingsRoutingNeckdownCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingNeckdownCheckBox, gridbagConstraints);
    settingsRoutingNeckdownCheckBox.setToolTipText(tm.getText("automatic_neckdown_tooltip"));
    mainPanel.add(settingsRoutingNeckdownCheckBox, gridbagConstraints);

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // Restrict pin exit direction and Pad to Turn Gap
    settingsRoutingRestrictPinExitDirectionsCheckBox =
        new JCheckBox(tm.getText("restrict_pin_exit_directions"));
    settingsRoutingRestrictPinExitDirectionsCheckBox.addActionListener(
        new RestrictPinExitDirectionsListener());
    settingsRoutingRestrictPinExitDirectionsCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingRestrictPinExitDirectionsCheckBox",
                settingsRoutingRestrictPinExitDirectionsCheckBox.getText()));
    gridbag.setConstraints(settingsRoutingRestrictPinExitDirectionsCheckBox, gridbagConstraints);
    settingsRoutingRestrictPinExitDirectionsCheckBox.setToolTipText(
        tm.getText("restrict_pin_exit_directions_tooltip"));
    mainPanel.add(settingsRoutingRestrictPinExitDirectionsCheckBox, gridbagConstraints);

    JLabel pinExitEdgeToTurnLabel = new JLabel(tm.getText("pin_pad_to_turn_gap"));
    pinExitEdgeToTurnLabel.setToolTipText(tm.getText("pin_pad_to_turn_gap_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(pinExitEdgeToTurnLabel, gridbagConstraints);
    mainPanel.add(pinExitEdgeToTurnLabel);

    NumberFormat numberFormat = NumberFormat.getNumberInstance(p_board_frame.get_locale());
    numberFormat.setMaximumFractionDigits(3);
    numberFormat.setGroupingUsed(false);
    this.edgeToTurnDistField = new JFormattedTextField(numberFormat);
    this.edgeToTurnDistField.setColumns(6);
    this.edgeToTurnDistField.setToolTipText(tm.getText("pin_pad_to_turn_gap_tooltip"));

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.NONE;
    this.edgeToTurnSuffixLabel =
        new JLabel(this.guiBoardManager.coordinateTransform.userUnit.toString());
    mainPanel.add(
        createFieldWithSuffix(edgeToTurnDistField, edgeToTurnSuffixLabel, 4, 6),
        gridbagConstraints);
    edgeToTurnDistField.addKeyListener(new EdgeToTurnDistFieldKeyListener());
    edgeToTurnDistField.addFocusListener(new EdgeToTurnDistFieldFocusListener());

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // Pull-tight radius (search distance around cursor for trace cleanup)
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.NONE;
    gridbagConstraints.insets = new Insets(3, 10, 3, 10);
    JLabel pullTightRegionLabel = new JLabel(tm.getText("pullTightRegion"));
    pullTightRegionLabel.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    gridbag.setConstraints(pullTightRegionLabel, gridbagConstraints);
    mainPanel.add(pullTightRegionLabel);
    gridbagConstraints.insets = new Insets(1, 10, 1, 10);

    NumberFormat userUnitFormat = NumberFormat.getNumberInstance(p_board_frame.get_locale());
    userUnitFormat.setMaximumFractionDigits(3);
    userUnitFormat.setGroupingUsed(false);

    this.regionSlider = new JSlider();
    regionSlider.setMaximum(c_region_max_slider_value);
    regionSlider.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    regionSlider.addChangeListener(new SliderChangeListener());
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(regionSlider, gridbagConstraints);
    mainPanel.add(regionSlider);

    this.regionWidthField = new JFormattedTextField(userUnitFormat);
    this.regionWidthField.setColumns(6);
    this.regionWidthField.setToolTipText(tm.getText("pull_tight_region_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.NONE;

    this.regionSuffixLabel =
        new JLabel(this.guiBoardManager.coordinateTransform.userUnit.toString());
    mainPanel.add(
        createFieldWithSuffix(regionWidthField, regionSuffixLabel, 4, 6), gridbagConstraints);
    regionWidthField.addKeyListener(new RegionWidthFieldKeyListener());
    regionWidthField.addFocusListener(new RegionWidthFieldFocusListener());

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // Clearance compensation
    clearanceCompensationCheckBox = new JCheckBox(tm.getText("clearance_compensation_checkbox"));
    clearanceCompensationCheckBox.setSelected(false);
    clearanceCompensationCheckBox.setToolTipText(
        tm.getText("clearance_compensation_checkbox_tooltip"));
    clearanceCompensationCheckBox.addActionListener(
        new WindowRouteParameter.CompensationCheckboxListener());
    clearanceCompensationCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "clearanceCompensationCheckBox", clearanceCompensationCheckBox.getText()));

    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(clearanceCompensationCheckBox, gridbagConstraints);
    mainPanel.add(clearanceCompensationCheckBox, gridbagConstraints);

    NumberFormat compFormat = NumberFormat.getNumberInstance(p_board_frame.get_locale());
    compFormat.setMaximumFractionDigits(3);
    compFormat.setGroupingUsed(false);

    this.clearanceValueField = new JFormattedTextField(compFormat);
    this.clearanceValueField.setColumns(6);
    this.clearanceValueField.setEditable(false);
    this.clearanceValueField.setToolTipText(tm.getText("clearance_compensation_checkbox_tooltip"));

    this.clearanceSuffixLabel =
        new JLabel(this.guiBoardManager.coordinateTransform.userUnit.toString());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.NONE;
    mainPanel.add(
        createFieldWithSuffix(clearanceValueField, clearanceSuffixLabel, 4, 6), gridbagConstraints);

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // Pull tight accuracy
    JLabel pullTightAccuracyLabel = new JLabel(tm.getText("pullTightAccuracy"));
    pullTightAccuracyLabel.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.insets = new Insets(3, 10, 3, 10);
    gridbagConstraints.fill = GridBagConstraints.NONE;
    gridbag.setConstraints(pullTightAccuracyLabel, gridbagConstraints);
    mainPanel.add(pullTightAccuracyLabel);
    gridbagConstraints.insets = new Insets(1, 10, 1, 10);

    this.accuracySlider = new JSlider();
    accuracySlider.setMaximum(c_accuracy_max_slider_value);
    accuracySlider.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    accuracySlider.addChangeListener(new WindowRouteParameter.AccuracySliderChangeListener());
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(accuracySlider, gridbagConstraints);
    mainPanel.add(accuracySlider);

    this.accuracyValueField = new JFormattedTextField(userUnitFormat);
    this.accuracyValueField.setColumns(6);
    accuracyValueField.setToolTipText(tm.getText("pull_tight_accuracy_tooltip"));
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.NONE;

    this.accuracySuffixLabel =
        new JLabel(this.guiBoardManager.coordinateTransform.userUnit.toString());
    mainPanel.add(
        createFieldWithSuffix(accuracyValueField, accuracySuffixLabel, 4, 6), gridbagConstraints);
    accuracyValueField.addKeyListener(new AccuracyFieldKeyListener());
    accuracyValueField.addFocusListener(new AccuracyFieldFocusListener());

    addSeparator(mainPanel, gridbag, gridbagConstraints);

    // Outline Keepout
    routeDetailOutlineKeepoutCheckBox = new JCheckBox(tm.getText("keepoutOutsideOutline"));
    routeDetailOutlineKeepoutCheckBox.setSelected(false);
    routeDetailOutlineKeepoutCheckBox.addActionListener(
        new WindowRouteParameter.OutLineKeepoutListener());
    routeDetailOutlineKeepoutCheckBox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "routeDetailOutlineKeepoutCheckBox", routeDetailOutlineKeepoutCheckBox.getText()));
    gridbag.setConstraints(routeDetailOutlineKeepoutCheckBox, gridbagConstraints);
    routeDetailOutlineKeepoutCheckBox.setToolTipText(tm.getText("keepout_outside_outline_tooltip"));
    mainPanel.add(routeDetailOutlineKeepoutCheckBox, gridbagConstraints);

    this.refresh();
    this.pack();
    this.setResizable(false);

    InteractiveSettings is = this.guiBoardManager.getInteractiveSettings();
    if (is != null) {
      is.addPropertyChangeListener(_ -> javax.swing.SwingUtilities.invokeLater(this::refresh));
    }
  }

  // Inject standard JSeparators keeping sizing under control
  private void addSeparator(JPanel panel, GridBagLayout gridbag, GridBagConstraints constraints) {
    int oldGridWidth = constraints.gridwidth;
    int oldGridHeight = constraints.gridheight;
    double oldWeightX = constraints.weightx;
    int oldFill = constraints.fill;
    Insets oldInsets = constraints.insets;

    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridheight = 1;
    constraints.weightx = 0.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(6, 10, 6, 10);

    JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
    gridbag.setConstraints(separator, constraints);
    panel.add(separator);

    constraints.gridwidth = oldGridWidth;
    constraints.gridheight = oldGridHeight;
    constraints.weightx = oldWeightX;
    constraints.fill = oldFill;
    constraints.insets = oldInsets;
  }

  @Override
  public void dispose() {
    manualRuleWindow.dispose();
    super.dispose();
  }

  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    boolean readOk = super.read(p_object_stream);
    if (!readOk) {
      return false;
    }
    readOk = manualRuleWindow.read(p_object_stream);
    if (!readOk) {
      return false;
    }
    this.manualTraceWidthListener.firstTime = false;
    this.refresh();
    return true;
  }

  @Override
  public void save(ObjectOutputStream p_object_stream) {
    super.save(p_object_stream);
    manualRuleWindow.save(p_object_stream);
  }

  @Override
  public void refresh() {
    updatingControls = true;
    try {
      AngleRestriction snapAngle =
          this.guiBoardManager.get_routing_board().rules.get_trace_angle_restriction();

      if (snapAngle == AngleRestriction.NINETY_DEGREE) {
        settingsRoutingSnapAngle90Button.setSelected(true);
      } else if (snapAngle == AngleRestriction.FORTYFIVE_DEGREE) {
        settingsRoutingSnapAngle45Button.setSelected(true);
      } else {
        settingsRoutingSnapAngleNoneButton.setSelected(true);
      }

      if (this.guiBoardManager.getInteractiveSettings().get_is_stitch_route()) {
        settingsRoutingStitchButton.setSelected(true);
      } else {
        settingsRoutingDynamicButton.setSelected(true);
      }

      if (this.guiBoardManager.getInteractiveSettings().get_manual_rule_selection()) {
        settingsRoutingManualButton.setSelected(true);
        if (this.manualRuleWindow != null) {
          this.manualRuleWindow.setVisible(true);
        }
      } else {
        settingsRoutingAutomaticButton.setSelected(true);
      }

      this.settingsRoutingShoveCheckBox.setSelected(
          this.guiBoardManager.getInteractiveSettings().get_push_enabled());
      this.settingsRoutingDragComponentCheckBox.setSelected(
          this.guiBoardManager.getInteractiveSettings().get_drag_components_enabled());
      this.settingsRoutingViaSnapToSmdCenterCheckBox.setSelected(
          this.guiBoardManager.getInteractiveSettings().get_via_snap_to_smd_center());
      this.settingsRoutingIgnoreConductionCheckBox.setSelected(
          this.guiBoardManager.get_routing_board().rules.get_ignore_conduction());
      this.settingsRoutingHilightRoutingObstacleCheckBox.setSelected(
          this.guiBoardManager.getInteractiveSettings().get_hilight_routing_obstacle());
      this.settingsRoutingNeckdownCheckBox.setSelected(
          this.guiBoardManager.getInteractiveSettings().get_automatic_neckdown());

      double edgeToTurnDist =
          this.guiBoardManager.get_routing_board().rules.get_pin_edge_to_turn_dist();
      this.edgeToTurnDistField.setValue(
          this.guiBoardManager.coordinateTransform.board_to_user(edgeToTurnDist));
      this.settingsRoutingRestrictPinExitDirectionsCheckBox.setSelected(edgeToTurnDist > 0);

      int regionSliderValue =
          this.guiBoardManager.getInteractiveSettings().get_trace_pull_tight_region_width()
              / c_region_scale_factor;
      regionSliderValue = Math.min(regionSliderValue, c_region_max_slider_value);
      regionSlider.setValue(regionSliderValue);
      regionWidthField.setValue(
          this.guiBoardManager.coordinateTransform.board_to_user(
              regionSliderValue * c_region_scale_factor));

      if (this.manualRuleWindow != null) {
        this.manualRuleWindow.refresh();
      }

      this.edgeToTurnSuffixLabel.setText(
          this.guiBoardManager.coordinateTransform.userUnit.toString());
      this.regionSuffixLabel.setText(this.guiBoardManager.coordinateTransform.userUnit.toString());

      boolean compUsed =
          this.guiBoardManager
              .get_routing_board()
              .searchTreeManager
              .is_clearance_compensation_used();
      this.clearanceCompensationCheckBox.setSelected(compUsed);
      int clearanceClass =
          Math.min(
              1,
              this.guiBoardManager.get_routing_board().rules.clearanceMatrix.get_class_count() - 1);
      double compensation = 0;
      if (clearanceClass >= 1) {
        int layer = this.guiBoardManager.getInteractiveSettings().get_layer();
        compensation =
            this.guiBoardManager
                .get_routing_board()
                .rules
                .clearanceMatrix
                .clearance_compensation_value(clearanceClass, layer);
      }
      BoardOutline outline = this.guiBoardManager.get_routing_board().get_outline();
      if (outline != null) {
        this.routeDetailOutlineKeepoutCheckBox.setSelected(
            outline.keepout_outside_outline_generated());
      }
      int accuracySliderValue =
          c_accuracy_max_slider_value
              - this.guiBoardManager.getInteractiveSettings().get_trace_pull_tight_accuracy()
                  / c_accuracy_scale_factor
              + 1;
      accuracySlider.setValue(accuracySliderValue);
      int accuracyBoardValue =
          (c_accuracy_max_slider_value - accuracySliderValue + 1) * c_accuracy_scale_factor;
      accuracyValueField.setValue(
          this.guiBoardManager.coordinateTransform.board_to_user(accuracyBoardValue));
      updateUserUnitSuffixLabels();

      double appliedCompensation = compUsed ? compensation : 0;
      this.clearanceCompensationCheckBox.setText(tm.getText("clearance_compensation_checkbox"));
      this.clearanceValueField.setValue(
          this.guiBoardManager.coordinateTransform.board_to_user(appliedCompensation));
      this.clearanceSuffixLabel.setText(
          this.guiBoardManager.coordinateTransform.userUnit.toString());

      updateDynamicTooltips();
    } finally {
      updatingControls = false;
    }
  }

  @Override
  public void parent_iconified() {
    manualRuleWindow.parent_iconified();
    super.parent_iconified();
  }

  @Override
  public void parent_deiconified() {
    manualRuleWindow.parent_deiconified();
    super.parent_deiconified();
  }

  private void set_pull_tight_region_width(int p_slider_value) {
    int sliderValue = Math.max(p_slider_value, 0);
    sliderValue = Math.min(sliderValue, c_region_max_slider_value);
    int newTidyWidth;
    if (sliderValue >= c_region_max_slider_value) {
      sliderValue = c_region_max_slider_value;
      newTidyWidth = Integer.MAX_VALUE;
    } else {
      newTidyWidth = sliderValue * c_region_scale_factor;
    }
    regionSlider.setValue(sliderValue);
    regionWidthField.setValue(this.guiBoardManager.coordinateTransform.board_to_user(newTidyWidth));
    guiBoardManager.getInteractiveSettings().set_current_pull_tight_region_width(newTidyWidth);
  }

  private void updateDynamicTooltips() {
    double edgeToTurnDist =
        this.guiBoardManager.get_routing_board().rules.get_pin_edge_to_turn_dist();
    this.edgeToTurnDistField.setToolTipText(
        tm.getText("pin_pad_to_turn_gap_tooltip_current", formatUserDistance(edgeToTurnDist)));

    int regionWidth =
        this.guiBoardManager.getInteractiveSettings().get_trace_pull_tight_region_width();
    String regionTooltip;
    if (regionWidth >= Integer.MAX_VALUE) {
      regionTooltip = tm.getText("pull_tight_region_tooltip_whole_board");
    } else {
      regionTooltip =
          tm.getText("pull_tight_region_tooltip_radius", formatUserDistance(regionWidth));
    }
    this.regionSlider.setToolTipText(regionTooltip);
    this.regionWidthField.setToolTipText(regionTooltip);
    this.regionSuffixLabel.setToolTipText(regionTooltip);

    int accSliderVal = accuracySlider.getValue();
    int accBoardValue = (c_accuracy_max_slider_value - accSliderVal + 1) * c_accuracy_scale_factor;
    String accUser = formatUserDistance(accBoardValue);
    String accuracyTooltip = tm.getText("pull_tight_accuracy_tooltip_current", accUser);
    this.accuracySlider.setToolTipText(accuracyTooltip);
    this.accuracyValueField.setToolTipText(accuracyTooltip);
    this.accuracySuffixLabel.setToolTipText(accuracyTooltip);

    int clearanceClass =
        Math.min(
            1,
            this.guiBoardManager.get_routing_board().rules.clearanceMatrix.get_class_count() - 1);
    double compensation = 0;
    if (clearanceClass >= 1) {
      int layer = this.guiBoardManager.getInteractiveSettings().get_layer();
      compensation =
          this.guiBoardManager
              .get_routing_board()
              .rules
              .clearanceMatrix
              .clearance_compensation_value(clearanceClass, layer);
    }
    this.clearanceCompensationCheckBox.setToolTipText(
        tm.getText(
            "clearance_compensation_checkbox_tooltip_current", formatUserDistance(compensation)));
  }

  private String formatUserDistance(double boardValue) {
    NumberFormat format = NumberFormat.getNumberInstance(this.getLocale());
    format.setMaximumFractionDigits(3);
    return format.format(this.guiBoardManager.coordinateTransform.board_to_user(boardValue))
        + " "
        + this.guiBoardManager.coordinateTransform.userUnit;
  }

  private void updateUserUnitSuffixLabels() {
    String unit = this.guiBoardManager.coordinateTransform.userUnit.toString();
    this.edgeToTurnSuffixLabel.setText(unit);
    this.regionSuffixLabel.setText(unit);
    this.accuracySuffixLabel.setText(unit);
    this.clearanceSuffixLabel.setText(unit);
  }

  private JPanel createFieldWithSuffix(
      JFormattedTextField field, JLabel suffixLabel, int suffixPadLeft, int suffixPadRight) {
    Border border = field.getBorder();
    field.setBorder(
        BorderFactory.createCompoundBorder(
            border, BorderFactory.createEmptyBorder(0, 0, 0, suffixPadLeft + suffixPadRight)));

    suffixLabel.setOpaque(true);
    suffixLabel.setBackground(field.getBackground());
    suffixLabel.setBorder(BorderFactory.createEmptyBorder(0, suffixPadLeft, 0, suffixPadRight));
    suffixLabel.setHorizontalAlignment(JLabel.RIGHT);
    suffixLabel.setVerticalAlignment(JLabel.CENTER);

    JPanel wrapper = new JPanel(new java.awt.BorderLayout());
    wrapper.setBorder(border);
    wrapper.add(field, java.awt.BorderLayout.CENTER);
    wrapper.add(suffixLabel, java.awt.BorderLayout.EAST);

    // Enforce a minimum width so it doesn't shrink when layout unit strings update
    Dimension minSize = wrapper.getPreferredSize();
    minSize.width = Math.max(minSize.width, 110);
    wrapper.setMinimumSize(minSize);
    wrapper.setPreferredSize(minSize);

    return wrapper;
  }

  /**
   * Applies the stitch route selection to the given interactive settings. Used by unit tests to
   * verify the stitch route behavior.
   */
  public static void applyStitchRouteSelection(
      InteractiveSettings p_interactive_settings, boolean p_value) {
    p_interactive_settings.set_stitch_route(p_value);
  }

  /**
   * Applies the push and shove selection to the given interactive settings. Used by unit tests to
   * verify the push and shove behavior.
   */
  public static void applyPushAndShoveSelection(
      InteractiveSettings p_interactive_settings, boolean p_value) {
    p_interactive_settings.set_push_enabled(p_value);
  }

  /**
   * Applies the ignore conduction selection to the given board manager. Used by unit tests to
   * verify the ignore conduction behavior.
   */
  public static void applyIgnoreConductionSelection(
      GuiBoardManager p_board_manager, boolean p_value) {
    p_board_manager.set_ignore_conduction(p_value);
  }

  /**
   * Applies the clearance compensation selection to the given board manager. Used by unit tests to
   * verify the clearance compensation behavior.
   */
  public static void applyClearanceCompensationSelection(
      GuiBoardManager p_board_manager, boolean p_value) {
    p_board_manager.set_clearance_compensation(p_value);
  }

  /**
   * Applies the pin exit edge to turn distance to the given board manager. Used by unit tests to
   * verify the pin exit edge to turn distance behavior.
   */
  public static void applyPinExitEdgeToTurnDistance(
      GuiBoardManager p_board_manager, float p_value) {
    p_board_manager.set_pin_edge_to_turn_dist(p_value);
  }

  private class SnapAngle90Listener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (guiBoardManager.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.NINETY_DEGREE) {
        return;
      }
      Collection<Trace> traceList = guiBoardManager.get_routing_board().get_traces();
      boolean freeAngleTracesFound = false;
      for (Trace currTrace : traceList) {
        if (currTrace instanceof PolylineTrace trace) {
          if (!trace.polyline().is_orthogonal()) {
            freeAngleTracesFound = true;
            break;
          }
        }
      }
      if (freeAngleTracesFound) {
        String currMessage = tm.getText("change_snap_angle_90");
        if (!WindowMessage.confirm(currMessage)) {
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
      if (guiBoardManager.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.FORTYFIVE_DEGREE) {
        return;
      }
      Collection<Trace> traceList = guiBoardManager.get_routing_board().get_traces();
      boolean freeAngleTracesFound = false;
      for (Trace currTrace : traceList) {
        if (currTrace instanceof PolylineTrace trace) {
          if (!trace.polyline().is_multiple_of_45_degree()) {
            freeAngleTracesFound = true;
            break;
          }
        }
      }
      if (freeAngleTracesFound) {
        String currMessage = tm.getText("change_snap_angle_45");
        if (!WindowMessage.confirm(currMessage)) {
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
      if (updatingControls) {
        return;
      }
      guiBoardManager.getInteractiveSettings().set_stitch_route(false);
    }
  }

  private class StitchRouteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager.getInteractiveSettings().set_stitch_route(true);
    }
  }

  private class AutomaticTraceWidthListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      manualRuleWindow.setVisible(false);
      guiBoardManager.getInteractiveSettings().set_manual_tracewidth_selection(false);
    }
  }

  private class ManualTraceWidthListener implements ActionListener {

    boolean firstTime = true;

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (firstTime) {
        Point location = getLocation();
        manualRuleWindow.setLocation((int) location.getX() + 200, (int) location.getY() + 200);
        firstTime = false;
      }
      manualRuleWindow.setVisible(true);
      guiBoardManager.getInteractiveSettings().set_manual_tracewidth_selection(true);
    }
  }

  private class ShoveListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager
          .getInteractiveSettings()
          .set_push_enabled(settingsRoutingShoveCheckBox.isSelected());
      refresh();
    }
  }

  private class ViaSnapToSMDCenterListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager
          .getInteractiveSettings()
          .set_via_snap_to_smd_center(settingsRoutingViaSnapToSmdCenterCheckBox.isSelected());
    }
  }

  private class IgnoreConductionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager.set_ignore_conduction(settingsRoutingIgnoreConductionCheckBox.isSelected());
    }
  }

  private class HilightObstacleListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager
          .getInteractiveSettings()
          .set_hilight_routing_obstacle(settingsRoutingHilightRoutingObstacleCheckBox.isSelected());
    }
  }

  private class DragComponentListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager
          .getInteractiveSettings()
          .set_drag_components_enabled(settingsRoutingDragComponentCheckBox.isSelected());
      refresh();
    }
  }

  private class NeckDownListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager
          .getInteractiveSettings()
          .set_automatic_neckdown(settingsRoutingNeckdownCheckBox.isSelected());
    }
  }

  private class RestrictPinExitDirectionsListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      if (settingsRoutingRestrictPinExitDirectionsCheckBox.isSelected()) {
        BoardRules boardRules = guiBoardManager.get_routing_board().rules;
        double edgeToTurnDist =
            guiBoardManager.coordinateTransform.board_to_user(
                boardRules.get_min_trace_half_width());
        guiBoardManager.set_pin_edge_to_turn_dist(edgeToTurnDist);
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
        keyInputCompleted = true;
        Object input = edgeToTurnDistField.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        float inputValue = ((Number) input).floatValue();
        guiBoardManager.set_pin_edge_to_turn_dist(inputValue);
        settingsRoutingRestrictPinExitDirectionsCheckBox.setSelected(inputValue > 0);
        refresh();
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class EdgeToTurnDistFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!keyInputCompleted) {
        double edgeToTurnDist =
            guiBoardManager.get_routing_board().rules.get_pin_edge_to_turn_dist();
        edgeToTurnDist = guiBoardManager.coordinateTransform.board_to_user(edgeToTurnDist);
        edgeToTurnDistField.setValue(edgeToTurnDist);
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class RegionWidthFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        keyInputCompleted = true;
        Object input = regionWidthField.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double userValue = Math.max(0.0, ((Number) input).doubleValue());
        double boardValue = guiBoardManager.coordinateTransform.user_to_board(userValue);
        int sliderValue = (int) Math.round(boardValue / c_region_scale_factor);
        set_pull_tight_region_width(sliderValue);
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class RegionWidthFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!keyInputCompleted) {
        regionWidthField.setValue(
            WindowRouteParameter.this.guiBoardManager.coordinateTransform.board_to_user(
                regionSlider.getValue() * c_region_scale_factor));
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class AccuracyFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        keyInputCompleted = true;
        Object input = accuracyValueField.getValue();
        if (!(input instanceof Number)) {
          return;
        }
        double userValue = Math.max(0.0, ((Number) input).doubleValue());
        double boardValue = guiBoardManager.coordinateTransform.user_to_board(userValue);
        int sliderValue =
            c_accuracy_max_slider_value
                - (int) Math.round(boardValue / c_accuracy_scale_factor)
                + 1;
        sliderValue = Math.max(0, Math.min(c_accuracy_max_slider_value, sliderValue));
        accuracySlider.setValue(sliderValue);
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class AccuracyFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!keyInputCompleted) {
        int accuracyBoardValue =
            (c_accuracy_max_slider_value - accuracySlider.getValue() + 1) * c_accuracy_scale_factor;
        accuracyValueField.setValue(
            guiBoardManager.coordinateTransform.board_to_user(accuracyBoardValue));
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class SliderChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent evt) {
      if (updatingControls) {
        return;
      }
      int sliderValue = regionSlider.getValue();
      regionWidthField.setValue(
          guiBoardManager.coordinateTransform.board_to_user(sliderValue * c_region_scale_factor));
      set_pull_tight_region_width(sliderValue);
    }
  }

  private class CompensationCheckboxListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (updatingControls) {
        return;
      }
      guiBoardManager.set_clearance_compensation(clearanceCompensationCheckBox.isSelected());
      refresh();
    }
  }

  private class AccuracySliderChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent evt) {
      if (updatingControls) {
        return;
      }
      int sliderValue = accuracySlider.getValue();
      int newAccuracy = (c_accuracy_max_slider_value - sliderValue + 1) * c_accuracy_scale_factor;
      accuracyValueField.setValue(guiBoardManager.coordinateTransform.board_to_user(newAccuracy));
      guiBoardManager.getInteractiveSettings().set_trace_pull_tight_accuracy(newAccuracy);
    }
  }

  private class OutLineKeepoutListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (guiBoardManager.is_board_read_only()) {
        return;
      }
      BoardOutline outline = guiBoardManager.get_routing_board().get_outline();
      if (outline != null) {
        outline.generate_keepout_outside(routeDetailOutlineKeepoutCheckBox.isSelected());
      }
    }
  }
}
