package app.freerouting.gui;

/**
 * Subwindows of the board frame.
 */
public class BoardSubWindow extends javax.swing.JFrame
{

    public void parent_iconified()
    {
        this.visible_before_iconifying = this.isVisible();
        this.setVisible(false);
    }

    public void parent_deiconified()
    {
        this.setVisible(this.visible_before_iconifying);
    }


    private boolean visible_before_iconifying = false;
}
