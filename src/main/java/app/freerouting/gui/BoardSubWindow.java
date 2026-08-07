package app.freerouting.gui;

/** Subwindows of the board frame. */
public class BoardSubWindow extends WindowBase {

  private boolean visibleBeforeIconifying;

  public BoardSubWindow() {
    super(300, 200);
  }

  public void parent_iconified() {
    this.visibleBeforeIconifying = this.isVisible();
    this.setVisible(false);
  }

  public void parent_deiconified() {
    this.setVisible(this.visibleBeforeIconifying);
  }
}
