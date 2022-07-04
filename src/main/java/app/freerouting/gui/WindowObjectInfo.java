package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import java.util.Collection;

/**
 * Window displaying text information for a list of objects implementing the
 * ObjectInfoWindow.Printable interface.
 */
public class WindowObjectInfo extends BoardTemporarySubWindow
    implements app.freerouting.board.ObjectInfoPanel {
  private static final int MAX_WINDOW_HEIGHT = 500;
  private static final int SCROLLBAR_ADD = 30;
  private final javax.swing.JTextPane text_pane;
  private final app.freerouting.board.CoordinateTransform coordinate_transform;
  private final java.util.ResourceBundle resources;
  private final java.text.NumberFormat number_format;
  /**
   * The new created windows by pushing buttons inside this window. Used when closing this window to
   * close also all subwindows.
   */
  private final Collection<WindowObjectInfo> subwindows = new java.util.LinkedList<WindowObjectInfo>();

  /** Creates a new instance of ItemInfoWindow */
  private WindowObjectInfo(
      BoardFrame p_board_frame, app.freerouting.board.CoordinateTransform p_coordinate_transform) {
    super(p_board_frame);
    this.resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.WindowObjectInfo", p_board_frame.get_locale());
    this.coordinate_transform = p_coordinate_transform;

    // create the text pane
    this.text_pane = new javax.swing.JTextPane();
    this.text_pane.setEditable(false);
    this.number_format = java.text.NumberFormat.getInstance(p_board_frame.get_locale());
    this.number_format.setMaximumFractionDigits(4);

    // set document and text styles
    javax.swing.text.StyledDocument document = this.text_pane.getStyledDocument();

    javax.swing.text.Style default_style =
        javax.swing.text.StyleContext.getDefaultStyleContext()
            .getStyle(javax.swing.text.StyleContext.DEFAULT_STYLE);

    // add bold style to the document
    javax.swing.text.Style bold_style = document.addStyle("bold", default_style);
    javax.swing.text.StyleConstants.setBold(bold_style, true);

    // Create a scoll_pane arount the text_pane and insert it into this window.
    javax.swing.JScrollPane scroll_pane = new javax.swing.JScrollPane(this.text_pane);
    this.add(scroll_pane);

    // Dispose this window and all subwindows when closing the window.
    this.addWindowListener(
        new java.awt.event.WindowAdapter() {
          public void windowClosing(java.awt.event.WindowEvent evt) {
            dispose();
          }
        });
  }

  /**
   * Displays a new ObjectInfoWindow with information about the items in p_item_list.
   * p_coordinate_transform is for transforming board to user coordinates, and p_location is the
   * location of the window.
   */
  public static void display(
      Collection<app.freerouting.board.Item> p_item_list,
      BoardFrame p_board_frame,
      app.freerouting.board.CoordinateTransform p_coordinate_transform,
      java.awt.Point p_location) {
    WindowObjectInfo new_instance = new WindowObjectInfo(p_board_frame, p_coordinate_transform);
    new_instance.setTitle(new_instance.resources.getString("title"));
    Integer pin_count = 0;
    Integer via_count = 0;
    Integer trace_count = 0;
    double cumulative_trace_length = 0;
    for (WindowObjectInfo.Printable curr_object : p_item_list) {
      curr_object.print_info(new_instance, p_board_frame.get_locale());
      if (curr_object instanceof app.freerouting.board.Pin) {
        ++pin_count;
      } else if (curr_object instanceof app.freerouting.board.Via) {
        ++via_count;
      } else if (curr_object instanceof app.freerouting.board.Trace) {
        ++trace_count;
        cumulative_trace_length += ((app.freerouting.board.Trace) curr_object).get_length();
      }
    }
    new_instance.append_bold(new_instance.resources.getString("summary") + " ");
    java.text.NumberFormat number_format =
        java.text.NumberFormat.getInstance(p_board_frame.get_locale());
    if (pin_count > 0) {
      new_instance.append(number_format.format(pin_count));
      if (pin_count == 1) {
        new_instance.append(" " + new_instance.resources.getString("pin"));
      } else {
        new_instance.append(" " + new_instance.resources.getString("pins"));
      }
      if (via_count + trace_count > 0) {
        new_instance.append(", ");
      }
    }
    if (via_count > 0) {
      new_instance.append(number_format.format(via_count));
      if (via_count == 1) {
        new_instance.append(" " + new_instance.resources.getString("via"));
      } else {
        new_instance.append(" " + new_instance.resources.getString("vias"));
      }
      if (trace_count > 0) {
        new_instance.append(", ");
      }
    }
    if (trace_count > 0) {
      new_instance.append(number_format.format(trace_count));
      if (trace_count == 1) {
        new_instance.append(" " + new_instance.resources.getString("trace") + " ");
      } else {
        new_instance.append(" " + new_instance.resources.getString("traces") + " ");
      }
      new_instance.append(cumulative_trace_length);
    }

    new_instance.pack();
    java.awt.Dimension size = new_instance.getSize();
    // make the window smaller, if its heicht gets bigger than MAX_WINDOW_HEIGHT
    if (size.getHeight() > MAX_WINDOW_HEIGHT) {
      new_instance.setPreferredSize(
          new java.awt.Dimension((int) size.getWidth() + SCROLLBAR_ADD, MAX_WINDOW_HEIGHT));
      new_instance.pack();
    }
    new_instance.setLocation(p_location);
    new_instance.setVisible(true);
  }

  /**
   * Displays a new ObjectInfoWindow with information about the objects in p_object_list.
   * p_coordinate_transform is for transforming board to user coordinates, and p_location is the
   * location of the window.
   */
  public static WindowObjectInfo display(
      String p_title,
      Collection<Printable> p_object_list,
      BoardFrame p_board_frame,
      app.freerouting.board.CoordinateTransform p_coordinate_transform) {
    WindowObjectInfo new_window = new WindowObjectInfo(p_board_frame, p_coordinate_transform);
    new_window.setTitle(p_title);
    if (p_object_list.isEmpty()) {
      new_window.append(new_window.resources.getString("list_empty"));
    }
    for (Printable curr_object : p_object_list) {
      curr_object.print_info(new_window, p_board_frame.get_locale());
    }
    new_window.pack();
    java.awt.Dimension size = new_window.getSize();
    // make the window smaller, if its heicht gets bigger than MAX_WINDOW_HEIGHT
    if (size.getHeight() > MAX_WINDOW_HEIGHT) {
      new_window.setPreferredSize(
          new java.awt.Dimension((int) size.getWidth() + SCROLLBAR_ADD, MAX_WINDOW_HEIGHT));
      new_window.pack();
    }
    new_window.setVisible(true);
    return new_window;
  }

  /** Appends p_string to the text pane. Returns false, if that was not possible. */
  private boolean append(String p_string, String p_style) {

    javax.swing.text.StyledDocument document = text_pane.getStyledDocument();
    try {
      document.insertString(document.getLength(), p_string, document.getStyle(p_style));
    } catch (javax.swing.text.BadLocationException e) {
      FRLogger.warn("ObjectInfoWindow.append: unable to insert text into text pane.");
      return false;
    }
    return true;
  }

  /** Appends p_string to the text pane. Returns false, if that was not possible. */
  public boolean append(String p_string) {
    return append(p_string, "normal");
  }

  /** Appends p_string in bold styleto the text pane. Returns false, if that was not possible. */
  public boolean append_bold(String p_string) {
    return append(p_string, "bold");
  }

  /**
   * Appends p_value to the text pane after transforming it to the user coordinate sytem. Returns
   * false, if that was not possible.
   */
  public boolean append(double p_value) {
    Float value = (float) this.coordinate_transform.board_to_user(p_value);
    return append(number_format.format(value));
  }

  /**
   * Appends p_value to the text pane without transforming it to the user coordinate sytem. Returns
   * false, if that was not possible.
   */
  public boolean append_without_transforming(double p_value) {
    Float value = (float) p_value;
    return append(number_format.format(value));
  }

  /**
   * Appends p_point to the text pane after transforming to the user coordinate sytem. Returns
   * false, if that was not possible.
   */
  public boolean append(app.freerouting.geometry.planar.FloatPoint p_point) {
    app.freerouting.geometry.planar.FloatPoint transformed_point =
        this.coordinate_transform.board_to_user(p_point);
    return append(transformed_point.to_string(board_frame.get_locale()));
  }

  /**
   * Appends p_shape to the text pane after transforming to the user coordinate sytem. Returns
   * false, if that was not possible.
   */
  public boolean append(app.freerouting.geometry.planar.Shape p_shape, java.util.Locale p_locale) {
    app.freerouting.board.PrintableShape transformed_shape =
        this.coordinate_transform.board_to_user(p_shape, p_locale);
    if (transformed_shape == null) {
      return false;
    }
    return append(transformed_shape.toString());
  }

  /** Begins a new line in the text pane. */
  public boolean newline() {
    return append("\n");
  }

  /** Appends a fixed number of spaces to the text pane. */
  public boolean indent() {
    return append("       ");
  }

  /**
   * Appends a button for creating a new ObjectInfoWindow with the information of p_object to the
   * text pane. Returns false, if that was not possible.
   */
  public boolean append(
      String p_button_name, String p_window_title, WindowObjectInfo.Printable p_object) {
    java.util.Collection<WindowObjectInfo.Printable> object_list =
        new java.util.LinkedList<WindowObjectInfo.Printable>();
    object_list.add(p_object);
    return append_objects(p_button_name, p_window_title, object_list);
  }

  /**
   * Appends a button for creating a new ObjectInfoWindow with the information of p_items to the
   * text pane. Returns false, if that was not possible.
   */
  public boolean append_items(
      String p_button_name,
      String p_window_title,
      java.util.Collection<app.freerouting.board.Item> p_items) {
    java.util.Collection<WindowObjectInfo.Printable> object_list =
        new java.util.LinkedList<WindowObjectInfo.Printable>();
    object_list.addAll(p_items);
    return append_objects(p_button_name, p_window_title, object_list);
  }

  /**
   * Appends a button for creating a new ObjectInfoWindow with the information of p_objects to the
   * text pane. Returns false, if that was not possible.
   */
  public boolean append_objects(
      String p_button_name,
      String p_window_title,
      java.util.Collection<WindowObjectInfo.Printable> p_objects) {
    // create a button without border and color.
    javax.swing.JButton button = new javax.swing.JButton();
    button.setText(p_button_name);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setMargin(new java.awt.Insets(0, 0, 0, 0));
    button.setAlignmentY(0.75f);
    // Display the button name in blue.
    button.setForeground(java.awt.Color.blue);

    button.addActionListener(new InfoButtonListener(p_window_title, p_objects));

    // Add style for inserting the button  to the document.
    javax.swing.text.StyledDocument document = this.text_pane.getStyledDocument();
    javax.swing.text.Style default_style =
        javax.swing.text.StyleContext.getDefaultStyleContext()
            .getStyle(javax.swing.text.StyleContext.DEFAULT_STYLE);
    javax.swing.text.Style button_style = document.addStyle(p_button_name, default_style);
    javax.swing.text.StyleConstants.setAlignment(
        button_style, javax.swing.text.StyleConstants.ALIGN_CENTER);
    javax.swing.text.StyleConstants.setComponent(button_style, button);

    // Add the button to the document.
    try {
      document.insertString(document.getLength(), p_button_name, button_style);
    } catch (javax.swing.text.BadLocationException e) {
      System.err.println("ObjectInfoWindow.append: unable to insert text into text pane.");
      return false;
    }
    return true;
  }

  public void dispose() {
    for (WindowObjectInfo curr_subwindow : this.subwindows) {
      if (curr_subwindow != null) {
        curr_subwindow.dispose();
      }
    }
    super.dispose();
  }

  private class InfoButtonListener implements java.awt.event.ActionListener {
    private static final int WINDOW_OFFSET = 30;
    /** The title of this window */
    private final String title;
    /** The objects, for which information is displayed in tne new window */
    private final Collection<Printable> objects;

    public InfoButtonListener(String p_title, java.util.Collection<Printable> p_objects) {
      this.title = p_title;
      this.objects = p_objects;
    }

    public void actionPerformed(java.awt.event.ActionEvent p_evt) {
      WindowObjectInfo new_window =
          display(this.title, this.objects, board_frame, coordinate_transform);

      java.awt.Point loc = getLocation();
      java.awt.Point new_window_location =
          new java.awt.Point(
              (int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      new_window.setLocation(new_window_location);
      subwindows.add(new_window);
    }
  }
}
