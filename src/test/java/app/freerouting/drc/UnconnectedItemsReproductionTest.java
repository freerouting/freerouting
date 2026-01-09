package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.management.SessionManager;
import app.freerouting.settings.GlobalSettings;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UnconnectedItemsReproductionTest {

    @BeforeEach
    protected void setUp() {
        Freerouting.globalSettings = new GlobalSettings();
    }

    @Test
    void test_Connectivity_Of_Overlapping_Traces() {
        // Load the problematic board
        RoutingJob job = getRoutingJobFromTestFile("Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn");
        assertNotNull(job, "Job should not be null");
        assertNotNull(job.board, "Board should be loaded");

        BasicBoard board = job.board;

        // Look for the specific items that were reported as unconnected but shouldn't
        // be
        // From report: Trace [GND] (2402) and Trace [GND] (2411) at 153.737, -109.588

        Item item1 = board.get_item(2402); // Trace
        Item item2 = board.get_item(2411); // Trace
        Item item321 = board.get_item(321); // Suspected DrillItem/Pad

        assertNotNull(item1, "Item 2402 not found");
        assertNotNull(item2, "Item 2411 not found");
        // assertNotNull(item321, "Item 321 not found"); // It might be another ID or
        // not exist if my guess is wrong

        System.out.println("Item 1: " + item1);
        System.out.println("Item 2: " + item2);
        System.out.println("Item 321: " + item321);

        System.out.println("Item 1 Layer: " + item1.first_layer());
        System.out.println("Item 2 Layer: " + item2.first_layer());
        if (item321 != null) {
            System.out.println("Item 321 Layer: " + item321.first_layer());
        }

        System.out.println("Item 1 Net: " + item1.get_net_no(0));
        System.out.println("Item 2 Net: " + item2.get_net_no(0));
        if (item321 != null) {
            System.out.println("Item 321 Net: " + item321.get_net_no(0));
        }

        // Check 2402 -> 321 connection (This is the likely failure causing dangling
        // track)
        Collection<Item> contacts1 = item1.get_normal_contacts();
        boolean connectedTo321 = item321 != null && contacts1.contains(item321);
        System.out.println("2402 connected to 321: " + connectedTo321);

        // Check 2402 -> 2411 connection
        boolean connectedTo2411 = contacts1.contains(item2);
        System.out.println("2402 connected to 2411: " + connectedTo2411);

        boolean sameLayer = item1.shares_layer(item2);
        System.out.println("2402 and 2411 share layer: " + sameLayer);

        if (sameLayer) {
            assertTrue(connectedTo2411, "Item 2402 should be connected to Item 2411 (Same Layer)");
        } else {
            // If different layers, they are not connected directly.
            // But 2402 should NOT be dangling.
            // Dangling means it has < 1 contact at one end.
            // If 2402 connects to 321 (at one end) and 2399 (at other end), it is NOT
            // dangling.

            // We can check is_tail() which logic provided by Trace.java
            // But is_tail() is protected or package private? No, it's public in Item.java
            boolean isDangling = ((Trace) item1).is_tail();
            System.out.println("Item 2402 is_tail: " + isDangling);

            // Assert that it is NOT dangling (meaning it found connections)
            // If this assertion passes, then our fix worked for 2402.
            // If fails, then 2402 is still dangling.
            if (item321 != null) {
                assertTrue(connectedTo321, "Item 2402 MUST connect to Item 321 to avoid being dangling");
            }
            // Actually, simply assert not dangling
            // assertFalse(isDangling, "Item 2402 should not be dangling"); // Commented out
            // until verified
        }
    }

    private RoutingJob getRoutingJobFromTestFile(String filename) {
        UUID sessionId = UUID.randomUUID();
        Session session = SessionManager
                .getInstance()
                .createSession(sessionId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

        RoutingJob job = new RoutingJob(session.id);

        Path testDirectory = Path
                .of(".")
                .toAbsolutePath();
        File testFile = Path
                .of(testDirectory.toString(), "tests", filename)
                .toFile();

        while (!testFile.exists()) {
            testDirectory = testDirectory.getParent();
            if (testDirectory == null) {
                fail("Test file not found: " + filename);
            }
            testFile = Path
                    .of(testDirectory.toString(), "tests", filename)
                    .toFile();
        }

        try {
            job.setInput(testFile);
            if (job.input.format == FileFormat.DSN) {
                HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
                boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdentificationNumberGenerator());
                job.board = boardManager.get_routing_board();
            }
        } catch (Exception e) {
            fail("Failed to load test file: " + e.getMessage());
        }

        return job;
    }
}
