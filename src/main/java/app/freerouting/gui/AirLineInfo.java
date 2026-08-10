package app.freerouting.gui;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.drc.AirLine;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Wrapper class for displaying information about an AirLine in the ObjectInfoPanel. */
public class AirLineInfo implements ObjectInfoPanel.Printable {

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
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(AirLine.class, locale);

    window.appendBold(tm.getText("incomplete"));
    window.append(" " + tm.getText("net") + " ");
    window.append(airline.net.name);
    window.append(" " + tm.getText("from") + " ", "Incomplete Start Item", airline.fromItem);
    window.append(airline.fromCorner);
    window.append(" " + tm.getText("to") + " ", "Incomplete End Item", airline.toItem);
    window.append(airline.toCorner);
    window.newline();
  }

  @Override
  public String toString() {
    return this.airline.toString();
  }
}
