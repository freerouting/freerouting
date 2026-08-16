package app.freerouting.gui;

import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.drc.AirLine;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Wrapper class for displaying information about an AirLine in the ItemInfoPrinter. */
public class AirLineInfo implements ItemInfoPrinter.Printable {

  public final AirLine airline;

  /**
   * Creates an information-panel adapter for an incomplete connection.
   *
   * @param airline the incomplete connection to display
   */
  public AirLineInfo(AirLine airline) {
    this.airline = airline;
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(AirLine.class, locale);

    printer.appendBold(tm.getText("incomplete"));
    printer.append(" " + tm.getText("net") + " ");
    printer.append(airline.net.name);
    printer.append(" " + tm.getText("from") + " ", "Incomplete Start Item", airline.fromItem);
    printer.append(airline.fromCorner);
    printer.append(" " + tm.getText("to") + " ", "Incomplete End Item", airline.toItem);
    printer.append(airline.toCorner);
    printer.newline();
  }

  @Override
  public String toString() {
    return this.airline.toString();
  }
}
