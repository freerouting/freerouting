package app.freerouting.autoroute;

import app.freerouting.board.SearchTreeObject;
import java.util.Collection;

/** Represents a complete expansion room containing target item doors and search tree objects. */
public interface CompleteExpansionRoom extends ExpansionRoom {

  /** Returns the list of doors to target items of this room. */
  Collection<TargetItemExpansionDoor> getTargetDoors();

  /** Returns the object of this complete_expansion_room. */
  SearchTreeObject getObject();

  /** Emits the shape of this room for an optional diagnostic consumer. */
  void emitDiagnostic(AutorouteDiagnostic.Sink sink, double intensity);
}
