package app.freerouting.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RoutingEtaCalculatorTest {

  @Test
  void terminalStateIsPreservedUntilTheNextBoardStarts() {
    RoutingEtaCalculator calculator = new RoutingEtaCalculator();

    calculator.startRouting();
    calculator.onAutorouteStarted();
    calculator.update(12.5, null, null);
    calculator.onRoutingCompleted();

    assertEquals("completed", calculator.getCurrentEta().phase);
    assertEquals("Completed.", calculator.getCurrentEta().toDisplayString());

    calculator.update(20.0, null, null);

    assertEquals("completed", calculator.getCurrentEta().phase);
    assertEquals("Completed.", calculator.getCurrentEta().toDisplayString());
  }

  @Test
  void boardSwapClearsAnyPreviousTerminalEta() {
    RoutingEtaCalculator calculator = new RoutingEtaCalculator();

    calculator.startRouting();
    calculator.onRoutingStopped();
    assertEquals("Stopped.", calculator.getCurrentEta().toDisplayString());

    calculator.resetForBoardSwap();
    assertNull(calculator.getCurrentEta());

    calculator.startRouting();
    assertEquals("starting", calculator.getCurrentEta().phase);
    assertEquals("Calculating ETA...", calculator.getCurrentEta().toDisplayString());
  }

  @Test
  void fanoutPhaseProducesARoughEarlyEtaAfterProgressAppears() {
    RoutingEtaCalculator calculator = new RoutingEtaCalculator();

    calculator.startRouting();
    calculator.setTotalConnectableItems(100);
    calculator.onFanoutStarted();

    calculator.update(2.0, new RouterCounters() {{
      passCount = 1;
      incompleteCount = 80;
      phase = "fanout";
    }}, null);

    assertEquals("fanout", calculator.getCurrentEta().phase);
    assertEquals("Escaping pins (Fanout)...", calculator.getCurrentEta().progressText);
    assertEquals(RoutingEta.Confidence.LOW, calculator.getCurrentEta().confidence);
  }

  @Test
  void autoroutePhaseShowsARoughFirstPassEstimateWhenMaxPassesIsKnown() {
    RoutingEtaCalculator calculator = new RoutingEtaCalculator();

    calculator.startRouting();
    calculator.setMaxPasses(20);
    calculator.onAutorouteStarted();

    calculator.update(3.0, new RouterCounters() {{
      passCount = 1;
      incompleteCount = 75;
      phase = "autoroute";
    }}, null);

    assertEquals("autoroute", calculator.getCurrentEta().phase);
    assertEquals(RoutingEta.Confidence.LOW, calculator.getCurrentEta().confidence);
    assertEquals("Analyzing search space...", calculator.getCurrentEta().progressText);
    assertTrue(calculator.getCurrentEta().etaSeconds > 0);
  }
}