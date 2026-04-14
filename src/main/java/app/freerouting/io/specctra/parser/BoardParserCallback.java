package app.freerouting.io.specctra.parser;

import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;

/**
 * Narrow callback interface used internally by the DSN parser to delegate board creation and
 * board-lifecycle events. This interface deliberately has <em>no</em> dependency on the
 * {@code interactive} or {@code gui} packages so that the parser stays self-contained.
 *
 * <p>The only production implementation is the package-private {@code MinimalBoardManager}
 * nested inside {@link ReadScopeParameter}.
 */
interface BoardParserCallback {

  /** Returns the board that was created, or {@code null} if the board has not been constructed yet. */
  RoutingBoard get_routing_board();

  /**
   * Called by the structure reader once it has parsed the board outline, layers, and rules.
   * Implementations should create and store a new {@link RoutingBoard} from the given parameters.
   */
  void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure,
      PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name,
      BoardRules p_rules, Communication p_board_communication);

  /**
   * Called after board creation to populate per-layer manual trace widths from the default net
   * class. Implementations that have no interactive settings may provide a no-op body.
   */
  void initialize_manual_trace_half_widths();

  /**
   * Returns the active {@link RoutingJob} associated with this parse context, or {@code null}
   * when operating in isolation (e.g. pure DSN-reader mode without a routing job).
   */
  RoutingJob getCurrentRoutingJob();
}

