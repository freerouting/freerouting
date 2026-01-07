package app.freerouting.api.dto;

import app.freerouting.core.BoardFileDetails;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Payload containing board file data and metadata for routing jobs")
public class BoardFilePayload extends BoardFileDetails {

  @SerializedName("job_id")
  @Schema(description = "Unique identifier for the routing job", example = "550e8400-e29b-41d4-a716-446655440000")
  public UUID jobId;
  @SerializedName("data")
  @Schema(description = "Base64-encoded board file data (typically in Specctra DSN format)", example = "UENCIERlc2lnbiBGaWxlCg==")
  public String dataBase64;
}