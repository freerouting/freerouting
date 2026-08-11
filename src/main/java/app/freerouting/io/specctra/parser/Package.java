package app.freerouting.io.specctra.parser;

import app.freerouting.board.Item;
import app.freerouting.core.Padstack;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for reading and writing package scopes from dsn-files. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class Package {

  public final String name;

  /** List of objects of type PinInfo. */
  public final PinInfo[] pinInfoArr;

  /** The outline of the package. */
  public final Collection<Shape> outline;

  /** Collection of keepoouts belonging to this package. */
  public final Collection<Shape.ReadAreaScopeResult> keepouts;

  /** Collection of via keepoouts belonging to this package. */
  public final Collection<Shape.ReadAreaScopeResult> viaKeepouts;

  /** Collection of place keepoouts belonging to this package. */
  public final Collection<Shape.ReadAreaScopeResult> placeKeepouts;

  /** If false, the package is placed on the back side of the board. */
  public final boolean isFront;

  /** Creates a new instance of Package. */
  public Package(
      String name,
      PinInfo[] pinInfoArr,
      Collection<Shape> outline,
      Collection<Shape.ReadAreaScopeResult> keepouts,
      Collection<Shape.ReadAreaScopeResult> viaKeepouts,
      Collection<Shape.ReadAreaScopeResult> placeKeepouts,
      boolean isFront) {
    this.name = name;
    this.pinInfoArr = pinInfoArr;
    this.outline = outline;
    this.keepouts = keepouts;
    this.viaKeepouts = viaKeepouts;
    this.placeKeepouts = placeKeepouts;
    this.isFront = isFront;
  }

  public static Package readScope(IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      boolean isFront = true;
      Collection<Shape> outline = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> keepouts = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> viaKeepouts = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> placeKeepouts = new LinkedList<>();
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String packageName)) {
        FRLogger.warn(
            "Package.read_scope: String expected at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      scanner.setScopeIdentifier(packageName);
      Collection<PinInfo> pinInfoList = new LinkedList<>();
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = scanner.nextToken();

        if (nextToken == null) {
          FRLogger.warn(
              "Package.read_scope: unexpected end of file at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.PIN) {
            PinInfo nextPin = readPinInfo(scanner);
            if (nextPin == null) {
              return null;
            }
            pinInfoList.add(nextPin);
          } else if (nextToken == Keyword.SIDE) {
            isFront = readPlacementSide(scanner);
          } else if (nextToken == Keyword.OUTLINE) {
            Shape currShape = Shape.readScope(scanner, layerStructure);
            if (currShape != null) {
              outline.add(currShape);
            }
            // overread closing bracket
            nextToken = scanner.nextToken();
            if (nextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "Package.read_scope: closed bracket expected at '"
                      + scanner.getScopeIdentifier()
                      + "'");
              return null;
            }
          } else if (nextToken == Keyword.KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(scanner, layerStructure, false);
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
                Shape.readAreaScope(scanner, layerStructure, false);
            if (keepoutArea != null) {
              viaKeepouts.add(keepoutArea);
            }
          } else if (nextToken == Keyword.PLACE_KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(scanner, layerStructure, false);
            if (keepoutArea != null) {
              placeKeepouts.add(keepoutArea);
            }
          } else {
            ScopeKeyword.skipScope(scanner);
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

  public static void writeScope(WriteScopeParameter par, app.freerouting.core.Package boardPackage)
      throws IOException {
    par.file.startScope();
    par.file.write("image ");
    par.identifierType.write(boardPackage.name, par.file);
    // write the placement side of the package
    par.file.newLine();
    par.file.write("(side ");
    if (boardPackage.isFront) {
      par.file.write("front)");
    } else {
      par.file.write("back)");
    }
    // write the pins of the package
    for (int i = 0; i < boardPackage.pinCount(); i++) {
      app.freerouting.core.Package.Pin currPin = boardPackage.getPin(i);
      par.file.newLine();
      par.file.write("(pin ");
      Padstack currPadstack = par.board.library.padstacks.get(currPin.padstackNo);
      par.identifierType.write(currPadstack.name, par.file);
      par.file.write(" ");
      par.identifierType.write(currPin.name, par.file);
      double[] relCoor = par.coordinateTransform.boardToDsn(currPin.relativeLocation);
      for (int j = 0; j < relCoor.length; j++) {
        par.file.write(" ");
        par.file.write(String.valueOf(relCoor[j]));
      }
      int rotation = (int) Math.round(currPin.rotationInDegree);
      if (rotation != 0) {
        par.file.write("(rotate ");
        par.file.write(String.valueOf(rotation));
        par.file.write(")");
      }
      par.file.write(")");
    }
    // write the keepouts belonging to  the package.
    for (int i = 0; i < boardPackage.keepoutArr.length; i++) {
      writePackageKeepout(boardPackage.keepoutArr[i], par, false);
    }
    for (int i = 0; i < boardPackage.viaKeepoutArr.length; i++) {
      writePackageKeepout(boardPackage.viaKeepoutArr[i], par, true);
    }
    // write the package outline.
    if (boardPackage.outline != null) {
      for (int i = 0; i < boardPackage.outline.length; i++) {
        par.file.startScope();
        par.file.write("outline");
        Shape currOutline =
            par.coordinateTransform.boardToDsnRel(boardPackage.outline[i], Layer.SIGNAL);
        currOutline.writeScope(par.file, par.identifierType);
        par.file.endScope();
      }
    }
    par.file.endScope();
  }

  private static void writePackageKeepout(
      app.freerouting.core.Package.Keepout keepout, WriteScopeParameter par, boolean isViaKeepout)
      throws IOException {
    Layer keepoutLayer;
    if (keepout.layer >= 0) {
      app.freerouting.board.Layer boardLayer = par.board.layerStructure.arr[keepout.layer];
      keepoutLayer = new Layer(boardLayer.name, keepout.layer, boardLayer.isSignal);
    } else {
      keepoutLayer = Layer.SIGNAL;
    }
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (keepout.area instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = keepout.area.getBorder();
      holes = keepout.area.getHoles();
    }
    par.file.startScope();
    if (isViaKeepout) {
      par.file.write("via_keepout");
    } else {
      par.file.write("keepout");
    }
    Shape dsnShape = par.coordinateTransform.boardToDsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(par.file, par.identifierType);
    }
    for (int j = 0; j < holes.length; j++) {
      Shape dsnHole = par.coordinateTransform.boardToDsn(holes[j], keepoutLayer);
      dsnHole.writeHoleScope(par.file, par.identifierType);
    }
    par.file.endScope();
  }

  /** Reads the information of a single pin in a package. */
  private static PinInfo readPinInfo(IJFlexScanner scanner) {
    try {
      // Read the padstack name.
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String) && !(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Package.read_pin_info: String or Integer expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      final String padstackName = nextToken.toString();
      double rotation = 0;

      scanner.yybegin(
          SpecctraDsnStreamReader.NAME); // to be able to handle pin names starting with a digit.
      nextToken = scanner.nextToken();
      if (nextToken == Keyword.OPEN_BRACKET) {
        // read the padstack rotation
        nextToken = scanner.nextToken();
        if (nextToken == Keyword.ROTATE) {
          rotation = readRotation(scanner);
        } else {
          ScopeKeyword.skipScope(scanner);
        }
        scanner.yybegin(SpecctraDsnStreamReader.NAME);
        nextToken = scanner.nextToken();
      }
      // Read the pin name.
      if (!(nextToken instanceof String) && !(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Package.read_pin_info: String or Integer expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      String pinName = nextToken.toString();

      double[] pinCoor = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = scanner.nextToken();
        if (nextToken instanceof Double double1) {
          pinCoor[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          pinCoor[i] = integer;
        } else {
          FRLogger.warn(
              "Package.read_pin_info: number expected at '" + scanner.getScopeIdentifier() + "'");
          return null;
        }
      }
      // Handle scopes at the end of the pin scope.
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = scanner.nextToken();

        if (nextToken == null) {
          FRLogger.warn(
              "Package.read_pin_info: unexpected end of file at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.ROTATE) {
            rotation = readRotation(scanner);
          } else {
            ScopeKeyword.skipScope(scanner);
          }
        }
      }
      return new PinInfo(padstackName, pinName, pinCoor, rotation);
    } catch (IOException e) {
      FRLogger.error("Package.read_pin_info: IO error while scanning file", e);
      return null;
    }
  }

  private static double readRotation(IJFlexScanner scanner) {
    double result = 0;

    try {
      String nextString = scanner.nextString();
      result = Double.parseDouble(nextString);

      // Overread The closing bracket.
      Object nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Package.read_rotation: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
      }
    } catch (IOException e) {
      FRLogger.error("Package.read_rotation: IO error while scanning file", e);
    }

    return result;
  }

  /** Writes the placements of p_package to a Specctra dsn-file. */
  public static void writePlacementScope(
      WriteScopeParameter par, app.freerouting.core.Package boardPackage) throws IOException {
    Collection<Item> boardItems = par.board.getItems();
    boolean componentFound = false;
    for (int i = 1; i <= par.board.components.count(); i++) {
      app.freerouting.board.Component currComponent = par.board.components.get(i);
      if (currComponent.getPackage() == boardPackage) {
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
            par.file.startScope();
            par.file.write("component ");
            par.identifierType.write(boardPackage.name, par.file);
            componentFound = true;
          }
          Component.writeScope(par, currComponent);
        }
      }
    }
    if (componentFound) {
      par.file.endScope();
    }
  }

  private static boolean readPlacementSide(IJFlexScanner scanner) throws IOException {
    Object nextToken = scanner.nextToken();
    boolean result = nextToken != Keyword.BACK;

    nextToken = scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "Package.read_placement_side: closing bracket expected at '"
              + scanner.getScopeIdentifier()
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

    PinInfo(String padstackName, String pinName, double[] relCoor, double rotation) {
      this.padstackName = padstackName;
      this.pinName = pinName;
      this.relCoor = relCoor;
      this.rotation = rotation;
    }
  }
}
