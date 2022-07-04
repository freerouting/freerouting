package app.freerouting.gui;

/** Subwindows of the board frame. */
public class BoardSubWindow extends javax.swing.JFrame {

  private boolean visible_before_iconifying = false;

  public void parent_iconified() {
    this.visible_before_iconifying = this.isVisible();
    this.setVisible(false);
  }

  public void parent_deiconified() {
    this.setVisible(this.visible_before_iconifying);
  }
}
