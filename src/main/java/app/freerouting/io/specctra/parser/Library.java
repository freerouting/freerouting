package app.freerouting.io.specctra.parser;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.Packages;
import app.freerouting.core.Padstack;
import app.freerouting.core.Padstacks;
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
public class Library extends ScopeKeyword {

  /** Creates a new instance of Library */
  public Library() {
    super("library");
  }

  public static void write_scope(WriteScopeParameter p_par) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("library");

    if (p_par.board.library.packages != null) {
      for (int i = 1; i <= p_par.board.library.packages.count(); i++) {
        Package.write_scope(p_par, p_par.board.library.packages.get(i));
      }
    }

    if (p_par.board.library.padstacks != null) {
      for (int i = 1; i <= p_par.board.library.padstacks.count(); i++) {
        write_padstack_scope(p_par, p_par.board.library.padstacks.get(i));
      }
    }

    p_par.file.end_scope();
  }

  public static void write_padstack_scope(WriteScopeParameter p_par, Padstack p_padstack)
      throws IOException {
    // search the layer range of the padstack
    int firstLayerNo = 0;
    while (firstLayerNo < p_par.board.get_layer_count()
        && p_padstack.get_shape(firstLayerNo) == null) {
      ++firstLayerNo;
    }
    int lastLayerNo = p_par.board.get_layer_count() - 1;
    while (lastLayerNo >= 0 && p_padstack.get_shape(lastLayerNo) == null) {
      --lastLayerNo;
    }
    if (firstLayerNo >= p_par.board.get_layer_count() || lastLayerNo < 0) {
      FRLogger.warn(
          "Library.write_padstack_scope: padstack shape not found at '" + p_padstack.name + "'");
      return;
    }

    p_par.file.start_scope();
    p_par.file.write("padstack ");
    p_par.identifierType.write(p_padstack.name, p_par.file);
    for (int i = firstLayerNo; i <= lastLayerNo; i++) {
      app.freerouting.geometry.planar.Shape currBoardShape = p_padstack.get_shape(i);
      if (currBoardShape == null) {
        continue;
      }
      app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[i];
      Layer currLayer = new Layer(boardLayer.name, i, boardLayer.isSignal);
      Shape currShape = p_par.coordinateTransform.board_to_dsn_rel(currBoardShape, currLayer);
      p_par.file.start_scope();
      p_par.file.write("shape");
      currShape.write_scope(p_par.file, p_par.identifierType);
      p_par.file.end_scope();
    }
    if (!p_padstack.attachAllowed) {
      p_par.file.new_line();
      p_par.file.write("(attach off)");
    }
    if (p_padstack.placedAbsolute) {
      p_par.file.new_line();
      p_par.file.write("(absolute on)");
    }
    p_par.file.end_scope();
  }

  public static boolean read_padstack_scope(
      IJFlexScanner p_scanner,
      LayerStructure p_layer_structure,
      CoordinateTransform p_coordinate_transform,
      Padstacks p_board_padstacks) {
    String padstackName;
    boolean isDrilllable = true;
    boolean placedAbsolute = false;
    Collection<Shape> shapeList = new LinkedList<>();
    try {
      Object nextToken = p_scanner.next_token();
      if (nextToken instanceof String string) {
        padstackName = string.replaceAll("\\.\\d+", "");
        p_scanner.set_scope_identifier(padstackName);
      } else {
        FRLogger.warn(
            "Library.read_padstack_scope: unexpected padstack identifier at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return false;
      }

      while (nextToken != Keyword.CLOSED_BRACKET) {
        Object prevToken = nextToken;
        nextToken = p_scanner.next_token();
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.SHAPE) {
            Shape currShape = Shape.read_scope(p_scanner, p_layer_structure);
            if (currShape != null) {
              shapeList.add(currShape);
            }
            // overread the closing bracket and unknown scopes.
            Object currNextToken = p_scanner.next_token();
            while (currNextToken == Keyword.OPEN_BRACKET) {
              ScopeKeyword.skip_scope(p_scanner);
              currNextToken = p_scanner.next_token();
            }
            if (currNextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "Library.read_padstack_scope: closing bracket expected at '"
                      + p_scanner.get_scope_identifier()
                      + "'");
              return false;
            }
          } else if (nextToken == Keyword.ATTACH) {
            isDrilllable = DsnFile.read_on_off_scope(p_scanner);
          } else if (nextToken == Keyword.ABSOLUTE) {
            placedAbsolute = DsnFile.read_on_off_scope(p_scanner);
          } else {
            ScopeKeyword.skip_scope(p_scanner);
          }
        }
      }
    } catch (IOException e) {
      FRLogger.error("Library.read_padstack_scope: IO error scanning file", e);
      return false;
    }
    if (p_board_padstacks.get(padstackName) != null) {
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
    ConvexShape[] padstackShapes = new ConvexShape[p_layer_structure.arr.length];
    for (Shape padShape : shapeList) {
      app.freerouting.geometry.planar.Shape currShape =
          padShape.transform_to_board_rel(p_coordinate_transform);
      ConvexShape convexShape;
      if (currShape instanceof ConvexShape shape1) {
        convexShape = shape1;
      } else {
        if (currShape instanceof PolygonShape shape) {
          currShape = shape.convex_hull();
        }
        TileShape[] convexShapes = currShape.split_to_convex();
        if (convexShapes.length != 1) {
          FRLogger.warn(
              "Library.read_padstack_scope: convex shape expected at '"
                  + p_scanner.get_scope_identifier()
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
                  + "' is not an area. We will enlarge it as a workaround, but it may result unintended consequences.");
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
        int shapeLayer = p_layer_structure.get_no(padShape.layer.name);
        if (shapeLayer < 0 || shapeLayer >= padstackShapes.length) {
          FRLogger.warn(
              "Library.read_padstack_scope: layer number found at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return false;
        }
        padstackShapes[shapeLayer] = padstackShape;
      }
    }
    p_board_padstacks.add(padstackName, padstackShapes, isDrilllable, placedAbsolute);
    return true;
  }

  @Override
  public boolean read_scope(ReadScopeParameter p_par) {
    RoutingBoard board = p_par.boardHandling.get_routing_board();
    board.library.padstacks = new Padstacks(p_par.boardHandling.get_routing_board().layerStructure);
    Collection<Package> packageList = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Library.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Library.read_scope: unexpected end of file at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.PADSTACK) {
          if (!read_padstack_scope(
              p_par.scanner,
              p_par.layerStructure,
              p_par.coordinateTransform,
              board.library.padstacks)) {
            return false;
          }
        } else if (nextToken == Keyword.IMAGE) {
          Package currPackage = Package.read_scope(p_par.scanner, p_par.layerStructure);
          if (currPackage == null) {
            return false;
          }
          packageList.add(currPackage);
        } else {
          skip_scope(p_par.scanner);
        }
      }
    }

    // Create the library packages on the board
    board.library.packages = new Packages(board.library.padstacks);
    for (Package currPackage : packageList) {
      app.freerouting.core.Package.Pin[] pinArr =
          new app.freerouting.core.Package.Pin[currPackage.pinInfoArr.length];
      for (int i = 0; i < pinArr.length; i++) {
        Package.PinInfo pinInfo = currPackage.pinInfoArr[i];
        int relX = (int) Math.round(p_par.coordinateTransform.dsn_to_board(pinInfo.relCoor[0]));
        int relY = (int) Math.round(p_par.coordinateTransform.dsn_to_board(pinInfo.relCoor[1]));
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
                  + p_par.scanner.get_scope_identifier()
                  + "'");
          return false;
        }
        pinArr[i] =
            new app.freerouting.core.Package.Pin(
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
          outlineArr[i] = currShape.transform_to_board_rel(p_par.coordinateTransform);
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
                  + p_par.scanner.get_scope_identifier()
                  + "'");
        }
      }
      generate_missing_keepout_names("keepout_", currPackage.keepouts);
      generate_missing_keepout_names("via_keepout_", currPackage.viaKeepouts);
      generate_missing_keepout_names("place_keepout_", currPackage.placeKeepouts);
      app.freerouting.core.Package.Keepout[] keepoutArr =
          new app.freerouting.core.Package.Keepout[currPackage.keepouts.size()];
      Iterator<Shape.ReadAreaScopeResult> it2 = currPackage.keepouts.iterator();
      for (int i = 0; i < keepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currKeepout = it2.next();
        Layer currLayer = currKeepout.shapeList.iterator().next().layer;
        Area currArea =
            Shape.transform_area_to_board_rel(currKeepout.shapeList, p_par.coordinateTransform);
        keepoutArr[i] =
            new app.freerouting.core.Package.Keepout(currKeepout.areaName, currArea, currLayer.no);
      }
      app.freerouting.core.Package.Keepout[] viaKeepoutArr =
          new app.freerouting.core.Package.Keepout[currPackage.viaKeepouts.size()];
      it2 = currPackage.viaKeepouts.iterator();
      for (int i = 0; i < viaKeepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currKeepout = it2.next();
        Layer currLayer = (currKeepout.shapeList.iterator().next()).layer;
        Area currArea =
            Shape.transform_area_to_board_rel(currKeepout.shapeList, p_par.coordinateTransform);
        viaKeepoutArr[i] =
            new app.freerouting.core.Package.Keepout(currKeepout.areaName, currArea, currLayer.no);
      }
      app.freerouting.core.Package.Keepout[] placeKeepoutArr =
          new app.freerouting.core.Package.Keepout[currPackage.placeKeepouts.size()];
      it2 = currPackage.placeKeepouts.iterator();
      for (int i = 0; i < placeKeepoutArr.length; i++) {
        Shape.ReadAreaScopeResult currKeepout = it2.next();
        Layer currLayer = (currKeepout.shapeList.iterator().next()).layer;
        Area currArea =
            Shape.transform_area_to_board_rel(currKeepout.shapeList, p_par.coordinateTransform);
        placeKeepoutArr[i] =
            new app.freerouting.core.Package.Keepout(currKeepout.areaName, currArea, currLayer.no);
      }
      String basePackageName =
          currPackage.name != null ? currPackage.name.replaceAll("::\\d+$", "") : "Package";
      int suffix = 0;
      while (true) {
        String testName = suffix == 0 ? basePackageName : basePackageName + "::" + suffix;
        try {
          app.freerouting.core.Package existingPkg =
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

  private void generate_missing_keepout_names(
      String p_keepout_type, Collection<Shape.ReadAreaScopeResult> p_keepout_list) {
    boolean allNamesExisting = true;
    for (Shape.ReadAreaScopeResult currKeepout : p_keepout_list) {
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
    for (Shape.ReadAreaScopeResult currKeepout : p_keepout_list) {
      currKeepout.areaName = p_keepout_type + currNameIndex;
      ++currNameIndex;
    }
  }

  private static boolean arePackagePinsIdentical(
      app.freerouting.core.Package pkg1, app.freerouting.core.Package.Pin[] p2) {
    if (pkg1 == null || p2 == null) {
      return (pkg1 == null) == (p2 == null);
    }
    if (pkg1.pin_count() != p2.length) {
      return false;
    }
    for (int i = 0; i < p2.length; i++) {
      app.freerouting.core.Package.Pin pin1 = pkg1.get_pin(i);
      app.freerouting.core.Package.Pin pin2 = p2[i];
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
      app.freerouting.geometry.planar.FloatPoint loc1 = pin1.relativeLocation.to_float();
      app.freerouting.geometry.planar.FloatPoint loc2 = pin2.relativeLocation.to_float();
      if (Math.abs(loc1.x - loc2.x) > 0.001 || Math.abs(loc1.y - loc2.y) > 0.001) {
        return false;
      }
      if (Math.abs(pin1.rotationInDegree - pin2.rotationInDegree) > 0.001) {
        return false;
      }
    }
    return true;
  }
}
