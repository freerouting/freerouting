package app.freerouting.gui;

/** Class for temporary subwindows of the board frame */
public class BoardTemporarySubWindow extends BoardSubWindow {

  protected final BoardFrame board_frame;

  /** Creates a new instance of BoardTemporarySubWindow */
  public BoardTemporarySubWindow(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;
    p_board_frame.temporary_subwindows.add(this);

    this.addWindowListener(
        new java.awt.event.WindowAdapter() {
          public void windowClosing(java.awt.event.WindowEvent evt) {
            dispose();
          }
        });
  }

  /** Used, when the board frame with all the subwindows is disposed. */
  public void board_frame_disposed() {
    super.dispose();
  }

  public void dispose() {
    this.board_frame.temporary_subwindows.remove(this);
    super.dispose();
  }
}
