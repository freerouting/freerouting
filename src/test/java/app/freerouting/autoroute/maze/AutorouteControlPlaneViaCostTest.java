package app.freerouting.autoroute.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AutorouteControlPlaneViaCostTest {

  private static final String FIXTURE = "Plane-via-garland-4layer.dsn";

  @Test
  void planeViaCheaperThanSignalVia() throws IOException {
    var board = DsnTestFixtures.loadBoard(FIXTURE);
    board.awaitPostLoad();
    Net gnd = board.rules.nets.get("GND").iterator().next();
    assertNotNull(gnd);

    var settings = new DefaultSettings().getSettings();
    settings.setViaCosts(200);
    settings.setPlaneViaCosts(50);

    var control = new AutorouteControl(board, gnd.netNumber, settings);
    double factor = control.maxViaRadius;

    assertEquals(50 * factor, control.minNormalViaCost, 1e-6);
    // Signal-to-signal (F.Cu [0] to B.Cu [3]): costs 200
    int signalSurcharge = (int) Math.round((200 - 50) * factor);
    assertEquals(signalSurcharge, control.addViaCosts[0].toLayer[3]);
    assertEquals(200 * factor, control.minNormalViaCost + control.addViaCosts[0].toLayer[3], 1e-6);

    // Signal-to-plane (F.Cu [0] to In1.Cu [1]): costs 50
    assertEquals(0, control.addViaCosts[0].toLayer[1]);
    assertEquals(50 * factor, control.minNormalViaCost + control.addViaCosts[0].toLayer[1], 1e-6);
  }

  @Test
  void planeViaMoreExpensiveThanSignalVia() throws IOException {
    var board = DsnTestFixtures.loadBoard(FIXTURE);
    board.awaitPostLoad();
    Net gnd = board.rules.nets.get("GND").iterator().next();
    assertNotNull(gnd);

    var settings = new DefaultSettings().getSettings();
    settings.setViaCosts(50);
    settings.setPlaneViaCosts(200);

    var control = new AutorouteControl(board, gnd.netNumber, settings);
    double factor = control.maxViaRadius;

    assertEquals(50 * factor, control.minNormalViaCost, 1e-6);
    // Signal-to-signal (F.Cu [0] to B.Cu [3]): costs 50
    assertEquals(0, control.addViaCosts[0].toLayer[3]);
    assertEquals(50 * factor, control.minNormalViaCost + control.addViaCosts[0].toLayer[3], 1e-6);

    // Signal-to-plane (F.Cu [0] to In1.Cu [1]): costs 200
    int planeSurcharge = (int) Math.round((200 - 50) * factor);
    assertEquals(planeSurcharge, control.addViaCosts[0].toLayer[1]);
    assertEquals(200 * factor, control.minNormalViaCost + control.addViaCosts[0].toLayer[1], 1e-6);
  }

  @Test
  void planeViaEqualCostToSignalVia() throws IOException {
    var board = DsnTestFixtures.loadBoard(FIXTURE);
    board.awaitPostLoad();
    Net gnd = board.rules.nets.get("GND").iterator().next();
    assertNotNull(gnd);

    var settings = new DefaultSettings().getSettings();
    settings.setViaCosts(100);
    settings.setPlaneViaCosts(100);

    var control = new AutorouteControl(board, gnd.netNumber, settings);
    double factor = control.maxViaRadius;

    assertEquals(100 * factor, control.minNormalViaCost, 1e-6);
    assertEquals(0, control.addViaCosts[0].toLayer[3]);
    assertEquals(0, control.addViaCosts[0].toLayer[1]);
  }
}
