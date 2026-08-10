package app.freerouting.gui;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveSettings;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Window for the handling of the interactive selection parameters. */
public class WindowSelectParameter extends BoardSavableSubWindow {

  private final BoardFrame boardFrame;
  private final GuiBoardManager boardHandling;
  private final JToggleButton[] settingsSelectLayerNameArr;
  private final JCheckBox[] settingsSelectLayerEyeArr;

  private final JToggleButton[] settingsVirtualLayerNameArr;
  private final JCheckBox[] settingsVirtualLayerEyeArr;

  private final JCheckBox[] settingsSelectItemSelectionChoices;
  private final JToggleButton settingsSelectAllVisibleButton;
  private final JToggleButton settingsSelectCurrentOnlyButton;

  /**
   * Resource-bundle keys for the six virtual layers, in order: F.Silk, B.Silk, F.CY, B.CY, F.Fab,.
   * B.Fab
   */
  private static final String[] VIRTUAL_LAYER_KEYS = {
    "F_Silkscreen", "B_Silkscreen", "F_Courtyard", "B_Courtyard", "F_Fab", "B_Fab"
  };

  /** Creates a new instance of SelectWindow. */
  public WindowSelectParameter(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
    this.boardHandling = boardFrame.boardPanel.boardHandling;
    final GraphicsContext gc = this.boardHandling.graphicsContext;

    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("title"));

    // create main panel
    final JPanel mainPanel = new JPanel();
    getContentPane().add(mainPanel);
    GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.anchor = GridBagConstraints.WEST;
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbagConstraints.insets = new Insets(1, 10, 1, 10);

    // Create buttongroup for the selection layers
    JLabel selectionLayerLabel = new JLabel(tm.getText("selection_layers"));
    gridbag.setConstraints(selectionLayerLabel, gridbagConstraints);
    mainPanel.add(selectionLayerLabel);

    this.settingsSelectAllVisibleButton = new JToggleButton(tm.getText("all_visible"));
    settingsSelectAllVisibleButton.setToolTipText(tm.getText("all_visible_tooltip"));
    this.settingsSelectCurrentOnlyButton = new JToggleButton(tm.getText("current_only"));
    settingsSelectCurrentOnlyButton.setToolTipText(tm.getText("current_only_tooltip"));

    settingsSelectAllVisibleButton.addActionListener(new AllVisibleListener());
    settingsSelectAllVisibleButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsSelectAllVisibleButton", settingsSelectAllVisibleButton.getText()));
    settingsSelectCurrentOnlyButton.addActionListener(new CurrentOnlyListener());
    settingsSelectCurrentOnlyButton.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsSelectCurrentOnlyButton", settingsSelectCurrentOnlyButton.getText()));

    ButtonGroup selectionLayerButtonGroup = new ButtonGroup();
    selectionLayerButtonGroup.add(settingsSelectAllVisibleButton);
    selectionLayerButtonGroup.add(settingsSelectCurrentOnlyButton);
    gridbagConstraints.gridheight = 1;
    gridbag.setConstraints(settingsSelectAllVisibleButton, gridbagConstraints);
    mainPanel.add(settingsSelectAllVisibleButton, gridbagConstraints);
    gridbag.setConstraints(settingsSelectCurrentOnlyButton, gridbagConstraints);
    mainPanel.add(settingsSelectCurrentOnlyButton, gridbagConstraints);

