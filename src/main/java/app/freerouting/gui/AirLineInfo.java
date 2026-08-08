package app.freerouting.gui;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.drc.AirLine;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Wrapper class for displaying information about an AirLine in the ObjectInfoPanel. */
public class AirLineInfo implements ObjectInfoPanel.Printable {

  public final AirLine airline;

  public AirLineInfo(AirLine p_airline) {
    this.airline = p_airline;
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(AirLine.class, p_locale);

    p_window.appendBold(tm.getText("incomplete"));
    p_window.append(" " + tm.getText("net") + " ");
    p_window.append(airline.net.name);
    p_window.append(" " + tm.getText("from") + " ", "Incomplete Start Item", airline.fromItem);
    p_window.append(airline.fromCorner);
    p_window.append(" " + tm.getText("to") + " ", "Incomplete End Item", airline.toItem);
    p_window.append(airline.toCorner);
    p_window.newline();
  }

  @Override
  public String toString() {
    return this.airline.toString();
  }
}
