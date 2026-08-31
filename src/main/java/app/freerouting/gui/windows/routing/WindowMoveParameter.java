package app.freerouting.gui.windows.routing;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardSavableSubWindow;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceSettings;
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

  /** Creates a new instance of WindowMoveParameter. */
  public WindowMoveParameter(BoardFrame boardFrame) {
    setLanguage(boardFrame.getLocale());
    this.boardHandling = boardFrame.boardPanel.boardHandling;

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
    horizontalGridLabel.setToolTipText(tm.getText("horizontal_component_grid_tooltip"));
    gridbag.setConstraints(horizontalGridLabel, gridbagConstraints);
    mainPanel.add(horizontalGridLabel);

    NumberFormat numberFormat = NumberFormat.getInstance(boardFrame.getLocale());
    numberFormat.setMaximumFractionDigits(7);
    this.horizontalGridField = new JFormattedTextField(numberFormat);
    this.horizontalGridField.setColumns(5);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(horizontalGridField, gridbagConstraints);
    horizontalGridField.setToolTipText(tm.getText("horizontal_component_grid_tooltip"));
    mainPanel.add(horizontalGridField);
    setHorizontalGridField(this.boardHandling.getWorkspaceSettings().getHorizontalComponentGrid());
    horizontalGridField.addKeyListener(new HorizontalGridFieldKeyListener());
    horizontalGridField.addFocusListener(new HorizontalGridFieldFocusListener());

    gridbagConstraints.gridwidth = 2;
    JLabel verticalGridLabel = new JLabel(tm.getText("verticalComponentGrid"));
    verticalGridLabel.setToolTipText(tm.getText("vertical_component_grid_tooltip"));
    gridbag.setConstraints(verticalGridLabel, gridbagConstraints);
    mainPanel.add(verticalGridLabel);

    this.verticalGridField = new JFormattedTextField(numberFormat);
    this.verticalGridField.setColumns(5);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(verticalGridField, gridbagConstraints);
    verticalGridField.setToolTipText(tm.getText("vertical_component_grid_tooltip"));
    mainPanel.add(verticalGridField);
    setVerticalGridField(this.boardHandling.getWorkspaceSettings().getVerticalComponentGrid());
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
    if (this.boardHandling.getWorkspaceSettings().getZoomWithWheel()) {
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

    // Subscribe to the WorkspaceSettings singleton so this window stays in sync.
    WorkspaceSettings is = this.boardHandling.getWorkspaceSettings();
    if (is != null) {
      is.addPropertyChangeListener(_ -> javax.swing.SwingUtilities.invokeLater(this::refresh));
    }
  }

  private void setHorizontalGridField(double value) {
    if (value <= 0) {
      this.horizontalGridField.setValue(0);
    } else {
      Float gridWidth = (float) boardHandling.coordinateTransform.boardToUser(value);
      this.horizontalGridField.setValue(gridWidth);
    }
  }

  private void setVerticalGridField(double value) {
    if (value <= 0) {
      this.verticalGridField.setValue(0);
    } else {
      Float gridWidth = (float) boardHandling.coordinateTransform.boardToUser(value);
      this.verticalGridField.setValue(gridWidth);
    }
  }

  private class HorizontalGridFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
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
            .getWorkspaceSettings()
            .setHorizontalComponentGrid(
                (int) Math.round(boardHandling.coordinateTransform.userToBoard(inputValue)));
        setHorizontalGridField(boardHandling.getWorkspaceSettings().getHorizontalComponentGrid());
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class HorizontalGridFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!keyInputCompleted) {
        double oldValue =
            boardHandling.coordinateTransform.boardToUser(
                boardHandling.getWorkspaceSettings().getHorizontalComponentGrid());
        try {
          horizontalGridField.commitEdit();
        } catch (java.text.ParseException _) {
          horizontalGridField.setValue(oldValue);
        }
        Object input = horizontalGridField.getValue();
        double inputValue =
            (input instanceof Number number) ? Math.max(0, number.doubleValue()) : 0;
        boardHandling
            .getWorkspaceSettings()
            .setHorizontalComponentGrid(
                (int) Math.round(boardHandling.coordinateTransform.userToBoard(inputValue)));
        setHorizontalGridField(boardHandling.getWorkspaceSettings().getHorizontalComponentGrid());
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class VerticalGridFieldKeyListener extends KeyAdapter {

    @Override
    public void keyTyped(KeyEvent evt) {
      if (evt.getKeyChar() == '\n') {
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
            .getWorkspaceSettings()
            .setVerticalComponentGrid(
                (int) Math.round(boardHandling.coordinateTransform.userToBoard(inputValue)));
        setVerticalGridField(boardHandling.getWorkspaceSettings().getVerticalComponentGrid());
      } else {
        keyInputCompleted = false;
      }
    }
  }

  private class VerticalGridFieldFocusListener implements FocusListener {

    @Override
    public void focusLost(FocusEvent evt) {
      if (!keyInputCompleted) {
        double oldValue =
            boardHandling.coordinateTransform.boardToUser(
                boardHandling.getWorkspaceSettings().getVerticalComponentGrid());
        try {
          verticalGridField.commitEdit();
        } catch (java.text.ParseException _) {
          verticalGridField.setValue(oldValue);
        }
        Object input = verticalGridField.getValue();
        double inputValue =
            (input instanceof Number number) ? Math.max(0, number.doubleValue()) : 0;
        boardHandling
            .getWorkspaceSettings()
            .setVerticalComponentGrid(
                (int) Math.round(boardHandling.coordinateTransform.userToBoard(inputValue)));
        setVerticalGridField(boardHandling.getWorkspaceSettings().getVerticalComponentGrid());
        keyInputCompleted = true;
      }
    }

    @Override
    public void focusGained(FocusEvent evt) {}
  }

  private class ZoomButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      boardHandling.getWorkspaceSettings().setZoomWithWheel(true);
    }
  }

  private class RotateButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      boardHandling.getWorkspaceSettings().setZoomWithWheel(false);
    }
  }
}
