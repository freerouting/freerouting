package app.freerouting.gui;

import app.freerouting.board.BoardOutline;
import app.freerouting.interactive.BoardHandling;

import app.freerouting.management.FRAnalytics;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Window handling detail parameters of the interactive routing. */
public class WindowRouteDetail extends BoardSavableSubWindow {

  private static final int c_max_slider_value = 100;
  private static final int c_accuracy_scale_factor = 20;
  private final BoardHandling board_handling;
  private final JSlider accuracy_slider;
  private final JRadioButton route_detail_on_button;
  private final JRadioButton route_detail_off_button;
  private final JCheckBox route_detail_outline_keepout_check_box;
  /** Creates a new instance of RouteDetailWindow */
  public WindowRouteDetail(BoardFrame p_board_frame) {
    this.board_handling = p_board_frame.board_panel.board_handling;
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowRouteDetail", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    // create main panel

    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.anchor = GridBagConstraints.WEST;
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);

    // add label and button group for the clearance compensation.

    JLabel clearance_compensation_label =
        new JLabel(resources.getString("clearance_compensation"));
    clearance_compensation_label.setToolTipText(
        resources.getString("clearance_compensation_tooltip"));

    gridbag_constraints.gridwidth = GridBagConstraints.RELATIVE;
    gridbag_constraints.gridheight = 2;
    gridbag.setConstraints(clearance_compensation_label, gridbag_constraints);
    main_panel.add(clearance_compensation_label);

    route_detail_on_button = new JRadioButton(resources.getString("on"));
    route_detail_off_button = new JRadioButton(resources.getString("off"));

    route_detail_on_button.addActionListener(new CompensationOnListener());
    route_detail_on_button.addActionListener(evt -> FRAnalytics.buttonClicked("route_detail_on_button", route_detail_on_button.getText()));
    route_detail_off_button.addActionListener(new CompensationOffListener());
    route_detail_off_button.addActionListener(evt -> FRAnalytics.buttonClicked("route_detail_off_button", route_detail_off_button.getText()));

    ButtonGroup clearance_compensation_button_group = new ButtonGroup();
    clearance_compensation_button_group.add(route_detail_on_button);
    clearance_compensation_button_group.add(route_detail_off_button);
    route_detail_off_button.setSelected(true);

    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag_constraints.gridheight = 1;
    gridbag.setConstraints(route_detail_on_button, gridbag_constraints);
    main_panel.add(route_detail_on_button, gridbag_constraints);
    gridbag.setConstraints(route_detail_off_button, gridbag_constraints);
    main_panel.add(route_detail_off_button, gridbag_constraints);

    JLabel separator =
        new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add label and slider for the pull tight accuracy.

    JLabel pull_tight_accuracy_label =
        new JLabel(resources.getString("pull_tight_accuracy"));
    pull_tight_accuracy_label.setToolTipText(resources.getString("pull_tight_accuracy_tooltip"));
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag.setConstraints(pull_tight_accuracy_label, gridbag_constraints);
    main_panel.add(pull_tight_accuracy_label);

    this.accuracy_slider = new JSlider();
    accuracy_slider.setMaximum(c_max_slider_value);
    accuracy_slider.addChangeListener(new SliderChangeListener());
    gridbag.setConstraints(accuracy_slider, gridbag_constraints);
    main_panel.add(accuracy_slider);

    separator = new JLabel("  ––––––––––––––––––––––––––––––––––––––––  ");
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    // add switch to define, if keepout is generated outside the outline.

    route_detail_outline_keepout_check_box = new JCheckBox(resources.getString("keepout_outside_outline"));
    route_detail_outline_keepout_check_box.setSelected(false);
    route_detail_outline_keepout_check_box.addActionListener(new OutLineKeepoutListener());
    route_detail_outline_keepout_check_box.addActionListener(evt -> FRAnalytics.buttonClicked("route_detail_outline_keepout_check_box", route_detail_outline_keepout_check_box.getText()));
    gridbag.setConstraints(route_detail_outline_keepout_check_box, gridbag_constraints);
    route_detail_outline_keepout_check_box.setToolTipText(resources.getString("keepout_outside_outline_tooltip"));
    main_panel.add(route_detail_outline_keepout_check_box, gridbag_constraints);

    separator = new JLabel();
    gridbag.setConstraints(separator, gridbag_constraints);
    main_panel.add(separator, gridbag_constraints);

    this.refresh();
    this.pack();
    this.setResizable(false);
  }

  /** Recalculates all displayed values */
  @Override
  public void refresh() {
    if (this.board_handling
        .get_routing_board()
        .search_tree_manager
        .is_clearance_compensation_used()) {
      this.route_detail_on_button.setSelected(true);
    } else {
      this.route_detail_off_button.setSelected(true);
    }
    BoardOutline outline = this.board_handling.get_routing_board().get_outline();
    if (outline != null) {
      this.route_detail_outline_keepout_check_box.setSelected(outline.keepout_outside_outline_generated());
    }
    int accuracy_slider_value =
        c_max_slider_value
            - this.board_handling.settings.get_trace_pull_tight_accuracy() / c_accuracy_scale_factor
            + 1;
    accuracy_slider.setValue(accuracy_slider_value);
  }

  private class CompensationOnListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.set_clearance_compensation(true);
    }
  }

  private class CompensationOffListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      board_handling.set_clearance_compensation(false);
    }
  }

  private class SliderChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent evt) {
      int new_accuracy =
          (c_max_slider_value - accuracy_slider.getValue() + 1) * c_accuracy_scale_factor;
      board_handling.settings.set_current_pull_tight_accuracy(new_accuracy);
    }
  }

  private class OutLineKeepoutListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (board_handling.is_board_read_only()) {
        return;
      }
      BoardOutline outline = board_handling.get_routing_board().get_outline();
      if (outline != null) {
        outline.generate_keepout_outside(route_detail_outline_keepout_check_box.isSelected());
      }
    }
  }
}
