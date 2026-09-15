package app.freerouting.io.specctra.parser;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.library.Package;
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
    while (nextToken != CLOSED_BRACKET && nextToken != null) {
      if (prevToken == OPEN_BRACKET && nextToken == PLACE) {
        ComponentPlacement.ComponentLocation nextLocation = readPlaceScope(scanner);
        if (nextLocation != null) {
          componentPlacement.locations.add(nextLocation);
        } else {
          return null;
        }
      }
      prevToken = nextToken;
      nextToken = scanner.nextToken();
    }
    return componentPlacement;
  }

  public static void writeScope(
      WriteScopeParameter scopeParameter, app.freerouting.board.model.structure.Component component)
      throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("place ");
    scopeParameter.file.newLine();
    scopeParameter.identifierType.write(component.name, scopeParameter.file);
    if (component.isPlaced()) {
      double[] coor =
          scopeParameter.coordinateTransform.boardToDsn(component.getLocation().toFloat());
      for (int i = 0; i < coor.length; i++) {
        scopeParameter.file.write(" ");
        scopeParameter.file.write(String.valueOf(coor[i]));
      }
      if (component.placedOnFront()) {
        scopeParameter.file.write(" front ");
      } else {
        scopeParameter.file.write(" back ");
      }
      int rotation = (int) Math.round(component.getRotationInDegree());
      scopeParameter.file.write(String.valueOf(rotation));
    }
    if (component.positionFixed) {
      scopeParameter.file.newLine();
      scopeParameter.file.write(" (lock_type position)");
    }
    int pinCount = component.getPackage().pinCount();
    for (int i = 0; i < pinCount; i++) {
      writePinInfo(scopeParameter, component, i);
    }
    writeKeepoutInfos(scopeParameter, component);
    scopeParameter.file.endScope();
  }

  private static void writePinInfo(
      WriteScopeParameter scopeParameter,
      app.freerouting.board.model.structure.Component component,
      int pinIndex)
      throws IOException {
    if (!component.isPlaced()) {
      return;
    }
    Package.Pin packagePin = component.getPackage().getPin(pinIndex);
    if (packagePin == null) {
      FRLogger.warn("Component.write_pin_info: package pin not found at '" + component.name + "'");
      return;
    }
    Pin componentPin = scopeParameter.board.getPin(component.id, pinIndex);
    if (componentPin == null) {
      FRLogger.warn(
          "Component.write_pin_info: component pin not found at '" + component.name + "'");
      return;
    }
    String clClassName =
        scopeParameter.board.rules.clearanceMatrix.getName(componentPin.clearanceClassIndex());
    if (clClassName == null) {
      FRLogger.warn(
          "Component.write_pin_info: clearance class  name not found at '" + component.name + "'");
      return;
    }
    scopeParameter.file.newLine();
    scopeParameter.file.write("(pin ");
    scopeParameter.identifierType.write(packagePin.name, scopeParameter.file);
    scopeParameter.file.write(" (clearance_class ");
    scopeParameter.identifierType.write(clClassName, scopeParameter.file);
    scopeParameter.file.write("))");
  }

  private static void writeKeepoutInfos(
      WriteScopeParameter scopeParameter, app.freerouting.board.model.structure.Component component)
      throws IOException {
    if (!component.isPlaced()) {
      return;
    }
    Package.Keepout[] currentKeepoutArr;
    String keepoutType;
    for (int j = 0; j < 3; j++) {
      if (j == 0) {
        currentKeepoutArr = component.getPackage().keepouts;
        keepoutType = "(keepout ";
      } else if (j == 1) {
        currentKeepoutArr = component.getPackage().viaKeepouts;
        keepoutType = "(via_keepout ";
      } else {
        currentKeepoutArr = component.getPackage().placeKeepoutArr;
        keepoutType = "(place_keepout ";
      }
      for (int i = 0; i < currentKeepoutArr.length; i++) {
        Package.Keepout currentKeepout = currentKeepoutArr[i];
        ObstacleArea currentObstacleArea =
            getKeepout(scopeParameter.board, component.id, currentKeepout.name);
        if (currentObstacleArea == null || currentObstacleArea.clearanceClassIndex() == 0) {
          continue;
        }
        String clClassName =
            scopeParameter.board.rules.clearanceMatrix.getName(
                currentObstacleArea.clearanceClassIndex());
        if (clClassName == null) {
          FRLogger.warn(
              "Component.write_keepout_infos: clearance class name not found at '"
                  + component.name
                  + "'");
          return;
        }
        scopeParameter.file.newLine();
        scopeParameter.file.write(keepoutType);
        scopeParameter.identifierType.write(currentKeepout.name, scopeParameter.file);
        scopeParameter.file.write(" (clearance_class ");
        scopeParameter.identifierType.write(clClassName, scopeParameter.file);
        scopeParameter.file.write("))");
      }
    }
  }

  private static ObstacleArea getKeepout(BasicBoard board, int componentId, String name) {
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem.getComponentId() == componentId
          && currentItem instanceof ObstacleArea currentArea) {
        if (currentArea.name != null && currentArea.name.equals(name)) {
          return currentArea;
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
          ComponentPlacement.ItemClearanceInfo currentPinInfo = readItemClearanceInfo(scanner);
          if (currentPinInfo == null) {
            return null;
          }
          pinInfos.put(currentPinInfo.name, currentPinInfo);
        } else if (nextToken == KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currentKeepoutInfo = readItemClearanceInfo(scanner);
          if (currentKeepoutInfo == null) {
            return null;
          }
          keepoutInfos.put(currentKeepoutInfo.name, currentKeepoutInfo);
        } else if (nextToken == VIA_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currentKeepoutInfo = readItemClearanceInfo(scanner);
          if (currentKeepoutInfo == null) {
            return null;
          }
          viaKeepoutInfos.put(currentKeepoutInfo.name, currentKeepoutInfo);
        } else if (nextToken == PLACE_KEEPOUT) {
          ComponentPlacement.ItemClearanceInfo currentKeepoutInfo = readItemClearanceInfo(scanner);
          if (currentKeepoutInfo == null) {
            return null;
          }
          placeKeepoutInfos.put(currentKeepoutInfo.name, currentKeepoutInfo);
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
      if (nextToken == CLEARANCE_CLASS
          || (nextToken instanceof String s
              && ("clearance_class".equalsIgnoreCase(s) || "clearanceClass".equalsIgnoreCase(s)))) {
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
  public boolean readScope(ReadScopeParameter scopeParameter) {
    try {
      ComponentPlacement componentPlacement = readScope(scopeParameter.scanner);
      if (componentPlacement == null) {
        return false;
      }
      scopeParameter.placementList.add(componentPlacement);
    } catch (IOException e) {
      FRLogger.error("Component.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }
}