    JLabel separator = new JLabel("   –––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbagConstraints);
    mainPanel.add(separator, gridbagConstraints);

    // Create check boxes for selectable items:
    JLabel selectableItemsLabel = new JLabel(tm.getText("selectable_items"));
    gridbag.setConstraints(selectableItemsLabel, gridbagConstraints);
    mainPanel.add(selectableItemsLabel);

    final ItemSelectionFilter.SelectableChoices[] filterValues =
        ItemSelectionFilter.SelectableChoices.values();
    this.settingsSelectItemSelectionChoices = new JCheckBox[filterValues.length];

    for (int i = 0; i < filterValues.length; i++) {
      this.settingsSelectItemSelectionChoices[i] =
          new JCheckBox(tm.getText(filterValues[i].toString()));
      gridbag.setConstraints(this.settingsSelectItemSelectionChoices[i], gridbagConstraints);
      mainPanel.add(this.settingsSelectItemSelectionChoices[i], gridbagConstraints);
      settingsSelectItemSelectionChoices[i].addActionListener(new ItemSelectionListener(i));
      settingsSelectItemSelectionChoices[i].addActionListener(
          _ -> FRAnalytics.buttonClicked("settingsSelectItemSelectionChoices", null));
    }

    JLabel separator2 = new JLabel("   –––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator2, gridbagConstraints);
    mainPanel.add(separator2, gridbagConstraints);

    // Create Layer visibility panel
    JLabel currentLayerLabel = new JLabel(tm.getText("currentLayer"));
    currentLayerLabel.setToolTipText(tm.getText("current_layer_tooltip"));
    gridbag.setConstraints(currentLayerLabel, gridbagConstraints);
    mainPanel.add(currentLayerLabel);

    final JPanel layersPanel = new JPanel(new GridBagLayout());
    GridBagConstraints lc = new GridBagConstraints();
    lc.anchor = GridBagConstraints.WEST;
    lc.insets = new Insets(1, 2, 1, 2);
    lc.gridy = 0;

    LayerStructure layerStructure = this.boardHandling.getRoutingBoard().layerStructure;
    int layerCount = layerStructure.arr.length;

    this.settingsSelectLayerNameArr = new JToggleButton[layerCount];
    this.settingsSelectLayerEyeArr = new JCheckBox[layerCount];

    ButtonGroup layerSelectionGroup = new ButtonGroup();

    // 1. Signal Layers
    for (int i = 0; i < layerCount; i++) {
      Layer currLayer = layerStructure.arr[i];
      final int layerNo = layerStructure.getNo(currLayer);

      // Eye visibility toggle
      JCheckBox eyeCb = new JCheckBox();
      eyeCb.setToolTipText(tm.getText("layer_eye_tooltip", currLayer.name));
      eyeCb.setSelected(gc.getRawLayerVisibility(i) > 0.0);
      eyeCb.addActionListener(new LayerEyeListener(i));
      settingsSelectLayerEyeArr[i] = eyeCb;

      // Color swatch
      Color traceColor = gc.getTraceColors(false)[i];
      final JLabel swatch = createSwatch(traceColor);

      // Active layer selection button
      JToggleButton btn = new JToggleButton(currLayer.name);
      btn.setToolTipText(tm.getText("layer_button_tooltip", currLayer.name));
      btn.setEnabled(true);
      btn.setMargin(new Insets(2, 5, 2, 5));
      if (!currLayer.isSignal) {
        btn.setToolTipText(tm.getText("disabled_layer_tooltip"));
      }
      btn.addActionListener(new CurrentLayerListener(i, layerNo));
      btn.addActionListener(_ -> FRAnalytics.buttonClicked("settingsSelectLayerNameArr", null));
      settingsSelectLayerNameArr[i] = btn;
      layerSelectionGroup.add(btn);

      lc.gridx = 0;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 0, 1, 2);
      layersPanel.add(eyeCb, lc);
      lc.gridx = 1;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layersPanel.add(swatch, lc);
      lc.gridx = 2;
      lc.weightx = 1.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layersPanel.add(btn, lc);
      lc.gridy++;
    }

    // 2. Virtual Layers
    this.settingsVirtualLayerNameArr = new JToggleButton[6];
    this.settingsVirtualLayerEyeArr = new JCheckBox[6];

    for (int i = 0; i < 6; i++) {
      String layerKey = VIRTUAL_LAYER_KEYS[i];
      String layerName = tm.getText(layerKey);

      // Eye visibility toggle
      JCheckBox eyeCb = new JCheckBox();
      eyeCb.setToolTipText(tm.getText("virtual_layer_eye_tooltip", layerName));
      eyeCb.setSelected(gc.getVirtualLayerVisible(i));
      eyeCb.addActionListener(new VirtualLayerEyeListener(i));
      settingsVirtualLayerEyeArr[i] = eyeCb;

      // Color swatch
      Color layerColor;
      if (i == 0 || i == 1) {
        layerColor = gc.otherColorTable.getSilkscreenColor(i == 0);
      } else if (i == 2 || i == 3) {
        layerColor = gc.otherColorTable.getCourtyardColor(i == 2);
      } else {
        layerColor = gc.otherColorTable.getFabColor(i == 4);
      }
      final JLabel swatch = createSwatch(layerColor);

      // Active layer selection button
      JToggleButton btn = new JToggleButton(layerName);
      btn.setToolTipText(tm.getText(layerKey + "_tooltip"));
      btn.setMargin(new Insets(2, 5, 2, 5));
      btn.addActionListener(new VirtualLayerActiveListener(i));
      settingsVirtualLayerNameArr[i] = btn;
      layerSelectionGroup.add(btn);

      lc.gridx = 0;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 0, 1, 2);
      layersPanel.add(eyeCb, lc);
      lc.gridx = 1;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layersPanel.add(swatch, lc);
      lc.gridx = 2;
      lc.weightx = 1.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layersPanel.add(btn, lc);
      lc.gridy++;
    }

    gridbagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(layersPanel, gridbagConstraints);
    mainPanel.add(layersPanel);

    JLabel emptyLabel = new JLabel();
    gridbag.setConstraints(emptyLabel, gridbagConstraints);
    mainPanel.add(emptyLabel);

    this.refresh();
    this.pack();
    this.setResizable(false);

    // Subscribe to the InteractiveSettings singleton so this window stays in sync.
    InteractiveSettings is = this.boardHandling.getInteractiveSettings();
    if (is != null) {
      is.addPropertyChangeListener(_ -> javax.swing.SwingUtilities.invokeLater(this::refresh));
    }
  }

  private JLabel createSwatch(Color c) {
    JLabel swatch =
        new JLabel() {
          @Override
          public Dimension getPreferredSize() {
            return new Dimension(12, 12);
          }
        };
    swatch.setOpaque(true);
    swatch.setBackground(c);
    swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    return swatch;
  }

  /** Refreshes the displayed values in this window. */
  @Override
  public void refresh() {
    InteractiveSettings is = this.boardHandling.getInteractiveSettings();
    if (is.getSelectOnAllVisibleLayers()) {
      settingsSelectAllVisibleButton.setSelected(true);
    } else {
      settingsSelectCurrentOnlyButton.setSelected(true);
    }

    ItemSelectionFilter itemSelectionFilter = is.getItemSelectionFilter();
    if (itemSelectionFilter == null) {
      FRLogger.warn("SelectParameterWindow.refresh: itemSelectionFilter is null");
    } else {
      final ItemSelectionFilter.SelectableChoices[] filterValues =
          ItemSelectionFilter.SelectableChoices.values();
      for (int i = 0; i < filterValues.length; i++) {
        this.settingsSelectItemSelectionChoices[i].setSelected(
            itemSelectionFilter.isSelected(filterValues[i]));
      }
    }

    GraphicsContext gc = this.boardHandling.graphicsContext;

    // Sync physical layers
    int activeLayer = is.getLayer();
    int activeVirtual = gc.getFullyVisibleVirtualLayer();

    for (int i = 0; i < settingsSelectLayerNameArr.length; i++) {
      settingsSelectLayerNameArr[i].setSelected(activeLayer == i && activeVirtual == -1);
      settingsSelectLayerEyeArr[i].setSelected(gc.getRawLayerVisibility(i) > 0.0);
    }

    // Sync virtual layers
    for (int i = 0; i < 6; i++) {
      settingsVirtualLayerNameArr[i].setSelected(activeVirtual == i);
      settingsVirtualLayerEyeArr[i].setSelected(gc.getVirtualLayerVisible(i));
    }
  }

  /** Selects the layer with the input signal number. */
  public void select(int signalLayerNo) {
    if (signalLayerNo >= 0 && signalLayerNo < settingsSelectLayerNameArr.length) {
      settingsSelectLayerNameArr[signalLayerNo].setSelected(true);
      if (boardHandling.graphicsContext != null) {
        boardHandling.graphicsContext.setFullyVisibleLayer(signalLayerNo);
      }
      boardFrame.boardPanel.repaint();
    }
  }

  private class CurrentLayerListener implements ActionListener {
    public final int signalLayerNo;
    public final int layerNo;

    public CurrentLayerListener(int signalLayerNo, int layerNo) {
      this.signalLayerNo = signalLayerNo;
      this.layerNo = layerNo;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      if (settingsSelectLayerNameArr[signalLayerNo].isSelected()) {
        boardHandling.setCurrentLayer(layerNo);
      } else {
        boardHandling.graphicsContext.setFullyVisibleLayer(-1);
      }
      boardFrame.boardPanel.repaint();
      refresh();
    }
  }

  private class LayerEyeListener implements ActionListener {
    private final int layerIdx;

    public LayerEyeListener(int idx) {
      this.layerIdx = idx;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      boolean visible = settingsSelectLayerEyeArr[layerIdx].isSelected();
      boardHandling.setLayerVisibility(layerIdx, visible ? 1.0 : 0.0);
      boardFrame.boardPanel.repaint();
    }
  }

  private class VirtualLayerEyeListener implements ActionListener {
    private final int virtualIdx;

    public VirtualLayerEyeListener(int idx) {
      this.virtualIdx = idx;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      boolean visible = settingsVirtualLayerEyeArr[virtualIdx].isSelected();
      boardHandling.graphicsContext.setVirtualLayerVisible(virtualIdx, visible);
      boardFrame.boardPanel.repaint();
    }
  }

  private class VirtualLayerActiveListener implements ActionListener {
    private final int virtualIdx;

    public VirtualLayerActiveListener(int idx) {
      this.virtualIdx = idx;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      if (settingsVirtualLayerNameArr[virtualIdx].isSelected()) {
        boardHandling.graphicsContext.setFullyVisibleVirtualLayer(virtualIdx);
      } else {
        boardHandling.graphicsContext.setFullyVisibleVirtualLayer(-1);
      }
      boardFrame.boardPanel.repaint();
      refresh();
    }
  }

  private class AllVisibleListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent evt) {
      boardHandling.getInteractiveSettings().setSelectOnAllVisibleLayers(true);
    }
  }

  private class CurrentOnlyListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent evt) {
      boardHandling.getInteractiveSettings().setSelectOnAllVisibleLayers(false);
    }
  }

  private class ItemSelectionListener implements ActionListener {
    private final int itemNo;

    public ItemSelectionListener(int itemNo) {
      this.itemNo = itemNo;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      boolean isSelected = settingsSelectItemSelectionChoices[itemNo].isSelected();
      ItemSelectionFilter.SelectableChoices itemType =
          ItemSelectionFilter.SelectableChoices.values()[itemNo];
      boardHandling.setSelectable(itemType, isSelected);

      // make sure that from fixed and unfixed items at least one type is selected.
      if (itemType == ItemSelectionFilter.SelectableChoices.FIXED) {
        int unfixedNo = ItemSelectionFilter.SelectableChoices.UNFIXED.ordinal();
        if (!isSelected && !settingsSelectItemSelectionChoices[unfixedNo].isSelected()) {
          settingsSelectItemSelectionChoices[unfixedNo].setSelected(true);
          boardHandling.setSelectable(ItemSelectionFilter.SelectableChoices.UNFIXED, true);
        }
      } else if (itemType == ItemSelectionFilter.SelectableChoices.UNFIXED) {
        int fixedNo = ItemSelectionFilter.SelectableChoices.FIXED.ordinal();
        if (!isSelected && !settingsSelectItemSelectionChoices[fixedNo].isSelected()) {
          settingsSelectItemSelectionChoices[fixedNo].setSelected(true);
          boardHandling.setSelectable(ItemSelectionFilter.SelectableChoices.FIXED, true);
        }
      }
    }
  }
}
