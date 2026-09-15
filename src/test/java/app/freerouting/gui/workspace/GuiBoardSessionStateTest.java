package app.freerouting.gui.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.gui.workspace.session.GuiBoardSessionState;
import org.junit.jupiter.api.Test;

/** Verifies the isolated batch configuration state used by the GUI board façade. */
class GuiBoardSessionStateTest {

  @Test
  void storesBatchOptionsWithoutOwningBoardState() {
    GuiBoardSessionState state = new GuiBoardSessionState(null, null);

    state.setBoardUpdateStrategy(BoardUpdateStrategy.GLOBAL_OPTIMAL);
    state.setItemSelectionStrategy(ItemSelectionStrategy.PRIORITIZED);
    state.setNumThreads(4);

    assertEquals(BoardUpdateStrategy.GLOBAL_OPTIMAL, state.getBoardUpdateStrategy());
    assertEquals(ItemSelectionStrategy.PRIORITIZED, state.getItemSelectionStrategy());
  }
}
