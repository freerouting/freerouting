package app.freerouting.gui;

import app.freerouting.interactive.BoardHandling;
import app.freerouting.interactive.SnapShot;

import app.freerouting.management.FRAnalytics;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Window for the settings of interactive snapshots. */
public class WindowSnapshotSettings extends BoardSavableSubWindow {

  final JCheckBox other_snapshots_settings_object_color_check_box;
  final JCheckBox other_snapshots_settings_object_visibility_check_box;
  final JCheckBox other_snapshots_settings_layer_visibility_check_box;
  final JCheckBox other_snapshots_settings_display_region_check_box;
  final JCheckBox other_snapshots_settings_interactive_state_check_box;
  final JCheckBox other_snapshots_settings_selection_layers_check_box;
  final JCheckBox other_snapshots_settings_selectable_items_check_box;
  final JCheckBox other_snapshots_settings_current_layer_check_box;
  final JCheckBox other_snapshots_settings_rule_selection_check_box;
  final JCheckBox other_snapshots_settings_manual_rule_settings_check_box;
  final JCheckBox other_snapshots_settings_push_and_shove_enabled_check_box;
  final JCheckBox other_snapshots_settings_drag_components_enabled_check_box;
  final JCheckBox other_snapshots_settings_pull_tight_region_check_box;
  final JCheckBox other_snapshots_settings_component_grid_check_box;
  final JCheckBox other_snapshots_settings_info_list_filter_check_box;
  private final BoardHandling board_handling;
  /** Creates a new instance of WindowSnapshotSettings */
  public WindowSnapshotSettings(BoardFrame p_board_frame) {
    this.board_handling = p_board_frame.board_panel.board_handling;

    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowSnapshotSettings", p_board_frame.get_locale());
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

    // add check box for the object colors

    this.other_snapshots_settings_object_color_check_box = new JCheckBox(resources.getString("object_colors"));
    gridbag.setConstraints(other_snapshots_settings_object_color_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_object_color_check_box, gridbag_constraints);
    other_snapshots_settings_object_color_check_box.addActionListener(new ObjectColorListener());
    other_snapshots_settings_object_color_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_object_color_check_box", other_snapshots_settings_object_color_check_box.getText()));

    // add check box for the object visibility

    this.other_snapshots_settings_object_visibility_check_box =
        new JCheckBox(resources.getString("object_visibility"));
    gridbag.setConstraints(other_snapshots_settings_object_visibility_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_object_visibility_check_box, gridbag_constraints);
    other_snapshots_settings_object_visibility_check_box.addActionListener(new ObjectVisibilityListener());
    other_snapshots_settings_object_visibility_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_object_visibility_check_box", other_snapshots_settings_object_visibility_check_box.getText()));

    // add check box for the layer visibility

    this.other_snapshots_settings_layer_visibility_check_box =
        new JCheckBox(resources.getString("layer_visibility"));
    gridbag.setConstraints(other_snapshots_settings_layer_visibility_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_layer_visibility_check_box, gridbag_constraints);
    other_snapshots_settings_layer_visibility_check_box.addActionListener(new LayerVisibilityListener());
    other_snapshots_settings_layer_visibility_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_layer_visibility_check_box", other_snapshots_settings_layer_visibility_check_box.getText()));

    // add check box for display region

    this.other_snapshots_settings_display_region_check_box =
        new JCheckBox(resources.getString("display_region"));
    gridbag.setConstraints(other_snapshots_settings_display_region_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_display_region_check_box, gridbag_constraints);
    other_snapshots_settings_display_region_check_box.addActionListener(new DisplayRegionListener());
    other_snapshots_settings_display_region_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_display_region_check_box", other_snapshots_settings_display_region_check_box.getText()));

    JLabel separator =
        new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add check box for the interactive state

    this.other_snapshots_settings_interactive_state_check_box =
        new JCheckBox(resources.getString("interactive_state"));
    gridbag.setConstraints(other_snapshots_settings_interactive_state_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_interactive_state_check_box, gridbag_constraints);
    other_snapshots_settings_interactive_state_check_box.addActionListener(new InteractiveStateListener());
    other_snapshots_settings_interactive_state_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_interactive_state_check_box", other_snapshots_settings_interactive_state_check_box.getText()));

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add check box for the selection layers

    this.other_snapshots_settings_selection_layers_check_box =
        new JCheckBox(resources.getString("selection_layers"));
    gridbag.setConstraints(other_snapshots_settings_selection_layers_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_selection_layers_check_box, gridbag_constraints);
    other_snapshots_settings_selection_layers_check_box.addActionListener(new SelectionLayersListener());
    other_snapshots_settings_selection_layers_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_selection_layers_check_box", other_snapshots_settings_selection_layers_check_box.getText()));

    // add check box for the selectable items

    this.other_snapshots_settings_selectable_items_check_box =
        new JCheckBox(resources.getString("selectable_items"));
    gridbag.setConstraints(other_snapshots_settings_selectable_items_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_selectable_items_check_box, gridbag_constraints);
    other_snapshots_settings_selectable_items_check_box.addActionListener(new SelectableItemsListener());
    other_snapshots_settings_selectable_items_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_selectable_items_check_box", other_snapshots_settings_selectable_items_check_box.getText()));

    // add check box for the current layer

    this.other_snapshots_settings_current_layer_check_box = new JCheckBox(resources.getString("current_layer"));
    gridbag.setConstraints(other_snapshots_settings_current_layer_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_current_layer_check_box, gridbag_constraints);
    other_snapshots_settings_current_layer_check_box.addActionListener(new CurrentLayerListener());
    other_snapshots_settings_current_layer_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_current_layer_check_box", other_snapshots_settings_current_layer_check_box.getText()));

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add check box for the rule selection

    this.other_snapshots_settings_rule_selection_check_box =
        new JCheckBox(resources.getString("rule_selection"));
    gridbag.setConstraints(other_snapshots_settings_rule_selection_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_rule_selection_check_box, gridbag_constraints);
    other_snapshots_settings_rule_selection_check_box.addActionListener(new RuleSelectionListener());
    other_snapshots_settings_rule_selection_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_rule_selection_check_box", other_snapshots_settings_rule_selection_check_box.getText()));

    // add check box for the manual rule settings

    this.other_snapshots_settings_manual_rule_settings_check_box =
        new JCheckBox(resources.getString("manual_rule_settings"));
    gridbag.setConstraints(other_snapshots_settings_manual_rule_settings_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_manual_rule_settings_check_box, gridbag_constraints);
    other_snapshots_settings_manual_rule_settings_check_box.addActionListener(new ManualRuleSettingsListener());
    other_snapshots_settings_manual_rule_settings_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_manual_rule_settings_check_box", other_snapshots_settings_manual_rule_settings_check_box.getText()));

    // add check box for push and shove enabled

    this.other_snapshots_settings_push_and_shove_enabled_check_box =
        new JCheckBox(resources.getString("push&shove_enabled"));
    gridbag.setConstraints(other_snapshots_settings_push_and_shove_enabled_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_push_and_shove_enabled_check_box, gridbag_constraints);
    other_snapshots_settings_push_and_shove_enabled_check_box.addActionListener(new PushAndShoveEnabledListener());
    other_snapshots_settings_push_and_shove_enabled_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_push_and_shove_enabled_check_box", other_snapshots_settings_push_and_shove_enabled_check_box.getText()));

    // add check box for drag components enabled

    this.other_snapshots_settings_drag_components_enabled_check_box =
        new JCheckBox(resources.getString("drag_components_enabled"));
    gridbag.setConstraints(other_snapshots_settings_drag_components_enabled_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_drag_components_enabled_check_box, gridbag_constraints);
    other_snapshots_settings_drag_components_enabled_check_box.addActionListener(new DragComponentsEnabledListener());
    other_snapshots_settings_drag_components_enabled_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_drag_components_enabled_check_box", other_snapshots_settings_drag_components_enabled_check_box.getText()));

    // add check box for the pull tight region

    this.other_snapshots_settings_pull_tight_region_check_box =
        new JCheckBox(resources.getString("pull_tight_region"));
    gridbag.setConstraints(other_snapshots_settings_pull_tight_region_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_pull_tight_region_check_box, gridbag_constraints);
    other_snapshots_settings_pull_tight_region_check_box.addActionListener(new PullTightRegionListener());
    other_snapshots_settings_pull_tight_region_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_pull_tight_region_check_box", other_snapshots_settings_pull_tight_region_check_box.getText()));

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add check box for the component grid

    this.other_snapshots_settings_component_grid_check_box =
        new JCheckBox(resources.getString("component_grid"));
    gridbag.setConstraints(other_snapshots_settings_component_grid_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_component_grid_check_box, gridbag_constraints);
    other_snapshots_settings_component_grid_check_box.addActionListener(new ComponentGridListener());
    other_snapshots_settings_component_grid_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_component_grid_check_box", other_snapshots_settings_component_grid_check_box.getText()));

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add check box for the info list filters

    this.other_snapshots_settings_info_list_filter_check_box =
        new JCheckBox(resources.getString("info_list_selections"));
    gridbag.setConstraints(other_snapshots_settings_info_list_filter_check_box, gridbag_constraints);
    main_panel.add(other_snapshots_settings_info_list_filter_check_box, gridbag_constraints);
    other_snapshots_settings_info_list_filter_check_box.addActionListener(new InfoListFilterListener());
    other_snapshots_settings_info_list_filter_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_info_list_filter_check_box", other_snapshots_settings_info_list_filter_check_box.getText()));

    p_board_frame.set_context_sensitive_help(this, "WindowSnapshots_SnapshotSettings");

    this.refresh();
    this.pack();
    this.setResizable(false);
  }

