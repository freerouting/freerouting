package app.freerouting.library;

import app.freerouting.logger.FRLogger;

/** Contains information for gate swap and pin swap for a single component. */
public class LogicalPart
    implements app.freerouting.board.ObjectInfoPanel.Printable, java.io.Serializable {

  public final String name;
  public final int no;
  private final PartPin[] part_pin_arr;

  /**
   * Creates a new instance of LogicalPart. The part pins are sorted by pin_no. The pin_no's of the
   * part pins must be the same number as in the components' library package.
   */
  public LogicalPart(String p_name, int p_no, PartPin[] p_part_pin_arr) {
    name = p_name;
    no = p_no;
    part_pin_arr = p_part_pin_arr;
  }

  public int pin_count() {
    return part_pin_arr.length;
  }

  /** Returns the pim with index p_no. Pin numbers are from 0 to pin_count - 1 */
  public PartPin get_pin(int p_no) {
    if (p_no < 0 || p_no >= part_pin_arr.length) {
      FRLogger.warn("LogicalPart.get_pin: p_no out of range");
      return null;
    }
    return part_pin_arr[p_no];
  }

  public void print_info(
      app.freerouting.board.ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("logical_part_2") + " ");
    p_window.append_bold(this.name);
    for (int i = 0; i < this.part_pin_arr.length; ++i) {
      PartPin curr_pin = this.part_pin_arr[i];
      p_window.newline();
      p_window.indent();
      p_window.append(resources.getString("pin") + " ");
      p_window.append(curr_pin.pin_name);
      p_window.append(", " + resources.getString("gate") + " ");
      p_window.append(curr_pin.gate_name);
      p_window.append(", " + resources.getString("swap_code") + " ");
      Integer gate_swap_code = curr_pin.gate_swap_code;
      p_window.append(gate_swap_code.toString());
      p_window.append(", " + resources.getString("gate_pin") + " ");
      p_window.append(curr_pin.gate_pin_name);
      p_window.append(", " + resources.getString("swap_code") + " ");
      Integer pin_swap_code = curr_pin.gate_pin_swap_code;
      p_window.append(pin_swap_code.toString());
    }
    p_window.newline();
    p_window.newline();
  }

  public static class PartPin implements Comparable<PartPin>, java.io.Serializable {
    /**
     * The number of the part pin. Must be the same number as in the componnents library package.
     */
    public final int pin_no;
    /** The name of the part pin. Must be the same name as in the componnents library package. */
    public final String pin_name;
    /** The name of the gate this pin belongs to. */
    public final String gate_name;
    /**
     * The gate swap code. Gates with the same gate swap code can be swapped. Gates with swap code
     * {@literal <}= 0 are not swappable.
     */
    public final int gate_swap_code;
    /** The identifier of the pin in the gate. */
    public final String gate_pin_name;
    /**
     * The pin swap code of the gate. Pins with the same pin swap code can be swapped inside a gate.
     * Pins with swap code {@literal <}= 0 are not swappable.
     */
    public final int gate_pin_swap_code;

    public PartPin(
        int p_pin_no,
        String p_pin_name,
        String p_gate_name,
        int p_gate_swap_code,
        String p_gate_pin_name,
        int p_gate_pin_swap_code) {
      pin_no = p_pin_no;
      pin_name = p_pin_name;
      gate_name = p_gate_name;
      gate_swap_code = p_gate_swap_code;
      gate_pin_name = p_gate_pin_name;
      gate_pin_swap_code = p_gate_pin_swap_code;
    }

    public int compareTo(PartPin p_other) {
      return this.pin_no - p_other.pin_no;
    }
  }
}
