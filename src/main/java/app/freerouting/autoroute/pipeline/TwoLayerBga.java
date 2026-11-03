package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Erik Gavriluk (freerouting.net) (router.fanout.two_layer_bga): how the main attempt starts on a
 * board with exactly two signal layers and a ball grid array. On two layers every escape via closes
 * both layers, and the eager fanout drills one beside each ball first: on decelerator4030 (two 0.75
 * mm BGAs, 0.1 mm traces) its first pass drilled 417 vias and the board stalled at 20 open at the
 * Tier C cap, where without the fanout it ends at 2 and direct-first at 6; the designer escapes the
 * balls row by row on the top layer with almost no vias.
 */
public final class TwoLayerBga {

  public static final Set<String> MODES = Set.of("off", "no_fanout", "direct_first");

  private TwoLayerBga() {}

  /** The setting's value, checked. */
  public static String mode(RouterSettings settings) {
    String mode =
        (settings.fanout != null && settings.fanout.twoLayerBga != null)
            ? settings.fanout.twoLayerBga
            : "off";
    if (!MODES.contains(mode)) {
      throw new IllegalArgumentException(
          "router.fanout.two_layer_bga must be off, no_fanout or direct_first");
    }
    return mode;
  }

  /**
   * Whether a part's pins are a ball grid: an area array (BallFields.fieldOf: at least 16
   * single-layer pins on one layer filling at least half of a grid of three or more rows and
   * columns) whose pads are round, all but a quarter at most. A QFN's or QFP's perimeter rows and
   * an LGA's or connector's rectangular pads are not.
   */
  public static boolean isBallGrid(Collection<Pin> pins) {
    if (pins == null) {
      return false;
    }
    BallFields.Field field = BallFields.fieldOf(pins);
    if (field == null) {
      return false;
    }
    int round = 0;
    for (Pin p : pins) {
      if (p.getPadstack() != null && p.getPadstack().getShape(field.layer()) instanceof Circle) {
        round++;
      }
    }
    return round >= BallFields.MIN_BALLS && round * 4 >= pins.size() * 3;
  }

  /** The names of the board's ball grid parts. */
  public static List<String> ballGridParts(BasicBoard board) {
    List<String> parts = new ArrayList<>();
    for (int id = 1; id <= board.components.count(); id++) {
      if (isBallGrid(board.getComponentPins(id))) {
        String name =
            board.components.get(id) != null ? board.components.get(id).name : Integer.toString(id);
        parts.add(name);
      }
    }
    return parts;
  }

  /**
   * Sets the main attempt's fanout for board as the setting says, in settings: the fanout off
   * (no_fanout) or direct-first (direct_first) where the board has exactly two signal layers, at
   * least one ball grid part and the default fanout (enabled, eager). Returns the line to log, or
   * null when nothing changed.
   */
  public static String apply(RouterSettings settings, BasicBoard board) {
    String mode = mode(settings);
    if ("off".equals(mode)
        || settings.fanout == null
        || settings.fanout.enabled != Boolean.TRUE
        || !"eager".equals(settings.fanout.strategy != null ? settings.fanout.strategy : "eager")
        || board.layerStructure.signalLayerCount() != 2) {
      return null;
    }
    List<String> parts = ballGridParts(board);
    if (parts.isEmpty()) {
      return null;
    }
    if ("no_fanout".equals(mode)) {
      settings.fanout.enabled = false;
    } else {
      settings.fanout.strategy = "direct_first";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Two signal layers and a ball grid array (");
    for (int i = 0; i < Math.min(5, parts.size()); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(parts.get(i));
    }
    if (parts.size() > 5) {
      sb.append(", ...");
    }
    sb.append("): the main attempt starts ");
    sb.append("no_fanout".equals(mode) ? "without the fanout" : "with the direct-first fanout");
    sb.append(" (router.fanout.two_layer_bga=").append(mode).append(").");
    return sb.toString();
  }
}
