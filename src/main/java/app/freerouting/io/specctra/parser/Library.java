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

  public static void writeScope(WriteScopeParameter scopeParameter) throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("library");

    if (scopeParameter.board.library.packages != null) {
      for (int i = 1; i <= scopeParameter.board.library.packages.count(); i++) {
        Package.writeScope(scopeParameter, scopeParameter.board.library.packages.get(i));
      }
    }

    if (scopeParameter.board.library.padstacks != null) {
      for (int i = 1; i <= scopeParameter.board.library.padstacks.count(); i++) {
        writePadstackScope(scopeParameter, scopeParameter.board.library.padstacks.get(i));
      }
    }

    scopeParameter.file.endScope();
  }

  public static void writePadstackScope(WriteScopeParameter scopeParameter, Padstack padstack)
      throws IOException {
    // search the layer range of the padstack
    int firstLayerNo = 0;
    while (firstLayerNo < scopeParameter.board.getLayerCount()
        && padstack.getShape(firstLayerNo) == null) {
      ++firstLayerNo;
    }
    int lastLayerNo = scopeParameter.board.getLayerCount() - 1;
    while (lastLayerNo >= 0 && padstack.getShape(lastLayerNo) == null) {
      --lastLayerNo;
    }
    if (firstLayerNo >= scopeParameter.board.getLayerCount() || lastLayerNo < 0) {
      FRLogger.warn(
          "Library.write_padstack_scope: padstack shape not found at '" + padstack.name + "'");
      return;
    }

    scopeParameter.file.startScope();
    scopeParameter.file.write("padstack ");
    scopeParameter.identifierType.write(padstack.name, scopeParameter.file);
    for (int i = firstLayerNo; i <= lastLayerNo; i++) {
      app.freerouting.geometry.planar.Shape currentBoardShape = padstack.getShape(i);
      if (currentBoardShape == null) {
        continue;
      }
      app.freerouting.board.Layer boardLayer = scopeParameter.board.layerStructure.layers[i];
      final Layer currentLayer = new Layer(boardLayer.name, i, boardLayer.isSignal);
      Shape currentShape =
          scopeParameter.coordinateTransform.boardToDsnRel(currentBoardShape, currentLayer);
      scopeParameter.file.startScope();
      scopeParameter.file.write("shape");
      currentShape.writeScope(scopeParameter.file, scopeParameter.identifierType);
      scopeParameter.file.endScope();
    }
    if (!padstack.attachAllowed) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(attach off)");
    }
    if (padstack.placedAbsolute) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(absolute on)");
    }
    scopeParameter.file.endScope();
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
            Shape currentShape = Shape.readScope(scanner, layerStructure);
            if (currentShape != null) {
              shapeList.add(currentShape);
            }
            // overread the closing bracket and unknown scopes.
            Object currentNextToken = scanner.nextToken();
            while (currentNextToken == Keyword.OPEN_BRACKET) {
              ScopeKeyword.skipScope(scanner);
              currentNextToken = scanner.nextToken();
            }
            if (currentNextToken != Keyword.CLOSED_BRACKET) {
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
    ConvexShape[] padstackShapes = new ConvexShape[layerStructure.layers.length];
    for (Shape padShape : shapeList) {
      app.freerouting.geometry.planar.Shape currentShape =
          padShape.transformToBoardRel(coordinateTransform);
      ConvexShape convexShape;
      if (currentShape instanceof ConvexShape shape1) {
        convexShape = shape1;
      } else {
        if (currentShape instanceof PolygonShape shape) {
          currentShape = shape.convexHull();
        }
        TileShape[] convexShapes = currentShape.splitToConvex();
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
  public boolean readScope(ReadScopeParameter scopeParameter) {
    RoutingBoard board = scopeParameter.boardHandling.getRoutingBoard();
    board.library.padstacks =
        new Padstacks(scopeParameter.boardHandling.getRoutingBoard().layerStructure);
    Collection<Package> packageList = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scopeParameter.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Library.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Library.read_scope: unexpected end of file at '"
                + scopeParameter.scanner.getScopeIdentifier()
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
              scopeParameter.scanner,
              scopeParameter.layerStructure,
              scopeParameter.coordinateTransform,
              board.library.padstacks)) {
            return false;
          }
        } else if (nextToken == Keyword.IMAGE) {
          Package currentPackage =
              Package.readScope(scopeParameter.scanner, scopeParameter.layerStructure);
          if (currentPackage == null) {
            return false;
          }
          packageList.add(currentPackage);
        } else {
          skipScope(scopeParameter.scanner);
        }
      }
    }

    // Create the library packages on the board
    board.library.packages = new Packages(board.library.padstacks);
    for (Package currentPackage : packageList) {
      app.freerouting.core.library.Package.Pin[] pins =
          new app.freerouting.core.library.Package.Pin[currentPackage.pinInfoArr.length];
      for (int i = 0; i < pins.length; i++) {
        Package.PinInfo pinInfo = currentPackage.pinInfoArr[i];
        int relX =
            (int) Math.round(scopeParameter.coordinateTransform.dsnToBoard(pinInfo.relCoor[0]));
        int relY =
            (int) Math.round(scopeParameter.coordinateTransform.dsnToBoard(pinInfo.relCoor[1]));
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
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        pins[i] =
            new app.freerouting.core.library.Package.Pin(
                pinInfo.pinName, boardPadstack.no, relCoor, pinInfo.rotation);
      }
      app.freerouting.geometry.planar.Shape[] outlines =
          new app.freerouting.geometry.planar.Shape[currentPackage.outline.size()];
      double[] outlineWidths = new double[currentPackage.outline.size()];
      boolean[] outlineIsClosed = new boolean[currentPackage.outline.size()];

      Iterator<Shape> it3 = currentPackage.outline.iterator();
      for (int i = 0; i < outlines.length; i++) {
        Shape currentShape = it3.next();
        if (currentShape != null) {
          outlines[i] = currentShape.transformToBoardRel(scopeParameter.coordinateTransform);
          if (currentShape instanceof Path path) {
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
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
        }
      }
      generateMissingKeepoutNames("keepout_", currentPackage.keepouts);
      generateMissingKeepoutNames("via_keepout_", currentPackage.viaKeepouts);
      generateMissingKeepoutNames("place_keepout_", currentPackage.placeKeepouts);
      app.freerouting.core.library.Package.Keepout[] keepouts =
          new app.freerouting.core.library.Package.Keepout[currentPackage.keepouts.size()];
      Iterator<Shape.ReadAreaScopeResult> it2 = currentPackage.keepouts.iterator();
      for (int i = 0; i < keepouts.length; i++) {
        Shape.ReadAreaScopeResult currentKeepout = it2.next();
        final Layer currentLayer = currentKeepout.shapeList.iterator().next().layer;
        Area currentArea =
            Shape.transformAreaToBoardRel(
                currentKeepout.shapeList, scopeParameter.coordinateTransform);
        keepouts[i] =
            new app.freerouting.core.library.Package.Keepout(
                currentKeepout.areaName, currentArea, currentLayer.no);
      }
      app.freerouting.core.library.Package.Keepout[] viaKeepouts =
          new app.freerouting.core.library.Package.Keepout[currentPackage.viaKeepouts.size()];
      it2 = currentPackage.viaKeepouts.iterator();
      for (int i = 0; i < viaKeepouts.length; i++) {
        Shape.ReadAreaScopeResult currentKeepout = it2.next();
        final Layer currentLayer = (currentKeepout.shapeList.iterator().next()).layer;
        Area currentArea =
            Shape.transformAreaToBoardRel(
                currentKeepout.shapeList, scopeParameter.coordinateTransform);
        viaKeepouts[i] =
            new app.freerouting.core.library.Package.Keepout(
                currentKeepout.areaName, currentArea, currentLayer.no);
      }
      app.freerouting.core.library.Package.Keepout[] placeKeepoutArr =
          new app.freerouting.core.library.Package.Keepout[currentPackage.placeKeepouts.size()];
      it2 = currentPackage.placeKeepouts.iterator();
      for (int i = 0; i < placeKeepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currentKeepout = it2.next();
        final Layer currentLayer = (currentKeepout.shapeList.iterator().next()).layer;
        Area currentArea =
            Shape.transformAreaToBoardRel(
                currentKeepout.shapeList, scopeParameter.coordinateTransform);
        placeKeepoutArr[i] =
            new app.freerouting.core.library.Package.Keepout(
                currentKeepout.areaName, currentArea, currentLayer.no);
      }
      String basePackageName =
          currentPackage.name != null ? currentPackage.name.replaceAll("::\\d+$", "") : "Package";
      int suffix = 0;
      while (true) {
        String testName = suffix == 0 ? basePackageName : basePackageName + "::" + suffix;
        try {
          app.freerouting.core.library.Package existingPkg =
              board.library.packages.get(testName, currentPackage.isFront);
          if (existingPkg == null || !existingPkg.name.equalsIgnoreCase(testName)) {
            board.library.packages.add(
                testName,
                pins,
                outlines,
                outlineWidths,
                outlineIsClosed,
                keepouts,
                viaKeepouts,
                placeKeepoutArr,
                currentPackage.isFront);
            break;
          } else {
            if (arePackagePinsIdentical(existingPkg, pins)) {
              break;
            }
          }
        } catch (Exception e) {
          FRLogger.error("Library.read_scope package deduplication error, falling back", e);
          board.library.packages.add(
              currentPackage.name,
              pins,
              outlines,
              outlineWidths,
              outlineIsClosed,
              keepouts,
              viaKeepouts,
              placeKeepoutArr,
              currentPackage.isFront);
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
    for (Shape.ReadAreaScopeResult currentKeepout : keepoutList) {
      if (currentKeepout.areaName == null) {
        allNamesExisting = false;
        break;
      }
    }
    if (allNamesExisting) {
      return;
    }
    // generate names
    int currentNameIndex = 1;
    for (Shape.ReadAreaScopeResult currentKeepout : keepoutList) {
      currentKeepout.areaName = keepoutType + currentNameIndex;
      ++currentNameIndex;
    }
  }
}
