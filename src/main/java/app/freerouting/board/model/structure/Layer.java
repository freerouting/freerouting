package app.freerouting.board.model.structure;

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

  /** Creates a new instance of Layer. */
  public Layer(String name, boolean isSignal) {
    this.name = name;
    this.isSignal = isSignal;
  }

  @Override
  public String toString() {
    return name;
  }
}
