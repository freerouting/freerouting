package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DecouplingIdentifierTest {

  @Test
  void testNullBoardReturnsEmptyResult() {
    IdentificationResult result = DecouplingIdentifier.identify(null);
    assertNotNull(result);
    assertTrue(result.candidates().isEmpty());
    assertEquals(0, result.summary().components());
    assertEquals(0, result.summary().twoPinCandidates());
  }

  @Test
  void testGoldenFixtureIdentification() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    int pinsBefore = board.getPinCount();

    IdentificationResult result = DecouplingIdentifier.identify(board);

    assertNotNull(result);
    assertFalse(result.candidates().isEmpty(), "Expected candidates on golden fixture");

    DiagnosticsSummary summary = result.summary();
    assertEquals(result.candidates().size(), summary.twoPinCandidates());
    assertEquals(
        result.candidates().size(), summary.decoupling() + summary.review() + summary.rejected());

    // Verify board immutability (board state is untouched)
    assertEquals(pinsBefore, board.getPinCount(), "Pin count must not change");

    // Verify candidate properties
    for (CapacitorCandidate c : result.candidates()) {
      assertTrue(c.pinIdA() < c.pinIdB(), "pinIdA must be strictly less than pinIdB");
      assertTrue(c.score() >= 0 && c.score() <= 100, "Score must be clamped between 0 and 100");
      if (c.verdict() == Verdict.DECOUPLING) {
        assertTrue(c.score() >= 60, "Decoupling verdict must meet or exceed threshold");
        assertTrue(c.railNetNumber() > 0, "Decoupling candidate must have resolved rail");
        assertTrue(c.groundNetNumber() > 0, "Decoupling candidate must have resolved ground");
      }
    }
  }

  @Test
  void testDeterminism() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    IdentificationResult run1 = DecouplingIdentifier.identify(board);
    IdentificationResult run2 = DecouplingIdentifier.identify(board);

    assertEquals(run1.candidates().size(), run2.candidates().size());
    for (int i = 0; i < run1.candidates().size(); i++) {
      CapacitorCandidate c1 = run1.candidates().get(i);
      CapacitorCandidate c2 = run2.candidates().get(i);

      assertEquals(c1.componentId(), c2.componentId());
      assertEquals(c1.pinIdA(), c2.pinIdA());
      assertEquals(c1.pinIdB(), c2.pinIdB());
      assertEquals(c1.netNumberA(), c2.netNumberA());
      assertEquals(c1.netNumberB(), c2.netNumberB());
      assertEquals(c1.railNetNumber(), c2.railNetNumber());
      assertEquals(c1.groundNetNumber(), c2.groundNetNumber());
      assertEquals(c1.score(), c2.score());
      assertEquals(c1.verdict(), c2.verdict());
      assertEquals(c1.reasonMask(), c2.reasonMask());
      assertEquals(c1.audit(), c2.audit());
      assertEquals(c1.owners().size(), c2.owners().size());
    }
  }

  @Test
  void testPerformanceRequirement() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    // Warm up
    DecouplingIdentifier.identify(board);

    // Measure
    long start = System.nanoTime();
    IdentificationResult result = DecouplingIdentifier.identify(board);
    long durationMs = (System.nanoTime() - start) / 1_000_000L;

    assertTrue(durationMs < 50, "Execution time should be under 50 ms, was " + durationMs + " ms");
  }

  @Test
  void testRoyalBlueFeatherCrystalAndConnectorRejection() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue732-RoyalBlue54L-Feather.dsn");
    IdentificationResult res = DecouplingIdentifier.identify(board);
    for (CapacitorCandidate c : res.candidates()) {
      String name = board.components.get(c.componentId()).name;
      if (name.equals("C18") || name.equals("C20") || name.equals("C13") || name.equals("C14")) {
        assertEquals(
            Verdict.REJECTED,
            c.verdict(),
            "Expected " + name + " to be REJECTED, was " + c.verdict());
      }
    }
  }

  @Test
  void testMultiBoardComprehensiveBenchmark() throws IOException {
    record BoardEntry(String name, String path, boolean isUpload) {}
    java.util.List<BoardEntry> boards =
        java.util.List.of(
            new BoardEntry(
                "Door_Opener (ESP32-H2)",
                "C:/Users/oppyu/.gemini/antigravity/brain/21fc1298-be63-4631-9c4f-d4667d57509b/.user_uploaded/media_1793641525430_224260a2.dsn",
                true),
            new BoardEntry(
                "A64-OlinuXino Rev H (Allwinner A64)",
                "C:/Users/oppyu/.gemini/antigravity/brain/21fc1298-be63-4631-9c4f-d4667d57509b/.user_uploaded/media_1793642930162_ac15d14f.dsn",
                true),
            new BoardEntry("Tomu/Fomu (Lattice iCE40UP5K)", "Issue756-tomu-fpga.dsn", false),
            new BoardEntry(
                "Smoothieboard v1 (LPC1769 5-Axis CNC)", "Issue145-smoothieboard.dsn", false),
            new BoardEntry(
                "RoyalBlue-Feather (Nordic nRF52840)", "Issue732-RoyalBlue54L-Feather.dsn", false),
            new BoardEntry(
                "CANIOT-Tiny-ARM (STM32 CAN Bus)", "Issue269-caniot-tiny-arm.dsn", false),
            new BoardEntry(
                "ColdFire-Xilinx (MCF5213 + CPLD)",
                "Issue690-kit-dev-coldfire-xilinx_5213.dsn",
                false),
            new BoardEntry("LogicBoard SMT (Digital Logic)", "Issue219-LogicBoard_smt.dsn", false));

    java.io.File logFile = new java.io.File("build/decoupling_verification.txt");
    logFile.getParentFile().mkdirs();
    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(logFile))) {
      pw.println(
          "==========================================================================================================");
      pw.printf(
          "%-38s | %-6s | %-6s | %-10s | %-6s | %-8s | %-9s | %-7s%n",
          "Board Design",
          "Comps",
          "2-Pins",
          "Decoupling",
          "Review",
          "Rejected",
          "Conflicts",
          "Time");
      pw.println(
          "==========================================================================================================");

      for (BoardEntry be : boards) {
        RoutingBoard board;
        if (be.isUpload) {
          java.io.File file = new java.io.File(be.path);
          if (!file.exists()) continue;
          try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            app.freerouting.io.BoardReadResult readResult =
                app.freerouting.io.specctra.DsnReader.readBoard(is, null, null, file.getName());
            board =
                switch (readResult) {
                  case app.freerouting.io.BoardReadResult.Success s -> (RoutingBoard) s.board();
                  case app.freerouting.io.BoardReadResult.OutlineMissing o ->
                      (RoutingBoard) o.board();
                  default -> throw new IOException("Failed to parse board: " + readResult);
                };
          }
        } else {
          board = DsnTestFixtures.loadBoard(be.path);
        }

        long t0 = System.nanoTime();
        IdentificationResult res = DecouplingIdentifier.identify(board);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        pw.printf(
            "%-38s | %6d | %6d | %10d | %6d | %8d | %9d | %4d ms%n",
            be.name,
            res.summary().components(),
            res.summary().twoPinCandidates(),
            res.summary().decoupling(),
            res.summary().review(),
            res.summary().rejected(),
            res.summary().auditConflicts(),
            ms);

        // Detailed breakdown of every candidate
        pw.println("--- DECOUPLING CAPS (" + res.summary().decoupling() + ") ---");
        for (CapacitorCandidate c : res.candidates()) {
          if (c.verdict() == Verdict.DECOUPLING) {
            String name = board.components.get(c.componentId()).name;
            String pwr =
                c.railNetNumber() > 0 ? board.rules.nets.get(c.railNetNumber()).name : "none";
            String gnd =
                c.groundNetNumber() > 0 ? board.rules.nets.get(c.groundNetNumber()).name : "none";
            String owner =
                !c.owners().isEmpty()
                    ? board.components.get(c.owners().get(0).componentId()).name
                    : "none";
            pw.printf(
                "  [DECOUPLING] %-8s | score=%2d | pwr=%-20s | gnd=%-15s | owner=%-8s%n",
                name, c.score(), pwr, gnd, owner);
          }
        }

        pw.println("--- REVIEW CAPS (" + res.summary().review() + ") ---");
        for (CapacitorCandidate c : res.candidates()) {
          if (c.verdict() == Verdict.REVIEW) {
            String name = board.components.get(c.componentId()).name;
            String pwr =
                c.railNetNumber() > 0 ? board.rules.nets.get(c.railNetNumber()).name : "none";
            String gnd =
                c.groundNetNumber() > 0 ? board.rules.nets.get(c.groundNetNumber()).name : "none";
            String owner =
                !c.owners().isEmpty()
                    ? board.components.get(c.owners().get(0).componentId()).name
                    : "none";
            pw.printf(
                "  [REVIEW]     %-8s | score=%2d | pwr=%-20s | gnd=%-15s | owner=%-8s | audit=%s%n",
                name, c.score(), pwr, gnd, owner, c.audit());
          }
        }

        pw.println("--- REJECTED SAMPLES (C... or low score) ---");
        int rejectedPrinted = 0;
        for (CapacitorCandidate c : res.candidates()) {
          String name = board.components.get(c.componentId()).name;
          if (c.verdict() == Verdict.REJECTED && (name.startsWith("C") || rejectedPrinted < 10)) {
            String pwr =
                c.railNetNumber() > 0 ? board.rules.nets.get(c.railNetNumber()).name : "none";
            String gnd =
                c.groundNetNumber() > 0 ? board.rules.nets.get(c.groundNetNumber()).name : "none";
            String owner =
                !c.owners().isEmpty()
                    ? board.components.get(c.owners().get(0).componentId()).name
                    : "none";
            pw.printf(
                "  [REJECTED]   %-8s | score=%2d | pwr=%-20s | gnd=%-15s | owner=%-8s | audit=%s%n",
                name, c.score(), pwr, gnd, owner, c.audit());
            rejectedPrinted++;
          }
        }
        pw.println();
      }
      pw.println(
          "==========================================================================================================");
    }
  }
}
