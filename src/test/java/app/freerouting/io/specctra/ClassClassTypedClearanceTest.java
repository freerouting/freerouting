package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.ClearanceMatrix;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * A {@code class_class} clearance rule restricted to wire item classes, e.g. {@code (type wire_wire
 * wire_via)}, must only change the clearance between the wires and vias of the two classes. The SMD
 * pads of nets whose class has its own clearance rule must keep that rule.
 */
class ClassClassTypedClearanceTest {

  private static final int CLASS_CLEARANCE = 200;
  private static final int WIRE_RULE_CLEARANCE = 1000;
  private static final int UNTYPED_RULE_CLEARANCE = 600;

  private static BasicBoard loadBoard(String classClassRules) {
    String dsn =
        """
        (pcb typed_class_class
          (resolution um 1)
          (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 30000 10000))
            (rule (width 200) (clearance 200)))
          (placement
            (component R
              (place RA 5000 5000 front 0)
              (place RB 15000 5000 front 0)
              (place RC 25000 5000 front 0)))
          (library
            (image R (pin SMD 1 -500 0) (pin SMD 2 500 0))
            (padstack SMD (shape (rect top -200 -200 200 200)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network
            (net A1 (pins RA-1 RA-2))
            (net B1 (pins RB-1 RB-2))
            (net C1 (pins RC-1 RC-2))
            (class A A1 (circuit (use_via V)) (rule (width 200) (clearance 200)))
            (class B B1 (circuit (use_via V)) (rule (width 200) (clearance 200)))
            (class C C1 (circuit (use_via V)) (rule (width 200) (clearance 200)))
        """
            + classClassRules
            + "))\n";
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return ((BoardReadResult.Success) result).board();
  }

  private static int pinClearanceClass(BasicBoard board, String netName) {
    int netNo = board.rules.nets.get(netName, 1).netNumber;
    return board.getSmdPins().stream()
        .filter(pin -> pin.containsNet(netNo))
        .map(Pin::clearanceClassIndex)
        .findFirst()
        .orElseThrow();
  }

  private static int wireClearanceClass(BasicBoard board, String netClassName) {
    return board.rules.netClasses.get(netClassName).getTraceClearanceClass();
  }

  private static int clearance(BasicBoard board, int firstClass, int secondClass) {
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    return matrix.getValue(firstClass, secondClass, 0, false);
  }

  @Test
  void wireTypedRuleDoesNotWidenPinClearances() {
    BasicBoard board =
        loadBoard("(class_class (classes A B) (rule (clearance 1000 (type wire_wire wire_via))))");
    int pinA = pinClearanceClass(board, "A1");
    int pinB = pinClearanceClass(board, "B1");
    int wireA = wireClearanceClass(board, "A");
    int wireB = wireClearanceClass(board, "B");

    assertEquals(WIRE_RULE_CLEARANCE, clearance(board, wireA, wireB), "wire to wire");
    assertEquals(CLASS_CLEARANCE, clearance(board, pinA, pinB), "pin to pin");
    assertEquals(CLASS_CLEARANCE, clearance(board, pinA, wireB), "pin to wire");
    assertEquals(CLASS_CLEARANCE, clearance(board, pinA, pinA), "pin to pin of the same class");
  }

  @Test
  void laterUntypedRuleStillReachesSeparatedPins() {
    BasicBoard board =
        loadBoard(
            "(class_class (classes A B) (rule (clearance 1000 (type wire_wire))))\n"
                + "(class_class (classes A C) (rule (clearance 600)))");
    int pinA = pinClearanceClass(board, "A1");
    int pinC = pinClearanceClass(board, "C1");
    int wireA = wireClearanceClass(board, "A");
    int wireC = wireClearanceClass(board, "C");

    assertEquals(UNTYPED_RULE_CLEARANCE, clearance(board, pinA, pinC), "pin to pin");
    assertEquals(UNTYPED_RULE_CLEARANCE, clearance(board, pinA, wireC), "pin to wire");
    assertEquals(UNTYPED_RULE_CLEARANCE, clearance(board, wireA, wireC), "wire to wire");
  }
}
