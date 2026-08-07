package app.freerouting.gui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Class for temporary subwindows of the board frame */
public class BoardTemporarySubWindow extends BoardSubWindow {

  protected final BoardFrame boardFrame;

  /** Creates a new instance of BoardTemporarySubWindow */
  public BoardTemporarySubWindow(BoardFrame p_board_frame) {
    this.boardFrame = p_board_frame;
    p_board_frame.temporarySubwindows.add(this);

    this.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent evt) {
            dispose();
          }
        });
  }

  /** Used, when the board frame with all the subwindows is disposed. */
  public void board_frame_disposed() {
    super.dispose();
  }

  @Override
  public void dispose() {
    this.boardFrame.temporarySubwindows.remove(this);
    super.dispose();
  }
}
