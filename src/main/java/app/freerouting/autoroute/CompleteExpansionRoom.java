package app.freerouting.autoroute;

import app.freerouting.board.SearchTreeObject;
import app.freerouting.boardgraphics.GraphicsContext;

import java.awt.Graphics;
import java.util.Collection;

public interface CompleteExpansionRoom extends ExpansionRoom {

  /** Returns the list of doors to target items of this room */
  Collection<TargetItemExpansionDoor> get_target_doors();

  /** Returns the object of this complete_expansion_room. */
  SearchTreeObject get_object();

  /** Draws the shape of this room for test purposes */
  void draw(
      Graphics p_graphics,
      GraphicsContext p_graphics_context,
      double p_intensity);
}
