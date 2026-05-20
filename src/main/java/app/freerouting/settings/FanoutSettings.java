package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Configuration for the SMD-pin fanout pre-pass that runs before the main
 * batch autorouter.  During fanout each single-layer (SMD) pin is given a
 * short escape trace and a via so that the main router can work pin-to-via
 * rather than pin-to-pin, which dramatically improves routing quality for
 * dense SMD boards.
 *
 * <p>All fields follow the {@link RouterSettings} nullable-field contract: a
 * {@code null} value means "this source has no opinion" and is skipped by
 * {@code SettingsMerger}. Hardcoded defaults live exclusively in
 * {@link app.freerouting.settings.sources.DefaultSettings}.
 */
public class FanoutSettings implements Serializable, Cloneable {

  /**
   * Whether to run the fanout pre-pass at all.
   * When {@code false} the router skips fanout and begins the standard
   * autoroute phase immediately.
   */
  @SerializedName("enabled")
  public Boolean enabled;

  /**
   * Maximum number of fanout passes.  Each pass iterates over all SMD pins
   * once; passes continue until every pin is escaped <em>or</em> this limit
   * is reached.  Typical boards converge in 1–3 passes; the limit exists only
   * as a safety cap.
   */
  @SerializedName("max_passes")
  public Integer maxPasses;

  /**
   * Base time budget (in milliseconds) that each individual SMD pin may
   * consume in pass 1.  The budget scales linearly with the pass number so
   * that later, harder passes are given proportionally more time:
   * {@code effectiveLimit = maxMillisecondsPerPin * passNumber}.
   *
   * <p>Reducing this value speeds up fanout on simple boards at the cost of
   * fewer escape attempts per pin per pass.
   */
  @SerializedName("max_milliseconds_per_pin")
  public Long maxMillisecondsPerPin;

  /**
   * Whether the fanout router is allowed to rip up and re-route existing
   * traces in order to make room for a new escape via.  When {@code false}
   * fanout runs without any ripup, which is faster but may leave more pins
   * un-escaped on congested boards.
   */
  @SerializedName("ripup_allowed")
  public Boolean ripupAllowed;

  /**
   * No-arg constructor required for deserialisation and {@link #clone()}.
   */
  public FanoutSettings() {
  }

  /**
   * Creates a deep copy of this {@code FanoutSettings} object.
   * All fields are immutable wrappers or primitives, so a shallow clone is
   * sufficient.
   */
  @Override
  public FanoutSettings clone() {
    try {
      return (FanoutSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}