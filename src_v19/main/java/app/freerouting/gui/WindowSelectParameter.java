package app.freerouting.gui;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.logger.FRLogger;

import app.freerouting.management.FRAnalytics;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Window for the handling of the interactive selection parameters, */
public class WindowSelectParameter extends BoardSavableSubWindow {

  private final BoardHandling board_handling;
  private final JRadioButton[] settings_select_layer_name_arr;
  private final JCheckBox[] settings_select_item_selection_choices;
  private final JRadioButton settings_select_all_visible_button;
  private final JRadioButton settings_select_current_only_button;

  /** Creates a new instance of SelectWindow */
  public WindowSelectParameter(BoardFrame p_board_frame) {
    this.board_handling = p_board_frame.board_panel.board_handling;

    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowSelectParameter", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

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

    JLabel selection_layer_label =
        new JLabel(resources.getString("selection_layers"));
    gridbag.setConstraints(selection_layer_label, gridbag_constraints);
    main_panel.add(selection_layer_label);

    this.settings_select_all_visible_button = new JRadioButton(resources.getString("all_visible"));
    settings_select_all_visible_button.setToolTipText(resources.getString("all_visible_tooltip"));
    this.settings_select_current_only_button = new JRadioButton(resources.getString("current_only"));
    settings_select_current_only_button.setToolTipText(resources.getString("current_only_tooltip"));

    settings_select_all_visible_button.addActionListener(new AllVisibleListener());
    settings_select_all_visible_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_select_all_visible_button", settings_select_all_visible_button.getText()));
    settings_select_current_only_button.addActionListener(new CurrentOnlyListener());
    settings_select_current_only_button.addActionListener(evt -> FRAnalytics.buttonClicked("settings_select_current_only_button", settings_select_current_only_button.getText()));

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

    JLabel selectable_items_label =
        new JLabel(resources.getString("selectable_items"));
    gridbag.setConstraints(selectable_items_label, gridbag_constraints);
    main_panel.add(selectable_items_label);

    final ItemSelectionFilter.SelectableChoices[] filter_values =
        ItemSelectionFilter.SelectableChoices.values();

    this.settings_select_item_selection_choices = new JCheckBox[filter_values.length];

    for (int i = 0; i < filter_values.length; ++i) {
      this.settings_select_item_selection_choices[i] =
          new JCheckBox(resources.getString(filter_values[i].toString()));
      gridbag.setConstraints(this.settings_select_item_selection_choices[i], gridbag_constraints);
      main_panel.add(this.settings_select_item_selection_choices[i], gridbag_constraints);
      settings_select_item_selection_choices[i].addActionListener(new ItemSelectionListener(i));
      settings_select_item_selection_choices[i].addActionListener(evt -> FRAnalytics.buttonClicked("settings_select_item_selection_choices", null));
    }

    JLabel separator2 = new JLabel("   –––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator2, gridbag_constraints);
    main_panel.add(separator2, gridbag_constraints);

    // Create buttongroup for the current layer:

    JLabel current_layer_label =
        new JLabel(resources.getString("current_layer"));
    current_layer_label.setToolTipText(resources.getString("current_layer_tooltip"));
    gridbag.setConstraints(current_layer_label, gridbag_constraints);
    main_panel.add(current_layer_label);

    LayerStructure layer_structure =
        this.board_handling.get_routing_board().layer_structure;
    int layer_count = layer_structure.arr.length;
    this.settings_select_layer_name_arr = new JRadioButton[layer_count];
    ButtonGroup current_layer_button_group = new ButtonGroup();
    gridbag_constraints.gridheight = 1;
    for (int i = 0; i < layer_count; ++i) {
      Layer curr_layer = layer_structure.arr[i];
      settings_select_layer_name_arr[i] = new JRadioButton();
      settings_select_layer_name_arr[i].setText(curr_layer.name);
      settings_select_layer_name_arr[i].setEnabled(curr_layer.is_signal);
      gridbag.setConstraints(settings_select_layer_name_arr[i], gridbag_constraints);
      main_panel.add(settings_select_layer_name_arr[i]);
      current_layer_button_group.add(settings_select_layer_name_arr[i]);
      int layer_no = layer_structure.get_no(curr_layer);
      settings_select_layer_name_arr[i].addActionListener(new CurrentLayerListener(i, layer_no));
      settings_select_layer_name_arr[i].addActionListener(evt -> FRAnalytics.buttonClicked("settings_select_layer_name_arr", null));
    }

    JLabel empty_label = new JLabel();
    gridbag.setConstraints(empty_label, gridbag_constraints);
    main_panel.add(empty_label);

    p_board_frame.set_context_sensitive_help(this, "WindowSelectParameter");

    this.refresh();
    this.pack();
    this.setResizable(false);
  }

  /** Refreshes the displayed values in this window. */
  @Override
  public void refresh() {
    if (this.board_handling.settings.get_select_on_all_visible_layers()) {
      settings_select_all_visible_button.setSelected(true);
    } else {
      settings_select_current_only_button.setSelected(true);
    }
    ItemSelectionFilter item_selection_filter =
        this.board_handling.settings.get_item_selection_filter();
    if (item_selection_filter == null) {
      FRLogger.warn("SelectParameterWindow.refresh: item_selection_filter is null");
    } else {
      final ItemSelectionFilter.SelectableChoices[] filter_values =
          ItemSelectionFilter.SelectableChoices.values();
      for (int i = 0; i < filter_values.length; ++i) {
        this.settings_select_item_selection_choices[i].setSelected(
            item_selection_filter.is_selected(filter_values[i]));
      }
    }
    settings_select_layer_name_arr[this.board_handling.settings.get_layer()].setSelected(true);
  }

  /** Selects the layer with the input signal number. */
  public void select(int p_signal_layer_no) {
    settings_select_layer_name_arr[p_signal_layer_no].setSelected(true);
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
      board_handling.set_current_layer(layer_no);
    }
  }

  private class AllVisibleListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_select_on_all_visible_layers(true);
    }
  }

  private class CurrentOnlyListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.set_select_on_all_visible_layers(false);
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

      ItemSelectionFilter.SelectableChoices item_type =
          ItemSelectionFilter.SelectableChoices.values()[item_no];

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
