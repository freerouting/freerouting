package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import app.freerouting.boardgraphics.ColorIntensityTable.ObjectNames;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;

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
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Combined visibility frame for board layers and board objects.
 */
public class WindowVisibility extends BoardSavableSubWindow {

  protected static final int MAX_SLIDER_VALUE = 100;
  private static final int SLIDER_STEP = 10;
  private static final int SLIDER_WIDTH = 160;
  private static final int LABEL_WIDTH = 160;
  private static final int VALUE_FIELD_WIDTH = 44;
  private static final Dimension CONTENT_SIZE = new Dimension(500, 420);

  protected final BoardPanel board_panel;
  private final VisibilitySection layer_section;
  private final VisibilitySection object_section;
  protected boolean bulk_update_in_progress;

  private static final java.util.Map<Locale, TextManager> text_manager_cache = new ConcurrentHashMap<>();

  public WindowVisibility(BoardFrame board_frame) {
    this.board_panel = board_frame.board_panel;
    setLanguage(board_frame.get_locale());

    TextManager tm = new TextManager(WindowVisibility.class, board_frame.get_locale());
    this.setTitle(tm.getText("title"));

    LayerStructure layer_structure = board_panel.board_handling.get_routing_board().layer_structure;
    String[] layer_messages = new String[layer_structure.arr.length];
    for (int i = 0; i < layer_messages.length; i++) {
      layer_messages[i] = layer_structure.arr[i].name;
    }

    String[] object_messages = new String[ObjectNames.values().length];
    for (int i = 0; i < object_messages.length; i++) {
      object_messages[i] = tm.getText(ObjectNames.values()[i].toString());
    }

    this.layer_section = new VisibilitySection(
        tm.getText("layer_section_title"),
        layer_messages,
        index -> get_board_handling().graphics_context.get_raw_layer_visibility(index),
        (index, value) -> get_board_handling().set_layer_visibility(index, value)
    );
    this.object_section = new VisibilitySection(
        tm.getText("object_section_title"),
        object_messages,
        index -> get_board_handling().graphics_context.color_intensity_table.get_value(index),
        (index, value) -> get_board_handling().graphics_context.color_intensity_table.set_value(index, value)
    );

    JPanel main_panel = new JPanel(new BorderLayout());
    getContentPane().add(main_panel);

    JPanel header_panel = new JPanel(new BorderLayout());
    JLabel header_message = new JLabel(tm.getText("header_message"), JLabel.CENTER);
    header_panel.add(header_message, BorderLayout.CENTER);
    header_panel.add(new JSeparator(), BorderLayout.SOUTH);
    main_panel.add(header_panel, BorderLayout.NORTH);

    JPanel content_panel = new JPanel(new GridBagLayout());
    JScrollPane scroll_pane = new JScrollPane(
        content_panel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scroll_pane.setPreferredSize(CONTENT_SIZE);
    scroll_pane.getVerticalScrollBar().setUnitIncrement(24);
    scroll_pane.getVerticalScrollBar().setBlockIncrement(72);
    scroll_pane.getHorizontalScrollBar().setUnitIncrement(24);
    main_panel.add(scroll_pane, BorderLayout.CENTER);

    bulk_update_in_progress = true;
    try {
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.insets = new Insets(4, 8, 4, 8);
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1.0;

      content_panel.add(layer_section.create_panel(), constraints);

      constraints.fill = GridBagConstraints.BOTH;
      constraints.weighty = 0.0;
      constraints.insets = new Insets(6, 8, 6, 8);
      content_panel.add(new JSeparator(), constraints);

      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.insets = new Insets(4, 8, 4, 8);
      content_panel.add(object_section.create_panel(), constraints);
    } finally {
      bulk_update_in_progress = false;
    }

    JPanel button_row_panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    TextManager visibility_tm = text_manager_cache.computeIfAbsent(
        board_frame.get_locale(),
        locale -> new TextManager(WindowVisibility.class, locale));

    JButton reset_button = new JButton(visibility_tm.getText("reset_value"));
    reset_button.setToolTipText(visibility_tm.getText("reset_value_tooltip"));
    reset_button.addActionListener(_ -> {
      reset_to_defaults();
      board_panel.repaint();
    });
    reset_button.addActionListener(_ -> FRAnalytics.buttonClicked("visibility_reset_button", reset_button.getText()));
    button_row_panel.add(reset_button);

    JPanel footer_panel = new JPanel(new BorderLayout());
    footer_panel.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 12));
    footer_panel.add(new JSeparator(), BorderLayout.NORTH);
    footer_panel.add(button_row_panel, BorderLayout.CENTER);
    main_panel.add(footer_panel, BorderLayout.SOUTH);

