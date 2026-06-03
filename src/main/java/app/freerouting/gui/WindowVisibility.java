package app.freerouting.gui;

import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public abstract class WindowVisibility extends BoardSavableSubWindow {

  protected static final int MAX_SLIDER_VALUE = 100;
  private static final int SLIDER_WIDTH = 160;
  private static final int LABEL_WIDTH = 145;
  private static final int VALUE_FIELD_WIDTH = 44;

  protected final BoardPanel board_panel;
  private final JLabel[] message_arr;
  private final JSlider[] slider_arr;
  private final JTextField[] value_arr;
  
  private final int[] original_defaults;
  private final boolean[] defaults_set;

  private static final java.util.Map<java.util.Locale, TextManager> text_manager_cache = new java.util.concurrent.ConcurrentHashMap<>();
  protected boolean bulk_update_in_progress;

  public WindowVisibility(BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr) {
    this.board_panel = p_board_frame.board_panel;
    setLanguage(p_board_frame.get_locale());
    this.setTitle(p_title);

    final JPanel main_panel = new JPanel(new GridBagLayout());
    getContentPane().add(main_panel);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = new Insets(4, 8, 4, 8);
    
    JLabel header_message = new JLabel(p_header_message, JLabel.CENTER);
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.ipady = 10;
    main_panel.add(header_message, constraints);

    int items_count = p_message_arr.length;
    slider_arr = new JSlider[items_count];
    message_arr = new JLabel[items_count];
    value_arr = new JTextField[items_count];
    original_defaults = new int[items_count];
    defaults_set = new boolean[items_count];
    
    constraints.ipady = 0;

    for (int i = 0; i < items_count; i++) {
      message_arr[i] = new JLabel(p_message_arr[i], JLabel.LEFT); 
      Dimension label_size = new Dimension(LABEL_WIDTH, message_arr[i].getPreferredSize().height);
      message_arr[i].setPreferredSize(label_size);

      JPanel row_panel = new JPanel(new BorderLayout(2, 0));
      row_panel.add(message_arr[i], BorderLayout.WEST);

      slider_arr[i] = new JSlider(0, MAX_SLIDER_VALUE);
      slider_arr[i].setMajorTickSpacing(10); 
      slider_arr[i].setPaintTicks(true);     
      slider_arr[i].setSnapToTicks(true); // Allows smooth gliding, snaps on release
      
      Dimension slider_size = new Dimension(SLIDER_WIDTH, slider_arr[i].getPreferredSize().height);
      slider_arr[i].setPreferredSize(slider_size);
      row_panel.add(slider_arr[i], BorderLayout.CENTER);

      value_arr[i] = new JTextField(5);
      value_arr[i].setEditable(false);
      value_arr[i].setHorizontalAlignment(JTextField.RIGHT);
      Dimension value_size = new Dimension(VALUE_FIELD_WIDTH, value_arr[i].getPreferredSize().height);
      value_arr[i].setPreferredSize(value_size);
      row_panel.add(value_arr[i], BorderLayout.EAST);

      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1.0;
      main_panel.add(row_panel, constraints);

      slider_arr[i].addChangeListener(new SliderChangeListener(i));
      set_slider_text_value(i, slider_arr[i].getValue());
    }

    main_panel.add(new JLabel(), constraints);

    JPanel button_row_panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    TextManager visibility_tm = text_manager_cache.computeIfAbsent(
        p_board_frame.get_locale(),
        locale -> new TextManager(WindowVisibility.class, locale)
    );

    // Simplified ActionListeners using modern Java lambdas to remove bulky classes
    JButton reset_button = new JButton(visibility_tm.getText("reset_defaults"));
    reset_button.setToolTipText(visibility_tm.getText("reset_defaults_tooltip"));
    reset_button.addActionListener(_ -> {
      reset_to_defaults();
      board_panel.repaint();
    });
    reset_button.addActionListener(_ -> FRAnalytics.buttonClicked("appearance_layer_visibility_reset_button", reset_button.getText()));
    button_row_panel.add(reset_button);

    constraints.fill = GridBagConstraints.NONE;
    constraints.weightx = 0.0;
    constraints.anchor = GridBagConstraints.CENTER;
    main_panel.add(button_row_panel, constraints);

    this.pack();
    this.setResizable(false);
  }

  public void set_slider_value(int p_no, double p_value) {
    if (p_no < 0 || p_no >= slider_arr.length) {
      return;
    }
    
    int visibility = (int) Math.round(p_value * MAX_SLIDER_VALUE);
    visibility = Math.max(0, Math.min(MAX_SLIDER_VALUE, visibility));
    
    if (!defaults_set[p_no]) {
        original_defaults[p_no] = visibility;
        defaults_set[p_no] = true;
    }

    slider_arr[p_no].setValue(visibility);
    set_slider_text_value(p_no, visibility);
  }

  private void set_slider_text_value(int p_no, int p_value) {
    value_arr[p_no].setText(p_value + "%");
  }

  protected GuiBoardManager get_board_handling() {
    return board_panel.board_handling;
  }

  protected void reset_to_defaults() {
    bulk_update_in_progress = true;
    try {
      for (int i = 0; i < slider_arr.length; i++) {
        if (defaults_set[i]) {
            int original_val = original_defaults[i];
            slider_arr[i].setValue(original_val); 
            set_slider_text_value(i, original_val);
            set_changed_value(i, ((double) original_val) / ((double) MAX_SLIDER_VALUE));
        }
      }
    } finally {
      bulk_update_in_progress = false;
    }
  }

  protected abstract void set_changed_value(int p_index, double p_value);

  private class SliderChangeListener implements ChangeListener {
    public final int slider_no;

    public SliderChangeListener(int p_slider_no) {
      slider_no = p_slider_no;
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
      int current_value = slider_arr[slider_no].getValue();
      
      // Live text updates while dragging for a smooth UI experience
      set_slider_text_value(slider_no, current_value);
      
      // Stop execution if adjusting (dragging) or if bulk updating to prevent heavy visual lag
      if (bulk_update_in_progress || slider_arr[slider_no].getValueIsAdjusting()) {
        return;
      }
      
      // Apply the final snapped value to the board data and render it once let go
      set_changed_value(slider_no, ((double) current_value) / ((double) MAX_SLIDER_VALUE));
      board_panel.repaint();
    }
  }
}