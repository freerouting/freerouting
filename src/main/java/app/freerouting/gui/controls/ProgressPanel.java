package app.freerouting.gui.controls;

import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.support.ProgressSnapshot;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

/**
 * Reusable, top-level-window-free progress surface.
 *
 * <p>The panel owns only Swing state. The caller supplies translated labels, immutable snapshots,
 * and a cancellation callback, so it can be embedded in any GUI workflow without coupling it to a
 * routing job or a particular window.
 */
public final class ProgressPanel extends JPanel {

  private final ProgressLabels labels;
  private final JLabel statusLabel;
  private final JLabel phaseLabel;
  private final JLabel countersLabel;
  private final JProgressBar progressBar;
  private final JButton cancelButton;
  private final Runnable cancelAction;
  private volatile ProgressSnapshot snapshot;

  /** Creates a progress surface with an initially empty, determinate snapshot. */
  public ProgressPanel(ProgressLabels labels, Runnable cancelAction) {
    super(new BorderLayout(8, 8));
    this.labels = Objects.requireNonNull(labels, "labels");
    this.cancelAction = cancelAction == null ? () -> {} : cancelAction;
    this.snapshot = new ProgressSnapshot("", "", 0, 0, false, true);

    A11y.tag(this, GuiLocators.PROGRESS_ROOT);
    A11y.describe(this, labels.rootName(), null);

    final JPanel content = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = GridBagConstraints.RELATIVE;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.weightx = 1.0;
    constraints.insets = new Insets(2, 4, 2, 4);

    statusLabel = new JLabel();
    statusLabel.setHorizontalAlignment(SwingConstants.LEADING);
    A11y.tag(statusLabel, GuiLocators.PROGRESS_STATUS);
    content.add(statusLabel, constraints);

    phaseLabel = new JLabel();
    phaseLabel.setHorizontalAlignment(SwingConstants.LEADING);
    A11y.tag(phaseLabel, GuiLocators.PROGRESS_PHASE);
    content.add(phaseLabel, constraints);

    countersLabel = new JLabel();
    countersLabel.setHorizontalAlignment(SwingConstants.LEADING);
    A11y.tag(countersLabel, GuiLocators.PROGRESS_COUNTERS);
    content.add(countersLabel, constraints);

    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    A11y.tag(progressBar, GuiLocators.PROGRESS_BAR);
    content.add(progressBar, constraints);

    cancelButton = new JButton(labels.cancelText());
    A11y.tag(cancelButton, GuiLocators.PROGRESS_CANCEL);
    A11y.describe(cancelButton, labels.cancelText(), null);
    cancelButton.addActionListener(_ -> this.cancelAction.run());

    add(content, BorderLayout.CENTER);
    add(cancelButton, BorderLayout.SOUTH);
    applySnapshot(this.snapshot);
  }

  /**
   * Applies a snapshot on the EDT.
   *
   * <p>Calls from worker threads are synchronously marshalled to the EDT so that a subsequent
   * {@link #getSnapshot()} observes the same state that is visible in the component.
   */
  public void update(ProgressSnapshot nextSnapshot) {
    Objects.requireNonNull(nextSnapshot, "nextSnapshot");
    if (EventQueue.isDispatchThread()) {
      applySnapshot(nextSnapshot);
      return;
    }
    try {
      EventQueue.invokeAndWait(() -> applySnapshot(nextSnapshot));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while updating progress on the EDT", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException("Progress update failed on the EDT", e.getCause());
    }
  }

  /** Returns the last snapshot applied to this panel. */
  public ProgressSnapshot getSnapshot() {
    return snapshot;
  }

  /** Returns the status label for accessibility clients and component-only tests. */
  public JLabel getStatusLabel() {
    return statusLabel;
  }

  /** Returns the phase label for accessibility clients and component-only tests. */
  public JLabel getPhaseLabel() {
    return phaseLabel;
  }

  /** Returns the counter label for accessibility clients and component-only tests. */
  public JLabel getCountersLabel() {
    return countersLabel;
  }

  /** Returns the progress bar for accessibility clients and component-only tests. */
  public JProgressBar getProgressBar() {
    return progressBar;
  }

  /** Returns the cancellation button for accessibility clients and component-only tests. */
  public JButton getCancelButton() {
    return cancelButton;
  }

  private void applySnapshot(ProgressSnapshot nextSnapshot) {
    if (!EventQueue.isDispatchThread()) {
      throw new IllegalStateException("Progress state must be applied on the EDT");
    }
    snapshot = nextSnapshot;
    statusLabel.setText(nextSnapshot.status());
    phaseLabel.setText(nextSnapshot.phase());
    countersLabel.setText(nextSnapshot.completed() + " / " + nextSnapshot.total());

    boolean indeterminate = nextSnapshot.indeterminate();
    progressBar.setIndeterminate(indeterminate);
    if (!indeterminate) {
      progressBar.setValue(toPercent(nextSnapshot.completed(), nextSnapshot.total()));
      progressBar.setString(nextSnapshot.total() == 0 ? "0%" : progressBar.getValue() + "%");
    } else {
      progressBar.setString(null);
    }
    cancelButton.setEnabled(nextSnapshot.cancelEnabled());

    A11y.describe(statusLabel, labels.statusName() + ": " + nextSnapshot.status(), null);
    A11y.describe(phaseLabel, labels.phaseName() + ": " + nextSnapshot.phase(), null);
    A11y.describe(
        countersLabel,
        labels.countersName() + ": " + nextSnapshot.completed() + " / " + nextSnapshot.total(),
        null);
    A11y.describe(
        progressBar,
        labels.progressName()
            + ": "
            + (indeterminate ? labels.progressName() : progressBar.getString()),
        null);
  }

  private static int toPercent(long completed, long total) {
    if (total == 0) {
      return 0;
    }
    long boundedCompleted = Math.min(completed, total);
    return (int) Math.min(100, (boundedCompleted * 100L) / total);
  }
}
