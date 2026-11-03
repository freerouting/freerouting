package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Component;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.logger.FRLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DecouplingCorpusBenchmarkTest {

  private static final Pattern C_REFDES_PATTERN = Pattern.compile("(?i)^C\\d.*");

  @Test
  void testCorpusPrecisionAndRecall() throws IOException {
    List<String> fixtures =
        List.of(
            "Issue508-DAC2020_bm01.dsn", "Issue508-DAC2020_bm02.dsn", "Issue508-DAC2020_bm04.dsn");

    int totalTruePositive = 0;
    int totalFalsePositive = 0;
    int totalFalseNegative = 0;

    for (String fixture : fixtures) {
      RoutingBoard board = DsnTestFixtures.loadBoard(fixture);
      IdentificationResult result = DecouplingIdentifier.identify(board);

      assertFalse(result.candidates().isEmpty(), "Expected candidates for fixture: " + fixture);

      for (CapacitorCandidate c : result.candidates()) {
        Component comp = board.components.get(c.componentId());
        String name = comp != null && comp.name != null ? comp.name.trim() : "";
        app.freerouting.core.library.Package pkg = comp != null ? comp.getPackage() : null;
        String pkgName = pkg != null && pkg.name != null ? pkg.name.trim() : "";
        boolean isCap =
            C_REFDES_PATTERN.matcher(name).matches()
                || Pattern.compile("(?i).*(C0402|C0603|C0805|C1206|C1210|CAP).*")
                    .matcher(pkgName)
                    .matches();

        if (c.verdict() == Verdict.DECOUPLING) {
          if (isCap) {
            totalTruePositive++;
          } else {
            totalFalsePositive++;
            FRLogger.info(
                String.format(
                    "FP in %s: compId=%d, name='%s', pkg='%s', score=%d, audit=%s, reasonMask=0x%X",
                    fixture, c.componentId(), name, pkgName, c.score(), c.audit(), c.reasonMask()));
          }
        } else if (c.verdict() == Verdict.REJECTED && isCap) {
          totalFalseNegative++;
        }
      }
    }

    double precision =
        (totalTruePositive + totalFalsePositive) > 0
            ? (double) totalTruePositive / (totalTruePositive + totalFalsePositive)
            : 1.0;

    FRLogger.info(
        String.format(
            "Corpus Benchmark: TP=%d, FP=%d, FN_candidates=%d, Precision=%.2f%%",
            totalTruePositive, totalFalsePositive, totalFalseNegative, precision * 100.0));

    // Precision target: >= 98%
    assertTrue(precision >= 0.98, "Expected precision >= 98%, was " + (precision * 100.0) + "%");
  }

  @Test
  void testProductionCorpusMultiBoard() throws IOException {
    List<String> productionFixtures =
        List.of(
            "Issue756-tomu-fpga.dsn",
            "Issue145-smoothieboard.dsn",
            "Issue690-kit-dev-coldfire-xilinx_5213.dsn",
            "Issue732-RoyalBlue54L-Feather.dsn",
            "Issue269-caniot-tiny-arm.dsn",
            "Issue219-LogicBoard_smt.dsn",
            "Issue754-avionics_hub.dsn",
            "Issue978-esp32c3.dsn",
            "Issue732-CM5_MINIMA_3.dsn");

    int corpusTp = 0;
    int corpusFp = 0;
    int corpusDemotedConflicts = 0;
    long totalTimeMs = 0;

    for (String fixture : productionFixtures) {
      long t0 = System.currentTimeMillis();
      RoutingBoard board = DsnTestFixtures.loadBoard(fixture);
      IdentificationResult result = DecouplingIdentifier.identify(board);
      long elapsed = System.currentTimeMillis() - t0;
      totalTimeMs += elapsed;

      for (CapacitorCandidate c : result.candidates()) {
        Component comp = board.components.get(c.componentId());
        String name = comp != null && comp.name != null ? comp.name.trim() : "";
        app.freerouting.core.library.Package pkg = comp != null ? comp.getPackage() : null;
        String pkgName = pkg != null && pkg.name != null ? pkg.name.trim() : "";
        boolean isCap =
            C_REFDES_PATTERN.matcher(name).matches()
                || Pattern.compile("(?i).*(C0402|C0603|C0805|C1206|C1210|CAP).*")
                    .matcher(pkgName)
                    .matches();

        if (c.verdict() == Verdict.DECOUPLING) {
          if (isCap) {
            corpusTp++;
          } else {
            corpusFp++;
            FRLogger.info(
                String.format(
                    "PROD FP in %s: compId=%d, name='%s', pkg='%s', score=%d",
                    fixture, c.componentId(), name, pkgName, c.score()));
          }
        } else if (c.verdict() == Verdict.REVIEW && c.audit() == AuditResult.CONFLICT) {
          corpusDemotedConflicts++;
        }
      }
    }

    double precision = (corpusTp + corpusFp) > 0 ? (double) corpusTp / (corpusTp + corpusFp) : 1.0;

    FRLogger.info(
        String.format(
            "Production Corpus Benchmark across %d boards: TP=%d, FP=%d, DemotedConflicts=%d, Precision=%.2f%% in %d ms",
            productionFixtures.size(),
            corpusTp,
            corpusFp,
            corpusDemotedConflicts,
            precision * 100.0,
            totalTimeMs));

    assertTrue(
        precision >= 0.98,
        "Expected production precision >= 98%, was " + (precision * 100.0) + "%");
  }

  @Test
  void testUserUploadedBoardsIfPresent() throws IOException {
    File a64File =
        new File(
            "C:/Users/oppyu/.gemini/antigravity/brain/21fc1298-be63-4631-9c4f-d4667d57509b/.user_uploaded/media_1793642930162_ac15d14f.dsn");
    if (a64File.exists()) {
      RoutingBoard a64Board;
      try (FileInputStream in = new FileInputStream(a64File)) {
        BoardReadResult readResult = DsnReader.readBoard(in, null, null);
        a64Board =
            switch (readResult) {
              case BoardReadResult.Success s -> (RoutingBoard) s.board();
              case BoardReadResult.OutlineMissing o -> (RoutingBoard) o.board();
              default -> throw new IOException("Failed to parse A64 DSN");
            };
      }
      IdentificationResult result = DecouplingIdentifier.identify(a64Board);

      FRLogger.info(
          String.format(
              "A64-OlinuXino result: Decoupling=%d, Review=%d, Rejected=%d, Conflicts=%d",
              result.summary().decoupling(),
              result.summary().review(),
              result.summary().rejected(),
              result.summary().auditConflicts()));

      // Check specific component C193 (KEYADC peripheral line): must NOT be DECOUPLING
      for (CapacitorCandidate c : result.candidates()) {
        Component comp = a64Board.components.get(c.componentId());
        if (comp != null && "C193".equalsIgnoreCase(comp.name)) {
          FRLogger.info(
              String.format(
                  "A64 C193 evaluation: score=%d, verdict=%s, reasonMask=0x%X",
                  c.score(), c.verdict(), c.reasonMask()));
          assertFalse(
              c.verdict() == Verdict.DECOUPLING,
              "C193 on KEYADC is an RC signal filter, not a decoupling capacitor!");
        }
      }
    }

    File doorFile =
        new File(
            "C:/Users/oppyu/.gemini/antigravity/brain/21fc1298-be63-4631-9c4f-d4667d57509b/.user_uploaded/media_1793641525430_224260a2.dsn");
    if (doorFile.exists()) {
      RoutingBoard doorBoard;
      try (FileInputStream in = new FileInputStream(doorFile)) {
        BoardReadResult readResult = DsnReader.readBoard(in, null, null);
        doorBoard =
            switch (readResult) {
              case BoardReadResult.Success s -> (RoutingBoard) s.board();
              case BoardReadResult.OutlineMissing o -> (RoutingBoard) o.board();
              default -> throw new IOException("Failed to parse Door_Opener DSN");
            };
      }
      IdentificationResult result = DecouplingIdentifier.identify(doorBoard);
      FRLogger.info(
          String.format(
              "Door_Opener result: Decoupling=%d, Review=%d, Rejected=%d, Conflicts=%d",
              result.summary().decoupling(),
              result.summary().review(),
              result.summary().rejected(),
              result.summary().auditConflicts()));

      for (CapacitorCandidate c : result.candidates()) {
        Component comp = doorBoard.components.get(c.componentId());
        String name = comp != null ? comp.name : ("Comp#" + c.componentId());
        if (c.verdict() == Verdict.DECOUPLING) {
          FRLogger.info(
              String.format(
                  "Door_Opener DECOUPLING: %s, score=%d, owners=%d",
                  name, c.score(), c.owners().size()));
        }
      }
    }
  }
}
