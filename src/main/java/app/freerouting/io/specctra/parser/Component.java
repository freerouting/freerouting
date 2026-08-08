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
public class Component extends ScopeKeyword {

  /** Creates a new instance of Component */
  public Component() {
    super("component");
  }

  /** Used also when reading a session file. */
  public static ComponentPlacement readScope(IJFlexScanner pScanner) throws IOException {
    Object nextToken = pScanner.nextToken();
    if (!(nextToken instanceof String name)) {
      FRLogger.warn(
          "Component.read_scope: component name expected at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    ComponentPlacement componentPlacement = new ComponentPlacement(name);
    Object prevToken = nextToken;
    nextToken = pScanner.nextToken();
    while (nextToken != CLOSED_BRACKET) {
      if (prevToken == OPEN_BRACKET && nextToken == PLACE) {
        ComponentPlacement.ComponentLocation nextLocation = readPlaceScope(pScanner);
        if (nextLocation != null) {
          componentPlacement.locations.add(nextLocation);
        }
      }
      prevToken = nextToken;
      nextToken = pScanner.nextToken();
    }
    return componentPlacement;
  }

  public static void writeScope(
      WriteScopeParameter pPar, app.freerouting.board.Component pComponent) throws IOException {
    pPar.file.startScope();
    pPar.file.write("place ");
    pPar.file.newLine();
    pPar.identifierType.write(pComponent.name, pPar.file);
    if (pComponent.isPlaced()) {
      double[] coor = pPar.coordinateTransform.boardToDsn(pComponent.getLocation().toFloat());
      for (int i = 0; i < coor.length; i++) {
        pPar.file.write(" ");
        pPar.file.write(String.valueOf(coor[i]));
      }
      if (pComponent.placedOnFront()) {
        pPar.file.write(" front ");
      } else {
        pPar.file.write(" back ");
      }
      int rotation = (int) Math.round(pComponent.getRotationInDegree());
      pPar.file.write(String.valueOf(rotation));
    }
    if (pComponent.positionFixed) {
      pPar.file.newLine();
      pPar.file.write(" (lock_type position)");
    }
    int pinCount = pComponent.getPackage().pinCount();
    for (int i = 0; i < pinCount; i++) {
      writePinInfo(pPar, pComponent, i);
    }
    writeKeepoutInfos(pPar, pComponent);
    pPar.file.endScope();
  }

  private static void writePinInfo(
      WriteScopeParameter pPar, app.freerouting.board.Component pComponent, int pPinNo)
      throws IOException {
    if (!pComponent.isPlaced()) {
      return;
    }
    Package.Pin packagePin = pComponent.getPackage().getPin(pPinNo);
    if (packagePin == null) {
      FRLogger.warn("Component.write_pin_info: package pin not found at '" + pComponent.name + "'");
      return;
    }
    Pin componentPin = pPar.board.getPin(pComponent.no, pPinNo);
    if (componentPin == null) {
      FRLogger.warn(
          "Component.write_pin_info: component pin not found at '" + pComponent.name + "'");
      return;
    }
    String clClassName = pPar.board.rules.clearanceMatrix.getName(componentPin.clearanceClassNo());
    if (clClassName == null) {
      FRLogger.warn(
          "Component.write_pin_info: clearance class  name not found at '" + pComponent.name + "'");
      return;
    }
    pPar.file.newLine();
    pPar.file.write("(pin ");
    pPar.identifierType.write(packagePin.name, pPar.file);
    pPar.file.write(" (clearanceClass ");
    pPar.identifierType.write(clClassName, pPar.file);
    pPar.file.write("))");
  }

  private static void writeKeepoutInfos(
      WriteScopeParameter pPar, app.freerouting.board.Component pComponent) throws IOException {
    if (!pComponent.isPlaced()) {
      return;
    }
    Package.Keepout[] currKeepoutArr;
    String keepoutType;
    for (int j = 0; j < 3; j++) {
      if (j == 0) {
        currKeepoutArr = pComponent.getPackage().keepoutArr;
        keepoutType = "(keepout ";
      } else if (j == 1) {
        currKeepoutArr = pComponent.getPackage().viaKeepoutArr;
        keepoutType = "(via_keepout ";
      } else {
        currKeepoutArr = pComponent.getPackage().placeKeepoutArr;
        keepoutType = "(place_keepout ";
      }
      for (int i = 0; i < currKeepoutArr.length; i++) {
        Package.Keepout currKeepout = currKeepoutArr[i];
        ObstacleArea currObstacleArea = getKeepout(pPar.board, pComponent.no, currKeepout.name);
        if (currObstacleArea == null || currObstacleArea.clearanceClassNo() == 0) {
          continue;
        }
        String clClassName =
            pPar.board.rules.clearanceMatrix.getName(currObstacleArea.clearanceClassNo());
        if (clClassName == null) {
          FRLogger.warn(
              "Component.write_keepout_infos: clearance class name not found at '"
                  + pComponent.name
                  + "'");
          return;
        }
        pPar.file.newLine();
        pPar.file.write(keepoutType);
        pPar.identifierType.write(currKeepout.name, pPar.file);
        pPar.file.write(" (clearanceClass ");
        pPar.identifierType.write(clClassName, pPar.file);
        pPar.file.write("))");
      }
    }
  }

  private static ObstacleArea getKeepout(BasicBoard pBoard, int pComponentNo, String pName) {
    Iterator<UndoableObjects.UndoableObjectNode> it = pBoard.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) pBoard.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      if (currItem.getComponentNo() == pComponentNo && currItem instanceof ObstacleArea currArea) {
        if (currArea.name != null && currArea.name.equals(pName)) {
          return currArea;
        }
      }
    }
    return null;
  }

