package app.freerouting.io.specctra.parser;

import app.freerouting.board.Layer;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;

public final class AutorouteSettings {

  private AutorouteSettings() {}

  static RouterSettings readScope(IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    RouterSettings result = new RouterSettings();
    result.setLayerCount(p_layer_structure.arr.length);
    boolean withAutoroute = true;
    boolean withPostroute = true;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("AutorouteSettings.read_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "AutorouteSettings.read_scope: unexpected end of file at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.FANOUT) {
          DsnFile.readOnOffScope(p_scanner);
        } else if (nextToken == Keyword.AUTOROUTE) {
          withAutoroute = DsnFile.readOnOffScope(p_scanner);
        } else if (nextToken == Keyword.POSTROUTE) {
          withPostroute = DsnFile.readOnOffScope(p_scanner);
        } else if (nextToken == Keyword.VIAS) {
          result.setViasAllowed(DsnFile.readOnOffScope(p_scanner));
        } else if (nextToken == Keyword.VIA_COSTS) {
          result.setViaCosts(DsnFile.readIntegerScope(p_scanner));
        } else if (nextToken == Keyword.PLANE_VIA_COSTS) {
          result.setPlaneViaCosts(DsnFile.readIntegerScope(p_scanner));
        } else if (nextToken == Keyword.START_RIPUP_COSTS) {
          result.setStartRipupCosts(DsnFile.readIntegerScope(p_scanner));
        } else if (nextToken == Keyword.LAYER_RULE) {
          result = readLayerRule(p_scanner, p_layer_structure, result);
          if (result == null) {
            return null;
          }
        } else {
          ScopeKeyword.skipScope(p_scanner);
        }
      }
    }
    result.setRunRouter(withAutoroute);
    result.setRunOptimizer(withPostroute);
    return result;
  }

  static RouterSettings readLayerRule(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure, RouterSettings p_settings) {
    p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
    Object nextToken;
    try {
      nextToken = p_scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
      return null;
    }
    if (!(nextToken instanceof String)) {
      FRLogger.warn(
          "AutorouteSettings.read_layer_rule: String expected at '"
              + p_scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    int layerNo = p_layer_structure.getNo((String) nextToken);
    if (layerNo < 0) {
      FRLogger.warn(
          "AutorouteSettings.read_layer_rule: layer not found at '"
              + p_scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "AutorouteSettings.read_layer_rule: unexpected end of file at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.ACTIVE) {
          p_settings.setLayerActive(layerNo, DsnFile.readOnOffScope(p_scanner));
        } else if (nextToken == Keyword.PREFERRED_DIRECTION) {
          try {
            boolean prefDirIsHorizontal = true;
            nextToken = p_scanner.nextToken();
            if (nextToken == Keyword.VERTICAL) {
              prefDirIsHorizontal = false;
            } else if (nextToken != Keyword.HORIZONTAL) {
              FRLogger.warn(
                  "AutorouteSettings.read_layer_rule: unexpected key word at '"
                      + p_scanner.getScopeIdentifier()
                      + "'");
              return null;
            }
            p_settings.setPreferredDirectionIsHorizontal(layerNo, prefDirIsHorizontal);
            nextToken = p_scanner.nextToken();
            if (nextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "AutorouteSettings.read_layer_rule: closing bracket expected at '"
                      + p_scanner.getScopeIdentifier()
                      + "'");
              return null;
            }
          } catch (IOException e) {
            FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
            return null;
          }
        } else if (nextToken == Keyword.PREFERRED_DIRECTION_TRACE_COSTS) {
          p_settings.setPreferredDirectionTraceCosts(
              layerNo, DsnFile.readFloatScope(p_scanner));
        } else if (nextToken == Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS) {
          p_settings.setAgainstPreferredDirectionTraceCosts(
              layerNo, DsnFile.readFloatScope(p_scanner));
        } else {
          ScopeKeyword.skipScope(p_scanner);
        }
      }
    }
    return p_settings;
  }

  public static void writeScope(
      IndentFileWriter p_file,
      RouterSettings p_settings,
      app.freerouting.board.LayerStructure p_layer_structure,
      IdentifierType p_identifier_type)
      throws IOException {
    p_file.startScope();
    p_file.write("autorouteSettings");
    p_file.newLine();
    p_file.write("(autoroute ");
    if (p_settings.getRunRouter()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.newLine();
    p_file.write("(postroute ");
    if (p_settings.getRunOptimizer()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.newLine();
    p_file.write("(vias ");
    if (p_settings.getViasAllowed()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.newLine();
    p_file.write("(viaCosts ");
    {
      int viaCosts = p_settings.getViaCosts();
      p_file.write(String.valueOf(viaCosts));
    }
    p_file.write(")");
    p_file.newLine();
    p_file.write("(plane_via_costs ");
    {
      int viaCosts = p_settings.getPlaneViaCosts();
      p_file.write(String.valueOf(viaCosts));
    }
    p_file.write(")");
    p_file.newLine();
    p_file.write("(startRipupCosts ");
    {
      int ripupCosts = p_settings.getStartRipupCosts();
      p_file.write(String.valueOf(ripupCosts));
    }
    p_file.write(")");
    p_file.newLine();
    for (int i = 0; i < p_layer_structure.arr.length; i++) {
      Layer currLayer = p_layer_structure.arr[i];
      p_file.startScope();
      p_file.write("layer_rule ");
      p_identifier_type.write(currLayer.name, p_file);
      p_file.newLine();
      p_file.write("(active ");
      if (p_settings.getLayerActive(i)) {
        p_file.write("on)");
      } else {
        p_file.write("off)");
      }
      p_file.newLine();
      p_file.write("(preferred_direction ");
      if (p_settings.getPreferredDirectionIsHorizontal(i)) {
        p_file.write("horizontal)");
      } else {
        p_file.write("vertical)");
      }
      p_file.newLine();
      p_file.write("(preferred_direction_trace_costs ");
      float traceCosts = (float) p_settings.getPreferredDirectionTraceCosts(i);
      p_file.write(String.valueOf(traceCosts));
      p_file.write(")");
      p_file.newLine();
      p_file.write("(against_preferred_direction_trace_costs ");
      traceCosts = (float) p_settings.getAgainstPreferredDirectionTraceCosts(i);
      p_file.write(String.valueOf(traceCosts));
      p_file.write(")");
      p_file.endScope();
    }
    p_file.endScope();
  }
}
