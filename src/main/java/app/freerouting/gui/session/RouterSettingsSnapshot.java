package app.freerouting.gui.session;

import app.freerouting.settings.RouterSettings;
import java.util.Objects;

/** Detached settings value captured for one background operation. */
public final class RouterSettingsSnapshot {

  private final RouterSettings settings;

  /** Captures a deep copy of the supplied settings. */
  public RouterSettingsSnapshot(RouterSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings").clone();
  }

  /** Returns another deep copy suitable for handing to a worker. */
  public RouterSettings copy() {
    return settings.clone();
  }
}
