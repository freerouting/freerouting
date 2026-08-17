package app.freerouting.gui;

import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.drc.AirLine;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Wrapper record for displaying information about an AirLine in the ItemInfoPrinter. */
public record AirLineInfo(AirLine airline) implements ItemInfoPrinter.Printable {

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
