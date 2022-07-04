package app.freerouting.autoroute;

import java.util.Collection;

public interface CompleteExpansionRoom extends ExpansionRoom {

  /** Returns the list of doors to target items of this room */
  Collection<TargetItemExpansionDoor> get_target_doors();

  /** Returns the object of tthis complete_expansion_rooom. */
  app.freerouting.board.SearchTreeObject get_object();

  /** Draws the shape of this room for test purposes */
  void draw(
      java.awt.Graphics p_graphics,
      app.freerouting.boardgraphics.GraphicsContext p_graphics_context,
      double p_intensity);
}
