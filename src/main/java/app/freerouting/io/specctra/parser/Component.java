package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.Pin;
import app.freerouting.core.Package;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** Handles the placement data of a library component. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:OverloadMethodsDeclarationOrder"
})
public class Component extends ScopeKeyword {

  /** Creates a new instance of Component. */
  public Component() {
    super("component");
  }

  /** Used also when reading a session file. */
  public static ComponentPlacement readScope(IJFlexScanner scanner) throws IOException {
    Object nextToken = scanner.nextToken();
    if (!(nextToken instanceof String name)) {
      FRLogger.warn(
          "Component.read_scope: component name expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    ComponentPlacement componentPlacement = new ComponentPlacement(name);
    Object prevToken = nextToken;
    nextToken = scanner.nextToken();
    while (nextToken != CLOSED_BRACKET) {
      if (prevToken == OPEN_BRACKET && nextToken == PLACE) {
        ComponentPlacement.ComponentLocation nextLocation = readPlaceScope(scanner);
        if (nextLocation != null) {
          componentPlacement.locations.add(nextLocation);
        }
      }
      prevToken = nextToken;
      nextToken = scanner.nextToken();
    }
    return componentPlacement;
  }

  public static void writeScope(WriteScopeParameter par, app.freerouting.board.Component component)
      throws IOException {
    par.file.startScope();
    par.file.write("place ");
    par.file.newLine();
    par.identifierType.write(component.name, par.file);
    if (component.isPlaced()) {
      double[] coor = par.coordinateTransform.boardToDsn(component.getLocation().toFloat());
      for (int i = 0; i < coor.length; i++) {
        par.file.write(" ");
        par.file.write(String.valueOf(coor[i]));
      }
      if (component.placedOnFront()) {
        par.file.write(" front ");
      } else {
        par.file.write(" back ");
      }
      int rotation = (int) Math.round(component.getRotationInDegree());
      par.file.write(String.valueOf(rotation));
    }
    if (component.positionFixed) {
      par.file.newLine();
      par.file.write(" (lock_type position)");
    }
    int pinCount = component.getPackage().pinCount();
    for (int i = 0; i < pinCount; i++) {
      writePinInfo(par, component, i);
    }
    writeKeepoutInfos(par, component);
    par.file.endScope();
  }

  private static void writePinInfo(
      WriteScopeParameter par, app.freerouting.board.Component component, int pinNo)
      throws IOException {
    if (!component.isPlaced()) {
      return;
    }
    Package.Pin packagePin = component.getPackage().getPin(pinNo);
    if (packagePin == null) {
      FRLogger.warn("Component.write_pin_info: package pin not found at '" + component.name + "'");
      return;
    }
    Pin componentPin = par.board.getPin(component.no, pinNo);
    if (componentPin == null) {
      FRLogger.warn(
          "Component.write_pin_info: component pin not found at '" + component.name + "'");
      return;
    }
    String clClassName = par.board.rules.clearanceMatrix.getName(componentPin.clearanceClassNo());
    if (clClassName == null) {
      FRLogger.warn(
          "Component.write_pin_info: clearance class  name not found at '" + component.name + "'");
      return;
    }
    par.file.newLine();
    par.file.write("(pin ");
    par.identifierType.write(packagePin.name, par.file);
    par.file.write(" (clearanceClass ");
    par.identifierType.write(clClassName, par.file);
    par.file.write("))");
  }

  private static void writeKeepoutInfos(
      WriteScopeParameter par, app.freerouting.board.Component component) throws IOException {
    if (!component.isPlaced()) {
      return;
    }
    Package.Keepout[] currKeepoutArr;
    String keepoutType;
    for (int j = 0; j < 3; j++) {
      if (j == 0) {
        currKeepoutArr = component.getPackage().keepoutArr;
        keepoutType = "(keepout ";
      } else if (j == 1) {
        currKeepoutArr = component.getPackage().viaKeepoutArr;
        keepoutType = "(via_keepout ";
      } else {
        currKeepoutArr = component.getPackage().placeKeepoutArr;
        keepoutType = "(place_keepout ";
      }
      for (int i = 0; i < currKeepoutArr.length; i++) {
        Package.Keepout currKeepout = currKeepoutArr[i];
        ObstacleArea currObstacleArea = getKeepout(par.board, component.no, currKeepout.name);
        if (currObstacleArea == null || currObstacleArea.clearanceClassNo() == 0) {
          continue;
        }
        String clClassName =
            par.board.rules.clearanceMatrix.getName(currObstacleArea.clearanceClassNo());
        if (clClassName == null) {
          FRLogger.warn(
              "Component.write_keepout_infos: clearance class name not found at '"
                  + component.name
                  + "'");
          return;
        }
        par.file.newLine();
        par.file.write(keepoutType);
        par.identifierType.write(currKeepout.name, par.file);
        par.file.write(" (clearanceClass ");
        par.identifierType.write(clClassName, par.file);
        par.file.write("))");
      }
    }
  }

  private static ObstacleArea getKeepout(BasicBoard board, int componentNo, String name) {
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      if (currItem.getComponentNo() == componentNo && currItem instanceof ObstacleArea currArea) {
        if (currArea.name != null && currArea.name.equals(name)) {
          return currArea;
        }
      }
    }
    return null;
  }

  private static ComponentPlacement.ComponentLocation readPlaceScope(IJFlexScanner scanner) {
    try {
      Map<String, ComponentPlacement.ItemClearanceInfo> pinInfos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> keepoutInfos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> viaKeepoutInfos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> placeKeepoutInfos = new TreeMap<>();

      String name = scanner.nextString(true);

      Object nextToken;
      double[] location = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = scanner.nextToken();
        if (nextToken instanceof Double double1) {
          location[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          location[i] = integer;
        } else if (nextToken == CLOSED_BRACKET) {
          // component is not yet placed
          return new ComponentPlacement.ComponentLocation(
              name,
              null,
              true,
              0,
              false,
              pinInfos,
              keepoutInfos,
              viaKeepoutInfos,
              placeKeepoutInfos,
              null);
        } else {
          FRLogger.warn(
              "Component.read_place_scope: Double was expected as the second and third "
                  + "parameter of the component/place command at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }

      nextToken = scanner.nextToken();
      boolean isFront = true;
      if (nextToken == BACK) {
        isFront = false;
      } else if (nextToken != FRONT) {
        FRLogger.warn(
            "Component.read_place_scope: Keyword.FRONT expected at '"
                + scanner.getScopeIdentifier()
                + "'");
      }
      double rotation;
      nextToken = scanner.nextToken();
      if (nextToken instanceof Double double1) {
        rotation = double1;
      } else if (nextToken instanceof Integer integer) {
        rotation = integer;
      } else {
        FRLogger.warn(
            "Component.read_place_scope: number expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      boolean positionFixed = false;
      String partNumber = null;
      nextToken = scanner.nextToken();
      while (nextToken == OPEN_BRACKET) {
        nextToken = scanner.nextToken();
        if (nextToken == LOCK_TYPE) {
          positionFixed = readLockType(scanner);
        } else if (nextToken == PIN) {
          ComponentPlacement.ItemClearanceInfo currPinInfo = readItemClearanceInfo(scanner);
          if (currPinInfo == null) {
            return null;
          }
          pinInfos.put(currPinInfo.name, currPinInfo);
        } else if (nextToken == KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo = readItemClearanceInfo(scanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          keepoutInfos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == VIA_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo = readItemClearanceInfo(scanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          viaKeepoutInfos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == PLACE_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo = readItemClearanceInfo(scanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          placeKeepoutInfos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == Keyword.PN
            || (nextToken instanceof String && "PN".equalsIgnoreCase((String) nextToken))) {
          partNumber = DsnFile.readStringScope(scanner);
        } else {
          skipScope(scanner);
        }
        nextToken = scanner.nextToken();
      }
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Component.read_place_scope: ) expected at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      return new ComponentPlacement.ComponentLocation(
          name,
          location,
          isFront,
          rotation,
          positionFixed,
          pinInfos,
          keepoutInfos,
          viaKeepoutInfos,
          placeKeepoutInfos,
          partNumber);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return null;
    }
  }

  private static ComponentPlacement.ItemClearanceInfo readItemClearanceInfo(IJFlexScanner scanner)
      throws IOException {
    scanner.yybegin(SpecctraDsnStreamReader.NAME);
    Object nextToken = scanner.nextToken();
    if (!(nextToken instanceof String name)) {
      FRLogger.warn(
          "Component.read_item_clearance_info: String expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    String clClassName = null;
    nextToken = scanner.nextToken();
    while (nextToken == OPEN_BRACKET) {
      nextToken = scanner.nextToken();
      if (nextToken == CLEARANCE_CLASS) {
        clClassName = DsnFile.readStringScope(scanner);
      } else {
        skipScope(scanner);
      }
      nextToken = scanner.nextToken();
    }
    if (nextToken != CLOSED_BRACKET) {
      FRLogger.warn(
          "Component.read_item_clearance_info: ) expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    if (clClassName == null) {
      FRLogger.warn(
          "Component.read_item_clearance_info: clearance class name not found at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    return new ComponentPlacement.ItemClearanceInfo(name, clClassName);
  }

  private static boolean readLockType(IJFlexScanner scanner) throws IOException {
    boolean result = false;
    for (; ; ) {
      Object nextToken = scanner.nextToken();
      if (nextToken == CLOSED_BRACKET) {
        break;
      }
      if (nextToken == POSITION) {
        result = true;
      }
    }
    return result;
  }

  /** Overwrites the function read_scope in ScopeKeyword. */
  @Override
  public boolean readScope(ReadScopeParameter par) {
    try {
      ComponentPlacement componentPlacement = readScope(par.scanner);
      if (componentPlacement == null) {
        return false;
      }
      par.placementList.add(componentPlacement);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }
}
