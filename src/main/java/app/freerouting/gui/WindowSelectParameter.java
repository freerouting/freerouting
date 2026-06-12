package app.freerouting.gui;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.interactive.InteractiveSettings;
import app.freerouting.interactive.GuiBoardManager;
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

/**
 * Window for the handling of the interactive selection parameters.
 */
public class WindowSelectParameter extends BoardSavableSubWindow {

  private final BoardFrame board_frame;
  private final GuiBoardManager board_handling;
  private final JToggleButton[] settings_select_layer_name_arr;
  private final JCheckBox[] settings_select_layer_eye_arr;
  
  private final JToggleButton[] settings_virtual_layer_name_arr;
  private final JCheckBox[] settings_virtual_layer_eye_arr;
  
  private final JCheckBox[] settings_select_item_selection_choices;
  private final JToggleButton settings_select_all_visible_button;
  private final JToggleButton settings_select_current_only_button;

  /** Resource-bundle keys for the six virtual layers, in order: F.Silk, B.Silk, F.CY, B.CY, F.Fab, B.Fab */
  private static final String[] VIRTUAL_LAYER_KEYS = {
    "F_Silkscreen", "B_Silkscreen", "F_Courtyard", "B_Courtyard", "F_Fab", "B_Fab"
  };

  /**
   * Creates a new instance of SelectWindow
   */
  public WindowSelectParameter(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;
    this.board_handling = p_board_frame.board_panel.board_handling;
    GraphicsContext gc = this.board_handling.graphics_context;

    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("title"));

    // create main panel
    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.anchor = GridBagConstraints.WEST;
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.insets = new Insets(1, 10, 1, 10);

    // Create buttongroup for the selection layers
    JLabel selection_layer_label = new JLabel(tm.getText("selection_layers"));
    gridbag.setConstraints(selection_layer_label, gridbag_constraints);
    main_panel.add(selection_layer_label);

    this.settings_select_all_visible_button = new JToggleButton(tm.getText("all_visible"));
    settings_select_all_visible_button.setToolTipText(tm.getText("all_visible_tooltip"));
    this.settings_select_current_only_button = new JToggleButton(tm.getText("current_only"));
    settings_select_current_only_button.setToolTipText(tm.getText("current_only_tooltip"));

    settings_select_all_visible_button.addActionListener(new AllVisibleListener());
    settings_select_all_visible_button.addActionListener(_ -> FRAnalytics.buttonClicked("settings_select_all_visible_button", settings_select_all_visible_button.getText()));
    settings_select_current_only_button.addActionListener(new CurrentOnlyListener());
    settings_select_current_only_button.addActionListener(_ -> FRAnalytics.buttonClicked("settings_select_current_only_button", settings_select_current_only_button.getText()));

    ButtonGroup selection_layer_button_group = new ButtonGroup();
    selection_layer_button_group.add(settings_select_all_visible_button);
    selection_layer_button_group.add(settings_select_current_only_button);
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(settings_select_all_visible_button, gridbag_constraints);
    main_panel.add(settings_select_all_visible_button, gridbag_constraints);
    gridbag.setConstraints(settings_select_current_only_button, gridbag_constraints);
    main_panel.add(settings_select_current_only_button, gridbag_constraints);

