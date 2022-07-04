package app.freerouting.designforms.specctra;

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
  public final boolean is_signal;
  public final java.util.Collection<String> net_names;
  /**
   * Creates a new instance of Layer. p_no is the physical layer number starting with 0 at the
   * component side and ending at the solder side. If p_is_signal, the layer is a signal layer,
   * otherwise it is a powerground layer. For Layer objects describing more than 1 layer the number
   * is -1. p_net_names is a list of nets for this layer, if the layer is a power plane.
   */
  public Layer(String p_name, int p_no, boolean p_is_signal, Collection<String> p_net_names) {
    name = p_name;
    no = p_no;
    is_signal = p_is_signal;
    net_names = p_net_names;
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
    is_signal = p_is_signal;
    net_names = new LinkedList<String>();
  }

  /** Writes a layer scope in the stucture scope. */
  public static void write_scope(WriteScopeParameter p_par, int p_layer_no, boolean p_write_rule)
      throws java.io.IOException {
    p_par.file.start_scope();
    p_par.file.write("layer ");
    app.freerouting.board.Layer board_layer = p_par.board.layer_structure.arr[p_layer_no];
    p_par.identifier_type.write(board_layer.name, p_par.file);
    p_par.file.new_line();
    p_par.file.write("(type ");
    if (board_layer.is_signal) {
      p_par.file.write("signal)");
    } else {
      p_par.file.write("power)");
    }
    if (p_write_rule) {
      Rule.write_default_rule(p_par, p_layer_no);
    }
    p_par.file.end_scope();
  }
}
