package app.freerouting.gui.interactive;

import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.EditorStateHandle;
import app.freerouting.gui.workspace.EditorStateKind;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.InteractiveCommand;
import app.freerouting.util.TextManager;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import javax.swing.JPopupMenu;

/** Common base class of all interaction states with the graphical interface. */
public class InteractiveState implements EditorStateHandle {

  /** Provides board settings access to derived classes. */
  protected final GuiBoardManager hdlg;

  /** Contains the language-dependent messages. */
  protected final TextManager tm;

  /** The intended state after this state is finished. */
  protected InteractiveState returnState;

  /** Creates a new instance of InteractiveState. */
  protected InteractiveState(InteractiveState returnState, GuiBoardManager boardHandling) {
    this.returnState = returnState;
    this.hdlg = boardHandling;

    this.tm = new TextManager(InteractiveState.class, boardHandling.getLocale());
  }

  @Override
  public EditorStateKind kind() {
    if (this instanceof RouteState) {
      return EditorStateKind.ROUTE;
    }
    if (this instanceof InspectMenuState || this instanceof InspectedItemState) {
      return EditorStateKind.INSPECT;
    }
    if (this instanceof DragMenuState) {
      return EditorStateKind.DRAG;
    }
    if (this instanceof MenuState) {
      return EditorStateKind.MENU;
    }
    if (this instanceof DragState || this instanceof MoveItemState) {
      return EditorStateKind.DRAG;
    }
    if (this instanceof ZoomRegionState) {
      return EditorStateKind.ZOOM;
    }
    if (this instanceof ExpandTestState) {
      return EditorStateKind.EXPAND;
    }
    if (this instanceof CircleConstructionState
        || this instanceof CornerItemConstructionState
        || this instanceof CutoutRouteState
        || this instanceof HoleConstructionState
        || this instanceof PolygonShapeConstructionState
        || this instanceof SelectRegionState
        || this instanceof TileConstructionState) {
      return EditorStateKind.CONSTRUCT;
    }
    return EditorStateKind.UNKNOWN;
  }

  @Override
  public boolean hasSelection() {
    return this instanceof InspectedItemState;
  }

  /** Provides the default draw function to be overridden in derived classes. */
  public void draw(Graphics graphics) {}

  /**
   * Default function to be overwritten in derived classes. Returns the returnState of this state,
   * if the state is left after the method, or else this state.
   */
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return this;
  }

  /** Wraps {@link #leftButtonClicked(FloatPoint)} into a command for easier event testing. */
  public InteractiveCommand leftButtonClickedCommand(FloatPoint location) {
    return InteractiveCommand.from(() -> this.leftButtonClicked(location));
  }

  /**
   * Actions to be taken when a mouse button is released. Default function to be overwritten in
   * derived classes. Returns the returnState of this state, if the state is left after the method,
   * or else this state.
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
    FloatPoint mousePosition = hdlg.coordinateTransform.boardToUser(hdlg.getCurrentMousePosition());
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
  public InteractiveState mouseDragged(FloatPoint point) {
    return this;
  }

  /** Wraps {@link #mouseDragged(FloatPoint)} into a command for easier event testing. */
  public InteractiveCommand mouseDraggedCommand(FloatPoint point) {
    return InteractiveCommand.from(() -> this.mouseDragged(point));
  }

  /**
   * Actions to be taken when the left mouse button is pressed down. Default function to be
   * overwritten in derived classes. Returns the returnState of this state, if the state is left
   * after the method, or else this state.
   */
  public InteractiveState mousePressed(FloatPoint point) {
    return this;
  }

  /** Wraps {@link #mousePressed(FloatPoint)} into a command for easier event testing. */
  public InteractiveCommand mousePressedCommand(FloatPoint point) {
    return InteractiveCommand.from(() -> this.mousePressed(point));
  }

  /** Action to be taken, when the mouse wheel was turned. */
  public InteractiveState mouseWheelMoved(int rotation) {
    FloatPoint mousePosition = hdlg.getCurrentMousePosition();
    if (mousePosition != null) {
      Point2D screenMousePos =
          hdlg.graphicsContext.coordinateTransform.boardToScreen(mousePosition);
      hdlg.getPanel().zoomWithMouseWheel(screenMousePos, rotation);
    }
    return this;
  }

  /** Wraps {@link #mouseWheelMoved(int)} into a command for easier event testing. */
  public InteractiveCommand mouseWheelMovedCommand(int rotation) {
    return InteractiveCommand.from(() -> this.mouseWheelMoved(rotation));
  }

  /**
   * Default actions when a key shortcut is pressed. Overwritten in derived classes for other key
   * shortcut actions.
   */
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState result = this;
    Point2D screenMousePos =
        hdlg.graphicsContext.coordinateTransform.boardToScreen(hdlg.getCurrentMousePosition());
    switch (keyChar) {
      case 'a' -> hdlg.getPanel().boardFrame.zoomAll();
      case 'c' -> hdlg.getPanel().centerDisplay(screenMousePos);
      case 'f' -> result = ZoomRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);

      case 'o' -> hdlg.getPanel().zoomOut(screenMousePos);
      case 'z' -> hdlg.getPanel().zoomIn(screenMousePos);
      case ',' ->
          // toggle the crosshair cursor
          hdlg.getPanel().setCustomCrosshairCursor(!hdlg.getPanel().isCustomCrossHairCursor());
      case '\n', ' ' -> result = this.complete();
      case KeyEvent.VK_ESCAPE -> result = this.cancel();
      default -> {
        if (Character.isDigit(keyChar)) {
          // Change the current layer to the signal layer selected by the numeric key.
          LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
          int d = Character.digit(keyChar, 10);
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
  public InteractiveCommand keyTypedCommand(char keyChar) {
    return InteractiveCommand.from(() -> this.keyTyped(keyChar));
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
   * Changes the current layer. Returns false if the layer could not be changed.
   *
   * <p>This is the default implementation for derived classes.
   */
  public boolean changeLayerAction(int newLayer) {
    hdlg.setLayer(newLayer);
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
