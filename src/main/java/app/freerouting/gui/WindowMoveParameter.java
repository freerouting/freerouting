package app.freerouting.gui;

import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveSettings;
import app.freerouting.management.analytics.FRAnalytics;
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
import javax.swing.ButtonGroup;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/** Window with the parameters for moving components. */
public class WindowMoveParameter extends BoardSavableSubWindow {

  private final GuiBoardManager boardHandling;
  private final JFormattedTextField horizontalGridField;
  private final JFormattedTextField verticalGridField;
  private final JRadioButton settingsControlsZoomRadiobutton;
  private final JRadioButton settingsControlsRotateRadiobutton;
  private boolean keyInputCompleted = true;

  /** Creates a new instance of WindowMoveParameter */
  public WindowMoveParameter(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());
    this.boardHandling = p_board_frame.boardPanel.boardHandling;

    this.setTitle(tm.getText("title"));

    // create main panel

    final JPanel mainPanel = new JPanel();
    this.add(mainPanel);
    GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.anchor = GridBagConstraints.WEST;
    gridbagConstraints.insets = new Insets(1, 10, 1, 10);

    // Create label and number field for the horizontal and vertical component grid

    gridbagConstraints.gridwidth = 2;
    JLabel horizontalGridLabel = new JLabel(tm.getText("horizontalComponentGrid"));
    gridbag.setConstraints(horizontalGridLabel, gridbagConstraints);
    mainPanel.add(horizontalGridLabel);

    NumberFormat numberFormat = NumberFormat.getInstance(p_board_frame.get_locale());
    numberFormat.setMaximumFractionDigits(7);
    this.horizontalGridField = new JFormattedTextField(numberFormat);
    this.horizontalGridField.setColumns(5);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(horizontalGridField, gridbagConstraints);
    mainPanel.add(horizontalGridField);
    set_horizontal_grid_field(
        this.boardHandling.getInteractiveSettings().get_horizontal_component_grid());
    horizontalGridField.addKeyListener(new HorizontalGridFieldKeyListener());
    horizontalGridField.addFocusListener(new HorizontalGridFieldFocusListener());

    gridbagConstraints.gridwidth = 2;
    JLabel verticalGridLabel = new JLabel(tm.getText("verticalComponentGrid"));
    gridbag.setConstraints(verticalGridLabel, gridbagConstraints);
    mainPanel.add(verticalGridLabel);

    this.verticalGridField = new JFormattedTextField(numberFormat);
    this.verticalGridField.setColumns(5);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(verticalGridField, gridbagConstraints);
    mainPanel.add(verticalGridField);
    set_vertical_grid_field(
        this.boardHandling.getInteractiveSettings().get_vertical_component_grid());
    verticalGridField.addKeyListener(new VerticalGridFieldKeyListener());
    verticalGridField.addFocusListener(new VerticalGridFieldFocusListener());

    JLabel separator = new JLabel("  –––––––––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);

    // add label and button group for the wheel function.

    JLabel wheelFunctionLabel = new JLabel(tm.getText("wheel_function"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 2;
    gridbag.setConstraints(wheelFunctionLabel, gridbagConstraints);
    mainPanel.add(wheelFunctionLabel);
    wheelFunctionLabel.setToolTipText(tm.getText("wheel_function_tooltip"));

    settingsControlsZoomRadiobutton = new JRadioButton(tm.getText("zoom"));
    settingsControlsRotateRadiobutton = new JRadioButton(tm.getText("rotate"));

    settingsControlsZoomRadiobutton.addActionListener(new ZoomButtonListener());
    settingsControlsZoomRadiobutton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsControlsZoomRadiobutton", settingsControlsZoomRadiobutton.getText()));
    settingsControlsRotateRadiobutton.addActionListener(new RotateButtonListener());
    settingsControlsRotateRadiobutton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsControlsRotateRadiobutton", settingsControlsRotateRadiobutton.getText()));

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(settingsControlsZoomRadiobutton);
    buttonGroup.add(settingsControlsRotateRadiobutton);
    if (this.boardHandling.getInteractiveSettings().get_zoom_with_wheel()) {
      settingsControlsZoomRadiobutton.setSelected(true);
    } else {
      settingsControlsRotateRadiobutton.setSelected(true);
    }

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;
    gridbag.setConstraints(settingsControlsZoomRadiobutton, gridbagConstraints);
    mainPanel.add(settingsControlsZoomRadiobutton, gridbagConstraints);
    gridbag.setConstraints(settingsControlsRotateRadiobutton, gridbagConstraints);
    mainPanel.add(settingsControlsRotateRadiobutton, gridbagConstraints);

    this.refresh();
    this.pack();
    this.setResizable(false);

    // Subscribe to the InteractiveSettings singleton so this window stays in sync.
    InteractiveSettings is = this.boardHandling.getInteractiveSettings();
    if (is != null) {
      is.addPropertyChangeListener(_ -> javax.swing.SwingUtilities.invokeLater(this::refresh));
    }
  }

  private void set_horizontal_grid_field(double p_value) {
    if (p_value <= 0) {
      this.horizontalGridField.setValue(0);
    } else {
      Float gridWidth = (float) boardHandling.coordinateTransform.board_to_user(p_value);
      this.horizontalGridField.setValue(gridWidth);
    }
  }

  private void set_vertical_grid_field(double p_value) {
    if (p_value <= 0) {
      this.verticalGridField.setValue(0);
    } else {
      Float gridWidth = (float) boardHandling.coordinateTransform.board_to_user(p_value);
      this.verticalGridField.setValue(gridWidth);
    }
  }

  private class HorizontalGridFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        keyInputCompleted = true;
        Object input = horizontalGridField.getValue();
        double inputValue;
        if (!(input instanceof Number)) {
          inputValue = 0;
        }
        inputValue = ((Number) input).doubleValue();
        if (inputValue < 0) {
          inputValue = 0;
        }
        boardHandling
            .getInteractiveSettings()
            .set_horizontal_component_grid(
                (int) Math.round(boardHandling.coordinateTransform.user_to_board(inputValue)));
        set_horizontal_grid_field(
            boardHandling.getInteractiveSettings().get_horizontal_component_grid());
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class HorizontalGridFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!keyInputCompleted) {
        // restore the text field.
        set_horizontal_grid_field(
            boardHandling.getInteractiveSettings().get_horizontal_component_grid());
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class VerticalGridFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent p_evt) {
      if (p_evt.getKeyChar() == '\n') {
        keyInputCompleted = true;
        Object input = verticalGridField.getValue();
        double inputValue;
        if (!(input instanceof Number)) {
          inputValue = 0;
        }
        inputValue = ((Number) input).doubleValue();
        if (inputValue < 0) {
          inputValue = 0;
        }
        boardHandling
            .getInteractiveSettings()
            .set_vertical_component_grid(
                (int) Math.round(boardHandling.coordinateTransform.user_to_board(inputValue)));
        set_vertical_grid_field(
            boardHandling.getInteractiveSettings().get_vertical_component_grid());
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class VerticalGridFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent p_evt) {
      if (!keyInputCompleted) {
        // restore the text field.
        set_vertical_grid_field(
            boardHandling.getInteractiveSettings().get_vertical_component_grid());
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent p_evt) {}
  }

  private class ZoomButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      boardHandling.getInteractiveSettings().set_zoom_with_wheel(true);
    }
  }

  private class RotateButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      boardHandling.getInteractiveSettings().set_zoom_with_wheel(false);
    }
  }
}
