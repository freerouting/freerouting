package app.freerouting.core;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/** Contains information for gate swap and pin swap for a single component. */
public class LogicalPart implements ObjectInfoPanel.Printable, Serializable {

  public final String name;
  public final int no;
  private final PartPin[] partPinArr;

  /**
   * Creates a new instance of LogicalPart. The part pins are sorted by pinNo. The pinNo's of the
   * part pins must be the same number as in the components' library package.
   */
  public LogicalPart(String p_name, int p_no, PartPin[] p_part_pin_arr) {
    name = p_name;
    no = p_no;
    partPinArr = p_part_pin_arr;
  }

  public int pin_count() {
    return partPinArr.length;
  }

  /** Returns the pim with index p_no. Pin numbers are from 0 to pinCount - 1 */
  public PartPin get_pin(int p_no) {
    if (p_no < 0 || p_no >= partPinArr.length) {
      FRLogger.warn("LogicalPart.get_pin: p_no out of range");
      return null;
    }
    return partPinArr[p_no];
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("logical_part_2") + " ");
    p_window.append_bold(this.name);
    for (int i = 0; i < this.partPinArr.length; i++) {
      PartPin currPin = this.partPinArr[i];
      p_window.newline();
      p_window.indent();
      p_window.append(tm.getText("pin") + " ");
      p_window.append(currPin.pinName);
      p_window.append(", " + tm.getText("gate") + " ");
      p_window.append(currPin.gateName);
      p_window.append(", " + tm.getText("swap_code") + " ");
      int gateSwapCode = currPin.gateSwapCode;
      p_window.append(String.valueOf(gateSwapCode));
      p_window.append(", " + tm.getText("gate_pin") + " ");
      p_window.append(currPin.gatePinName);
      p_window.append(", " + tm.getText("swap_code") + " ");
      int pinSwapCode = currPin.gatePinSwapCode;
      p_window.append(String.valueOf(pinSwapCode));
    }
    p_window.newline();
    p_window.newline();
  }

  public static class PartPin implements Comparable<PartPin>, Serializable {

    /** The number of the part pin. Must be the same number as in the components library package. */
    public final int pinNo;

    /** The name of the part pin. Must be the same name as in the components library package. */
    public final String pinName;

    /** The name of the gate this pin belongs to. */
    public final String gateName;

    /**
     * The gate swap code. Gates with the same gate swap code can be swapped. Gates with swap code
     * {@literal <}= 0 are not swappable.
     */
    public final int gateSwapCode;

    /** The identifier of the pin in the gate. */
    public final String gatePinName;

    /**
     * The pin swap code of the gate. Pins with the same pin swap code can be swapped inside a gate.
     * Pins with swap code {@literal <}= 0 are not swappable.
     */
    public final int gatePinSwapCode;

    public PartPin(
        int p_pin_no,
        String p_pin_name,
        String p_gate_name,
        int p_gate_swap_code,
        String p_gate_pin_name,
        int p_gate_pin_swap_code) {
      pinNo = p_pin_no;
      pinName = p_pin_name;
      gateName = p_gate_name;
      gateSwapCode = p_gate_swap_code;
      gatePinName = p_gate_pin_name;
      gatePinSwapCode = p_gate_pin_swap_code;
    }

    @Override
    public int compareTo(PartPin p_other) {
      return this.pinNo - p_other.pinNo;
    }
  }
}
