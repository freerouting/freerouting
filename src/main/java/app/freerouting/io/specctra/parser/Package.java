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
      String pName,
      PinInfo[] pPinInfoArr,
      Collection<Shape> pOutline,
      Collection<Shape.ReadAreaScopeResult> pKeepouts,
      Collection<Shape.ReadAreaScopeResult> pViaKeepouts,
      Collection<Shape.ReadAreaScopeResult> pPlaceKeepouts,
      boolean pIsFront) {
    name = pName;
    pinInfoArr = pPinInfoArr;
    outline = pOutline;
    keepouts = pKeepouts;
    viaKeepouts = pViaKeepouts;
    placeKeepouts = pPlaceKeepouts;
    isFront = pIsFront;
  }

  public static Package readScope(IJFlexScanner pScanner, LayerStructure pLayerStructure) {
    try {
      boolean isFront = true;
      Collection<Shape> outline = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> keepouts = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> viaKeepouts = new LinkedList<>();
      Collection<Shape.ReadAreaScopeResult> placeKeepouts = new LinkedList<>();
      Object nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String packageName)) {
        FRLogger.warn(
            "Package.read_scope: String expected at '" + pScanner.getScopeIdentifier() + "'");
        return null;
      }
      pScanner.setScopeIdentifier(packageName);
      Collection<PinInfo> pinInfoList = new LinkedList<>();
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = pScanner.nextToken();

        if (nextToken == null) {
          FRLogger.warn(
              "Package.read_scope: unexpected end of file at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.PIN) {
            PinInfo nextPin = readPinInfo(pScanner);
            if (nextPin == null) {
              return null;
            }
            pinInfoList.add(nextPin);
          } else if (nextToken == Keyword.SIDE) {
            isFront = readPlacementSide(pScanner);
          } else if (nextToken == Keyword.OUTLINE) {
            Shape currShape = Shape.readScope(pScanner, pLayerStructure);
            if (currShape != null) {
              outline.add(currShape);
            }
            // overread closing bracket
            nextToken = pScanner.nextToken();
            if (nextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "Package.read_scope: closed bracket expected at '"
                      + pScanner.getScopeIdentifier()
                      + "'");
              return null;
            }
          } else if (nextToken == Keyword.KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(pScanner, pLayerStructure, false);
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
                Shape.readAreaScope(pScanner, pLayerStructure, false);
            if (keepoutArea != null) {
              viaKeepouts.add(keepoutArea);
            }
          } else if (nextToken == Keyword.PLACE_KEEPOUT) {
            Shape.ReadAreaScopeResult keepoutArea =
                Shape.readAreaScope(pScanner, pLayerStructure, false);
            if (keepoutArea != null) {
              placeKeepouts.add(keepoutArea);
            }
          } else {
            ScopeKeyword.skipScope(pScanner);
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

  public static void writeScope(WriteScopeParameter pPar, app.freerouting.core.Package pPackage)
      throws IOException {
    pPar.file.startScope();
    pPar.file.write("image ");
    pPar.identifierType.write(pPackage.name, pPar.file);
    // write the placement side of the package
    pPar.file.newLine();
    pPar.file.write("(side ");
    if (pPackage.isFront) {
      pPar.file.write("front)");
    } else {
      pPar.file.write("back)");
    }
    // write the pins of the package
    for (int i = 0; i < pPackage.pinCount(); i++) {
      app.freerouting.core.Package.Pin currPin = pPackage.getPin(i);
      pPar.file.newLine();
      pPar.file.write("(pin ");
      Padstack currPadstack = pPar.board.library.padstacks.get(currPin.padstackNo);
      pPar.identifierType.write(currPadstack.name, pPar.file);
      pPar.file.write(" ");
      pPar.identifierType.write(currPin.name, pPar.file);
      double[] relCoor = pPar.coordinateTransform.boardToDsn(currPin.relativeLocation);
      for (int j = 0; j < relCoor.length; j++) {
        pPar.file.write(" ");
        pPar.file.write(String.valueOf(relCoor[j]));
      }
      int rotation = (int) Math.round(currPin.rotationInDegree);
      if (rotation != 0) {
        pPar.file.write("(rotate ");
        pPar.file.write(String.valueOf(rotation));
        pPar.file.write(")");
      }
      pPar.file.write(")");
    }
    // write the keepouts belonging to  the package.
    for (int i = 0; i < pPackage.keepoutArr.length; i++) {
      writePackageKeepout(pPackage.keepoutArr[i], pPar, false);
    }
    for (int i = 0; i < pPackage.viaKeepoutArr.length; i++) {
      writePackageKeepout(pPackage.viaKeepoutArr[i], pPar, true);
    }
    // write the package outline.
    if (pPackage.outline != null) {
      for (int i = 0; i < pPackage.outline.length; i++) {
        pPar.file.startScope();
        pPar.file.write("outline");
        Shape currOutline =
            pPar.coordinateTransform.boardToDsnRel(pPackage.outline[i], Layer.SIGNAL);
        currOutline.writeScope(pPar.file, pPar.identifierType);
        pPar.file.endScope();
      }
    }
    pPar.file.endScope();
  }

  private static void writePackageKeepout(
      app.freerouting.core.Package.Keepout pKeepout,
      WriteScopeParameter pPar,
      boolean pIsViaKeepout)
      throws IOException {
    Layer keepoutLayer;
    if (pKeepout.layer >= 0) {
      app.freerouting.board.Layer boardLayer = pPar.board.layerStructure.arr[pKeepout.layer];
      keepoutLayer = new Layer(boardLayer.name, pKeepout.layer, boardLayer.isSignal);
    } else {
      keepoutLayer = Layer.SIGNAL;
    }
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (pKeepout.area instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = pKeepout.area.getBorder();
      holes = pKeepout.area.getHoles();
    }
    pPar.file.startScope();
    if (pIsViaKeepout) {
      pPar.file.write("via_keepout");
    } else {
      pPar.file.write("keepout");
    }
    Shape dsnShape = pPar.coordinateTransform.boardToDsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(pPar.file, pPar.identifierType);
    }
    for (int j = 0; j < holes.length; j++) {
      Shape dsnHole = pPar.coordinateTransform.boardToDsn(holes[j], keepoutLayer);
      dsnHole.writeHoleScope(pPar.file, pPar.identifierType);
    }
    pPar.file.endScope();
  }

  /** Reads the information of a single pin in a package. */
  private static PinInfo readPinInfo(IJFlexScanner pScanner) {
    try {
      // Read the padstack name.
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String) && !(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Package.read_pin_info: String or Integer expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      String padstackName = nextToken.toString();
      double rotation = 0;

      pScanner.yybegin(
          SpecctraDsnStreamReader.NAME); // to be able to handle pin names starting with a digit.
      nextToken = pScanner.nextToken();
      if (nextToken == Keyword.OPEN_BRACKET) {
        // read the padstack rotation
        nextToken = pScanner.nextToken();
        if (nextToken == Keyword.ROTATE) {
          rotation = readRotation(pScanner);
        } else {
          ScopeKeyword.skipScope(pScanner);
        }
        pScanner.yybegin(SpecctraDsnStreamReader.NAME);
        nextToken = pScanner.nextToken();
      }
      // Read the pin name.
      if (!(nextToken instanceof String) && !(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Package.read_pin_info: String or Integer expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      String pinName = nextToken.toString();

      double[] pinCoor = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = pScanner.nextToken();
        if (nextToken instanceof Double double1) {
          pinCoor[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          pinCoor[i] = integer;
        } else {
          FRLogger.warn(
              "Package.read_pin_info: number expected at '" + pScanner.getScopeIdentifier() + "'");
          return null;
        }
      }
      // Handle scopes at the end of the pin scope.
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = pScanner.nextToken();

        if (nextToken == null) {
          FRLogger.warn(
              "Package.read_pin_info: unexpected end of file at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.ROTATE) {
            rotation = readRotation(pScanner);
          } else {
            ScopeKeyword.skipScope(pScanner);
          }
        }
      }
      return new PinInfo(padstackName, pinName, pinCoor, rotation);
    } catch (IOException e) {
      FRLogger.error("Package.read_pin_info: IO error while scanning file", e);
      return null;
    }
  }

  private static double readRotation(IJFlexScanner pScanner) {
    double result = 0;

    try {
      String nextString = pScanner.nextString();
      result = Double.parseDouble(nextString);

      // Overread The closing bracket.
      Object nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Package.read_rotation: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
      }
    } catch (IOException e) {
      FRLogger.error("Package.read_rotation: IO error while scanning file", e);
    }

    return result;
  }

  /** Writes the placements of p_package to a Specctra dsn-file. */
  public static void writePlacementScope(
      WriteScopeParameter pPar, app.freerouting.core.Package pPackage) throws IOException {
    Collection<Item> boardItems = pPar.board.getItems();
    boolean componentFound = false;
    for (int i = 1; i <= pPar.board.components.count(); i++) {
      app.freerouting.board.Component currComponent = pPar.board.components.get(i);
      if (currComponent.getPackage() == pPackage) {
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
            pPar.file.startScope();
            pPar.file.write("component ");
            pPar.identifierType.write(pPackage.name, pPar.file);
            componentFound = true;
          }
          Component.writeScope(pPar, currComponent);
        }
      }
    }
    if (componentFound) {
      pPar.file.endScope();
    }
  }

  private static boolean readPlacementSide(IJFlexScanner pScanner) throws IOException {
    Object nextToken = pScanner.nextToken();
    boolean result = nextToken != Keyword.BACK;

    nextToken = pScanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "Package.read_placement_side: closing bracket expected at '"
              + pScanner.getScopeIdentifier()
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

    PinInfo(String pPadstackName, String pPinName, double[] pRelCoor, double pRotation) {
      padstackName = pPadstackName;
      pinName = pPinName;
      relCoor = pRelCoor;
      rotation = pRotation;
    }
  }
}