  /** Recalculates all displayed values */
  @Override
  public void refresh() {
    SnapShot.Attributes attributes =
        this.board_handling.settings.get_snapshot_attributes();
    this.other_snapshots_settings_object_color_check_box.setSelected(attributes.object_colors);
    this.other_snapshots_settings_object_visibility_check_box.setSelected(attributes.object_visibility);
    this.other_snapshots_settings_layer_visibility_check_box.setSelected(attributes.layer_visibility);
    this.other_snapshots_settings_display_region_check_box.setSelected(attributes.display_region);
    this.other_snapshots_settings_interactive_state_check_box.setSelected(attributes.interactive_state);
    this.other_snapshots_settings_selection_layers_check_box.setSelected(attributes.selection_layers);
    this.other_snapshots_settings_selectable_items_check_box.setSelected(attributes.selectable_items);
    this.other_snapshots_settings_current_layer_check_box.setSelected(attributes.current_layer);
    this.other_snapshots_settings_rule_selection_check_box.setSelected(attributes.rule_selection);
    this.other_snapshots_settings_manual_rule_settings_check_box.setSelected(attributes.manual_rule_settings);
    this.other_snapshots_settings_push_and_shove_enabled_check_box.setSelected(attributes.push_and_shove_enabled);
    this.other_snapshots_settings_drag_components_enabled_check_box.setSelected(attributes.drag_components_enabled);
    this.other_snapshots_settings_pull_tight_region_check_box.setSelected(attributes.pull_tight_region);
    this.other_snapshots_settings_component_grid_check_box.setSelected(attributes.component_grid);
    other_snapshots_settings_info_list_filter_check_box.setSelected(attributes.info_list_selections);
  }

