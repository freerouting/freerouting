package app.freerouting.core.events;

import app.freerouting.core.BoardFileDetails;

import java.util.EventObject;

public class BoardFileDetailsUpdatedEvent extends EventObject
{
  private final BoardFileDetails details;

  public BoardFileDetailsUpdatedEvent(Object source, BoardFileDetails details)
  {
    super(source);
    this.details = details;
  }

  public BoardFileDetails getDetails()
  {
    return details;
  }
}