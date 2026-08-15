package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.LayerStructure;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.rendering.ColorIntensityTable.ObjectNames;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.util.TextManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntToDoubleFunction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Combined visibility frame for board layers and board objects. */
public class WindowVisibility extends BoardSavableSubWindow {

  protected static final int MAX_SLIDER_VALUE = 100;
  private static final int SLIDER_STEP = 10;
  private static final int SLIDER_WIDTH = 160;
  private static final int LABEL_WIDTH = 160;
  private static final int VALUE_FIELD_WIDTH = 44;
  private static final Dimension CONTENT_SIZE = new Dimension(500, 420);
  private static final java.util.Map<Locale, TextManager> text_manager_cache =
      new ConcurrentHashMap<>();
  protected final BoardPanel boardPanel;
  private final VisibilitySection layerSection;
  private final VisibilitySection objectSection;
  protected boolean bulkUpdateInProgress;

  /** Creates a window for editing layer and object visibility. */
  public WindowVisibility(BoardFrame boardFrame) {
    this.boardPanel = boardFrame.boardPanel;
    setLanguage(boardFrame.get_locale());

    TextManager tm = new TextManager(WindowVisibility.class, boardFrame.get_locale());
    this.setTitle(tm.getText("title"));

    LayerStructure layerStructure = boardPanel.boardHandling.getRoutingBoard().layerStructure;
    String[] layerMessages = new String[layerStructure.arr.length];
    for (int i = 0; i < layerMessages.length; i++) {
      layerMessages[i] = layerStructure.arr[i].name;
    }

    String[] objectMessages = new String[ObjectNames.values().length];
    for (int i = 0; i < objectMessages.length; i++) {
      objectMessages[i] = tm.getText(ObjectNames.values()[i].toString());
    }

    this.layerSection =
        new VisibilitySection(
            tm.getText("layer_section_title"),
            layerMessages,
            index -> getBoardHandling().graphicsContext.getRawLayerVisibility(index),
            (index, value) -> getBoardHandling().setLayerVisibility(index, value));
    this.objectSection =
        new VisibilitySection(
            tm.getText("object_section_title"),
            objectMessages,
            index -> getBoardHandling().graphicsContext.colorIntensityTable.getValue(index),
            (index, value) ->
                getBoardHandling().graphicsContext.colorIntensityTable.setValue(index, value));

    JPanel mainPanel = new JPanel(new BorderLayout());
    getContentPane().add(mainPanel);

    JPanel headerPanel = new JPanel(new BorderLayout());
    JLabel headerMessage = new JLabel(tm.getText("headerMessage"), JLabel.CENTER);
    headerPanel.add(headerMessage, BorderLayout.CENTER);
    headerPanel.add(new JSeparator(), BorderLayout.SOUTH);
    mainPanel.add(headerPanel, BorderLayout.NORTH);

    JPanel contentPanel = new JPanel(new GridBagLayout());
    JScrollPane scrollPane =
        new JScrollPane(
            contentPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setPreferredSize(CONTENT_SIZE);
    scrollPane.getVerticalScrollBar().setUnitIncrement(24);
    scrollPane.getVerticalScrollBar().setBlockIncrement(72);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(24);
    mainPanel.add(scrollPane, BorderLayout.CENTER);

    bulkUpdateInProgress = true;
    try {
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.insets = new Insets(4, 8, 4, 8);
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1.0;

      contentPanel.add(layerSection.createPanel(), constraints);

      constraints.fill = GridBagConstraints.BOTH;
      constraints.weighty = 0.0;
      constraints.insets = new Insets(6, 8, 6, 8);
      contentPanel.add(new JSeparator(), constraints);

      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.insets = new Insets(4, 8, 4, 8);
      contentPanel.add(objectSection.createPanel(), constraints);
    } finally {
      bulkUpdateInProgress = false;
    }

    final JPanel buttonRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    TextManager visibilityTm =
        text_manager_cache.computeIfAbsent(
            boardFrame.get_locale(), locale -> new TextManager(WindowVisibility.class, locale));

    JButton resetButton = new JButton(visibilityTm.getText("reset_to_defaults"));
    resetButton.setToolTipText(visibilityTm.getText("reset_to_defaults_tooltip"));
    resetButton.addActionListener(
        _ -> {
          resetToDefaults();
          boardPanel.repaint();
        });
    resetButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("visibility_reset_button", resetButton.getText()));
    buttonRowPanel.add(resetButton);

    JPanel footerPanel = new JPanel(new BorderLayout());
    footerPanel.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 12));
    footerPanel.add(new JSeparator(), BorderLayout.NORTH);
    footerPanel.add(buttonRowPanel, BorderLayout.CENTER);
    mainPanel.add(footerPanel, BorderLayout.SOUTH);

    this.pack();
    this.setResizable(false);
  }

  /**
   * Creates the reusable visibility-settings content without constructing this top-level window.
   *
   * <p>The panel mirrors the two stateful controls in the full visibility window and is
   * deliberately independent of {@link BoardFrame}. It is suitable for forced-headless
   * accessibility coverage and for future embedded settings surfaces.
   *
   * @param locale locale for translated labels and descriptions
   * @param changeListener receives {@code "layer"} or {@code "object"} and the new percentage
   * @return a component-only visibility settings panel
   */
  public static JPanel createComponentOnly(
      Locale locale, BiConsumer<String, Integer> changeListener) {
    TextManager tm = new TextManager(WindowVisibility.class, locale);
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    A11y.tag(panel, GuiLocators.DISPLAY_SETTINGS);
    A11y.describe(panel, tm.getText("title"), null);

    final JPanel controls = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = GridBagConstraints.RELATIVE;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.weightx = 1.0;
    constraints.insets = new Insets(4, 8, 4, 8);

    JLabel layerLabel = new JLabel(tm.getText("layer_section_title"));
    JSlider layerSlider = new JSlider(0, MAX_SLIDER_VALUE, MAX_SLIDER_VALUE);
    A11y.tag(layerSlider, GuiLocators.DISPLAY_LAYER_VISIBILITY);
    A11y.describe(
        layerSlider, tm.getText("layer_visibility_control"), tm.getText("header_message"));
    layerLabel.setLabelFor(layerSlider);
    controls.add(layerLabel, constraints);
    controls.add(layerSlider, constraints);

    JLabel objectLabel = new JLabel(tm.getText("object_section_title"));
    JSlider objectSlider = new JSlider(0, MAX_SLIDER_VALUE, MAX_SLIDER_VALUE);
    A11y.tag(objectSlider, GuiLocators.DISPLAY_OBJECT_VISIBILITY);
    A11y.describe(
        objectSlider, tm.getText("object_visibility_control"), tm.getText("header_message"));
    objectLabel.setLabelFor(objectSlider);
    controls.add(objectLabel, constraints);
    controls.add(objectSlider, constraints);

    BiConsumer<String, Integer> listener = changeListener == null ? (_, _) -> {} : changeListener;
    layerSlider.addChangeListener(_ -> listener.accept("layer", layerSlider.getValue()));
    objectSlider.addChangeListener(_ -> listener.accept("object", objectSlider.getValue()));
    panel.add(controls, BorderLayout.CENTER);

    JButton resetButton = new JButton(tm.getText("reset_to_defaults"));
    resetButton.setToolTipText(tm.getText("reset_to_defaults_tooltip"));
    A11y.tag(resetButton, GuiLocators.DISPLAY_RESET);
    A11y.describe(resetButton, resetButton.getText(), resetButton.getToolTipText());
    resetButton.addActionListener(
        _ -> {
          layerSlider.setValue(MAX_SLIDER_VALUE);
          objectSlider.setValue(MAX_SLIDER_VALUE);
        });
    panel.add(resetButton, BorderLayout.SOUTH);
    return panel;
  }

  /** Refreshes visibility controls from the current board state. */
  public void refresh() {
    bulkUpdateInProgress = true;
    try {
      layerSection.refresh();
      objectSection.refresh();
    } finally {
      bulkUpdateInProgress = false;
    }
  }

  /** Returns the GUI board manager controlled by this window. */
  protected GuiBoardManager getBoardHandling() {
    return boardPanel.boardHandling;
  }

  /** Restores all visibility controls to their default values. */
  protected void resetToDefaults() {
    bulkUpdateInProgress = true;
    try {
      layerSection.resetToDefaults();
      objectSection.resetToDefaults();
    } finally {
      bulkUpdateInProgress = false;
    }
  }

  private int snapToStep(int value) {
    int snappedValue = Math.round((float) value / (float) SLIDER_STEP) * SLIDER_STEP;
    return Math.max(0, Math.min(MAX_SLIDER_VALUE, snappedValue));
  }

  private void setSliderTextValue(JTextField valueField, int value) {
    valueField.setText(value + "%");
  }

  private final class SliderChangeListener implements ChangeListener {
    private final VisibilitySection section;
    private final int sliderNo;

    private SliderChangeListener(VisibilitySection section, int sliderNo) {
      this.section = section;
      this.sliderNo = sliderNo;
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
      int currentValue = section.sliderArr[sliderNo].getValue();
      int snappedValue = snapToStep(currentValue);

      if (currentValue != snappedValue) {
        section.sliderArr[sliderNo].setValue(snappedValue);
        return;
      }

      setSliderTextValue(section.valueArr[sliderNo], currentValue);

      if (bulkUpdateInProgress || section.sliderArr[sliderNo].getValueIsAdjusting()) {
        return;
      }

      section.setChangedValue(sliderNo, ((double) snappedValue) / ((double) MAX_SLIDER_VALUE));
      boardPanel.repaint();
    }
  }

  private final class VisibilitySection {
    private final String title;
    private final String[] messageArr;
    private final JSlider[] sliderArr;
    private final JTextField[] valueArr;
    private final int[] originalDefaults;
    private final boolean[] defaultsSet;
    private final IntToDoubleFunction currentValueSupplier;
    private final BiConsumer<Integer, Double> changedValueConsumer;

    private VisibilitySection(
        String title,
        String[] messageArr,
        IntToDoubleFunction currentValueSupplier,
        BiConsumer<Integer, Double> changedValueConsumer) {
      this.title = title;
      this.messageArr = messageArr;
      this.currentValueSupplier = currentValueSupplier;
      this.changedValueConsumer = changedValueConsumer;
      this.sliderArr = new JSlider[messageArr.length];
      this.valueArr = new JTextField[messageArr.length];
      this.originalDefaults = new int[messageArr.length];
      this.defaultsSet = new boolean[messageArr.length];
    }

    private JPanel createPanel() {
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.insets = new Insets(4, 8, 4, 8);
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1.0;

      JPanel panel = new JPanel(new GridBagLayout());

      JLabel sectionTitle = new JLabel(title, JLabel.LEFT);
      sectionTitle.setFont(sectionTitle.getFont().deriveFont(java.awt.Font.BOLD));
      panel.add(sectionTitle, constraints);

      for (int i = 0; i < messageArr.length; i++) {
        addRow(panel, constraints, i);
      }

      return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints constraints, int index) {
      JPanel rowPanel = new JPanel(new BorderLayout(2, 0));

      JLabel label = new JLabel(messageArr[index], JLabel.LEFT);
      Dimension labelSize = new Dimension(LABEL_WIDTH, label.getPreferredSize().height);
      label.setPreferredSize(labelSize);
      rowPanel.add(label, BorderLayout.WEST);

      sliderArr[index] = new JSlider(0, MAX_SLIDER_VALUE);
      sliderArr[index].setMajorTickSpacing(SLIDER_STEP);
      sliderArr[index].setMinorTickSpacing(SLIDER_STEP);
      sliderArr[index].setPaintTicks(true);
      sliderArr[index].setSnapToTicks(true);
      Dimension sliderSize =
          new Dimension(SLIDER_WIDTH, sliderArr[index].getPreferredSize().height);
      sliderArr[index].setPreferredSize(sliderSize);
      sliderArr[index].addChangeListener(new SliderChangeListener(this, index));
      rowPanel.add(sliderArr[index], BorderLayout.CENTER);

      valueArr[index] = new JTextField(5);
      valueArr[index].setEditable(false);
      valueArr[index].setHorizontalAlignment(JTextField.RIGHT);
      Dimension valueSize =
          new Dimension(VALUE_FIELD_WIDTH, valueArr[index].getPreferredSize().height);
      valueArr[index].setPreferredSize(valueSize);
      rowPanel.add(valueArr[index], BorderLayout.EAST);

      panel.add(rowPanel, constraints);

      setSliderValue(index, currentValueSupplier.applyAsDouble(index));
    }

    private void refresh() {
      for (int i = 0; i < messageArr.length; i++) {
        setSliderValue(i, currentValueSupplier.applyAsDouble(i));
      }
    }

    private void resetToDefaults() {
      for (int i = 0; i < messageArr.length; i++) {
        if (defaultsSet[i]) {
          int originalVal = originalDefaults[i];
          sliderArr[i].setValue(originalVal);
          setSliderTextValue(valueArr[i], originalVal);
          changedValueConsumer.accept(i, ((double) originalVal) / ((double) MAX_SLIDER_VALUE));
        }
      }
    }

    private void setSliderValue(int index, double value) {
      int visibility = (int) Math.round(value * MAX_SLIDER_VALUE);
      visibility = Math.max(0, Math.min(MAX_SLIDER_VALUE, visibility));

      if (!defaultsSet[index]) {
        originalDefaults[index] = visibility;
        defaultsSet[index] = true;
      }

      sliderArr[index].setValue(visibility);
      setSliderTextValue(valueArr[index], visibility);
    }

    private void setChangedValue(int index, double value) {
      changedValueConsumer.accept(index, value);
    }
  }
}
