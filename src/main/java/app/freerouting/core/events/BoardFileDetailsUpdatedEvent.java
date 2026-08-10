package app.freerouting.core.events;

import app.freerouting.core.BoardFileDetails;
import java.util.EventObject;

/** Event emitted when board file details change. */
public class BoardFileDetailsUpdatedEvent extends EventObject {

  private final BoardFileDetails details;

  /** Creates an event for the changed file details. */
  public BoardFileDetailsUpdatedEvent(Object source, BoardFileDetails details) {
    super(source);
    this.details = details;
  }

  /** Returns the changed file details. */
  public BoardFileDetails getDetails() {
    return details;
  }
}
