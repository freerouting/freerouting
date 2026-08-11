package app.freerouting.gui;

/** Subwindows of the board frame. */
public class BoardSubWindow extends WindowBase {

  private boolean visibleBeforeIconifying;

  /** Creates a board subwindow with the default dimensions. */
  public BoardSubWindow() {
    super(300, 200);
  }

  /** Hides this subwindow while its parent frame is iconified. */
  public void parentIconified() {
    this.visibleBeforeIconifying = this.isVisible();
    this.setVisible(false);
  }

  /** Restores this subwindow after its parent frame is deiconified. */
  public void parentDeiconified() {
    this.setVisible(this.visibleBeforeIconifying);
  }
}