  private class ObjectColorListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().object_colors =
          other_snapshots_settings_object_color_check_box.isSelected();
    }
  }

  private class ObjectVisibilityListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().object_visibility =
          other_snapshots_settings_object_visibility_check_box.isSelected();
    }
  }

  private class LayerVisibilityListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().layer_visibility =
          other_snapshots_settings_layer_visibility_check_box.isSelected();
    }
  }

  private class DisplayRegionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().display_region =
          other_snapshots_settings_display_region_check_box.isSelected();
    }
  }

  private class InteractiveStateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().interactive_state =
          other_snapshots_settings_interactive_state_check_box.isSelected();
    }
  }

  private class SelectionLayersListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().selection_layers =
          other_snapshots_settings_selection_layers_check_box.isSelected();
    }
  }

  private class SelectableItemsListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().selectable_items =
          other_snapshots_settings_selectable_items_check_box.isSelected();
    }
  }

  private class CurrentLayerListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().current_layer =
          other_snapshots_settings_current_layer_check_box.isSelected();
    }
  }

  private class RuleSelectionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().rule_selection =
          other_snapshots_settings_rule_selection_check_box.isSelected();
    }
  }

  private class ManualRuleSettingsListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().manual_rule_settings =
          other_snapshots_settings_manual_rule_settings_check_box.isSelected();
    }
  }

  private class PushAndShoveEnabledListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().push_and_shove_enabled =
          other_snapshots_settings_push_and_shove_enabled_check_box.isSelected();
    }
  }

  private class DragComponentsEnabledListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().drag_components_enabled =
          other_snapshots_settings_drag_components_enabled_check_box.isSelected();
    }
  }

  private class PullTightRegionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().pull_tight_region =
          other_snapshots_settings_pull_tight_region_check_box.isSelected();
    }
  }

  private class ComponentGridListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().component_grid =
          other_snapshots_settings_component_grid_check_box.isSelected();
    }
  }

  private class InfoListFilterListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.settings.get_snapshot_attributes().info_list_selections =
          other_snapshots_settings_info_list_filter_check_box.isSelected();
    }
  }
}
