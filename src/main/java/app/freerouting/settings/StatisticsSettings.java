package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Runtime counters and timestamps for application activity. */
public class StatisticsSettings implements Serializable {

  /** Timestamp at which the current statistics period started. */
  @SerializedName("start_time")
  public String startTime;

  /** Timestamp at which the most recent counter changed. */
  @SerializedName("end_time")
  public String endTime;

  /** Total number of sessions started. */
  @SerializedName("sessions_total")
  public Integer sessionsTotal = 0;

  /** Total number of jobs started. */
  @SerializedName("jobs_started")
  public Integer jobsStarted = 0;

  /** Total number of jobs completed. */
  @SerializedName("jobs_completed")
  public Integer jobsCompleted = 0;

  /** Creates counters and records the start timestamp. */
  public StatisticsSettings() {
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    startTime = formatter.format(Instant.now());
  }

  private void setEndTime() {
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    endTime = formatter.format(Instant.now());
  }

  /** Increments the total session counter. */
  public void incrementSessionsTotal() {
    sessionsTotal++;
    setEndTime();
  }

  /** Increments the total jobs-started counter. */
  public void incrementJobsStarted() {
    jobsStarted++;
    setEndTime();
  }

  /** Increments the total jobs-completed counter. */
  public void incrementJobsCompleted() {
    jobsCompleted++;
    setEndTime();
  }
}
