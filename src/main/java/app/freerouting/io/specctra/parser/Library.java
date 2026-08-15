package app.freerouting.io.specctra.parser;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for reading and writing library scopes from dsn-files. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class Library extends ScopeKeyword {

  /** Creates a new instance of Library. */
  public Library() {
    super("library");
  }

  public static void writeScope(WriteScopeParameter par) throws IOException {
    par.file.startScope();
    par.file.write("library");

    if (par.board.library.packages != null) {
      for (int i = 1; i <= par.board.library.packages.count(); i++) {
        Package.writeScope(par, par.board.library.packages.get(i));
      }
    }

    if (par.board.library.padstacks != null) {
      for (int i = 1; i <= par.board.library.padstacks.count(); i++) {
        writePadstackScope(par, par.board.library.padstacks.get(i));
      }
    }

    par.file.endScope();
  }

  public static void writePadstackScope(WriteScopeParameter par, Padstack padstack)
      throws IOException {
    // search the layer range of the padstack
    int firstLayerNo = 0;
    while (firstLayerNo < par.board.getLayerCount() && padstack.getShape(firstLayerNo) == null) {
      ++firstLayerNo;
    }
    int lastLayerNo = par.board.getLayerCount() - 1;
    while (lastLayerNo >= 0 && padstack.getShape(lastLayerNo) == null) {
      --lastLayerNo;
    }
    if (firstLayerNo >= par.board.getLayerCount() || lastLayerNo < 0) {
      FRLogger.warn(
          "Library.write_padstack_scope: padstack shape not found at '" + padstack.name + "'");
      return;
    }

    par.file.startScope();
    par.file.write("padstack ");
    par.identifierType.write(padstack.name, par.file);
    for (int i = firstLayerNo; i <= lastLayerNo; i++) {
      app.freerouting.geometry.planar.Shape currBoardShape = padstack.getShape(i);
      if (currBoardShape == null) {
        continue;
      }
      app.freerouting.board.Layer boardLayer = par.board.layerStructure.arr[i];
      final Layer currLayer = new Layer(boardLayer.name, i, boardLayer.isSignal);
      Shape currShape = par.coordinateTransform.boardToDsnRel(currBoardShape, currLayer);
      par.file.startScope();
      par.file.write("shape");
      currShape.writeScope(par.file, par.identifierType);
      par.file.endScope();
    }
    if (!padstack.attachAllowed) {
      par.file.newLine();
      par.file.write("(attach off)");
    }
    if (padstack.placedAbsolute) {
      par.file.newLine();
      par.file.write("(absolute on)");
    }
    par.file.endScope();
  }

  public static boolean readPadstackScope(
      IJFlexScanner scanner,
      LayerStructure layerStructure,
      CoordinateTransform coordinateTransform,
      Padstacks boardPadstacks) {
    String padstackName;
    boolean isDrilllable = true;
    boolean placedAbsolute = false;
    Collection<Shape> shapeList = new LinkedList<>();
    try {
      Object nextToken = scanner.nextToken();
      if (nextToken instanceof String string) {
        padstackName = string.replaceAll("\\.\\d+", "");
        scanner.setScopeIdentifier(padstackName);
      } else {
        FRLogger.warn(
            "Library.read_padstack_scope: unexpected padstack identifier at '"
                + scanner.getScopeIdentifier()
                + "'");
        return false;
      }

      while (nextToken != Keyword.CLOSED_BRACKET) {
        Object prevToken = nextToken;
        nextToken = scanner.nextToken();
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.SHAPE) {
            Shape currShape = Shape.readScope(scanner, layerStructure);
            if (currShape != null) {
              shapeList.add(currShape);
            }
            // overread the closing bracket and unknown scopes.
            Object currNextToken = scanner.nextToken();
            while (currNextToken == Keyword.OPEN_BRACKET) {
              ScopeKeyword.skipScope(scanner);
              currNextToken = scanner.nextToken();
            }
            if (currNextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "Library.read_padstack_scope: closing bracket expected at '"
                      + scanner.getScopeIdentifier()
                      + "'");
              return false;
            }
          } else if (nextToken == Keyword.ATTACH) {
            isDrilllable = DsnFile.readOnOffScope(scanner);
          } else if (nextToken == Keyword.ABSOLUTE) {
            placedAbsolute = DsnFile.readOnOffScope(scanner);
          } else {
            ScopeKeyword.skipScope(scanner);
          }
        }
      }
    } catch (IOException e) {
      FRLogger.error("Library.read_padstack_scope: IO error scanning file", e);
      return false;
    }
    if (boardPadstacks.get(padstackName) != null) {
      // Padstack exists already
      return true;
    }
    if (shapeList.isEmpty()) {
      FRLogger.warn(
          "Library.read_padstack_scope: shape not found for padstack with name '"
              + padstackName
              + "'");
      return true;
    }
    ConvexShape[] padstackShapes = new ConvexShape[layerStructure.arr.length];
    for (Shape padShape : shapeList) {
      app.freerouting.geometry.planar.Shape currShape =
          padShape.transformToBoardRel(coordinateTransform);
      ConvexShape convexShape;
      if (currShape instanceof ConvexShape shape1) {
        convexShape = shape1;
      } else {
        if (currShape instanceof PolygonShape shape) {
          currShape = shape.convexHull();
        }
        TileShape[] convexShapes = currShape.splitToConvex();
        if (convexShapes.length != 1) {
          FRLogger.warn(
              "Library.read_padstack_scope: convex shape expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
        }
        convexShape = convexShapes[0];
        if (convexShape instanceof Simplex simplex) {
          convexShape = simplex.simplify();
        }
      }
      ConvexShape padstackShape = convexShape;
      if (padstackShape != null) {
        if (padstackShape.dimension() < 2) {
          FRLogger.warn(
              "Library.read_padstack_scope: the shape of padstack '"
                  + padstackName
                  + "' is not an area. We will enlarge it as a workaround, but it may "
                  + "result in unintended consequences.");
          // enlarge the shape a little bit, so that it is an area
          padstackShape = padstackShape.offset(1);
          if (padstackShape.dimension() < 2) {
            padstackShape = null;
          }
        }
      }

      if (padShape.layer == Layer.PCB || padShape.layer == Layer.SIGNAL) {
        Arrays.fill(padstackShapes, padstackShape);
      } else {
        int shapeLayer = layerStructure.getNo(padShape.layer.name);
        if (shapeLayer < 0 || shapeLayer >= padstackShapes.length) {
          FRLogger.warn(
              "Library.read_padstack_scope: layer number found at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        padstackShapes[shapeLayer] = padstackShape;
      }
    }
    boardPadstacks.add(padstackName, padstackShapes, isDrilllable, placedAbsolute);
    return true;
  }

  private static boolean arePackagePinsIdentical(
      app.freerouting.core.library.Package pkg1, app.freerouting.core.library.Package.Pin[] p2) {
    if (pkg1 == null || p2 == null) {
      return (pkg1 == null) == (p2 == null);
    }
    if (pkg1.pinCount() != p2.length) {
      return false;
    }
    for (int i = 0; i < p2.length; i++) {
      app.freerouting.core.library.Package.Pin pin1 = pkg1.getPin(i);
      app.freerouting.core.library.Package.Pin pin2 = p2[i];
      if (pin1 == null || pin2 == null) {
        if (pin1 != pin2) {
          return false;
        }
        continue;
      }
      if (!pin1.name.equals(pin2.name)) {
        return false;
      }
      if (pin1.padstackNo != pin2.padstackNo) {
        return false;
      }
      app.freerouting.geometry.planar.FloatPoint loc1 = pin1.relativeLocation.toFloat();
      app.freerouting.geometry.planar.FloatPoint loc2 = pin2.relativeLocation.toFloat();
      if (Math.abs(loc1.x - loc2.x) > 0.001 || Math.abs(loc1.y - loc2.y) > 0.001) {
        return false;
      }
      if (Math.abs(pin1.rotationInDegree - pin2.rotationInDegree) > 0.001) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean readScope(ReadScopeParameter par) {
    RoutingBoard board = par.boardHandling.getRoutingBoard();
    board.library.padstacks = new Padstacks(par.boardHandling.getRoutingBoard().layerStructure);
    Collection<Package> packageList = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Library.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Library.read_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.PADSTACK) {
          if (!readPadstackScope(
              par.scanner, par.layerStructure, par.coordinateTransform, board.library.padstacks)) {
            return false;
          }
        } else if (nextToken == Keyword.IMAGE) {
          Package currPackage = Package.readScope(par.scanner, par.layerStructure);
          if (currPackage == null) {
            return false;
          }
          packageList.add(currPackage);
        } else {
          skipScope(par.scanner);
        }
      }
    }

    // Create the library packages on the board
    board.library.packages = new Packages(board.library.padstacks);
    for (Package currPackage : packageList) {
      app.freerouting.core.library.Package.Pin[] pinArr =
          new app.freerouting.core.library.Package.Pin[currPackage.pinInfoArr.length];
      for (int i = 0; i < pinArr.length; i++) {
        Package.PinInfo pinInfo = currPackage.pinInfoArr[i];
        int relX = (int) Math.round(par.coordinateTransform.dsnToBoard(pinInfo.relCoor[0]));
        int relY = (int) Math.round(par.coordinateTransform.dsnToBoard(pinInfo.relCoor[1]));
        Vector relCoor = new IntVector(relX, relY);
        String cleanedLookupName =
            pinInfo.padstackName != null ? pinInfo.padstackName.replaceAll("\\.\\d+", "") : null;
        Padstack boardPadstack = board.library.padstacks.get(cleanedLookupName);
        if (boardPadstack == null) {
          FRLogger.warn(
              "Library.read_scope: board padstack '"
                  + pinInfo.padstackName
                  + "' (cleaned: '"
                  + cleanedLookupName
                  + "') not found at '"
                  + par.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        pinArr[i] =
            new app.freerouting.core.library.Package.Pin(
                pinInfo.pinName, boardPadstack.no, relCoor, pinInfo.rotation);
      }
      app.freerouting.geometry.planar.Shape[] outlineArr =
          new app.freerouting.geometry.planar.Shape[currPackage.outline.size()];
      double[] outlineWidths = new double[currPackage.outline.size()];
      boolean[] outlineIsClosed = new boolean[currPackage.outline.size()];

      Iterator<Shape> it3 = currPackage.outline.iterator();
      for (int i = 0; i < outlineArr.length; i++) {
        Shape currShape = it3.next();
        if (currShape != null) {
          outlineArr[i] = currShape.transformToBoardRel(par.coordinateTransform);
          if (currShape instanceof Path path) {
            outlineWidths[i] = path.width;
            double[] coords = path.coordinateArr;
            if (coords.length >= 4) {
              outlineIsClosed[i] =
                  coords[0] == coords[coords.length - 2] && coords[1] == coords[coords.length - 1];
            }
          } else {
            outlineWidths[i] = 0.0;
            outlineIsClosed[i] = true; // Non-path shapes (polygons/rects) are closed
          }
        } else {
          FRLogger.warn(
              "Library.read_scope: outline shape is null at '"
                  + par.scanner.getScopeIdentifier()
                  + "'");
        }
      }
      generateMissingKeepoutNames("keepout_", currPackage.keepouts);
      generateMissingKeepoutNames("via_keepout_", currPackage.viaKeepouts);
      generateMissingKeepoutNames("place_keepout_", currPackage.placeKeepouts);
      app.freerouting.core.library.Package.Keepout[] keepoutArr =
          new app.freerouting.core.library.Package.Keepout[currPackage.keepouts.size()];
      Iterator<Shape.ReadAreaScopeResult> it2 = currPackage.keepouts.iterator();
      for (int i = 0; i < keepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currKeepout = it2.next();
        final Layer currLayer = currKeepout.shapeList.iterator().next().layer;
        Area currArea =
            Shape.transformAreaToBoardRel(currKeepout.shapeList, par.coordinateTransform);
        keepoutArr[i] =
            new app.freerouting.core.library.Package.Keepout(
                currKeepout.areaName, currArea, currLayer.no);
      }
      app.freerouting.core.library.Package.Keepout[] viaKeepoutArr =
          new app.freerouting.core.library.Package.Keepout[currPackage.viaKeepouts.size()];
      it2 = currPackage.viaKeepouts.iterator();
      for (int i = 0; i < viaKeepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currKeepout = it2.next();
        final Layer currLayer = (currKeepout.shapeList.iterator().next()).layer;
        Area currArea =
            Shape.transformAreaToBoardRel(currKeepout.shapeList, par.coordinateTransform);
        viaKeepoutArr[i] =
            new app.freerouting.core.library.Package.Keepout(
                currKeepout.areaName, currArea, currLayer.no);
      }
      app.freerouting.core.library.Package.Keepout[] placeKeepoutArr =
          new app.freerouting.core.library.Package.Keepout[currPackage.placeKeepouts.size()];
      it2 = currPackage.placeKeepouts.iterator();
      for (int i = 0; i < placeKeepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currKeepout = it2.next();
        final Layer currLayer = (currKeepout.shapeList.iterator().next()).layer;
        Area currArea =
            Shape.transformAreaToBoardRel(currKeepout.shapeList, par.coordinateTransform);
        placeKeepoutArr[i] =
            new app.freerouting.core.library.Package.Keepout(
                currKeepout.areaName, currArea, currLayer.no);
      }
      String basePackageName =
          currPackage.name != null ? currPackage.name.replaceAll("::\\d+$", "") : "Package";
      int suffix = 0;
      while (true) {
        String testName = suffix == 0 ? basePackageName : basePackageName + "::" + suffix;
        try {
          app.freerouting.core.library.Package existingPkg =
              board.library.packages.get(testName, currPackage.isFront);
          if (existingPkg == null || !existingPkg.name.equalsIgnoreCase(testName)) {
            board.library.packages.add(
                testName,
                pinArr,
                outlineArr,
                outlineWidths,
                outlineIsClosed,
                keepoutArr,
                viaKeepoutArr,
                placeKeepoutArr,
                currPackage.isFront);
            break;
          } else {
            if (arePackagePinsIdentical(existingPkg, pinArr)) {
              break;
            }
          }
        } catch (Exception e) {
          FRLogger.error("Library.read_scope package deduplication error, falling back", e);
          board.library.packages.add(
              currPackage.name,
              pinArr,
              outlineArr,
              outlineWidths,
              outlineIsClosed,
              keepoutArr,
              viaKeepoutArr,
              placeKeepoutArr,
              currPackage.isFront);
          break;
        }
        suffix++;
      }
    }
    return true;
  }

  private void generateMissingKeepoutNames(
      String keepoutType, Collection<Shape.ReadAreaScopeResult> keepoutList) {
    boolean allNamesExisting = true;
    for (Shape.ReadAreaScopeResult currKeepout : keepoutList) {
      if (currKeepout.areaName == null) {
        allNamesExisting = false;
        break;
      }
    }
    if (allNamesExisting) {
      return;
    }
    // generate names
    int currNameIndex = 1;
    for (Shape.ReadAreaScopeResult currKeepout : keepoutList) {
      currKeepout.areaName = keepoutType + currNameIndex;
      ++currNameIndex;
    }
  }
}
