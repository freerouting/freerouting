package app.freerouting.autoroute;

public class AutorouteAttemptResult
{
  public AutorouteAttemptState state;
  public String details;

  public AutorouteAttemptResult(AutorouteAttemptState state)
  {
    this.state = state;
    this.details = "";
  }

  public AutorouteAttemptResult(AutorouteAttemptState state, String details)
  {
    this.state = state;
    this.details = details;
  }

  @Override
  public String toString()
  {
    return this.state
        .toString()
        .toUpperCase() + ": " + this.details;
  }
}
