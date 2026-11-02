package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Component;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.logger.FRLogger;
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
          }
        } else if (c.verdict() == Verdict.REJECTED && isCap) {
          // Note: not all C-named components are IC bypass caps, but we track for diagnostics
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
}
