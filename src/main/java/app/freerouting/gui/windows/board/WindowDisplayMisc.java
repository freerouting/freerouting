package app.freerouting.gui.windows.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardPanel;
import app.freerouting.gui.board.BoardSavableSubWindow;
import app.freerouting.gui.rendering.ScreenTransform;
import app.freerouting.logger.FRLogger;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Window for interactive changing of miscellaneous display properties. */
public class WindowDisplayMisc extends BoardSavableSubWindow {

  private static final int MAX_SLIDER_VALUE = 100;
  private final BoardPanel panel;
  private final JRadioButton appearanceMiscSmallCursorCheckbox;
  private final JRadioButton appearanceMiscBigCursorCheckbox;
  private final JRadioButton appearanceMiscRotationNoneCheckbox;
  private final JRadioButton appearanceMiscRotation90DegreeCheckbox;
  private final JRadioButton appearanceMiscRotation180DegreeCheckbox;
  private final JRadioButton appearanceMiscRotation270DegreeCheckbox;
  private final JRadioButton appearanceMiscMirrorNoneCheckbox;
  private final JRadioButton appearanceMiscVerticalMirrorCheckbox;
  private final JRadioButton appearanceMiscHorizontalMirrorCheckbox;
  private final JSlider autoLayerDimSlider;