    JLabel separator = new JLabel("   –––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // Create check boxes for selectable items:
    JLabel selectable_items_label = new JLabel(tm.getText("selectable_items"));
    gridbag.setConstraints(selectable_items_label, gridbag_constraints);
    main_panel.add(selectable_items_label);

    final ItemSelectionFilter.SelectableChoices[] filter_values = ItemSelectionFilter.SelectableChoices.values();
    this.settings_select_item_selection_choices = new JCheckBox[filter_values.length];

    for (int i = 0; i < filter_values.length; i++) {
      this.settings_select_item_selection_choices[i] = new JCheckBox(tm.getText(filter_values[i].toString()));
      gridbag.setConstraints(this.settings_select_item_selection_choices[i], gridbag_constraints);
      main_panel.add(this.settings_select_item_selection_choices[i], gridbag_constraints);
      settings_select_item_selection_choices[i].addActionListener(new ItemSelectionListener(i));
      settings_select_item_selection_choices[i].addActionListener(_ -> FRAnalytics.buttonClicked("settings_select_item_selection_choices", null));
    }

    JLabel separator2 = new JLabel("   –––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator2, gridbag_constraints);
    main_panel.add(separator2, gridbag_constraints);

    // Create Layer visibility panel
    JLabel current_layer_label = new JLabel(tm.getText("current_layer"));
    current_layer_label.setToolTipText(tm.getText("current_layer_tooltip"));
    gridbag.setConstraints(current_layer_label, gridbag_constraints);
    main_panel.add(current_layer_label);

    JPanel layers_panel = new JPanel(new GridBagLayout());
    GridBagConstraints lc = new GridBagConstraints();
    lc.anchor = GridBagConstraints.WEST;
    lc.insets = new Insets(1, 2, 1, 2);
    lc.gridy = 0;

    LayerStructure layer_structure = this.board_handling.get_routing_board().layer_structure;
    int layer_count = layer_structure.arr.length;
    
    this.settings_select_layer_name_arr = new JToggleButton[layer_count];
    this.settings_select_layer_eye_arr = new JCheckBox[layer_count];
    
    ButtonGroup layer_selection_group = new ButtonGroup();

    // 1. Signal Layers
    for (int i = 0; i < layer_count; i++) {
      Layer curr_layer = layer_structure.arr[i];
      int layer_no = layer_structure.get_no(curr_layer);

      // Eye visibility toggle
      JCheckBox eye_cb = new JCheckBox();
      eye_cb.setToolTipText(tm.getText("layer_eye_tooltip", curr_layer.name));
      eye_cb.setSelected(gc.get_raw_layer_visibility(i) > 0.0);
      eye_cb.addActionListener(new LayerEyeListener(i));
      settings_select_layer_eye_arr[i] = eye_cb;

      // Color swatch
      Color traceColor = gc.get_trace_colors(false)[i];
      JLabel swatch = createSwatch(traceColor);

      // Active layer selection button
      JToggleButton btn = new JToggleButton(curr_layer.name);
      btn.setToolTipText(tm.getText("layer_button_tooltip", curr_layer.name));
      btn.setEnabled(curr_layer.is_signal);
      btn.setMargin(new Insets(2, 5, 2, 5));
      if (!curr_layer.is_signal) {
        btn.setToolTipText(tm.getText("disabled_layer_tooltip"));
      }
      btn.addActionListener(new CurrentLayerListener(i, layer_no));
      btn.addActionListener(_ -> FRAnalytics.buttonClicked("settings_select_layer_name_arr", null));
      settings_select_layer_name_arr[i] = btn;
      layer_selection_group.add(btn);

      lc.gridx = 0;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 0, 1, 2);
      layers_panel.add(eye_cb, lc);
      lc.gridx = 1;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layers_panel.add(swatch, lc);
      lc.gridx = 2;
      lc.weightx = 1.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layers_panel.add(btn, lc);
      lc.gridy++;
    }

    // 2. Virtual Layers
    this.settings_virtual_layer_name_arr = new JToggleButton[6];
    this.settings_virtual_layer_eye_arr = new JCheckBox[6];

    for (int i = 0; i < 6; i++) {
      String layerKey = VIRTUAL_LAYER_KEYS[i];
      String layerName = tm.getText(layerKey);

      // Eye visibility toggle
      JCheckBox eye_cb = new JCheckBox();
      eye_cb.setToolTipText(tm.getText("virtual_layer_eye_tooltip", layerName));
      eye_cb.setSelected(gc.get_virtual_layer_visible(i));
      eye_cb.addActionListener(new VirtualLayerEyeListener(i));
      settings_virtual_layer_eye_arr[i] = eye_cb;

      // Color swatch
      Color layerColor;
      if (i == 0 || i == 1) {
        layerColor = gc.other_color_table.get_silkscreen_color(i == 0);
      } else if (i == 2 || i == 3) {
        layerColor = gc.other_color_table.get_courtyard_color(i == 2);
      } else {
        layerColor = gc.other_color_table.get_fab_color(i == 4);
      }
      JLabel swatch = createSwatch(layerColor);

      // Active layer selection button
      JToggleButton btn = new JToggleButton(layerName);
      btn.setToolTipText(tm.getText(layerKey + "_tooltip"));
      btn.setMargin(new Insets(2, 5, 2, 5));
      btn.addActionListener(new VirtualLayerActiveListener(i));
      settings_virtual_layer_name_arr[i] = btn;
      layer_selection_group.add(btn);

      lc.gridx = 0;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 0, 1, 2);
      layers_panel.add(eye_cb, lc);
      lc.gridx = 1;
      lc.weightx = 0.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layers_panel.add(swatch, lc);
      lc.gridx = 2;
      lc.weightx = 1.0;
      lc.insets = new Insets(1, 2, 1, 2);
      layers_panel.add(btn, lc);
      lc.gridy++;
    }

