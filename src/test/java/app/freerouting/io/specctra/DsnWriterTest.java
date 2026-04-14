package app.freerouting.io.specctra;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DsnWriterTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void writesValidDsnHeader() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test", false);
    String content = out.toString(StandardCharsets.UTF_8);
    assertTrue(content.startsWith("(pcb"), "DSN output must start with (pcb");
    assertTrue(content.contains("(structure"), "DSN output must contain (structure scope");
  }

  @Test
  void roundtripPreservesLayerCount() throws Exception {
    RoutingBoard original = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    int originalLayers = original.get_layer_count();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(original, out, "roundtrip", false);
    RoutingBoard reloaded = DsnTestFixtures.loadBoard(out.toByteArray());
    assertEquals(originalLayers, reloaded.get_layer_count());
  }

  @Test
  void compatModeProducesOutput() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "compat-test", true);
    String content = out.toString(StandardCharsets.UTF_8);
    assertTrue(content.startsWith("(pcb"), "Compat-mode DSN output must start with (pcb");
  }

  @Test
  void outputStreamContainsDataAfterWrite() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "flush-test", false);
    assertTrue(out.size() > 0, "Output stream must contain data after write (flush must have occurred)");
  }
}

