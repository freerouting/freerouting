package app.freerouting.gui;

import app.freerouting.logger.FRLogger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ResourceBundle;

/**
 * Abstract class for windows displaying a list of objects The object name can be filtered by an
 * alphanumeric input string.
 */
public abstract class WindowObjectListWithFilter extends WindowObjectList {

  private final JTextField filter_string;

  /** Creates a new instance of ObjectListWindowWithFilter */
  public WindowObjectListWithFilter(BoardFrame p_board_frame) {
    super(p_board_frame);
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowObjectList", p_board_frame.get_locale());
    JPanel input_panel = new JPanel();
    this.south_panel.add(input_panel, BorderLayout.SOUTH);

    JLabel filter_label = new JLabel(resources.getString("filter"));
    input_panel.add(filter_label, BorderLayout.WEST);

    this.filter_string = new JTextField(10);
    this.filter_string.setText("");
    input_panel.add(filter_string, BorderLayout.EAST);
  }

  /** Adds p_object to the list only if its name matches the filter. */
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

  /** Returns the filter text string of this window. */
  public SnapshotInfo get_snapshot_info() {
    int[] selected_indices;
    if (this.list != null) {
      selected_indices = this.list.getSelectedIndices();
    } else {
      selected_indices = new int[0];
    }
    return new SnapshotInfo(filter_string.getText(), selected_indices);
  }

  public void set_snapshot_info(SnapshotInfo p_snapshot_info) {
    if (!p_snapshot_info.filter.equals(this.filter_string.getText())) {
      this.filter_string.setText(p_snapshot_info.filter);
      this.recalculate();
    }
    if (this.list != null && p_snapshot_info.selected_indices.length > 0) {
      this.list.setSelectedIndices(p_snapshot_info.selected_indices);
    }
  }

  /** Saves also the filter string to disk. */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    try {
      p_object_stream.writeObject(filter_string.getText());
    } catch (IOException e) {
      FRLogger.warn("WindowObjectListWithFilter.save: save failed");
    }
    super.save(p_object_stream);
  }

  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    try {
      String curr_string = (String) p_object_stream.readObject();
      this.filter_string.setText(curr_string);
    } catch (Exception e) {
      FRLogger.warn("WindowObjectListWithFilter.read: read failed");
    }
    return super.read(p_object_stream);
  }

  /** Information to be stored in a SnapShot. */
  public static class SnapshotInfo implements Serializable {
    private final String filter;
    private final int[] selected_indices;
    private SnapshotInfo(String p_filter, int[] p_selected_indices) {
      filter = p_filter;
      selected_indices = p_selected_indices;
    }
  }
}