    gridbag_constraints.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(layers_panel, gridbag_constraints);
    main_panel.add(layers_panel);

    JLabel empty_label = new JLabel();
    gridbag.setConstraints(empty_label, gridbag_constraints);
    main_panel.add(empty_label);

    this.refresh();
    this.pack();
    this.setResizable(false);

    // Subscribe to the InteractiveSettings singleton so this window stays in sync.
    InteractiveSettings is = this.board_handling.getInteractiveSettings();
    if (is != null) {
      is.addPropertyChangeListener(_ -> javax.swing.SwingUtilities.invokeLater(this::refresh));
    }
  }

  private JLabel createSwatch(Color c) {
    JLabel swatch = new JLabel() {
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

  /**
   * Refreshes the displayed values in this window.
   */
  @Override
  public void refresh() {
    InteractiveSettings is = this.board_handling.getInteractiveSettings();
    if (is.get_select_on_all_visible_layers()) {
      settings_select_all_visible_button.setSelected(true);
    } else {
      settings_select_current_only_button.setSelected(true);
    }
    
    ItemSelectionFilter item_selection_filter = is.get_item_selection_filter();
    if (item_selection_filter == null) {
      FRLogger.warn("SelectParameterWindow.refresh: item_selection_filter is null");
    } else {
      final ItemSelectionFilter.SelectableChoices[] filter_values = ItemSelectionFilter.SelectableChoices.values();
      for (int i = 0; i < filter_values.length; i++) {
        this.settings_select_item_selection_choices[i].setSelected(item_selection_filter.is_selected(filter_values[i]));
      }
    }

    GraphicsContext gc = this.board_handling.graphics_context;
    
    // Sync physical layers
    int active_layer = is.get_layer();
    int active_virtual = gc.get_fully_visible_virtual_layer();

    for (int i = 0; i < settings_select_layer_name_arr.length; i++) {
      settings_select_layer_name_arr[i].setSelected(active_layer == i && active_virtual == -1);
      settings_select_layer_eye_arr[i].setSelected(gc.get_raw_layer_visibility(i) > 0.0);
    }

    // Sync virtual layers
    for (int i = 0; i < 6; i++) {
      settings_virtual_layer_name_arr[i].setSelected(active_virtual == i);
      settings_virtual_layer_eye_arr[i].setSelected(gc.get_virtual_layer_visible(i));
    }
  }

  /**
   * Selects the layer with the input signal number.
   */
  public void select(int p_signal_layer_no) {
    if (p_signal_layer_no >= 0 && p_signal_layer_no < settings_select_layer_name_arr.length) {
      settings_select_layer_name_arr[p_signal_layer_no].setSelected(true);
      if (board_handling.graphics_context != null) {
        board_handling.graphics_context.set_fully_visible_layer(p_signal_layer_no);
      }
      board_frame.board_panel.repaint();
    }
  }

  private class CurrentLayerListener implements ActionListener {
    public final int signal_layer_no;
    public final int layer_no;

    public CurrentLayerListener(int p_signal_layer_no, int p_layer_no) {
      signal_layer_no = p_signal_layer_no;
      layer_no = p_layer_no;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (settings_select_layer_name_arr[signal_layer_no].isSelected()) {
        board_handling.set_current_layer(layer_no);
      } else {
        board_handling.graphics_context.set_fully_visible_layer(-1);
      }
      board_frame.board_panel.repaint();
      refresh();
    }
  }

  private class LayerEyeListener implements ActionListener {
    private final int layer_idx;

    public LayerEyeListener(int idx) {
      layer_idx = idx;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      boolean visible = settings_select_layer_eye_arr[layer_idx].isSelected();
      board_handling.set_layer_visibility(layer_idx, visible ? 1.0 : 0.0);
      board_frame.board_panel.repaint();
    }
  }

  private class VirtualLayerEyeListener implements ActionListener {
    private final int virtual_idx;

    public VirtualLayerEyeListener(int idx) {
      virtual_idx = idx;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      boolean visible = settings_virtual_layer_eye_arr[virtual_idx].isSelected();
      board_handling.graphics_context.set_virtual_layer_visible(virtual_idx, visible);
      board_frame.board_panel.repaint();
    }
  }

  private class VirtualLayerActiveListener implements ActionListener {
    private final int virtual_idx;

    public VirtualLayerActiveListener(int idx) {
      virtual_idx = idx;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (settings_virtual_layer_name_arr[virtual_idx].isSelected()) {
        board_handling.graphics_context.set_fully_visible_virtual_layer(virtual_idx);
      } else {
        board_handling.graphics_context.set_fully_visible_virtual_layer(-1);
      }
      board_frame.board_panel.repaint();
      refresh();
    }
  }

  private class AllVisibleListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.getInteractiveSettings().set_select_on_all_visible_layers(true);
    }
  }

  private class CurrentOnlyListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.getInteractiveSettings().set_select_on_all_visible_layers(false);
    }
  }

  private class ItemSelectionListener implements ActionListener {
    private final int item_no;

    public ItemSelectionListener(int p_item_no) {
      item_no = p_item_no;
    }

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      boolean is_selected = settings_select_item_selection_choices[item_no].isSelected();
      ItemSelectionFilter.SelectableChoices item_type = ItemSelectionFilter.SelectableChoices.values()[item_no];
      board_handling.set_selectable(item_type, is_selected);

      // make sure that from fixed and unfixed items at least one type is selected.
      if (item_type == ItemSelectionFilter.SelectableChoices.FIXED) {
        int unfixed_no = ItemSelectionFilter.SelectableChoices.UNFIXED.ordinal();
        if (!is_selected && !settings_select_item_selection_choices[unfixed_no].isSelected()) {
          settings_select_item_selection_choices[unfixed_no].setSelected(true);
          board_handling.set_selectable(ItemSelectionFilter.SelectableChoices.UNFIXED, true);
        }
      } else if (item_type == ItemSelectionFilter.SelectableChoices.UNFIXED) {
        int fixed_no = ItemSelectionFilter.SelectableChoices.FIXED.ordinal();
        if (!is_selected && !settings_select_item_selection_choices[fixed_no].isSelected()) {
          settings_select_item_selection_choices[fixed_no].setSelected(true);
          board_handling.set_selectable(ItemSelectionFilter.SelectableChoices.FIXED, true);
        }
      }
    }
  }
}