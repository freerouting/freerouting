package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
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
    final app.freerouting.board.LayerStructure boardLayerStructure = routingBoard.layerStructure;
    if (boardLayerStructure.arr.length <= 2) {
      return false;
    }
    for (app.freerouting.board.Layer currentLayer : boardLayerStructure.arr) {
      if (!currentLayer.isSignal) {
        return false;
      }
    }
    boolean[] layerContainsWiresArr = new boolean[boardLayerStructure.arr.length];
    boolean[] changedLayerArr = new boolean[boardLayerStructure.arr.length];
    for (int i = 0; i < layerContainsWiresArr.length; i++) {
      layerContainsWiresArr[i] = false;
      changedLayerArr[i] = false;
    }
    Collection<ConductionArea> conductionAreaList = new LinkedList<>();
    Collection<Item> itemList = routingBoard.getItems();
    for (Item currentItem : itemList) {
      if (currentItem instanceof Trace trace) {
        final int currentLayer = trace.getLayer();
        layerContainsWiresArr[currentLayer] = true;
      } else if (currentItem instanceof ConductionArea area) {
        conductionAreaList.add(area);
      }
    }
    boolean nothingChanged = true;

    BoardOutline boardOutline = routingBoard.getOutline();
    double boardArea = 0;
    for (int i = 0; i < boardOutline.shapeCount(); i++) {
      TileShape[] currentPieceArr = boardOutline.getShape(i).splitToConvex();
      if (currentPieceArr != null) {
        for (TileShape currentPiece : currentPieceArr) {
          boardArea += currentPiece.area();
        }
      }
    }
    for (ConductionArea currentConductionArea : conductionAreaList) {
      int layerNo = currentConductionArea.getLayer();
      if (layerContainsWiresArr[layerNo]) {
        continue;
      }
      final app.freerouting.board.Layer currentLayer = routingBoard.layerStructure.arr[layerNo];
      if (!currentLayer.isSignal || layerNo == 0 || layerNo == boardLayerStructure.arr.length - 1) {
        continue;
      }
      TileShape[] convexPieces = currentConductionArea.getArea().splitToConvex();
      double currentArea = 0;
      for (TileShape currentPiece : convexPieces) {
        currentArea += currentPiece.area();
      }
      if (currentArea < 0.5 * boardArea) {
        continue;
      }
      for (int i = 0; i < currentConductionArea.netCount(); i++) {
        final Net currentNet = routingBoard.rules.nets.get(currentConductionArea.getNetNo(i));
        currentNet.setContainsPlane(true);
        nothingChanged = false;
      }
      changedLayerArr[layerNo] = true;
      if (currentConductionArea.getFixedState().ordinal() < FixedState.USER_FIXED.ordinal()) {
        currentConductionArea.setFixedState(FixedState.USER_FIXED);
      }
    }
    for (int i = 0; i < changedLayerArr.length; i++) {
      if (changedLayerArr[i]) {
        FRLogger.info(
            "Layer '"
                + routingBoard.layerStructure.arr[i].name
                + "' has been automatically configured as a dedicated power plane because it "
                + "contains a large conduction area covering >50% of the board.");
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
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      String result = scanner.nextString();
      Object nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_string_scope: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
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
