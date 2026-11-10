package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.io.BoardReadResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Specctra DSN test verifying that nets and default pad nets differing only in case
 * remain separate and are not falsely merged into multi-pin nets.
 */
class NetNameCaseTest {

  @Test
  void mixedCaseNetsAndPadLabelsRemainDistinct() {
    String dsn =
        """
        (pcb case_test (resolution um 10)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 50000 50000))
            (via V)
            (rule (width 200) (clearance 200))
          )
          (placement
            (component "SW_ENCODER"
              (place SW1 25000 25000 front 0)
            )
            (component "CONN_1PIN"
              (place J1 10000 10000 front 0)
              (place J2 40000 10000 front 0)
            )
          )
          (library
            (image "SW_ENCODER"
              (pin p A -2000 -2000)
              (pin p a 2000 -2000)
              (pin p B -2000 0)
              (pin p b 2000 0)
              (pin p C -2000 2000)
              (pin p c 2000 2000)
            )
            (image "CONN_1PIN"
              (pin p 1 0 0)
            )
            (padstack p
              (shape (circle top 500))
              (shape (circle bottom 500))
            )
            (padstack V
              (shape (circle top 600))
              (shape (circle bottom 600))
            )
          )
          (network
            (net F
              (pins J1-1)
            )
            (net f
              (pins J2-1)
            )
            (net "Net-(SW1-PadA)"
              (pins SW1-A)
            )
            (net "Net-(SW1-Pada)"
              (pins SW1-a)
            )
            (net "Net-(SW1-PadB)"
              (pins SW1-B)
            )
            (net "Net-(SW1-Padb)"
              (pins SW1-b)
            )
            (net "Net-(SW1-PadC)"
              (pins SW1-C)
            )
            (net "Net-(SW1-Padc)"
              (pins SW1-c)
            )
          )
        )
        """;

    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null, "test");
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    // Verify board net rules have 8 separate nets
    assertEquals(8, board.rules.nets.maxNetNumber());

    var netF = board.rules.nets.get("F", 1);
    var netf = board.rules.nets.get("f", 1);
    assertNotNull(netF);
    assertNotNull(netf);
    assertNotEquals(netF.netNumber, netf.netNumber);

    var netSW1_A = board.rules.nets.get("Net-(SW1-PadA)", 1);
    var netSW1_a = board.rules.nets.get("Net-(SW1-Pada)", 1);
    assertNotNull(netSW1_A);
    assertNotNull(netSW1_a);
    assertNotEquals(netSW1_A.netNumber, netSW1_a.netNumber);

    var netSW1_B = board.rules.nets.get("Net-(SW1-PadB)", 1);
    var netSW1_b = board.rules.nets.get("Net-(SW1-Padb)", 1);
    assertNotNull(netSW1_B);
    assertNotNull(netSW1_b);
    assertNotEquals(netSW1_B.netNumber, netSW1_b.netNumber);

    var netSW1_C = board.rules.nets.get("Net-(SW1-PadC)", 1);
    var netSW1_c = board.rules.nets.get("Net-(SW1-Padc)", 1);
    assertNotNull(netSW1_C);
    assertNotNull(netSW1_c);
    assertNotEquals(netSW1_C.netNumber, netSW1_c.netNumber);

    // Map pins by ComponentName-PinName
    Map<String, Pin> pinsByName =
        board.getPins().stream()
            .collect(
                Collectors.toMap(
                    p -> board.components.get(p.getComponentId()).name + "-" + p.name(), p -> p));

    assertEquals(8, pinsByName.size());

    // Verify J1-1 and J2-1 are on different nets
    Pin pinJ1 = pinsByName.get("J1-1");
    Pin pinJ2 = pinsByName.get("J2-1");
    assertNotNull(pinJ1);
    assertNotNull(pinJ2);
    assertEquals(netF.netNumber, pinJ1.getNetNumber(0));
    assertEquals(netf.netNumber, pinJ2.getNetNumber(0));
    assertNotEquals(pinJ1.getNetNumber(0), pinJ2.getNetNumber(0));

    // Verify SW1 pins are on different nets
    Pin pinA = pinsByName.get("SW1-A");
    Pin pina = pinsByName.get("SW1-a");
    assertNotNull(pinA);
    assertNotNull(pina);
    assertEquals(netSW1_A.netNumber, pinA.getNetNumber(0));
    assertEquals(netSW1_a.netNumber, pina.getNetNumber(0));
    assertNotEquals(pinA.getNetNumber(0), pina.getNetNumber(0));

    Pin pinB = pinsByName.get("SW1-B");
    Pin pinb = pinsByName.get("SW1-b");
    assertNotNull(pinB);
    assertNotNull(pinb);
    assertEquals(netSW1_B.netNumber, pinB.getNetNumber(0));
    assertEquals(netSW1_b.netNumber, pinb.getNetNumber(0));
    assertNotEquals(pinB.getNetNumber(0), pinb.getNetNumber(0));

    Pin pinC = pinsByName.get("SW1-C");
    Pin pinc = pinsByName.get("SW1-c");
    assertNotNull(pinC);
    assertNotNull(pinc);
    assertEquals(netSW1_C.netNumber, pinC.getNetNumber(0));
    assertEquals(netSW1_c.netNumber, pinc.getNetNumber(0));
    assertNotEquals(pinC.getNetNumber(0), pinc.getNetNumber(0));

    // Since each net has only 1 pin, each pin's unconnected set must be empty (no airwires to
    // route)
    for (Pin pin : board.getPins()) {
      assertEquals(0, pin.getUnconnectedSet(pin.getNetNumber(0)).size());
    }
  }
}
