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
  public LogicalPart(String pName, int pNo, PartPin[] pPartPinArr) {
    name = pName;
    no = pNo;
    partPinArr = pPartPinArr;
  }

  public int pinCount() {
    return partPinArr.length;
  }

  /** Returns the pim with index p_no. Pin numbers are from 0 to pinCount - 1 */
  public PartPin getPin(int pNo) {
    if (pNo < 0 || pNo >= partPinArr.length) {
      FRLogger.warn("LogicalPart.get_pin: p_no out of range");
      return null;
    }
    return partPinArr[pNo];
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("logical_part_2") + " ");
    pWindow.appendBold(this.name);
    for (int i = 0; i < this.partPinArr.length; i++) {
      PartPin currPin = this.partPinArr[i];
      pWindow.newline();
      pWindow.indent();
      pWindow.append(tm.getText("pin") + " ");
      pWindow.append(currPin.pinName);
      pWindow.append(", " + tm.getText("gate") + " ");
      pWindow.append(currPin.gateName);
      pWindow.append(", " + tm.getText("swap_code") + " ");
      int gateSwapCode = currPin.gateSwapCode;
      pWindow.append(String.valueOf(gateSwapCode));
      pWindow.append(", " + tm.getText("gate_pin") + " ");
      pWindow.append(currPin.gatePinName);
      pWindow.append(", " + tm.getText("swap_code") + " ");
      int pinSwapCode = currPin.gatePinSwapCode;
      pWindow.append(String.valueOf(pinSwapCode));
    }
    pWindow.newline();
    pWindow.newline();
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
        int pPinNo,
        String pPinName,
        String pGateName,
        int pGateSwapCode,
        String pGatePinName,
        int pGatePinSwapCode) {
      pinNo = pPinNo;
      pinName = pPinName;
      gateName = pGateName;
      gateSwapCode = pGateSwapCode;
      gatePinName = pGatePinName;
      gatePinSwapCode = pGatePinSwapCode;
    }

    @Override
    public int compareTo(PartPin pOther) {
      return this.pinNo - pOther.pinNo;
    }
  }
}
