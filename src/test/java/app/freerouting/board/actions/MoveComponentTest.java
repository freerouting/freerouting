package app.freerouting.board.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MoveComponentTest {

  private RoutingBoard board;

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    Communication communication = new Communication();
    return new RoutingBoard(
        new IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
        0,
        boardRules,
        communication);
  }

  @BeforeEach
  void setUp() {
    board = spy(createTestBoard());
  }

  @Test
  void getMinDrillItemWidthWithTracesReturnsNegativeWhenNoConnectedTraces() {
    Component comp =
        board.components.add("U1", new IntPoint(0, 0), 0, true, null, null, false, "ESP32");
    DrillItem pin1 = mock(DrillItem.class);
    pin1.board = board;
    when(pin1.getComponentId()).thenReturn(comp.id);
    when(pin1.getCenter()).thenReturn(new IntPoint(0, 0));
    when(pin1.minWidth()).thenReturn(200.0);
    when(pin1.getNormalContacts()).thenReturn(Set.of());

    doReturn(List.of(pin1)).when(board).getComponentItems(comp.id);

    MoveComponent moveComponent = new MoveComponent(pin1, new IntVector(500, 0), 99, 5);

    assertEquals(-1.0, moveComponent.getMinDrillItemWidthWithTraces());
  }

  @Test
  void getMinDrillItemWidthWithTracesReturnsWidthWhenTraceConnected() {
    Component comp =
        board.components.add("U1", new IntPoint(0, 0), 0, true, null, null, false, "ESP32");
    DrillItem pin1 = mock(DrillItem.class);
    pin1.board = board;
    when(pin1.getComponentId()).thenReturn(comp.id);
    when(pin1.getCenter()).thenReturn(new IntPoint(0, 0));
    when(pin1.minWidth()).thenReturn(200.0);

    Trace connectedTrace = mock(Trace.class);
    when(pin1.getNormalContacts()).thenReturn(Set.of(connectedTrace));

    doReturn(List.of(pin1)).when(board).getComponentItems(comp.id);

    MoveComponent moveComponent = new MoveComponent(pin1, new IntVector(500, 0), 99, 5);

    assertEquals(200.0, moveComponent.getMinDrillItemWidthWithTraces());
  }

  @Test
  void checkAllowsMoveLargerThanMinWidthWhenNoConnectingTracesAndSpaceClear() {
    Component comp =
        board.components.add("U1", new IntPoint(0, 0), 0, true, null, null, false, "ESP32");
    DrillItem pin1 = mock(DrillItem.class);
    pin1.board = board;
    when(pin1.getComponentId()).thenReturn(comp.id);
    when(pin1.getCenter()).thenReturn(new IntPoint(0, 0));
    when(pin1.minWidth()).thenReturn(200.0);
    when(pin1.getNormalContacts()).thenReturn(Set.of());

    doReturn(List.of(pin1)).when(board).getComponentItems(comp.id);
    doReturn(true).when(board).checkMoveItem(eq(pin1), any(Vector.class), anyCollection());

    Vector largeVector = new IntVector(1000, 0); // 1000 >= minWidth (200)
    MoveComponent moveComponent = new MoveComponent(pin1, largeVector, 99, 5);

    assertTrue(moveComponent.check());
  }

  @Test
  void checkFailsMoveLargerThanMinWidthWhenConnectingTracePresent() {
    Component comp =
        board.components.add("U1", new IntPoint(0, 0), 0, true, null, null, false, "ESP32");
    DrillItem pin1 = mock(DrillItem.class);
    pin1.board = board;
    when(pin1.getComponentId()).thenReturn(comp.id);
    when(pin1.getCenter()).thenReturn(new IntPoint(0, 0));
    when(pin1.minWidth()).thenReturn(200.0);

    Trace connectedTrace = mock(Trace.class);
    when(pin1.getNormalContacts()).thenReturn(Set.of(connectedTrace));

    doReturn(List.of(pin1)).when(board).getComponentItems(comp.id);

    Vector largeVector = new IntVector(1000, 0); // 1000 >= minWidth (200)
    MoveComponent moveComponent = new MoveComponent(pin1, largeVector, 99, 5);

    assertFalse(moveComponent.check());
  }

  @Test
  void insertBypassesMoveDrillItemWhenNoConnectingTracesAndSpaceClear() {
    Component comp =
        board.components.add("U1", new IntPoint(0, 0), 0, true, null, null, false, "ESP32");
    DrillItem pin1 = mock(DrillItem.class);
    pin1.board = board;
    when(pin1.getComponentId()).thenReturn(comp.id);
    when(pin1.getCenter()).thenReturn(new IntPoint(0, 0));
    when(pin1.minWidth()).thenReturn(200.0);
    when(pin1.getNormalContacts()).thenReturn(Set.of());

    doReturn(List.of(pin1)).when(board).getComponentItems(comp.id);
    doReturn(true).when(board).checkMoveItem(eq(pin1), any(Vector.class), anyCollection());

    Vector vector = new IntVector(500, 0);
    MoveComponent moveComponent = new MoveComponent(pin1, vector, 99, 5);

    assertTrue(moveComponent.check());
    assertTrue(moveComponent.insert(100, 50));

    verify(pin1).moveBy(vector);
    verify(board, never())
        .moveDrillItem(
            any(DrillItem.class),
            any(Vector.class),
            anyInt(),
            anyInt(),
            anyInt(),
            anyInt(),
            anyInt());
  }
}
