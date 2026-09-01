package app.freerouting.gui.windows.routing;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardSavableSubWindow;
import app.freerouting.gui.support.GuiTextManager;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.settings.RouterSettings;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Window handling parameters of the automatic routing. */
public class WindowAutorouteParameter extends BoardSavableSubWindow {

  private final GuiBoardManager boardHandling;
  private final JLabel[] layerNameArr;
  private final JLabel[] signalLayerNameArr;
  private final JCheckBox[] settingsAutorouterLayerActiveArr;
  private final List<JComboBox<String>> settingsAutorouterComboBoxArr;
  private final JCheckBox settingsAutorouterViasAllowed;
  private final JCheckBox settingsAutorouterFanoutButton;
  private final JCheckBox settingsAutorouterAutoroutePassButton;
  private final JCheckBox settingsAutorouterOptimizationButton;
  private final String horizontal;
  private final String vertical;
  private final JFormattedTextField viaCostField;
  private final JFormattedTextField planeViaCostField;
  private final JFormattedTextField startRipupCosts;
  private final JFormattedTextField maxPassesField;
  private final JPanel jobTimeoutPanel;
  private final JButton jobTimeoutDecrementButton;
  private final JFormattedTextField jobTimeoutValueField;
  private final JButton jobTimeoutIncrementButton;
  private final JComboBox<TimeoutUnitItem> jobTimeoutUnitComboBox;
  private final JFormattedTextField maxThreadsField;
  private final JFormattedTextField[] preferredDirectionTraceCostArr;
  private final JFormattedTextField[] againstPreferredDirectionTraceCostArr;
  private final JFormattedTextField[] bendCostArr;
  private final boolean[] preferredDirectionTraceCostsInputCompleted;
  private final boolean[] againstPreferredDirectionTraceCostsInputCompleted;
  private final boolean[] bendCostsInputCompleted;
  private GuiTextManager tm;
  private boolean viaCostInputCompleted = true;
  private boolean planeViaCostInputCompleted = true;
  private boolean startRipupCostInputCompleted = true;
  private boolean maxPassesInputCompleted = true;
  private boolean maxThreadsInputCompleted = true;
  // Flag to prevent circular updates between GUI and settings
  private boolean isUpdatingFromSettings;

  /** Creates a new instance of WindowAutorouteParameter. */
  public WindowAutorouteParameter(BoardFrame boardFrame) {
    setLanguage(boardFrame.getLocale());

    this.boardHandling = boardFrame.boardPanel.boardHandling;
    this.setTitle(tm.getText("title"));

    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    // create main panel

    final JPanel mainPanel = new JPanel();
    getContentPane().add(createScrollableContainer(mainPanel));
    GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);

    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.anchor = GridBagConstraints.WEST;
    gridbagConstraints.insets = new Insets(1, 10, 1, 10);

    gridbagConstraints.gridwidth = 3;
    JLabel layerLabel = new JLabel();
    tm.setText(layerLabel, "layer");
    gridbag.setConstraints(layerLabel, gridbagConstraints);
    mainPanel.add(layerLabel);

    JLabel activeLabel = new JLabel();
    tm.setText(activeLabel, "active");
    gridbag.setConstraints(activeLabel, gridbagConstraints);
    mainPanel.add(activeLabel);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    JLabel preferredDirectionLabel = new JLabel();
    tm.setText(preferredDirectionLabel, "preferred_direction");
    gridbag.setConstraints(preferredDirectionLabel, gridbagConstraints);
    mainPanel.add(preferredDirectionLabel);

    this.horizontal = tm.getText("horizontal");
    this.vertical = tm.getText("vertical");

    // create the layer list
    LayerStructure layerStructure = boardHandling.getRoutingBoard().layerStructure;
    int layerCount = layerStructure.layers.length;

    // every layer is a row in the gridbag and has 3 columns: name, active,
    // preferred direction
    layerNameArr = new JLabel[layerCount];
    settingsAutorouterLayerActiveArr = new JCheckBox[layerCount];
    settingsAutorouterComboBoxArr = new ArrayList<>(layerCount);

    for (int i = 0; i < layerCount; i++) {
      gridbagConstraints.gridwidth = 3;
      Layer currentLayer = layerStructure.layers[i];

      // set the name
      layerNameArr[i] = new JLabel();
      layerNameArr[i].setText(currentLayer.name);
      gridbag.setConstraints(layerNameArr[i], gridbagConstraints);
      mainPanel.add(layerNameArr[i]);

      // set the active checkbox
      settingsAutorouterLayerActiveArr[i] = new JCheckBox();
      settingsAutorouterLayerActiveArr[i].addActionListener(new LayerActiveListener(i));
      settingsAutorouterLayerActiveArr[i].addActionListener(
          _ -> FRAnalytics.buttonClicked("settingsAutorouterLayerActiveArr", null));
      settingsAutorouterLayerActiveArr[i].setEnabled(currentLayer.isSignal);
      if (!currentLayer.isSignal) {
        settingsAutorouterLayerActiveArr[i].setToolTipText(tm.getText("power_layer_tooltip"));
      }
      gridbag.setConstraints(settingsAutorouterLayerActiveArr[i], gridbagConstraints);
      mainPanel.add(settingsAutorouterLayerActiveArr[i]);

      // set the preferred direction combobox
      settingsAutorouterComboBoxArr.add(new JComboBox<>());
      settingsAutorouterComboBoxArr.get(i).addItem(this.horizontal);
      settingsAutorouterComboBoxArr.get(i).addItem(this.vertical);
      settingsAutorouterComboBoxArr.get(i).addActionListener(new PreferredDirectionListener(i));
      settingsAutorouterComboBoxArr.get(i).setEnabled(currentLayer.isSignal);
      gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridbag.setConstraints(settingsAutorouterComboBoxArr.get(i), gridbagConstraints);
      mainPanel.add(settingsAutorouterComboBoxArr.get(i));
    }

