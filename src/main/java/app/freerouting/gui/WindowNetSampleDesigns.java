package app.freerouting.gui;

/** Window with a list for selecting sample board designs in the net. */
public class WindowNetSampleDesigns extends WindowNetSamples {

  /** Creates a new instance of WindowNetSampleDesigns */
  public WindowNetSampleDesigns(java.util.Locale p_locale) {
    super(p_locale, "sample_designs", "open_sample_design", 11);
  }

  protected void fill_list() {
    this.add("hexapod_empty.dsn");
    this.add("hexapod_autorouted.dsn");
    this.add("sharc_handrouted.dsn");
    this.add("at14_empty.dsn");
    this.add("at14_autorouted.dsn");
    this.add("sharp_empty.dsn");
    this.add("sharp_autorouted.dsn");
    this.add("bigdesign_unrouted.dsn");
    this.add("int_empty.dsn");
    this.add("int_autorouted.dsn");
    this.add("single_layer_empty.dsn");
    this.add("single_layer_handrouted.dsn");
  }

  protected void button_pushed() {
    int index = list.getSelectedIndex();
    if (index < 0 || index >= list_model.getSize()) {
      return;
    }
    String design_name = list_model.elementAt(index).design_name;
    String[] name_parts = design_name.split("\\.");
    String archive_name = name_parts[0];
    open_design(archive_name, design_name, this.locale, false, 0);
  }
}
