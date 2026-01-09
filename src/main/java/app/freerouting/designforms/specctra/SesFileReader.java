package app.freerouting.designforms.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.FixedState;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a Specctra session file (.ses) and imports the routing data (wires and
 * vias) into the board.
 * This allows restoring previously routed designs.
 */
public class SesFileReader {

    private final IJFlexScanner scanner;
    private final BasicBoard board;
    private final LayerStructure specctra_layer_structure;
    private final double session_file_scale_denominator;
    private int wires_imported = 0;
    private int vias_imported = 0;
    private int errors_encountered = 0;

    private SesFileReader(IJFlexScanner p_scanner, BasicBoard p_board, double p_session_file_scale_denominator) {
        scanner = p_scanner;
        board = p_board;
        this.specctra_layer_structure = new LayerStructure(p_board.layer_structure);
        session_file_scale_denominator = p_session_file_scale_denominator;
    }

    /**
     * Reads a SES file and imports the routing data into the board.
     *
     * @param p_session Input stream of the SES file
     * @param p_board   The board to import routing data into
     * @return true if successful, false if an error occurred
     */
    public static boolean read(InputStream p_session, BasicBoard p_board) {
        if (p_session == null || p_board == null) {
            FRLogger.warn("SesFileReader.read: null input");
            return false;
        }

        // Create a scanner for reading the session file
        IJFlexScanner scanner = new SpecctraDsnStreamReader(p_session);

        // SES files use a specific scale factor: dsn_to_board(1) / resolution
        // This matches how SpecctraSesFileWriter writes SES files (line 69)
        double scale_factor = p_board.communication.coordinate_transform.dsn_to_board(1)
                / p_board.communication.resolution;

        SesFileReader reader = new SesFileReader(scanner, p_board, scale_factor);

        boolean result;
        try {
            result = reader.process_session_scope();
            if (result) {
                FRLogger.info("SES file import complete: " + reader.wires_imported + " wires, " +
                        reader.vias_imported + " vias imported" +
                        (reader.errors_encountered > 0 ? " (" + reader.errors_encountered + " errors)" : ""));
            }
        } catch (IOException e) {
            FRLogger.error("Unable to process SES file", e);
            result = false;
        }

        // Close the input stream
        try {
            p_session.close();
        } catch (IOException e) {
            FRLogger.error("Unable to close SES file", e);
        }

        return result;
    }

