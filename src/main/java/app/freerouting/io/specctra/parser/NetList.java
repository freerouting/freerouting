package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Describes a list of nets sorted by its names. The net number is generated internally. */
public class NetList {

  /** The entries of this map are of type Net, the keys are the net_ids. */
  private final Map<Net.Id, Net> nets = new TreeMap<>();

  /** Returns true, if the netlist contains a net with the input name. */
  public boolean contains(Net.Id netId) {
    return nets.containsKey(netId);
  }

  /**
   * Adds a new net mit the input name to the net list. Returns null, if a net with p_name already
   * exists in the net list. In this case no new net is added.
   */
  public Net addNet(Net.Id netId) {
    Net result;
    if (nets.containsKey(netId)) {
      result = null;
    } else {
      result = new Net(netId);
      nets.put(netId, result);
    }
    return result;
  }

  /**
   * Returns the net with the input name, or null, if the netlist does not contain a net with the
   * input name.
   */
  public Net getNet(Net.Id netId) {
    return nets.get(netId);
  }

  /** Returns all nets in this net list containing the input pin. */
  public Collection<Net> getNets(String componentName, String pinName) {
    Collection<Net> result = new LinkedList<>();
    Net.Pin searchPin = new Net.Pin(componentName, pinName);
    Collection<Net> netList = nets.values();
    for (Net currentNet : netList) {
      Set<Net.Pin> netPins = currentNet.getPins();
      if (netPins != null && netPins.contains(searchPin)) {
        result.add(currentNet);
      }
    }
    return result;
  }
}
