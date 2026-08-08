package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import java.util.Collection;
import java.util.Locale;

/** Output window for printing information about board objects. */
public interface ObjectInfoPanel {

  /** Appends p_string to the window. Returns false, if that was not possible. */
  boolean append(String pString);

  /** Appends p_string in bold style to the window. Returns false, if that was not possible. */
  boolean appendBold(String pString);

  /**
   * Appends p_value to the window after transforming it to the user coordinate system. Returns
   * false, if that was not possible.
   */
  boolean append(double pValue);

  /**
   * Appends p_value to the window without transforming it to the user coordinate system. Returns
   * false, if that was not possible.
   */
  boolean appendWithoutTransforming(double pValue);

  /**
   * Appends p_point to the window after transforming to the user coordinate system. Returns false,
   * if that was not possible.
   */
  boolean append(FloatPoint pPoint);

  /**
   * Appends p_shape to the window after transforming to the user coordinate system. Returns false,
   * if that was not possible.
   */
  boolean append(Shape pShape, Locale pLocale);

  /** Begins a new line in the window. */
  boolean newline();

  /** Appends a fixed number of spaces to the window. */
  boolean indent();

  /**
   * Appends a link for creating a new PrintInfoWindow with the information of p_object to the
   * window. Returns false, if that was not possible.
   */
  boolean append(String pLinkName, String pWindowTitle, ObjectInfoPanel.Printable pObject);

  /**
   * Appends a link for creating a new PrintInfoWindow with the information of p_items to the
   * window. Returns false, if that was not possible.
   */
  boolean appendItems(String pLinkName, String pWindowTitle, Collection<Item> pItems);

  /**
   * Appends a link for creating a new PrintInfoWindow with the information of p_objects to the
   * window. Returns false, if that was not possible.
   */
  boolean appendObjects(String pButtonName, String pWindowTitle, Collection<Printable> pObjects);

  /** Functionality needed for objects to print information into an ObjectInfoWindow */
  interface Printable {

    /** Prints information about an ObjectInfoWindow.Printable object into the input window. */
    void printInfo(ObjectInfoPanel pWindow, Locale pLocale);
  }
}
