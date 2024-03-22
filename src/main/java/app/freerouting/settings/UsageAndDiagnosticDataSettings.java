package app.freerouting.settings;

import java.util.UUID;

public class UsageAndDiagnosticDataSettings
{
  public boolean disable_analytics = false;
  public int analytics_modulo = 16;
  public final String user_id;
  public String user_email;

  public UsageAndDiagnosticDataSettings()
  {
    this.user_id = UUID.randomUUID().toString();
  }

}
