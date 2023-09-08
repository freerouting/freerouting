package app.freerouting.designforms.specctra;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.TestLevel;
import app.freerouting.datastructures.IdNoGenerator;
import app.freerouting.interactive.IBoardHandling;

import java.util.Collection;
import java.util.LinkedList;

/** Default parameter type used while reading a Specctra dsn-file. */
public class ReadScopeParameter {

  final IJFlexScanner scanner;
  final IBoardHandling board_handling;
  final NetList netlist = new NetList();
  final BoardObservers observers;
  final IdNoGenerator item_id_no_generator;
  final TestLevel test_level;
  /**
   * Collection of elements of class PlaneInfo. The plane cannot be inserted directly into the
   * boards, because the layers may not be read completely.
   */
  final Collection<PlaneInfo> plane_list = new LinkedList<>();
  /**
   * Component placement information. It is filled while reading the placement scope and can be
   * evaluated after reading the library and network scope.
   */
  final Collection<ComponentPlacement> placement_list = new LinkedList<>();
  final Collection<String[]> constants = new LinkedList<>();
  /**
   * The names of the via padstacks filled while reading the structure scope and evaluated after
   * reading the library scope.
   */
  Collection<String> via_padstack_names;

  boolean via_at_smd_allowed = false;
  AngleRestriction snap_angle =
      AngleRestriction.FORTYFIVE_DEGREE;

  /** The logical parts are used for pin and gate swaw */
  Collection<PartLibrary.LogicalPartMapping> logical_part_mappings =
      new LinkedList<>();

  Collection<PartLibrary.LogicalPart> logical_parts =
      new LinkedList<>();

  /** The following objects are from the parser scope. */
  String string_quote = "\"";

  String host_cad;
  String host_version;

  boolean dsn_file_generated_by_host = true;

  boolean board_outline_ok = true;
  Communication.SpecctraParserInfo.WriteResolution write_resolution;
  /** The following objects will be initialised when the structure scope is read. */
  CoordinateTransform coordinate_transform;
  LayerStructure layer_structure;
  app.freerouting.interactive.AutorouteSettings autoroute_settings;
  app.freerouting.board.Unit unit = app.freerouting.board.Unit.MIL;
  int resolution = 100; // default resolution
  /** Creates a new instance of ReadScopeParameter */
  ReadScopeParameter(
      IJFlexScanner p_scanner,
      IBoardHandling p_board_handling,
      BoardObservers p_observers,
      IdNoGenerator p_item_id_no_generator,
      TestLevel p_test_level) {
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
