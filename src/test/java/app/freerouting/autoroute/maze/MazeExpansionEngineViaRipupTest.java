package app.freerouting.autoroute.maze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.autoroute.drill.ExpansionDrill;
import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Via;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MazeExpansionEngineViaRipupTest {

  private static final String DSN =
      """
      (pcb viatest (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 7000 6000)) (via V) (rule (width 150) (clearance 150)))
        (placement
          (component CA (place A 5000 5000 front 0)) (component CB (place B 5095 4751 front 0)))
        (library
          (image CA (pin PA 1 0 0)) (image CB (pin PB 1 0 0))
          (padstack PA (shape (rect top -200 -300 200 300)) (attach off))
          (padstack PB (shape (rect bottom -300 -200 300 200)) (attach off))
          (padstack V (shape (circle top 700)) (shape (circle bottom 700)) (attach off)))
        (network (net N (pins A-1 B-1))))
      """;

  private static RoutingBoard readBoard(String dsn) {
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void expandToOtherLayersDoesNotRipViaOfKeptAwayNet() {
    RoutingBoard board = readBoard(DSN);
    RouterSettings settings = new DefaultSettings().getSettings();
    AutorouteControl ctrl = new AutorouteControl(board, 1, settings);
    ctrl.ripupAllowed = true;
    ctrl.netsNotToRip = Set.of(5);

    AutorouteEngine autorouteEngine = new AutorouteEngine(board, 1, false);
    MazeSearchEngine search = new MazeSearchEngine(autorouteEngine, ctrl);

    MazeExpansionEngine engine = new MazeExpansionEngine(search);

    IntBox shape = new IntBox(0, 0, 100, 100);
    ExpansionDrill drill = new ExpansionDrill(shape, new IntPoint(0, 0), 0, 1);
    ObstacleExpansionRoom room = mock(ObstacleExpansionRoom.class);
    Via obstacleVia = mock(Via.class);
    when(obstacleVia.netCount()).thenReturn(1);
    when(obstacleVia.getNetNumber(0)).thenReturn(5);
    when(room.getItem()).thenReturn(obstacleVia);
    drill.roomArr[0] = room;

    MazeListElement listElement =
        new MazeListElement(drill, 0, null, 0, 0.0, 1.0, null, null, false, null, false);

    engine.expandToOtherLayers(listElement);

    // Because net 5 is in netsNotToRip, expandToOtherLayers returns early
    // and drill's target layer element is not occupied or ripped
    assertFalse(drill.getMazeSearchElement(1).isOccupied);
    assertFalse(drill.getMazeSearchElement(1).roomRipped);
  }
}