    this.pack();
    this.setResizable(false);
  }

  public void refresh() {
    bulk_update_in_progress = true;
    try {
      layer_section.refresh();
      object_section.refresh();
    } finally {
      bulk_update_in_progress = false;
    }
  }

  protected GuiBoardManager get_board_handling() {
    return board_panel.board_handling;
  }

  protected void reset_to_defaults() {
    bulk_update_in_progress = true;
    try {
      layer_section.reset_to_defaults();
      object_section.reset_to_defaults();
    } finally {
      bulk_update_in_progress = false;
    }
  }

  private int snap_to_step(int value) {
    int snapped_value = Math.round((float) value / (float) SLIDER_STEP) * SLIDER_STEP;
    return Math.max(0, Math.min(MAX_SLIDER_VALUE, snapped_value));
  }

  private void set_slider_text_value(JTextField value_field, int value) {
    value_field.setText(value + "%");
  }

  private final class SliderChangeListener implements ChangeListener {
    private final VisibilitySection section;
    private final int slider_no;

    private SliderChangeListener(VisibilitySection section, int slider_no) {
      this.section = section;
      this.slider_no = slider_no;
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
      int current_value = section.slider_arr[slider_no].getValue();
      int snapped_value = snap_to_step(current_value);

      if (current_value != snapped_value) {
        section.slider_arr[slider_no].setValue(snapped_value);
        return;
      }

      set_slider_text_value(section.value_arr[slider_no], current_value);

      if (bulk_update_in_progress || section.slider_arr[slider_no].getValueIsAdjusting()) {
        return;
      }

      section.set_changed_value(slider_no, ((double) snapped_value) / ((double) MAX_SLIDER_VALUE));
      board_panel.repaint();
    }
  }

  private final class VisibilitySection {
    private final String title;
    private final String[] message_arr;
    private final JSlider[] slider_arr;
    private final JTextField[] value_arr;
    private final int[] original_defaults;
    private final boolean[] defaults_set;
    private final IntToDoubleFunction current_value_supplier;
    private final BiConsumer<Integer, Double> changed_value_consumer;

    private VisibilitySection(String title, String[] message_arr, IntToDoubleFunction current_value_supplier,
        BiConsumer<Integer, Double> changed_value_consumer) {
      this.title = title;
      this.message_arr = message_arr;
      this.current_value_supplier = current_value_supplier;
      this.changed_value_consumer = changed_value_consumer;
      this.slider_arr = new JSlider[message_arr.length];
      this.value_arr = new JTextField[message_arr.length];
      this.original_defaults = new int[message_arr.length];
      this.defaults_set = new boolean[message_arr.length];
    }

    private JPanel create_panel() {
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.insets = new Insets(4, 8, 4, 8);
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1.0;

      JPanel panel = new JPanel(new GridBagLayout());

      JLabel section_title = new JLabel(title, JLabel.LEFT);
      section_title.setFont(section_title.getFont().deriveFont(java.awt.Font.BOLD));
      panel.add(section_title, constraints);

      for (int i = 0; i < message_arr.length; i++) {
        add_row(panel, constraints, i);
      }

      return panel;
    }

    private void add_row(JPanel panel, GridBagConstraints constraints, int index) {
      JPanel row_panel = new JPanel(new BorderLayout(2, 0));

      JLabel label = new JLabel(message_arr[index], JLabel.LEFT);
      Dimension label_size = new Dimension(LABEL_WIDTH, label.getPreferredSize().height);
      label.setPreferredSize(label_size);
      row_panel.add(label, BorderLayout.WEST);

      slider_arr[index] = new JSlider(0, MAX_SLIDER_VALUE);
      slider_arr[index].setMajorTickSpacing(SLIDER_STEP);
      slider_arr[index].setMinorTickSpacing(SLIDER_STEP);
      slider_arr[index].setPaintTicks(true);
      slider_arr[index].setSnapToTicks(true);
      Dimension slider_size = new Dimension(SLIDER_WIDTH, slider_arr[index].getPreferredSize().height);
      slider_arr[index].setPreferredSize(slider_size);
      slider_arr[index].addChangeListener(new SliderChangeListener(this, index));
      row_panel.add(slider_arr[index], BorderLayout.CENTER);

      value_arr[index] = new JTextField(5);
      value_arr[index].setEditable(false);
      value_arr[index].setHorizontalAlignment(JTextField.RIGHT);
      Dimension value_size = new Dimension(VALUE_FIELD_WIDTH, value_arr[index].getPreferredSize().height);
      value_arr[index].setPreferredSize(value_size);
      row_panel.add(value_arr[index], BorderLayout.EAST);

      panel.add(row_panel, constraints);

      set_slider_value(index, current_value_supplier.applyAsDouble(index));
    }

    private void refresh() {
      for (int i = 0; i < message_arr.length; i++) {
        set_slider_value(i, current_value_supplier.applyAsDouble(i));
      }
    }

    private void reset_to_defaults() {
      for (int i = 0; i < message_arr.length; i++) {
        if (defaults_set[i]) {
          int original_val = original_defaults[i];
          slider_arr[i].setValue(original_val);
          set_slider_text_value(value_arr[i], original_val);
          changed_value_consumer.accept(i, ((double) original_val) / ((double) MAX_SLIDER_VALUE));
        }
      }
    }

    private void set_slider_value(int index, double value) {
      int visibility = (int) Math.round(value * MAX_SLIDER_VALUE);
      visibility = Math.max(0, Math.min(MAX_SLIDER_VALUE, visibility));

      if (!defaults_set[index]) {
        original_defaults[index] = visibility;
        defaults_set[index] = true;
      }

      slider_arr[index].setValue(visibility);
      set_slider_text_value(value_arr[index], visibility);
    }

    private void set_changed_value(int index, double value) {
      changed_value_consumer.accept(index, value);
    }
  }
}
