package app.freerouting.designforms.specctra;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/** Describes the placement data of a library component */
public class ComponentPlacement {

  /** The name of the corresponding library component */
  public final String lib_name;
  /** The list of ComponentLocations of the library component on the board. */
  public final Collection<ComponentLocation> locations;

  /** Creates a new instance of ComponentPlacement */
  public ComponentPlacement(String p_lib_name) {
    lib_name = p_lib_name;
    locations = new LinkedList<ComponentLocation>();
  }

  /** The structure of an entry in the list locations. */
  public static class ComponentLocation {
    public final String name;
    /** the x- and the y-coordinate of the location. */
    public final double[] coor;
    /**
     * True, if the component is placed at the component side. Else the component is placed at the
     * solder side.
     */
    public final boolean is_front;
    /** The rotation of the component in degree. */
    public final double rotation;
    /** If true, the component cannot be moved. */
    public final boolean position_fixed;
    /** The entries of this map are of type ItemClearanceInfo, the keys are the pin names. */
    public final Map<String, ItemClearanceInfo> pin_infos;
    public final Map<String, ItemClearanceInfo> keepout_infos;
    public final Map<String, ItemClearanceInfo> via_keepout_infos;
    public final Map<String, ItemClearanceInfo> place_keepout_infos;

    ComponentLocation(
        String p_name,
        double[] p_coor,
        boolean p_is_front,
        double p_rotation,
        boolean p_position_fixed,
        Map<String, ItemClearanceInfo> p_pin_infos,
        Map<String, ItemClearanceInfo> p_keepout_infos,
        Map<String, ItemClearanceInfo> p_via_keepout_infos,
        Map<String, ItemClearanceInfo> p_place_keepout_infos) {
      name = p_name;
      coor = p_coor;
      is_front = p_is_front;
      rotation = p_rotation;
      position_fixed = p_position_fixed;
      pin_infos = p_pin_infos;
      keepout_infos = p_keepout_infos;
      via_keepout_infos = p_via_keepout_infos;
      place_keepout_infos = p_place_keepout_infos;
    }
  }

  public static class ItemClearanceInfo {
    public final String name;
    public final String clearance_class;
    ItemClearanceInfo(String p_name, String p_clearance_class) {
      name = p_name;
      clearance_class = p_clearance_class;
    }
  }
}
