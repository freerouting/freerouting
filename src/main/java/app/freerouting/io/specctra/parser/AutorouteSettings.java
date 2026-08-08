package app.freerouting.io.specctra.parser;

import app.freerouting.board.Layer;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;

public final class AutorouteSettings {

  private AutorouteSettings() {}

  static RouterSettings readScope(IJFlexScanner pScanner, LayerStructure pLayerStructure) {
    RouterSettings result = new RouterSettings();
    result.setLayerCount(pLayerStructure.arr.length);
    boolean withAutoroute = true;
    boolean withPostroute = true;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pScanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("AutorouteSettings.read_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "AutorouteSettings.read_scope: unexpected end of file at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.FANOUT) {
          DsnFile.readOnOffScope(pScanner);
        } else if (nextToken == Keyword.AUTOROUTE) {
          withAutoroute = DsnFile.readOnOffScope(pScanner);
        } else if (nextToken == Keyword.POSTROUTE) {
          withPostroute = DsnFile.readOnOffScope(pScanner);
        } else if (nextToken == Keyword.VIAS) {
          result.setViasAllowed(DsnFile.readOnOffScope(pScanner));
        } else if (nextToken == Keyword.VIA_COSTS) {
          result.setViaCosts(DsnFile.readIntegerScope(pScanner));
        } else if (nextToken == Keyword.PLANE_VIA_COSTS) {
          result.setPlaneViaCosts(DsnFile.readIntegerScope(pScanner));
        } else if (nextToken == Keyword.START_RIPUP_COSTS) {
          result.setStartRipupCosts(DsnFile.readIntegerScope(pScanner));
        } else if (nextToken == Keyword.LAYER_RULE) {
          result = readLayerRule(pScanner, pLayerStructure, result);
          if (result == null) {
            return null;
          }
        } else {
          ScopeKeyword.skipScope(pScanner);
        }
      }
    }
    result.setRunRouter(withAutoroute);
    result.setRunOptimizer(withPostroute);
    return result;
  }

  static RouterSettings readLayerRule(
      IJFlexScanner pScanner, LayerStructure pLayerStructure, RouterSettings pSettings) {
    pScanner.yybegin(SpecctraDsnStreamReader.NAME);
    Object nextToken;
    try {
      nextToken = pScanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
      return null;
    }
    if (!(nextToken instanceof String)) {
      FRLogger.warn(
          "AutorouteSettings.read_layer_rule: String expected at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    int layerNo = pLayerStructure.getNo((String) nextToken);
    if (layerNo < 0) {
      FRLogger.warn(
          "AutorouteSettings.read_layer_rule: layer not found at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pScanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "AutorouteSettings.read_layer_rule: unexpected end of file at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.ACTIVE) {
          pSettings.setLayerActive(layerNo, DsnFile.readOnOffScope(pScanner));
        } else if (nextToken == Keyword.PREFERRED_DIRECTION) {
          try {
            boolean prefDirIsHorizontal = true;
            nextToken = pScanner.nextToken();
            if (nextToken == Keyword.VERTICAL) {
              prefDirIsHorizontal = false;
            } else if (nextToken != Keyword.HORIZONTAL) {
              FRLogger.warn(
                  "AutorouteSettings.read_layer_rule: unexpected key word at '"
                      + pScanner.getScopeIdentifier()
                      + "'");
              return null;
            }
            pSettings.setPreferredDirectionIsHorizontal(layerNo, prefDirIsHorizontal);
            nextToken = pScanner.nextToken();
            if (nextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "AutorouteSettings.read_layer_rule: closing bracket expected at '"
                      + pScanner.getScopeIdentifier()
                      + "'");
              return null;
            }
          } catch (IOException e) {
            FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
            return null;
          }
        } else if (nextToken == Keyword.PREFERRED_DIRECTION_TRACE_COSTS) {
          pSettings.setPreferredDirectionTraceCosts(layerNo, DsnFile.readFloatScope(pScanner));
        } else if (nextToken == Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS) {
          pSettings.setAgainstPreferredDirectionTraceCosts(
              layerNo, DsnFile.readFloatScope(pScanner));
        } else {
          ScopeKeyword.skipScope(pScanner);
        }
      }
    }
    return pSettings;
  }

  public static void writeScope(
      IndentFileWriter pFile,
      RouterSettings pSettings,
      app.freerouting.board.LayerStructure pLayerStructure,
      IdentifierType pIdentifierType)
      throws IOException {
    pFile.startScope();
    pFile.write("autorouteSettings");
    pFile.newLine();
    pFile.write("(autoroute ");
    if (pSettings.getRunRouter()) {
      pFile.write("on)");
    } else {
      pFile.write("off)");
    }
    pFile.newLine();
    pFile.write("(postroute ");
    if (pSettings.getRunOptimizer()) {
      pFile.write("on)");
    } else {
      pFile.write("off)");
    }
    pFile.newLine();
    pFile.write("(vias ");
    if (pSettings.getViasAllowed()) {
      pFile.write("on)");
    } else {
      pFile.write("off)");
    }
    pFile.newLine();
    pFile.write("(viaCosts ");
    {
      int viaCosts = pSettings.getViaCosts();
      pFile.write(String.valueOf(viaCosts));
    }
    pFile.write(")");
    pFile.newLine();
    pFile.write("(plane_via_costs ");
    {
      int viaCosts = pSettings.getPlaneViaCosts();
      pFile.write(String.valueOf(viaCosts));
    }
    pFile.write(")");
    pFile.newLine();
    pFile.write("(startRipupCosts ");
    {
      int ripupCosts = pSettings.getStartRipupCosts();
      pFile.write(String.valueOf(ripupCosts));
    }
    pFile.write(")");
    pFile.newLine();
    for (int i = 0; i < pLayerStructure.arr.length; i++) {
      Layer currLayer = pLayerStructure.arr[i];
      pFile.startScope();
      pFile.write("layer_rule ");
      pIdentifierType.write(currLayer.name, pFile);
      pFile.newLine();
      pFile.write("(active ");
      if (pSettings.getLayerActive(i)) {
        pFile.write("on)");
      } else {
        pFile.write("off)");
      }
      pFile.newLine();
      pFile.write("(preferred_direction ");
      if (pSettings.getPreferredDirectionIsHorizontal(i)) {
        pFile.write("horizontal)");
      } else {
        pFile.write("vertical)");
      }
      pFile.newLine();
      pFile.write("(preferred_direction_trace_costs ");
      float traceCosts = (float) pSettings.getPreferredDirectionTraceCosts(i);
      pFile.write(String.valueOf(traceCosts));
      pFile.write(")");
      pFile.newLine();
      pFile.write("(against_preferred_direction_trace_costs ");
      traceCosts = (float) pSettings.getAgainstPreferredDirectionTraceCosts(i);
      pFile.write(String.valueOf(traceCosts));
      pFile.write(")");
      pFile.endScope();
    }
    pFile.endScope();
  }
}
