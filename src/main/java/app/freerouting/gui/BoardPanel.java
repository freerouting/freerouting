package app.freerouting.gui;

import app.freerouting.core.RoutingJob;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.ScreenMessages;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
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
 * <ul>
 *   <li><strong>Visual Display:</strong> Renders the routing board with all its components,
 *       traces, vias, and design elements</li>
 *   <li><strong>User Interaction:</strong> Captures and processes mouse and keyboard events
 *       for interactive routing and editing</li>
 *   <li><strong>Viewport Management:</strong> Handles zooming, panning, and scrolling of
 *       the board view</li>
 *   <li><strong>Context Menus:</strong> Provides context-sensitive popup menus for various
 *       operations</li>
 * </ul>
 *
 * <p><strong>Key Components:</strong>
 * <ul>
 *   <li><strong>{@link GuiBoardManager}:</strong> Manages board state and interactive operations</li>
 *   <li><strong>{@link ScreenMessages}:</strong> Displays status messages and coordinate info</li>
 *   <li><strong>Custom Cursor:</strong> Optional crosshair cursor for precise positioning</li>
 *   <li><strong>Popup Menus:</strong> Context-sensitive menus for different interactive states</li>
 * </ul>
 *
 * <p><strong>Mouse Interaction:</strong>
 * <ul>
 *   <li><strong>Left Button:</strong> Primary selection and action (place, route, select)</li>
 *   <li><strong>Middle Button:</strong> Pan/scroll the board view by dragging</li>
 *   <li><strong>Right Button:</strong> Open context-sensitive popup menus</li>
 *   <li><strong>Mouse Wheel:</strong> Zoom in/out centered at mouse position</li>
 * </ul>
 *
 * <p><strong>Keyboard Interaction:</strong>
 * Keyboard events are forwarded to the board handling instance, which interprets them
 * based on the current interactive state (ESC to cancel, numeric keys for layers, etc.).
 *
 * <p><strong>Rendering Pipeline:</strong>
 * The panel delegates rendering to {@link GuiBoardManager#draw(Graphics)}, which handles:
 * <ul>
 *   <li>Board items (traces, vias, pads, components)</li>
 *   <li>Rats nest (incomplete connections)</li>
 *   <li>Interactive state graphics (rubber bands, temporary items)</li>
 *   <li>Clearance violations and design rule indicators</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong>
 * <ul>
 *   <li>Custom crosshair cursor may impact display performance significantly</li>
 *   <li>Panel size is limited to prevent Java rendering exceptions</li>
 *   <li>Viewport scrolling is optimized for responsive panning</li>
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
   * <p>When zooming in, the view is scaled by this factor. When zooming out,
   * the view is scaled by 1/c_zoom_factor (0.5x).
   */
  private static final double c_zoom_factor = 2.0;

  /**
   * Message display component showing status information and coordinates.
   *
   * <p>Displays:
   * <ul>
   *   <li>Current mouse position in board coordinates</li>
   *   <li>Status messages about current operations</li>
   *   <li>Active layer name</li>
   *   <li>Unit of measurement</li>
   * </ul>
   *
   * @see ScreenMessages
   */
  public final ScreenMessages screen_messages;

  /**
   * Parent frame containing this panel and other UI components.
   *
   * <p>Provides access to:
   * <ul>
   *   <li>Menu bar and toolbar</li>
   *   <li>Parameter selection windows</li>
   *   <li>Other dialog windows</li>
   *   <li>Frame-level operations</li>
   * </ul>
   *
   * @see BoardFrame
   */
  public final BoardFrame board_frame;

  /**
   * Scroll pane that contains this panel for viewport management.
   *
   * <p>Used for:
   * <ul>
   *   <li>Getting viewport position and bounds</li>
   *   <li>Programmatic scrolling during panning</li>
   *   <li>Auto-scrolling near edges during drag operations</li>
   * </ul>
   */
  private final JScrollPane scroll_pane;

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
  public JPopupMenu popup_menu_insert_cancel;

  /**
   * Popup menu for copy operations with layer selection.
   *
   * @see PopupMenuCopy
   */
  public PopupMenuCopy popup_menu_copy;

  /**
   * Popup menu for move/drag operations with options.
   *
   * @see PopupMenuMove
   */
  public PopupMenuMove popup_menu_move;

  /**
   * Popup menu displayed during corner item construction.
   *
   * @see PopupMenuCornerItemConstruction
   */
  public JPopupMenu popup_menu_corneritem_construction;

  /**
   * Main popup menu for general board operations.
   *
   * @see PopupMenuMain
   */
  public JPopupMenu popup_menu_main;

  /**
   * Popup menu for dynamic (push & shove) routing operations.
   *
   * @see PopupMenuDynamicRoute
   */
  public PopupMenuDynamicRoute popup_menu_dynamic_route;

  /**
   * Popup menu for stitch routing operations.
   *
   * @see PopupMenuStitchRoute
   */
  public PopupMenuStitchRoute popup_menu_stitch_route;

  /**
   * Popup menu for item selection and inspection operations.
   *
   * @see PopupMenuInspectedItems
   */
  public JPopupMenu popup_menu_select;

  /**
   * Board handling instance managing interactive board operations.
   *
   * <p>Handles:
   * <ul>
   *   <li>Mouse and keyboard event processing</li>
   *   <li>Interactive state management</li>
   *   <li>Board rendering coordination</li>
   *   <li>Autorouting and optimization</li>
   * </ul>
   *
   * @see GuiBoardManager
   */
  GuiBoardManager board_handling;

  /**
   * Screen location where the right mouse button was last clicked.
   *
   * <p>Used for operations that need to reference the popup menu trigger location.
   */
  Point2D right_button_click_location;

  /**
   * AWT Robot for programmatically moving the mouse cursor.
   *
   * <p>Used to reposition the mouse pointer during certain interactive operations
   * (e.g., centering on a point). May be null if Robot creation failed.
   */
  private Robot robot;

  /**
   * Starting position for middle mouse button drag operation.
   *
   * <p>Non-null while middle button panning is in progress. Used to calculate
   * scroll delta during drag.
   */
  private Point middle_drag_position;

  /**
   * Custom crosshair cursor for precise positioning, or null for standard cursor.
   *
   * <p>When enabled, displays a 45-degree crosshair at the mouse position.
   * <strong>Warning:</strong> Using the custom cursor can significantly impact
   * display performance as it requires manual rendering on every mouse move.
   *
   * @see Cursor
   * @see #set_custom_crosshair_cursor(boolean)
   */
  private Cursor custom_cursor;

  /**
   * Creates a new BoardPanel within a GUI application context.
   *
   * <p>Initialization includes:
   * <ul>
   *   <li>Setting up the Robot for programmatic mouse control (if available)</li>
   *   <li>Storing references to parent components</li>
   *   <li>Configuring panel appearance and event listeners</li>
   *   <li>Creating the GuiBoardManager for board operations</li>
   * </ul>
   *
   * <p>The Robot may fail to initialize on some systems (e.g., headless environments),
   * in which case programmatic mouse movement will not be available.
   *
   * @param p_screen_messages the message display component for status information
   * @param p_board_frame the parent frame containing this panel
   * @param globalSettings global application settings
   * @param routingJob the routing job context for this session
   * @param settingsMerger merger for combining different settings sources
   *
   * @see GuiBoardManager
   * @see Robot
   */
  public BoardPanel(ScreenMessages p_screen_messages, BoardFrame p_board_frame, GlobalSettings globalSettings,
      RoutingJob routingJob, SettingsMerger settingsMerger) {
    this.screen_messages = p_screen_messages;
    try {
      // used to be able to change the location of the mouse pointer
      robot = new Robot();
    } catch (AWTException _) {
      FRLogger.warn("unable to create robot");
    }
    this.board_frame = p_board_frame;
    this.globalSettings = globalSettings;
    this.scroll_pane = board_frame.scroll_pane;
    default_init(globalSettings, routingJob, settingsMerger);
  }

  private void default_init(GlobalSettings globalSettings, RoutingJob routingJob, SettingsMerger settingMerger) {
    setLayout(new BorderLayout());

    setBackground(new Color(0, 0, 0));
    setMaximumSize(new Dimension(30000, 20000));
    setMinimumSize(new Dimension(90, 60));
    setPreferredSize(new Dimension(1200, 900));
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent evt) {
        mouse_dragged_action(evt);
      }

      @Override
      public void mouseMoved(MouseEvent evt) {
        mouse_moved_action(evt);
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent evt) {
        board_handling.key_typed_action(evt.getKeyChar());
      }
    });
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
        mouse_clicked_action(evt);
      }

      @Override
      public void mousePressed(MouseEvent evt) {
        mouse_pressed_action(evt);
      }

      @Override
      public void mouseReleased(MouseEvent evt) {
        board_handling.button_released();
        middle_drag_position = null;
      }
    });
    addMouseWheelListener(evt -> board_handling.mouse_wheel_moved(evt.getPoint(), evt.getWheelRotation()));

    board_handling = new GuiBoardManager(this, globalSettings, routingJob, settingMerger);
    setAutoscrolls(true);
    this.setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
  }

  /**
   * Resets the board handling instance for a new routing job.
   *
   * <p>This method safely transitions to a new routing job by:
   * <ol>
   *   <li>Preserving the settings merger reference</li>
   *   <li>Disposing the old board handling instance (cleanup)</li>
   *   <li>Creating a new GuiBoardManager with the new job</li>
   * </ol>
   *
   * <p>Used when:
   * <ul>
   *   <li>Loading a different board design</li>
   *   <li>Restarting the routing session</li>
   *   <li>Switching between multiple boards</li>
   * </ul>
   *
   * @param routingJob the new routing job to initialize
   *
   * @see GuiBoardManager#dispose()
   */
  public void reset_board_handling(RoutingJob routingJob) {
    // Save the settingsMerger reference before disposing the old instance
    SettingsMerger settingsMerger = (board_handling != null) ? board_handling.settingsMerger : null;

    // Dispose the old board_handling instance before creating a new one
    if (board_handling != null) {
      board_handling.dispose();
    }

    board_handling = new GuiBoardManager(this, globalSettings, routingJob, settingsMerger);
  }

  /**
   * Initializes all popup menus used throughout the interactive session.
   *
   * <p>Creates popup menu instances for different contexts:
   * <ul>
   *   <li><strong>Main menu:</strong> General board operations</li>
   *   <li><strong>Routing menus:</strong> Dynamic route and stitch route options</li>
   *   <li><strong>Construction menus:</strong> Corner item and insert/cancel operations</li>
   *   <li><strong>Selection menus:</strong> Operations on selected items</li>
   *   <li><strong>Edit menus:</strong> Copy and move operations</li>
   * </ul>
   *
   * <p>Should be called after the board frame is fully initialized and before
   * interactive operations begin.
   *
   * @see PopupMenuMain
   * @see PopupMenuDynamicRoute
   * @see PopupMenuStitchRoute
   */
  void create_popup_menus() {
    popup_menu_main = new PopupMenuMain(this.board_frame);
    popup_menu_dynamic_route = new PopupMenuDynamicRoute(this.board_frame);
    popup_menu_stitch_route = new PopupMenuStitchRoute(this.board_frame);
    popup_menu_corneritem_construction = new PopupMenuCornerItemConstruction(this.board_frame);
    popup_menu_select = new PopupMenuInspectedItems(this.board_frame);
    popup_menu_insert_cancel = new PopupMenuInsertCancel(this.board_frame);
    popup_menu_copy = new PopupMenuCopy(this.board_frame);
    popup_menu_move = new PopupMenuMove(this.board_frame);
  }

  /**
   * Handles mouse wheel zoom events at the specified screen position.
   *
   * <p>Zoom behavior:
   * <ul>
   *   <li><strong>Scroll down:</strong> Zoom out (decrease magnification)</li>
   *   <li><strong>Scroll up:</strong> Zoom in (increase magnification)</li>
   *   <li><strong>Zoom center:</strong> Mouse position (stays fixed relative to board)</li>
   * </ul>
   *
   * <p>The method applies a zoom factor of 10% per wheel notch, with a minimum
   * zoom factor of 0.5 to prevent excessive zoom out.
   *
   * <p>Ignored if middle mouse button panning is in progress or wheel rotation is zero.
   *
   * @param p_point the screen position to center the zoom operation on
   * @param p_wheel_rotation the wheel rotation amount (negative for zoom in, positive for zoom out)
   *
   * @see #zoom(double, Point2D)
   */
  public void zoom_with_mouse_wheel(Point2D p_point, int p_wheel_rotation) {
    if (this.middle_drag_position != null || p_wheel_rotation == 0) {
      return; // scrolling with the middle mouse button in progress
    }
    double zoom_factor = 1 - 0.1 * p_wheel_rotation;
    zoom_factor = Math.max(zoom_factor, 0.5);
    zoom(zoom_factor, p_point);
  }

  private void mouse_pressed_action(MouseEvent evt) {
    if (evt.getButton() == 1) {
      board_handling.mouse_pressed(evt.getPoint());
    } else if (evt.getButton() == 2 && middle_drag_position == null) {
      middle_drag_position = new Point(evt.getPoint());
    }
  }

  private void mouse_dragged_action(MouseEvent evt) {
    if (middle_drag_position != null) {
      scroll_middle_mouse(evt);
    } else {
      board_handling.mouse_dragged(evt.getPoint());
      scroll_near_border(evt);
    }
  }

  private void mouse_moved_action(MouseEvent p_evt) {
    this.requestFocusInWindow(); // to enable keyboard aliases
    if (board_handling != null) {
      board_handling.mouse_moved(p_evt.getPoint());
    }
    if (this.custom_cursor != null) {
      this.custom_cursor.set_location(p_evt.getPoint());
      this.repaint();
    }
  }

  private void mouse_clicked_action(MouseEvent evt) {
    if (evt.getButton() == 1) {
      board_handling.left_button_clicked(evt.getPoint());
    } else if (evt.getButton() == 3) {
      JPopupMenu curr_menu = board_handling.get_current_popup_menu();
      if (curr_menu != null) {
        int curr_x = evt.getX();
        int curr_y = evt.getY();
        if (false) {
          int dx = curr_menu.getWidth();
          if (dx <= 0) {
            // force the width to be calculated
            curr_menu.show(this, curr_x, curr_y);
            dx = curr_menu.getWidth();
          }
          curr_x -= dx;
        }
        curr_menu.show(this, curr_x, curr_y);
      }
      right_button_click_location = evt.getPoint();
    }
  }

  /**
   * Renders the board panel including all visual elements.
   *
   * <p>Rendering pipeline:
   * <ol>
   *   <li>Call super to paint the panel background</li>
   *   <li>Delegate board drawing to {@link GuiBoardManager#draw(Graphics)}</li>
   *   <li>Draw custom cursor overlay if enabled</li>
   * </ol>
   *
   * <p>The board manager handles rendering of:
   * <ul>
   *   <li>Board geometry and items</li>
   *   <li>Rats nest and violations</li>
   *   <li>Interactive state graphics</li>
   * </ul>
   *
   * @param p_g the graphics context for rendering
   *
   * @see GuiBoardManager#draw(Graphics)
   */
  @Override
  public void paintComponent(Graphics p_g) {
    super.paintComponent(p_g);
    if (board_handling != null) {
      board_handling.draw(p_g);
    }
    if (this.custom_cursor != null) {
      this.custom_cursor.draw(p_g);
    }
  }

  /**
   * Returns the current viewport position in panel coordinates.
   *
   * <p>The viewport position represents the top-left corner of the visible
   * area within the scrollable panel.
   *
   * @return the viewport position as a Point
   *
   * @see #set_viewport_position(Point)
   */
  public Point get_viewport_position() {
    JViewport viewport = scroll_pane.getViewport();
    return viewport.getViewPosition();
  }

  /**
   * Sets the viewport position to the specified point.
   *
   * <p>Scrolls the panel so that the specified point becomes the top-left
   * corner of the visible viewport area.
   *
   * @param p_position the new viewport position
   *
   * @see #get_viewport_position()
   */
  void set_viewport_position(Point p_position) {
    JViewport viewport = scroll_pane.getViewport();
    viewport.setViewPosition(p_position);
  }

  /**
   * Zooms in at the specified screen position by the default zoom factor.
   *
   * <p>Increases the board magnification by {@link #c_zoom_factor} (2x),
   * keeping the specified point fixed in screen coordinates.
   *
   * @param p_position the screen position to center zoom on
   *
   * @see #zoom_out(Point2D)
   * @see #zoom(double, Point2D)
   */
  public void zoom_in(Point2D p_position) {
    zoom(c_zoom_factor, p_position);
  }

  /**
   * Zooms out at the specified screen position by the inverse zoom factor.
   *
   * <p>Decreases the board magnification by 1/{@link #c_zoom_factor} (0.5x),
   * keeping the specified point fixed in screen coordinates.
   *
   * @param p_position the screen position to center zoom on
   *
   * @see #zoom_in(Point2D)
   * @see #zoom(double, Point2D)
   */
  public void zoom_out(Point2D p_position) {
    double zoom_factor = 1 / c_zoom_factor;
    zoom(zoom_factor, p_position);
  }

  /**
   * Zooms to fit a rectangular frame defined by two corner points.
   *
   * <p>Calculates the appropriate zoom factor to display the entire rectangle
   * within the viewport, then centers the view on the rectangle's midpoint.
   *
   * <p>Used for "zoom to selection" and "zoom to frame" operations where the
   * user defines a region of interest.
   *
   * @param p_position1 first corner of the rectangle to zoom to
   * @param p_position2 opposite corner of the rectangle
   *
   * @see #zoom(double, Point2D)
   */
  public void zoom_frame(Point2D p_position1, Point2D p_position2) {
    double width_of_zoom_frame = Math.abs(p_position1.getX() - p_position2.getX());
    double height_of_zoom_frame = Math.abs(p_position1.getY() - p_position2.getY());

    double center_x = Math.min(p_position1.getX(), p_position2.getX()) + (width_of_zoom_frame / 2);
    double center_y = Math.min(p_position1.getY(), p_position2.getY()) + (height_of_zoom_frame / 2);

    Point2D center_point = new Point2D.Double(center_x, center_y);

    Rectangle display_rect = get_viewport_bounds();

    double width_factor = display_rect.getWidth() / width_of_zoom_frame;
    double height_factor = display_rect.getHeight() / height_of_zoom_frame;

    Point2D changed_location = zoom(Math.min(width_factor, height_factor), center_point);
    set_viewport_center(changed_location);
  }

  /**
   * Centers the display on the specified board position.
   *
   * <p>This method:
   * <ol>
   *   <li>Adjusts the viewport to center on the specified point</li>
   *   <li>Calculates the mouse cursor offset from the new center</li>
   *   <li>Moves the mouse cursor to maintain visual continuity</li>
   *   <li>Triggers a repaint to update the display</li>
   * </ol>
   *
   * <p>Useful for "go to" operations and centering on specific board features.
   *
   * @param p_new_center the board position to center the view on
   *
   * @see #set_viewport_center(Point2D)
   * @see #move_mouse(Point2D)
   */
  public void center_display(Point2D p_new_center) {
    Point delta = set_viewport_center(p_new_center);
    Point2D new_center = get_viewport_center();
    Point new_mouse_location = new Point((int) (new_center.getX() - delta.getX()),
        (int) (new_center.getY() - delta.getY()));
    move_mouse(new_mouse_location);
    repaint();

  }

  /**
   * Returns the center point of the current viewport in panel coordinates.
   *
   * <p>Calculates the center by adding half the viewport dimensions to the
   * viewport position.
   *
   * @return the viewport center as a Point2D
   *
   * @see #get_viewport_position()
   * @see #get_viewport_bounds()
   */
  public Point2D get_viewport_center() {
    Point pos = get_viewport_position();
    Rectangle display_rect = get_viewport_bounds();
    return new Point2D.Double(pos.getX() + display_rect.getCenterX(), pos.getY() + display_rect.getCenterY());
  }

  /**
   * Zooms the board view by the specified factor centered at the given location.
   *
   * <p>This method:
   * <ol>
   *   <li>Scales the panel size by the zoom factor</li>
   *   <li>Adjusts the coordinate transform in the graphics context</li>
   *   <li>Repositions the viewport to keep p_location fixed on screen</li>
   *   <li>Returns the adjusted cursor position after zoom</li>
   * </ol>
   *
   * <p><strong>Zoom Factor Examples:</strong>
   * <ul>
   *   <li>{@code 2.0}: Zoom in 2x (200%)</li>
   *   <li>{@code 0.5}: Zoom out 2x (50%)</li>
   *   <li>{@code 1.0}: No change</li>
   * </ul>
   *
   * <p><strong>Size Limit:</strong>
   * Panel size is capped at 10,000,000 pixels to prevent Java rendering exceptions
   * on large zooms.
   *
   * @param p_factor the zoom multiplication factor (>1 zooms in, <1 zooms out)
   * @param p_location the screen position that should remain fixed during zoom
   * @return the adjusted cursor location after zoom and viewport adjustment
   *
   * @see #zoom_in(Point2D)
   * @see #zoom_out(Point2D)
   */
  public Point2D zoom(double p_factor, Point2D p_location) {
    final int max_panel_size = 10000000;
    Dimension old_size = this.getSize();
    Point2D old_center = get_viewport_center();

    if (p_factor > 1 && Math.max(old_size.getWidth(), old_size.getHeight()) >= max_panel_size) {
      return p_location; // to prevent an sun.dc.pr.PRException, which I do not know, how to handle;
      // maybe a bug in Java.
    }
    int new_width = (int) Math.round(p_factor * old_size.getWidth());
    int new_height = (int) Math.round(p_factor * old_size.getHeight());
    Dimension new_size = new Dimension(new_width, new_height);
    board_handling.graphics_context.change_panel_size(new_size);
    setPreferredSize(new_size);
    setSize(new_size);
    revalidate();

    Point2D new_cursor = new Point2D.Double(p_location.getX() * p_factor, p_location.getY() * p_factor);
    double dx = new_cursor.getX() - p_location.getX();
    double dy = new_cursor.getY() - p_location.getY();
    Point2D new_center = new Point2D.Double(old_center.getX() + dx, old_center.getY() + dy);
    Point2D adjustment_vector = set_viewport_center(new_center);
    repaint();
    Point2D adjusted_new_cursor = new Point2D.Double(new_cursor.getX() + adjustment_vector.getX() + 0.5,
        new_cursor.getY() + adjustment_vector.getY() + 0.5);
    return adjusted_new_cursor;
  }

  /**
   * Returns the rectangular bounds of the current viewport.
   *
   * <p>The viewport bounds represent the visible area of the panel within
   * the scroll pane, in panel coordinates.
   *
   * @return the viewport bounds rectangle
   *
   * @see JScrollPane#getViewportBorderBounds()
   */
  Rectangle get_viewport_bounds() {
    return scroll_pane.getViewportBorderBounds();
  }

  /**
   * Sets the viewport center to the specified point with boundary adjustments.
   *
   * <p>Attempts to center the viewport on p_point, but adjusts if the point is
   * near the panel edges to keep the viewport within valid bounds. Returns the
   * adjustment vector representing how much the requested center had to be shifted.
   *
   * <p>The adjustment ensures:
   * <ul>
   *   <li>Viewport stays within panel boundaries</li>
   *   <li>No part of the viewport extends beyond the panel</li>
   *   <li>Smooth scrolling behavior near edges</li>
   * </ul>
   *
   * @param p_point the desired center point in panel coordinates
   * @return the adjustment vector (delta from requested to actual position)
   *
   * @see #get_viewport_center()
   * @see #set_viewport_position(Point)
   */
  Point set_viewport_center(Point2D p_point) {
    Rectangle display_rect = get_viewport_bounds();
    double x_corner = p_point.getX() - display_rect.getWidth() / 2;
    double y_corner = p_point.getY() - display_rect.getHeight() / 2;
    Dimension panel_size = getSize();
    double adjusted_x_corner = Math.min(x_corner, panel_size.getWidth());
    adjusted_x_corner = Math.max(x_corner, 0);
    double adjusted_y_corner = Math.min(y_corner, panel_size.getHeight());
    adjusted_y_corner = Math.max(y_corner, 0);
    Point new_position = new Point((int) adjusted_x_corner, (int) adjusted_y_corner);
    set_viewport_position(new_position);
    Point adjustment_vector = new Point((int) (adjusted_x_corner - x_corner), (int) (adjusted_y_corner - y_corner));
    return adjustment_vector;
  }

  /**
   * Selects the specified signal layer in the parameter selection window and updates menus.
   *
   * <p>This method:
   * <ul>
   *   <li>Updates the layer selection in the parameter window</li>
   *   <li>Disables the selected layer in routing popup menus (can't route to current layer)</li>
   *   <li>Synchronizes UI state across all layer-dependent controls</li>
   * </ul>
   *
   * @param p_signal_layer_no the signal layer number to select (0-based index)
   *
   * @see BoardFrame#select_parameter_window
   */
  public void set_selected_signal_layer(int p_signal_layer_no) {
    if (this.board_frame.select_parameter_window != null) {
      this.board_frame.select_parameter_window.select(p_signal_layer_no);
      this.popup_menu_dynamic_route.disable_layer_item(p_signal_layer_no);
      this.popup_menu_stitch_route.disable_layer_item(p_signal_layer_no);
      this.popup_menu_copy.disable_layer_item(p_signal_layer_no);
    }
  }

  /**
   * Initializes color table listeners to respond to color changes.
   *
   * <p>Sets up listeners on both item and other color tables that will:
   * <ul>
   *   <li>Update the panel background when colors change</li>
   *   <li>Trigger repaints to reflect new color schemes</li>
   *   <li>Maintain visual consistency with color preferences</li>
   * </ul>
   *
   * <p>Should be called after the graphics context is fully initialized.
   *
   * @see ColorTableListener
   */
  void init_colors() {
    board_handling.graphics_context.item_color_table.addTableModelListener(new ColorTableListener());
    board_handling.graphics_context.other_color_table.addTableModelListener(new ColorTableListener());
    setBackground(board_handling.graphics_context.get_background_color());
  }

  private void scroll_near_border(MouseEvent p_evt) {
    final int border_dist = 50;
    Rectangle r = new Rectangle(p_evt.getX() - border_dist, p_evt.getY() - border_dist, 2 * border_dist,
        2 * border_dist);
    ((JPanel) p_evt.getSource()).scrollRectToVisible(r);
  }

  private void scroll_middle_mouse(MouseEvent p_evt) {
    double delta_x = middle_drag_position.x - p_evt.getX();
    double delta_y = middle_drag_position.y - p_evt.getY();

    Point view_position = get_viewport_position();

    double x = view_position.x + delta_x;
    double y = view_position.y + delta_y;

    Dimension panel_size = this.getSize();
    x = Math.min(x, panel_size.getWidth() - this.get_viewport_bounds().getWidth());
    y = Math.min(y, panel_size.getHeight() - this.get_viewport_bounds().getHeight());

    x = Math.max(x, 0);
    y = Math.max(y, 0);

    Point p = new Point((int) x, (int) y);
    set_viewport_position(p);
  }

  /**
   * Programmatically moves the mouse cursor to the specified panel location.
   *
   * <p>Converts the panel coordinates to absolute screen coordinates, accounting
   * for frame position and viewport scrolling, then uses the Robot to move the
   * system mouse cursor.
   *
   * <p>Does nothing if Robot initialization failed during construction.
   *
   * @param p_location the target position in panel coordinates
   *
   * @see Robot#mouseMove(int, int)
   * @see #center_display(Point2D)
   */
  public void move_mouse(Point2D p_location) {
    if (robot == null) {
      return;
    }
    Point absolute_panel_location = board_frame.absolute_panel_location();
    Point view_position = get_viewport_position();
    int x = (int) Math.round(absolute_panel_location.getX() - view_position.getX() + p_location.getX()) + 1;
    int y = (int) Math.round(absolute_panel_location.getY() - view_position.getY() + p_location.getY() + 1);
    robot.mouseMove(x, y);
  }

  /**
   * Enables or disables the custom crosshair cursor.
   *
   * <p>When enabled, displays a 45-degree crosshair cursor for precise positioning.
   * The custom cursor is drawn as an overlay on the board panel.
   *
   * <p><strong>Performance Warning:</strong> Using the custom cursor can significantly
   * slow down display performance because it requires manual rendering and repaint
   * on every mouse movement. Use only when precise cursor positioning is critical.
   *
   * @param p_value true to enable custom crosshair, false for standard cursor
   *
   * @see Cursor#get_45_degree_cross_hair_cursor()
   * @see #is_custom_cross_hair_cursor()
   */
  public void set_custom_crosshair_cursor(boolean p_value) {
    if (p_value) {
      this.custom_cursor = Cursor.get_45_degree_cross_hair_cursor();
    } else {
      this.custom_cursor = null;
    }
    board_frame.refresh_windows();
    repaint();
  }

  /**
   * Checks if the custom crosshair cursor is currently enabled.
   *
   * <p>Returns true if the custom 45-degree crosshair cursor is being used,
   * false if the standard system cursor is active.
   *
   * @return true if custom crosshair cursor is enabled, false otherwise
   *
   * @see #set_custom_crosshair_cursor(boolean)
   */
  public boolean is_custom_cross_hair_cursor() {
    return this.custom_cursor != null;
  }

  private class ColorTableListener implements TableModelListener {

    @Override
    public void tableChanged(TableModelEvent p_event) {
      // redisplay board because some colors have changed.
      setBackground(board_handling.graphics_context.get_background_color());
      repaint();
    }
  }
}