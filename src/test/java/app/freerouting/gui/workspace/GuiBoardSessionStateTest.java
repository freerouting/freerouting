package app.freerouting.gui.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import org.junit.jupiter.api.Test;

/** Verifies the isolated batch configuration state used by the GUI board façade. */
class GuiBoardSessionStateTest {

  @Test
  void storesBatchOptionsWithoutOwningBoardState() {
    GuiBoardSessionState state = new GuiBoardSessionState(null, null);

    state.setBoardUpdateStrategy(BoardUpdateStrategy.HYBRID);
    state.setHybridRatio("3:1");
    state.setItemSelectionStrategy(ItemSelectionStrategy.PRIORITIZED);
    state.setNumThreads(4);

    assertEquals(BoardUpdateStrategy.HYBRID, state.getBoardUpdateStrategy());
    assertEquals("3:1", state.getHybridRatio());
    assertEquals(ItemSelectionStrategy.PRIORITIZED, state.getItemSelectionStrategy());
  }
}
