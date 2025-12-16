package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class StatisticsSettings implements Serializable
{
  @SerializedName("start_time")
  public String startTime;
  @SerializedName("end_time")
  public String endTime;
  @SerializedName("sessions_total")
  public Integer sessionsTotal = 0;
  @SerializedName("jobs_started")
  public Integer jobsStarted = 0;
  @SerializedName("jobs_completed")
  public Integer jobsCompleted = 0;

  public StatisticsSettings()
  {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    startTime = formatter.format(Instant.now());
  }

  private void setEndTime()
  {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    endTime = formatter.format(Instant.now());
  }

  public void incrementSessionsTotal()
  {
    sessionsTotal++;
    setEndTime();
  }

  public void incrementJobsStarted()
  {
    jobsStarted++;
    setEndTime();
  }

  public void incrementJobsCompleted()
  {
    jobsCompleted++;
    setEndTime();
  }
}
