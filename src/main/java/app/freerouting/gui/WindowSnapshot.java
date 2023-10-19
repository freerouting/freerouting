package app.freerouting.gui;

import app.freerouting.boardgraphics.CoordinateTransform;
import app.freerouting.boardgraphics.ItemColorTableModel;
import app.freerouting.boardgraphics.OtherColorTableModel;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.interactive.SnapShot;
import app.freerouting.logger.FRLogger;

import app.freerouting.management.FRAnalytics;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ResourceBundle;

/** Window handling snapshots of the interactive situation. */
public class WindowSnapshot extends BoardSavableSubWindow {

  final WindowSnapshotSettings settings_window;
  private final BoardFrame board_frame;
  private final JList<SnapShot> list;
  private final JTextField name_field;
  private final ResourceBundle resources;
  private DefaultListModel<SnapShot> list_model =
      new DefaultListModel<>();
  private int snapshot_count = 0;

  /** Creates a new instance of SnapshotFrame */
  public WindowSnapshot(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;
    this.settings_window = new WindowSnapshotSettings(p_board_frame);
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowSnapshot", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));

    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    // create main panel
    final JPanel main_panel = new JPanel();
    getContentPane().add(main_panel);
    main_panel.setLayout(new BorderLayout());

    // create goto button
    JButton other_snapshots_goto_button = new JButton(resources.getString("goto_snapshot"));
    other_snapshots_goto_button.setToolTipText(resources.getString("goto_tooltip"));
    GotoListener goto_listener = new GotoListener();
    other_snapshots_goto_button.addActionListener(goto_listener);
    other_snapshots_goto_button.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_goto_button", other_snapshots_goto_button.getText()));
    main_panel.add(other_snapshots_goto_button, BorderLayout.NORTH);

    // create snapshot list
    this.list = new JList<>(this.list_model);
    this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.list.setSelectedIndex(0);
    this.list.setVisibleRowCount(5);
    this.list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() > 1) {
              goto_selected();
            }
          }
        });

    JScrollPane list_scroll_pane = new JScrollPane(this.list);
    list_scroll_pane.setPreferredSize(new Dimension(200, 100));
    main_panel.add(list_scroll_pane, BorderLayout.CENTER);

    // create the south panel
    final JPanel south_panel = new JPanel();
    main_panel.add(south_panel, BorderLayout.SOUTH);
    GridBagLayout gridbag = new GridBagLayout();
    south_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;

    // create panel to add a new snapshot
    final JPanel add_panel = new JPanel();
    gridbag.setConstraints(add_panel, gridbag_constraints);
    add_panel.setLayout(new BorderLayout());
    south_panel.add(add_panel);

    JButton other_snapshots_add_button = new JButton(resources.getString("create"));
    AddListener add_listener = new AddListener();
    other_snapshots_add_button.addActionListener(add_listener);
    other_snapshots_add_button.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_add_button", other_snapshots_add_button.getText()));
    add_panel.add(other_snapshots_add_button, BorderLayout.WEST);

    this.name_field = new JTextField(10);
    name_field.setText(resources.getString("snapshot") + " 1");
    add_panel.add(name_field, BorderLayout.EAST);

    // create delete buttons
    JButton other_snapshots_delete_button = new JButton(resources.getString("remove"));
    DeleteListener delete_listener = new DeleteListener();
    other_snapshots_delete_button.addActionListener(delete_listener);
    other_snapshots_delete_button.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_delete_button", other_snapshots_delete_button.getText()));
    gridbag.setConstraints(other_snapshots_delete_button, gridbag_constraints);
    south_panel.add(other_snapshots_delete_button);

    JButton other_snapshots_delete_all_button =
        new JButton(resources.getString("remove_all"));
    DeleteAllListener delete_all_listener = new DeleteAllListener();
    other_snapshots_delete_all_button.addActionListener(delete_all_listener);
    other_snapshots_delete_all_button.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_delete_all_button", other_snapshots_delete_all_button.getText()));
    gridbag.setConstraints(other_snapshots_delete_all_button, gridbag_constraints);
    south_panel.add(other_snapshots_delete_all_button);

    // create button for the snapshot settings
    JButton other_snapshots_settings_button = new JButton(resources.getString("settings"));
    other_snapshots_settings_button.setToolTipText(resources.getString("settings_tooltip"));
    SettingsListener settings_listener = new SettingsListener();
    other_snapshots_settings_button.addActionListener(settings_listener);
    other_snapshots_settings_button.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_settings_button", other_snapshots_settings_button.getText()));
    gridbag.setConstraints(other_snapshots_delete_all_button, gridbag_constraints);
    south_panel.add(other_snapshots_settings_button);

    p_board_frame.set_context_sensitive_help(this, "WindowSnapshots");

    this.pack();
  }

  @Override
  public void dispose() {
    settings_window.dispose();
    super.dispose();
  }

  @Override
  public void parent_iconified() {
    settings_window.parent_iconified();
    super.parent_iconified();
  }

  @Override
  public void parent_deiconified() {
    settings_window.parent_deiconified();
    super.parent_deiconified();
  }

  /** Reads the data of this frame from disk. Returns false, if the reading failed. */
  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    try {
      SavedAttributes saved_attributes = (SavedAttributes) p_object_stream.readObject();
      this.snapshot_count = saved_attributes.snapshot_count;
      this.list_model = saved_attributes.list_model;
      this.list.setModel(this.list_model);
      String next_default_name = "snapshot " + (snapshot_count + 1);
      this.name_field.setText(next_default_name);
      this.setLocation(saved_attributes.location);
      this.setVisible(saved_attributes.is_visible);
      this.settings_window.read(p_object_stream);
      return true;
    } catch (Exception e) {
      FRLogger.error("VisibilityFrame.read_attriutes: read failed", e);
      return false;
    }
  }

  /** Saves this frame to disk. */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    SavedAttributes saved_attributes =
        new SavedAttributes(
            this.list_model, this.snapshot_count, this.getLocation(), this.isVisible());
    try {
      p_object_stream.writeObject(saved_attributes);
    } catch (IOException e) {
      FRLogger.error("VisibilityFrame.save_attriutes: save failed", e);
    }
    this.settings_window.save(p_object_stream);
  }

  void goto_selected() {
    int index = list.getSelectedIndex();
    if (index >= 0 && list_model.getSize() > index) {
      BoardHandling board_handling =
          board_frame.board_panel.board_handling;
      SnapShot curr_snapshot = list_model.elementAt(index);

      curr_snapshot.go_to(board_handling);

      if (curr_snapshot.settings.get_snapshot_attributes().object_colors) {
        board_handling.graphics_context.item_color_table =
            new ItemColorTableModel(
                curr_snapshot.graphics_context.item_color_table);
        board_handling.graphics_context.other_color_table =
            new OtherColorTableModel(
                curr_snapshot.graphics_context.other_color_table);

        board_frame.color_manager.set_table_models(board_handling.graphics_context);
      }

      if (curr_snapshot.settings.get_snapshot_attributes().display_region) {
        Point viewport_position = curr_snapshot.copy_viewport_position();
        if (viewport_position != null) {
          board_handling.graphics_context.coordinate_transform =
              new CoordinateTransform(
                  curr_snapshot.graphics_context.coordinate_transform);
          Dimension panel_size = board_handling.graphics_context.get_panel_size();
          board_frame.board_panel.setSize(panel_size);
          board_frame.board_panel.setPreferredSize(panel_size);
          board_frame.board_panel.set_viewport_position(viewport_position);
        }
      }

      board_frame.refresh_windows();
      board_frame.hilight_selected_button();
      board_frame.setVisible(true);
      board_frame.repaint();
    }
  }

  /** Refreshes the displayed values in this window. */
  @Override
  public void refresh() {
    this.settings_window.refresh();
  }

  /**
   * Selects the item, which is previous to the current selected item in the list. The current
   * selected item is then no more selected.
   */
  public void select_previous_item() {
    if (!this.isVisible()) {
      return;
    }
    int selected_index = this.list.getSelectedIndex();
    if (selected_index <= 0) {
      return;
    }
    this.list.setSelectedIndex(selected_index - 1);
  }

  /**
   * Selects the item, which is next to the current selected item in the list. The current selected
   * item is then no more selected.
   */
  public void select_next_item() {
    if (!this.isVisible()) {
      return;
    }
    int selected_index = this.list.getSelectedIndex();
    if (selected_index < 0 || selected_index >= this.list_model.getSize() - 1) {
      return;
    }

    this.list.setSelectedIndex(selected_index + 1);
  }

  /** Type for attributes of this class, which are saved to an Objectstream. */
  private static class SavedAttributes implements Serializable {
    public final DefaultListModel<SnapShot> list_model;
    public final int snapshot_count;
    public final Point location;
    public final boolean is_visible;
    public SavedAttributes(
        DefaultListModel<SnapShot> p_list_model,
        int p_snapshot_count,
        Point p_location,
        boolean p_is_visible) {
      list_model = p_list_model;
      snapshot_count = p_snapshot_count;
      location = p_location;
      is_visible = p_is_visible;
    }
  }

  private class AddListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      SnapShot new_snapshot =
          SnapShot.get_instance(
              name_field.getText(), board_frame.board_panel.board_handling);
      if (new_snapshot != null) {
        ++snapshot_count;
        list_model.addElement(new_snapshot);
        String next_default_name =
            resources.getString("snapshot")
                + " "
                + (snapshot_count + 1);
        name_field.setText(next_default_name);
      }
    }
  }

  private class DeleteListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      Object selected_snapshot = list.getSelectedValue();
      if (selected_snapshot != null) {
        list_model.removeElement(selected_snapshot);
      }
    }
  }

  private class DeleteAllListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      list_model.removeAllElements();
    }
  }

  private class GotoListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      goto_selected();
    }
  }

  private class SettingsListener implements ActionListener {
    boolean first_time = true;

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (first_time) {
        Point location = getLocation();
        settings_window.setLocation((int) location.getX() + 200, (int) location.getY());
        first_time = false;
      }
      settings_window.setVisible(true);
    }
  }
}
