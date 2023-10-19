package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.logger.FRLogger;

import app.freerouting.management.FRAnalytics;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

/** Abstract class for windows displaying a list of objects */
public abstract class WindowObjectList extends BoardSavableSubWindow {

  protected static final int DEFAULT_TABLE_SIZE = 20;
  protected final BoardFrame board_frame;
  protected final JPanel south_panel;
  /** The subwindows with information about selected object */
  protected final Collection<WindowObjectInfo> subwindows =
      new LinkedList<>();

  private final JPanel main_panel;
  private final ResourceBundle resources;
  protected JLabel list_empty_message;
  protected JList<Object> list;
  private JScrollPane list_scroll_pane;
  private DefaultListModel<Object> list_model;
  /** Creates a new instance of ObjectListWindow */
  public WindowObjectList(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowObjectList", p_board_frame.get_locale());

    // create main panel
    this.main_panel = new JPanel();
    main_panel.setLayout(new BorderLayout());
    this.add(main_panel);

    // create a panel for adding buttons
    this.south_panel = new JPanel();
    south_panel.setLayout(new BorderLayout());
    main_panel.add(south_panel, BorderLayout.SOUTH);

    JPanel button_panel = new JPanel();
    button_panel.setLayout(new BorderLayout());
    this.south_panel.add(button_panel, BorderLayout.CENTER);

    JPanel north_button_panel = new JPanel();
    button_panel.add(north_button_panel, BorderLayout.NORTH);

    JButton info_components_show_button = new JButton(resources.getString("info"));
    info_components_show_button.setToolTipText(resources.getString("info_tooltip"));
    ShowListener show_listener = new ShowListener();
    info_components_show_button.addActionListener(show_listener);
    info_components_show_button.addActionListener(evt -> FRAnalytics.buttonClicked("info_components_show_button", info_components_show_button.getText()));
    north_button_panel.add(info_components_show_button);

    JButton info_components_instance_button = new JButton(resources.getString("select"));
    info_components_instance_button.setToolTipText(resources.getString("select_tooltip"));
    SelectListener instance_listener = new SelectListener();
    info_components_instance_button.addActionListener(instance_listener);
    info_components_instance_button.addActionListener(evt -> FRAnalytics.buttonClicked("info_components_instance_button", info_components_instance_button.getText()));
    north_button_panel.add(info_components_instance_button);

    JPanel south_button_panel = new JPanel();
    button_panel.add(south_button_panel, BorderLayout.SOUTH);

    JButton info_components_invert_button = new JButton(resources.getString("invert"));
    info_components_invert_button.setToolTipText(resources.getString("invert_tooltip"));
    info_components_invert_button.addActionListener(new InvertListener());
    info_components_invert_button.addActionListener(evt -> FRAnalytics.buttonClicked("info_components_invert_button", info_components_invert_button.getText()));
    south_button_panel.add(info_components_invert_button);

    JButton info_components_recalculate_button =
        new JButton(resources.getString("recalculate"));
    info_components_recalculate_button.setToolTipText(resources.getString("recalculate_tooltip"));
    RecalculateListener recalculate_listener = new RecalculateListener();
    info_components_recalculate_button.addActionListener(recalculate_listener);
    info_components_recalculate_button.addActionListener(evt -> FRAnalytics.buttonClicked("info_components_recalculate_button", info_components_recalculate_button.getText()));
    south_button_panel.add(info_components_recalculate_button);

    this.list_empty_message = new JLabel(resources.getString("list_empty"));
    this.list_empty_message.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Dispose this window and all subwindows when closing the window.
    this.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent evt) {
            dispose();
          }
        });
  }

  @Override
  public void setVisible(boolean p_value) {
    if (p_value) {
      recalculate();
    }
    super.setVisible(p_value);
  }

  protected void recalculate() {
    if (this.list_scroll_pane != null) {
      main_panel.remove(this.list_scroll_pane);
    }
    main_panel.remove(this.list_empty_message);
    // Create display list
    this.list_model = new DefaultListModel<>();
    this.list = new JList<>(this.list_model);
    this.list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    this.fill_list();
    if (this.list.getVisibleRowCount() > 0) {
      list_scroll_pane = new JScrollPane(this.list);
      main_panel.add(list_scroll_pane, BorderLayout.CENTER);
    } else {
      main_panel.add(list_empty_message, BorderLayout.CENTER);
    }
    this.pack();

    this.list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() > 1) {
              select_instances();
            }
          }
        });
  }

  @Override
  public void dispose() {
    for (WindowObjectInfo curr_subwindow : this.subwindows) {
      if (curr_subwindow != null) {
        curr_subwindow.dispose();
      }
    }
    super.dispose();
  }

  protected void add_to_list(Object p_object) {
    this.list_model.addElement(p_object);
  }

  /** Fills the list with the objects to display. */
  protected abstract void fill_list();

  protected abstract void select_instances();

  /** Saves also the filter string to disk. */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    int[] selected_indices;
    if (this.list != null) {
      selected_indices = this.list.getSelectedIndices();
    } else {
      selected_indices = new int[0];
    }
    try {
      p_object_stream.writeObject(selected_indices);
    } catch (IOException e) {
      FRLogger.error("WindowObjectList.save: save failed", e);
    }
    super.save(p_object_stream);
  }

  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    int[] saved_selected_indices;
    try {
      saved_selected_indices = (int[]) p_object_stream.readObject();
    } catch (Exception e) {
      FRLogger.error("WindowObjectListWithFilter.read: read failed", e);
      return false;
    }
    boolean result = super.read(p_object_stream);
    if (this.list != null && saved_selected_indices.length > 0) {
      this.list.setSelectedIndices(saved_selected_indices);
    }
    return result;
  }

  /** Listens to the button for showing the selected padstacks */
  private class ShowListener implements ActionListener {
    private static final int WINDOW_OFFSET = 30;

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      List<Object> selected_objects = list.getSelectedValuesList();
      if (selected_objects.isEmpty()) {
        return;
      }
      Collection<WindowObjectInfo.Printable> object_list =
          new LinkedList<>();
      for (int i = 0; i < selected_objects.size(); ++i) {
        object_list.add((WindowObjectInfo.Printable) (selected_objects.get(i)));
      }
      CoordinateTransform coordinate_transform =
          board_frame.board_panel.board_handling.coordinate_transform;
      WindowObjectInfo new_window =
          WindowObjectInfo.display(
              resources.getString("window_title"), object_list, board_frame, coordinate_transform);
      Point loc = getLocation();
      Point new_window_location =
          new Point(
              (int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      new_window.setLocation(new_window_location);
      subwindows.add(new_window);
    }
  }

  /** Listens to the button for showing the selected incompletes */
  private class SelectListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      select_instances();
    }
  }

  /** Listens to the button for inverting the selection */
  private class InvertListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (list_model == null) {
        return;
      }
      int[] new_selected_indices = new int[list_model.getSize() - list.getSelectedIndices().length];
      int curr_index = 0;
      for (int i = 0; i < list_model.getSize(); ++i) {
        if (!list.isSelectedIndex(i)) {
          new_selected_indices[curr_index] = i;
          ++curr_index;
        }
      }
      list.setSelectedIndices(new_selected_indices);
    }
  }

  /** Listens to the button for recalculating the content of the window */
  private class RecalculateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent p_evt) {
      recalculate();
    }
  }
}