    /**
     * Processes the outermost scope of the session file.
     */
    private boolean process_session_scope() throws IOException {
        // Read the first line of the session file: (session ...
        Object next_token = null;
        for (int i = 0; i < 3; i++) {
            next_token = this.scanner.next_token();
            boolean keyword_ok = true;
            if (i == 0) {
                keyword_ok = next_token == Keyword.OPEN_BRACKET;
            } else if (i == 1) {
                keyword_ok = next_token == Keyword.SESSION;
                this.scanner.yybegin(SpecctraDsnStreamReader.NAME); // to overread the name of the pcb for i = 2
            }
            if (!keyword_ok) {
                FRLogger.warn("SesFileReader.process_session_scope: specctra session file format expected");
                return false;
            }
        }

        // Read the direct subscopes of the session scope
        for (;;) {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null) {
                // end of file
                return true;
            }
            if (next_token == Keyword.CLOSED_BRACKET) {
                // end of scope
                break;
            }

            if (prev_token == Keyword.OPEN_BRACKET) {
                if (next_token == Keyword.ROUTES) {
                    if (!process_routes_scope()) {
                        return false;
                    }
                } else {
                    // Skip all other scopes (placement, etc.) - we only care about routes
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        return true;
    }

    /**
     * Processes the routes scope containing network data.
     */
    private boolean process_routes_scope() throws IOException {
        Object next_token = null;
        for (;;) {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null) {
                // unexpected end of file
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET) {
                // end of scope
                break;
            }

            if (prev_token == Keyword.OPEN_BRACKET) {
                if (next_token == Keyword.NETWORK_OUT) {
                    if (!process_network_scope()) {
                        return false;
                    }
                } else {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        return true;
    }

    /**
     * Processes the network scope containing individual nets.
     */
    private boolean process_network_scope() throws IOException {
        Object next_token = null;
        // read the net scopes
        for (;;) {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null) {
                // unexpected end of file
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET) {
                // end of scope
                break;
            }

            if (prev_token == Keyword.OPEN_BRACKET) {
                if (next_token == Keyword.NET) {
                    if (!process_net_scope()) {
                        return false;
                    }
                } else {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        return true;
    }

    /**
     * Processes a single net scope containing wires and vias.
     */
    private boolean process_net_scope() throws IOException {
        // read the net name
        Object next_token = this.scanner.next_token();
        if (!(next_token instanceof String net_name)) {
            FRLogger.warn("SesFileReader.process_net_scope: String expected at '" + this.scanner.get_scope_identifier()
                    + "'");
            return false;
        }
        this.scanner.set_scope_identifier(net_name);

        // Get the net number from the net name (subnet_number is 1 for normal nets)
        Net net = board.rules.nets.get(net_name, 1);
        if (net == null) {
            FRLogger.warn("SesFileReader: Net not found: " + net_name + " - skipping");
            errors_encountered++;
            // Skip this net scope
            ScopeKeyword.skip_scope(this.scanner);
            return true;
        }
        int net_no = net.net_number;
        int[] net_no_arr = new int[] { net_no };

        // read the wires and vias of this net
        for (;;) {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null) {
                // end of file
                return true;
            }
            if (next_token == Keyword.CLOSED_BRACKET) {
                // end of scope
                break;
            }

            if (prev_token == Keyword.OPEN_BRACKET) {
                if (next_token == Keyword.WIRE) {
                    if (!process_wire_scope(net_no_arr)) {
                        // Don't fail completely, just log and continue
                        errors_encountered++;
                    }
                } else if (next_token == Keyword.VIA) {
                    if (!process_via_scope(net_no_arr)) {
                        // Don't fail completely, just log and continue
                        errors_encountered++;
                    }
                } else {
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        return true;
    }

    /**
     * Processes a wire scope and imports the trace into the board.
     */
    private boolean process_wire_scope(int[] p_net_no_arr) throws IOException {
        PolygonPath wire_path = null;
        Object next_token = null;
        for (;;) {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null) {
                FRLogger.warn("SesFileReader.process_wire_scope: unexpected end of file at '"
                        + this.scanner.get_scope_identifier() + "'");
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET) {
                // end of scope
                break;
            }
            if (prev_token == Keyword.OPEN_BRACKET) {
                if (next_token == Keyword.POLYGON_PATH) {
                    wire_path = Shape.read_polygon_path_scope(this.scanner, this.specctra_layer_structure);
                } else {
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }

        if (wire_path == null) {
            // conduction areas are skipped
            return true;
        }

        try {
            // Get layer number
            int layer_no = wire_path.layer.no;

            // Convert coordinates from SES file scale to board scale
            int[] board_coordinates = new int[wire_path.coordinate_arr.length];
            for (int i = 0; i < wire_path.coordinate_arr.length; i++) {
                board_coordinates[i] = (int) Math.round(wire_path.coordinate_arr[i] / session_file_scale_denominator);
            }

            // Create polyline from coordinates
            Point[] points = new Point[board_coordinates.length / 2];
            for (int i = 0; i < points.length; i++) {
                points[i] = Point.get_instance(
                        board_coordinates[2 * i],
                        board_coordinates[2 * i + 1]);
            }

            Polyline polyline = new Polyline(points);

            // Calculate half width
            int half_width = (int) Math.round(wire_path.width / (2.0 * session_file_scale_denominator));

            // Get clearance class (use default for now)
            int clearance_class = board.rules.get_default_net_class().default_item_clearance_classes
                    .get(app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);

            // Insert the trace into the board
            board.insert_trace(polyline, layer_no, half_width, p_net_no_arr, clearance_class, FixedState.USER_FIXED);

            wires_imported++;
            return true;

        } catch (Exception e) {
            FRLogger.warn("SesFileReader.process_wire_scope: failed to import wire - " + e.getMessage());
            return false;
        }
    }

    /**
     * Processes a via scope and imports the via into the board.
     */
    private boolean process_via_scope(int[] p_net_no_arr) throws IOException {
        // read the padstack name
        Object next_token = this.scanner.next_token();
        if (!(next_token instanceof String padstack_name)) {
            FRLogger.warn("SesFileReader.process_via_scope: padstack name expected at '"
                    + this.scanner.get_scope_identifier() + "'");
            return false;
        }
        this.scanner.set_scope_identifier(padstack_name);

        // read the location
        double[] location = new double[2];
        for (int i = 0; i < 2; i++) {
            next_token = this.scanner.next_token();
            if (next_token instanceof Double double1) {
                location[i] = double1;
            } else if (next_token instanceof Integer integer) {
                location[i] = integer;
            } else {
                FRLogger.warn("SesFileReader.process_via_scope: number expected at '"
                        + this.scanner.get_scope_identifier() + "'");
                return false;
            }
        }

        // Skip any additional scopes
        next_token = this.scanner.next_token();
        while (next_token == Keyword.OPEN_BRACKET) {
            ScopeKeyword.skip_scope(this.scanner);
            next_token = this.scanner.next_token();
        }

        if (next_token != Keyword.CLOSED_BRACKET) {
            FRLogger.warn("SesFileReader.process_via_scope: closing bracket expected at '"
                    + this.scanner.get_scope_identifier() + "'");
            return false;
        }

        try {
            // Get the padstack
            Padstack via_padstack = this.board.library.padstacks.get(padstack_name);
            if (via_padstack == null) {
                FRLogger.warn("SesFileReader.process_via_scope: via padstack not found: " + padstack_name);
                return false;
            }

            // Convert coordinates from SES file scale to board scale
            int x = (int) Math.round(location[0] / session_file_scale_denominator);
            int y = (int) Math.round(location[1] / session_file_scale_denominator);
            Point via_location = Point.get_instance(x, y);

            // Get clearance class (use default for now)
            int clearance_class = board.rules.get_default_net_class().default_item_clearance_classes
                    .get(app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.VIA);

            // Insert the via into the board
            board.insert_via(via_padstack, via_location, p_net_no_arr, clearance_class, FixedState.USER_FIXED, true);

            vias_imported++;
            return true;

        } catch (Exception e) {
            FRLogger.warn("SesFileReader.process_via_scope: failed to import via - " + e.getMessage());
            return false;
        }
    }
}
