package app.freerouting.gui;

import app.freerouting.interactive.BoardHandling;

import app.freerouting.management.FRAnalytics;
import javax.swing.ButtonGroup;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ResourceBundle;

/** Window with the parameters for moving components. */
public class WindowMoveParameter extends BoardSavableSubWindow {

  private final BoardHandling board_handling;
  private final JFormattedTextField horizontal_grid_field;
  private final JFormattedTextField vertical_grid_field;
  private final JRadioButton settings_controls_zoom_radiobutton;
  private final JRadioButton settings_controls_rotate_radiobutton;
  private boolean key_input_completed = true;
  /** Creates a new instance of WindowMoveParameter */
  public WindowMoveParameter(BoardFrame p_board_frame) {
    this.board_handling = p_board_frame.board_panel.board_handling;
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowMoveParameter", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    // create main panel

    final JPanel main_panel = new JPanel();
    this.add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.anchor = GridBagConstraints.WEST;
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    // Create label and number field for the horizontal and vertical component grid

    gridbag_constraints.gridwidth = 2;
    JLabel horizontal_grid_label =
        new JLabel(resources.getString("horizontal_component_grid"));
    gridbag.setConstraints(horizontal_grid_label, gridbag_constraints);
    main_panel.add(horizontal_grid_label);

    NumberFormat number_format =
        NumberFormat.getInstance(p_board_frame.get_locale());
    number_format.setMaximumFractionDigits(7);
    this.horizontal_grid_field = new JFormattedTextField(number_format);
    this.horizontal_grid_field.setColumns(5);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(horizontal_grid_field, gridbag_constraints);
    main_panel.add(horizontal_grid_field);
    set_horizontal_grid_field(this.board_handling.settings.get_horizontal_component_grid());
    horizontal_grid_field.addKeyListener(new HorizontalGridFieldKeyListener());
    horizontal_grid_field.addFocusListener(new HorizontalGridFieldFocusListener());

    gridbag_constraints.gridwidth = 2;
    JLabel vertical_grid_label =
        new JLabel(resources.getString("vertical_component_grid"));
    gridbag.setConstraints(vertical_grid_label, gridbag_constraints);
    main_panel.add(vertical_grid_label);

    this.vertical_grid_field = new JFormattedTextField(number_format);
    this.vertical_grid_field.setColumns(5);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(vertical_grid_field, gridbag_constraints);
    main_panel.add(vertical_grid_field);
    set_vertical_grid_field(this.board_handling.settings.get_vertical_component_grid());
    vertical_grid_field.addKeyListener(new VerticalGridFieldKeyListener());
    vertical_grid_field.addFocusListener(new VerticalGridFieldFocusListener());

    JLabel separator =
        new JLabel("  –––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and button group for the wheel function.

    JLabel wheel_function_label =
        new JLabel(resources.getString("wheel_function"));
    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 2;
    gridbag.setConstraints(wheel_function_label, gridbag_constraints);
    main_panel.add(wheel_function_label);
    wheel_function_label.setToolTipText(resources.getString("wheel_function_tooltip"));

    settings_controls_zoom_radiobutton = new JRadioButton(resources.getString("zoom"));
    settings_controls_rotate_radiobutton = new JRadioButton(resources.getString("rotate"));

    settings_controls_zoom_radiobutton.addActionListener(new ZoomButtonListener());
    settings_controls_zoom_radiobutton.addActionListener(evt -> FRAnalytics.buttonClicked("settings_controls_zoom_radiobutton", settings_controls_zoom_radiobutton.getText()));
    settings_controls_rotate_radiobutton.addActionListener(new RotateButtonListener());
    settings_controls_rotate_radiobutton.addActionListener(evt -> FRAnalytics.buttonClicked("settings_controls_rotate_radiobutton", settings_controls_rotate_radiobutton.getText()));

    ButtonGroup button_group = new ButtonGroup();
    button_group.add(settings_controls_zoom_radiobutton);
    button_group.add(settings_controls_rotate_radiobutton);
    if (this.board_handling.settings.get_zoom_with_wheel()) {
      settings_controls_zoom_radiobutton.setSelected(true);
    } else {
      settings_controls_rotate_radiobutton.setSelected(true);
    }

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(settings_controls_zoom_radiobutton, gridbag_constraints);
    main_panel.add(settings_controls_zoom_radiobutton, gridbag_constraints);
    gridbag.setConstraints(settings_controls_rotate_radiobutton, gridbag_constraints);
    main_panel.add(settings_controls_rotate_radiobutton, gridbag_constraints);

    p_board_frame.set_context_sensitive_help(this, "WindowMoveParameter");

    this.refresh();
    this.pack();
    this.setResizable(false);
  }

  private void set_horizontal_grid_field(double p_value) {
    if (p_value <= 0) {
      this.horizontal_grid_field.setValue(0);
    } else {
      Float grid_width = (float) board_handling.coordinate_transform.board_to_user(p_value);
      this.horizontal_grid_field.setValue(grid_width);
    }
  }

  private void set_vertical_grid_field(double p_value) {
    if (p_value <= 0) {
      this.vertical_grid_field.setValue(0);
    } else {
      Float grid_width = (float) board_handling.coordinate_transform.board_to_user(p_value);
      this.vertical_grid_field.setValue(grid_width);
    }
  }

  private class HorizontalGridFieldKeyListener extends KeyAdapter {
    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        key_input_completed = true;
        Object input = horizontal_grid_field.getValue();
        double input_value;
        if (!(input instanceof Number)) {
          input_value = 0;
        }
        input_value = ((Number) input).doubleValue();
        if (input_value < 0) {
          input_value = 0;
        }
        board_handling.settings.set_horizontal_component_grid(
            (int) Math.round(board_handling.coordinate_transform.user_to_board(input_value)));
        set_horizontal_grid_field(board_handling.settings.get_horizontal_component_grid());
      } else {
        key_input_completed = false;
      }
    }
  }

  private class HorizontalGridFieldFocusListener implements FocusListener {
    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!key_input_completed) {
        // restore the text field.
        set_horizontal_grid_field(board_handling.settings.get_horizontal_component_grid());
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class VerticalGridFieldKeyListener extends KeyAdapter {
    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        key_input_completed = true;
        Object input = vertical_grid_field.getValue();
        double input_value;
        if (!(input instanceof Number)) {
          input_value = 0;
        }
        input_value = ((Number) input).doubleValue();
        if (input_value < 0) {
          input_value = 0;
        }
        board_handling.settings.set_vertical_component_grid(
            (int) Math.round(board_handling.coordinate_transform.user_to_board(input_value)));
        set_vertical_grid_field(board_handling.settings.get_vertical_component_grid());
      } else {
        key_input_completed = false;
      }
    }
  }

  private class VerticalGridFieldFocusListener implements FocusListener {
    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!key_input_completed) {
        // restore the text field.
        set_vertical_grid_field(board_handling.settings.get_vertical_component_grid());
        key_input_completed = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class ZoomButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_zoom_with_wheel(true);
    }
  }

  private class RotateButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_zoom_with_wheel(false);
    }
  }
}
