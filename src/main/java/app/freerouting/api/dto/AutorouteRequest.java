package app.freerouting.api.dto;

import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.RouterSettings;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite request payload for single-turn autorouting (POST /v1/autoroute).
 *
 * <p>Supports uploading primary PCB design (.dsn or .json), optional rules (.rules), and optional
 * initial session (.ses or .json), either as direct content or sanitized file paths.
 */
@Schema(
    name = "AutorouteRequest",
    description =
        "Single-turn autorouting request with multi-file input support and output format options")
public class AutorouteRequest {

  @SerializedName(
      value = "file_content",
      alternate = {"fileContent"})
  @Schema(
      name = "file_content",
      description = "Raw content of the primary design file (Specctra DSN text or KiCad JSON)",
      example = "(pcb board ...)")
  public String fileContent;

  @SerializedName(
      value = "file_path",
      alternate = {"filePath"})
  @Schema(
      name = "file_path",
      description =
          "Local file path to the primary design file (used when running locally or with server filesystem access)",
      example = "C:/projects/board.dsn")
  public String filePath;

  @SerializedName(
      value = "rules_content",
      alternate = {"rulesContent"})
  @Schema(
      name = "rules_content",
      description = "Raw content of the optional Specctra design rules (.rules) file",
      example = "(rules ...)")
  public String rulesContent;

  @SerializedName(
      value = "rules_path",
      alternate = {"rulesPath"})
  @Schema(
      name = "rules_path",
      description = "Local file path to the optional design rules (.rules) file",
      example = "C:/projects/board.rules")
  public String rulesPath;

  @SerializedName(
      value = "session_content",
      alternate = {"sessionContent"})
  @Schema(
      name = "session_content",
      description =
          "Raw content of an initial routing session (.ses or KiCad .json) to import before routing",
      example = "(session ...)")
  public String sessionContent;

  @SerializedName(
      value = "session_path",
      alternate = {"sessionPath"})
  @Schema(
      name = "session_path",
      description =
          "Local file path to an initial routing session (.ses or KiCad .json) to import before routing",
      example = "C:/projects/board.ses")
  public String sessionPath;

  @SerializedName(
      value = "router_settings",
      alternate = {"routerSettings"})
  @Schema(name = "router_settings", description = "Router settings configuration overrides")
  public RouterSettings routerSettings;

  @SerializedName(
      value = "drc_settings",
      alternate = {"drcSettings"})
  @Schema(name = "drc_settings", description = "Design rule checker settings configuration")
  public DesignRulesCheckerSettings drcSettings;

  @SerializedName(
      value = "output_formats",
      alternate = {"outputFormats"})
  @Schema(
      name = "output_formats",
      description =
          "List of desired output formats: 'SES', 'KICAD_JSON', 'SCR', 'DRC_JSON', 'DRC_SUMMARY'. Defaults to ['SES']",
      example = "[\"SES\", \"DRC_SUMMARY\"]")
  public List<String> outputFormats = new ArrayList<>();

  @SerializedName(
      value = "timeout_seconds",
      alternate = {"timeoutSeconds"})
  @Schema(
      name = "timeout_seconds",
      description = "Maximum execution timeout budget in seconds (default 300 = 5 minutes)",
      example = "120")
  public Integer timeoutSeconds;
}
