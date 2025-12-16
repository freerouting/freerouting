package app.freerouting.api.dto;

import app.freerouting.core.BoardFileDetails;
import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public class BoardFilePayload extends BoardFileDetails {

  @SerializedName("job_id")
  public UUID jobId;
  @SerializedName("data")
  public String dataBase64;
}