package app.freerouting.api.dto;

import app.freerouting.core.BoardFileDetails;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Payload containing board file data and metadata for routing jobs")
public class BoardFilePayload extends BoardFileDetails {

  @SerializedName("job_id")
  @Schema(name = "job_id", description = "Unique identifier for the routing job", example = "550e8400-e29b-41d4-a716-446655440000")
  public UUID jobId;
  @SerializedName("data")
  @Schema(name = "data", description = "Base64-encoded board file data (typically in Specctra DSN format). For MCP/LLM clients, it is recommended to use the local 'encode_base64' tool to encode, and 'decode_base64' to decode, this data rather than calling external shell commands (like powershell or base64).", example = "UENCIERlc2lnbiBGaWxlCg==")
  public String dataBase64;
}