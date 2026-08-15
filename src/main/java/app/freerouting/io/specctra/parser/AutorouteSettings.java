package app.freerouting.io.specctra.parser;

import app.freerouting.board.Layer;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;

@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public final class AutorouteSettings {

  private AutorouteSettings() {}

  static RouterSettings readScope(IJFlexScanner scanner, LayerStructure layerStructure) {
    RouterSettings result = new RouterSettings();
    result.setLayerCount(layerStructure.layers.length);
    boolean withAutoroute = true;
    boolean withPostroute = true;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("AutorouteSettings.read_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "AutorouteSettings.read_scope: unexpected end of file at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.FANOUT) {
          DsnFile.readOnOffScope(scanner);
        } else if (nextToken == Keyword.AUTOROUTE) {
          withAutoroute = DsnFile.readOnOffScope(scanner);
        } else if (nextToken == Keyword.POSTROUTE) {
          withPostroute = DsnFile.readOnOffScope(scanner);
        } else if (nextToken == Keyword.VIAS) {
          result.setViasAllowed(DsnFile.readOnOffScope(scanner));
        } else if (nextToken == Keyword.VIA_COSTS) {
          result.setViaCosts(DsnFile.readIntegerScope(scanner));
        } else if (nextToken == Keyword.PLANE_VIA_COSTS) {
          result.setPlaneViaCosts(DsnFile.readIntegerScope(scanner));
        } else if (nextToken == Keyword.START_RIPUP_COSTS) {
          result.setStartRipupCosts(DsnFile.readIntegerScope(scanner));
        } else if (nextToken == Keyword.LAYER_RULE) {
          result = readLayerRule(scanner, layerStructure, result);
          if (result == null) {
            return null;
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    result.setRunRouter(withAutoroute);
    result.setRunOptimizer(withPostroute);
    return result;
  }

  static RouterSettings readLayerRule(
      IJFlexScanner scanner, LayerStructure layerStructure, RouterSettings settings) {
    scanner.yybegin(SpecctraDsnStreamReader.NAME);
    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
      return null;
    }
    if (!(nextToken instanceof String)) {
      FRLogger.warn(
          "AutorouteSettings.read_layer_rule: String expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    int layerIndex = layerStructure.getNo((String) nextToken);
    if (layerIndex < 0) {
      FRLogger.warn(
          "AutorouteSettings.read_layer_rule: layer not found at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "AutorouteSettings.read_layer_rule: unexpected end of file at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.ACTIVE) {
          settings.setLayerActive(layerIndex, DsnFile.readOnOffScope(scanner));
        } else if (nextToken == Keyword.PREFERRED_DIRECTION) {
          try {
            boolean prefDirIsHorizontal = true;
            nextToken = scanner.nextToken();
            if (nextToken == Keyword.VERTICAL) {
              prefDirIsHorizontal = false;
            } else if (nextToken != Keyword.HORIZONTAL) {
              FRLogger.warn(
                  "AutorouteSettings.read_layer_rule: unexpected key word at '"
                      + scanner.getScopeIdentifier()
                      + "'");
              return null;
            }
            settings.setPreferredDirectionIsHorizontal(layerIndex, prefDirIsHorizontal);
            nextToken = scanner.nextToken();
            if (nextToken != Keyword.CLOSED_BRACKET) {
              FRLogger.warn(
                  "AutorouteSettings.read_layer_rule: closing bracket expected at '"
                      + scanner.getScopeIdentifier()
                      + "'");
              return null;
            }
          } catch (IOException e) {
            FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
            return null;
          }
        } else if (nextToken == Keyword.PREFERRED_DIRECTION_TRACE_COSTS) {
          settings.setPreferredDirectionTraceCosts(layerIndex, DsnFile.readFloatScope(scanner));
        } else if (nextToken == Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS) {
          settings.setAgainstPreferredDirectionTraceCosts(
              layerIndex, DsnFile.readFloatScope(scanner));
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    return settings;
  }

  public static void writeScope(
      IndentFileWriter file,
      RouterSettings settings,
      app.freerouting.board.LayerStructure layerStructure,
      IdentifierType identifierType)
      throws IOException {
    file.startScope();
    file.write("autorouteSettings");
    file.newLine();
    file.write("(autoroute ");
    if (settings.getRunRouter()) {
      file.write("on)");
    } else {
      file.write("off)");
    }
    file.newLine();
    file.write("(postroute ");
    if (settings.getRunOptimizer()) {
      file.write("on)");
    } else {
      file.write("off)");
    }
    file.newLine();
    file.write("(vias ");
    if (settings.getViasAllowed()) {
      file.write("on)");
    } else {
      file.write("off)");
    }
    file.newLine();
    file.write("(viaCosts ");
    {
      int viaCosts = settings.getViaCosts();
      file.write(String.valueOf(viaCosts));
    }
    file.write(")");
    file.newLine();
    file.write("(plane_via_costs ");
    {
      int viaCosts = settings.getPlaneViaCosts();
      file.write(String.valueOf(viaCosts));
    }
    file.write(")");
    file.newLine();
    file.write("(startRipupCosts ");
    {
      int ripupCosts = settings.getStartRipupCosts();
      file.write(String.valueOf(ripupCosts));
    }
    file.write(")");
    file.newLine();
    for (int i = 0; i < layerStructure.layers.length; i++) {
      final Layer currentLayer = layerStructure.layers[i];
      file.startScope();
      file.write("layer_rule ");
      identifierType.write(currentLayer.name, file);
      file.newLine();
      file.write("(active ");
      if (settings.getLayerActive(i)) {
        file.write("on)");
      } else {
        file.write("off)");
      }
      file.newLine();
      file.write("(preferred_direction ");
      if (settings.getPreferredDirectionIsHorizontal(i)) {
        file.write("horizontal)");
      } else {
        file.write("vertical)");
      }
      file.newLine();
      file.write("(preferred_direction_trace_costs ");
      float traceCosts = (float) settings.getPreferredDirectionTraceCosts(i);
      file.write(String.valueOf(traceCosts));
      file.write(")");
      file.newLine();
      file.write("(against_preferred_direction_trace_costs ");
      traceCosts = (float) settings.getAgainstPreferredDirectionTraceCosts(i);
      file.write(String.valueOf(traceCosts));
      file.write(")");
      file.endScope();
    }
    file.endScope();
  }
}
