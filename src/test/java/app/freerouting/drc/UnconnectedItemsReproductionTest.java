package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.TileShape;
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

        // Check 2402 -> 321 connection
        Collection<Item> contacts1 = item1.get_normal_contacts();
        boolean connectedTo321 = item321 != null && contacts1.contains(item321);
        System.out.println("2402 connected to 321: " + connectedTo321);

        boolean isDangling = ((Trace) item1).is_tail();
        System.out.println("Item 2402 is_tail: " + isDangling);

        if (item321 != null) {
            assertTrue(connectedTo321, "Item 2402 MUST connect to Item 321");
        }
    }

    @Test
    void test_Connectivity_Of_Via_2522() {
        // Load the problematic board
        RoutingJob job = getRoutingJobFromTestFile("Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn");
        assertNotNull(job, "Job should not be null");
        assertNotNull(job.board, "Board should be loaded");

        BasicBoard board = job.board;

        System.out.println("--- Debugging Via 2522 ---");
        Item via2522 = board.get_items().stream()
                .filter(item -> item.get_id_no() == 2522)
                .findFirst()
                .orElse(null);

        if (via2522 != null) {
            System.out.println("Found Via 2522. Layers: " + via2522.first_layer() + " to " + via2522.last_layer());
            System.out.println("Via 2522 is_tail: " + ((Via) via2522).is_tail());

            Collection<Item> contacts = via2522.get_normal_contacts();
            System.out.println("Via 2522 contacts count: " + contacts.size());
            for (Item contact : contacts) {
                System.out.println(
                        " - Contact: " + contact.get_id_no() + " (" + contact.getClass().getSimpleName() + ")");
            }
        } else {
            fail("Via 2522 not found in the board.");
        }
    }

    @Test
    void test_Connectivity_Of_Trace_2576() {
        // Load the problematic board
        RoutingJob job = getRoutingJobFromTestFile("Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn");
        assertNotNull(job, "Job should not be null");
        assertNotNull(job.board, "Board should be loaded");

        BasicBoard board = job.board;

        System.out.println("--- Debugging Trace 2576 ---");
        Item trace2576 = board.get_items().stream()
                .filter(item -> item.get_id_no() == 2576)
                .findFirst()
                .orElse(null);

        if (trace2576 != null) {
            Trace tr = (Trace) trace2576;
            System.out.println("Found Trace 2576. Layer: " + tr.first_layer());
            System.out.println("Trace 2576 is_tail: " + tr.is_tail());

            Collection<Item> startContacts = tr.get_start_contacts();
            System.out.println("Start Contacts: " + startContacts.size());
            for (Item contact : startContacts) {
                System.out.println(
                        " - Contact: " + contact.get_id_no() + " (" + contact.getClass().getSimpleName() + ")");
            }

            Collection<Item> endContacts = tr.get_end_contacts();
            System.out.println("End Contacts: " + endContacts.size());
            for (Item contact : endContacts) {
                System.out.println(
                        " - Contact: " + contact.get_id_no() + " (" + contact.getClass().getSimpleName() + ")");
            }

            System.out.println("Trace Start Point: " + tr.first_corner());
            System.out.println("Trace End Point: " + tr.last_corner());

            // Check what is at the end point
            IntPoint endPoint = new IntPoint(1661000, -994139); // Hardcoded from previous output for precision check
            System.out.println("Checking items at end point: " + endPoint);
            TileShape pointShape = TileShape.get_instance(endPoint);
            Collection<SearchTreeObject> itemsAtEnd = board.overlapping_objects(pointShape, tr.first_layer());

            System.out.println("Items at End Point: " + itemsAtEnd.size());
            for (SearchTreeObject obj : itemsAtEnd) {
                if (obj instanceof Item) {
                    Item item = (Item) obj;
                    System.out.println(" - Item: " + item.get_id_no() + " (" + item.getClass().getSimpleName()
                            + ") Layer: " + item.first_layer() + " to " + item.last_layer());
                } else {
                    System.out.println(" - Object: " + obj.getClass().getSimpleName());
                }
            }

        } else {
            fail("Trace 2576 not found in the board.");
        }
    }

    private RoutingJob getRoutingJobFromTestFile(String filename) {
        UUID sessionId = UUID.randomUUID();
        Session session = SessionManager
                .getInstance()
                .createSession(sessionId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

        RoutingJob job = new RoutingJob(session.id);

        Path testDirectory = Path.of(".").toAbsolutePath();
        File testFile = Path.of(testDirectory.toString(), "tests", filename).toFile();

        while (!testFile.exists()) {
            testDirectory = testDirectory.getParent();
            if (testDirectory == null) {
                fail("Test file not found: " + filename);
            }
            testFile = Path.of(testDirectory.toString(), "tests", filename).toFile();
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