  private static ComponentPlacement.ComponentLocation readPlaceScope(IJFlexScanner pScanner) {
    try {
      Map<String, ComponentPlacement.ItemClearanceInfo> pinInfos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> keepoutInfos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> viaKeepoutInfos = new TreeMap<>();
      Map<String, ComponentPlacement.ItemClearanceInfo> placeKeepoutInfos = new TreeMap<>();

      String name = pScanner.nextString(true);

      Object nextToken;
      double[] location = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = pScanner.nextToken();
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
              "Component.read_place_scope: Double was expected as the second and third parameter of the component/place command at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }

      nextToken = pScanner.nextToken();
      boolean isFront = true;
      if (nextToken == BACK) {
        isFront = false;
      } else if (nextToken != FRONT) {
        FRLogger.warn(
            "Component.read_place_scope: Keyword.FRONT expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
      }
      double rotation;
      nextToken = pScanner.nextToken();
      if (nextToken instanceof Double double1) {
        rotation = double1;
      } else if (nextToken instanceof Integer integer) {
        rotation = integer;
      } else {
        FRLogger.warn(
            "Component.read_place_scope: number expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      boolean positionFixed = false;
      String partNumber = null;
      nextToken = pScanner.nextToken();
      while (nextToken == OPEN_BRACKET) {
        nextToken = pScanner.nextToken();
        if (nextToken == LOCK_TYPE) {
          positionFixed = readLockType(pScanner);
        } else if (nextToken == PIN) {
          ComponentPlacement.ItemClearanceInfo currPinInfo = readItemClearanceInfo(pScanner);
          if (currPinInfo == null) {
            return null;
          }
          pinInfos.put(currPinInfo.name, currPinInfo);
        } else if (nextToken == KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo = readItemClearanceInfo(pScanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          keepoutInfos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == VIA_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo = readItemClearanceInfo(pScanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          viaKeepoutInfos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == PLACE_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currKeepoutInfo = readItemClearanceInfo(pScanner);
          if (currKeepoutInfo == null) {
            return null;
          }
          placeKeepoutInfos.put(currKeepoutInfo.name, currKeepoutInfo);
        } else if (nextToken == Keyword.PN
            || (nextToken instanceof String && "PN".equalsIgnoreCase((String) nextToken))) {
          partNumber = DsnFile.readStringScope(pScanner);
        } else {
          skipScope(pScanner);
        }
        nextToken = pScanner.nextToken();
      }
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Component.read_place_scope: ) expected at '" + pScanner.getScopeIdentifier() + "'");
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

  private static ComponentPlacement.ItemClearanceInfo readItemClearanceInfo(IJFlexScanner pScanner)
      throws IOException {
    pScanner.yybegin(SpecctraDsnStreamReader.NAME);
    Object nextToken = pScanner.nextToken();
    if (!(nextToken instanceof String name)) {
      FRLogger.warn(
          "Component.read_item_clearance_info: String expected at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    String clClassName = null;
    nextToken = pScanner.nextToken();
    while (nextToken == OPEN_BRACKET) {
      nextToken = pScanner.nextToken();
      if (nextToken == CLEARANCE_CLASS) {
        clClassName = DsnFile.readStringScope(pScanner);
      } else {
        skipScope(pScanner);
      }
      nextToken = pScanner.nextToken();
    }
    if (nextToken != CLOSED_BRACKET) {
      FRLogger.warn(
          "Component.read_item_clearance_info: ) expected at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    if (clClassName == null) {
      FRLogger.warn(
          "Component.read_item_clearance_info: clearance class name not found at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    return new ComponentPlacement.ItemClearanceInfo(name, clClassName);
  }

  private static boolean readLockType(IJFlexScanner pScanner) throws IOException {
    boolean result = false;
    for (; ; ) {
      Object nextToken = pScanner.nextToken();
      if (nextToken == CLOSED_BRACKET) {
        break;
      }
      if (nextToken == POSITION) {
        result = true;
      }
    }
    return result;
  }

  /** Overwrites the function read_scope in ScopeKeyword */
  @Override
  public boolean readScope(ReadScopeParameter pPar) {
    try {
      ComponentPlacement componentPlacement = readScope(pPar.scanner);
      if (componentPlacement == null) {
        return false;
      }
      pPar.placementList.add(componentPlacement);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }
}
