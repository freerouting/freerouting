package app.freerouting.interactive;

import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.commands.InteractiveCommand;
import app.freerouting.util.TextManager;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import javax.swing.JPopupMenu;

/** Common base class of all interaction states with the graphical interface */
public class InteractiveState {

  /** board setting access handler for the derived classes */
  protected final GuiBoardManager hdlg;

  /** Contains the files with the language dependent messages */
  protected final TextManager tm;

  /** The intended state after this state is finished */
  protected InteractiveState returnState;

  /** Creates a new instance of InteractiveState */
  protected InteractiveState(InteractiveState p_return_state, GuiBoardManager p_board_handling) {
    this.returnState = p_return_state;
    this.hdlg = p_board_handling;

    this.tm = new TextManager(InteractiveState.class, p_board_handling.getLocale());
  }

  /** default draw function to be overwritten in derived classes */
  public void draw(Graphics p_graphics) {}

  /**
   * Default function to be overwritten in derived classes. Returns the returnState of this state,
   * if the state is left after the method, or else this state.
   */
  public InteractiveState leftButtonClicked(FloatPoint p_location) {
    return this;
  }

  /** Wraps {@link #leftButtonClicked(FloatPoint)} into a command for easier event testing. */
  public InteractiveCommand leftButtonClickedCommand(FloatPoint p_location) {
    return InteractiveCommand.from(() -> this.leftButtonClicked(p_location));
  }

  /*
   * Actions to be taken when a mouse button is released.
   * Default function to be overwritten in derived classes.
   * Returns the returnState of this state, if the state is left
   * after the method, or else this state.
   */
  public InteractiveState buttonReleased() {
    return this;
  }

  /** Wraps {@link #buttonReleased()} into a command for easier event testing. */
  public InteractiveCommand buttonReleasedCommand() {
    return InteractiveCommand.from(this::buttonReleased);
  }

  /**
   * Actions to be taken, when the location of the mouse pointer changes. Default function to be
   * overwritten in derived classes. Returns the returnState of this state, if the state ends after
   * the method, or else this state.
   */
  public InteractiveState mouseMoved() {
    FloatPoint mousePosition =
        hdlg.coordinateTransform.boardToUser(hdlg.getCurrentMousePosition());
    hdlg.screenMessages.setMousePosition(mousePosition);
    return this;
  }

  /** Wraps {@link #mouseMoved()} into a command for easier event testing. */
  public InteractiveCommand mouseMovedCommand() {
    return InteractiveCommand.from(this::mouseMoved);
  }

  /**
   * Actions to be taken when the mouse moves with a button pressed down. Default function to be
   * overwritten in derived classes. Returns the returnState of this state, if the state is left
   * after the method, or else this state.
   */
  public InteractiveState mouseDragged(FloatPoint p_point) {
    return this;
  }

  /** Wraps {@link #mouseDragged(FloatPoint)} into a command for easier event testing. */
  public InteractiveCommand mouseDraggedCommand(FloatPoint p_point) {
    return InteractiveCommand.from(() -> this.mouseDragged(p_point));
  }

  /**
   * Actions to be taken when the left mouse button is pressed down. Default function to be
   * overwritten in derived classes. Returns the returnState of this state, if the state is left
   * after the method, or else this state.
   */
  public InteractiveState mousePressed(FloatPoint p_point) {
    return this;
  }

  /** Wraps {@link #mousePressed(FloatPoint)} into a command for easier event testing. */
  public InteractiveCommand mousePressedCommand(FloatPoint p_point) {
    return InteractiveCommand.from(() -> this.mousePressed(p_point));
  }

  /** Action to be taken, when the mouse wheel was turned. */
  public InteractiveState mouseWheelMoved(int p_rotation) {
    FloatPoint mousePosition = hdlg.getCurrentMousePosition();
    if (mousePosition != null) {
      Point2D screenMousePos =
          hdlg.graphicsContext.coordinateTransform.boardToScreen(mousePosition);
      hdlg.getPanel().zoomWithMouseWheel(screenMousePos, p_rotation);
    }
    return this;
  }

  /** Wraps {@link #mouseWheelMoved(int)} into a command for easier event testing. */
  public InteractiveCommand mouseWheelMovedCommand(int p_rotation) {
    return InteractiveCommand.from(() -> this.mouseWheelMoved(p_rotation));
  }

  /**
   * Default actions when a key shortcut is pressed. Overwritten in derived classes for other key
   * shortcut actions.
   */
  public InteractiveState keyTyped(char p_key_char) {
    InteractiveState result = this;
    Point2D screenMousePos =
        hdlg.graphicsContext.coordinateTransform.boardToScreen(hdlg.getCurrentMousePosition());
    switch (p_key_char) {
      case 'a' -> hdlg.getPanel().boardFrame.zoomAll();
      case 'c' -> hdlg.getPanel().centerDisplay(screenMousePos);
      case 'f' ->
          result = ZoomRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);

      case 'o' -> hdlg.getPanel().zoomOut(screenMousePos);
      case 'z' -> hdlg.getPanel().zoomIn(screenMousePos);
      case ',' ->
          // toggle the crosshair cursor
          hdlg.getPanel()
              .setCustomCrosshairCursor(!hdlg.getPanel().isCustomCrossHairCursor());
      case '\n', ' ' -> result = this.complete();
      case KeyEvent.VK_ESCAPE -> result = this.cancel();
      default -> {
        if (Character.isDigit(p_key_char)) {
          // change the current layer to the p_key_char-ths signal layer
          LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
          int d = Character.digit(p_key_char, 10);
          d = Math.min(d, layerStructure.signalLayerCount());
          // Board layers start at 0, keyboard input for layers starts at 1.
          d = Math.max(d - 1, 0);
          d = layerStructure.getNo(layerStructure.getSignalLayer(d));
          hdlg.setCurrentLayer(d);
        }
      }
    }
    return result;
  }

  /** Wraps {@link #keyTyped(char)} into a command for easier event testing. */
  public InteractiveCommand keyTypedCommand(char p_key_char) {
    return InteractiveCommand.from(() -> this.keyTyped(p_key_char));
  }

  /**
   * Action to be taken, when this state is completed and exited. Default function to be overwritten
   * in derived classes. Returns the returnState of this state.
   */
  public InteractiveState complete() {

    return this.returnState;
  }

  /** Wraps {@link #complete()} into a command for easier event testing. */
  public InteractiveCommand completeCommand() {
    return InteractiveCommand.from(this::complete);
  }

  /**
   * Actions to be taken, when this state gets cancelled. Default function to be overwritten in
   * derived classes. Returns the parent state of this state.
   */
  public InteractiveState cancel() {

    return this.returnState;
  }

  /** Wraps {@link #cancel()} into a command for easier event testing. */
  public InteractiveCommand cancelCommand() {
    return InteractiveCommand.from(this::cancel);
  }

  /**
   * Action to be taken, when the current layer is changed. returns false, if the layer could not be
   * changed, Default function to be overwritten in derived classes.
   */
  public boolean changeLayerAction(int p_new_layer) {
    hdlg.setLayer(p_new_layer);
    return true;
  }

  /** The default message displayed, when this state is active. */
  public void displayDefaultMessage() {}

  /** Gets the identifier for displaying help for the user about this state. */
  public String getHelpId() {
    return "MenuState";
  }

  /**
   * Returns the popup menu from boardPanel, which is used in this interactive state. Default
   * function to be overwritten in derived classes.
   */
  public JPopupMenu getPopupMenu() {
    return null;
  }

  /** A state using toolbar must overwrite this function. */
  public void setToolbar() {}
}
