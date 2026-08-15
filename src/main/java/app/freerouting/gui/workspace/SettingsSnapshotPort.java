package app.freerouting.gui.workspace;

/** Session-owned port for detached worker settings. */
public interface SettingsSnapshotPort {

  /** Captures a deep, detached settings snapshot. */
  RouterSettingsSnapshot settingsSnapshot();
}
