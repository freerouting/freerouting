package app.freerouting.gui;

import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Interactive Frame to adjust the visibility of a set of objects
 */
public abstract class WindowVisibility extends BoardSavableSubWindow {

  private static final int MAX_SLIDER_VALUE = 100;
  private static final int SLIDER_WIDTH = 160;
  private static final int LABEL_WIDTH = 145;
  private static final int VALUE_FIELD_WIDTH = 44;
  private final BoardPanel board_panel;
  private final JLabel header_message;
  private final JLabel[] message_arr;
  private final JSlider[] slider_arr;
  private final JTextField[] value_arr;
  private boolean bulk_update_in_progress;

  /**
   * Creates a new instance of VisibilityFrame
   */
  public WindowVisibility(BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr) {
    this.board_panel = p_board_frame.board_panel;

    setLanguage(p_board_frame.get_locale());

    this.setTitle(p_title);

    // create main panel
    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);

    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(4, 8, 4, 8);
    header_message = new JLabel();
    header_message.setText(p_header_message);
    header_message.setHorizontalAlignment(JLabel.CENTER);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.ipady = 10;
    gridbag.setConstraints(header_message, gridbag_constraints);
    main_panel.add(header_message);

    slider_arr = new JSlider[p_message_arr.length];
    message_arr = new JLabel[p_message_arr.length];
    value_arr = new JTextField[p_message_arr.length];
    gridbag_constraints.ipady = 0;

    for (int i = 0; i < p_message_arr.length; i++) {
      message_arr[i] = new JLabel();
      message_arr[i].setText(p_message_arr[i]);
      message_arr[i].setHorizontalAlignment(JLabel.CENTER);
      // Pin all labels to the same fixed width so every slider gets identical space
      Dimension label_size = new Dimension(LABEL_WIDTH, message_arr[i].getPreferredSize().height);
      message_arr[i].setPreferredSize(label_size);
      message_arr[i].setMinimumSize(label_size);
      message_arr[i].setMaximumSize(label_size);

      JPanel row_panel = new JPanel(new BorderLayout(2, 0));
      row_panel.add(message_arr[i], BorderLayout.WEST);

      slider_arr[i] = new JSlider(0, MAX_SLIDER_VALUE);
      Dimension slider_size = new Dimension(SLIDER_WIDTH, slider_arr[i].getPreferredSize().height);
      slider_arr[i].setPreferredSize(slider_size);
      slider_arr[i].setMinimumSize(slider_size);
      slider_arr[i].setMaximumSize(slider_size);
      row_panel.add(slider_arr[i], BorderLayout.CENTER);

      value_arr[i] = new JTextField(5);
      value_arr[i].setEditable(false);
      value_arr[i].setHorizontalAlignment(JTextField.RIGHT);
      value_arr[i].setColumns(4);
      Dimension value_size = new Dimension(VALUE_FIELD_WIDTH, value_arr[i].getPreferredSize().height);
      value_arr[i].setPreferredSize(value_size);
      value_arr[i].setMinimumSize(value_size);
      value_arr[i].setMaximumSize(value_size);
      row_panel.add(value_arr[i], BorderLayout.EAST);

      gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
      gridbag_constraints.fill = GridBagConstraints.HORIZONTAL;
      gridbag_constraints.weightx = 1.0;
      gridbag.setConstraints(row_panel, gridbag_constraints);
      main_panel.add(row_panel);

      slider_arr[i].addChangeListener(new SliderChangeListener(i));
      set_slider_text_value(i, slider_arr[i].getValue());
    }

    JLabel empty_label = new JLabel();
    gridbag.setConstraints(empty_label, gridbag_constraints);
    main_panel.add(empty_label);

    JPanel button_panel = new JPanel(new java.awt.GridLayout(1, 2, 20, 0));
    JPanel button_row_panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    button_row_panel.add(button_panel);

    TextManager visibility_tm = new TextManager(WindowVisibility.class, p_board_frame.get_locale());

    JButton appearance_layer_visibility_min_all_button = new JButton(visibility_tm.getText("minimum_all"));
    appearance_layer_visibility_min_all_button.setToolTipText(visibility_tm.getText("minimum_all_tooltip"));
    appearance_layer_visibility_min_all_button.addActionListener(new MinAllButtonListener());
    appearance_layer_visibility_min_all_button.addActionListener(_ -> FRAnalytics.buttonClicked("appearance_layer_visibility_min_all_button", appearance_layer_visibility_min_all_button.getText()));
    button_panel.add(appearance_layer_visibility_min_all_button);

    JButton appearance_layer_visibility_max_all_button = new JButton(visibility_tm.getText("maximum_all"));
    appearance_layer_visibility_max_all_button.setToolTipText(visibility_tm.getText("maximum_all_tooltip"));
    appearance_layer_visibility_max_all_button.addActionListener(new MaxAllButtonListener());
    appearance_layer_visibility_max_all_button.addActionListener(_ -> FRAnalytics.buttonClicked("appearance_layer_visibility_max_all_button", appearance_layer_visibility_max_all_button.getText()));
    button_panel.add(appearance_layer_visibility_max_all_button);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.fill = GridBagConstraints.NONE;
    gridbag_constraints.weightx = 0.0;
    gridbag_constraints.anchor = GridBagConstraints.CENTER;
    gridbag.setConstraints(button_row_panel, gridbag_constraints);
    main_panel.add(button_row_panel);

    this.pack();
    this.setResizable(false);
  }

  /**
   * Sets the value of the p_no-th slider contained in this frame.
   */
  public void set_slider_value(int p_no, double p_value) {
    if (p_no < 0 || p_no >= slider_arr.length) {
      return;
    }
    int visibility = (int) Math.round(p_value * MAX_SLIDER_VALUE);
    // Clamp to valid range to guard against out-of-range inputs
    visibility = Math.max(0, Math.min(MAX_SLIDER_VALUE, visibility));
    slider_arr[p_no].setValue(visibility);
    set_slider_text_value(p_no, visibility);
  }

  private void set_slider_text_value(int p_no, int p_value) {
    value_arr[p_no].setText(p_value + "%");
  }

  protected GuiBoardManager get_board_handling() {
    return board_panel.board_handling;
  }

  protected void set_all_minimum() {
    bulk_update_in_progress = true;
    try {
      for (int i = 0; i < slider_arr.length; i++) {
        set_slider_value(i, 0);
        set_changed_value(i, 0);
      }
    } finally {
      bulk_update_in_progress = false;
    }
  }

  protected void set_all_maximum() {
    bulk_update_in_progress = true;
    try {
      for (int i = 0; i < slider_arr.length; i++) {
        set_slider_value(i, 1.0);
        set_changed_value(i, 1);
      }
    } finally {
      bulk_update_in_progress = false;
    }
  }

  /**
   * Stores the new value in the board database when a slider value was changed.
   */
  protected abstract void set_changed_value(int p_index, double p_value);

  private class MinAllButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      set_all_minimum();
      board_panel.repaint();
    }
  }

  private class MaxAllButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      set_all_maximum();
      board_panel.repaint();
    }
  }

  /**
   * p_slider_no identifies the index of the slider in slider_arr.
   */
  private class SliderChangeListener implements ChangeListener {

    public final int slider_no;

    public SliderChangeListener(int p_slider_no) {
      slider_no = p_slider_no;
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
      int new_visibility = slider_arr[slider_no].getValue();
      set_slider_text_value(slider_no, new_visibility);
      if (bulk_update_in_progress) {
        return;
      }
      set_changed_value(slider_no, ((double) new_visibility) / ((double) MAX_SLIDER_VALUE));
      board_panel.repaint();
    }
  }
}