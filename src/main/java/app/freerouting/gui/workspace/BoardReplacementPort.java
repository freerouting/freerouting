package app.freerouting.gui.workspace;

/** Session-owned port for publishing board replacements. */
public interface BoardReplacementPort {

  /** Publishes a replacement when its run/load generation is current. */
  void replaceBoard(BoardReplacement replacement);
}
