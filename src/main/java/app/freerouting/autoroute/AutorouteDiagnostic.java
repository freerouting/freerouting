package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.Objects;

/** A headless snapshot of geometry exposed for optional autorouter diagnostics. */
public record AutorouteDiagnostic(Kind kind, TileShape shape, int layer, double intensity) {

  /** The diagnostic geometry category used by GUI/debug adapters. */
  public enum Kind {
    FREE_SPACE_ROOM,
    OBSTACLE_ROOM,
    EXPANSION_DRILL
  }

  /** Receives diagnostic snapshots without introducing a GUI dependency into autoroute. */
  @FunctionalInterface
  public interface Sink {

    /** Accepts one diagnostic snapshot. */
    void accept(AutorouteDiagnostic diagnostic);
  }

  /** Creates a validated diagnostic snapshot. */
  public AutorouteDiagnostic {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(shape, "shape");
    if (intensity < 0) {
      throw new IllegalArgumentException("intensity must not be negative");
    }
  }
}