    JLabel separator =
        new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);
    gridbagConstraints.fill = GridBagConstraints.NONE;

    gridbagConstraints.gridwidth = 2;
    JLabel viasAllowedLabel = new JLabel();
    tm.setText(viasAllowedLabel, "viasAllowed");
    viasAllowedLabel.setToolTipText(tm.getText("vias_allowed_tooltip"));
    gridbag.setConstraints(viasAllowedLabel, gridbagConstraints);
    mainPanel.add(viasAllowedLabel);

    settingsAutorouterViasAllowed = new JCheckBox();
    settingsAutorouterViasAllowed.setToolTipText(tm.getText("vias_allowed_tooltip"));
    settingsAutorouterViasAllowed.addActionListener(new ViasAllowedListener());
    settingsAutorouterViasAllowed.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsAutorouterViasAllowed", settingsAutorouterViasAllowed.getText()));

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(settingsAutorouterViasAllowed, gridbagConstraints);
    mainPanel.add(settingsAutorouterViasAllowed);

    separator = new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);
    gridbagConstraints.fill = GridBagConstraints.NONE;

    JLabel stagesLabel = new JLabel();
    tm.setText(stagesLabel, "routing_stages");

    gridbagConstraints.gridwidth = 2;
    gridbagConstraints.gridheight = 3;
    gridbag.setConstraints(stagesLabel, gridbagConstraints);
    mainPanel.add(stagesLabel);

    this.settingsAutorouterFanoutButton = new JCheckBox();
    tm.setText(this.settingsAutorouterFanoutButton, "fanout");
    this.settingsAutorouterAutoroutePassButton = new JCheckBox();
    tm.setText(this.settingsAutorouterAutoroutePassButton, "autoroute");
    this.settingsAutorouterOptimizationButton = new JCheckBox();
    tm.setText(this.settingsAutorouterOptimizationButton, "optimization");

    settingsAutorouterFanoutButton.addActionListener(new FanoutListener());
    settingsAutorouterFanoutButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsAutorouterFanoutButton", settingsAutorouterFanoutButton.getText()));
    settingsAutorouterAutoroutePassButton.addActionListener(new AutorouteListener());
    settingsAutorouterAutoroutePassButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsAutorouterAutoroutePassButton",
                settingsAutorouterAutoroutePassButton.getText()));
    settingsAutorouterOptimizationButton.addActionListener(new OptimizationListener());
    settingsAutorouterOptimizationButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsAutorouterOptimizationButton",
                settingsAutorouterOptimizationButton.getText()));

    settingsAutorouterFanoutButton.setSelected(true);
    settingsAutorouterAutoroutePassButton.setSelected(true);
    settingsAutorouterOptimizationButton.setSelected(false);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;

    gridbag.setConstraints(settingsAutorouterFanoutButton, gridbagConstraints);
    mainPanel.add(settingsAutorouterFanoutButton, gridbagConstraints);
    gridbag.setConstraints(settingsAutorouterAutoroutePassButton, gridbagConstraints);
    mainPanel.add(settingsAutorouterAutoroutePassButton, gridbagConstraints);
    gridbag.setConstraints(settingsAutorouterOptimizationButton, gridbagConstraints);
    mainPanel.add(settingsAutorouterOptimizationButton, gridbagConstraints);

    separator = new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);
    gridbagConstraints.fill = GridBagConstraints.NONE;

    // add label and number field for the via costs.

    gridbagConstraints.gridwidth = 2;
    JLabel viaCostLabel = new JLabel();
    tm.setText(viaCostLabel, "viaCosts");
    gridbag.setConstraints(viaCostLabel, gridbagConstraints);
    mainPanel.add(viaCostLabel);

    NumberFormat numberFormat = NumberFormat.getIntegerInstance(boardFrame.getLocale());
    this.viaCostField = new JFormattedTextField(numberFormat);
    this.viaCostField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
    this.viaCostField.setColumns(3);
    this.viaCostField.setToolTipText(tm.getText("via_costs_tooltip"));
    this.viaCostField.addKeyListener(new WindowAutorouteParameter.ViaCostFieldKeyListener());
    this.viaCostField.addFocusListener(new WindowAutorouteParameter.ViaCostFieldFocusListener());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(viaCostField, gridbagConstraints);
    mainPanel.add(viaCostField);

    this.planeViaCostField = new JFormattedTextField(numberFormat);
    this.planeViaCostField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
    this.planeViaCostField.setColumns(3);
    this.planeViaCostField.addKeyListener(
        new WindowAutorouteParameter.PlaneViaCostFieldKeyListener());
    this.planeViaCostField.addFocusListener(
        new WindowAutorouteParameter.PlaneViaCostFieldFocusListener());

    gridbagConstraints.gridwidth = 2;
    JLabel planeViaCostLabel = new JLabel();
    tm.setText(planeViaCostLabel, "plane_via_costs");
    gridbag.setConstraints(planeViaCostLabel, gridbagConstraints);
    mainPanel.add(planeViaCostLabel);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    planeViaCostField.setToolTipText(tm.getText("plane_via_costs_tooltip"));
    gridbag.setConstraints(planeViaCostField, gridbagConstraints);
    mainPanel.add(planeViaCostField);

    // add label and number field for the start ripup costs.

    gridbagConstraints.gridwidth = 2;
    JLabel startRipupCostsLabel = new JLabel();
    tm.setText(startRipupCostsLabel, "startRipupCosts");
    gridbag.setConstraints(startRipupCostsLabel, gridbagConstraints);
    mainPanel.add(startRipupCostsLabel);

    startRipupCosts = new JFormattedTextField(numberFormat);
    startRipupCosts.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
    startRipupCosts.setColumns(3);
    startRipupCosts.setToolTipText(tm.getText("start_ripup_costs_tooltip"));
    this.startRipupCosts.addKeyListener(
        new WindowAutorouteParameter.StartRipupCostFieldKeyListener());
    this.startRipupCosts.addFocusListener(
        new WindowAutorouteParameter.StartRipupCostFieldFocusListener());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(startRipupCosts, gridbagConstraints);
    mainPanel.add(startRipupCosts);

    // add label and number field for max passes

    gridbagConstraints.gridwidth = 2;
    JLabel maxPassesLabel = new JLabel();
    tm.setText(maxPassesLabel, "max_passes");
    gridbag.setConstraints(maxPassesLabel, gridbagConstraints);
    mainPanel.add(maxPassesLabel);

    maxPassesField = new JFormattedTextField(numberFormat);
    maxPassesField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
    maxPassesField.setColumns(5);
    maxPassesField.setToolTipText(tm.getText("max_passes_tooltip"));
    this.maxPassesField.addKeyListener(new WindowAutorouteParameter.MaxPassesFieldKeyListener());
    this.maxPassesField.addFocusListener(
        new WindowAutorouteParameter.MaxPassesFieldFocusListener());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(maxPassesField, gridbagConstraints);
    mainPanel.add(maxPassesField);

    // add label and structured fields for job timeout

    gridbagConstraints.gridwidth = 2;
    JLabel jobTimeoutLabel = new JLabel();
    tm.setText(jobTimeoutLabel, "job_timeout");
    gridbag.setConstraints(jobTimeoutLabel, gridbagConstraints);
    mainPanel.add(jobTimeoutLabel);

    final NumberFormat timeoutValueFormat = new DecimalFormat("#0");

    this.jobTimeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    this.jobTimeoutPanel.setOpaque(false);
    this.jobTimeoutPanel.setToolTipText(tm.getText("job_timeout_tooltip"));

    Dimension btnSize = new Dimension(26, 24);
    this.jobTimeoutDecrementButton = new JButton("−");
    this.jobTimeoutDecrementButton.setPreferredSize(btnSize);
    this.jobTimeoutDecrementButton.setMargin(new Insets(0, 0, 0, 0));
    this.jobTimeoutDecrementButton.setFocusable(false);
    this.jobTimeoutDecrementButton.setToolTipText(tm.getText("job_timeout_tooltip"));
    this.jobTimeoutDecrementButton.addActionListener(e -> decrementJobTimeout());

    this.jobTimeoutValueField = new JFormattedTextField(timeoutValueFormat);
    this.jobTimeoutValueField.setFocusLostBehavior(JFormattedTextField.COMMIT);
    this.jobTimeoutValueField.setColumns(4);
    this.jobTimeoutValueField.setHorizontalAlignment(JFormattedTextField.CENTER);
    this.jobTimeoutValueField.setToolTipText(tm.getText("job_timeout_tooltip"));
    this.jobTimeoutValueField.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent event) {
            jobTimeoutValueField.selectAll();
          }

          @Override
          public void focusLost(FocusEvent event) {
            commitJobTimeoutEdit();
          }
        });
    this.jobTimeoutValueField.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent event) {
            if (event.getKeyChar() == '\n') {
              commitJobTimeoutEdit();
            }
          }
        });

    this.jobTimeoutIncrementButton = new JButton("+");
    this.jobTimeoutIncrementButton.setPreferredSize(btnSize);
    this.jobTimeoutIncrementButton.setMargin(new Insets(0, 0, 0, 0));
    this.jobTimeoutIncrementButton.setFocusable(false);
    this.jobTimeoutIncrementButton.setToolTipText(tm.getText("job_timeout_tooltip"));
    this.jobTimeoutIncrementButton.addActionListener(e -> incrementJobTimeout());

    this.jobTimeoutUnitComboBox = new JComboBox<>();
    this.jobTimeoutUnitComboBox.setToolTipText(tm.getText("job_timeout_tooltip"));
    populateTimeoutUnits();
    this.jobTimeoutUnitComboBox.addActionListener(e -> onTimeoutUnitChanged());

    this.jobTimeoutPanel.add(this.jobTimeoutDecrementButton);
    this.jobTimeoutPanel.add(this.jobTimeoutValueField);
    this.jobTimeoutPanel.add(this.jobTimeoutIncrementButton);
    this.jobTimeoutPanel.add(Box.createHorizontalStrut(4));
    this.jobTimeoutPanel.add(this.jobTimeoutUnitComboBox);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(jobTimeoutPanel, gridbagConstraints);
    mainPanel.add(jobTimeoutPanel);

    // add label and number field for max threads

    gridbagConstraints.gridwidth = 2;
    JLabel maxThreadsLabel = new JLabel();
    tm.setText(maxThreadsLabel, "max_threads");
    gridbag.setConstraints(maxThreadsLabel, gridbagConstraints);
    mainPanel.add(maxThreadsLabel);

    maxThreadsField = new JFormattedTextField(numberFormat);
    maxThreadsField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
    maxThreadsField.setColumns(3);
    maxThreadsField.setToolTipText(tm.getText("max_threads_tooltip"));
    this.maxThreadsField.addKeyListener(new WindowAutorouteParameter.MaxThreadsFieldKeyListener());
    this.maxThreadsField.addFocusListener(
        new WindowAutorouteParameter.MaxThreadsFieldFocusListener());
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(maxThreadsField, gridbagConstraints);
    mainPanel.add(maxThreadsField);

    JLabel separator2 =
        new JLabel("––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(separator2, gridbagConstraints);
    mainPanel.add(separator2, gridbagConstraints);
    gridbagConstraints.fill = GridBagConstraints.NONE;

    // add label and number field for the trace costs on each layer.

    gridbagConstraints.gridwidth = 3;
    JLabel traceCostsOnLayer = new JLabel();
    tm.setText(traceCostsOnLayer, "traceCostsOnLayer");
    gridbag.setConstraints(traceCostsOnLayer, gridbagConstraints);
    mainPanel.add(traceCostsOnLayer);

    gridbagConstraints.gridwidth = 3;
    javax.swing.JComponent prefDirLabel = createWordWrapLabel("in_preferred_direction", 80, 45);
    gridbag.setConstraints(prefDirLabel, gridbagConstraints);
    mainPanel.add(prefDirLabel);

    gridbagConstraints.gridwidth = 3;
    javax.swing.JComponent againstPrefDirLabel =
        createWordWrapLabel("against_preferred_direction", 80, 45);
    gridbag.setConstraints(againstPrefDirLabel, gridbagConstraints);
    mainPanel.add(againstPrefDirLabel);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    javax.swing.JComponent bendCostHeader = createWordWrapLabel("bend_cost", 80, 45);
    gridbag.setConstraints(bendCostHeader, gridbagConstraints);
    mainPanel.add(bendCostHeader);

    int signalLayerCount = layerStructure.signalLayerCount();
    signalLayerNameArr = new JLabel[signalLayerCount];
    preferredDirectionTraceCostArr = new JFormattedTextField[signalLayerCount];
    againstPreferredDirectionTraceCostArr = new JFormattedTextField[signalLayerCount];
    bendCostArr = new JFormattedTextField[signalLayerCount];
    preferredDirectionTraceCostsInputCompleted = new boolean[signalLayerCount];
    againstPreferredDirectionTraceCostsInputCompleted = new boolean[signalLayerCount];
    bendCostsInputCompleted = new boolean[signalLayerCount];
    numberFormat = NumberFormat.getInstance(boardFrame.getLocale());
    numberFormat.setMaximumFractionDigits(2);
    final int textFieldLength = 3;
    NumberFormat floatNumberFormat = new DecimalFormat("0.0");
    for (int i = 0; i < signalLayerCount; i++) {
      signalLayerNameArr[i] = new JLabel();
      Layer currentSignalLayer = layerStructure.getSignalLayer(i);
      signalLayerNameArr[i].setText(currentSignalLayer.name);
      gridbagConstraints.gridwidth = 3;
      gridbag.setConstraints(signalLayerNameArr[i], gridbagConstraints);
      mainPanel.add(signalLayerNameArr[i]);
      preferredDirectionTraceCostArr[i] = new JFormattedTextField(floatNumberFormat);
      preferredDirectionTraceCostArr[i].setHorizontalAlignment(javax.swing.JTextField.RIGHT);
      preferredDirectionTraceCostArr[i].setColumns(textFieldLength);
      preferredDirectionTraceCostArr[i].setPreferredSize(
          new Dimension(150, preferredDirectionTraceCostArr[i].getPreferredSize().height));
      preferredDirectionTraceCostArr[i].addKeyListener(
          new WindowAutorouteParameter.PreferredDirectionTraceCostKeyListener(i));
      preferredDirectionTraceCostArr[i].addFocusListener(
          new WindowAutorouteParameter.PreferredDirectionTraceCostFocusListener(i));
      gridbag.setConstraints(preferredDirectionTraceCostArr[i], gridbagConstraints);
      mainPanel.add(preferredDirectionTraceCostArr[i]);
      againstPreferredDirectionTraceCostArr[i] = new JFormattedTextField(floatNumberFormat);
      againstPreferredDirectionTraceCostArr[i].setHorizontalAlignment(javax.swing.JTextField.RIGHT);
      againstPreferredDirectionTraceCostArr[i].setColumns(textFieldLength);
      againstPreferredDirectionTraceCostArr[i].setPreferredSize(
          new Dimension(150, againstPreferredDirectionTraceCostArr[i].getPreferredSize().height));
      againstPreferredDirectionTraceCostArr[i].addKeyListener(
          new WindowAutorouteParameter.AgainstPreferredDirectionTraceCostKeyListener(i));
      againstPreferredDirectionTraceCostArr[i].addFocusListener(
          new WindowAutorouteParameter.AgainstPreferredDirectionTraceCostFocusListener(i));
      gridbag.setConstraints(againstPreferredDirectionTraceCostArr[i], gridbagConstraints);
      mainPanel.add(againstPreferredDirectionTraceCostArr[i]);
      bendCostArr[i] = new JFormattedTextField(floatNumberFormat);
      bendCostArr[i].setHorizontalAlignment(javax.swing.JTextField.RIGHT);
      bendCostArr[i].setColumns(textFieldLength);
      bendCostArr[i].setPreferredSize(new Dimension(150, bendCostArr[i].getPreferredSize().height));
      bendCostArr[i].addKeyListener(new WindowAutorouteParameter.BendCostKeyListener(i));
      bendCostArr[i].addFocusListener(new WindowAutorouteParameter.BendCostFocusListener(i));
      gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridbag.setConstraints(bendCostArr[i], gridbagConstraints);
      mainPanel.add(bendCostArr[i]);
      preferredDirectionTraceCostsInputCompleted[i] = true;
      againstPreferredDirectionTraceCostsInputCompleted[i] = true;
      bendCostsInputCompleted[i] = true;
    }

    this.refresh();
    this.pack();
    this.setResizable(false);
    clampWindowHeight(this, boardFrame);

    // Register as listener for settings changes (bidirectional binding)
    this.boardHandling
        .getCurrentRoutingJob()
        .routerSettings
        .addPropertyChangeListener(this::onSettingsChanged);
  }

  public static int normalizeIntInput(Object input, int oldValue, int minValue, int maxValue) {
    return WindowAutorouteParameterState.normalizeIntInput(input, oldValue, minValue, maxValue);
  }

  public static double normalizePositiveDoubleInput(Object input, double oldValue) {
    return WindowAutorouteParameterState.normalizePositiveDoubleInput(input, oldValue);
  }

  public static String normalizeTimeoutInput(Object input, String oldValue) {
    return WindowAutorouteParameterState.normalizeTimeoutInput(input, oldValue);
  }

  public static void applyViasAllowedSelection(RouterSettings settings, boolean selected) {
    settings.setViasAllowed(selected);
  }

  public static void applyFanoutEnabledSelection(RouterSettings settings, boolean selected) {
    settings.setFanoutEnabled(selected);
  }

  public static void applyAutorouteEnabledSelection(RouterSettings settings, boolean selected) {
    settings.setEnabled(selected);
  }

  public static void applyOptimizerEnabledSelection(RouterSettings settings, boolean selected) {
    settings.setOptimizerEnabled(selected);
  }

  @Override
  public void setLanguage(Locale locale) {
    if (tm != null) {
      tm.setLocale(locale);
    }
    super.setLanguage(locale);
    if (tm == null) {
      tm = new GuiTextManager(this.getClass(), locale);
    }
    if (this.jobTimeoutUnitComboBox != null) {
      populateTimeoutUnits();
    }
    if (this.jobTimeoutValueField != null
        && this.boardHandling != null
        && this.boardHandling.getCurrentRoutingJob() != null) {
      setJobTimeoutFields(
          this.boardHandling.getCurrentRoutingJob().routerSettings.jobTimeoutString);
    }
  }

  /** Handle property change events from RouterSettings to update GUI controls. */
  private void onSettingsChanged(java.beans.PropertyChangeEvent evt) {
    if (isUpdatingFromSettings) {
      return; // Prevent circular updates
    }

    isUpdatingFromSettings = true;
    try {
      String propertyName = evt.getPropertyName();
      Object newValue = evt.getNewValue();

      switch (propertyName) {
        case "maxPasses" -> {
          if (newValue != null) {
            int val = (Integer) newValue;
            maxPassesField.setValue(val == Integer.MAX_VALUE ? 0 : val);
          }
        }
        case "maxThreads" -> {
          if (newValue != null) {
            maxThreadsField.setValue(newValue);
          }
        }
        case "jobTimeoutString" -> {
          if (newValue != null) {
            setJobTimeoutFields(newValue.toString());
          }
        }
        case "enabled" -> {
          if (newValue instanceof Boolean bool) {
            settingsAutorouterAutoroutePassButton.setSelected(bool);
          }
        }
        case "viasAllowed" -> {
          if (newValue instanceof Boolean bool) {
            settingsAutorouterViasAllowed.setSelected(bool);
          }
        }
        case "fanout.enabled" -> {
          if (newValue instanceof Boolean bool) {
            settingsAutorouterFanoutButton.setSelected(bool);
          }
        }
        case "optimizer.enabled" -> {
          if (newValue instanceof Boolean bool) {
            settingsAutorouterOptimizationButton.setSelected(bool);
          }
        }
        default -> {}
      }
    } finally {
      isUpdatingFromSettings = false;
    }
  }

  /** Recalculates all displayed values. */
  @Override
  public void refresh() {
    RouterSettings settings = this.boardHandling.getCurrentRoutingJob().routerSettings;
    final LayerStructure layerStructure = this.boardHandling.getRoutingBoard().layerStructure;

    this.settingsAutorouterViasAllowed.setSelected(settings.getViasAllowed());
    this.settingsAutorouterFanoutButton.setSelected(settings.getRunFanout());
    this.settingsAutorouterAutoroutePassButton.setSelected(settings.getRunRouter());
    this.settingsAutorouterOptimizationButton.setSelected(settings.getRunOptimizer());

    for (int i = 0; i < settingsAutorouterLayerActiveArr.length; i++) {
      this.settingsAutorouterLayerActiveArr[i].setSelected(settings.getLayerActive(i));
    }

    for (int i = 0; i < settingsAutorouterComboBoxArr.size(); i++) {
      if (settings.getPreferredDirectionIsHorizontal(layerStructure.getLayerNo(i))) {
        this.settingsAutorouterComboBoxArr.get(i).setSelectedItem(this.horizontal);
      } else {
        this.settingsAutorouterComboBoxArr.get(i).setSelectedItem(this.vertical);
      }
    }

    this.viaCostField.setValue(settings.getViaCosts());
    this.planeViaCostField.setValue(settings.getPlaneViaCosts());
    this.startRipupCosts.setValue(settings.getStartRipupCosts());
    this.maxPassesField.setValue(
        settings.maxPasses == null || settings.maxPasses == Integer.MAX_VALUE
            ? 0
            : settings.maxPasses);
    setJobTimeoutFields(settings.jobTimeoutString);
    this.maxThreadsField.setValue(settings.maxThreads);
    for (int i = 0; i < preferredDirectionTraceCostArr.length; i++) {
      this.preferredDirectionTraceCostArr[i].setValue(
          settings.getPreferredDirectionTraceCosts(layerStructure.getLayerNo(i)));
    }
    for (int i = 0; i < againstPreferredDirectionTraceCostArr.length; i++) {
      this.againstPreferredDirectionTraceCostArr[i].setValue(
          settings.getAgainstPreferredDirectionTraceCosts(layerStructure.getLayerNo(i)));
    }
    for (int i = 0; i < bendCostArr.length; i++) {
      this.bendCostArr[i].setValue(settings.getBendCost(layerStructure.getLayerNo(i)));
    }
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @Override
  public void parentIconified() {
    super.parentIconified();
  }

  @Override
  public void parentDeiconified() {
    super.parentDeiconified();
  }

  private record TimeoutUnitItem(
      WindowAutorouteParameterState.TimeoutUnit unit, String displayName) {
    @Override
    public String toString() {
      return displayName;
    }
  }

  private void populateTimeoutUnits() {
    WindowAutorouteParameterState.TimeoutUnit selected = getSelectedTimeoutUnit();
    this.jobTimeoutUnitComboBox.removeAllItems();
    for (WindowAutorouteParameterState.TimeoutUnit unit :
        WindowAutorouteParameterState.TimeoutUnit.values()) {
      String name =
          switch (unit) {
            case MINUTES -> this.tm.getText("timeout_unit_minutes");
            case HOURS -> this.tm.getText("timeout_unit_hours");
            case DAYS -> this.tm.getText("timeout_unit_days");
            case WEEKS -> this.tm.getText("timeout_unit_weeks");
          };
      this.jobTimeoutUnitComboBox.addItem(new TimeoutUnitItem(unit, name));
    }
    setSelectedTimeoutUnit(
        selected != null ? selected : WindowAutorouteParameterState.TimeoutUnit.HOURS);
  }

  private WindowAutorouteParameterState.TimeoutUnit getSelectedTimeoutUnit() {
    TimeoutUnitItem item = (TimeoutUnitItem) this.jobTimeoutUnitComboBox.getSelectedItem();
    return item != null ? item.unit() : null;
  }

  private void setSelectedTimeoutUnit(WindowAutorouteParameterState.TimeoutUnit unit) {
    if (unit == null) {
      return;
    }
    for (int i = 0; i < this.jobTimeoutUnitComboBox.getItemCount(); i++) {
      TimeoutUnitItem item = this.jobTimeoutUnitComboBox.getItemAt(i);
      if (item != null && item.unit() == unit) {
        this.jobTimeoutUnitComboBox.setSelectedIndex(i);
        break;
      }
    }
  }

  private long readTimeoutValue() {
    try {
      this.jobTimeoutValueField.commitEdit();
    } catch (java.text.ParseException ignored) {
      // Fall through to text or value parsing
    }

    String text = this.jobTimeoutValueField.getText();
    if (text != null && !text.isBlank()) {
      try {
        return Math.max(1, Long.parseLong(text.trim()));
      } catch (NumberFormatException ignored) {
        // Fall back
      }
    }

    Object val = this.jobTimeoutValueField.getValue();
    if (val instanceof Number number) {
      return Math.max(1, number.longValue());
    }
    return 1L;
  }

  private void decrementJobTimeout() {
    long currentValue = readTimeoutValue();
    long newValue = Math.max(1, currentValue - 1);
    this.jobTimeoutValueField.setValue(newValue);
    commitJobTimeoutEdit();
  }

  private void incrementJobTimeout() {
    long currentValue = readTimeoutValue();
    WindowAutorouteParameterState.TimeoutUnit unit = getSelectedTimeoutUnit();
    long maxValue =
        unit != null
            ? unit.getMaxUnits()
            : WindowAutorouteParameterState.TimeoutUnit.HOURS.getMaxUnits();
    long newValue = Math.min(maxValue, currentValue + 1);
    this.jobTimeoutValueField.setValue(newValue);
    commitJobTimeoutEdit();
  }

  private void onTimeoutUnitChanged() {
    if (this.isUpdatingFromSettings) {
      return;
    }
    long currentValue = readTimeoutValue();
    WindowAutorouteParameterState.TimeoutUnit unit = getSelectedTimeoutUnit();
    if (unit != null) {
      if (currentValue > unit.getMaxUnits()) {
        this.jobTimeoutValueField.setValue(unit.getMaxUnits());
      } else if (currentValue < 1) {
        this.jobTimeoutValueField.setValue(1);
      }
    }
    commitJobTimeoutEdit();
  }

  // Set timeout fields based on the provided timeout string (in format "HH:MM:SS" or seconds)
  private void setJobTimeoutFields(String timeoutString) {
    WindowAutorouteParameterState.Timeout timeout =
        WindowAutorouteParameterState.parseTimeout(timeoutString);
    WindowAutorouteParameterState.DecomposedTimeout decomposed =
        WindowAutorouteParameterState.decomposeTimeout(timeout.totalSeconds());
    this.jobTimeoutValueField.setValue(decomposed.value());
    setSelectedTimeoutUnit(decomposed.unit());
  }

  private void commitJobTimeoutEdit() {
    if (this.isUpdatingFromSettings) {
      return;
    }
    String oldValue = boardHandling.getCurrentRoutingJob().routerSettings.jobTimeoutString;
    String newValue = buildJobTimeoutString();
    if (newValue == null) {
      newValue = oldValue;
    }

    this.isUpdatingFromSettings = true;
    try {
      boardHandling.getCurrentRoutingJob().routerSettings.setJobTimeoutString(newValue);
    } finally {
      this.isUpdatingFromSettings = false;
    }

    setJobTimeoutFields(newValue);
  }

  private String buildJobTimeoutString() {
    long value = readTimeoutValue();
    WindowAutorouteParameterState.TimeoutUnit unit = getSelectedTimeoutUnit();
    return WindowAutorouteParameterState.buildTimeout(value, unit);
  }

  private javax.swing.JComponent createWordWrapLabel(String key, int width, int height) {
    javax.swing.JTextArea textArea = new javax.swing.JTextArea();
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    textArea.setEditable(false);
    textArea.setFocusable(false);
    textArea.setOpaque(false); // Makes background transparent like a JLabel

    // Copy native JLabel look and feel properties
    textArea.setFont(javax.swing.UIManager.getFont("Label.font"));
    textArea.setBorder(javax.swing.BorderFactory.createEmptyBorder());

    textArea.setPreferredSize(new java.awt.Dimension(width, height));
    tm.setText(textArea, key);
    return textArea;
  }

  private class LayerActiveListener implements ActionListener {

    private final int signalLayerNo;

    public LayerActiveListener(int layerIndex) {
      signalLayerNo = layerIndex;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      int currentLayerIndex = this.signalLayerNo;
      boardHandling
          .getCurrentRoutingJob()
          .routerSettings
          .setLayerActive(
              currentLayerIndex, settingsAutorouterLayerActiveArr[this.signalLayerNo].isSelected());
    }
  }

  private class PreferredDirectionListener implements ActionListener {

    private final int signalLayerNo;

    public PreferredDirectionListener(int layerIndex) {
      signalLayerNo = layerIndex;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      int currentLayerIndex =
          boardHandling.getRoutingBoard().layerStructure.getLayerNo(this.signalLayerNo);
      boardHandling
          .getCurrentRoutingJob()
          .routerSettings
          .setPreferredDirectionIsHorizontal(
              currentLayerIndex,
              settingsAutorouterComboBoxArr.get(signalLayerNo).getSelectedItem() == horizontal);
    }
  }

  private class ViasAllowedListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      isUpdatingFromSettings = true;
      try {
        applyViasAllowedSelection(
            boardHandling.getCurrentRoutingJob().routerSettings,
            settingsAutorouterViasAllowed.isSelected());
      } finally {
        isUpdatingFromSettings = false;
      }
    }
  }

  private class FanoutListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      RouterSettings autorouteSettings = boardHandling.getCurrentRoutingJob().routerSettings;
      isUpdatingFromSettings = true;
      try {
        applyFanoutEnabledSelection(autorouteSettings, settingsAutorouterFanoutButton.isSelected());
      } finally {
        isUpdatingFromSettings = false;
      }
    }
  }

  private class AutorouteListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      RouterSettings autorouteSettings = boardHandling.getCurrentRoutingJob().routerSettings;
      isUpdatingFromSettings = true;
      try {
        applyAutorouteEnabledSelection(
            autorouteSettings, settingsAutorouterAutoroutePassButton.isSelected());
      } finally {
        isUpdatingFromSettings = false;
      }
    }
  }

  private class OptimizationListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      RouterSettings autorouteSettings = boardHandling.getCurrentRoutingJob().routerSettings;
      isUpdatingFromSettings = true;
      try {
        applyOptimizerEnabledSelection(
            autorouteSettings, settingsAutorouterOptimizationButton.isSelected());
      } finally {
        isUpdatingFromSettings = false;
      }
    }
  }

  private class ViaCostFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.getViaCosts();
        Object input = viaCostField.getValue();
        int inputValue = normalizeIntInput(input, oldValue, 1, Integer.MAX_VALUE);
        boardHandling.getCurrentRoutingJob().routerSettings.setViaCosts(inputValue);
        viaCostField.setValue(inputValue);
        viaCostInputCompleted = true;

      } else {
        viaCostInputCompleted = false;
      }
    }
  }

  private class ViaCostFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!viaCostInputCompleted) {
        // Save the value when focus is lost
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.getViaCosts();

        // Commit the edit to ensure getValue() returns the typed value
        try {
          viaCostField.commitEdit();
        } catch (java.text.ParseException e) {
          // If parse fails, revert to old value
          viaCostField.setValue(oldValue);
        }

        Object input = viaCostField.getValue();
        int inputValue;
        if (input instanceof Number number) {
          inputValue = number.intValue();
          if (inputValue <= 0) {
            inputValue = 1;
            viaCostField.setValue(inputValue);
          }
        } else {
          inputValue = oldValue;
          viaCostField.setValue(oldValue);
        }
        boardHandling.getCurrentRoutingJob().routerSettings.setViaCosts(inputValue);
        viaCostField.setValue(inputValue);
        viaCostInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class PlaneViaCostFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.getPlaneViaCosts();
        Object input = planeViaCostField.getValue();
        int inputValue = normalizeIntInput(input, oldValue, 1, Integer.MAX_VALUE);
        boardHandling.getCurrentRoutingJob().routerSettings.setPlaneViaCosts(inputValue);
        planeViaCostField.setValue(inputValue);
        planeViaCostInputCompleted = true;

      } else {
        planeViaCostInputCompleted = false;
      }
    }
  }

  private class PlaneViaCostFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!planeViaCostInputCompleted) {
        // Save the value when focus is lost
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.getPlaneViaCosts();

        // Commit the edit to ensure getValue() returns the typed value
        try {
          planeViaCostField.commitEdit();
        } catch (java.text.ParseException e) {
          // If parse fails, revert to old value
          planeViaCostField.setValue(oldValue);
        }

        Object input = planeViaCostField.getValue();
        int inputValue;
        if (input instanceof Number number) {
          inputValue = number.intValue();
          if (inputValue <= 0) {
            inputValue = 1;
            planeViaCostField.setValue(inputValue);
          }
        } else {
          inputValue = oldValue;
          planeViaCostField.setValue(oldValue);
        }
        boardHandling.getCurrentRoutingJob().routerSettings.setPlaneViaCosts(inputValue);
        planeViaCostField.setValue(inputValue);
        planeViaCostInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class StartRipupCostFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.getStartRipupCosts();
        Object input = startRipupCosts.getValue();
        int inputValue = normalizeIntInput(input, oldValue, 1, Integer.MAX_VALUE);
        boardHandling.getCurrentRoutingJob().routerSettings.setStartRipupCosts(inputValue);
        startRipupCosts.setValue(inputValue);
        startRipupCostInputCompleted = true;
      } else {
        startRipupCostInputCompleted = false;
      }
    }
  }

  private class StartRipupCostFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!startRipupCostInputCompleted) {
        // Save the value when focus is lost
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.getStartRipupCosts();

        // Commit the edit to ensure getValue() returns the typed value
        try {
          startRipupCosts.commitEdit();
        } catch (java.text.ParseException e) {
          // If parse fails, revert to old value
          startRipupCosts.setValue(oldValue);
        }

        Object input = startRipupCosts.getValue();
        int inputValue;
        if (input instanceof Number number) {
          inputValue = number.intValue();
          if (inputValue <= 0) {
            inputValue = 1;
          }
        } else {
          inputValue = oldValue;
        }
        boardHandling.getCurrentRoutingJob().routerSettings.setStartRipupCosts(inputValue);
        startRipupCosts.setValue(inputValue);
        startRipupCostInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class MaxPassesFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        Integer currentVal = boardHandling.getCurrentRoutingJob().routerSettings.maxPasses;
        int oldValue = currentVal != null ? currentVal : 0;
        Object input = maxPassesField.getValue();
        int inputValue = normalizeIntInput(input, oldValue, 0, 9999);
        // Use setter to fire property change event
        isUpdatingFromSettings = true;
        try {
          boardHandling.getCurrentRoutingJob().routerSettings.setMaxPasses(inputValue);
        } finally {
          isUpdatingFromSettings = false;
        }
        maxPassesField.setValue(inputValue);
        maxPassesInputCompleted = true;
      } else {
        maxPassesInputCompleted = false;
      }
    }
  }

  private class MaxPassesFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!maxPassesInputCompleted) {
        // Save the value when focus is lost
        Integer currentVal = boardHandling.getCurrentRoutingJob().routerSettings.maxPasses;
        int oldValue = currentVal != null ? currentVal : 0;

        // Commit the edit to ensure getValue() returns the typed value
        try {
          maxPassesField.commitEdit();
        } catch (java.text.ParseException e) {
          // If parse fails, revert to old value
          maxPassesField.setValue(oldValue);
        }

        Object input = maxPassesField.getValue();
        int inputValue;
        if (input instanceof Number number) {
          inputValue = number.intValue();
          if (inputValue < 0) {
            inputValue = 0;
          }
          if (inputValue > 9999) {
            inputValue = 9999;
          }
        } else {
          inputValue = oldValue;
        }
        isUpdatingFromSettings = true;
        try {
          boardHandling.getCurrentRoutingJob().routerSettings.setMaxPasses(inputValue);
        } finally {
          isUpdatingFromSettings = false;
        }
        maxPassesField.setValue(inputValue);
        maxPassesInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class MaxThreadsFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.maxThreads;
        Object input = maxThreadsField.getValue();
        int inputValue;
        int maxAvailable = Runtime.getRuntime().availableProcessors();
        inputValue = normalizeIntInput(input, oldValue, 1, maxAvailable);
        // Use setter to fire property change event
        isUpdatingFromSettings = true;
        try {
          boardHandling.getCurrentRoutingJob().routerSettings.setMaxThreads(inputValue);
        } finally {
          isUpdatingFromSettings = false;
        }
        maxThreadsField.setValue(inputValue);
        maxThreadsInputCompleted = true;
      } else {
        maxThreadsInputCompleted = false;
      }
    }
  }

  private class MaxThreadsFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!maxThreadsInputCompleted) {
        // Save the value when focus is lost
        int oldValue = boardHandling.getCurrentRoutingJob().routerSettings.maxThreads;

        // Commit the edit to ensure getValue() returns the typed value
        try {
          maxThreadsField.commitEdit();
        } catch (java.text.ParseException e) {
          // If parse fails, revert to old value
          maxThreadsField.setValue(oldValue);
        }

        Object input = maxThreadsField.getValue();
        int inputValue;
        int maxAvailable = Runtime.getRuntime().availableProcessors();
        if (input instanceof Number number) {
          inputValue = number.intValue();
          if (inputValue < 1) {
            inputValue = 1;
          }
          if (inputValue > maxAvailable) {
            inputValue = maxAvailable;
          }
        } else {
          inputValue = oldValue;
        }
        isUpdatingFromSettings = true;
        try {
          boardHandling.getCurrentRoutingJob().routerSettings.setMaxThreads(inputValue);
        } finally {
          isUpdatingFromSettings = false;
        }
        maxThreadsField.setValue(inputValue);
        maxThreadsInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class PreferredDirectionTraceCostKeyListener extends KeyAdapter {

    private final int signalLayerNo;

    public PreferredDirectionTraceCostKeyListener(int layerIndex) {
      this.signalLayerNo = layerIndex;
    }

    @Override
    public void keyTyped(KeyEvent evt) {
      preferredDirectionTraceCostsInputCompleted[this.signalLayerNo] = false;
    }
  }

  private class PreferredDirectionTraceCostFocusListener implements FocusListener {

    private final int signalLayerNo;

    public PreferredDirectionTraceCostFocusListener(int layerIndex) {
      this.signalLayerNo = layerIndex;
    }

    @Override
    public void focusLost(FocusEvent evt) {
      if (!preferredDirectionTraceCostsInputCompleted[this.signalLayerNo]) {
        int currentLayerIndex =
            boardHandling.getRoutingBoard().layerStructure.getLayerNo(this.signalLayerNo);
        double oldValue =
            boardHandling
                .getCurrentRoutingJob()
                .routerSettings
                .getPreferredDirectionTraceCosts(currentLayerIndex);

        try {
          preferredDirectionTraceCostArr[this.signalLayerNo].commitEdit();
        } catch (java.text.ParseException e) {
          preferredDirectionTraceCostArr[this.signalLayerNo].setValue(oldValue);
        }

        Object input = preferredDirectionTraceCostArr[this.signalLayerNo].getValue();
        double inputValue;
        if (input instanceof Number number) {
          inputValue = number.doubleValue();
          if (inputValue < 0.1) {
            inputValue = 0.1;
          }
          if (inputValue > 9.9) {
            inputValue = 9.9;
          }
        } else {
          inputValue = oldValue;
        }

        boardHandling
            .getCurrentRoutingJob()
            .routerSettings
            .setPreferredDirectionTraceCosts(currentLayerIndex, inputValue);
        preferredDirectionTraceCostArr[this.signalLayerNo].setValue(inputValue);
        preferredDirectionTraceCostsInputCompleted[this.signalLayerNo] = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class AgainstPreferredDirectionTraceCostKeyListener extends KeyAdapter {

    private final int signalLayerNo;

    public AgainstPreferredDirectionTraceCostKeyListener(int layerIndex) {
      this.signalLayerNo = layerIndex;
    }

    @Override
    public void keyTyped(KeyEvent evt) {
      againstPreferredDirectionTraceCostsInputCompleted[this.signalLayerNo] = false;
    }
  }

  private class AgainstPreferredDirectionTraceCostFocusListener implements FocusListener {

    private final int signalLayerNo;

    public AgainstPreferredDirectionTraceCostFocusListener(int layerIndex) {
      this.signalLayerNo = layerIndex;
    }

    @Override
    public void focusLost(FocusEvent evt) {
      if (!againstPreferredDirectionTraceCostsInputCompleted[this.signalLayerNo]) {
        int currentLayerIndex =
            boardHandling.getRoutingBoard().layerStructure.getLayerNo(this.signalLayerNo);
        double oldValue =
            boardHandling
                .getCurrentRoutingJob()
                .routerSettings
                .getAgainstPreferredDirectionTraceCosts(currentLayerIndex);

        try {
          againstPreferredDirectionTraceCostArr[this.signalLayerNo].commitEdit();
        } catch (java.text.ParseException e) {
          againstPreferredDirectionTraceCostArr[this.signalLayerNo].setValue(oldValue);
        }

        Object input = againstPreferredDirectionTraceCostArr[this.signalLayerNo].getValue();
        double inputValue;
        if (input instanceof Number number) {
          inputValue = number.doubleValue();
          if (inputValue < 0.1) {
            inputValue = 0.1;
          }
          if (inputValue > 9.9) {
            inputValue = 9.9;
          }
        } else {
          inputValue = oldValue;
        }

        boardHandling
            .getCurrentRoutingJob()
            .routerSettings
            .setAgainstPreferredDirectionTraceCosts(currentLayerIndex, inputValue);
        againstPreferredDirectionTraceCostArr[this.signalLayerNo].setValue(inputValue);
        againstPreferredDirectionTraceCostsInputCompleted[this.signalLayerNo] = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class BendCostKeyListener extends KeyAdapter {

    private final int signalLayerNo;

    public BendCostKeyListener(int layerIndex) {
      this.signalLayerNo = layerIndex;
    }

    @Override
    public void keyTyped(KeyEvent evt) {
      bendCostsInputCompleted[this.signalLayerNo] = false;
    }
  }

  private class BendCostFocusListener implements FocusListener {

    private final int signalLayerNo;

    public BendCostFocusListener(int layerIndex) {
      this.signalLayerNo = layerIndex;
    }

    @Override
    public void focusLost(FocusEvent evt) {
      if (!bendCostsInputCompleted[this.signalLayerNo]) {
        // Save the value when focus is lost
        int currentLayerIndex =
            boardHandling.getRoutingBoard().layerStructure.getLayerNo(this.signalLayerNo);
        double oldValue =
            boardHandling.getCurrentRoutingJob().routerSettings.getBendCost(currentLayerIndex);

        // Commit the edit to ensure getValue() returns the typed value
        try {
          bendCostArr[this.signalLayerNo].commitEdit();
        } catch (java.text.ParseException e) {
          bendCostArr[this.signalLayerNo].setValue(oldValue);
        }

        Object input = bendCostArr[this.signalLayerNo].getValue();
        double inputValue;
        if (input instanceof Number number) {
          inputValue = number.doubleValue();
          if (inputValue < 0.0) {
            inputValue = 0.0;
          }
          if (inputValue > RouterSettings.MAX_BEND_COST) {
            inputValue = RouterSettings.MAX_BEND_COST;
          }
        } else {
          inputValue = oldValue;
        }
        boardHandling
            .getCurrentRoutingJob()
            .routerSettings
            .setBendCost(currentLayerIndex, inputValue);
        bendCostArr[this.signalLayerNo].setValue(inputValue);
        bendCostsInputCompleted[this.signalLayerNo] = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }
}
