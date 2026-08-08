package app.freerouting.gui;

import app.freerouting.core.RoutingJob;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.ScreenMessages;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Point2D;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * Primary Swing panel component for displaying and interacting with the PCB routing board.
 *
 * <p>This panel serves as the main visual interface for the routing application, providing:
 *
 * <ul>
 *   <li><strong>Visual Display:</strong> Renders the routing board with all its components, traces,
 *       vias, and design elements
 *   <li><strong>User Interaction:</strong> Captures and processes mouse and keyboard events for
 *       interactive routing and editing
 *   <li><strong>Viewport Management:</strong> Handles zooming, panning, and scrolling of the board
 *       view
 *   <li><strong>Context Menus:</strong> Provides context-sensitive popup menus for various
 *       operations
 * </ul>
 *
 * <p><strong>Key Components:</strong>
 *
 * <ul>
 *   <li><strong>{@link GuiBoardManager}:</strong> Manages board state and interactive operations
 *   <li><strong>{@link ScreenMessages}:</strong> Displays status messages and coordinate info
 *   <li><strong>Custom Cursor:</strong> Optional crosshair cursor for precise positioning
 *   <li><strong>Popup Menus:</strong> Context-sensitive menus for different interactive states
 * </ul>
 *
 * <p><strong>Mouse Interaction:</strong>
 *
 * <ul>
 *   <li><strong>Left Button:</strong> Primary selection and action (place, route, select)
 *   <li><strong>Middle Button:</strong> Pan/scroll the board view by dragging
 *   <li><strong>Right Button:</strong> Open context-sensitive popup menus
 *   <li><strong>Mouse Wheel:</strong> Zoom in/out centered at mouse position
 * </ul>
 *
 * <p><strong>Keyboard Interaction:</strong> Keyboard events are forwarded to the board handling
 * instance, which interprets them based on the current interactive state (ESC to cancel, numeric
 * keys for layers, etc.).
 *
 * <p><strong>Rendering Pipeline:</strong> The panel delegates rendering to {@link
 * GuiBoardManager#draw(Graphics)}, which handles:
 *
 * <ul>
 *   <li>Board items (traces, vias, pads, components)
 *   <li>Rats nest (incomplete connections)
 *   <li>Interactive state graphics (rubber bands, temporary items)
 *   <li>Clearance violations and design rule indicators
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong>
 *
 * <ul>
 *   <li>Custom crosshair cursor may impact display performance significantly
 *   <li>Panel size is limited to prevent Java rendering exceptions
 *   <li>Viewport scrolling is optimized for responsive panning
 * </ul>
 *
 * @see GuiBoardManager
 * @see BoardFrame
 * @see ScreenMessages
 */
public class BoardPanel extends JPanel {

  /**
   * Default zoom factor for zoom in/out operations (2.0x).
   *
   * <p>When zooming in, the view is scaled by this factor. When zooming out, the view is scaled by
   * 1/c_zoom_factor (0.5x).
   */
  private static final double c_zoom_factor = 2.0;

  /**
   * Minimum interval (in milliseconds) between repaints triggered by mouse movement when the custom
   * crosshair cursor is enabled. This throttles the repaint rate to prevent flooding the AWT event
   * queue with full board redraws during rapid mouse motion (e.g. high-DPI mice generating 100+
   * events/second).
   */
  private static final long CURSOR_REPAINT_THROTTLE_MS = 16; // ~60 fps max

  /**
   * Message display component showing status information and coordinates.
   *
   * <p>Displays:
   *
   * <ul>
   *   <li>Current mouse position in board coordinates
   *   <li>Status messages about current operations
   *   <li>Active layer name
   *   <li>Unit of measurement
   * </ul>
   *
   * @see ScreenMessages
   */
  public final ScreenMessages screenMessages;

  /**
   * Parent frame containing this panel and other UI components.
   *
   * <p>Provides access to:
   *
   * <ul>
   *   <li>Menu bar and toolbar
   *   <li>Parameter selection windows
   *   <li>Other dialog windows
   *   <li>Frame-level operations
   * </ul>
   *
   * @see BoardFrame
   */
  public final BoardFrame boardFrame;

  /**
   * Scroll pane that contains this panel for viewport management.
   *
   * <p>Used for:
   *
   * <ul>
   *   <li>Getting viewport position and bounds
   *   <li>Programmatic scrolling during panning
   *   <li>Auto-scrolling near edges during drag operations
   * </ul>
   */
  private final JScrollPane scrollPane;

  /**
   * Global application settings affecting behavior and features.
   *
   * @see GlobalSettings
   */
  private final GlobalSettings globalSettings;

  /**
   * Popup menu displayed during interactive construction with insert/cancel options.
   *
   * @see PopupMenuInsertCancel
   */
  public JPopupMenu popupMenuInsertCancel;

  /**
   * Popup menu for copy operations with layer selection.
   *
   * @see PopupMenuCopy
   */
  public PopupMenuCopy popupMenuCopy;

  /**
   * Popup menu for move/drag operations with options.
   *
   * @see PopupMenuMove
   */
  public PopupMenuMove popupMenuMove;

  /**
   * Popup menu displayed during corner item construction.
   *
   * @see PopupMenuCornerItemConstruction
   */
  public JPopupMenu popupMenuCorneritemConstruction;

  /**
   * Main popup menu for general board operations.
   *
   * @see PopupMenuMain
   */
  public JPopupMenu popupMenuMain;

  /**
   * Popup menu for dynamic (push and shove) routing operations.
   *
   * @see PopupMenuDynamicRoute
   */
  public PopupMenuDynamicRoute popupMenuDynamicRoute;

  /**
   * Popup menu for stitch routing operations.
   *
   * @see PopupMenuStitchRoute
   */
  public PopupMenuStitchRoute popupMenuStitchRoute;

  /**
   * Popup menu for item selection and inspection operations.
   *
   * @see PopupMenuInspectedItems
   */
  public JPopupMenu popupMenuSelect;

  /**
   * Drop target listener for handling drag-and-drop file operations.
   *
   * @see BoardPanelDropTargetListener
   */
  private BoardPanelDropTargetListener dropTargetListener;

  /** Non-null while the first board paint after load is in progress. */
  private String renderingOverlayMessage;

  /**
   * Board handling instance managing interactive board operations.
   *
   * <p>Handles:
   *
   * <ul>
   *   <li>Mouse and keyboard event processing
   *   <li>Interactive state management
   *   <li>Board rendering coordination
   *   <li>Autorouting and optimization
   * </ul>
   *
   * @see GuiBoardManager
   */
  GuiBoardManager boardHandling;

  /**
   * Screen location where the right mouse button was last clicked.
   *
   * <p>Used for operations that need to reference the popup menu trigger location.
   */
  Point2D rightButtonClickLocation;

  /**
   * AWT Robot for programmatically moving the mouse cursor.
   *
   * <p>Used to reposition the mouse pointer during certain interactive operations (e.g., centering
   * on a point). May be null if Robot creation failed.
   */
  private Robot robot;

  /**
   * Starting position for middle mouse button drag operation.
   *
   * <p>Non-null while middle button panning is in progress. Used to calculate scroll delta during
   * drag.
   */
  private Point middleDragPosition;

  /**
   * Custom crosshair cursor for precise positioning, or null for standard cursor.
   *
   * <p>When enabled, displays a 45-degree crosshair at the mouse position.
   * <strong>Warning:</strong> Using the custom cursor can significantly impact display performance
   * as it requires manual rendering on every mouse move.
   *
   * @see Cursor
   * @see #setCustomCrosshairCursor(boolean)
   */
  private Cursor customCursor;

  /**
   * Timestamp (in milliseconds) of the last repaint triggered by cursor movement.
   *
   * <p>Used to throttle cursor-driven repaints. Only one repaint is issued per {@link
   * #CURSOR_REPAINT_THROTTLE_MS} window; mouse-move events that arrive within the throttle window
   * update the cursor position but do not immediately trigger a repaint. The final position will be
   * painted on the next scheduled repaint.
   */
  private long lastCursorRepaintTime;

  /**
   * Creates a new BoardPanel within a GUI application context.
   *
   * <p>Initialization includes:
   *
   * <ul>
   *   <li>Setting up the Robot for programmatic mouse control (if available)
   *   <li>Storing references to parent components
   *   <li>Configuring panel appearance and event listeners
   *   <li>Creating the GuiBoardManager for board operations
   * </ul>
   *
   * <p>The Robot may fail to initialize on some systems (e.g., headless environments), in which
   * case programmatic mouse movement will not be available.
   *
   * @param p_screen_messages the message display component for status information
   * @param p_board_frame the parent frame containing this panel
   * @param globalSettings global application settings
   * @param routingJob the routing job context for this session
   * @param settingsMerger merger for combining different settings sources
   * @see GuiBoardManager
   * @see Robot
   */
  public BoardPanel(
      ScreenMessages p_screen_messages,
      BoardFrame p_board_frame,
      GlobalSettings globalSettings,
      RoutingJob routingJob,
      SettingsMerger settingsMerger) {
    this.screenMessages = p_screen_messages;
    try {
      // used to be able to change the location of the mouse pointer
      robot = new Robot();
    } catch (AWTException _) {
      FRLogger.warn("unable to create robot");
    }
    this.boardFrame = p_board_frame;
    this.globalSettings = globalSettings;
    this.scrollPane = boardFrame.scrollPane;
    defaultInit(globalSettings, routingJob, settingsMerger);
  }

  private void defaultInit(
      GlobalSettings globalSettings, RoutingJob routingJob, SettingsMerger settingMerger) {
    setLayout(new BorderLayout());

    setBackground(new Color(0, 0, 0));
    setMaximumSize(new Dimension(30000, 20000));
    setMinimumSize(new Dimension(90, 60));
    setPreferredSize(new Dimension(1200, 900));
    addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseDragged(MouseEvent evt) {
            mouseDraggedAction(evt);
          }

          @Override
          public void mouseMoved(MouseEvent evt) {
            mouseMovedAction(evt);
          }
        });
    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent evt) {
            boardHandling.keyTypedAction(evt.getKeyChar());
          }
        });
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evt) {
            mouseClickedAction(evt);
          }

          @Override
          public void mousePressed(MouseEvent evt) {
            mousePressedAction(evt);
          }

          @Override
          public void mouseReleased(MouseEvent evt) {
            boardHandling.buttonReleased();
            if (middleDragPosition != null) {
              // Restore the detailed copper-pour rendering now that panning has ended.
              if (boardHandling != null && boardHandling.graphicsContext != null) {
                boardHandling.graphicsContext.setSimplifiedPlaneRendering(false);
              }
              repaint();
            }
            middleDragPosition = null;
          }
        });
    addMouseWheelListener(
        evt -> boardHandling.mouseWheelMoved(evt.getPoint(), evt.getWheelRotation()));

    boardHandling = new GuiBoardManager(this, globalSettings, routingJob, settingMerger);
    boardHandling.setBoardFrame(this.boardFrame);
    setAutoscrolls(true);
    this.setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));

    // Initialize drag-and-drop support for file loading
    dropTargetListener = new BoardPanelDropTargetListener(this);
    new DropTarget(this, dropTargetListener);
  }

  /**
   * Resets the board handling instance for a new routing job.
   *
   * <p>This method safely transitions to a new routing job by:
   *
   * <ol>
   *   <li>Preserving the settings merger reference
   *   <li>Disposing the old board handling instance (cleanup)
   *   <li>Creating a new GuiBoardManager with the new job
   * </ol>
   *
   * <p>Used when:
   *
   * <ul>
   *   <li>Loading a different board design
   *   <li>Restarting the routing session
   *   <li>Switching between multiple boards
   * </ul>
   *
   * @param routingJob the new routing job to initialize
   * @see GuiBoardManager#dispose()
   */
  public void resetBoardHandling(RoutingJob routingJob) {
    // Save the settingsMerger reference before disposing the old instance
    SettingsMerger settingsMerger = boardHandling != null ? boardHandling.settingsMerger : null;

    // Dispose the old boardHandling instance before creating a new one
    if (boardHandling != null) {
      boardHandling.dispose();
    }

    boardHandling = new GuiBoardManager(this, globalSettings, routingJob, settingsMerger);
    boardHandling.setBoardFrame(this.boardFrame);
  }

  /**
   * Initializes all popup menus used throughout the interactive session.
   *
   * <p>Creates popup menu instances for different contexts:
   *
   * <ul>
   *   <li><strong>Main menu:</strong> General board operations
   *   <li><strong>Routing menus:</strong> Dynamic route and stitch route options
   *   <li><strong>Construction menus:</strong> Corner item and insert/cancel operations
   *   <li><strong>Selection menus:</strong> Operations on selected items
   *   <li><strong>Edit menus:</strong> Copy and move operations
   * </ul>
   *
   * <p>Should be called after the board frame is fully initialized and before interactive
   * operations begin.
   *
   * @see PopupMenuMain
   * @see PopupMenuDynamicRoute
   * @see PopupMenuStitchRoute
   */
  void createPopupMenus() {
    popupMenuMain = new PopupMenuMain(this.boardFrame);
    popupMenuDynamicRoute = new PopupMenuDynamicRoute(this.boardFrame);
    popupMenuStitchRoute = new PopupMenuStitchRoute(this.boardFrame);
    popupMenuCorneritemConstruction = new PopupMenuCornerItemConstruction(this.boardFrame);
    popupMenuSelect = new PopupMenuInspectedItems(this.boardFrame);
    popupMenuInsertCancel = new PopupMenuInsertCancel(this.boardFrame);
    popupMenuCopy = new PopupMenuCopy(this.boardFrame);
    popupMenuMove = new PopupMenuMove(this.boardFrame);
  }

  /**
   * Handles mouse wheel zoom events at the specified screen position.
   *
   * <p>Zoom behavior:
   *
   * <ul>
   *   <li><strong>Scroll down:</strong> Zoom out (decrease magnification)
   *   <li><strong>Scroll up:</strong> Zoom in (increase magnification)
   *   <li><strong>Zoom center:</strong> Mouse position (stays fixed relative to board)
   * </ul>
   *
   * <p>The method applies a zoom factor of 10% per wheel notch, with a minimum zoom factor of 0.5
   * to prevent excessive zoom out.
   *
   * <p>Ignored if middle mouse button panning is in progress or wheel rotation is zero.
   *
   * @param p_point the screen position to center the zoom operation on
   * @param p_wheel_rotation the wheel rotation amount (negative for zoom in, positive for zoom out)
   * @see #zoom(double, Point2D)
   */
  public void zoomWithMouseWheel(Point2D p_point, int p_wheel_rotation) {
    if (this.middleDragPosition != null || p_wheel_rotation == 0) {
      return; // scrolling with the middle mouse button in progress
    }
    double zoomFactor = 1 - 0.1 * p_wheel_rotation;
    zoomFactor = Math.max(zoomFactor, 0.5);
    zoom(zoomFactor, p_point);
  }

  private void mousePressedAction(MouseEvent evt) {
    if (evt.getButton() == 1) {
      boardHandling.mousePressed(evt.getPoint());
    } else if (evt.getButton() == 2 && middleDragPosition == null) {
      middleDragPosition = new Point(evt.getPoint());
      // While panning, render copper pours as fast solid fills (same mechanism used
      // for the first paint after load) so that the expensive per-frame clearance
      // CSG / transformed-area fill is skipped during the drag. The detailed view
      // is restored on mouse release.
      if (boardHandling != null && boardHandling.graphicsContext != null) {
        boardHandling.graphicsContext.setSimplifiedPlaneRendering(true);
      }
    }
  }

  private void mouseDraggedAction(MouseEvent evt) {
    if (middleDragPosition != null) {
      scrollMiddleMouse(evt);
    } else {
      boardHandling.mouseDragged(evt.getPoint());
      scrollNearBorder(evt);
    }
  }

  private void mouseMovedAction(MouseEvent p_evt) {
    this.requestFocusInWindow(); // to enable keyboard aliases
    if (boardHandling != null) {
      boardHandling.mouseMoved(p_evt.getPoint());
    }
    if (this.customCursor != null) {
      this.customCursor.setLocation(p_evt.getPoint());
      // Throttle repaints to avoid flooding the AWT event queue with full board redraws
      // during rapid mouse motion. High-DPI mice can generate 100+ events/second, and
      // each repaint triggers a full board render + cursor overlay.
      long now = System.currentTimeMillis();
      if (now - this.lastCursorRepaintTime >= CURSOR_REPAINT_THROTTLE_MS) {
        this.lastCursorRepaintTime = now;
        this.repaint();
      }
    }
  }

  private void mouseClickedAction(MouseEvent evt) {
    if (evt.getButton() == 1) {
      boardHandling.leftButtonClicked(evt.getPoint());
    } else if (evt.getButton() == 3) {
      JPopupMenu currMenu = boardHandling.getCurrentPopupMenu();
      if (currMenu != null) {
        int currX = evt.getX();
        int currY = evt.getY();
        if (false) {
          int dx = currMenu.getWidth();
          if (dx <= 0) {
            // force the width to be calculated
            currMenu.show(this, currX, currY);
            dx = currMenu.getWidth();
          }
          currX -= dx;
        }
        currMenu.show(this, currX, currY);
      }
      rightButtonClickLocation = evt.getPoint();
    }
  }

  /**
   * Renders the board panel including all visual elements.
   *
   * <p>Rendering pipeline:
   *
   * <ol>
   *   <li>Call super to paint the panel background
   *   <li>Delegate board drawing to {@link GuiBoardManager#draw(Graphics)}
   *   <li>Draw drag-and-drop ghosting overlay if active
   *   <li>Draw custom cursor overlay if enabled
   * </ol>
   *
   * <p>The board manager handles rendering of:
   *
   * <ul>
   *   <li>Board geometry and items
   *   <li>Rats nest and violations
   *   <li>Interactive state graphics
   * </ul>
   *
   * @param p_g the graphics context for rendering
   * @see GuiBoardManager#draw(Graphics)
   */
  @Override
  public void paintComponent(Graphics p_g) {
    super.paintComponent(p_g);
    if (boardHandling != null) {
      boardHandling.draw(p_g);
    }

    // Draw ghosting overlay for drag-and-drop file operations
    if (isGhostingActive()) {
      Graphics2D g2d = (Graphics2D) p_g.create();
      try {
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g2d.setColor(new Color(128, 128, 128, 180));
        g2d.fillRect(0, 0, getWidth(), getHeight());
      } finally {
        g2d.dispose();
      }
    }

    if (renderingOverlayMessage != null) {
      drawRenderingOverlay(p_g);
    }

    if (this.customCursor != null) {
      this.customCursor.draw(p_g);
    }
  }

  /**
   * Checks if the drag-and-drop ghosting overlay is currently visible.
   *
   * @return true if ghosting overlay is active
   */
  private boolean isGhostingActive() {
    return dropTargetListener != null && dropTargetListener.isGhostingActive();
  }

  /** Shows a semi-transparent overlay while the first board paint after load is in progress. */
  public void showRenderingOverlay(String message) {
    renderingOverlayMessage = message;
    repaint();
  }

  /** Clears the first-paint rendering overlay. */
  public void clearRenderingOverlay() {
    renderingOverlayMessage = null;
    repaint();
  }

  private void drawRenderingOverlay(Graphics p_g) {
    Graphics2D g2d = (Graphics2D) p_g.create();
    try {
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
      g2d.setColor(new Color(240, 240, 240, 220));
      g2d.fillRect(0, 0, getWidth(), getHeight());

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
      g2d.setColor(new Color(60, 60, 60));
      Font font = g2d.getFont().deriveFont(Font.BOLD, 16f);
      g2d.setFont(font);
      FontMetrics metrics = g2d.getFontMetrics(font);
      int textWidth = metrics.stringWidth(renderingOverlayMessage);
      int x = Math.max(8, (getWidth() - textWidth) / 2);
      int y = Math.max(metrics.getAscent() + 8, getHeight() / 2);
      g2d.drawString(renderingOverlayMessage, x, y);
    } finally {
      g2d.dispose();
    }
  }

  /**
   * Returns the current viewport position in panel coordinates.
   *
   * <p>The viewport position represents the top-left corner of the visible area within the
   * scrollable panel.
   *
   * @return the viewport position as a Point
   * @see #setViewportPosition(Point)
   */
  public Point getViewportPosition() {
    JViewport viewport = scrollPane.getViewport();
    return viewport.getViewPosition();
  }

  /**
   * Sets the viewport position to the specified point.
   *
   * <p>Scrolls the panel so that the specified point becomes the top-left corner of the visible
   * viewport area.
   *
   * @param p_position the new viewport position
   * @see #getViewportPosition()
   */
  void setViewportPosition(Point p_position) {
    JViewport viewport = scrollPane.getViewport();
    viewport.setViewPosition(p_position);
  }

  /**
   * Zooms in at the specified screen position by the default zoom factor.
   *
   * <p>Increases the board magnification by {@link #c_zoom_factor} (2x), keeping the specified
   * point fixed in screen coordinates.
   *
   * @param p_position the screen position to center zoom on
   * @see #zoomOut(Point2D)
   * @see #zoom(double, Point2D)
   */
  public void zoomIn(Point2D p_position) {
    zoom(c_zoom_factor, p_position);
  }

  /**
   * Zooms out at the specified screen position by the inverse zoom factor.
   *
   * <p>Decreases the board magnification by 1/{@link #c_zoom_factor} (0.5x), keeping the specified
   * point fixed in screen coordinates.
   *
   * @param p_position the screen position to center zoom on
   * @see #zoomIn(Point2D)
   * @see #zoom(double, Point2D)
   */
  public void zoomOut(Point2D p_position) {
    double zoomFactor = 1 / c_zoom_factor;
    zoom(zoomFactor, p_position);
  }

  /**
   * Zooms to fit a rectangular frame defined by two corner points.
   *
   * <p>Calculates the appropriate zoom factor to display the entire rectangle within the viewport,
   * then centers the view on the rectangle's midpoint.
   *
   * <p>Used for "zoom to selection" and "zoom to frame" operations where the user defines a region
   * of interest.
   *
   * @param p_position1 first corner of the rectangle to zoom to
   * @param p_position2 opposite corner of the rectangle
   * @see #zoom(double, Point2D)
   */
  public void zoomFrame(Point2D p_position1, Point2D p_position2) {
    double widthOfZoomFrame = Math.abs(p_position1.getX() - p_position2.getX());
    double heightOfZoomFrame = Math.abs(p_position1.getY() - p_position2.getY());

    double centerX = Math.min(p_position1.getX(), p_position2.getX()) + (widthOfZoomFrame / 2);
    double centerY = Math.min(p_position1.getY(), p_position2.getY()) + (heightOfZoomFrame / 2);

    Point2D centerPoint = new Point2D.Double(centerX, centerY);

    Rectangle displayRect = getViewportBounds();

    double widthFactor = displayRect.getWidth() / widthOfZoomFrame;
    double heightFactor = displayRect.getHeight() / heightOfZoomFrame;

    Point2D changedLocation = zoom(Math.min(widthFactor, heightFactor), centerPoint);
    setViewportCenter(changedLocation);
  }

  /**
   * Centers the display on the specified board position.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Adjusts the viewport to center on the specified point
   *   <li>Calculates the mouse cursor offset from the new center
   *   <li>Moves the mouse cursor to maintain visual continuity
   *   <li>Triggers a repaint to update the display
   * </ol>
   *
   * <p>Useful for "go to" operations and centering on specific board features.
   *
   * @param p_new_center the board position to center the view on
   * @see #setViewportCenter(Point2D)
   * @see #moveMouse(Point2D)
   */
  public void centerDisplay(Point2D p_new_center) {
    Point delta = setViewportCenter(p_new_center);
    Point2D newCenter = getViewportCenter();
    Point newMouseLocation =
        new Point((int) (newCenter.getX() - delta.getX()), (int) (newCenter.getY() - delta.getY()));
    moveMouse(newMouseLocation);
    repaint();
  }

  /**
   * Returns the center point of the current viewport in panel coordinates.
   *
   * <p>Calculates the center by adding half the viewport dimensions to the viewport position.
   *
   * @return the viewport center as a Point2D
   * @see #getViewportPosition()
   * @see #getViewportBounds()
   */
  public Point2D getViewportCenter() {
    Point pos = getViewportPosition();
    Rectangle displayRect = getViewportBounds();
    return new Point2D.Double(
        pos.getX() + displayRect.getCenterX(), pos.getY() + displayRect.getCenterY());
  }

  /**
   * Zooms the board view by the specified factor centered at the given location.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Scales the panel size by the zoom factor
   *   <li>Adjusts the coordinate transform in the graphics context
   *   <li>Repositions the viewport to keep p_location fixed on screen
   *   <li>Returns the adjusted cursor position after zoom
   * </ol>
   *
   * <p><strong>Zoom Factor Examples:</strong>
   *
   * <ul>
   *   <li>{@code 2.0}: Zoom in 2x (200%)
   *   <li>{@code 0.5}: Zoom out 2x (50%)
   *   <li>{@code 1.0}: No change
   * </ul>
   *
   * <p><strong>Size Limit:</strong> Panel size is capped at 10,000,000 pixels to prevent Java
   * rendering exceptions on large zooms.
   *
   * @param p_factor the zoom multiplication factor (greater than 1 zooms in, less than 1 zooms out)
   * @param p_location the screen position that should remain fixed during zoom
   * @return the adjusted cursor location after zoom and viewport adjustment
   * @see #zoomIn(Point2D)
   * @see #zoomOut(Point2D)
   */
  public Point2D zoom(double p_factor, Point2D p_location) {
    final int maxPanelSize = 10000000;
    Dimension oldSize = this.getSize();
    Point2D oldCenter = getViewportCenter();

    if (p_factor > 1 && Math.max(oldSize.getWidth(), oldSize.getHeight()) >= maxPanelSize) {
      return p_location; // to prevent an sun.dc.pr.PRException, which I do not know, how to handle;
      // maybe a bug in Java.
    }
    int newWidth = (int) Math.round(p_factor * oldSize.getWidth());
    int newHeight = (int) Math.round(p_factor * oldSize.getHeight());
    Dimension newSize = new Dimension(newWidth, newHeight);
    boardHandling.graphicsContext.changePanelSize(newSize);
    setPreferredSize(newSize);
    setSize(newSize);
    revalidate();

    Point2D newCursor =
        new Point2D.Double(p_location.getX() * p_factor, p_location.getY() * p_factor);
    double dx = newCursor.getX() - p_location.getX();
    double dy = newCursor.getY() - p_location.getY();
    Point2D newCenter = new Point2D.Double(oldCenter.getX() + dx, oldCenter.getY() + dy);
    Point2D adjustmentVector = setViewportCenter(newCenter);
    // Update the custom cursor position to match the new zoom level
    if (this.customCursor != null) {
      Point2D adjustedNewCursor =
          new Point2D.Double(
              newCursor.getX() + adjustmentVector.getX() + 0.5,
              newCursor.getY() + adjustmentVector.getY() + 0.5);
      this.customCursor.setLocation(adjustedNewCursor);
    }
    repaint();
    return new Point2D.Double(
        newCursor.getX() + adjustmentVector.getX() + 0.5,
        newCursor.getY() + adjustmentVector.getY() + 0.5);
  }

  /**
   * Returns the rectangular bounds of the current viewport.
   *
   * <p>The viewport bounds represent the visible area of the panel within the scroll pane, in panel
   * coordinates.
   *
   * @return the viewport bounds rectangle
   * @see JScrollPane#getViewportBorderBounds()
   */
  Rectangle getViewportBounds() {
    return scrollPane.getViewportBorderBounds();
  }

  /**
   * Sets the viewport center to the specified point with boundary adjustments.
   *
   * <p>Attempts to center the viewport on p_point, but adjusts if the point is near the panel edges
   * to keep the viewport within valid bounds. Returns the adjustment vector representing how much
   * the requested center had to be shifted.
   *
   * <p>The adjustment ensures:
   *
   * <ul>
   *   <li>Viewport stays within panel boundaries
   *   <li>No part of the viewport extends beyond the panel
   *   <li>Smooth scrolling behavior near edges
   * </ul>
   *
   * @param p_point the desired center point in panel coordinates
   * @return the adjustment vector (delta from requested to actual position)
   * @see #getViewportCenter()
   * @see #setViewportPosition(Point)
   */
  Point setViewportCenter(Point2D p_point) {
    Rectangle displayRect = getViewportBounds();
    double xCorner = p_point.getX() - displayRect.getWidth() / 2;
    double yCorner = p_point.getY() - displayRect.getHeight() / 2;
    Dimension panelSize = getSize();
    double adjustedXCorner = Math.min(xCorner, panelSize.getWidth());
    adjustedXCorner = Math.max(xCorner, 0);
    double adjustedYCorner = Math.min(yCorner, panelSize.getHeight());
    adjustedYCorner = Math.max(yCorner, 0);
    Point newPosition = new Point((int) adjustedXCorner, (int) adjustedYCorner);
    setViewportPosition(newPosition);
    return new Point((int) (adjustedXCorner - xCorner), (int) (adjustedYCorner - yCorner));
  }

  /**
   * Selects the specified signal layer in the parameter selection window and updates menus.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Updates the layer selection in the parameter window
   *   <li>Disables the selected layer in routing popup menus (can't route to current layer)
   *   <li>Synchronizes UI state across all layer-dependent controls
   * </ul>
   *
   * @param p_signal_layer_no the signal layer number to select (0-based index)
   * @see BoardFrame#selectParameterWindow
   */
  public void setSelectedSignalLayer(int p_signal_layer_no) {
    if (this.boardFrame.selectParameterWindow != null) {
      this.boardFrame.selectParameterWindow.select(p_signal_layer_no);
      this.popupMenuDynamicRoute.disableLayerItem(p_signal_layer_no);
      this.popupMenuStitchRoute.disableLayerItem(p_signal_layer_no);
      this.popupMenuCopy.disableLayerItem(p_signal_layer_no);
    }
  }

  /**
   * Initializes color table listeners to respond to color changes.
   *
   * <p>Sets up listeners on both item and other color tables that will:
   *
   * <ul>
   *   <li>Update the panel background when colors change
   *   <li>Trigger repaints to reflect new color schemes
   *   <li>Maintain visual consistency with color preferences
   * </ul>
   *
   * <p>Should be called after the graphics context is fully initialized.
   *
   * @see ColorTableListener
   */
  void initColors() {
    boardHandling.graphicsContext.itemColorTable.addTableModelListener(new ColorTableListener());
    boardHandling.graphicsContext.otherColorTable.addTableModelListener(new ColorTableListener());
    setBackground(boardHandling.graphicsContext.getBackgroundColor());
  }

  private void scrollNearBorder(MouseEvent p_evt) {
    final int borderDist = 50;
    Rectangle r =
        new Rectangle(
            p_evt.getX() - borderDist, p_evt.getY() - borderDist, 2 * borderDist, 2 * borderDist);
    ((JPanel) p_evt.getSource()).scrollRectToVisible(r);
  }

  private void scrollMiddleMouse(MouseEvent p_evt) {
    double deltaX = middleDragPosition.x - p_evt.getX();
    double deltaY = middleDragPosition.y - p_evt.getY();

    Point viewPosition = getViewportPosition();

    double x = viewPosition.x + deltaX;
    double y = viewPosition.y + deltaY;

    Dimension panelSize = this.getSize();
    x = Math.min(x, panelSize.getWidth() - this.getViewportBounds().getWidth());
    y = Math.min(y, panelSize.getHeight() - this.getViewportBounds().getHeight());

    x = Math.max(x, 0);
    y = Math.max(y, 0);

    Point p = new Point((int) x, (int) y);
    setViewportPosition(p);
  }

  /**
   * Programmatically moves the mouse cursor to the specified panel location.
   *
   * <p>Converts the panel coordinates to absolute screen coordinates, accounting for frame position
   * and viewport scrolling, then uses the Robot to move the system mouse cursor.
   *
   * <p>Does nothing if Robot initialization failed during construction.
   *
   * @param p_location the target position in panel coordinates
   * @see Robot#mouseMove(int, int)
   * @see #centerDisplay(Point2D)
   */
  public void moveMouse(Point2D p_location) {
    if (robot == null) {
      return;
    }
    Point absolutePanelLocation = boardFrame.absolutePanelLocation();
    Point viewPosition = getViewportPosition();
    int x =
        (int) Math.round(absolutePanelLocation.getX() - viewPosition.getX() + p_location.getX())
            + 1;
    int y =
        (int)
            Math.round(absolutePanelLocation.getY() - viewPosition.getY() + p_location.getY() + 1);
    robot.mouseMove(x, y);
  }

  /**
   * Enables or disables the custom crosshair cursor.
   *
   * <p>When enabled, displays a 45-degree crosshair cursor for precise positioning. The custom
   * cursor is drawn as an overlay on the board panel.
   *
   * <p><strong>Performance Warning:</strong> Using the custom cursor can significantly slow down
   * display performance because it requires manual rendering and repaint on every mouse movement.
   * Use only when precise cursor positioning is critical.
   *
   * @param p_value true to enable custom crosshair, false for standard cursor
   * @see Cursor#get45DegreeCrossHairCursor()
   * @see #isCustomCrossHairCursor()
   */
  public void setCustomCrosshairCursor(boolean p_value) {
    if (p_value) {
      this.customCursor = Cursor.get45DegreeCrossHairCursor();
    } else {
      this.customCursor = null;
    }
    boardFrame.refreshWindows();
    repaint();
  }

  /**
   * Checks if the custom crosshair cursor is currently enabled.
   *
   * <p>Returns true if the custom 45-degree crosshair cursor is being used, false if the standard
   * system cursor is active.
   *
   * @return true if custom crosshair cursor is enabled, false otherwise
   * @see #setCustomCrosshairCursor(boolean)
   */
  public boolean isCustomCrossHairCursor() {
    return this.customCursor != null;
  }

  private class ColorTableListener implements TableModelListener {

    @Override
    public void tableChanged(TableModelEvent p_event) {
      // redisplay board because some colors have changed.
      setBackground(boardHandling.graphicsContext.getBackgroundColor());
      repaint();
    }
  }
}
