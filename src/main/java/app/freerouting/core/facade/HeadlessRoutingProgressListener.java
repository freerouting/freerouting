package app.freerouting.core.facade;

/** Functional callback interface for receiving progress updates during headless routing. */
@FunctionalInterface
public interface HeadlessRoutingProgressListener {

  /**
   * Invoked when routing progress occurs.
   *
   * @param progress the current progress snapshot
   */
  void onProgress(HeadlessRoutingProgress progress);
}
