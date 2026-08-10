package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/** Describes placement data for a library component. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class ComponentPlacement {

  /** The name of the corresponding library component. */
  public final String libName;

  /** The list of ComponentLocations of the library component on the board. */
  public final Collection<ComponentLocation> locations;

  /** Creates a new instance of ComponentPlacement. */
  public ComponentPlacement(String libName) {
    this.libName = libName;
    locations = new LinkedList<>();
  }

  /** The structure of an entry in the list locations. */
  public static class ComponentLocation {

    public final String name;

    /** The x- and y-coordinates of the location. */
    public final double[] coor;

    /**
     * True, if the component is placed at the component side. Else the component is placed at the
     * solder side.
     */
    public final boolean isFront;

    /** The rotation of the component in degree. */
    public final double rotation;

    /** If true, the component cannot be moved. */
    public final boolean positionFixed;

    /** The entries of this map are of type ItemClearanceInfo, the keys are the pin names. */
    @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
    public final Map<String, ItemClearanceInfo> pin_infos;

    @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
    public final Map<String, ItemClearanceInfo> keepout_infos;

    @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
    public final Map<String, ItemClearanceInfo> via_keepout_infos;

    @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
    public final Map<String, ItemClearanceInfo> place_keepout_infos;

    public final String partNumber;

    ComponentLocation(
        String name,
        double[] coor,
        boolean isFront,
        double rotation,
        boolean positionFixed,
        Map<String, ItemClearanceInfo> pinInfos,
        Map<String, ItemClearanceInfo> keepoutInfos,
        Map<String, ItemClearanceInfo> viaKeepoutInfos,
        Map<String, ItemClearanceInfo> placeKeepoutInfos,
        String partNumber) {
      this.name = name;
      this.coor = coor;
      this.isFront = isFront;
      this.rotation = rotation;
      this.positionFixed = positionFixed;
      pin_infos = pinInfos;
      keepout_infos = keepoutInfos;
      via_keepout_infos = viaKeepoutInfos;
      place_keepout_infos = placeKeepoutInfos;
      this.partNumber = partNumber;
    }
  }

  public static class ItemClearanceInfo {

    public final String name;
    public final String clearanceClass;

    ItemClearanceInfo(String name, String clearanceClass) {
      this.name = name;
      this.clearanceClass = clearanceClass;
    }
  }
}
