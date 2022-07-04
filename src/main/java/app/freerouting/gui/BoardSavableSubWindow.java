package app.freerouting.gui;

import app.freerouting.logger.FRLogger;

/** Subwindow of the board frame, whose location and visibility can be saved and read from disc. */
public abstract class BoardSavableSubWindow extends BoardSubWindow {

  /** Reads the data of this frame from disc. Returns false, if the reading failed. */
  public boolean read(java.io.ObjectInputStream p_object_stream) {
    try {
      SavedAttributes saved_attributes = (SavedAttributes) p_object_stream.readObject();
      this.setBounds(saved_attributes.bounds);
      this.setVisible(saved_attributes.is_visible);
      return true;
    } catch (Exception e) {
      FRLogger.error("SelectParameterWindow.read: read failed", e);
      return false;
    }
  }

  /** Saves this frame to disk. */
  public void save(java.io.ObjectOutputStream p_object_stream) {
    SavedAttributes saved_attributes = new SavedAttributes(this.getBounds(), this.isVisible());

    try {
      p_object_stream.writeObject(saved_attributes);
    } catch (java.io.IOException e) {
      FRLogger.error("BoardSubWindow.save: save failed", e);
    }
  }

  /** Refreshs the displayed values in this window. To be overwritten in derived classes. */
  public void refresh() {}

  /** Type for attributes of this class, which are saved to an Objectstream. */
  private static class SavedAttributes implements java.io.Serializable {
    public final java.awt.Rectangle bounds;
    public final boolean is_visible;
    public SavedAttributes(java.awt.Rectangle p_bounds, boolean p_is_visible) {
      bounds = p_bounds;
      is_visible = p_is_visible;
    }
  }
}
