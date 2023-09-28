package app.freerouting.designforms.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
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
    Object next_token = p_scanner.next_token();
    if (!(next_token instanceof String)) {
      FRLogger.warn("Component.read_scope: component name expected at '" + p_scanner.get_scope_identifier() + "'");
      return null;
    }
    String name = (String) next_token;
    ComponentPlacement component_placement = new ComponentPlacement(name);
    Object prev_token = next_token;
    next_token = p_scanner.next_token();
    while (next_token != CLOSED_BRACKET) {
      if (prev_token == OPEN_BRACKET && next_token == PLACE) {
        ComponentPlacement.ComponentLocation next_location = read_place_scope(p_scanner);
        if (next_location != null) {
          component_placement.locations.add(next_location);
        }
      }
      prev_token = next_token;
      next_token = p_scanner.next_token();
    }
    return component_placement;
  }

  public static void write_scope(
      WriteScopeParameter p_par, app.freerouting.board.Component p_component)
      throws IOException {
    p_par.file.start_scope();
    p_par.file.write("place ");
    p_par.file.new_line();
    p_par.identifier_type.write(p_component.name, p_par.file);
    if (p_component.is_placed()) {
      double[] coor =
          p_par.coordinate_transform.board_to_dsn(p_component.get_location().to_float());
      for (int i = 0; i < coor.length; ++i) {
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
    if (p_component.position_fixed) {
      p_par.file.new_line();
      p_par.file.write(" (lock_type position)");
    }
    int pin_count = p_component.get_package().pin_count();
    for (int i = 0; i < pin_count; ++i) {
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
    app.freerouting.library.Package.Pin package_pin = p_component.get_package().get_pin(p_pin_no);
    if (package_pin == null) {
      FRLogger.warn("Component.write_pin_info: package pin not found at '" + p_component.name + "'");
      return;
    }
    app.freerouting.board.Pin component_pin = p_par.board.get_pin(p_component.no, p_pin_no);
    if (component_pin == null) {
      FRLogger.warn("Component.write_pin_info: component pin not found at '" + p_component.name + "'");
      return;
    }
    String cl_class_name =
        p_par.board.rules.clearance_matrix.get_name(component_pin.clearance_class_no());
    if (cl_class_name == null) {
      FRLogger.warn("Component.write_pin_info: clearance class  name not found at '" + p_component.name + "'");
      return;
    }
    p_par.file.new_line();
    p_par.file.write("(pin ");
    p_par.identifier_type.write(package_pin.name, p_par.file);
    p_par.file.write(" (clearance_class ");
    p_par.identifier_type.write(cl_class_name, p_par.file);
    p_par.file.write("))");
  }

  private static void write_keepout_infos(
      WriteScopeParameter p_par, app.freerouting.board.Component p_component)
      throws IOException {
    if (!p_component.is_placed()) {
      return;
    }
    app.freerouting.library.Package.Keepout[] curr_keepout_arr;
    String keepout_type;
    for (int j = 0; j < 3; ++j) {
      if (j == 0) {
        curr_keepout_arr = p_component.get_package().keepout_arr;
        keepout_type = "(keepout ";
      } else if (j == 1) {
        curr_keepout_arr = p_component.get_package().via_keepout_arr;
        keepout_type = "(via_keepout ";
      } else {
        curr_keepout_arr = p_component.get_package().place_keepout_arr;
        keepout_type = "(place_keepout ";
      }
      for (int i = 0; i < curr_keepout_arr.length; ++i) {
        app.freerouting.library.Package.Keepout curr_keepout = curr_keepout_arr[i];
        ObstacleArea curr_obstacle_area =
            get_keepout(p_par.board, p_component.no, curr_keepout.name);
        if (curr_obstacle_area == null || curr_obstacle_area.clearance_class_no() == 0) {
          continue;
        }
        String cl_class_name =
            p_par.board.rules.clearance_matrix.get_name(curr_obstacle_area.clearance_class_no());
        if (cl_class_name == null) {
          FRLogger.warn("Component.write_keepout_infos: clearance class name not found at '" + p_component.name + "'");
          return;
        }
        p_par.file.new_line();
        p_par.file.write(keepout_type);
        p_par.identifier_type.write(curr_keepout.name, p_par.file);
        p_par.file.write(" (clearance_class ");
        p_par.identifier_type.write(cl_class_name, p_par.file);
        p_par.file.write("))");
      }
    }
  }

  private static ObstacleArea get_keepout(
      BasicBoard p_board, int p_component_no, String p_name) {
    Iterator<UndoableObjects.UndoableObjectNode> it =
        p_board.item_list.start_read_object();
    for (; ; ) {
      Item curr_item =
          (Item) p_board.item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item.get_component_no() == p_component_no
          && curr_item instanceof ObstacleArea) {
        ObstacleArea curr_area =
            (ObstacleArea) curr_item;
        if (curr_area.name != null && curr_area.name.equals(p_name)) {
          return curr_area;
        }
      }
    }
    return null;
  }

  private static ComponentPlacement.ComponentLocation read_place_scope(IJFlexScanner p_scanner) {
    try {
      Map<String, ComponentPlacement.ItemClearanceInfo> pin_infos =
          new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> keepout_infos =
          new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> via_keepout_infos =
          new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> place_keepout_infos =
          new TreeMap<>();

      String name = p_scanner.next_string(true);

      Object next_token;
      double[] location = new double[2];
      for (int i = 0; i < 2; ++i) {
        next_token = p_scanner.next_token();
        if (next_token instanceof Double) {
          location[i] = (Double) next_token;
        } else if (next_token instanceof Integer) {
          location[i] = (Integer) next_token;
        } else if (next_token == CLOSED_BRACKET) {
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
              place_keepout_infos);
        } else {
          FRLogger.warn("Component.read_place_scope: Double was expected as the second and third parameter of the component/place command at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
      }

      next_token = p_scanner.next_token();
      boolean is_front = true;
      if (next_token == BACK) {
        is_front = false;
      } else if (next_token != FRONT) {
        FRLogger.warn("Component.read_place_scope: Keyword.FRONT expected at '" + p_scanner.get_scope_identifier() + "'");
      }
      double rotation;
      next_token = p_scanner.next_token();
      if (next_token instanceof Double) {
        rotation = (Double) next_token;
      } else if (next_token instanceof Integer) {
        rotation = (Integer) next_token;
      } else {
        FRLogger.warn("Component.read_place_scope: number expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      boolean position_fixed = false;
      next_token = p_scanner.next_token();
      while (next_token == OPEN_BRACKET) {
        next_token = p_scanner.next_token();
        if (next_token == LOCK_TYPE) {
          position_fixed = read_lock_type(p_scanner);
        } else if (next_token == PIN) {
          ComponentPlacement.ItemClearanceInfo curr_pin_info = read_item_clearance_info(p_scanner);
          if (curr_pin_info == null) {
            return null;
          }
          pin_infos.put(curr_pin_info.name, curr_pin_info);
        } else if (next_token == KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo curr_keepout_info =
              read_item_clearance_info(p_scanner);
          if (curr_keepout_info == null) {
            return null;
          }
          keepout_infos.put(curr_keepout_info.name, curr_keepout_info);
        } else if (next_token == VIA_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo curr_keepout_info =
              read_item_clearance_info(p_scanner);
          if (curr_keepout_info == null) {
            return null;
          }
          via_keepout_infos.put(curr_keepout_info.name, curr_keepout_info);
        } else if (next_token == PLACE_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo curr_keepout_info =
              read_item_clearance_info(p_scanner);
          if (curr_keepout_info == null) {
            return null;
          }
          place_keepout_infos.put(curr_keepout_info.name, curr_keepout_info);
        } else {
          skip_scope(p_scanner);
        }
        next_token = p_scanner.next_token();
      }
      if (next_token != CLOSED_BRACKET) {
        FRLogger.warn("Component.read_place_scope: ) expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      return new ComponentPlacement.ComponentLocation(
              name,
              location,
              is_front,
              rotation,
              position_fixed,
              pin_infos,
              keepout_infos,
              via_keepout_infos,
              place_keepout_infos);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return null;
    }
  }

  private static ComponentPlacement.ItemClearanceInfo read_item_clearance_info(
      IJFlexScanner p_scanner) throws IOException {
    p_scanner.yybegin(SpecctraDsnFileReader.NAME);
    Object next_token = p_scanner.next_token();
    if (!(next_token instanceof String)) {
      FRLogger.warn("Component.read_item_clearance_info: String expected at '" + p_scanner.get_scope_identifier() + "'");
      return null;
    }
    String name = (String) next_token;
    String cl_class_name = null;
    next_token = p_scanner.next_token();
    while (next_token == OPEN_BRACKET) {
      next_token = p_scanner.next_token();
      if (next_token == CLEARANCE_CLASS) {
        cl_class_name = DsnFile.read_string_scope(p_scanner);
      } else {
        skip_scope(p_scanner);
      }
      next_token = p_scanner.next_token();
    }
    if (next_token != CLOSED_BRACKET) {
      FRLogger.warn("Component.read_item_clearance_info: ) expected at '" + p_scanner.get_scope_identifier() + "'");
      return null;
    }
    if (cl_class_name == null) {
      FRLogger.warn("Component.read_item_clearance_info: clearance class name not found at '" + p_scanner.get_scope_identifier() + "'");
      return null;
    }
    return new ComponentPlacement.ItemClearanceInfo(name, cl_class_name);
  }

  private static boolean read_lock_type(IJFlexScanner p_scanner) throws IOException {
    boolean result = false;
    for (; ; ) {
      Object next_token = p_scanner.next_token();
      if (next_token == CLOSED_BRACKET) {
        break;
      }
      if (next_token == POSITION) {
        result = true;
      }
    }
    return result;
  }

  /** Overwrites the function read_scope in ScopeKeyword */
  @Override
  public boolean read_scope(ReadScopeParameter p_par) {
    try {
      ComponentPlacement component_placement = read_scope(p_par.scanner);
      if (component_placement == null) {
        return false;
      }
      p_par.placement_list.add(component_placement);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }
}
