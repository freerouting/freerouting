package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Abstract class for windows displaying a list of objects The object name can
 * be filtered by an alphanumeric input string.
 */
public abstract class WindowObjectListWithFilter extends WindowObjectList {

  private final JTextField filter_string;

  /**
   * Creates a new instance of ObjectListWindowWithFilter
   */
  public WindowObjectListWithFilter(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    JPanel input_panel = new JPanel();
    this.south_panel.add(input_panel, BorderLayout.SOUTH);

    JLabel filter_label = new JLabel(tm.getText("filter"));
    input_panel.add(filter_label, BorderLayout.WEST);

    this.filter_string = new JTextField(10);
    this.filter_string.setText("");
    input_panel.add(filter_string, BorderLayout.EAST);
  }

  /**
   * Adds p_object to the list only if its name matches the filter.
   */
  @Override
  protected void add_to_list(Object p_object) {
    String curr_filter_string = this.filter_string.getText().trim();
    boolean object_matches;
    if (curr_filter_string.isEmpty()) {
      object_matches = true;
    } else {
      object_matches = p_object.toString().contains(curr_filter_string);
    }
    if (object_matches) {
      super.add_to_list(p_object);
    }
  }

  /**
   * Returns the filter text string of this window.
   */

  /**
   * Saves also the filter string to disk.
   */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    try {
      p_object_stream.writeObject(filter_string.getText());
    } catch (IOException _) {
      FRLogger.warn("WindowObjectListWithFilter.save: save failed");
    }
    super.save(p_object_stream);
  }

  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    try {
      String curr_string = (String) p_object_stream.readObject();
      this.filter_string.setText(curr_string);
    } catch (Exception _) {
      FRLogger.warn("WindowObjectListWithFilter.read: read failed");
    }
    return super.read(p_object_stream);
  }

}