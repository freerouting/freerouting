package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.Pin;
import app.freerouting.core.Package;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** Handles the placement data of a library component. */
public class Component extends ScopeKeyword {

  /** Creates a new instance of Component */
  public Component() {
    super("component");
  }

  /** Used also when reading a session file. */
  public static ComponentPlacement read_scope(IJFlexScanner p_scanner) throws IOException {
    Object nextToken = p_scanner.next_token();
    if (!(nextToken instanceof String name)) {
      FRLogger.warn(
          "Component.read_scope: component name expected at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return null;
    }
    ComponentPlacement componentPlacement = new ComponentPlacement(name);
    Object prevToken = nextToken;
    nextToken = p_scanner.next_token();
    while (nextToken != CLOSED_BRACKET) {
      if (prevToken == OPEN_BRACKET && nextToken == PLACE) {
        ComponentPlacement.ComponentLocation nextLocation = read_place_scope(p_scanner);
        if (nextLocation != null) {
          componentPlacement.locations.add(nextLocation);
        }
      }
      prevToken = nextToken;
      nextToken = p_scanner.next_token();
    }
    return componentPlacement;
  }

  public static void write_scope(
      WriteScopeParameter p_par, app.freerouting.board.Component p_component) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("place ");
    p_par.file.new_line();
    p_par.identifierType.write(p_component.name, p_par.file);
    if (p_component.is_placed()) {
      double[] coor = p_par.coordinateTransform.board_to_dsn(p_component.get_location().to_float());
      for (int i = 0; i < coor.length; i++) {
        p_par.file.write(" ");
        p_par.file.write(String.valueOf(coor[i]));
      }
      if (p_component.placed_on_front()) {
        p_par.file.write(" front ");
      } else {
        p_par.file.write(" back ");
      }
      int rotation = (int) Math.round(p_component.get_rotation_in_degree());
      p_par.file.write(String.valueOf(rotation));
    }
    if (p_component.positionFixed) {
      p_par.file.new_line();
      p_par.file.write(" (lock_type position)");
    }
    int pinCount = p_component.get_package().pin_count();
    for (int i = 0; i < pinCount; i++) {
      write_pin_info(p_par, p_component, i);
    }
    write_keepout_infos(p_par, p_component);
    p_par.file.end_scope();
  }

  private static void write_pin_info(
      WriteScopeParameter p_par, app.freerouting.board.Component p_component, int p_pin_no)
      throws IOException {
    if (!p_component.is_placed()) {
      return;
    }
    Package.Pin packagePin = p_component.get_package().get_pin(p_pin_no);
    if (packagePin == null) {
      FRLogger.warn(
          "Component.write_pin_info: package pin not found at '" + p_component.name + "'");
      return;
    }
    Pin componentPin = p_par.board.get_pin(p_component.no, p_pin_no);
    if (componentPin == null) {
      FRLogger.warn(
          "Component.write_pin_info: component pin not found at '" + p_component.name + "'");
      return;
    }
    String clClassName =
        p_par.board.rules.clearanceMatrix.get_name(componentPin.clearance_class_no());
    if (clClassName == null) {
      FRLogger.warn(
          "Component.write_pin_info: clearance class  name not found at '"
              + p_component.name
              + "'");
      return;
    }
    p_par.file.new_line();
    p_par.file.write("(pin ");
    p_par.identifierType.write(packagePin.name, p_par.file);
    p_par.file.write(" (clearanceClass ");
    p_par.identifierType.write(clClassName, p_par.file);
    p_par.file.write("))");
  }

  private static void write_keepout_infos(
      WriteScopeParameter p_par, app.freerouting.board.Component p_component) throws IOException {
    if (!p_component.is_placed()) {
      return;
    }
    Package.Keepout[] currKeepoutArr;
    String keepoutType;
    for (int j = 0; j < 3; j++) {
      if (j == 0) {
        currKeepoutArr = p_component.get_package().keepoutArr;
        keepoutType = "(keepout ";
      } else if (j == 1) {
        currKeepoutArr = p_component.get_package().viaKeepoutArr;
        keepoutType = "(via_keepout ";
      } else {
        currKeepoutArr = p_component.get_package().placeKeepoutArr;
        keepoutType = "(place_keepout ";
      }
      for (int i = 0; i < currKeepoutArr.length; i++) {
        Package.Keepout currKeepout = currKeepoutArr[i];
        ObstacleArea currObstacleArea = get_keepout(p_par.board, p_component.no, currKeepout.name);
        if (currObstacleArea == null || currObstacleArea.clearance_class_no() == 0) {
          continue;
        }
        String clClassName =
            p_par.board.rules.clearanceMatrix.get_name(currObstacleArea.clearance_class_no());
        if (clClassName == null) {
          FRLogger.warn(
              "Component.write_keepout_infos: clearance class name not found at '"
                  + p_component.name
                  + "'");
          return;
        }
        p_par.file.new_line();
        p_par.file.write(keepoutType);
        p_par.identifierType.write(currKeepout.name, p_par.file);
        p_par.file.write(" (clearanceClass ");
        p_par.identifierType.write(clClassName, p_par.file);
        p_par.file.write("))");
      }
    }
  }

