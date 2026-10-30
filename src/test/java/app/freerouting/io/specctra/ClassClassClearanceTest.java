package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass;
import app.freerouting.settings.DesignRulesCheckerSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClassClassClearanceTest {
  @ParameterizedTest
  @ValueSource(strings = {"kicad_default", "default", "Default", "KICAD_DEFAULT", "ordinary"})
  void untypedRuleReachesActualPads(String name) {
    for (String structureRule : new String[] {"", "(clearance 100 (type smd_smd))"}) {
      BasicBoard board =
          load(name, structureRule, "(rule (clearance 200))", "(rule (clearance 2220))", "");
      var pad = board.getPins().iterator().next();
      var trace = board.getTraces().iterator().next();
      assertClearance(board, pad.clearanceClassIndex(), trace.clearanceClassIndex(), 0, 2220);
      assertEquals(
          1,
          new DesignRulesChecker(board, new DesignRulesCheckerSettings())
              .getAllClearanceViolations()
              .size(),
          "The actual 575 um edge gap violates 2220 um");
    }
  }

  @Test
  void preservesWithinClassSmdException() {
    BasicBoard board =
        load(
            "kicad_default",
            "(clearance 100 (type smd_smd))",
            "(rule (clearance 200))",
            "(rule (clearance 2220))",
            "");
    int smd = item(board, "N1", ItemClass.SMD);
    assertClearance(board, smd, smd, 0, 100);
    assertClearance(board, smd, item(board, "N2", ItemClass.SMD), 0, 2220);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void typedExceptionsAndLayerRulesOverrideGeneralRule(boolean typedFirst) {
    String general = "(clearance 2220)";
    String typed = "(clearance 400 (type wire_smd)) (clearance 150 (type smd_smd))";
    BasicBoard board =
        load(
            "kicad_default",
            "(clearance 100 (type smd_smd))",
            "(rule (clearance 200))",
            "(rule "
                + (typedFirst ? typed + general : general + typed)
                + ")"
                + "(layer_rule bottom (rule (clearance 900) (clearance 300 (type wire_smd))))",
            "");
    int firstWire = item(board, "N1", ItemClass.TRACE);
    int firstPad = item(board, "N1", ItemClass.SMD);
    int secondWire = item(board, "N2", ItemClass.TRACE);
    int secondPad = item(board, "N2", ItemClass.SMD);
    assertClearance(board, firstWire, secondWire, 0, 2220);
    assertClearance(board, firstPad, secondWire, 0, 400);
    assertClearance(board, firstWire, secondPad, 0, 400);
    assertClearance(board, firstPad, secondPad, 0, 150);
    assertClearance(board, firstWire, secondWire, 1, 900);
    assertClearance(board, firstPad, secondWire, 1, 300);
    assertClearance(board, firstWire, secondPad, 1, 300);
    assertClearance(board, firstPad, secondPad, 1, 900);
    assertClearance(board, firstPad, firstPad, 0, 100);
    assertEquals(
        0,
        new DesignRulesChecker(board, new DesignRulesCheckerSettings())
            .getAllClearanceViolations()
            .size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ordinary", "kicad_default"})
  void classPairWithoutLocalClearanceDoesNotChangeUnrelatedClass(String name) {
    BasicBoard board =
        load(
            name,
            "(clearance 100 (type smd_smd))",
            "",
            "(rule (clearance 2220))",
            "(net N3) (class unrelated N3)");
    int firstPad = item(board, "N1", ItemClass.SMD);
    int secondWire = item(board, "N2", ItemClass.TRACE);
    assertClearance(board, firstPad, secondWire, 0, 2220);
    assertClearance(board, item(board, "N3", ItemClass.SMD), secondWire, 0, 200);
    assertClearance(board, item(board, "N3", ItemClass.TRACE), secondWire, 0, 200);
    assertClearance(board, firstPad, firstPad, 0, 100);
    for (String net : new String[] {"N1", "N2"}) {
      var netClass = board.rules.nets.get(net, 1).getNetClass();
      assertEquals(netClass.getTraceClearanceClass(), item(board, net, ItemClass.TRACE));
      assertEquals(
          item(board, net, ItemClass.VIA),
          netClass.getViaRule().getVia(0).getClearanceClassIndex());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"kicad_default", "ordinary"})
  void preservesLocalTypedClearance(String name) {
    BasicBoard board =
        load(
            name,
            "(clearance 100 (type smd_smd))",
            "(rule (clearance 200) (clearance 80 (type smd_smd)))",
            "(rule (clearance 2220))",
            "");
    int pad = item(board, "N1", ItemClass.SMD);
    assertClearance(board, pad, pad, 0, 80);
    assertClearance(board, pad, item(board, "N2", ItemClass.TRACE), 0, 2220);
  }

  @Test
  void localizingViaClearancesPreservesSharedRuleSelection() {
    String dsn =
        fixture(
                "kicad_default",
                "(clearance 100 (type smd_smd))",
                "(via_rule selected)",
                "(rule (clearance 2220))",
                "(via first V default attach) (via second V default)"
                    + "(via_rule selected second first)"
                    + "(net N3) (class unrelated N3 (via_rule selected))")
            .replace("(circuit (use_via V))", "");
    BasicBoard board = read(dsn);
    var selected = board.rules.nets.get("N1", 1).getNetClass().getViaRule();
    var unrelated = board.rules.nets.get("N3", 1).getNetClass().getViaRule();
    assertEquals(2, selected.viaCount());
    assertEquals(2, unrelated.viaCount());
    for (int i = 0; i < selected.viaCount(); i++) {
      assertEquals(unrelated.getVia(i).getPadstack(), selected.getVia(i).getPadstack());
      assertEquals(unrelated.getVia(i).attachSmdAllowed(), selected.getVia(i).attachSmdAllowed());
      assertEquals(i == 1, selected.getVia(i).attachSmdAllowed());
      assertEquals(item(board, "N1", ItemClass.VIA), selected.getVia(i).getClearanceClassIndex());
      assertEquals(item(board, "N3", ItemClass.VIA), unrelated.getVia(i).getClearanceClassIndex());
      assertClearance(
          board,
          selected.getVia(i).getClearanceClassIndex(),
          item(board, "N2", ItemClass.TRACE),
          0,
          2220);
      assertClearance(
          board,
          unrelated.getVia(i).getClearanceClassIndex(),
          item(board, "N2", ItemClass.TRACE),
          0,
          200);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void planeFollowsNetClassUnlessItsClearanceIsExplicit(boolean explicit) {
    String plane =
        "(plane N1 (rect top 20000 20000 21000 21000)"
            + (explicit ? " (clearance_class default)" : "")
            + ")";
    BasicBoard board =
        read(
            fixture(
                    "kicad_default",
                    "(clearance 100 (type smd_smd))",
                    "(rule (clearance 200))",
                    "(rule (clearance 2220))",
                    "")
                .replace("(boundary", plane + "(boundary"));
    int actualPlaneClass = board.getConductionAreas().iterator().next().clearanceClassIndex();
    assertClearance(
        board, actualPlaneClass, item(board, "N2", ItemClass.TRACE), 0, explicit ? 200 : 2220);
  }

  private static int item(BasicBoard board, String net, ItemClass type) {
    return board.rules.nets.get(net, 1).getNetClass().defaultItemClearanceClasses.get(type);
  }

  private static void assertClearance(BasicBoard board, int a, int b, int layer, int micrometers) {
    assertEquals(micrometers * 10, board.rules.clearanceMatrix.getValue(a, b, layer, false));
    assertEquals(micrometers * 10, board.rules.clearanceMatrix.getValue(b, a, layer, false));
  }

  private static BasicBoard load(
      String name, String structureRule, String classRule, String pairRules, String extraNetwork) {
    return read(fixture(name, structureRule, classRule, pairRules, extraNetwork));
  }

  private static String fixture(
      String name, String structureRule, String classRule, String pairRules, String extraNetwork) {
    return """
        (pcb clearance (resolution um 10) (unit um)
          (structure (layer top (type signal)) (layer bottom (type signal))
            (boundary (rect pcb 0 0 50000 50000))
            (rule (width 250) (clearance 200) %s))
          (placement (component P (place J1 10000 10000 front 0)))
          (library (image P (pin PAD 1 0 0))
            (padstack PAD (shape (rect top -300 -300 300 300)))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600))))
          (network (net N1 (pins J1-1)) (net N2)
            (class %s N1 (circuit (use_via V)) %s)
            (class HV N2 (circuit (use_via V)) (rule (width 250) (clearance 200)))
            %s
            (class_class (classes %s HV) %s))
          (wiring (wire (path top 250 9000 11000 11000 11000) (net N2) (type protect))))
        """
        .formatted(structureRule, name, classRule, extraNetwork, name, pairRules);
  }

  private static BasicBoard read(String dsn) {
    var result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    return assertInstanceOf(BoardReadResult.Success.class, result).board();
  }
}
