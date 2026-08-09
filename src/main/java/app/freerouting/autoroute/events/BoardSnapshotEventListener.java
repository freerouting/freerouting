package app.freerouting.autoroute.events;

/** Listener for board snapshot events. */
public interface BoardSnapshotEventListener {

  /** Handles a board snapshot event. */
  void onBoardSnapshotEvent(BoardSnapshotEvent event);
}
