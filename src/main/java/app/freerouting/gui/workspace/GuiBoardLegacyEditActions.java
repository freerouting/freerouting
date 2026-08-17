package app.freerouting.gui.workspace;

import java.awt.geom.Point2D;

/**
 * Preserves the legacy selected-item editing API while those operations remain disabled.
 *
 * <p>These methods are intentionally inert compatibility hooks. Keeping them together prevents
 * unsupported editing placeholders from obscuring active GUI behavior in {@link GuiBoardManager}.
 */
final class GuiBoardLegacyEditActions {

  void fixSelectedItems() {}

  void unfixSelectedItems() {}

  void assignSelectedToNewNet() {}

  void assignSelectedToNewGroup() {}

  void deleteSelectedItems() {}

  void cutoutSelectedItems() {}

  void assignClearanceClasssToSelectedItems(int clearanceClassIndex) {}

  void moveSelectedItems(Point2D fromLocation) {}

  void copySelectedItems(Point2D fromLocation) {}

  void optimizeSelectedItems() {}

  void autorouteSelectedItems() {}
}
