package app.freerouting.io;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.drc.DrcSummaryResponse;
import app.freerouting.io.kicad.KiCadJsonWriter;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.DesignRulesCheckerSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility to generate multiple output representations (SES, KiCad JSON, Fusion SCR, DRC JSON, DRC
 * Summary) from a routed board in a single operation.
 */
public final class MultiOutputGenerator {

  private MultiOutputGenerator() {}

  /** Result container holding generated output files and diagnostic reports. */
  public static class MultiOutputResult {
    private final Map<FileFormat, byte[]> files = new LinkedHashMap<>();
    private DrcSummaryResponse drcSummary;

    /** Returns an unmodifiable map of generated file formats and byte contents. */
    public Map<FileFormat, byte[]> getFiles() {
      return Collections.unmodifiableMap(files);
    }

    /** Returns the bytes for a specific format, or {@code null} if not generated. */
    public byte[] getFile(FileFormat format) {
      return files.get(format);
    }

    /** Returns the UTF-8 text representation for a format, or {@code null} if not generated. */
    public String getFileAsText(FileFormat format) {
      byte[] data = files.get(format);
      return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    /** Adds a generated file for a given format. */
    public void putFile(FileFormat format, byte[] data) {
      files.put(format, data);
    }

    /** Gets the DRC summary diagnostic report if requested and generated. */
    public DrcSummaryResponse getDrcSummary() {
      return drcSummary;
    }

    /** Sets the DRC summary diagnostic report. */
    public void setDrcSummary(DrcSummaryResponse drcSummary) {
      this.drcSummary = drcSummary;
    }
  }

  /**
   * Generates all requested formats for the given board.
   *
   * @param board The routed board
   * @param designName Design name for headers (e.g. PCB name)
   * @param requestedFormats Set of file formats to generate
   * @param drcSettings Settings to use if DRC formats are requested (or default if null)
   * @param includeDrcSummary Whether to also populate DrcSummaryResponse
   * @return MultiOutputResult containing generated artifacts
   */
  public static MultiOutputResult generateOutputs(
      BasicBoard board,
      String designName,
      Set<FileFormat> requestedFormats,
      DesignRulesCheckerSettings drcSettings,
      boolean includeDrcSummary) {

    MultiOutputResult result = new MultiOutputResult();
    if (board == null) {
      return result;
    }

    String effectiveDesignName =
        designName != null && !designName.isBlank() ? designName : "design";
    byte[] cachedSesBytes = null;

    if (requestedFormats != null) {
      for (FileFormat format : requestedFormats) {
        if (format == null) {
          continue;
        }
        switch (format) {
          case SES -> {
            if (cachedSesBytes == null) {
              cachedSesBytes = generateSesBytes(board, effectiveDesignName);
            }
            if (cachedSesBytes != null) {
              result.putFile(FileFormat.SES, cachedSesBytes);
            }
          }
          case KICAD_SESSION_JSON, KICAD_DESIGN_JSON -> {
            if (board instanceof RoutingBoard routingBoard) {
              try {
                String json = KiCadJsonWriter.write(routingBoard, effectiveDesignName);
                result.putFile(format, json.getBytes(StandardCharsets.UTF_8));
              } catch (Exception e) {
                FRLogger.error("Failed to generate KiCad JSON output", e);
              }
            }
          }
          case SCR -> {
            if (cachedSesBytes == null) {
              cachedSesBytes = generateSesBytes(board, effectiveDesignName);
            }
            if (cachedSesBytes != null) {
              byte[] scrBytes = generateScrBytes(cachedSesBytes, board);
              if (scrBytes != null) {
                result.putFile(FileFormat.SCR, scrBytes);
              }
            }
          }
          case DRC_JSON -> {
            DesignRulesCheckerSettings settings =
                drcSettings != null ? drcSettings : new DesignRulesCheckerSettings();
            DesignRulesChecker checker = new DesignRulesChecker(board, settings);
            String drcJson = checker.generateReportJson(effectiveDesignName, "mm");
            result.putFile(FileFormat.DRC_JSON, drcJson.getBytes(StandardCharsets.UTF_8));
          }
          default -> {
            // Other formats (DSN, FRB, RULES, UNKNOWN) are not produced as routed outputs
          }
        }
      }
    }

    if (includeDrcSummary) {
      DesignRulesCheckerSettings settings =
          drcSettings != null ? drcSettings : new DesignRulesCheckerSettings();
      DesignRulesChecker checker = new DesignRulesChecker(board, settings);
      result.setDrcSummary(checker.generateSummary());
    }

    return result;
  }

  private static byte[] generateSesBytes(BasicBoard board, String designName) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      SesWriter.write(board, baos, designName);
      return baos.toByteArray();
    } catch (IOException e) {
      FRLogger.error("Failed to generate SES output bytes", e);
      return null;
    }
  }

  private static byte[] generateScrBytes(byte[] sesBytes, BasicBoard board) {
    try (ByteArrayInputStream inStream = new ByteArrayInputStream(sesBytes);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
      if (SesReader.saveSpecctraSessionSesAsFusionScriptScr(inStream, outStream, board)) {
        return outStream.toByteArray();
      }
    } catch (Exception e) {
      FRLogger.error("Failed to generate Fusion SCR output bytes", e);
    }
    return null;
  }
}
