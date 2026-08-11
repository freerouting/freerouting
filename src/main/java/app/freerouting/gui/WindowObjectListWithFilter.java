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

  /** Creates a new instance of ObjectListWindowWithFilter. */
  protected WindowObjectListWithFilter(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

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
  protected void addToList(Object object) {
    String currFilterString = this.filterString.getText().trim();
    boolean objectMatches;
    if (currFilterString.isEmpty()) {
      objectMatches = true;
    } else {
      objectMatches = object.toString().toLowerCase().contains(currFilterString.toLowerCase());
    }
    if (objectMatches) {
      super.addToList(object);
    }
  }

  /** Saves also the filter string to disk. */
  @Override
  public void save(ObjectOutputStream objectStream) {
    try {
      objectStream.writeObject(filterString.getText());
    } catch (IOException _) {
      FRLogger.warn("WindowObjectListWithFilter.save: save failed");
    }
    super.save(objectStream);
  }

  @Override
  public boolean read(ObjectInputStream objectStream) {
    try {
      String currString = (String) objectStream.readObject();
      this.filterString.setText(currString);
    } catch (Exception _) {
      FRLogger.warn("WindowObjectListWithFilter.read: read failed");
    }
    return super.read(objectStream);
  }
}
