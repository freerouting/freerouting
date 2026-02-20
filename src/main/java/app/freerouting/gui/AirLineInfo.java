package app.freerouting.gui;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.drc.AirLine;
import app.freerouting.management.TextManager;
import java.util.Locale;

/**
 * Wrapper class for displaying information about an AirLine in the
 * ObjectInfoPanel.
 */
public class AirLineInfo implements ObjectInfoPanel.Printable {

    public final AirLine airline;

    public AirLineInfo(AirLine p_airline) {
        this.airline = p_airline;
    }

    @Override
    public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
        TextManager tm = new TextManager(AirLine.class, p_locale);

        p_window.append_bold(tm.getText("incomplete"));
        p_window.append(" " + tm.getText("net") + " ");
        p_window.append(airline.net.name);
        p_window.append(" " + tm.getText("from") + " ", "Incomplete Start Item", airline.from_item);
        p_window.append(airline.from_corner);
        p_window.append(" " + tm.getText("to") + " ", "Incomplete End Item", airline.to_item);
        p_window.append(airline.to_corner);
        p_window.newline();
    }

}
