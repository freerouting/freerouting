package app.freerouting.datastructures;

import java.util.Date;

/** Class used to cancel a performance critical algorithm after a time limit is exceeded. */
public class TimeLimit {

  private final long timeStamp;
  private int timeLimit;

  /** Creates a new instance with a time limit of milliSeconds milliseconds. */
  public TimeLimit(int milliSeconds) {
    this.timeLimit = milliSeconds;
    this.timeStamp = new Date().getTime();
  }

  /** Returns true, if the time limit provided in the constructor of this class is exceeded. */
  public boolean limitExceeded() {
    long currTime = new Date().getTime();
    return currTime - this.timeStamp > this.timeLimit;
  }

  /** Multiplies this TimeLimit by factor. */
  public void multiply(double factor) {
    if (factor <= 0) {
      return;
    }
    double newLimit = factor * this.timeLimit;
    newLimit = Math.min(newLimit, Integer.MAX_VALUE);
    this.timeLimit = (int) newLimit;
  }
}
