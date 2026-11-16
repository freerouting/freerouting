package app.freerouting.io.specctra.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds the copper areas of the {@code (wiring ...)} scope before the board is built.
 *
 * <p>The layer types are decided in the {@code (structure ...)} scope, which comes before the
 * wiring. Some exporters (KiCad since 2026-07) write the copper of a zone as {@code (wire (polygon
 * <layer> ...) (net <name>))} in the wiring, even on a {@code (type power)} layer, instead of a
 * {@code (plane ...)} scope in the structure. Without looking ahead, such a layer looks empty when
 * the structure is read, and is turned into a routable signal layer.
 */
public final class PolygonWireScan {

  private PolygonWireScan() {}

  /**
   * Returns, for every layer that receives at least one {@code (wire (polygon ...))}, the names of
   * the nets of those wires, in file order. Wires without a net are counted with no net name.
   * Returns an empty map if the file cannot be scanned.
   */
  public static Map<String, Set<String>> netsByLayer(InputStream dsn) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    SpecctraDsnStreamReader scanner = new SpecctraDsnStreamReader(dsn);
    int depth = 0;
    int wireDepth = -1;
    String wireLayer = null;
    String wireNet = null;
    Object prevToken = null;
    try {
      for (; ; ) {
        Object token = scanner.nextToken();
        if (token == null) {
          break;
        }
        if (token == Keyword.OPEN_BRACKET) {
          ++depth;
        } else if (token == Keyword.CLOSED_BRACKET) {
          if (depth == wireDepth) {
            if (wireLayer != null) {
              Set<String> nets = result.computeIfAbsent(wireLayer, k -> new LinkedHashSet<>());
              if (wireNet != null) {
                nets.add(wireNet);
              }
            }
            wireDepth = -1;
            wireLayer = null;
            wireNet = null;
          }
          --depth;
        } else if (prevToken == Keyword.OPEN_BRACKET) {
          if (token == Keyword.WIRE && wireDepth < 0) {
            wireDepth = depth;
          } else if (wireDepth >= 0 && depth == wireDepth + 1) {
            if (token == Keyword.POLYGON) {
              Object layerToken = scanner.nextToken();
              if (layerToken instanceof String layerName) {
                wireLayer = layerName;
              }
            } else if (token == Keyword.NET) {
              wireNet = scanner.nextString();
            }
          }
        }
        prevToken = token;
      }
    } catch (IOException | RuntimeException e) {
      return Collections.emptyMap();
    }
    return result;
  }
}
