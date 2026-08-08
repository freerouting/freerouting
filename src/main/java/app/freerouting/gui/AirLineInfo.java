package app.freerouting.gui;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.drc.AirLine;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Wrapper class for displaying information about an AirLine in the ObjectInfoPanel. */
public class AirLineInfo implements ObjectInfoPanel.Printable {

  public final AirLine airline;

  public AirLineInfo(AirLine pAirline) {
    this.airline = pAirline;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(AirLine.class, pLocale);

    pWindow.appendBold(tm.getText("incomplete"));
    pWindow.append(" " + tm.getText("net") + " ");
    pWindow.append(airline.net.name);
    pWindow.append(" " + tm.getText("from") + " ", "Incomplete Start Item", airline.fromItem);
    pWindow.append(airline.fromCorner);
    pWindow.append(" " + tm.getText("to") + " ", "Incomplete End Item", airline.toItem);
    pWindow.append(airline.toCorner);
    pWindow.newline();
  }

  @Override
  public String toString() {
    return this.airline.toString();
  }
}
