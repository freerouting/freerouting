package app.freerouting.io.specctra.parser;

import app.freerouting.board.Item;
import app.freerouting.core.Padstack;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for reading and writing package scopes from dsn-files. */
public class Package {

  public final String name;

  /** List of objects of type PinInfo. */
  public final PinInfo[] pinInfoArr;

  /** The outline of the package. */
  public final Collection<Shape> outline;

  /** Collection of keepoouts belonging to this package */
  public final Collection<Shape.ReadAreaScopeResult> keepouts;

  /** Collection of via keepoouts belonging to this package */
  public final Collection<Shape.ReadAreaScopeResult> viaKeepouts;

  /** Collection of place keepoouts belonging to this package */
  public final Collection<Shape.ReadAreaScopeResult> placeKeepouts;

  /** If false, the package is placed on the back side of the board */
  public final boolean isFront;

  /** Creates a new instance of Package */
  public Package(
      String p_name,
      PinInfo[] p_pin_info_arr,
      Collection<Shape> p_outline,
      Collection<Shape.ReadAreaScopeResult> p_keepouts,
      Collection<Shape.ReadAreaScopeResult> p_via_keepouts,
      Collection<Shape.ReadAreaScopeResult> p_place_keepouts,
      boolean p_is_front) {
    name = p_name;
    pinInfoArr = p_pin_info_arr;
    outline = p_outline;
    keepouts = p_keepouts;
    viaKeepouts = p_via_keepouts;
    placeKeepouts = p_place_keepouts;
    isFront = p_is_front;
  }

