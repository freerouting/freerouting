package app.freerouting.io.specctra.parser;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/** Describes a layer in a Specctra dsn file. */
public class Layer {

  /** All layers of the board. */
  public static final Layer PCB = new Layer("pcb", -1, false);

  /** The signal layers. */
  public static final Layer SIGNAL = new Layer("signal", -1, true);

  public final String name;
  public final int no;
  public final boolean isSignal;
  public final Collection<String> netNames;

  /**
   * Creates a new instance of Layer. no is the physical layer number starting with 0 at the
   * component side and ending at the solder side. If isSignal, the layer is a signal layer,
   * otherwise it is a powerground layer. For Layer objects describing more than 1 layer the number
   * is -1. netNames is a list of nets for this layer, if the layer is a power plane.
   */
  public Layer(String name, int no, boolean isSignal, Collection<String> netNames) {
    this.name = name;
    this.no = no;
    this.isSignal = isSignal;
    this.netNames = netNames;
  }

  /**
   * Creates a new instance of Layer. no is the physical layer number starting with 0 at the
   * component side and ending at the solder side. If isSignal, the layer is a signal layer,
   * otherwise it is a powerground layer. For Layer objects describing more than 1 layer the number
   * is -1.
   */
  public Layer(String name, int no, boolean isSignal) {
    this.name = name;
    this.no = no;
    this.isSignal = isSignal;
    netNames = new LinkedList<>();
  }

  /** Writes a layer scope in the structure scope. */
  public static void writeScope(
      WriteScopeParameter scopeParameter, int layerIndex, boolean writeRule) throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("layer ");
    app.freerouting.board.model.structure.Layer boardLayer =
        scopeParameter.board.layerStructure.layers[layerIndex];
    scopeParameter.identifierType.write(boardLayer.name, scopeParameter.file);
    scopeParameter.file.newLine();
    scopeParameter.file.write("(type ");
    if (boardLayer.isSignal) {
      scopeParameter.file.write("signal)");
    } else {
      scopeParameter.file.write("power)");
    }
    if (writeRule) {
      Rule.writeDefaultRule(scopeParameter, layerIndex);
    }
    scopeParameter.file.endScope();
  }
}
