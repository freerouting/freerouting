package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.board.PrintableShape;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.RatsNest;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.NetClasses;
import app.freerouting.rules.Nets;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

public class WindowNets extends WindowObjectListWithFilter {

  private final JLabel net_count_label;
  private final JCheckBox filter_incompletes_checkbox;
  private final NetInfoTextPane info_pane;

  /**
   * Creates a new instance of NetsWindow
   */
  public WindowNets(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("title"));

    // Net count and explanation label at the top
    this.net_count_label = new JLabel();
    this.net_count_label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.add(this.net_count_label, BorderLayout.NORTH);

    JPanel filterControlPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
    if (this.input_panel != null) {
      this.south_panel.remove(this.input_panel);
      filterControlPanel.add(this.input_panel);
    }

    // Filter incompletes checkbox instead of button
    this.filter_incompletes_checkbox = new JCheckBox(tm.getText("filter_incompletes"));
    this.filter_incompletes_checkbox.setToolTipText(tm.getText("filter_incompletes_tooltip"));
    this.filter_incompletes_checkbox.addActionListener(_ -> recalculate());
    filterControlPanel.add(this.filter_incompletes_checkbox);

    headerPanel.add(filterControlPanel, BorderLayout.CENTER);
    this.main_panel.add(headerPanel, BorderLayout.NORTH);

    // Selected Net Info Pane
    this.info_pane = new NetInfoTextPane();
    JScrollPane infoScrollPane = new JScrollPane(this.info_pane);
    infoScrollPane.setPreferredSize(new Dimension(150, 80));
    this.center_panel.add(infoScrollPane, BorderLayout.SOUTH);

    JPanel curr_button_panel = new JPanel();
    this.south_panel.add(curr_button_panel, BorderLayout.NORTH);

