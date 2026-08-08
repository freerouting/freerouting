package app.freerouting.gui;

/** Subwindows of the board frame. */
public class BoardSubWindow extends WindowBase {

  private boolean visibleBeforeIconifying;

  public BoardSubWindow() {
    super(300, 200);
  }

  public void parentIconified() {
    this.visibleBeforeIconifying = this.isVisible();
    this.setVisible(false);
  }

  public void parentDeiconified() {
    this.setVisible(this.visibleBeforeIconifying);
  }
}
