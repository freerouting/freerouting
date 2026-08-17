package app.freerouting.core.library;

import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;

/** Describes padstack masks for pins or vias located at the origin. */
public class Padstack implements Comparable<Padstack>, ItemInfoPrinter.Printable, Serializable {

  public final String name;
  public final int id;

  /** Whether vias of the own net may overlap with this padstack. */
  public final boolean attachAllowed;

  /**
   * If false, the layers of the padstack are mirrored, if it is placed on the back side. The
   * default is false.
   */
  public final boolean placedAbsolute;

  private final ConvexShape[] shapes;

  /** Pointer to the padstack list containing this padstack. */
  private final Padstacks padstackList;

  /**
   * True for padstacks whose copper-layer shapes were synthesized from the drill radius because the
   * source padstack had no copper at all (non-plated holes). Such shapes exist only so the hole
   * becomes an obstacle for routing and DRC; they must not be treated as real copper (e.g. not
   * re-exported).
   */
  public boolean holeOnly;

  /** Cached drill radius to avoid repeated regex parsing on every render call. */
  private Double cachedDrillRadius;

  /** Creates a new Padstack with one shape per board layer. */
  Padstack(
      String name,
      int id,
      ConvexShape[] shapes,
      boolean isDrillable,
      boolean placedAbsolute,
      Padstacks padstackList) {
    this.shapes = shapes;
    this.name = name;
    this.id = id;
    this.attachAllowed = isDrillable;
    this.placedAbsolute = placedAbsolute;
    this.padstackList = padstackList;
  }

  /** Compares 2 padstacks by name. Useful for example to display padstacks in alphabetic order. */
  @Override
  public int compareTo(Padstack other) {
    return this.name.compareToIgnoreCase(other.name);
  }

  /**
   * Returns the drill radius of this padstack in board units. The result is cached after the first
   * computation to avoid repeated regex parsing.
   */
  public double getDrillRadius() {
    if (cachedDrillRadius != null) {
      return cachedDrillRadius;
    }
    double result;
    if (name != null) {
      int colonIndex = name.indexOf(':');
      if (colonIndex >= 0) {
        int underscoreIndex = name.indexOf('_', colonIndex);
        String drillStr;
        if (underscoreIndex > colonIndex) {
          drillStr = name.substring(colonIndex + 1, underscoreIndex);
        } else {
          drillStr = name.substring(colonIndex + 1);
        }
        try {
          drillStr = drillStr.replaceAll("[^0-9.]", "");
          double drillDia = Double.parseDouble(drillStr);
          int lastUnderscore = name.lastIndexOf('_', colonIndex);
          if (lastUnderscore >= 0) {
            String outerStr =
                name.substring(lastUnderscore + 1, colonIndex).replaceAll("[^0-9.]", "");
            double outerDia = Double.parseDouble(outerStr);
            if (outerDia > 0) {
              double actualOuterRadius = getSmallestRadius();
              if (actualOuterRadius > 0) {
                result = actualOuterRadius * (drillDia / outerDia);
                cachedDrillRadius = result;
                return cachedDrillRadius;
              }
            }
          }
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    result = getSmallestRadius() * 0.45;
    cachedDrillRadius = result;
    return cachedDrillRadius;
  }

  private double getSmallestRadius() {
    double minRadius = Double.MAX_VALUE;
    for (ConvexShape shape : shapes) {
      if (shape != null) {
        double radius = Math.min(shape.boundingBox().width(), shape.boundingBox().height()) / 2.0;
        if (radius < minRadius) {
          minRadius = radius;
        }
      }
    }
    return minRadius == Double.MAX_VALUE ? 0.0 : minRadius;
  }

  /** Gets the shape of this padstack on the specified layer. */
  public ConvexShape getShape(int layer) {
    if (layer < 0 || layer >= shapes.length) {
      FRLogger.warn("Padstack.get_layer layer out of range");
      return null;
    }
    return shapes[layer];
  }

  /** Returns the first layer of this padstack with a shape != null. */
  public int fromLayer() {
    int result = 0;
    while (result < shapes.length && shapes[result] == null) {
      ++result;
    }
    return result;
  }

  /** Returns the last layer of this padstack with a shape != null. */
  public int toLayer() {
    int result = shapes.length - 1;
    while (result >= 0 && shapes[result] == null) {
      --result;
    }
    return result;
  }

  /** Returns the layer count of the board of this padstack. */
  public int boardLayerCount() {
    return shapes.length;
  }

  @Override
  public String toString() {
    return this.name;
  }

  /**
   * Calculates the allowed trace exit directions on a layer. If the length of the pad is smaller
   * than factor times its height, connection also to the long side is allowed.
   */
  public Collection<Direction> getTraceExitDirections(int layer, double factor) {
    Collection<Direction> result = new LinkedList<>();
    if (layer < 0 || layer >= shapes.length) {
      return result;
    }
    ConvexShape currentShape = shapes[layer];
    if (currentShape == null) {
      return result;
    }
    if (!(currentShape instanceof IntBox || currentShape instanceof IntOctagon)) {
      return result;
    }
    IntBox currentBox = currentShape.boundingBox();

    boolean allDirs =
        Math.max(currentBox.width(), currentBox.height())
            < factor * Math.min(currentBox.width(), currentBox.height());

    if (allDirs || currentBox.width() >= currentBox.height()) {
      result.add(Direction.RIGHT);
      result.add(Direction.LEFT);
    }
    if (allDirs || currentBox.width() <= currentBox.height()) {
      result.add(Direction.UP);
      result.add(Direction.DOWN);
    }
    return result;
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("padstack") + " ");
    printer.appendBold(this.name);
    for (int i = 0; i < shapes.length; i++) {
      if (shapes[i] != null) {
        printer.newline();
        printer.indent();
        printer.append(shapes[i], locale);
        printer.append(" " + tm.getText("on_layer") + " ");
        printer.append(padstackList.boardLayerStructure.layers[i].name);
      }
    }
    printer.newline();
  }
}
