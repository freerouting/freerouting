package app.freerouting.autoroute;

import app.freerouting.board.SearchTreeObject;
import app.freerouting.boardgraphics.GraphicsContext;
import java.awt.Graphics;
import java.util.Collection;

public interface CompleteExpansionRoom extends ExpansionRoom {

  /** Returns the list of doors to target items of this room */
  Collection<TargetItemExpansionDoor> getTargetDoors();

  /** Returns the object of this complete_expansion_room. */
  SearchTreeObject getObject();

  /** Draws the shape of this room for test purposes */
  void draw(Graphics pGraphics, GraphicsContext pGraphicsContext, double pIntensity);
}
