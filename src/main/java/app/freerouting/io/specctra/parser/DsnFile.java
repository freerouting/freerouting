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
    Collection<Item> itemList = routingBoard.get_items();
    for (Item currItem : itemList) {
      if (currItem instanceof Trace trace) {
        int currLayer = trace.get_layer();
        layerContainsWiresArr[currLayer] = true;
      } else if (currItem instanceof ConductionArea area) {
        conductionAreaList.add(area);
      }
    }
    boolean nothingChanged = true;

    BoardOutline boardOutline = routingBoard.get_outline();
    double boardArea = 0;
    for (int i = 0; i < boardOutline.shape_count(); i++) {
      TileShape[] currPieceArr = boardOutline.get_shape(i).split_to_convex();
      if (currPieceArr != null) {
        for (TileShape currPiece : currPieceArr) {
          boardArea += currPiece.area();
        }
      }
    }
    for (ConductionArea curr_conduction_area : conductionAreaList) {
      int layerNo = curr_conduction_area.get_layer();
      if (layerContainsWiresArr[layerNo]) {
        continue;
      }
      app.freerouting.board.Layer currLayer = routingBoard.layerStructure.arr[layerNo];
      if (!currLayer.isSignal || layerNo == 0 || layerNo == boardLayerStructure.arr.length - 1) {
        continue;
      }
      TileShape[] convexPieces = curr_conduction_area.get_area().split_to_convex();
      double currArea = 0;
      for (TileShape currPiece : convexPieces) {
        currArea += currPiece.area();
      }
      if (currArea < 0.5 * boardArea) {
        continue;
      }
      for (int i = 0; i < curr_conduction_area.net_count(); i++) {
        Net currNet = routingBoard.rules.nets.get(curr_conduction_area.get_net_no(i));
        currNet.set_contains_plane(true);
        nothingChanged = false;
      }
      changedLayerArr[layerNo] = true;
      if (curr_conduction_area.get_fixed_state().ordinal() < FixedState.USER_FIXED.ordinal()) {
        curr_conduction_area.set_fixed_state(FixedState.USER_FIXED);
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

  static boolean read_on_off_scope(IJFlexScanner p_scanner) {
    try {
      Object nextToken = p_scanner.next_token();
      boolean result = false;
      if (nextToken == Keyword.ON) {
        result = true;
      } else if (nextToken != Keyword.OFF) {
        FRLogger.warn(
            "DsnFile.read_boolean: Keyword.OFF expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
      }
      ScopeKeyword.skip_scope(p_scanner);
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_boolean: IO error scanning file", e);
      return false;
    }
  }

  static int read_integer_scope(IJFlexScanner p_scanner) {
    try {
      int value;
      Object nextToken = p_scanner.next_token();
      if (nextToken instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn(
            "DsnFile.read_integer_scope: number expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return 0;
      }
      nextToken = p_scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_integer_scope: closing bracket expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_integer_scope: IO error scanning file", e);
      return 0;
    }
  }

  static double read_float_scope(IJFlexScanner p_scanner) {
    try {
      double value;
      Object nextToken = p_scanner.next_token();
      if (nextToken instanceof Double double1) {
        value = double1;
      } else if (nextToken instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn(
            "DsnFile.read_float_scope: number expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return 0;
      }
      nextToken = p_scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_float_scope: closing bracket expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_float_scope: IO error scanning file", e);
      return 0;
    }
  }

  public static String read_string_scope(IJFlexScanner p_scanner) {
    try {
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      String result = p_scanner.next_string();
      Object nextToken = p_scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "DsnFile.read_string_scope: closing bracket expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  public static String[] read_string_list_scope(IJFlexScanner p_scanner) {
    String[] result = p_scanner.next_string_list();
    if (!p_scanner.next_closing_bracket()) {
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
