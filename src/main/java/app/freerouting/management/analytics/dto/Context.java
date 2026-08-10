package app.freerouting.management.analytics.dto;

/** Describes the context associated with an analytics event. */
public class Context {

  public Library library;
  public String anonymousId;
  public String event;
  public Traits traits;
  public Properties properties;
}
