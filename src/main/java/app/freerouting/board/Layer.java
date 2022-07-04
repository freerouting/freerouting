package app.freerouting.board;

/** Describes the structure of a board layer. */
public class Layer implements java.io.Serializable {

  /** The name of the layer. */
  public final String name;
  /**
   * True, if this is a signal layer, which can be used for routing. Otherwise it may be for example
   * a power ground layer.
   */
  public final boolean is_signal;

  /** Creates a new instance of Layer */
  public Layer(String p_name, boolean p_is_signal) {
    name = p_name;
    is_signal = p_is_signal;
  }

  public String toString() {
    return name;
  }
}
