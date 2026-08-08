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
    app.freerouting.board.LayerStructure boardLayerStructure = routingBoard.layerStructure;
    if (boardLayerStructure.arr.length <= 2) {
      return false;
    }
    for (app.freerouting.board.Layer currLayer : boardLayerStructure.arr) {
      if (!currLayer.isSignal) {
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
    for (Item currItem : itemList) {
      if (currItem instanceof Trace trace) {
        int currLayer = trace.getLayer();
        layerContainsWiresArr[currLayer] = true;
      } else if (currItem instanceof ConductionArea area) {
        conductionAreaList.add(area);
      }
    }
    boolean nothingChanged = true;

    BoardOutline boardOutline = routingBoard.getOutline();
    double boardArea = 0;
    for (int i = 0; i < boardOutline.shapeCount(); i++) {
      TileShape[] currPieceArr = boardOutline.getShape(i).splitToConvex();
      if (currPieceArr != null) {
        for (TileShape currPiece : currPieceArr) {
          boardArea += currPiece.area();
        }
      }
    }
    for (ConductionArea currConductionArea : conductionAreaList) {
      int layerNo = currConductionArea.getLayer();
      if (layerContainsWiresArr[layerNo]) {
        continue;
      }
      app.freerouting.board.Layer currLayer = routingBoard.layerStructure.arr[layerNo];
      if (!currLayer.isSignal || layerNo == 0 || layerNo == boardLayerStructure.arr.length - 1) {
        continue;
      }
      TileShape[] convexPieces = currConductionArea.getArea().splitToConvex();
      double currArea = 0;
      for (TileShape currPiece : convexPieces) {
        currArea += currPiece.area();
      }
      if (currArea < 0.5 * boardArea) {
        continue;
      }
      for (int i = 0; i < currConductionArea.netCount(); i++) {
        Net currentNet = routingBoard.rules.nets.get(currConductionArea.getNetNo(i));
        currentNet.setContainsPlane(true);
        nothingChanged = false;
      }
      changedLayerArr[layerNo] = true;
      if (currConductionArea.getFixedState().ordinal() < FixedState.USER_FIXED.ordinal()) {
        currConductionArea.setFixedState(FixedState.USER_FIXED);
      }
    }
    for (int i = 0; i < changedLayerArr.length; i++) {
      if (changedLayerArr[i]) {
        FRLogger.info(
            "Layer '"
                + routingBoard.layerStructure.arr[i].name
                + "' has been automatically configured as a dedicated power plane because it contains a large conduction area covering >50% of the board.");
      }
    }
    return !nothingChanged;
  }

  static boolean readOnOffScope(IJFlexScanner pScanner) {
    try {
      Object nextToken = pScanner.nextToken();
      boolean result = false;
      if (nextToken == Keyword.ON) {
        result = true;
      } else if (nextToken != Keyword.OFF) {
        FRLogger.warn(
            "DsnFile.read_boolean: Keyword.OFF expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
      }
      ScopeKeyword.skipScope(pScanner);
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_boolean: IO error scanning file", e);
      return false;
    }
  }

  static int readIntegerScope(IJFlexScanner pScanner) {
    try {
      int value;
      Object nextToken = pScanner.nextToken();
      if (nextToken instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn(
            "DsnFile.read_integer_scope: number expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return 0;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_integer_scope: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_integer_scope: IO error scanning file", e);
      return 0;
    }
  }

  static double readFloatScope(IJFlexScanner pScanner) {
    try {
      double value;
      Object nextToken = pScanner.nextToken();
      if (nextToken instanceof Double double1) {
        value = double1;
      } else if (nextToken instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn(
            "DsnFile.read_float_scope: number expected at '" + pScanner.getScopeIdentifier() + "'");
        return 0;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_float_scope: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_float_scope: IO error scanning file", e);
      return 0;
    }
  }

  public static String readStringScope(IJFlexScanner pScanner) {
    try {
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      String result = pScanner.nextString();
      Object nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_string_scope: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  public static String[] readStringListScope(IJFlexScanner pScanner) {
    String[] result = pScanner.nextStringList();
    if (!pScanner.nextClosingBracket()) {
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
