package app.freerouting.gui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GuiSessionPortTest {

  @Test
  void settingsSnapshotIsDetachedAndDeep() {
    RouterSettings settings = new RouterSettings();
    settings.maxPasses = 7;
    settings.optimizer.enabled = true;
    RouterSettingsSnapshot snapshot = new RouterSettingsSnapshot(settings);

    settings.maxPasses = 11;
    settings.optimizer.enabled = false;

    RouterSettings copy = snapshot.copy();
    assertEquals(7, copy.maxPasses);
    assertTrue(copy.optimizer.enabled);
    assertNotSame(settings.optimizer, copy.optimizer);
  }

  @Test
  void loadGenerationRejectsStaleCallbacks() {
    GuiSessionPortAdapter port = new GuiSessionPortAdapter(() -> null, () -> null, Runnable::run);

    LoadGeneration first = port.beginBoardLoad();
    LoadGeneration second = port.beginBoardLoad();

    assertFalse(port.isCurrent(first));
    assertNotSame(first, second);
    assertTrue(port.isCurrent(second));
  }

  @Test
  void injectedEdtExecutorPreservesRouteEventOrdering() {
    List<Runnable> queued = new ArrayList<>();
    GuiSessionPortAdapter port = new GuiSessionPortAdapter(() -> null, () -> null, queued::add);
    RoutingJob job = new RoutingJob();

    RunGeneration generation = port.beginRoute(job);
    port.publishProgress(RouteProgress.status(generation, "started"));
    port.publishProgress(RouteProgress.status(generation, "finished"));

    assertEquals(3, queued.size());
    queued.forEach(Runnable::run);
  }

  @Test
  void screenMessagesRejectsOffEdtMutation() throws Exception {
    JLabel status = new JLabel();
    ScreenMessages messages =
        new ScreenMessages(
            new JLabel(),
            new JLabel(),
            status,
            new JLabel(),
            new JLabel(),
            new JLabel(),
            new JLabel(),
            new JLabel(),
            Locale.ENGLISH);

    assertThrows(IllegalStateException.class, () -> messages.setStatusMessage("worker"));
    SwingUtilities.invokeAndWait(
        () -> {
          messages.setStatusMessage("EDT");
          assertEquals("EDT", status.getText());
        });
  }
}
