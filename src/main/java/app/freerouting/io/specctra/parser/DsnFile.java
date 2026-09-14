package app.freerouting.io.specctra.parser;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/** Class for reading and writing dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public final class DsnFile {

  static final char CLASS_CLEARANCE_SEPARATOR = '-';

  private DsnFile() {}

  /**
   * Sets containsPlane to true for nets with a conductionArea covering a large part of a signal
   * layer, if that layer does not contain any traces. This is useful in case the layer type was not
   * set correctly to plane in the dsn-file. Returns true, if something was changed.
   *
   * <p>Called from {@link app.freerouting.io.specctra.DsnReader#readBoard} when the DSN file
   * contains no {@code (autoroute ...)} scope.
   */
  public static boolean adjustPlaneAutorouteSettings(BasicBoard routingBoard) {
    if (routingBoard == null) {
      return false;
    }
    final app.freerouting.board.model.structure.LayerStructure boardLayerStructure =
        routingBoard.layerStructure;
    if (boardLayerStructure.layers.length < 1) {
      return false;
    }
    for (app.freerouting.board.model.structure.Layer currentLayer : boardLayerStructure.layers) {
      if (!currentLayer.isSignal) {
        return false;
      }
    }
    boolean[] changedLayerArr = new boolean[boardLayerStructure.layers.length];
    Collection<ConductionArea> conductionAreaList = routingBoard.getConductionAreas();
    boolean nothingChanged = true;

    BoardOutline boardOutline = routingBoard.getOutline();
    double boardArea = 0;
    if (boardOutline != null) {
      for (int i = 0; i < boardOutline.shapeCount(); i++) {
        TileShape[] currentPieceArr = boardOutline.getShape(i).splitToConvex();
        if (currentPieceArr != null) {
          for (TileShape currentPiece : currentPieceArr) {
            boardArea += currentPiece.area();
          }
        }
      }
    }
    if (boardArea <= 0) {
      return false;
    }

    for (ConductionArea currentConductionArea : conductionAreaList) {
      int layerIndex = currentConductionArea.getLayer();
      if (layerIndex < 0 || layerIndex >= boardLayerStructure.layers.length) {
        continue;
      }
      final app.freerouting.board.model.structure.Layer currentLayer =
          routingBoard.layerStructure.layers[layerIndex];
      if (!currentLayer.isSignal) {
        continue;
      }
      TileShape[] convexPieces = currentConductionArea.getArea().splitToConvex();
      if (convexPieces == null) {
        continue;
      }
      double currentArea = 0;
      for (TileShape currentPiece : convexPieces) {
        currentArea += currentPiece.area();
      }
      // Relaxed area threshold: 30% of board area (supports outer-layer pours on 2-layer boards)
      if (currentArea < 0.3 * boardArea) {
        continue;
      }
      for (int i = 0; i < currentConductionArea.netCount(); i++) {
        final Net currentNet = routingBoard.rules.nets.get(currentConductionArea.getNetNumber(i));
        if (currentNet != null) {
          currentNet.setContainsPlane(true);
          nothingChanged = false;
        }
      }
      changedLayerArr[layerIndex] = true;
      if (currentConductionArea.getFixedState().ordinal() < FixedState.USER_FIXED.ordinal()) {
        currentConductionArea.setFixedState(FixedState.USER_FIXED);
      }
    }
    for (int i = 0; i < changedLayerArr.length; i++) {
      if (changedLayerArr[i]) {
        FRLogger.info(
            "Layer '"
                + routingBoard.layerStructure.layers[i].name
                + "' contains a power plane / large copper pour covering >=30% of the board; "
                + "associated net(s) configured for plane routing.");
      }
    }
    return !nothingChanged;
  }

  static boolean readOnOffScope(IJFlexScanner scanner) {
    try {
      Object nextToken = scanner.nextToken();
      boolean result = false;
      if (nextToken == Keyword.ON) {
        result = true;
      } else if (nextToken != Keyword.OFF) {
        FRLogger.warn(
            "DsnFile.read_boolean: Keyword.OFF expected at '" + scanner.getScopeIdentifier() + "'");
      }
      ScopeKeyword.skipScope(scanner);
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_boolean: IO error scanning file", e);
      return false;
    }
  }

  static int readIntegerScope(IJFlexScanner scanner) {
    try {
      int value;
      Object nextToken = scanner.nextToken();
      if (nextToken instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn(
            "DsnFile.read_integer_scope: number expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return 0;
      }
      nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_integer_scope: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_integer_scope: IO error scanning file", e);
      return 0;
    }
  }

  static double readFloatScope(IJFlexScanner scanner) {
    try {
      double value;
      Object nextToken = scanner.nextToken();
      if (nextToken instanceof Double double1) {
        value = double1;
      } else if (nextToken instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn(
            "DsnFile.read_float_scope: number expected at '" + scanner.getScopeIdentifier() + "'");
        return 0;
      }
      nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_float_scope: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_float_scope: IO error scanning file", e);
      return 0;
    }
  }

  public static String readStringScope(IJFlexScanner scanner) {
    try {
      String result = scanner.nextString(true);
      if (result == null) {
        return null;
      }
      Object nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_string_scope: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "', got: "
                + nextToken);
        while (nextToken != null && nextToken != Keyword.CLOSED_BRACKET) {
          nextToken = scanner.nextToken();
        }
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  public static String[] readStringListScope(IJFlexScanner scanner) {
    String[] result = scanner.nextStringList();
    if (!scanner.nextClosingBracket()) {
      return null;
    }
    return result;
  }

  public enum ReadResult {
    OK,
    OUTLINE_MISSING,
    ERROR
  }
}
