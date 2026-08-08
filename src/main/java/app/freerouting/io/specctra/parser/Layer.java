package app.freerouting.io.specctra.parser;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/** Describes a layer in a Specctra dsn file. */
public class Layer {

  /** all layers of the board */
  public static final Layer PCB = new Layer("pcb", -1, false);

  /** the signal layers */
  public static final Layer SIGNAL = new Layer("signal", -1, true);

  public final String name;
  public final int no;
  public final boolean isSignal;
  public final Collection<String> netNames;

  /**
   * Creates a new instance of Layer. p_no is the physical layer number starting with 0 at the
   * component side and ending at the solder side. If p_is_signal, the layer is a signal layer,
   * otherwise it is a powerground layer. For Layer objects describing more than 1 layer the number
   * is -1. p_net_names is a list of nets for this layer, if the layer is a power plane.
   */
  public Layer(String p_name, int p_no, boolean p_is_signal, Collection<String> p_net_names) {
    name = p_name;
    no = p_no;
    isSignal = p_is_signal;
    netNames = p_net_names;
  }

  /**
   * Creates a new instance of Layer. p_no is the physical layer number starting with 0 at the
   * component side and ending at the solder side. If p_is_signal, the layer is a signal layer,
   * otherwise it is a powerground layer. For Layer objects describing more than 1 layer the number
   * is -1.
   */
  public Layer(String p_name, int p_no, boolean p_is_signal) {
    name = p_name;
    no = p_no;
    isSignal = p_is_signal;
    netNames = new LinkedList<>();
  }

  /** Writes a layer scope in the structure scope. */
  public static void writeScope(WriteScopeParameter p_par, int p_layer_no, boolean p_write_rule)
      throws IOException {
    p_par.file.startScope();
    p_par.file.write("layer ");
    app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[p_layer_no];
    p_par.identifierType.write(boardLayer.name, p_par.file);
    p_par.file.newLine();
    p_par.file.write("(type ");
    if (boardLayer.isSignal) {
      p_par.file.write("signal)");
    } else {
      p_par.file.write("power)");
    }
    if (p_write_rule) {
      Rule.writeDefaultRule(p_par, p_layer_no);
    }
    p_par.file.endScope();
  }
}
