package app.freerouting.gui.workspace.ports;

import app.freerouting.gui.workspace.progress.RouterSettingsSnapshot;

/** Session-owned port for detached worker settings. */
public interface SettingsSnapshotPort {

  /** Captures a deep, detached settings snapshot. */
  public RouterSettingsSnapshot settingsSnapshot();
}
