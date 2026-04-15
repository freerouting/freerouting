package app.freerouting.io.specctra.parser;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class that contains some structured properties and helper functions for the DSN parser.
 */
public class ReadScopeParameter {

  final IJFlexScanner scanner;
  final BoardParserCallback board_handling;
  final NetList netlist = new NetList();
  final BoardObservers observers;
  final IdentificationNumberGenerator item_id_no_generator;

  /**
   * Warnings collected during DSN parsing (e.g. skipped wires, missing padstacks, degenerate
   * geometry). Callers can retrieve these via {@link #getWarnings()} after the read completes.
   */
  public final List<String> warnings = new ArrayList<>();
  /**
   * Collection of elements of class PlaneInfo. The plane cannot be inserted directly into the boards, because the layers may not be read completely.
   */
  final Collection<PlaneInfo> plane_list = new LinkedList<>();
  /**
   * Component placement information. It is filled while reading the placement scope and can be evaluated after reading the library and network scope.
   */
  final Collection<ComponentPlacement> placement_list = new LinkedList<>();
  final Collection<String[]> constants = new LinkedList<>();
  /**
   * The names of the via padstacks filled while reading the structure scope and evaluated after reading the library scope.
   */
  Collection<String> via_padstack_names;

  boolean via_at_smd_allowed;
  public AngleRestriction snap_angle = AngleRestriction.FORTYFIVE_DEGREE;

  /**
   * The logical parts are used for pin and gate swaw
   */
  Collection<PartLibrary.LogicalPartMapping> logical_part_mappings = new LinkedList<>();

  Collection<PartLibrary.LogicalPart> logical_parts = new LinkedList<>();

  /**
   * The following objects are from the parser scope.
   */
  public String string_quote = "\"";

  public String host_cad;
  public String host_version;

  boolean dsn_file_generated_by_host = true;

  /** Set to {@code false} by the structure reader when the board outline is absent. */
  public boolean board_outline_ok = true;
  public Communication.SpecctraParserInfo.WriteResolution write_resolution;
  /**
   * The following objects will be initialised when the structure scope is read.
   */
  public CoordinateTransform coordinate_transform;
  public LayerStructure layer_structure;
  /** Nullable — only populated when an {@code (autoroute ...)} scope is present in the DSN file. */
  public RouterSettings autoroute_settings;
  public Unit unit = Unit.MIL;
  public int resolution = 100; // default resolution

  /**
   * Creates a new instance of ReadScopeParameter without an external board manager.
   * An internal minimal shim is constructed to receive the parsed board.
   * Use this constructor from {@link app.freerouting.io.specctra.DsnReader#readBoard}.
   *
   * @param p_scanner              the token scanner over the DSN input stream
   * @param p_observers            nullable; for host-system embedding
   * @param p_item_id_no_generator nullable; for host-system embedding
   */
  public ReadScopeParameter(IJFlexScanner p_scanner, BoardObservers p_observers,
      IdentificationNumberGenerator p_item_id_no_generator) {
    scanner = p_scanner;
    board_handling = new MinimalBoardManager();
    observers = p_observers;
    item_id_no_generator = p_item_id_no_generator;
  }

  /**
   * Returns the board that was created during parsing, or {@code null} if parsing
   * has not yet reached the board-construction step.
   */
  public BasicBoard getBoard() {
    return board_handling.get_routing_board();
  }

  /**
   * Returns an unmodifiable view of the warnings collected during DSN parsing.
   * The list is populated as the file is read; call this method after the read completes.
   */
  public List<String> getWarnings() {
    return java.util.Collections.unmodifiableList(warnings);
  }

  // -------------------------------------------------------------------------
  // Minimal internal shim — satisfies the BoardParserCallback contract during
  // parsing without requiring a HeadlessBoardManager or a RoutingJob.
  // -------------------------------------------------------------------------

  private static final class MinimalBoardManager implements BoardParserCallback {

    private RoutingBoard board;

    @Override
    public RoutingBoard get_routing_board() {
      return board;
    }

    @Override
    public void create_board(IntBox p_bounding_box,
        app.freerouting.board.LayerStructure p_layer_structure,
        PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name,
        BoardRules p_rules, Communication p_board_communication) {
      int outlineClearanceNo = 0;
      if (p_rules != null) {
        if (p_outline_clearance_class_name != null && p_rules.clearance_matrix != null) {
          outlineClearanceNo = Math.max(0,
              p_rules.clearance_matrix.get_no(p_outline_clearance_class_name));
        } else {
          outlineClearanceNo = p_rules.get_default_net_class()
              .default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA);
        }
      }
      board = new RoutingBoard(p_bounding_box, p_layer_structure, p_outline_shapes,
          outlineClearanceNo, p_rules, p_board_communication);
    }

    @Override
    public void initialize_manual_trace_half_widths() {
      // no-op: no InteractiveSettings in headless shim
    }


    @Override
    public RoutingJob getCurrentRoutingJob() {
      return null;
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Information for inserting a plane
   */
  static class PlaneInfo {

    final Shape.ReadAreaScopeResult area;
    final String net_name;

    PlaneInfo(Shape.ReadAreaScopeResult p_area, String p_net_name) {
      area = p_area;
      net_name = p_net_name;
    }
  }
}