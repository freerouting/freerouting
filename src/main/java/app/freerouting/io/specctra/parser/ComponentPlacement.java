package app.freerouting.io.specctra.parser;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/** Describes the placement data of a library component */
public class ComponentPlacement {

  /** The name of the corresponding library component */
  public final String libName;

  /** The list of ComponentLocations of the library component on the board. */
  public final Collection<ComponentLocation> locations;

  /** Creates a new instance of ComponentPlacement */
  public ComponentPlacement(String pLibName) {
    libName = pLibName;
    locations = new LinkedList<>();
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
    public final boolean isFront;

    /** The rotation of the component in degree. */
    public final double rotation;

    /** If true, the component cannot be moved. */
    public final boolean positionFixed;

    /** The entries of this map are of type ItemClearanceInfo, the keys are the pin names. */
    public final Map<String, ItemClearanceInfo> pin_infos;

    public final Map<String, ItemClearanceInfo> keepout_infos;
    public final Map<String, ItemClearanceInfo> via_keepout_infos;
    public final Map<String, ItemClearanceInfo> place_keepout_infos;

    public final String partNumber;

    ComponentLocation(
        String pName,
        double[] pCoor,
        boolean pIsFront,
        double pRotation,
        boolean pPositionFixed,
        Map<String, ItemClearanceInfo> pPinInfos,
        Map<String, ItemClearanceInfo> pKeepoutInfos,
        Map<String, ItemClearanceInfo> pViaKeepoutInfos,
        Map<String, ItemClearanceInfo> pPlaceKeepoutInfos,
        String pPartNumber) {
      name = pName;
      coor = pCoor;
      isFront = pIsFront;
      rotation = pRotation;
      positionFixed = pPositionFixed;
      pin_infos = pPinInfos;
      keepout_infos = pKeepoutInfos;
      via_keepout_infos = pViaKeepoutInfos;
      place_keepout_infos = pPlaceKeepoutInfos;
      partNumber = pPartNumber;
    }
  }

  public static class ItemClearanceInfo {

    public final String name;
    public final String clearanceClass;

    ItemClearanceInfo(String pName, String pClearanceClass) {
      name = pName;
      clearanceClass = pClearanceClass;
    }
  }
}
