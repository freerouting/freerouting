package app.freerouting.core.events;

/** Receives events when board file details change. */
public interface BoardFileDetailsUpdatedEventListener {

  /** Handles a board file details update. */
  void onBoardFileDetailsUpdated(BoardFileDetailsUpdatedEvent event);
}