  /** Creates a new instance of DisplayMiscWindow. */
  public WindowDisplayMisc(BoardFrame boardFrame) {
    setLanguage(boardFrame.getLocale());

    this.panel = boardFrame.boardPanel;
    this.setTitle(tm.getText("title"));

    // Create main panel

    final JPanel mainPanel = new JPanel();
    getContentPane().add(createScrollableContainer(mainPanel));

    // Initialize gridbag layout.

    GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.anchor = GridBagConstraints.WEST;
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;

    // add label and buttongroup for the appearance of the cross-hair cursor.

    JLabel cursorLabel = new JLabel("   " + tm.getText("cross_hair_cursor"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 2;
    gridbag.setConstraints(cursorLabel, gridbagConstraints);
    mainPanel.add(cursorLabel, gridbagConstraints);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;

    appearanceMiscSmallCursorCheckbox = new JRadioButton(tm.getText("small"));
    appearanceMiscSmallCursorCheckbox.setToolTipText(tm.getText("cursor_checkbox_tooltip"));
    appearanceMiscSmallCursorCheckbox.addActionListener(new SmallCursorListener());
    appearanceMiscSmallCursorCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscSmallCursorCheckbox", appearanceMiscSmallCursorCheckbox.getText()));
    gridbag.setConstraints(appearanceMiscSmallCursorCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscSmallCursorCheckbox, gridbagConstraints);

    appearanceMiscBigCursorCheckbox = new JRadioButton(tm.getText("big"));
    appearanceMiscBigCursorCheckbox.addActionListener(new BigCursorListener());
    appearanceMiscBigCursorCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscBigCursorCheckbox", appearanceMiscBigCursorCheckbox.getText()));
    appearanceMiscBigCursorCheckbox.setToolTipText(tm.getText("cursor_checkbox_tooltip"));
    gridbag.setConstraints(appearanceMiscBigCursorCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscBigCursorCheckbox, gridbagConstraints);

    ButtonGroup cursorButtonGroup = new ButtonGroup();
    cursorButtonGroup.add(appearanceMiscSmallCursorCheckbox);
    cursorButtonGroup.add(appearanceMiscBigCursorCheckbox);

    JLabel separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);

    // Add label and buttongroup for the rotation of the board.

    JLabel rotationLabel = new JLabel("   " + tm.getText("rotation"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 4;
    gridbag.setConstraints(rotationLabel, gridbagConstraints);
    mainPanel.add(rotationLabel, gridbagConstraints);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;

    appearanceMiscRotationNoneCheckbox = new JRadioButton(tm.getText("none"));
    gridbag.setConstraints(appearanceMiscRotationNoneCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscRotationNoneCheckbox, gridbagConstraints);

    appearanceMiscRotation90DegreeCheckbox = new JRadioButton(tm.getText("90_degree"));
    gridbag.setConstraints(appearanceMiscRotation90DegreeCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscRotation90DegreeCheckbox, gridbagConstraints);

    appearanceMiscRotation180DegreeCheckbox = new JRadioButton(tm.getText("180_degree"));
    gridbag.setConstraints(appearanceMiscRotation180DegreeCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscRotation180DegreeCheckbox, gridbagConstraints);

    appearanceMiscRotation270DegreeCheckbox = new JRadioButton(tm.getText("-90_degree"));
    gridbag.setConstraints(appearanceMiscRotation270DegreeCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscRotation270DegreeCheckbox, gridbagConstraints);

    ButtonGroup rotationButtonGroup = new ButtonGroup();
    rotationButtonGroup.add(appearanceMiscRotationNoneCheckbox);
    rotationButtonGroup.add(appearanceMiscRotation90DegreeCheckbox);
    rotationButtonGroup.add(appearanceMiscRotation180DegreeCheckbox);
    rotationButtonGroup.add(appearanceMiscRotation270DegreeCheckbox);

    appearanceMiscRotationNoneCheckbox.addActionListener(new RotationNoneListener());
    appearanceMiscRotationNoneCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscRotationNoneCheckbox",
                appearanceMiscRotationNoneCheckbox.getText()));
    appearanceMiscRotation90DegreeCheckbox.addActionListener(new Rotation90Listener());
    appearanceMiscRotation90DegreeCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscRotation90DegreeCheckbox",
                appearanceMiscRotation90DegreeCheckbox.getText()));
    appearanceMiscRotation180DegreeCheckbox.addActionListener(new Rotation180Listener());
    appearanceMiscRotation180DegreeCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscRotation180DegreeCheckbox",
                appearanceMiscRotation180DegreeCheckbox.getText()));
    appearanceMiscRotation270DegreeCheckbox.addActionListener(new Rotation270Listener());
    appearanceMiscRotation270DegreeCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscRotation270DegreeCheckbox",
                appearanceMiscRotation270DegreeCheckbox.getText()));

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);

    // add label and buttongroup for the mirroring of the board.

    JLabel mirroringLabel = new JLabel("   " + tm.getText("board_mirroring"));
    gridbagConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbagConstraints.gridheight = 3;
    gridbag.setConstraints(mirroringLabel, gridbagConstraints);
    mainPanel.add(mirroringLabel, gridbagConstraints);

    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.gridheight = 1;

    appearanceMiscMirrorNoneCheckbox = new JRadioButton(tm.getText("none"));
    appearanceMiscMirrorNoneCheckbox.addActionListener(new MirrorNoneListener());
    appearanceMiscMirrorNoneCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscMirrorNoneCheckbox", appearanceMiscMirrorNoneCheckbox.getText()));
    gridbag.setConstraints(appearanceMiscMirrorNoneCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscMirrorNoneCheckbox, gridbagConstraints);

    appearanceMiscVerticalMirrorCheckbox = new JRadioButton(tm.getText("left_right"));
    appearanceMiscVerticalMirrorCheckbox.addActionListener(new VerticalMirrorListener());
    appearanceMiscVerticalMirrorCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscVerticalMirrorCheckbox",
                appearanceMiscVerticalMirrorCheckbox.getText()));
    gridbag.setConstraints(appearanceMiscVerticalMirrorCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscVerticalMirrorCheckbox, gridbagConstraints);

    appearanceMiscHorizontalMirrorCheckbox = new JRadioButton(tm.getText("top_bottom"));
    appearanceMiscHorizontalMirrorCheckbox.addActionListener(new HorizontalMirrorListener());
    appearanceMiscHorizontalMirrorCheckbox.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "appearanceMiscHorizontalMirrorCheckbox",
                appearanceMiscHorizontalMirrorCheckbox.getText()));
    gridbag.setConstraints(appearanceMiscHorizontalMirrorCheckbox, gridbagConstraints);
    mainPanel.add(appearanceMiscHorizontalMirrorCheckbox, gridbagConstraints);

    ButtonGroup mirroringButtonGroup = new ButtonGroup();
    mirroringButtonGroup.add(appearanceMiscMirrorNoneCheckbox);
    mirroringButtonGroup.add(appearanceMiscVerticalMirrorCheckbox);
    mirroringButtonGroup.add(appearanceMiscHorizontalMirrorCheckbox);

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);

    // add slider for automatic layer dimming

    gridbagConstraints.insets = new Insets(5, 10, 5, 10);
    JLabel autoLayerDimLabel = new JLabel(tm.getText("layerDimming"));
    autoLayerDimLabel.setToolTipText(tm.getText("layer_dimming_tooltip"));
    gridbag.setConstraints(autoLayerDimLabel, gridbagConstraints);
    mainPanel.add(autoLayerDimLabel);
    this.autoLayerDimSlider = new JSlider(0, MAX_SLIDER_VALUE);
    gridbag.setConstraints(autoLayerDimSlider, gridbagConstraints);
    mainPanel.add(autoLayerDimSlider);
    this.autoLayerDimSlider.addChangeListener(new SliderChangeListener());

    this.refresh();
    this.pack();
    this.setResizable(false);
    clampWindowHeight(this, boardFrame);
  }

  /** Refreshes the displayed values in this window. */
  @Override
  public void refresh() {
    appearanceMiscSmallCursorCheckbox.setSelected(!panel.isCustomCrossHairCursor());
    appearanceMiscBigCursorCheckbox.setSelected(panel.isCustomCrossHairCursor());

    int ninetyDegreeRotation =
        panel.boardHandling.graphicsContext.coordinateTransform.get90DegreeRotation();

    switch (ninetyDegreeRotation) {
      case 0 -> appearanceMiscRotationNoneCheckbox.setSelected(true);
      case 1 -> appearanceMiscRotation90DegreeCheckbox.setSelected(true);
      case 2 -> appearanceMiscRotation180DegreeCheckbox.setSelected(true);
      case 3 -> appearanceMiscRotation270DegreeCheckbox.setSelected(true);
      default -> {
        FRLogger.warn("DisplayMiscWindow: unexpected ninetyDegreeRotation");
        appearanceMiscRotationNoneCheckbox.setSelected(true);
      }
    }

    boolean isMirrorLeftRight =
        panel.boardHandling.graphicsContext.coordinateTransform.isMirrorLeftRight();
    boolean isMirrorTopButton =
        panel.boardHandling.graphicsContext.coordinateTransform.isMirrorTopBottom();
    appearanceMiscMirrorNoneCheckbox.setSelected(!(isMirrorLeftRight || isMirrorTopButton));

    appearanceMiscVerticalMirrorCheckbox.setSelected(
        panel.boardHandling.graphicsContext.coordinateTransform.isMirrorLeftRight());
    appearanceMiscHorizontalMirrorCheckbox.setSelected(
        panel.boardHandling.graphicsContext.coordinateTransform.isMirrorTopBottom());

    int currentSliderValue =
        (int)
            Math.round(
                MAX_SLIDER_VALUE
                    * (1 - panel.boardHandling.graphicsContext.getAutoLayerDimFactor()));
    autoLayerDimSlider.setValue(currentSliderValue);
  }

  private class SmallCursorListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      panel.setCustomCrosshairCursor(false);
    }
  }

  private class BigCursorListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      panel.setCustomCrosshairCursor(true);
    }
  }

  private class RotationNoneListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      coordinateTransform.setRotation(0);
      panel.repaint();
    }
  }

  private class Rotation90Listener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      coordinateTransform.setRotation(0.5 * Math.PI);
      panel.repaint();
    }
  }

  private class Rotation180Listener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      coordinateTransform.setRotation(Math.PI);
      panel.repaint();
    }
  }

  private class Rotation270Listener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      coordinateTransform.setRotation(1.5 * Math.PI);
      panel.repaint();
    }
  }

  private class MirrorNoneListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      if (!(coordinateTransform.isMirrorLeftRight() || coordinateTransform.isMirrorTopBottom())) {
        return; // mirroring already switched off
      }
      // remember the old viewort center to retain the displayed section of the board.
      FloatPoint oldViewportCenter = coordinateTransform.screenToBoard(panel.getViewportCenter());
      coordinateTransform.setMirrorLeftRight(false);
      coordinateTransform.setMirrorTopBottom(false);
      panel.setViewportCenter(coordinateTransform.boardToScreen(oldViewportCenter));
      panel.repaint();
    }
  }

  private class VerticalMirrorListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      if (coordinateTransform.isMirrorLeftRight()) {
        return; // already mirrored
      }
      // remember the old viewport center to retain the displayed section of the board.
      FloatPoint oldViewportCenter = coordinateTransform.screenToBoard(panel.getViewportCenter());
      coordinateTransform.setMirrorLeftRight(true);
      coordinateTransform.setMirrorTopBottom(false);
      panel.setViewportCenter(coordinateTransform.boardToScreen(oldViewportCenter));
      panel.repaint();
    }
  }

  private class HorizontalMirrorListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent evt) {
      ScreenTransform coordinateTransform = panel.boardHandling.graphicsContext.coordinateTransform;
      if (coordinateTransform.isMirrorTopBottom()) {
        return; // already mirrored
      }
      // remember the old viewport center to retain the displayed section of the board.
      FloatPoint oldViewportCenter = coordinateTransform.screenToBoard(panel.getViewportCenter());
      coordinateTransform.setMirrorTopBottom(true);
      coordinateTransform.setMirrorLeftRight(false);
      panel.setViewportCenter(coordinateTransform.boardToScreen(oldViewportCenter));
      panel.repaint();
    }
  }

  private class SliderChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent evt) {
      double newValue = 1 - (double) autoLayerDimSlider.getValue() / (double) MAX_SLIDER_VALUE;
      panel.boardHandling.graphicsContext.setAutoLayerDimFactor(newValue);
      panel.repaint();
    }
  }
}
