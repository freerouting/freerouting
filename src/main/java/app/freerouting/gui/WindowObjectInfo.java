package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.board.Pin;
import app.freerouting.board.PrintableShape;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/**
 * Window displaying text information for a list of objects implementing the
 * ObjectInfoWindow.Printable interface.
 */
public final class WindowObjectInfo extends BoardTemporarySubWindow implements ObjectInfoPanel {

  private static final int MAX_WINDOW_HEIGHT = 500;
  private static final int SCROLLBAR_ADD = 30;
  private final JTextPane textPane;
  private final CoordinateTransform coordinateTransform;
  private final NumberFormat numberFormat;

  /**
   * The new created windows by pushing buttons inside this window. Used when closing this window to
   * close also all subwindows.
   */
  private final Collection<WindowObjectInfo> subwindows = new LinkedList<>();

  /** Creates a new instance of ItemInfoWindow. */
  private WindowObjectInfo(BoardFrame boardFrame, CoordinateTransform coordinateTransform) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());
    this.coordinateTransform = coordinateTransform;

    // create the text pane
    this.textPane = new JTextPane();
    this.textPane.setEditable(false);
    this.numberFormat = NumberFormat.getInstance(boardFrame.get_locale());
    this.numberFormat.setMaximumFractionDigits(4);

    // set document and text styles
    StyledDocument document = this.textPane.getStyledDocument();

    Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

    // add bold style to the document
    Style boldStyle = document.addStyle("bold", defaultStyle);
    StyleConstants.setBold(boldStyle, true);

    // Create a scrollPane around the textPane and insert it into this window.
    JScrollPane scrollPane = new JScrollPane(this.textPane);
    this.add(scrollPane);

    // Dispose this window and all subwindows when closing the window.
    this.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent evt) {
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
      Collection<Item> itemList,
      BoardFrame boardFrame,
      CoordinateTransform coordinateTransform,
      Point location) {
    WindowObjectInfo newInstance = new WindowObjectInfo(boardFrame, coordinateTransform);
    newInstance.setTitle(newInstance.tm.getText("title"));
    Integer pinCount = 0;
    Integer viaCount = 0;
    Integer traceCount = 0;
    double cumulativeTraceLength = 0;
    for (WindowObjectInfo.Printable currObject : itemList) {
      currObject.printInfo(newInstance, boardFrame.get_locale());
      if (currObject instanceof Pin) {
        ++pinCount;
      } else if (currObject instanceof Via) {
        ++viaCount;
      } else if (currObject instanceof Trace trace) {
        ++traceCount;
        cumulativeTraceLength += trace.getLength();
      }
    }
    newInstance.appendBold(newInstance.tm.getText("summary") + " ");
    NumberFormat numberFormat = NumberFormat.getInstance(boardFrame.get_locale());
    if (pinCount > 0) {
      newInstance.append(numberFormat.format(pinCount));
      if (pinCount == 1) {
        newInstance.append(" " + newInstance.tm.getText("pin"));
      } else {
        newInstance.append(" " + newInstance.tm.getText("pins"));
      }
      if (viaCount + traceCount > 0) {
        newInstance.append(", ");
      }
    }
    if (viaCount > 0) {
      newInstance.append(numberFormat.format(viaCount));
      if (viaCount == 1) {
        newInstance.append(" " + newInstance.tm.getText("via"));
      } else {
        newInstance.append(" " + newInstance.tm.getText("vias"));
      }
      if (traceCount > 0) {
        newInstance.append(", ");
      }
    }
    if (traceCount > 0) {
      newInstance.append(numberFormat.format(traceCount));
      if (traceCount == 1) {
        newInstance.append(" " + newInstance.tm.getText("trace") + " ");
      } else {
        newInstance.append(" " + newInstance.tm.getText("traces") + " ");
      }
      newInstance.append(cumulativeTraceLength);
    }

    newInstance.pack();
    Dimension size = newInstance.getSize();
    // make the window smaller, if its height gets bigger than MAX_WINDOW_HEIGHT
    if (size.getHeight() > MAX_WINDOW_HEIGHT) {
      newInstance.setPreferredSize(
          new Dimension((int) size.getWidth() + SCROLLBAR_ADD, MAX_WINDOW_HEIGHT));
      newInstance.pack();
    }
    newInstance.setLocation(location);
    newInstance.setVisible(true);
  }

  /**
   * Displays a new ObjectInfoWindow with information about the objects in p_object_list.
   * p_coordinate_transform is for transforming board to user coordinates, and p_location is the
   * location of the window.
   */
  public static WindowObjectInfo display(
      String title,
      Collection<Printable> objectList,
      BoardFrame boardFrame,
      CoordinateTransform coordinateTransform) {
    WindowObjectInfo newWindow = new WindowObjectInfo(boardFrame, coordinateTransform);
    newWindow.setTitle(title);
    if (objectList.isEmpty()) {
      newWindow.append(newWindow.tm.getText("listEmpty"));
    }
    for (Printable currObject : objectList) {
      currObject.printInfo(newWindow, boardFrame.get_locale());
    }
    newWindow.pack();
    Dimension size = newWindow.getSize();
    // make the window smaller, if its height gets bigger than MAX_WINDOW_HEIGHT
    if (size.getHeight() > MAX_WINDOW_HEIGHT) {
      newWindow.setPreferredSize(
          new Dimension((int) size.getWidth() + SCROLLBAR_ADD, MAX_WINDOW_HEIGHT));
      newWindow.pack();
    }
    newWindow.setVisible(true);
    return newWindow;
  }

  /** Appends p_string to the text pane. Returns false, if that was not possible. */
  private boolean appendStyledText(String string, String style) {

    StyledDocument document = textPane.getStyledDocument();
    try {
      document.insertString(document.getLength(), string, document.getStyle(style));
    } catch (BadLocationException _) {
      FRLogger.warn("ObjectInfoWindow.append: unable to insert text into text pane.");
      return false;
    }
    return true;
  }

  /**
   * Appends a value in bold style to the text pane.
   *
   * @param string the text to append
   * @return whether the text was appended
   */
  @Override
  public boolean appendBold(String string) {
    return appendStyledText(string, "bold");
  }

  /**
   * Appends a value without transforming it to the user coordinate system.
   *
   * @param value the value to append
   * @return whether the value was appended
   */
  @Override
  public boolean appendWithoutTransforming(double value) {
    Float formattedValue = (float) value;
    return append(numberFormat.format(formattedValue));
  }

  /** Appends p_string to the text pane. Returns false, if that was not possible. */
  @Override
  public boolean append(String string) {
    return appendStyledText(string, "normal");
  }

  /**
   * Appends p_value to the text pane after transforming it to the user coordinate system. Returns
   * false, if that was not possible.
   */
  @Override
  public boolean append(double value) {
    Float userValue = (float) this.coordinateTransform.boardToUser(value);
    return append(numberFormat.format(userValue));
  }

  /**
   * Appends p_point to the text pane after transforming to the user coordinate system. Returns
   * false, if that was not possible.
   */
  @Override
  public boolean append(FloatPoint point) {
    FloatPoint transformedPoint = this.coordinateTransform.boardToUser(point);
    return append(transformedPoint.toString(boardFrame.get_locale()));
  }

  /**
   * Appends p_shape to the text pane after transforming to the user coordinate system. Returns
   * false, if that was not possible.
   */
  @Override
  public boolean append(Shape shape, Locale locale) {
    PrintableShape transformedShape = this.coordinateTransform.boardToUser(shape, locale);
    if (transformedShape == null) {
      return false;
    }
    return append(transformedShape.toString());
  }

  /**
   * Appends a button for creating a new ObjectInfoWindow with the information of an object.
   *
   * @param buttonName the button label
   * @param windowTitle the title of the information window
   * @param object the object to display
   * @return whether the button was appended
   */
  @Override
  public boolean append(String buttonName, String windowTitle, WindowObjectInfo.Printable object) {
    Collection<WindowObjectInfo.Printable> objectList = new LinkedList<>();
    objectList.add(object);
    return appendObjects(buttonName, windowTitle, objectList);
  }

  /** Begins a new line in the text pane. */
  @Override
  public boolean newline() {
    return append("\n");
  }

  /** Appends a fixed number of spaces to the text pane. */
  @Override
  public boolean indent() {
    return append("       ");
  }

  /**
   * Appends a button for creating a new ObjectInfoWindow with the information of p_items to the
   * text pane. Returns false, if that was not possible.
   */
  @Override
  public boolean appendItems(String buttonName, String windowTitle, Collection<Item> items) {
    Collection<WindowObjectInfo.Printable> objectList = new LinkedList<>(items);
    return appendObjects(buttonName, windowTitle, objectList);
  }

  /**
   * Appends a button for creating a new ObjectInfoWindow with the information of p_objects to the
   * text pane. Returns false, if that was not possible.
   */
  @Override
  public boolean appendObjects(
      String buttonName, String windowTitle, Collection<WindowObjectInfo.Printable> objects) {
    // create a button without border and color.
    JButton objectInfoButton = new JButton();
    objectInfoButton.setText(buttonName);
    objectInfoButton.setBorderPainted(false);
    objectInfoButton.setContentAreaFilled(false);
    objectInfoButton.setMargin(new Insets(0, 0, 0, 0));
    objectInfoButton.setAlignmentY(0.75f);
    // Display the button name in blue.
    objectInfoButton.setForeground(Color.blue);

    objectInfoButton.addActionListener(new InfoButtonListener(windowTitle, objects));
    objectInfoButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("objectInfoButton", objectInfoButton.getText()));

    // Add style for inserting the button  to the document.
    StyledDocument document = this.textPane.getStyledDocument();
    Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
    Style buttonStyle = document.addStyle(buttonName, defaultStyle);
    StyleConstants.setAlignment(buttonStyle, StyleConstants.ALIGN_CENTER);
    StyleConstants.setComponent(buttonStyle, objectInfoButton);

    // Add the button to the document.
    try {
      document.insertString(document.getLength(), buttonName, buttonStyle);
    } catch (BadLocationException _) {
      System.err.println("ObjectInfoWindow.append: unable to insert text into text pane.");
      return false;
    }
    return true;
  }

  @Override
  public void dispose() {
    for (WindowObjectInfo currSubwindow : this.subwindows) {
      if (currSubwindow != null) {
        currSubwindow.dispose();
      }
    }
    super.dispose();
  }

  private class InfoButtonListener implements ActionListener {

    private static final int WINDOW_OFFSET = 30;

    /** The title of this window. */
    private final String title;

    /** The objects, for which information is displayed in the new window. */
    private final Collection<Printable> objects;

    public InfoButtonListener(String title, Collection<Printable> objects) {
      this.title = title;
      this.objects = objects;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      WindowObjectInfo newWindow =
          display(this.title, this.objects, boardFrame, coordinateTransform);

      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }
}
