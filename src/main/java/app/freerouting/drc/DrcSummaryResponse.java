package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level diagnostic DRC summary designed for fast LLM/agent comprehension and concise API
 * reporting.
 *
 * <p>Provides aggregated counts, grouped violation explanations with component/pin context, spatial
 * congestion zones, and actionable layout auto-correction hints.
 */
@Schema(
    name = "DrcSummaryResponse",
    description =
        "Concise diagnostic summary of design rule violations and layout optimization hints")
public class DrcSummaryResponse {

  @SerializedName("clearance_violations_count")
  @Schema(description = "Total number of clearance violations")
  public int clearanceViolationsCount;

  @SerializedName("unconnected_nets_count")
  @Schema(description = "Total number of unconnected nets or incomplete connections")
  public int unconnectedNetsCount;

  @SerializedName("violations")
  @Schema(description = "Detailed list of diagnostic violation summaries")
  public List<DiagnosticViolation> violations = new ArrayList<>();

  @SerializedName("congestion_zones")
  @Schema(
      description = "Clustered areas with high concentration of violations or routing bottlenecks")
  public List<CongestionZone> congestionZones = new ArrayList<>();

  @SerializedName("hints")
  @Schema(description = "Actionable suggestions for router settings or layout auto-corrections")
  public List<String> hints = new ArrayList<>();

  /** Represents an individual violation with human/LLM readable context. */
  @Schema(name = "DiagnosticViolation", description = "Diagnostic details for a specific violation")
  public static class DiagnosticViolation {

    @SerializedName("type")
    @Schema(description = "Violation type (e.g. clearance, unconnected_net, dangling_trace)")
    public String type;

    @SerializedName("severity")
    @Schema(description = "Severity level (e.g. error, warning)")
    public String severity;

    @SerializedName("layer")
    @Schema(description = "Layer name where violation occurred")
    public String layer;

    @SerializedName("explanation")
    @Schema(description = "Natural-language explanation with component/pin and net names")
    public String explanation;

    @SerializedName("expected_clearance_mm")
    @Schema(description = "Expected clearance in mm, if applicable")
    public Double expectedClearanceMm;

    @SerializedName("actual_clearance_mm")
    @Schema(description = "Actual measured clearance in mm, if applicable")
    public Double actualClearanceMm;

    @SerializedName("shortfall_mm")
    @Schema(description = "Clearance shortfall in mm, if applicable")
    public Double shortfallMm;

    @SerializedName("items")
    @Schema(description = "Descriptions of the involved items")
    public List<String> items = new ArrayList<>();

    public DiagnosticViolation() {}

    public DiagnosticViolation(
        String type,
        String severity,
        String layer,
        String explanation,
        Double expectedClearanceMm,
        Double actualClearanceMm,
        Double shortfallMm,
        List<String> items) {
      this.type = type;
      this.severity = severity;
      this.layer = layer;
      this.explanation = explanation;
      this.expectedClearanceMm = expectedClearanceMm;
      this.actualClearanceMm = actualClearanceMm;
      this.shortfallMm = shortfallMm;
      if (items != null) {
        this.items.addAll(items);
      }
    }
  }

  /** Represents a spatial congestion cluster where multiple violations occur near each other. */
  @Schema(name = "CongestionZone", description = "Spatial zone where violations are concentrated")
  public static class CongestionZone {

    @SerializedName("center_x_mm")
    @Schema(description = "Center X coordinate in mm")
    public double centerXmm;

    @SerializedName("center_y_mm")
    @Schema(description = "Center Y coordinate in mm")
    public double centerYmm;

    @SerializedName("radius_mm")
    @Schema(description = "Radius of the congestion zone in mm")
    public double radiusMm;

    @SerializedName("violation_count")
    @Schema(description = "Number of violations within this cluster")
    public int violationCount;

    @SerializedName("description")
    @Schema(description = "Description of the congestion hotspot")
    public String description;

    public CongestionZone() {}

    public CongestionZone(
        double centerXmm,
        double centerYmm,
        double radiusMm,
        int violationCount,
        String description) {
      this.centerXmm = centerXmm;
      this.centerYmm = centerYmm;
      this.radiusMm = radiusMm;
      this.violationCount = violationCount;
      this.description = description;
    }
  }
}
