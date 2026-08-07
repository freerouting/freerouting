package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import app.freerouting.boardgraphics.ColorIntensityTable.ObjectNames;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.analytics.FRAnalytics;
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

  protected final BoardPanel boardPanel;
  private final VisibilitySection layerSection;
  private final VisibilitySection objectSection;
  protected boolean bulkUpdateInProgress;

  private static final java.util.Map<Locale, TextManager> text_manager_cache =
      new ConcurrentHashMap<>();

  public WindowVisibility(BoardFrame boardFrame) {
    this.boardPanel = boardFrame.boardPanel;
    setLanguage(boardFrame.get_locale());

    TextManager tm = new TextManager(WindowVisibility.class, boardFrame.get_locale());
    this.setTitle(tm.getText("title"));

    LayerStructure layerStructure = boardPanel.boardHandling.get_routing_board().layerStructure;
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
            index -> get_board_handling().graphicsContext.get_raw_layer_visibility(index),
            (index, value) -> get_board_handling().set_layer_visibility(index, value));
    this.objectSection =
        new VisibilitySection(
            tm.getText("object_section_title"),
            objectMessages,
            index -> get_board_handling().graphicsContext.colorIntensityTable.get_value(index),
            (index, value) ->
                get_board_handling().graphicsContext.colorIntensityTable.set_value(index, value));

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

      contentPanel.add(layerSection.create_panel(), constraints);

      constraints.fill = GridBagConstraints.BOTH;
      constraints.weighty = 0.0;
      constraints.insets = new Insets(6, 8, 6, 8);
      contentPanel.add(new JSeparator(), constraints);

      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.insets = new Insets(4, 8, 4, 8);
      contentPanel.add(objectSection.create_panel(), constraints);
    } finally {
      bulkUpdateInProgress = false;
    }

    JPanel buttonRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    TextManager visibilityTm =
        text_manager_cache.computeIfAbsent(
            boardFrame.get_locale(), locale -> new TextManager(WindowVisibility.class, locale));

    JButton resetButton = new JButton(visibilityTm.getText("reset_to_defaults"));
    resetButton.setToolTipText(visibilityTm.getText("reset_to_defaults_tooltip"));
    resetButton.addActionListener(
        _ -> {
          reset_to_defaults();
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

  public void refresh() {
    bulkUpdateInProgress = true;
    try {
      layerSection.refresh();
      objectSection.refresh();
    } finally {
      bulkUpdateInProgress = false;
    }
  }

  protected GuiBoardManager get_board_handling() {
    return boardPanel.boardHandling;
  }

  protected void reset_to_defaults() {
    bulkUpdateInProgress = true;
    try {
      layerSection.reset_to_defaults();
      objectSection.reset_to_defaults();
    } finally {
      bulkUpdateInProgress = false;
    }
  }

  private int snap_to_step(int value) {
    int snappedValue = Math.round((float) value / (float) SLIDER_STEP) * SLIDER_STEP;
    return Math.max(0, Math.min(MAX_SLIDER_VALUE, snappedValue));
  }

  private void set_slider_text_value(JTextField value_field, int value) {
    value_field.setText(value + "%");
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
      int snappedValue = snap_to_step(currentValue);

      if (currentValue != snappedValue) {
        section.sliderArr[sliderNo].setValue(snappedValue);
        return;
      }

      set_slider_text_value(section.valueArr[sliderNo], currentValue);

      if (bulkUpdateInProgress || section.sliderArr[sliderNo].getValueIsAdjusting()) {
        return;
      }

      section.set_changed_value(sliderNo, ((double) snappedValue) / ((double) MAX_SLIDER_VALUE));
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
    private final BiConsumer<Integer, Double> changed_value_consumer;

    private VisibilitySection(
        String title,
        String[] messageArr,
        IntToDoubleFunction currentValueSupplier,
        BiConsumer<Integer, Double> changed_value_consumer) {
      this.title = title;
      this.messageArr = messageArr;
      this.currentValueSupplier = currentValueSupplier;
      this.changed_value_consumer = changed_value_consumer;
      this.sliderArr = new JSlider[messageArr.length];
      this.valueArr = new JTextField[messageArr.length];
      this.originalDefaults = new int[messageArr.length];
      this.defaultsSet = new boolean[messageArr.length];
    }

    private JPanel create_panel() {
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
        add_row(panel, constraints, i);
      }

      return panel;
    }

    private void add_row(JPanel panel, GridBagConstraints constraints, int index) {
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

      set_slider_value(index, currentValueSupplier.applyAsDouble(index));
    }

    private void refresh() {
      for (int i = 0; i < messageArr.length; i++) {
        set_slider_value(i, currentValueSupplier.applyAsDouble(i));
      }
    }

    private void reset_to_defaults() {
      for (int i = 0; i < messageArr.length; i++) {
        if (defaultsSet[i]) {
          int originalVal = originalDefaults[i];
          sliderArr[i].setValue(originalVal);
          set_slider_text_value(valueArr[i], originalVal);
          changed_value_consumer.accept(i, ((double) originalVal) / ((double) MAX_SLIDER_VALUE));
        }
      }
    }

    private void set_slider_value(int index, double value) {
      int visibility = (int) Math.round(value * MAX_SLIDER_VALUE);
      visibility = Math.max(0, Math.min(MAX_SLIDER_VALUE, visibility));

      if (!defaultsSet[index]) {
        originalDefaults[index] = visibility;
        defaultsSet[index] = true;
      }

      sliderArr[index].setValue(visibility);
      set_slider_text_value(valueArr[index], visibility);
    }

    private void set_changed_value(int index, double value) {
      changed_value_consumer.accept(index, value);
    }
  }
}
