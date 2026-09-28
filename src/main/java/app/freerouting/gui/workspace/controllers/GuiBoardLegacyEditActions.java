package app.freerouting.gui.workspace.controllers;

import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.geom.Point2D;

/**
 * Preserves the legacy selected-item editing API while those operations remain disabled.
 *
 * <p>These methods are intentionally inert compatibility hooks. Keeping them together prevents
 * unsupported editing placeholders from obscuring active GUI behavior in {@link GuiBoardManager}.
 */
public final class GuiBoardLegacyEditActions {

  public void fixSelectedItems() {}

  public void unfixSelectedItems() {}

  public void assignSelectedToNewNet() {}

  public void assignSelectedToNewGroup() {}

  public void deleteSelectedItems() {}

  public void cutoutSelectedItems() {}

  public void assignClearanceClasssToSelectedItems(int clearanceClassIndex) {}

  public void moveSelectedItems(Point2D fromLocation) {}

  public void copySelectedItems(Point2D fromLocation) {}

  public void optimizeSelectedItems() {}

  public void autorouteSelectedItems() {}
}
