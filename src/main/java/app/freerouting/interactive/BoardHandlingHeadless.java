package app.freerouting.interactive;

import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;

import java.util.Locale;

/**
 * Base implementation for headless mode
 */
public class BoardHandlingHeadless implements IBoardHandling
{
  /**
   * The file used for logging interactive action, so that they can be replayed later
   */
  public final ActivityReplayFile activityReplayFile = new ActivityReplayFile();
  /**
   * The current settings for interactive actions on the board
   */
  public Settings settings;
  /**
   * The listener for the autorouter thread
   */
  public ThreadActionListener autorouter_listener;
  /**
   * The board object that contains all the data for the board
   */
  protected RoutingBoard board;
  protected Locale locale;

  public BoardHandlingHeadless(Locale p_locale)
  {
    this.locale = p_locale;
  }

  /**
   * Gets the routing board of this board handling.
   */
  @Override
  public RoutingBoard get_routing_board()
  {
    return this.board;
  }

  public synchronized void update_routing_board(RoutingBoard routing_board)
  {
    this.board = routing_board;
  }

  @Override
  public Settings get_settings()
  {
    return settings;
  }

  /**
   * Initializes the manual trace widths from the default trace widths in the board rules.
   */
  @Override
  public void initialize_manual_trace_half_widths()
  {
    for (int i = 0; i < settings.manual_trace_half_width_arr.length; ++i)
    {
      settings.manual_trace_half_width_arr[i] = this.board.rules.get_default_net_class().get_trace_half_width(i);
    }
  }

  @Override
  public void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name, BoardRules p_rules, Communication p_board_communication)
  {
    if (this.board != null)
    {
      FRLogger.warn(" BoardHandling.create_board: board already created");
    }
    int outline_cl_class_no = 0;

    if (p_rules != null)
    {
      if (p_outline_clearance_class_name != null && p_rules.clearance_matrix != null)
      {
        outline_cl_class_no = p_rules.clearance_matrix.get_no(p_outline_clearance_class_name);
        outline_cl_class_no = Math.max(outline_cl_class_no, 0);
      }
      else
      {
        outline_cl_class_no = p_rules.get_default_net_class().default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA);
      }
    }
    this.board = new RoutingBoard(p_bounding_box, p_layer_structure, p_outline_shapes, outline_cl_class_no, p_rules, p_board_communication);

    this.settings = new Settings(this.board, this.activityReplayFile);
  }

  @Override
  public Locale get_locale()
  {
    return this.locale;
  }
}