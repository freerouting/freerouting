package app.freerouting.autoroute.events;

import java.util.function.Consumer;

/** Listener for board updated events. */
public interface BoardUpdatedEventListener {

  /** Handles a board updated event. */
  void onBoardUpdatedEvent(BoardUpdatedEvent event);

  /**
   * Whether this listener reads the board statistics of the events. The router builds them, which
   * scans the whole board, only when one of its listeners does; otherwise they are null.
   */
  default boolean readsBoardStatistics() {
    return true;
  }

  /** A listener that handles the events without reading their board statistics. */
  static BoardUpdatedEventListener ignoringStatistics(Consumer<BoardUpdatedEvent> handler) {
    return new BoardUpdatedEventListener() {
      @Override
      public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
        handler.accept(event);
      }

      @Override
      public boolean readsBoardStatistics() {
        return false;
      }
    };
  }
}
