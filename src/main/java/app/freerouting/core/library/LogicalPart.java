package app.freerouting.core.library;

import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/** Contains information for gate swap and pin swap for a single component. */
public class LogicalPart implements ItemInfoPrinter.Printable, Serializable {

  public final String name;
  public final int id;
  private final PartPin[] partPinArr;

  /**
   * Creates a new instance of LogicalPart. The part pins are sorted by pinIndex. The pinIndex's of
   * the part pins must be the same index as in the components' library package.
   */
  public LogicalPart(String name, int id, PartPin[] partPinArr) {
    this.name = name;
    this.id = id;
    this.partPinArr = partPinArr;
  }

  /** Returns the number of pins in this logical part. */
  public int pinCount() {
    return partPinArr.length;
  }

  /** Returns the pin with the specified index. Pin indices range from 0 to pinCount - 1. */
  public PartPin getPin(int pinIndex) {
    if (pinIndex < 0 || pinIndex >= partPinArr.length) {
      FRLogger.warn("LogicalPart.getPin: pinIndex out of range");
      return null;
    }
    return partPinArr[pinIndex];
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("logical_part_2") + " ");
    printer.appendBold(this.name);
    for (int i = 0; i < this.partPinArr.length; i++) {
      PartPin currentPin = this.partPinArr[i];
      printer.newline();
      printer.indent();
      printer.append(tm.getText("pin") + " ");
      printer.append(currentPin.pinName);
      printer.append(", " + tm.getText("gate") + " ");
      printer.append(currentPin.gateName);
      printer.append(", " + tm.getText("swap_code") + " ");
      int gateSwapCode = currentPin.gateSwapCode;
      printer.append(String.valueOf(gateSwapCode));
      printer.append(", " + tm.getText("gate_pin") + " ");
      printer.append(currentPin.gatePinName);
      printer.append(", " + tm.getText("swap_code") + " ");
      int pinSwapCode = currentPin.gatePinSwapCode;
      printer.append(String.valueOf(pinSwapCode));
    }
    printer.newline();
    printer.newline();
  }

  /** Describes a pin belonging to a logical part. */
  public static class PartPin implements Comparable<PartPin>, Serializable {

    /** The index of the part pin. Must be the same index as in the components library package. */
    public final int pinIndex;

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

    /** Creates a part pin with its gate and swap metadata. */
    public PartPin(
        int pinIndex,
        String pinName,
        String gateName,
        int gateSwapCode,
        String gatePinName,
        int gatePinSwapCode) {
      this.pinIndex = pinIndex;
      this.pinName = pinName;
      this.gateName = gateName;
      this.gateSwapCode = gateSwapCode;
      this.gatePinName = gatePinName;
      this.gatePinSwapCode = gatePinSwapCode;
    }

    @Override
    public int compareTo(PartPin other) {
      return this.pinIndex - other.pinIndex;
    }
  }
}
