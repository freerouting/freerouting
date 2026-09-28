package app.freerouting.gui.rendering;

import app.freerouting.autoroute.AutorouteDiagnostic;
import java.awt.Color;
import java.awt.Graphics;

/** GUI adapter for rendering headless autorouter diagnostic snapshots. */
public final class AutorouteDiagnosticRenderer {

  private AutorouteDiagnosticRenderer() {}

  /** Returns a diagnostic sink that renders snapshots into the supplied graphics target. */
  public static AutorouteDiagnostic.Sink createSink(
      Graphics graphics, GraphicsContext graphicsContext) {
    return diagnostic -> render(diagnostic, graphics, graphicsContext);
  }

  /** Renders one diagnostic snapshot using the supplied GUI palette and transforms. */
  public static void render(
      AutorouteDiagnostic diagnostic, Graphics graphics, GraphicsContext graphicsContext) {
    if (diagnostic == null || graphics == null || graphicsContext == null) {
      return;
    }
    Color color = colorFor(diagnostic.kind(), diagnostic.layer(), graphicsContext);
    double visibility =
        diagnostic.layer() >= 0 ? graphicsContext.getLayerVisibility(diagnostic.layer()) : 1.0;
    if (visibility <= 0) {
      return;
    }
    graphicsContext.fillArea(
        diagnostic.shape(), graphics, color, diagnostic.intensity() * visibility);
    graphicsContext.drawBoundary(
        diagnostic.shape(), 0, color, graphics, diagnostic.layer() >= 0 ? visibility : 1.0);
  }

  private static Color colorFor(
      AutorouteDiagnostic.Kind kind, int layer, GraphicsContext graphicsContext) {
    return switch (kind) {
      case FREE_SPACE_ROOM -> graphicsContext.getTraceColors(false)[layer];
      case OBSTACLE_ROOM -> Color.WHITE;
      case EXPANSION_DRILL -> graphicsContext.getHighlightColor();
    };
  }
}
