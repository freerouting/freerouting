package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates routing settings before starting autoroute to detect unrealistic
 * configurations that would cause routing failures.
 * 
 * Checks for:
 * - Clearances larger than pin spacing (causes expansion room completion
 * failure)
 * - Trace widths larger than available routing space
 * - Excessive clearance compensation values
 */
public class RoutingSettingsValidator {

    private final RoutingBoard board;
    private final List<String> warnings;
    private final List<String> errors;

    /**
     * Creates a new validator for the given board.
     * 
     * @param p_board The routing board to validate settings for
     */
    public RoutingSettingsValidator(RoutingBoard p_board) {
        this.board = p_board;
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    /**
     * Validates all routing settings and returns true if routing can proceed.
     * Logs warnings and errors found during validation.
     * 
     * @return true if settings are acceptable (may have warnings), false if
     *         critical errors found
     */
    public boolean validate() {
        warnings.clear();
        errors.clear();

        validateClearances();
        validateTraceWidths();
        validatePinSpacing();

        // Log all findings
        if (!warnings.isEmpty()) {
            FRLogger.warn("Routing settings validation found " + warnings.size() + " warnings:");
            for (String warning : warnings) {
                FRLogger.warn("  - " + warning);
            }
        }

        if (!errors.isEmpty()) {
            FRLogger.warn("Routing settings validation found " + errors.size() + " CRITICAL errors:");
            for (String error : errors) {
                FRLogger.warn("  - " + error);
            }
            FRLogger.warn("Routing may fail due to invalid settings. Please review clearances and trace widths.");
        }

        return errors.isEmpty();
    }

    /**
     * Validates clearance values across all layers and clearance classes.
     * Checks for excessive clearances that would cause obstacle inflation.
     */
    private void validateClearances() {
        ClearanceMatrix cl_matrix = board.rules.clearance_matrix;
        int layer_count = board.get_layer_count();
        int class_count = cl_matrix.get_class_count();

        // Log clearance matrix info for diagnostics
        FRLogger.debug("=== Clearance Matrix Validation ===");
        FRLogger.debug("Total clearance classes: " + class_count);
        FRLogger.debug("Total layers: " + layer_count);

        // Find maximum clearances per layer
        for (int layer = 0; layer < layer_count; layer++) {
            int max_clearance = 0;
            int min_clearance = Integer.MAX_VALUE;

            for (int class1 = 1; class1 < class_count; class1++) {
                for (int class2 = 1; class2 < class_count; class2++) {
                    int clearance = cl_matrix.get_value(class1, class2, layer, false);
                    max_clearance = Math.max(max_clearance, clearance);
                    if (clearance > 0) {
                        min_clearance = Math.min(min_clearance, clearance);
                    }
                }
            }

            FRLogger.debug("Layer " + layer + " clearances: min=" +
                    (min_clearance == Integer.MAX_VALUE ? "N/A" : min_clearance) +
                    ", max=" + max_clearance);

            // Check for unreasonably large clearances
            // Rule of thumb: clearance > 10mm (400,000 units assuming 1 unit = 0.025µm) is
            // suspicious
            if (max_clearance > 400000) {
                warnings.add("Layer " + layer + " has very large clearance: " + max_clearance +
                        " units (≈" + (max_clearance / 40000.0) + " mm). This may cause routing failures.");
            }

            // Check clearance compensation if enabled
            if (board.search_tree_manager.is_clearance_compensation_used()) {
                for (int cl_class = 1; cl_class < class_count; cl_class++) {
                    int comp_value = cl_matrix.clearance_compensation_value(cl_class, layer);
                    FRLogger.debug("  Class " + cl_class + " compensation: " + comp_value);

                    if (comp_value > 100000) {
                        warnings.add("Layer " + layer + ", class " + cl_class +
                                " has large clearance compensation: " + comp_value + " units");
                    }
                }
            }
        }
    }

    /**
     * Validates trace widths for all nets.
     * Checks if trace widths are reasonable compared to board dimensions.
     */
    private void validateTraceWidths() {
        FRLogger.debug("=== Trace Width Validation ===");

        int board_width = board.bounding_box.width();
        int board_height = board.bounding_box.height();
        int min_board_dim = Math.min(board_width, board_height);

        FRLogger.debug("Board dimensions: " + board_width + " x " + board_height);

        // Check default trace widths
        for (int layer = 0; layer < board.get_layer_count(); layer++) {
            int default_width = board.rules.get_default_trace_half_width(layer) * 2;
            FRLogger.debug("Layer " + layer + " default trace width: " + default_width);

            // Check if trace width is unreasonably large compared to board
            if (default_width > min_board_dim / 10) {
                warnings.add("Layer " + layer + " default trace width (" + default_width +
                        ") is very large compared to board size (" + min_board_dim + ")");
            }

            // Check if trace width is suspiciously large (> 5mm)
            if (default_width > 200000) {
                errors.add("Layer " + layer + " default trace width (" + default_width +
                        " units ≈ " + (default_width / 40000.0) + " mm) is unrealistically large!");
            }
        }

        // Check per-net trace widths
        for (int net_no = 1; net_no <= board.rules.nets.max_net_no(); net_no++) {
            Net net = board.rules.nets.get(net_no);
            if (net != null) {
                int trace_class = net.get_class().get_trace_clearance_class();

                for (int layer = 0; layer < board.get_layer_count(); layer++) {
                    int trace_half_width = net.get_class().get_trace_half_width(layer);
                    int trace_width = trace_half_width * 2;

                    if (trace_width > min_board_dim / 5) {
                        warnings.add("Net \"" + net.name + "\" trace width on layer " + layer +
                                " (" + trace_width + ") is very large");
                    }
                }
            }
        }
    }

    /**
     * Validates pin spacing to ensure clearances don't exceed available space.
     * This is the critical check for expansion room completion failures.
     */
    private void validatePinSpacing() {
        FRLogger.debug("=== Pin Spacing vs Clearance Validation ===");

        // Estimate minimum pin spacing by looking at components
        // This is a heuristic - actual validation would require detailed component
        // geometry
        int estimated_min_spacing = Integer.MAX_VALUE;

        // Simple heuristic: look at board density
        int item_count = 0;
        for (var item : board.get_items()) {
            item_count++;
        }

        if (item_count > 0) {
            int board_area = board.bounding_box.width() * board.bounding_box.height();
            estimated_min_spacing = (int) Math.sqrt(board_area / item_count) / 2;

            FRLogger.debug("Estimated minimum spacing: " + estimated_min_spacing +
                    " (based on " + item_count + " items)");

            // Check if maximum clearance exceeds estimated spacing
            ClearanceMatrix cl_matrix = board.rules.clearance_matrix;
            for (int layer = 0; layer < board.get_layer_count(); layer++) {
                int max_clearance = 0;
                int max_trace_width = 0;

                for (int cl_class = 1; cl_class < cl_matrix.get_class_count(); cl_class++) {
                    for (int cl_class2 = 1; cl_class2 < cl_matrix.get_class_count(); cl_class2++) {
                        int clearance = cl_matrix.get_value(cl_class, cl_class2, layer, false);
                        max_clearance = Math.max(max_clearance, clearance);
                    }

                    int trace_width = board.rules.get_default_trace_half_width(layer) * 2;
                    max_trace_width = Math.max(max_trace_width, trace_width);
                }

                int total_obstacle_size = max_clearance + max_trace_width;

                if (total_obstacle_size > estimated_min_spacing) {
                    errors.add("Layer " + layer + ": Clearance (" + max_clearance +
                            ") + trace width (" + max_trace_width + ") = " + total_obstacle_size +
                            " exceeds estimated pin spacing (" + estimated_min_spacing + ")! " +
                            "This WILL cause expansion room completion failures!");
                } else if (total_obstacle_size > estimated_min_spacing * 0.8) {
                    warnings.add("Layer " + layer + ": Clearance + trace width (" + total_obstacle_size +
                            ") is close to estimated pin spacing (" + estimated_min_spacing +
                            "). May cause routing difficulties.");
                }
            }
        }
    }

    /**
     * Gets the list of warnings found during validation.
     * 
     * @return List of warning messages
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Gets the list of errors found during validation.
     * 
     * @return List of error messages
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}
