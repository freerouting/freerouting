package app.freerouting.gui;

import app.freerouting.interactive.BoardHandling;

import app.freerouting.management.FRAnalytics;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Interactive Frame to adjust the visibility of a set of objects */
public abstract class WindowVisibility extends BoardSavableSubWindow {

  private static final int MAX_SLIDER_VALUE = 100;
  private final BoardPanel board_panel;
  private final JLabel header_message;
  private final JLabel[] message_arr;
  private final JSlider[] slider_arr;

  /** Creates a new instance of VisibilityFrame */
  public WindowVisibility(
      BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr) {
    this.board_panel = p_board_frame.board_panel;
    this.setTitle(p_title);

    // create main panel
    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);

    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    header_message = new JLabel();
    header_message.setText(p_header_message);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.ipady = 10;
    gridbag.setConstraints(header_message, gridbag_constraints);
    main_panel.add(header_message);
    slider_arr = new JSlider[p_message_arr.length];
    message_arr = new JLabel[p_message_arr.length];
    gridbag_constraints.ipady = 0;
    for (int i = 0; i < p_message_arr.length; ++i) {
      message_arr[i] = new JLabel();

      message_arr[i].setText(p_message_arr[i]);
      gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
      gridbag.setConstraints(message_arr[i], gridbag_constraints);
      main_panel.add(message_arr[i]);

      slider_arr[i] = new JSlider(0, MAX_SLIDER_VALUE);
      gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
      gridbag.setConstraints(slider_arr[i], gridbag_constraints);
      main_panel.add(slider_arr[i]);

      slider_arr[i].addChangeListener(new SliderChangeListener(i));
    }
    JLabel empty_label = new JLabel();
    gridbag.setConstraints(empty_label, gridbag_constraints);
    main_panel.add(empty_label);
    gridbag_constraints.gridwidth = 2;

    ResourceBundle resources = ResourceBundle.getBundle("app.freerouting.gui.Default", p_board_frame.get_locale());

    JButton appearance_layer_visibility_min_all_button = new JButton(resources.getString("minimum_all"));
    appearance_layer_visibility_min_all_button.setToolTipText(resources.getString("minimum_all_tooltip"));
    appearance_layer_visibility_min_all_button.addActionListener(new MinAllButtonListener());
    appearance_layer_visibility_min_all_button.addActionListener(evt -> FRAnalytics.buttonClicked("appearance_layer_visibility_min_all_button", appearance_layer_visibility_min_all_button.getText()));
    gridbag.setConstraints(appearance_layer_visibility_min_all_button, gridbag_constraints);
    main_panel.add(appearance_layer_visibility_min_all_button);

    JButton appearance_layer_visibility_max_all_button = new JButton(resources.getString("maximum_all"));
    appearance_layer_visibility_max_all_button.setToolTipText(resources.getString("maximum_all_tooltip"));
    appearance_layer_visibility_max_all_button.addActionListener(new MaxAllButtonListener());
    appearance_layer_visibility_max_all_button.addActionListener(evt -> FRAnalytics.buttonClicked("appearance_layer_visibility_max_all_button", appearance_layer_visibility_max_all_button.getText()));
    gridbag.setConstraints(appearance_layer_visibility_max_all_button, gridbag_constraints);
    main_panel.add(appearance_layer_visibility_max_all_button);

    this.pack();
    this.setResizable(false);
  }

  // private data

  /** Sets the values of the p_no-ths slider contained in this frame. */
  public void set_slider_value(int p_no, double p_value) {
    int visibility = (int) Math.round(p_value * MAX_SLIDER_VALUE);
    slider_arr[p_no].setValue(visibility);
  }

  protected BoardHandling get_board_handling() {
    return board_panel.board_handling;
  }

  protected void set_all_minimum() {
    for (int i = 0; i < slider_arr.length; ++i) {
      set_slider_value(i, 0);
      set_changed_value(i, 0);
    }
  }

  protected void set_all_maximum() {
    for (int i = 0; i < slider_arr.length; ++i) {
      set_slider_value(i, MAX_SLIDER_VALUE);
      set_changed_value(i, 1);
    }
  }

  /** Stores the new value in the board database, when a slider value was changed. */
  protected abstract void set_changed_value(int p_index, double p_value);

  // private classes

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

  /** p_slider_no is required to identify the number of the slider in slider_arr. */
  private class SliderChangeListener implements ChangeListener {
    public int slider_no;

    public SliderChangeListener(int p_slider_no) {
      slider_no = p_slider_no;
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
      int new_visibility = slider_arr[slider_no].getValue();
      set_changed_value(slider_no, ((double) new_visibility) / ((double) MAX_SLIDER_VALUE));
      board_panel.repaint();
    }
  }
}