  private static ObstacleArea get_keepout(BasicBoard p_board, int p_component_no, String p_name) {
    Iterator<UndoableObjects.UndoableObjectNode> it = p_board.itemList.start_read_object();
    for (; ; ) {
      Item currItem = (Item) p_board.itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      if (currItem.get_component_no() == p_component_no
          && currItem instanceof ObstacleArea currArea) {
        if (currArea.name != null && currArea.name.equals(p_name)) {
          return currArea;
        }
      }
    }
    return null;
  }

  private static ComponentPlacement.ComponentLocation read_place_scope(IJFlexScanner p_scanner) {
    try {
      Map<String, ComponentPlacement.ItemClearanceInfo> pin_infos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> keepout_infos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> via_keepout_infos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> place_keepout_infos = new TreeMap<>();

      String name = p_scanner.next_string(true);

      Object nextToken;
      double[] location = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = p_scanner.next_token();
        if (nextToken instanceof Double double1) {
          location[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          location[i] = integer;
        } else if (nextToken == CLOSED_BRACKET) {
          // component is not yet placed
          return new ComponentPlacement.ComponentLocation(
              name,
              null,
              true,
              0,
              false,
              pin_infos,
              keepout_infos,
              via_keepout_infos,
              place_keepout_infos,
              null);
        } else {
          FRLogger.warn(
              "Component.read_place_scope: Double was expected as the second and third parameter of the component/place command at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
      }

      nextToken = p_scanner.next_token();
      boolean isFront = true;
      if (nextToken == BACK) {
        isFront = false;
      } else if (nextToken != FRONT) {
        FRLogger.warn(
            "Component.read_place_scope: Keyword.FRONT expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
      }
      double rotation;
      nextToken = p_scanner.next_token();
      if (nextToken instanceof Double double1) {
        rotation = double1;
      } else if (nextToken instanceof Integer integer) {
        rotation = integer;
      } else {
        FRLogger.warn(
            "Component.read_place_scope: number expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      boolean positionFixed = false;
      String partNumber = null;
      nextToken = p_scanner.next_token();
      while (nextToken == OPEN_BRACKET) {
        nextToken = p_scanner.next_token();
        if (nextToken == LOCK_TYPE) {
          positionFixed = read_lock_type(p_scanner);
        } else if (nextToken == PIN) {
          ComponentPlacement.ItemClearanceInfo currPinInfo = read_item_clearance_info(p_scanner);
          if (currPinInfo == null) {
            return null;
          }
          pin_infos.put(currPinInfo.name, currPinInfo);
        } else if (nextToken == KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo =
              read_item_clearance_info(p_scanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          keepout_infos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == VIA_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo =
              read_item_clearance_info(p_scanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          via_keepout_infos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == PLACE_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo =
              read_item_clearance_info(p_scanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          place_keepout_infos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == Keyword.PN
            || (nextToken instanceof String && "PN".equalsIgnoreCase((String) nextToken))) {
          partNumber = DsnFile.read_string_scope(p_scanner);
        } else {
          skip_scope(p_scanner);
        }
        nextToken = p_scanner.next_token();
      }
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Component.read_place_scope: ) expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      return new ComponentPlacement.ComponentLocation(
          name,
          location,
          isFront,
          rotation,
          positionFixed,
          pin_infos,
          keepout_infos,
          via_keepout_infos,
          place_keepout_infos,
          partNumber);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return null;
    }
  }

  private static ComponentPlacement.ItemClearanceInfo read_item_clearance_info(
      IJFlexScanner p_scanner) throws IOException {
    p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
    Object nextToken = p_scanner.next_token();
    if (!(nextToken instanceof String name)) {
      FRLogger.warn(
          "Component.read_item_clearance_info: String expected at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return null;
    }
    String clClassName = null;
    nextToken = p_scanner.next_token();
    while (nextToken == OPEN_BRACKET) {
      nextToken = p_scanner.next_token();
      if (nextToken == CLEARANCE_CLASS) {
        clClassName = DsnFile.read_string_scope(p_scanner);
      } else {
        skip_scope(p_scanner);
      }
      nextToken = p_scanner.next_token();
    }
    if (nextToken != CLOSED_BRACKET) {
      FRLogger.warn(
          "Component.read_item_clearance_info: ) expected at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return null;
    }
    if (clClassName == null) {
      FRLogger.warn(
          "Component.read_item_clearance_info: clearance class name not found at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return null;
    }
    return new ComponentPlacement.ItemClearanceInfo(name, clClassName);
  }

  private static boolean read_lock_type(IJFlexScanner p_scanner) throws IOException {
    boolean result = false;
    for (; ; ) {
      Object nextToken = p_scanner.next_token();
      if (nextToken == CLOSED_BRACKET) {
        break;
      }
      if (nextToken == POSITION) {
        result = true;
      }
    }
    return result;
  }

  /** Overwrites the function read_scope in ScopeKeyword */
  @Override
  public boolean read_scope(ReadScopeParameter p_par) {
    try {
      ComponentPlacement componentPlacement = read_scope(p_par.scanner);
      if (componentPlacement == null) {
        return false;
      }
      p_par.placementList.add(componentPlacement);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }
}
