package app.freerouting.gui;

/** Window with a list for selecting router demonstrations in the net. */
public class WindowNetDemonstrations extends WindowNetSamples {

  /** Creates a new instance of WindowNetDemonstration */
  public WindowNetDemonstrations(java.util.Locale p_locale) {
    super(p_locale, "router_demonstrations", "replay_example", 7);
  }

  /**
   * To be edited when the demonstration examples change. For every String in the second column a
   * String has to be added to the resource file WindowNetSamples.
   */
  protected void fill_list() {
    add("sample_45.dsn", "45_degree_logfile", AdditionalAction.READ_LOGFILE);
    add("int_ar.dsn", "drag_component_logfile", AdditionalAction.READ_LOGFILE);
    add("single_layer.dsn", "any_angle_logfile", AdditionalAction.READ_LOGFILE);
    add("hexapod_empty.dsn", "autorouter_example_1", AdditionalAction.AUTOROUTE);
    add("at14_empty.dsn", "autorouter_example_2", AdditionalAction.AUTOROUTE);
    add("sharp_empty.dsn", "autorouter_example_3", AdditionalAction.AUTOROUTE);
  }

  protected void button_pushed() {
    int index = list.getSelectedIndex();
    if (index < 0 || index >= list_model.getSize()) {
      return;
    }
    SampleDesignListElement selected_element = list_model.elementAt(index);
    String[] name_parts = selected_element.design_name.split("\\.");
    String archive_name = name_parts[0];
    BoardFrame new_frame =
        open_design(archive_name, selected_element.design_name, this.locale, false, 0);
    if (new_frame != null) {
      selected_element.additional_action.perform(new_frame, archive_name);
    }
  }
}
