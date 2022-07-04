package app.freerouting.designforms.specctra;

import java.util.Collection;
import java.util.LinkedList;

/** Default parameter type used while reading a Specctra dsn-file. */
public class ReadScopeParameter {

  final IJFlexScanner scanner;
  final app.freerouting.interactive.IBoardHandling board_handling;
  final NetList netlist = new NetList();
  final app.freerouting.board.BoardObservers observers;
  final app.freerouting.datastructures.IdNoGenerator item_id_no_generator;
  final app.freerouting.board.TestLevel test_level;
  /**
   * Collection of elements of class PlaneInfo. The plane cannot be inserted directly into the
   * boards, because the layers may not be read completely.
   */
  final Collection<PlaneInfo> plane_list = new LinkedList<PlaneInfo>();
  /**
   * Component placement information. It is filled while reading the placement scope and can be
   * evaluated after reading the library and network scope.
   */
  final Collection<ComponentPlacement> placement_list = new LinkedList<ComponentPlacement>();
  final Collection<String[]> constants = new LinkedList<String[]>();
  /**
   * The names of the via padstacks filled while reading the structure scope and evaluated after
   * reading the library scope.
   */
  Collection<String> via_padstack_names = null;

  boolean via_at_smd_allowed = false;
  app.freerouting.board.AngleRestriction snap_angle =
      app.freerouting.board.AngleRestriction.FORTYFIVE_DEGREE;

  /** The logical parts are used for pin and gate swaw */
  java.util.Collection<PartLibrary.LogicalPartMapping> logical_part_mappings =
      new java.util.LinkedList<PartLibrary.LogicalPartMapping>();

  java.util.Collection<PartLibrary.LogicalPart> logical_parts =
      new java.util.LinkedList<PartLibrary.LogicalPart>();

  /** The following objects are from the parser scope. */
  String string_quote = "\"";

  String host_cad = null;
  String host_version = null;

  boolean dsn_file_generated_by_host = true;

  boolean board_outline_ok = true;
  app.freerouting.board.Communication.SpecctraParserInfo.WriteResolution write_resolution = null;
  /** The following objects will be initialised when the structure scope is read. */
  CoordinateTransform coordinate_transform = null;
  LayerStructure layer_structure = null;
  app.freerouting.interactive.AutorouteSettings autoroute_settings = null;
  app.freerouting.board.Unit unit = app.freerouting.board.Unit.MIL;
  int resolution = 100; // default resulution
  /** Creates a new instance of ReadScopeParameter */
  ReadScopeParameter(
      IJFlexScanner p_scanner,
      app.freerouting.interactive.IBoardHandling p_board_handling,
      app.freerouting.board.BoardObservers p_observers,
      app.freerouting.datastructures.IdNoGenerator p_item_id_no_generator,
      app.freerouting.board.TestLevel p_test_level) {
    scanner = p_scanner;
    board_handling = p_board_handling;
    observers = p_observers;
    item_id_no_generator = p_item_id_no_generator;
    test_level = p_test_level;
  }

  /** Information for inserting a plane */
  static class PlaneInfo {
    final Shape.ReadAreaScopeResult area;
    final String net_name;
    PlaneInfo(Shape.ReadAreaScopeResult p_area, String p_net_name) {
      area = p_area;
      net_name = p_net_name;
    }
  }
}
