package interactive;

import board.Communication;
import board.LayerStructure;
import board.RoutingBoard;
import board.TestLevel;
import geometry.planar.IntBox;
import geometry.planar.PolylineShape;
import rules.BoardRules;

import java.util.Locale;

/**
 * Base implementation for headless mode
 *
 * Andrey Belomutskiy
 * 6/28/2014
 */
public class BoardHandlingImpl implements IBoardHandling {
    /**
     * The file used for logging interactive action,
     * so that they can be replayed later
     */
    public final Logfile logfile = new Logfile();
    /** The current settings for interactive actions on the board*/
    public Settings settings = null;
    /** The board database used in this interactive handling. */
    protected RoutingBoard board = null;

    public BoardHandlingImpl() {
    }

    /**
     * Gets the routing board of this board handling.
     */
    @Override
    public RoutingBoard get_routing_board()
    {
        return this.board;
    }

    @Override
    public Settings get_settings() {
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
    public void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name, BoardRules p_rules, Communication p_board_communication, TestLevel p_test_level) {
        if (this.board != null)
        {
            System.out.println(" BoardHandling.create_board: board already created");
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
                outline_cl_class_no =
                        p_rules.get_default_net_class().default_item_clearance_classes.get(rules.DefaultItemClearanceClasses.ItemClass.AREA);
            }
        }
        this.board =
                new RoutingBoard(p_bounding_box, p_layer_structure, p_outline_shapes, outline_cl_class_no,
                        p_rules, p_board_communication, p_test_level);

        this.settings = new Settings(this.board, this.logfile);
    }

    @Override
    public Locale get_locale() {
        return java.util.Locale.ENGLISH;
    }
}
