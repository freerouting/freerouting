package app.freerouting.core;

public enum RoutingJobPriority
{
  LOWEST(0.0f), LOW(2.0f), BELOWNORMAL(4.0f), NORMAL(5.0f), ABOVENORMAL(6.0f), HIGH(8.0f), HIGHEST(10.0f);
  private final float value;

  RoutingJobPriority(float value)
  {
    this.value = Math.max(0.0f, Math.min(value, 10.0f));
  }

  public float getValue()
  {
    return value;
  }
}
