package app.freerouting.designforms.specctra;

import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
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

  public static void write_scope(WriteScopeParameter p_par) throws java.io.IOException {
    p_par.file.start_scope();
    p_par.file.write("library");
    for (int i = 1; i <= p_par.board.library.packages.count(); ++i) {
      Package.write_scope(p_par, p_par.board.library.packages.get(i));
    }
    for (int i = 1; i <= p_par.board.library.padstacks.count(); ++i) {
      write_padstack_scope(p_par, p_par.board.library.padstacks.get(i));
    }
    p_par.file.end_scope();
  }

  public static void write_padstack_scope(
      WriteScopeParameter p_par, app.freerouting.library.Padstack p_padstack)
      throws java.io.IOException {
    // search the layer range of the padstack
    int first_layer_no = 0;
    while (first_layer_no < p_par.board.get_layer_count()) {
      if (p_padstack.get_shape(first_layer_no) != null) {
        break;
      }
      ++first_layer_no;
    }
    int last_layer_no = p_par.board.get_layer_count() - 1;
    while (last_layer_no >= 0) {
      if (p_padstack.get_shape(last_layer_no) != null) {
        break;
      }
      --last_layer_no;
    }
    if (first_layer_no >= p_par.board.get_layer_count() || last_layer_no < 0) {
      FRLogger.warn("Library.write_padstack_scope: padstack shape not found");
      return;
    }

    p_par.file.start_scope();
    p_par.file.write("padstack ");
    p_par.identifier_type.write(p_padstack.name, p_par.file);
    for (int i = first_layer_no; i <= last_layer_no; ++i) {
      app.freerouting.geometry.planar.Shape curr_board_shape = p_padstack.get_shape(i);
      if (curr_board_shape == null) {
        continue;
      }
      app.freerouting.board.Layer board_layer = p_par.board.layer_structure.arr[i];
      Layer curr_layer = new Layer(board_layer.name, i, board_layer.is_signal);
      Shape curr_shape = p_par.coordinate_transform.board_to_dsn_rel(curr_board_shape, curr_layer);
      p_par.file.start_scope();
      p_par.file.write("shape");
      curr_shape.write_scope(p_par.file, p_par.identifier_type);
      p_par.file.end_scope();
    }
    if (!p_padstack.attach_allowed) {
      p_par.file.new_line();
      p_par.file.write("(attach off)");
    }
    if (p_padstack.placed_absolute) {
      p_par.file.new_line();
      p_par.file.write("(absolute on)");
    }
    p_par.file.end_scope();
  }

  static boolean read_padstack_scope(
      IJFlexScanner p_scanner,
      LayerStructure p_layer_structure,
      CoordinateTransform p_coordinate_transform,
      app.freerouting.library.Padstacks p_board_padstacks) {
    String padstack_name = null;
    boolean is_drilllable = true;
    boolean placed_absolute = false;
    Collection<Shape> shape_list = new LinkedList<Shape>();
    try {
      Object next_token = p_scanner.next_token();
      if (next_token instanceof String) {
        padstack_name = (String) next_token;
      } else {
        FRLogger.warn("Library.read_padstack_scope: unexpected padstack identifier");
        return false;
      }

      while (next_token != Keyword.CLOSED_BRACKET) {
        Object prev_token = next_token;
        next_token = p_scanner.next_token();
        if (prev_token == Keyword.OPEN_BRACKET) {
          if (next_token == Keyword.SHAPE) {
            Shape curr_shape = Shape.read_scope(p_scanner, p_layer_structure);
            if (curr_shape != null) {
              shape_list.add(curr_shape);
            }
            // overread the closing bracket and unknown scopes.
            Object curr_next_token = p_scanner.next_token();
            while (curr_next_token == Keyword.OPEN_BRACKET) {
              ScopeKeyword.skip_scope(p_scanner);
              curr_next_token = p_scanner.next_token();
            }
            if (curr_next_token != Keyword.CLOSED_BRACKET) {
              FRLogger.warn("Library.read_padstack_scope: closing bracket expected");
              return false;
            }
          } else if (next_token == Keyword.ATTACH) {
            is_drilllable = DsnFile.read_on_off_scope(p_scanner);
          } else if (next_token == Keyword.ABSOLUTE) {
            placed_absolute = DsnFile.read_on_off_scope(p_scanner);
          } else {
            ScopeKeyword.skip_scope(p_scanner);
          }
        }
      }
    } catch (java.io.IOException e) {
      FRLogger.error("Library.read_padstack_scope: IO error scanning file", e);
      return false;
    }
    if (p_board_padstacks.get(padstack_name) != null) {
      // Padstack exists already
      return true;
    }
    if (shape_list.isEmpty()) {
      FRLogger.warn(
          "Library.read_padstack_scope: shape not found for padstack with name '"
              + padstack_name
              + "'");
      return true;
    }
    app.freerouting.geometry.planar.ConvexShape[] padstack_shapes =
        new app.freerouting.geometry.planar.ConvexShape[p_layer_structure.arr.length];
    Iterator<Shape> it = shape_list.iterator();
    while (it.hasNext()) {
      Shape pad_shape = it.next();
      app.freerouting.geometry.planar.Shape curr_shape =
          pad_shape.transform_to_board_rel(p_coordinate_transform);
      app.freerouting.geometry.planar.ConvexShape convex_shape;
      if (curr_shape instanceof app.freerouting.geometry.planar.ConvexShape) {
        convex_shape = (app.freerouting.geometry.planar.ConvexShape) curr_shape;
      } else {
        if (curr_shape instanceof PolygonShape) {
          curr_shape = ((PolygonShape) curr_shape).convex_hull();
        }
        app.freerouting.geometry.planar.TileShape[] convex_shapes = curr_shape.split_to_convex();
        if (convex_shapes.length != 1) {
          FRLogger.warn("Library.read_padstack_scope: convex shape expected");
        }
        convex_shape = convex_shapes[0];
        if (convex_shape instanceof Simplex) {
          convex_shape = ((Simplex) convex_shape).simplify();
        }
      }
      app.freerouting.geometry.planar.ConvexShape padstack_shape = convex_shape;
      if (padstack_shape != null) {
        if (padstack_shape.dimension() < 2) {
          FRLogger.warn("Library.read_padstack_scope: shape is not an area ");
          // enlarge the shape a little bit, so that it is an area
          padstack_shape = padstack_shape.offset(1);
          if (padstack_shape.dimension() < 2) {
            padstack_shape = null;
          }
        }
      }

      if (pad_shape.layer == Layer.PCB || pad_shape.layer == Layer.SIGNAL) {
        Arrays.fill(padstack_shapes, padstack_shape);
      } else {
        int shape_layer = p_layer_structure.get_no(pad_shape.layer.name);
        if (shape_layer < 0 || shape_layer >= padstack_shapes.length) {
          FRLogger.warn("Library.read_padstack_scope: layer number found");
          return false;
        }
        padstack_shapes[shape_layer] = padstack_shape;
      }
    }
    p_board_padstacks.add(padstack_name, padstack_shapes, is_drilllable, placed_absolute);
    return true;
  }

  public boolean read_scope(ReadScopeParameter p_par) {
    app.freerouting.board.RoutingBoard board = p_par.board_handling.get_routing_board();
    board.library.padstacks =
        new app.freerouting.library.Padstacks(
            p_par.board_handling.get_routing_board().layer_structure);
    Collection<Package> package_list = new LinkedList<Package>();
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_par.scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("Library.read_scope: IO error scanning file", e);
        return false;
      }
      if (next_token == null) {
        FRLogger.warn("Library.read_scope: unexpected end of file");
        return false;
      }
      if (next_token == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == OPEN_BRACKET) {
        if (next_token == Keyword.PADSTACK) {
          if (!read_padstack_scope(
              p_par.scanner,
              p_par.layer_structure,
              p_par.coordinate_transform,
              board.library.padstacks)) {
            return false;
          }
        } else if (next_token == Keyword.IMAGE) {
          Package curr_package = Package.read_scope(p_par.scanner, p_par.layer_structure);
          if (curr_package == null) {
            return false;
          }
          package_list.add(curr_package);
        } else {
          skip_scope(p_par.scanner);
        }
      }
    }

    // Create the library packages on the board
    board.library.packages = new app.freerouting.library.Packages(board.library.padstacks);
    Iterator<Package> it = package_list.iterator();
    while (it.hasNext()) {
      Package curr_package = it.next();
      app.freerouting.library.Package.Pin[] pin_arr =
          new app.freerouting.library.Package.Pin[curr_package.pin_info_arr.length];
      for (int i = 0; i < pin_arr.length; ++i) {
        Package.PinInfo pin_info = curr_package.pin_info_arr[i];
        int rel_x = (int) Math.round(p_par.coordinate_transform.dsn_to_board(pin_info.rel_coor[0]));
        int rel_y = (int) Math.round(p_par.coordinate_transform.dsn_to_board(pin_info.rel_coor[1]));
        Vector rel_coor = new IntVector(rel_x, rel_y);
        app.freerouting.library.Padstack board_padstack =
            board.library.padstacks.get(pin_info.padstack_name);
        if (board_padstack == null) {
          FRLogger.warn("Library.read_scope: app.freerouting.board padstack not found");
          return false;
        }
        pin_arr[i] =
            new app.freerouting.library.Package.Pin(
                pin_info.pin_name, board_padstack.no, rel_coor, pin_info.rotation);
      }
      app.freerouting.geometry.planar.Shape[] outline_arr =
          new app.freerouting.geometry.planar.Shape[curr_package.outline.size()];

      Iterator<Shape> it3 = curr_package.outline.iterator();
      for (int i = 0; i < outline_arr.length; ++i) {
        Shape curr_shape = it3.next();
        if (curr_shape != null) {
          outline_arr[i] = curr_shape.transform_to_board_rel(p_par.coordinate_transform);
        } else {
          FRLogger.warn("Library.read_scope: outline shape is null");
        }
      }
      generate_missing_keepout_names("keepout_", curr_package.keepouts);
      generate_missing_keepout_names("via_keepout_", curr_package.via_keepouts);
      generate_missing_keepout_names("place_keepout_", curr_package.place_keepouts);
      app.freerouting.library.Package.Keepout[] keepout_arr =
          new app.freerouting.library.Package.Keepout[curr_package.keepouts.size()];
      Iterator<Shape.ReadAreaScopeResult> it2 = curr_package.keepouts.iterator();
      for (int i = 0; i < keepout_arr.length; ++i) {
        Shape.ReadAreaScopeResult curr_keepout = it2.next();
        Layer curr_layer = curr_keepout.shape_list.iterator().next().layer;
        app.freerouting.geometry.planar.Area curr_area =
            Shape.transform_area_to_board_rel(curr_keepout.shape_list, p_par.coordinate_transform);
        keepout_arr[i] =
            new app.freerouting.library.Package.Keepout(
                curr_keepout.area_name, curr_area, curr_layer.no);
      }
      app.freerouting.library.Package.Keepout[] via_keepout_arr =
          new app.freerouting.library.Package.Keepout[curr_package.via_keepouts.size()];
      it2 = curr_package.via_keepouts.iterator();
      for (int i = 0; i < via_keepout_arr.length; ++i) {
        Shape.ReadAreaScopeResult curr_keepout = it2.next();
        Layer curr_layer = (curr_keepout.shape_list.iterator().next()).layer;
        app.freerouting.geometry.planar.Area curr_area =
            Shape.transform_area_to_board_rel(curr_keepout.shape_list, p_par.coordinate_transform);
        via_keepout_arr[i] =
            new app.freerouting.library.Package.Keepout(
                curr_keepout.area_name, curr_area, curr_layer.no);
      }
      app.freerouting.library.Package.Keepout[] place_keepout_arr =
          new app.freerouting.library.Package.Keepout[curr_package.place_keepouts.size()];
      it2 = curr_package.place_keepouts.iterator();
      for (int i = 0; i < place_keepout_arr.length; ++i) {
        Shape.ReadAreaScopeResult curr_keepout = it2.next();
        Layer curr_layer = (curr_keepout.shape_list.iterator().next()).layer;
        app.freerouting.geometry.planar.Area curr_area =
            Shape.transform_area_to_board_rel(curr_keepout.shape_list, p_par.coordinate_transform);
        place_keepout_arr[i] =
            new app.freerouting.library.Package.Keepout(
                curr_keepout.area_name, curr_area, curr_layer.no);
      }
      board.library.packages.add(
          curr_package.name,
          pin_arr,
          outline_arr,
          keepout_arr,
          via_keepout_arr,
          place_keepout_arr,
          curr_package.is_front);
    }
    return true;
  }

  private void generate_missing_keepout_names(
      String p_keepout_type, Collection<Shape.ReadAreaScopeResult> p_keepout_list) {
    boolean all_names_existing = true;
    for (Shape.ReadAreaScopeResult curr_keepout : p_keepout_list) {
      if (curr_keepout.area_name == null) {
        all_names_existing = false;
        break;
      }
    }
    if (all_names_existing) {
      return;
    }
    // generate names
    Integer curr_name_index = 1;
    for (Shape.ReadAreaScopeResult curr_keepout : p_keepout_list) {
      curr_keepout.area_name = p_keepout_type + curr_name_index;
      ++curr_name_index;
    }
  }
}
