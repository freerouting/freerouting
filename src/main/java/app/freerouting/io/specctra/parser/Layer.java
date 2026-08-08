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
  public Layer(String pName, int pNo, boolean pIsSignal, Collection<String> pNetNames) {
    name = pName;
    no = pNo;
    isSignal = pIsSignal;
    netNames = pNetNames;
  }

  /**
   * Creates a new instance of Layer. p_no is the physical layer number starting with 0 at the
   * component side and ending at the solder side. If p_is_signal, the layer is a signal layer,
   * otherwise it is a powerground layer. For Layer objects describing more than 1 layer the number
   * is -1.
   */
  public Layer(String pName, int pNo, boolean pIsSignal) {
    name = pName;
    no = pNo;
    isSignal = pIsSignal;
    netNames = new LinkedList<>();
  }

  /** Writes a layer scope in the structure scope. */
  public static void writeScope(WriteScopeParameter pPar, int pLayerNo, boolean pWriteRule)
      throws IOException {
    pPar.file.startScope();
    pPar.file.write("layer ");
    app.freerouting.board.Layer boardLayer = pPar.board.layerStructure.arr[pLayerNo];
    pPar.identifierType.write(boardLayer.name, pPar.file);
    pPar.file.newLine();
    pPar.file.write("(type ");
    if (boardLayer.isSignal) {
      pPar.file.write("signal)");
    } else {
      pPar.file.write("power)");
    }
    if (pWriteRule) {
      Rule.writeDefaultRule(pPar, pLayerNo);
    }
    pPar.file.endScope();
  }
}
