package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import java.util.Collection;
import java.util.Locale;

/** Output printer interface for printing detailed human-readable information about items. */
public interface ItemInfoPrinter {

  /** Appends string to the output printer. Returns false if that was not possible. */
  boolean append(String string);

  /**
   * Appends a link for opening/creating a new window with the information of object. Returns false
   * if that was not possible.
   */
  boolean append(String linkName, String windowTitle, ItemInfoPrinter.Printable object);

  /**
   * Appends value to the output printer after transforming it to the user coordinate system.
   * Returns false if that was not possible.
   */
  boolean append(double value);

  /**
   * Appends point to the output printer after transforming to the user coordinate system. Returns
   * false if that was not possible.
   */
  boolean append(FloatPoint point);

  /**
   * Appends shape to the output printer after transforming to the user coordinate system. Returns
   * false if that was not possible.
   */
  boolean append(Shape shape, Locale locale);

  /**
   * Appends value to the output printer without transforming it to the user coordinate system.
   * Returns false if that was not possible.
   */
  boolean appendWithoutTransforming(double value);

  /** Appends string in bold style to the output printer. Returns false if that was not possible. */
  boolean appendBold(String string);

  /** Begins a new line in the output printer. */
  boolean newline();

  /** Appends a fixed number of spaces to the output printer. */
  boolean indent();

  /**
   * Appends a link for creating a new window with the information of items. Returns false if that
   * was not possible.
   */
  boolean appendItems(String linkName, String windowTitle, Collection<Item> items);

  /**
   * Appends a link for creating a new window with the information of printable objects. Returns
   * false if that was not possible.
   */
  boolean appendObjects(String buttonName, String windowTitle, Collection<Printable> objects);

  /** Functionality needed for objects to print information into an {@link ItemInfoPrinter}. */
  interface Printable {

    /** Prints information about this object into the target printer. */
    void printInfo(ItemInfoPrinter printer, Locale locale);
  }
}
