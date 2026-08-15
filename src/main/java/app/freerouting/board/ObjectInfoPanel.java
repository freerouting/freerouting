package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import java.util.Collection;
import java.util.Locale;

/** Output window for printing information about board objects. */
public interface ObjectInfoPanel {

  /** Appends string to the window. Returns false, if that was not possible. */
  boolean append(String string);

  /**
   * Appends a link for creating a new PrintInfoWindow with the information of object to the window.
   * Returns false, if that was not possible.
   */
  boolean append(String linkName, String windowTitle, ObjectInfoPanel.Printable object);

  /**
   * Appends value to the window after transforming it to the user coordinate system. Returns false,
   * if that was not possible.
   */
  boolean append(double value);

  /**
   * Appends point to the window after transforming to the user coordinate system. Returns false, if
   * that was not possible.
   */
  boolean append(FloatPoint point);

  /**
   * Appends shape to the window after transforming to the user coordinate system. Returns false, if
   * that was not possible.
   */
  boolean append(Shape shape, Locale locale);

  /**
   * Appends value to the window without transforming it to the user coordinate system. Returns
   * false, if that was not possible.
   */
  boolean appendWithoutTransforming(double value);

  /** Appends string in bold style to the window. Returns false, if that was not possible. */
  boolean appendBold(String string);

  /** Begins a new line in the window. */
  boolean newline();

  /** Appends a fixed number of spaces to the window. */
  boolean indent();

  /**
   * Appends a link for creating a new PrintInfoWindow with the information of items to the window.
   * Returns false, if that was not possible.
   */
  boolean appendItems(String linkName, String windowTitle, Collection<Item> items);

  /**
   * Appends a link for creating a new PrintInfoWindow with the information of objects to the
   * window. Returns false, if that was not possible.
   */
  boolean appendObjects(String buttonName, String windowTitle, Collection<Printable> objects);

  /** Functionality needed for objects to print information into an ObjectInfoWindow. */
  interface Printable {

    /** Prints information about an ObjectInfoWindow.Printable object into the input window. */
    void printInfo(ObjectInfoPanel window, Locale locale);
  }
}
