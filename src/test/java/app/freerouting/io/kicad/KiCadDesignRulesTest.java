package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ClearanceMatrix;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading a .kicad_dru file and translating its clearance rules onto a loaded board. */
class KiCadDesignRulesTest {

  private static final String DSN = "Issue110-RelayModule.dsn";
  private static final String DRU = "Issue110-RelayModule.kicad_dru";

  @Test
  void readsRulesConstraintsAndUnits() {
    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            # a comment
            (rule "a" (layer outer) (condition "A.NetClass == 'HV'")
              (constraint clearance (min 0.5mm)) (constraint track_width (min 10mil)))
            (rule b (constraint clearance (min 20mil)) (severity ignore))
            (rule "c" (constraint clearance (min 0.3)) (constraint creepage (min 1in)))
            """,
            "test.kicad_dru");
    List<KiCadDesignRules.Rule> list = rules.rules();
    assertEquals(3, list.size());
    assertEquals("outer", list.get(0).layer());
    assertEquals(500.0, list.get(0).clearanceUm(), 1e-9);
    assertEquals(List.of("track_width 0.2540 mm"), list.get(0).otherConstraints());
    assertEquals(508.0, list.get(1).clearanceUm(), 1e-9);
    assertTrue(list.get(1).ignored());
    assertNull(list.get(1).condition());
    // a value without unit is read as millimetres, which is never less strict than KiCad
    assertEquals(300.0, list.get(2).clearanceUm(), 1e-9);
    assertTrue(list.get(2).warnings().getFirst().contains("no unit"));
    assertEquals(List.of("creepage 25.40 mm"), list.get(2).otherConstraints());
  }

  @Test
  void translatesClearanceRulesOntoTheBoard() throws IOException {
    BasicBoard board = loadBoard();
    KiCadDesignRulesTranslator.Result result =
        KiCadDesignRulesTranslator.apply(
            board, KiCadDesignRules.read(TestFixtures.resolvePath(DRU)));

    assertEquals(4, result.translatedRules());
    assertTrue(result.addedClearanceClasses() > 0);
    // the nets of FU1 need their own class: 1.5 mm between them, 2 mm or more to the others
    assertEquals(
        List.of("HighVoltage", "HighVoltage_2"), result.splitNetClasses().get("HighVoltage"));
    assertTrue(result.warnings().stream().anyMatch(w -> w.contains("creepage")));
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("intersectsArea") && w.contains("may")));

    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    // the last matching rule wins: coil to contacts of the same relay, then mains isolation
    assertEquals(mm(board, 2.5), value(matrix, pin(board, "K1", "1"), pin(board, "K1", "2")));
    assertEquals(mm(board, 3.0), value(matrix, pin(board, "K1", "1"), pin(board, "K2", "2")));
    // a rule may lower the clearance of the net class (2 mm)
    assertEquals(mm(board, 1.5), traceValue(board, "Net-(FU1-Pad1)", "Net-(FU1-Pad2)"));
    // where a rule that cannot be evaluated may match and no later rule surely does, its value
    // is taken: never less strict than KiCad
    assertEquals(mm(board, 4.0), traceValue(board, "Net-(FU1-Pad1)", "Net-(FU2-Pad1)"));
    assertEquals(mm(board, 3.0), traceValue(board, "Net-(FU2-Pad1)", "GND"));
    // pairs without a matching rule keep the clearance of the design
    BasicBoard original = loadBoard();
    assertEquals(traceValue(original, "GND", "+5V"), traceValue(board, "GND", "+5V"));
  }

  @Test
  void libraryIdentifierIsTheImageNameWithoutTheVariantSuffix() throws IOException {
    BasicBoard board = loadBoard();
    // K2 is placed with the image "Реле:SRD-xxVDC-SL-C::1", a variant of K1's footprint
    assertEquals("Реле:SRD-xxVDC-SL-C", libraryId(board, "K1"));
    assertEquals("Реле:SRD-xxVDC-SL-C", libraryId(board, "K2"));
    assertEquals("Diode_THT:D_DO-41_SOD81_P7.62mm_Horizontal", libraryId(board, "VD4"));
  }

  @Test
  void footprintSelectorWithLibraryIdentifierSetsTheClearance() throws IOException {
    BasicBoard board = loadBoard();
    KiCadDesignRulesTranslator.apply(
        board,
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "around_relays"
              (condition "A.memberOfFootprint('Реле:SRD-*') && !B.memberOfFootprint('Реле:*')")
              (constraint clearance (min 7mm)))
            """,
            "test.kicad_dru"));
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    // both relays, K2 through its variant image, keep 7 mm to the other footprints
    assertEquals(mm(board, 7.0), value(matrix, pin(board, "K1", "1"), pin(board, "VD4", "1")));
    assertEquals(mm(board, 7.0), value(matrix, pin(board, "K2", "1"), pin(board, "VD4", "1")));
  }

  private static String libraryId(BasicBoard board, String reference) {
    var component = board.components.get(reference);
    assertNotNull(component, reference);
    return KiCadDesignRulesTranslator.libraryId(component);
  }

  private static BasicBoard loadBoard() throws IOException {
    InputStream input = new FileInputStream(TestFixtures.resolveFile(DSN));
    BoardReadResult result = DsnReader.readBoard(input, null, null, DSN);
    assertTrue(result instanceof BoardReadResult.Success, "fixture must load");
    return ((BoardReadResult.Success) result).board();
  }

  private static int mm(BasicBoard board, double value) {
    int units = (int) Math.ceil(board.communication.physicalToBoard(value * 1000.0, Unit.UM));
    return (units + 1) / 2 * 2;
  }

  private static Pin pin(BasicBoard board, String reference, String number) {
    for (Item item : board.getItems()) {
      if (item instanceof Pin pin
          && number.equals(pin.name())
          && reference.equals(board.components.get(pin.getComponentId()).name)) {
        return pin;
      }
    }
    throw new AssertionError("pin " + reference + "-" + number + " not found");
  }

  private static int value(ClearanceMatrix matrix, Item first, Item second) {
    return matrix.getValue(first.clearanceClassIndex(), second.clearanceClassIndex(), 0, false);
  }

  private static int traceValue(BasicBoard board, String firstNet, String secondNet) {
    var first = board.rules.nets.get(firstNet, 1);
    var second = board.rules.nets.get(secondNet, 1);
    assertNotNull(first, firstNet);
    assertNotNull(second, secondNet);
    return board.rules.clearanceMatrix.getValue(
        first.getNetClass().getTraceClearanceClass(),
        second.getNetClass().getTraceClearanceClass(),
        0,
        false);
  }
}