  public static Package readScope(IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      boolean isFront = true;
      Collection<Shape> outline = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> keepouts = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> viaKeepouts = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> placeKeepouts = new LinkedList<>();
      Object nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String packageName)) {
        FRLogger.warn(
            "Package.read_scope: String expected at '" + p_scanner.getScopeIdentifier() + "'");
        return null;
      }
      p_scanner.setScopeIdentifier(packageName);
      Collection<PinInfo> pinInfoList = new LinkedList<>();
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = p_scanner.nextToken();

        if (nextToken == null) {
          FRLogger.warn(
              "Package.read_scope: unexpected end of file at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.PIN) {
            PinInfo nextPin = readPinInfo(p_scanner);
            if (nextPin == null) {
              return null;
            }
            pinInfoList.add(nextPin);
          } else if (nextToken == Keyword.SIDE) {
            isFront = readPlacementSide(p_scanner);
          } else if (nextToken == Keyword.OUTLINE) {
            Shape currShape = Shape.readScope(p_scanner, p_layer_structure);
            if (currShape != null) {
              outline.add(currShape);
            }
            // overread closing bracket
            nextToken = p_scanner.nextToken();
            if (nextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "Package.read_scope: closed bracket expected at '"
                      + p_scanner.getScopeIdentifier()
                      + "'");
              return null;
            }
          } else if (nextToken == Keyword.KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(p_scanner, p_layer_structure, false);
            if (keepoutArea != null) {
              keepouts.add(keepoutArea);
            } else {
              FRLogger.error(
                  "Package.read_scope: could not read keepout area of package '"
                      + packageName
                      + "'",
                  null);
            }
          } else if (nextToken == Keyword.VIA_KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(p_scanner, p_layer_structure, false);
            if (keepoutArea != null) {
              viaKeepouts.add(keepoutArea);
            }
          } else if (nextToken == Keyword.PLACE_KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(p_scanner, p_layer_structure, false);
            if (keepoutArea != null) {
              placeKeepouts.add(keepoutArea);
            }
          } else {
            ScopeKeyword.skipScope(p_scanner);
          }
        }
      }
      PinInfo[] pinInfoArr = new PinInfo[pinInfoList.size()];
      Iterator<PinInfo> it = pinInfoList.iterator();
      for (int i = 0; i < pinInfoArr.length; i++) {
        pinInfoArr[i] = it.next();
      }
      return new Package(
          packageName, pinInfoArr, outline, keepouts, viaKeepouts, placeKeepouts, isFront);
    } catch (IOException e) {
      FRLogger.error("Package.read_scope: IO error scanning file", e);
      return null;
    }
  }

  public static void writeScope(WriteScopeParameter p_par, app.freerouting.core.Package p_package)
      throws IOException {
    p_par.file.startScope();
    p_par.file.write("image ");
    p_par.identifierType.write(p_package.name, p_par.file);
    // write the placement side of the package
    p_par.file.newLine();
    p_par.file.write("(side ");
    if (p_package.isFront) {
      p_par.file.write("front)");
    } else {
      p_par.file.write("back)");
    }
    // write the pins of the package
    for (int i = 0; i < p_package.pinCount(); i++) {
      app.freerouting.core.Package.Pin currPin = p_package.getPin(i);
      p_par.file.newLine();
      p_par.file.write("(pin ");
      Padstack currPadstack = p_par.board.library.padstacks.get(currPin.padstackNo);
      p_par.identifierType.write(currPadstack.name, p_par.file);
      p_par.file.write(" ");
      p_par.identifierType.write(currPin.name, p_par.file);
      double[] relCoor = p_par.coordinateTransform.boardToDsn(currPin.relativeLocation);
      for (int j = 0; j < relCoor.length; j++) {
        p_par.file.write(" ");
        p_par.file.write(String.valueOf(relCoor[j]));
      }
      int rotation = (int) Math.round(currPin.rotationInDegree);
      if (rotation != 0) {
        p_par.file.write("(rotate ");
        p_par.file.write(String.valueOf(rotation));
        p_par.file.write(")");
      }
      p_par.file.write(")");
    }
    // write the keepouts belonging to  the package.
    for (int i = 0; i < p_package.keepoutArr.length; i++) {
      writePackageKeepout(p_package.keepoutArr[i], p_par, false);
    }
    for (int i = 0; i < p_package.viaKeepoutArr.length; i++) {
      writePackageKeepout(p_package.viaKeepoutArr[i], p_par, true);
    }
    // write the package outline.
    if (p_package.outline != null) {
      for (int i = 0; i < p_package.outline.length; i++) {
        p_par.file.startScope();
        p_par.file.write("outline");
        Shape currOutline =
            p_par.coordinateTransform.boardToDsnRel(p_package.outline[i], Layer.SIGNAL);
        currOutline.writeScope(p_par.file, p_par.identifierType);
        p_par.file.endScope();
      }
    }
    p_par.file.endScope();
  }

  private static void writePackageKeepout(
      app.freerouting.core.Package.Keepout p_keepout,
      WriteScopeParameter p_par,
      boolean p_is_via_keepout)
      throws IOException {
    Layer keepoutLayer;
    if (p_keepout.layer >= 0) {
      app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[p_keepout.layer];
      keepoutLayer = new Layer(boardLayer.name, p_keepout.layer, boardLayer.isSignal);
    } else {
      keepoutLayer = Layer.SIGNAL;
    }
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (p_keepout.area instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = p_keepout.area.getBorder();
      holes = p_keepout.area.getHoles();
    }
    p_par.file.startScope();
    if (p_is_via_keepout) {
      p_par.file.write("via_keepout");
    } else {
      p_par.file.write("keepout");
    }
    Shape dsnShape = p_par.coordinateTransform.boardToDsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(p_par.file, p_par.identifierType);
    }
    for (int j = 0; j < holes.length; j++) {
      Shape dsnHole = p_par.coordinateTransform.boardToDsn(holes[j], keepoutLayer);
      dsnHole.writeHoleScope(p_par.file, p_par.identifierType);
    }
    p_par.file.endScope();
  }

  /** Reads the information of a single pin in a package. */
  private static PinInfo readPinInfo(IJFlexScanner p_scanner) {
    try {
      // Read the padstack name.
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String) && !(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Package.read_pin_info: String or Integer expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      String padstackName = nextToken.toString();
      double rotation = 0;

      p_scanner.yybegin(
          SpecctraDsnStreamReader.NAME); // to be able to handle pin names starting with a digit.
      nextToken = p_scanner.nextToken();
      if (nextToken == Keyword.OPEN_BRACKET) {
        // read the padstack rotation
        nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.ROTATE) {
          rotation = readRotation(p_scanner);
        } else {
          ScopeKeyword.skipScope(p_scanner);
        }
        p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
        nextToken = p_scanner.nextToken();
      }
      // Read the pin name.
      if (!(nextToken instanceof String) && !(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Package.read_pin_info: String or Integer expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      String pinName = nextToken.toString();

      double[] pinCoor = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = p_scanner.nextToken();
        if (nextToken instanceof Double double1) {
          pinCoor[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          pinCoor[i] = integer;
        } else {
          FRLogger.warn(
              "Package.read_pin_info: number expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }
      // Handle scopes at the end of the pin scope.
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = p_scanner.nextToken();

        if (nextToken == null) {
          FRLogger.warn(
              "Package.read_pin_info: unexpected end of file at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.ROTATE) {
            rotation = readRotation(p_scanner);
          } else {
            ScopeKeyword.skipScope(p_scanner);
          }
        }
      }
      return new PinInfo(padstackName, pinName, pinCoor, rotation);
    } catch (IOException e) {
      FRLogger.error("Package.read_pin_info: IO error while scanning file", e);
      return null;
    }
  }

  private static double readRotation(IJFlexScanner p_scanner) {
    double result = 0;

    try {
      String nextString = p_scanner.nextString();
      result = Double.parseDouble(nextString);

      // Overread The closing bracket.
      Object nextToken = p_scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Package.read_rotation: closing bracket expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
      }
    } catch (IOException e) {
      FRLogger.error("Package.read_rotation: IO error while scanning file", e);
    }

    return result;
  }

  /** Writes the placements of p_package to a Specctra dsn-file. */
  public static void writePlacementScope(
      WriteScopeParameter p_par, app.freerouting.core.Package p_package) throws IOException {
    Collection<Item> boardItems = p_par.board.getItems();
    boolean componentFound = false;
    for (int i = 1; i <= p_par.board.components.count(); i++) {
      app.freerouting.board.Component currComponent = p_par.board.components.get(i);
      if (currComponent.getPackage() == p_package) {
        // check, if not all items of the component are deleted
        boolean undeletedItemFound = false;
        for (Item currItem : boardItems) {
          if (currItem.getComponentNo() == currComponent.no) {
            undeletedItemFound = true;
            break;
          }
        }
        if (undeletedItemFound || !currComponent.isPlaced()) {
          if (!componentFound) {
            // write the scope header
            p_par.file.startScope();
            p_par.file.write("component ");
            p_par.identifierType.write(p_package.name, p_par.file);
            componentFound = true;
          }
          Component.writeScope(p_par, currComponent);
        }
      }
    }
    if (componentFound) {
      p_par.file.endScope();
    }
  }

  private static boolean readPlacementSide(IJFlexScanner p_scanner) throws IOException {
    Object nextToken = p_scanner.nextToken();
    boolean result = nextToken != Keyword.BACK;

    nextToken = p_scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "Package.read_placement_side: closing bracket expected at '"
              + p_scanner.getScopeIdentifier()
              + "'");
    }
    return result;
  }

  /** Describes the Iinformation of a pin in a package. */
  public static class PinInfo {

    /** Phe name of the pastack of this pin. */
    public final String padstackName;

    /** Phe name of this pin. */
    public final String pinName;

    /** The x- and y-coordinates relative to the package location. */
    public final double[] relCoor;

    /** The rotation of the pin relative to the package. */
    public final double rotation;

    PinInfo(String p_padstack_name, String p_pin_name, double[] p_rel_coor, double p_rotation) {
      padstackName = p_padstack_name;
      pinName = p_pin_name;
      relCoor = p_rel_coor;
      rotation = p_rotation;
    }
  }
}
