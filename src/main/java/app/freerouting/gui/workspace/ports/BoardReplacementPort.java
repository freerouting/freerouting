package app.freerouting.gui.workspace.ports;

/** Session-owned port for publishing board replacements. */
public interface BoardReplacementPort {

  /** Publishes a replacement when its run/load generation is current. */
  public void replaceBoard(BoardReplacement replacement);
}
