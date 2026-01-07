package app.freerouting.management.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Analytics tracking payload for user events and identification")
public class Payload {

  @Schema(description = "Unique identifier for the authenticated user", example = "user_12345")
  public String userId;

  @Schema(description = "Anonymous identifier for tracking users without authentication", example = "anon_67890")
  public String anonymousId;

  @Schema(description = "Context information about the tracking event")
  public Context context;

  @Schema(description = "Name of the event being tracked", example = "job_started")
  public String event;

  @Schema(description = "User traits for identification")
  public Traits traits;

  @Schema(description = "Additional properties associated with the event")
  public Properties properties;
}