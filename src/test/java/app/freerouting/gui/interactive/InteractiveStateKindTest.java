package app.freerouting.gui.interactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import app.freerouting.gui.workspace.session.EditorStateKind;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the editor-state kind and selection flag reported by every interactive state. Each state
 * reports its own kind by overriding {@link InteractiveState#kind()}; subclasses inherit the kind
 * of their parent unless they override it.
 */
class InteractiveStateKindTest {

  static Stream<Arguments> states() {
    return Stream.of(
        Arguments.of(RouteState.class, EditorStateKind.ROUTE, false),
        Arguments.of(DynamicRouteState.class, EditorStateKind.ROUTE, false),
        Arguments.of(StitchRouteState.class, EditorStateKind.ROUTE, false),
        Arguments.of(MenuState.class, EditorStateKind.MENU, false),
        Arguments.of(RouteMenuState.class, EditorStateKind.MENU, false),
        Arguments.of(InspectMenuState.class, EditorStateKind.INSPECT, false),
        Arguments.of(DragMenuState.class, EditorStateKind.DRAG, false),
        Arguments.of(InspectedItemState.class, EditorStateKind.INSPECT, true),
        Arguments.of(DragState.class, EditorStateKind.DRAG, false),
        Arguments.of(DragItemState.class, EditorStateKind.DRAG, false),
        Arguments.of(MakeSpaceState.class, EditorStateKind.DRAG, false),
        Arguments.of(MoveItemState.class, EditorStateKind.DRAG, false),
        Arguments.of(ZoomRegionState.class, EditorStateKind.ZOOM, false),
        Arguments.of(ExpandTestState.class, EditorStateKind.EXPAND, false),
        Arguments.of(SelectRegionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(CutoutRouteState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(InspectItemsInRegionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(CircleConstructionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(CornerItemConstructionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(HoleConstructionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(PolygonShapeConstructionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(TileConstructionState.class, EditorStateKind.CONSTRUCT, false),
        Arguments.of(CopyItemState.class, EditorStateKind.UNKNOWN, false),
        Arguments.of(PinSwapState.class, EditorStateKind.UNKNOWN, false),
        Arguments.of(InteractiveState.class, EditorStateKind.UNKNOWN, false));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("states")
  void reportsExpectedKindAndSelection(
      Class<? extends InteractiveState> stateClass,
      EditorStateKind expectedKind,
      boolean expectedSelection) {
    InteractiveState state = mock(stateClass, CALLS_REAL_METHODS);

    assertEquals(expectedKind, state.kind());
    assertEquals(expectedSelection, state.hasSelection());
  }
}
