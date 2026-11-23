package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ClearanceMatrix;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** Reading a .kicad_dru file and translating its clearance rules onto a loaded board. */
class KiCadDesignRulesTest {

  private static final String DSN = "Issue110-RelayModule.dsn";
  private static final String DRU = "Issue110-RelayModule.kicad_dru";

  @Test
  void warnsAboutNamesThatMatchNothingInTheDesign() throws IOException {
    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "renamed_class" (condition "A.NetClass == 'HV'")
              (constraint clearance (min 3mm)))
            (rule "misspelt_net" (condition "A.NetName != 'GDN' && B.NetClass == 'HighVoltage'")
              (constraint clearance (min 3mm)))
            (rule "no_such_footprint"
              (condition "A.memberOfFootprint('K9') || A.hasNetclass('Mains')")
              (constraint clearance (min 3mm)))
            (rule "all_exist"
              (condition "A.NetClass == 'Default' && A.NetName == 'Net-(FU1-*' && 'GND' == B.NetName && A.memberOfFootprint('K*') && B.hasNetclass('HighVoltage')")
              (constraint clearance (min 3mm)))
            """,
            "names.kicad_dru");
    KiCadDesignRulesTranslator.Result result = KiCadDesignRulesTranslator.apply(loadBoard(), rules);

    List<String> noMatch =
        result.warnings().stream().filter(w -> w.contains("matches no net class")).toList();
    assertEquals(
        List.of(
            "rule 'renamed_class': 'A.NetClass == 'HV''",
            "rule 'misspelt_net': 'A.NetName != 'GDN''",
            "rule 'no_such_footprint': 'A.memberOfFootprint('K9')'",
            "rule 'no_such_footprint': 'A.hasNetclass('Mains')'"),
        noMatch.stream().map(w -> w.substring(0, w.indexOf(" matches no net class"))).toList());
  }

  @Test
  void noNameWarningWhenEveryNameExists() throws IOException {
    KiCadDesignRulesTranslator.Result result =
        KiCadDesignRulesTranslator.apply(
            loadBoard(), KiCadDesignRules.read(TestFixtures.resolvePath(DRU)));
    assertTrue(
        result.warnings().stream().noneMatch(w -> w.contains("matches no net class")),
        result.warnings().toString());
  }

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
    assertEquals(25400.0, list.get(2).creepageUm(), 1e-9);
    assertTrue(list.get(2).warnings().getFirst().contains("no unit"));
    assertEquals(List.of(), list.get(2).otherConstraints());
  }

  @Test
  void translatesCreepageConstraintsAsRoutingClearance() throws IOException {
    BasicBoard board = loadBoard();
    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "mains_creepage_only"
              (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
              (constraint creepage (min 5mm)))
            """,
            "creepage.kicad_dru");

    KiCadDesignRulesTranslator.Result result = KiCadDesignRulesTranslator.apply(board, rules);

    assertEquals(1, result.translatedRules());
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("creepage") && w.contains("applied conservatively")));
    // the creepage applies to the copper the router adds
    assertEquals(mm(board, 5.0), traceValue(board, "Net-(K2-Pad2)", "Net-(K1-Pad1)"));
    assertEquals(mm(board, 5.0), traceToPinValue(board, "Net-(K1-Pad1)", pin(board, "K2", "2")));
  }

  @Test
  void leavesTheCreepageBetweenTwoPadsToKiCad() throws IOException {
    BasicBoard original = loadBoard();
    int designValue =
        value(original.rules.clearanceMatrix, pin(original, "K1", "1"), pin(original, "K2", "2"));
    BasicBoard board = loadBoard();
    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "mains_creepage_only"
              (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
              (constraint creepage (min 5mm)))
            """,
            "creepage.kicad_dru");

    KiCadDesignRulesTranslator.Result result = KiCadDesignRulesTranslator.apply(board, rules);

    // the router never moves a pad, and a slot between two pads (not modelled) may lengthen their
    // creepage path: between pads, the clearance of the design stays, and KiCad checks the creepage
    assertEquals(
        designValue,
        value(board.rules.clearanceMatrix, pin(board, "K1", "1"), pin(board, "K2", "2")));
    assertTrue(
        result.warnings().stream()
            .anyMatch(
                w ->
                    w.contains("'mains_creepage_only'")
                        && w.contains("creepage not applied between two pads")));
  }

  @Test
  void laterClearanceRuleDoesNotLowerAnEarlierCreepage() throws IOException {
    BasicBoard board = loadBoard();
    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "mains_creepage"
              (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
              (constraint creepage (min 5mm)))
            (rule "low_voltage_clearance"
              (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
              (constraint clearance (min 0.3mm)))
            """,
            "creepage-then-clearance.kicad_dru");

    KiCadDesignRulesTranslator.Result result = KiCadDesignRulesTranslator.apply(board, rules);

    // KiCad keeps one rule list per constraint type: the clearance rule does not override the
    // creepage, which still asks for 5 mm from the copper the router adds
    assertEquals(2, result.translatedRules());
    assertEquals(mm(board, 5.0), traceValue(board, "Net-(K2-Pad2)", "Net-(K1-Pad1)"));
    assertEquals(mm(board, 5.0), traceToPinValue(board, "Net-(K1-Pad1)", pin(board, "K2", "2")));
    // between two pads, only the clearance rule counts
    assertEquals(
        mm(board, 0.3),
        value(board.rules.clearanceMatrix, pin(board, "K1", "1"), pin(board, "K2", "2")));
  }

  @Test
  void lastMatchingCreepageRuleSetsTheCreepage() throws IOException {
    BasicBoard board = loadBoard();
    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "mains"
              (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
              (constraint clearance (min 4mm))
              (constraint creepage (min 6mm)))
            (rule "mains_creepage_relaxed"
              (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
              (constraint creepage (min 1mm)))
            """,
            "creepage-relaxed.kicad_dru");

    KiCadDesignRulesTranslator.Result result = KiCadDesignRulesTranslator.apply(board, rules);

    // the later creepage rule sets the creepage (1 mm), the clearance of the first rule stays
    assertEquals(2, result.translatedRules());
    assertEquals(
        mm(board, 4.0),
        value(board.rules.clearanceMatrix, pin(board, "K1", "1"), pin(board, "K2", "2")));
  }

  @Test
  void resetsReducedSmdToSmdClearanceWhenDruApplied() throws IOException {
    BasicBoard board = loadBoard();
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int defaultClass = matrix.getNo("default");
    int smdClass = matrix.getNo("smd");
    if (smdClass <= 0) {
      matrix.appendClass("smd");
      smdClass = matrix.getNo("smd");
    }
    int defaultVal = matrix.getValue(defaultClass, defaultClass, 0, false);
    matrix.setValue(smdClass, smdClass, 0, Math.max(1, defaultVal / 4));

    KiCadDesignRules rules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "hv"
              (condition "A.NetClass == 'HighVoltage'")
              (constraint clearance (min 3mm)))
            """,
            "smd.kicad_dru");

    KiCadDesignRulesTranslator.apply(board, rules);

    assertEquals(defaultVal, matrix.getValue(smdClass, smdClass, 0, false));
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
    // the last matching rule wins: coil to contacts of the same relay (2.5 mm), then mains
    // isolation; the coil rule sets only a clearance, and between two pads the creepage of the
    // mains isolation (3 mm) is left to KiCad
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

  @Test
  void footprintSelectorIsCaseSensitive() throws IOException {
    BasicBoard board = loadBoard();
    KiCadDesignRulesTranslator.apply(
        board,
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "around_k1_lower"
              (condition "A.memberOfFootprint('k1') && !B.memberOfFootprint('k1')")
              (constraint clearance (min 9mm)))
            (rule "around_k2"
              (condition "A.memberOfFootprint('K2') && !B.memberOfFootprint('K2')")
              (constraint clearance (min 7mm)))
            """,
            "test.kicad_dru"));
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    // KiCad's wxString::Matches: 'k1' is not the reference K1, 'K2' is K2
    assertNotEquals(mm(board, 9.0), value(matrix, pin(board, "K1", "1"), pin(board, "VD4", "1")));
    assertEquals(mm(board, 7.0), value(matrix, pin(board, "K2", "1"), pin(board, "VD4", "1")));
  }

  @Test
  void footprintPossiblyRenamedByTheExportGetsTheRuleAndAWarning() throws IOException {
    // A board with two footprints K1: the KiCad export names the second one K1_1
    BasicBoard board =
        loadBoard(
            dsn -> dsn.replace("(place K2 ", "(place K1_1 ").replaceAll("(?<=[ (])K2-", "K1_1-"));
    var result =
        KiCadDesignRulesTranslator.apply(
            board,
            KiCadDesignRules.parse(
                """
                (version 1)
                (rule "around_k1"
                  (condition "A.memberOfFootprint('K1') && !B.memberOfFootprint('K1')")
                  (constraint clearance (min 7mm)))
                """,
                "test.kicad_dru"));
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    assertEquals(mm(board, 7.0), value(matrix, pin(board, "K1", "1"), pin(board, "VD4", "1")));
    // K1_1 may be the second K1: the rule may apply, so it does (never less strict than KiCad)
    assertEquals(mm(board, 7.0), value(matrix, pin(board, "K1_1", "1"), pin(board, "VD4", "1")));
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("K1_1") && w.contains("may be duplicate references")),
        result.warnings().toString());
    // The warning on KiCad-renamed duplicates is still given
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("'K1_1' appears to be a KiCad-renamed duplicate of 'K1'")),
        result.warnings().toString());
  }

  /** K1 with a plated pad without net, which the KiCad export writes as a round keepout. */
  private static BasicBoard loadBoardWithPadKeepout() throws IOException {
    return loadBoard(
        dsn ->
            dsn.replace(
                "(image \"Реле:SRD-xxVDC-SL-C\"\n",
                "(image \"Реле:SRD-xxVDC-SL-C\"\n"
                    + "      (keepout \"\" (circle F.Cu 1500 -6000 -6000))\n"
                    + "      (keepout \"\" (circle B.Cu 1500 -6000 -6000))\n"));
  }

  private static ObstacleArea padKeepout(BasicBoard board, String reference) {
    for (Item item : board.getItems()) {
      if (item.getClass() == ObstacleArea.class
          && item.getComponentId() > 0
          && ((ObstacleArea) item).getRelativeArea() instanceof Circle
          && reference.equals(board.components.get(item.getComponentId()).name)) {
        return (ObstacleArea) item;
      }
    }
    throw new AssertionError("no keepout in " + reference);
  }

  @Test
  void padWithoutNetExportedAsKeepoutGetsTheRulesOfItsFootprint() throws IOException {
    BasicBoard board = loadBoardWithPadKeepout();
    var result =
        KiCadDesignRulesTranslator.apply(
            board,
            KiCadDesignRules.parse(
                """
                (version 1)
                (rule "around_k1"
                  (condition "A.memberOfFootprint('K1') && !B.memberOfFootprint('K1')")
                  (constraint clearance (min 7mm)))
                """,
                "test.kicad_dru"));
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    // KiCad applies the rule to the pad: Freerouting to the keepout it was exported as
    assertEquals(mm(board, 7.0), value(matrix, padKeepout(board, "K1"), pin(board, "VD4", "1")));
    assertEquals(
        mm(board, 7.0),
        matrix.getValue(
            padKeepout(board, "K1").clearanceClassIndex(),
            board.rules.nets.get("GND", 1).getNetClass().getTraceClearanceClass(),
            0,
            false));
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("pads without net exported as keepouts in K1")),
        result.warnings().toString());
    // Circular netless pad keepouts are now handled, so the unsupported-keepout warning is omitted
    assertFalse(
        result.warnings().stream()
            .anyMatch(w -> w.contains("unsupported footprint image keepout(s)")),
        result.warnings().toString());
  }

  @Test
  void padWithoutNetExportedAsKeepoutIsAPadWithoutNet() throws IOException {
    BasicBoard board = loadBoardWithPadKeepout();
    KiCadDesignRulesTranslator.apply(
        board,
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "gnd_wide"
              (condition "A.NetName == 'GND' && B.NetName != 'GND' && B.Type == 'Pad'")
              (constraint clearance (min 3mm)))
            """,
            "test.kicad_dru"));
    // The Own-Mailbox case: a GND trace at 3 mm from the pad without net
    assertEquals(
        mm(board, 3.0),
        board.rules.clearanceMatrix.getValue(
            padKeepout(board, "K1").clearanceClassIndex(),
            board.rules.nets.get("GND", 1).getNetClass().getTraceClearanceClass(),
            0,
            false));
  }

  @Test
  void leavesCreepageBetweenPadAndPadKeepoutToKiCad() throws IOException {
    BasicBoard board = loadBoardWithPadKeepout();
    ObstacleArea padKeepout = padKeepout(board, "K1");
    Pin hvPin = pin(board, "K2", "2");
    int designValue = value(board.rules.clearanceMatrix, padKeepout, hvPin);

    KiCadDesignRulesTranslator.Result result =
        KiCadDesignRulesTranslator.apply(
            board,
            KiCadDesignRules.parse(
                """
                (version 1)
                (rule "mains_creepage_pad_keepout"
                  (condition "A.NetClass == 'HighVoltage' && B.NetClass != 'HighVoltage'")
                  (constraint creepage (min 5mm)))
                """,
                "creepage-pad-keepout.kicad_dru"));

    // A netless pad exported as a keepout counts as a pad for the pad-to-pad creepage exception
    assertEquals(designValue, value(board.rules.clearanceMatrix, padKeepout, hvPin));
    // Copper added by the router still observes the 5 mm creepage against the netless pad keepout
    assertEquals(
        mm(board, 5.0),
        board.rules.clearanceMatrix.getValue(
            padKeepout.clearanceClassIndex(),
            board.rules.nets.get("Net-(K2-Pad2)", 1).getNetClass().getTraceClearanceClass(),
            0,
            false));
    assertTrue(
        result.warnings().stream()
            .anyMatch(
                w ->
                    w.contains("'mains_creepage_pad_keepout'")
                        && w.contains("creepage not applied between two pads")));
  }

  @Test
  void numberAndPlatingOfAPadExportedAsKeepoutAreUnknown() throws IOException {
    BasicBoard board = loadBoardWithPadKeepout();
    KiCadDesignRulesTranslator.apply(
        board,
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "pad_9"
              (condition "A.Pad_Number == '9' && B.Type == 'Pad'")
              (constraint clearance (min 5mm)))
            (rule "npth"
              (condition "!A.isPlated() && A.Type == 'Pad' && B.Type == 'Pad'")
              (constraint clearance (min 6mm)))
            """,
            "test.kicad_dru"));
    // K1 has no pad 9, but the pad exported as a keepout may be one, and may be unplated: the
    // largest clearance that may apply applies
    assertEquals(
        mm(board, 6.0),
        value(board.rules.clearanceMatrix, padKeepout(board, "K1"), pin(board, "VD4", "1")));
    // the pins of K1 are known: neither rule applies to them
    assertNotEquals(
        mm(board, 5.0),
        value(board.rules.clearanceMatrix, pin(board, "K1", "1"), pin(board, "VD4", "1")));
  }

  @Test
  void otherKeepoutsKeepTheirClearance() throws IOException {
    // a polygonal keepout of K1 (a rule area of the footprint) and a round keepout of the board
    BasicBoard board =
        loadBoard(
            dsn ->
                dsn.replace(
                        "(image \"Реле:SRD-xxVDC-SL-C\"\n",
                        "(image \"Реле:SRD-xxVDC-SL-C\"\n"
                            + "      (keepout \"\" (polygon F.Cu 0  -7000 -5000  -5000 -5000"
                            + "  -5000 -7000  -7000 -7000  -7000 -5000))\n")
                    .replaceFirst(
                        "\\(keepout \"\" \\(polygon signal 0",
                        "(keepout \"\" (circle F.Cu 1500 140000 -200000))\n    $0"));
    List<ObstacleArea> others = new ArrayList<>();
    for (Item item : board.getItems()) {
      if (item.getClass() == ObstacleArea.class
          && ((item.getComponentId() > 0)
              != (((ObstacleArea) item).getRelativeArea() instanceof Circle))) {
        others.add((ObstacleArea) item);
      }
    }
    assertTrue(others.size() >= 2, "both keepouts must load: " + others);
    int[] before = others.stream().mapToInt(Item::clearanceClassIndex).toArray();
    var result =
        KiCadDesignRulesTranslator.apply(
            board,
            KiCadDesignRules.parse(
                """
                (version 1)
                (rule "around_k1"
                  (condition "A.memberOfFootprint('K1') && !B.memberOfFootprint('K1')")
                  (constraint clearance (min 7mm)))
                (rule "no_net"
                  (condition "A.Type == 'Pad' && B.Type == 'Pad'")
                  (constraint clearance (min 5mm)))
                """,
                "test.kicad_dru"));
    assertEquals(
        Arrays.toString(before),
        Arrays.toString(others.stream().mapToInt(Item::clearanceClassIndex).toArray()));
    // Polygonal footprint image keepouts are not handled as pads and still emit the warning
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("unsupported footprint image keepout(s)")),
        result.warnings().toString());
  }

  @Test
  void duplicateReferenceWarningIsCaseInsensitive() throws IOException {
    BasicBoard board =
        loadBoard(
            dsn -> dsn.replace("(place K2 ", "(place k1_1 ").replaceAll("(?<=[ (])K2-", "k1_1-"));
    var result =
        KiCadDesignRulesTranslator.apply(
            board,
            KiCadDesignRules.parse(
                """
                (version 1)
                (rule "around_k1"
                  (condition "A.memberOfFootprint('K1') && !B.memberOfFootprint('K1')")
                  (constraint clearance (min 7mm)))
                """,
                "test.kicad_dru"));
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.contains("'k1_1' appears to be a KiCad-renamed duplicate of 'k1'")),
        result.warnings().toString());
  }

  @Test
  void componentsRenamedByTheExport() {
    assertEquals(
        Map.of("P1_1", "P1", "p1_2", "p1"),
        KiCadDesignRulesTranslator.possiblyRenamed(
            List.of("P1", "P1_1", "p1_2", "U1_1", "R", "R_", "_1")));
  }

  private static String libraryId(BasicBoard board, String reference) {
    var component = board.components.get(reference);
    assertNotNull(component, reference);
    return KiCadDesignRulesTranslator.libraryId(component);
  }

  private static BasicBoard loadBoard() throws IOException {
    return loadBoard(dsn -> dsn);
  }

  private static BasicBoard loadBoard(UnaryOperator<String> edit) throws IOException {
    String dsn = Files.readString(TestFixtures.resolveFile(DSN).toPath(), StandardCharsets.UTF_8);
    InputStream input = new ByteArrayInputStream(edit.apply(dsn).getBytes(StandardCharsets.UTF_8));
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

  private static int traceToPinValue(BasicBoard board, String traceNet, Pin pin) {
    var net = board.rules.nets.get(traceNet, 1);
    assertNotNull(net, traceNet);
    return board.rules.clearanceMatrix.getValue(
        net.getNetClass().getTraceClearanceClass(), pin.clearanceClassIndex(), 0, false);
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
