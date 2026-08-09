package app.freerouting.autoroute.events;

import app.freerouting.autoroute.TaskState;
import java.util.EventObject;

/** TaskStateChangedEvent. */
public class TaskStateChangedEvent extends EventObject {

  private final TaskState tastState;
  private final int passNumber;
  private final String boardHash;

  /** TaskStateChangedEvent. */
  public TaskStateChangedEvent(
      Object source, TaskState taskState, int passNumber, String boardHash) {
    super(source);
    this.tastState = taskState;
    this.passNumber = passNumber;
    this.boardHash = boardHash;
  }

  public TaskState getTaskState() {
    return tastState;
  }

  public int getPassNumber() {
    return passNumber;
  }

  public String getBoardHash() {
    return boardHash;
  }
}
