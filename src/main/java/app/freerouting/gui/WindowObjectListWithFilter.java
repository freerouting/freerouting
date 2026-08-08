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
 * Abstract class for windows displaying a list of objects The object name can be filtered by an
 * alphanumeric input string.
 */
public abstract class WindowObjectListWithFilter extends WindowObjectList {

  protected final JPanel inputPanel;
  private final JTextField filterString;

  /** Creates a new instance of ObjectListWindowWithFilter */
  protected WindowObjectListWithFilter(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.inputPanel = new JPanel();
    this.southPanel.add(inputPanel, BorderLayout.SOUTH);

    JLabel filterLabel = new JLabel(tm.getText("filter"));
    inputPanel.add(filterLabel, BorderLayout.WEST);

    this.filterString = new JTextField(10);
    this.filterString.setText("");
    inputPanel.add(filterString, BorderLayout.EAST);

    this.filterString
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              @Override
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                recalculate();
              }

              @Override
              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                recalculate();
              }

              @Override
              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                recalculate();
              }
            });
  }

  /** Adds p_object to the list only if its name matches the filter. */
  @Override
  protected void addToList(Object p_object) {
    String currFilterString = this.filterString.getText().trim();
    boolean objectMatches;
    if (currFilterString.isEmpty()) {
      objectMatches = true;
    } else {
      objectMatches = p_object.toString().toLowerCase().contains(currFilterString.toLowerCase());
    }
    if (objectMatches) {
      super.addToList(p_object);
    }
  }

  /** Returns the filter text string of this window. */

  /** Saves also the filter string to disk. */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    try {
      p_object_stream.writeObject(filterString.getText());
    } catch (IOException _) {
      FRLogger.warn("WindowObjectListWithFilter.save: save failed");
    }
    super.save(p_object_stream);
  }

  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    try {
      String currString = (String) p_object_stream.readObject();
      this.filterString.setText(currString);
    } catch (Exception _) {
      FRLogger.warn("WindowObjectListWithFilter.read: read failed");
    }
    return super.read(p_object_stream);
  }
}
