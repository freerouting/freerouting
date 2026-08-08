package app.freerouting.board;

import java.io.Serializable;

/** Describes the structure of a board layer. */
public class Layer implements Serializable {

  /** The name of the layer. */
  public final String name;

  /**
   * True, if this is a signal layer, which can be used for routing. Otherwise, it may be for
   * example a power ground layer.
   */
  public final boolean isSignal;

  /** Creates a new instance of Layer */
  public Layer(String pName, boolean pIsSignal) {
    name = pName;
    isSignal = pIsSignal;
  }

  @Override
  public String toString() {
    return name;
  }
}
