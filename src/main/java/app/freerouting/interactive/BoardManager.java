package app.freerouting.interactive;

import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;

import java.util.Locale;

public interface BoardManager
{
  Locale get_locale();

  RoutingBoard get_routing_board();

  void initialize_manual_trace_half_widths();

  void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name, BoardRules p_rules, Communication p_board_communication);

  Settings get_settings();
}