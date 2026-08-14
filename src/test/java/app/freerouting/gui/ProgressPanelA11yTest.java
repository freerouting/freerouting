package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import java.awt.Component;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Component-only accessibility coverage for the reusable progress surface. */
@Tag("gui")
class ProgressPanelA11yTest {

  private static final ProgressLabels ENGLISH_LABELS =
      new ProgressLabels("Routing progress", "Status", "Phase", "Counters", "Progress", "Cancel");

  private static final ProgressLabels HUNGARIAN_LABELS =
      new ProgressLabels(
          "Útvonaltervezés állapota", "Állapot", "Fázis", "Számlálók", "Folyamat", "Mégse");

  @Test
  void englishProgressResolvesByLocatorAndUpdatesDeterminateAndIndeterminateState() {
    ProgressPanel panel = GuiA11yHarness.onEdt(() -> new ProgressPanel(ENGLISH_LABELS, () -> {}));

    GuiA11yHarness.onEdt(
        () -> {
          Component root = GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_ROOT);
          GuiA11yHarness.requireAccessibleName(root, GuiLocators.PROGRESS_ROOT);
          GuiA11yHarness.requireAccessibleName(
              GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_STATUS),
              GuiLocators.PROGRESS_STATUS);
          GuiA11yHarness.requireAccessibleName(
              GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_PHASE),
              GuiLocators.PROGRESS_PHASE);
          GuiA11yHarness.requireAccessibleName(
              GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_COUNTERS),
              GuiLocators.PROGRESS_COUNTERS);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_BAR),
              GuiLocators.PROGRESS_BAR,
              AccessibleRole.PROGRESS_BAR);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_CANCEL),
              GuiLocators.PROGRESS_CANCEL,
              AccessibleRole.PUSH_BUTTON);
          GuiA11yHarness.requireUniqueSiblingNames(panel);
        });

    panel.update(new ProgressSnapshot("Routing", "Pass 2", 2, 5, false, true));
    GuiA11yHarness.onEdt(
        () -> {
          assertEquals("Routing", panel.getStatusLabel().getText());
          assertEquals("Pass 2", panel.getPhaseLabel().getText());
          assertEquals("2 / 5", panel.getCountersLabel().getText());
          assertEquals(40, panel.getProgressBar().getValue());
          assertFalse(panel.getProgressBar().isIndeterminate());
          assertTrue(
              GuiA11yHarness.accessibleName(panel.getProgressBar()).contains("40%"),
              "determinate percentage should be exposed to accessibility clients");
        });

    panel.update(new ProgressSnapshot("Routing", "Preparing", 0, 0, true, true));
    GuiA11yHarness.onEdt(
        () -> {
          assertTrue(panel.getProgressBar().isIndeterminate());
          assertEquals("0 / 0", panel.getCountersLabel().getText());
          assertTrue(
              GuiA11yHarness.accessibleName(panel.getProgressBar()).contains("Progress"),
              "indeterminate progress should retain its translated accessible label");
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void hungarianProgressUsesTranslatedAccessibleNamesAndInvokesCancelOnEdt() {
    AtomicBoolean cancelCalled = new AtomicBoolean();
    AtomicBoolean cancelCalledOnEdt = new AtomicBoolean();
    ProgressPanel panel =
        GuiA11yHarness.onEdt(
            () ->
                new ProgressPanel(
                    HUNGARIAN_LABELS,
                    () -> {
                      cancelCalled.set(true);
                      cancelCalledOnEdt.set(EventQueue.isDispatchThread());
                    }));

    GuiA11yHarness.onEdt(
        () -> {
          assertEquals(HUNGARIAN_LABELS.rootName(), GuiA11yHarness.accessibleName(panel));
          assertTrue(GuiA11yHarness.accessibleName(panel.getStatusLabel()).startsWith("Állapot:"));
          assertTrue(GuiA11yHarness.accessibleName(panel.getCancelButton()).equals("Mégse"));
          GuiA11yHarness.invoke(
              GuiA11yHarness.findByLocator(panel, GuiLocators.PROGRESS_CANCEL),
              GuiLocators.PROGRESS_CANCEL);
        });

    assertTrue(cancelCalled.get(), "cancel callback should be invoked");
    assertTrue(cancelCalledOnEdt.get(), "cancel callback should run on the EDT");

    Thread worker =
        new Thread(
            () -> panel.update(new ProgressSnapshot("Fut", "1. fázis", 3, 4, false, false)),
            "progress-test-worker");
    worker.start();
    try {
      worker.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for the progress worker", e);
    }

    GuiA11yHarness.onEdt(
        () -> {
          assertEquals("3 / 4", panel.getCountersLabel().getText());
          assertEquals(75, panel.getProgressBar().getValue());
          JButton cancel = panel.getCancelButton();
          GuiA11yHarness.requireDisabled(cancel, GuiLocators.PROGRESS_CANCEL);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }
}
