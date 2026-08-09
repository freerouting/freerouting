package app.freerouting.autoroute.events;

/** Listener for board updated events. */
public interface BoardUpdatedEventListener {

  /** Handles a board updated event. */
  void onBoardUpdatedEvent(BoardUpdatedEvent event);
}
