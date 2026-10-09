package app.freerouting.cli;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.state.BoardComparator;
import app.freerouting.core.scoring.BenchmarkScoreCalculator;
import app.freerouting.io.BoardReadResult;
import app.freerouting.logger.FRLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Handles board comparison and benchmark score calculation CLI commands. */
public final class BenchmarkAndCompareCommands {

  private BenchmarkAndCompareCommands() {}

  /**
   * Compares two board design files and prints the comparison report.
   *
   * @param file1Path path to first board file
   * @param file2Path path to second board file
   * @return true if boards are identical within tolerance, false otherwise
   */
  public static boolean compareBoardFiles(String file1Path, String file2Path) {
    FRLogger.info("Starting comparison of board files: " + file1Path + " and " + file2Path);
    try {
      File file1 = new File(file1Path);
      File file2 = new File(file2Path);
      if (!file1.exists()) {
        FRLogger.error("Comparison file 1 does not exist: " + file1Path, null);
        return false;
      }
      if (!file2.exists()) {
        FRLogger.error("Comparison file 2 does not exist: " + file2Path, null);
        return false;
      }

      RoutingBoard board1 = loadBoardFromFile(file1);
      RoutingBoard board2 = loadBoardFromFile(file2);

      if (board1 == null || board2 == null) {
        FRLogger.error("Failed to load one or both boards for comparison.", null);
        return false;
      }

      BoardComparator.ComparisonResult result = BoardComparator.compare(board1, board2, 1e-3);

      System.out.println(result.report);

      if (result.areEqual) {
        FRLogger.info("SUCCESS: Boards are identical.");
      } else {
        FRLogger.warn("WARNING: Differences detected between the loaded boards.");
      }
      return result.areEqual;
    } catch (Exception e) {
      FRLogger.error("Error during board files comparison: " + e.getMessage(), e);
      return false;
    }
  }

  /**
   * Loads a board from either a Specctra DSN or KiCad JSON file.
   *
   * @param file file to read
   * @return loaded RoutingBoard, or null if outline is missing / cannot read
   * @throws Exception if reading fails with I/O error
   */
  public static RoutingBoard loadBoardFromFile(File file) throws Exception {
    try (InputStream is = new FileInputStream(file)) {
      if (file.getName().toLowerCase().endsWith(".json")) {
        try (Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
          BoardReadResult readResult =
              app.freerouting.io.kicad.KiCadJsonReader.readBoard(r, null, null);
          if (readResult instanceof BoardReadResult.Success success) {
            return (RoutingBoard) success.board();
          } else if (readResult instanceof BoardReadResult.OutlineMissing outlineMissing) {
            return (RoutingBoard) outlineMissing.board();
          }
        }
      } else {
        BoardReadResult readResult =
            app.freerouting.io.specctra.DsnReader.readBoard(is, null, null, file.getName());
        if (readResult instanceof BoardReadResult.Success success) {
          return (RoutingBoard) success.board();
        } else if (readResult instanceof BoardReadResult.OutlineMissing outlineMissing) {
          return (RoutingBoard) outlineMissing.board();
        }
      }
    }
    return null;
  }

  /**
   * Calculates benchmark scores from an input file and writes to output.
   *
   * @param inputPath input JSON file path
   * @param outputPath output JSON file path
   * @return exit code (0 for success)
   */
  public static int calculateBenchmarkScores(String inputPath, String outputPath) {
    if (inputPath == null || outputPath == null) {
      FRLogger.error(
          "Both --input=<path> and --output=<path> must be specified with --calculate-benchmark-scores",
          null);
      return 1;
    }
    return BenchmarkScoreCalculator.run(Path.of(inputPath), Path.of(outputPath));
  }
}
