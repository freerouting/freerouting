package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.board.BoardLoader;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.management.SessionManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.sources.TestingSettings;
import app.freerouting.tests.TestBasedOnAnIssue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DrcCoordinateTest extends TestBasedOnAnIssue {

    @BeforeEach
    protected void setUp() {
        Freerouting.globalSettings = new GlobalSettings();
    }

    @Test
    void test_DrcCoordinates_AreInCorrectRange() {
        // Get the job with injected settings
        RoutingJob job = GetRoutingJob("Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn");

        assertNotNull(job, "Job should not be null");

        BoardLoader.loadBoardIfNeeded(job);

        assertNotNull(job.board, "Board should be loaded");

        // Create DRC checker
        DesignRulesChecker drcChecker = new DesignRulesChecker(job.board, Freerouting.globalSettings.drcSettings);

        // Generate JSON report in mm
        String jsonReport = drcChecker.generateReportJson("test.dsn", "mm");

        // Parse JSON
        JsonObject json = JsonParser
                .parseString(jsonReport)
                .getAsJsonObject();

        // Check unconnected items
        JsonArray unconnectedItems = json.getAsJsonArray("unconnected_items");
        if (unconnectedItems != null && unconnectedItems.size() > 0) {
            JsonObject firstItem = unconnectedItems
                    .get(0)
                    .getAsJsonObject();
            JsonArray items = firstItem.getAsJsonArray("items");
            if (items != null && items.size() > 0) {
                JsonObject item = items
                        .get(0)
                        .getAsJsonObject();
                JsonObject pos = item.getAsJsonObject("pos");
                double x = pos
                        .get("x")
                        .getAsDouble();
                double y = pos
                        .get("y")
                        .getAsDouble();

                // For a typical PCB, coordinates in mm should be in the range of ~50-300mm
                // If coordinates are 10x too large, they would be in the range of ~500-3000mm
                // We'll check that coordinates are less than 500mm to ensure they're not 10x
                // too large
                assertTrue(Math.abs(x) < 500, "X coordinate should be less than 500mm, but was: " + x);
                assertTrue(Math.abs(y) < 500, "Y coordinate should be less than 500mm, but was: " + y);

                // Also check that coordinates are not too small (should be at least a few mm)
                assertTrue(Math.abs(x) > 10, "X coordinate should be greater than 10mm, but was: " + x);
                assertTrue(Math.abs(y) > 10, "Y coordinate should be greater than 10mm, but was: " + y);

                System.out.println("First DRC item coordinates: x=" + x + ", y=" + y);
            }
        }
    }
}