    final JButton rules_nets_assign_class_button = new JButton(tm.getText("assign_class"));
    curr_button_panel.add(rules_nets_assign_class_button);
    rules_nets_assign_class_button.setToolTipText(tm.getText("assign_class_tooltip"));
    rules_nets_assign_class_button.addActionListener(new AssignClassListener());
    rules_nets_assign_class_button.addActionListener(_ -> FRAnalytics.buttonClicked("rules_nets_assign_class_button", rules_nets_assign_class_button.getText()));
  }

  @Override
  protected boolean showInfoButton() {
    return false;
  }

  @Override
  protected boolean showSelectButton() {
    return false;
  }

  @Override
  protected boolean showInvertButton() {
    return false;
  }

  @Override
  protected boolean showRecalculateButton() {
    return false;
  }

  @Override
  protected void add_to_list(Object p_object) {
    if (p_object instanceof Net net) {
      if (this.filter_incompletes_checkbox.isSelected()) {
        RatsNest ratsnest = board_frame.board_panel.board_handling.get_ratsnest();
        if (ratsnest.incomplete_count(net.net_number) == 0) {
          return;
        }
      }
    }
    super.add_to_list(p_object);
  }

  @Override
  protected void recalculate() {
    super.recalculate();

    if (this.list != null) {
      // Set custom cell renderer to show Net ID, name, and currently assigned class
      this.list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (value instanceof Net net) {
            setText("Net #" + net.net_number + " (" + net.name + ") - Class: " + net.get_class().get_name());
          }
          return c;
        }
      });

      // Clear/remove old listeners to avoid multiple registrations
      for (java.awt.event.ContainerListener cl : this.list.getContainerListeners()) {
        this.list.removeContainerListener(cl);
      }

      this.list.addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          update_selected_net_info();
        }
      });

      update_selected_net_info();
    }
  }

  private void update_selected_net_info() {
    if (this.info_pane == null) {
      return;
    }
    this.info_pane.setText("");
    List<Object> selected_nets = this.list.getSelectedValuesList();
    if (selected_nets == null || selected_nets.isEmpty()) {
      return;
    }
    for (Object obj : selected_nets) {
      if (obj instanceof Net net) {
        net.print_info(this.info_pane, board_frame.get_locale());
      }
    }
    this.info_pane.setCaretPosition(0);
  }

  /**
   * Fills the list with the nets in the net list.
   */
  @Override
  protected void fill_list() {
    Nets nets = this.board_frame.board_panel.board_handling.get_routing_board().rules.nets;
    List<Net> net_list = new java.util.ArrayList<>();
    for (int i = 0; i < nets.max_net_no(); i++) {
      Net net = nets.get(i + 1);
      if (net != null) {
        net_list.add(net);
      }
    }
    net_list.sort(java.util.Comparator.comparingInt(n -> n.net_number));
    for (Net net : net_list) {
      this.add_to_list(net);
    }
    this.list.setVisibleRowCount(Math.min(net_list.size(), DEFAULT_TABLE_SIZE));

    if (this.net_count_label != null) {
      String explanation = tm.getText("net_explanation");
      String countSentence = tm.getText("net_count", String.valueOf(net_list.size()));
      this.net_count_label.setText("<html>" + explanation.replace("\n", "<br>") + "<b>" + countSentence + "</b></html>");
    }
  }

  @Override
  protected void select_instances() {
    List<Object> selected_nets = list.getSelectedValuesList();
    if (selected_nets.isEmpty()) {
      return;
    }
    int[] selected_net_numbers = new int[selected_nets.size()];
    for (int i = 0; i < selected_nets.size(); i++) {
      selected_net_numbers[i] = ((Net) selected_nets.get(i)).net_number;
    }
    RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
    Set<Item> selected_items = new TreeSet<>();
    Collection<Item> board_items = routing_board.get_items();
    for (Item curr_item : board_items) {
      boolean item_matches = false;
      for (int curr_net_no : selected_net_numbers) {
        if (curr_item.contains_net(curr_net_no)) {
          item_matches = true;
          break;
        }
      }
      if (item_matches) {
        selected_items.add(curr_item);
      }
    }
    board_frame.board_panel.board_handling.select_items(selected_items);
    board_frame.board_panel.board_handling.zoom_selection();
  }

  private class AssignClassListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      List<Object> selected_nets = list.getSelectedValuesList();
      if (selected_nets.isEmpty()) {
        return;
      }
      NetClasses net_classes = board_frame.board_panel.board_handling.get_routing_board().rules.net_classes;
      NetClass[] class_arr = new NetClass[net_classes.count()];
      for (int i = 0; i < class_arr.length; i++) {
        class_arr[i] = net_classes.get(i);
      }
      Object selected_value = JOptionPane.showInputDialog(null, tm.getText("message_1"), tm.getText("message_2"), JOptionPane.INFORMATION_MESSAGE, null, class_arr, class_arr[0]);
      if (!(selected_value instanceof NetClass selected_class)) {
        return;
      }
      for (int i = 0; i < selected_nets.size(); i++) {
        ((Net) selected_nets.get(i)).set_class(selected_class);
      }
      board_frame.refresh_windows();
    }
  }

  private class NetInfoTextPane extends JTextPane implements ObjectInfoPanel {
    private final NumberFormat number_format;

    public NetInfoTextPane() {
      this.setEditable(false);
      this.number_format = NumberFormat.getInstance(board_frame.get_locale());
      this.number_format.setMaximumFractionDigits(4);

      StyledDocument document = this.getStyledDocument();
      Style default_style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
      document.addStyle("normal", default_style);
      Style bold_style = document.addStyle("bold", default_style);
      StyleConstants.setBold(bold_style, true);
    }

    private boolean append(String p_string, String p_style) {
      StyledDocument document = this.getStyledDocument();
      try {
        document.insertString(document.getLength(), p_string, document.getStyle(p_style));
      } catch (BadLocationException _) {
        return false;
      }
      return true;
    }

    @Override
    public boolean append(String p_string) {
      return append(p_string, "normal");
    }

    @Override
    public boolean append_bold(String p_string) {
      return append(p_string, "bold");
    }

    @Override
    public boolean append(double p_value) {
      CoordinateTransform coordinate_transform = board_frame.board_panel.board_handling.coordinate_transform;
      Float value = (float) coordinate_transform.board_to_user(p_value);
      return append(number_format.format(value));
    }

    @Override
    public boolean append_without_transforming(double p_value) {
      Float value = (float) p_value;
      return append(number_format.format(value));
    }

    @Override
    public boolean append(FloatPoint p_point) {
      CoordinateTransform coordinate_transform = board_frame.board_panel.board_handling.coordinate_transform;
      FloatPoint transformed_point = coordinate_transform.board_to_user(p_point);
      return append(transformed_point.to_string(board_frame.get_locale()));
    }

    @Override
    public boolean append(Shape p_shape, Locale p_locale) {
      CoordinateTransform coordinate_transform = board_frame.board_panel.board_handling.coordinate_transform;
      PrintableShape transformed_shape = coordinate_transform.board_to_user(p_shape, p_locale);
      if (transformed_shape == null) {
        return false;
      }
      return append(transformed_shape.toString());
    }

    @Override
    public boolean newline() {
      return append("\n");
    }

    @Override
    public boolean indent() {
      return append("       ");
    }

    @Override
    public boolean append(String p_button_name, String p_window_title, ObjectInfoPanel.Printable p_object) {
      Collection<ObjectInfoPanel.Printable> object_list = new LinkedList<>();
      object_list.add(p_object);
      return append_objects(p_button_name, p_window_title, object_list);
    }

    @Override
    public boolean append_items(String p_button_name, String p_window_title, Collection<Item> p_items) {
      Collection<ObjectInfoPanel.Printable> object_list = new LinkedList<>(p_items);
      return append_objects(p_button_name, p_window_title, object_list);
    }

    @Override
    public boolean append_objects(String p_button_name, String p_window_title, Collection<ObjectInfoPanel.Printable> p_objects) {
      JButton object_info_button = new JButton();
      object_info_button.setText(p_button_name);
      object_info_button.setBorderPainted(false);
      object_info_button.setContentAreaFilled(false);
      object_info_button.setMargin(new Insets(0, 0, 0, 0));
      object_info_button.setAlignmentY(0.75f);
      object_info_button.setForeground(Color.blue);

      object_info_button.addActionListener(e -> {
        Collection<WindowObjectInfo.Printable> info_objects = new LinkedList<>();
        for (ObjectInfoPanel.Printable p : p_objects) {
          if (p instanceof WindowObjectInfo.Printable wp) {
            info_objects.add(wp);
          }
        }
        CoordinateTransform coordinate_transform = board_frame.board_panel.board_handling.coordinate_transform;
        WindowObjectInfo new_window = WindowObjectInfo.display(p_window_title, info_objects, board_frame, coordinate_transform);
        Point loc = getLocation();
        Point new_window_location = new Point((int) (loc.getX() + 30), (int) (loc.getY() + 30));
        new_window.setLocation(new_window_location);
        subwindows.add(new_window);
      });
      object_info_button.addActionListener(_ -> FRAnalytics.buttonClicked("object_info_button", object_info_button.getText()));

      StyledDocument document = this.getStyledDocument();
      Style default_style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
      Style button_style = document.addStyle(p_button_name, default_style);
      StyleConstants.setAlignment(button_style, StyleConstants.ALIGN_CENTER);
      StyleConstants.setComponent(button_style, object_info_button);

      try {
        document.insertString(document.getLength(), p_button_name, button_style);
      } catch (BadLocationException _) {
        return false;
      }
      return true;
    }
  }
}