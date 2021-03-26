/*
 *   Copyright (C) 2014  Alfons Wirtz
 *   website www.freerouting.net
 *
 *   Copyright (C) 2017 Michael Hoffer <info@michaelhoffer.de>
 *   Website www.freerouting.mihosoft.eu
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/>
 *   for more details.
 *
 * BoardPanel.java
 *
 * Created on 3. Oktober 2002, 18:47
 */

package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.interactive.ActivityReplayFileScope;
import eu.mihosoft.freerouting.interactive.BoardHandling;
import eu.mihosoft.freerouting.interactive.ScreenMessages;
import eu.mihosoft.freerouting.logger.FRLogger;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.geom.*;
import java.util.Locale;


/**
 * Panel containing the graphical representation of a routing board.
 *
 * @author Alfons Wirtz
 */
public class BoardPanel extends JPanel {

    public JPopupMenu popupMenuInsertCancel;
    public PopupMenuCopy popupMenuCopy;
    public PopupMenuMove popupMenuMove;
    public JPopupMenu popupMenuCornerItemConstruction;
    public JPopupMenu popupMenuMain;
    public PopupMenuDynamicRoute popupMenuDynamicRoute;
    public PopupMenuStitchRoute popupMenuStitchRoute;
    public JPopupMenu popupMenuSelect;

    public final ScreenMessages screenMessages;

    public final BoardFrame boardFrame;

    BoardHandling boardHandling = null;

    private final JScrollPane scrollPane;
    Point2D rightButtonClickLocation = null;
    private static final double C_ZOOM_FACTOR = 2.0;
    private Robot robot;
    private Point middleDragPosition = null;

    /**
     * Defines the appearance of the custom custom_cursor in the board panel. Null, if the standard custom_cursor is
     * used.
     */
    private Cursor customCursor = null;

    /**
     * Creates a new BoardPanel in an Application
     */
    public BoardPanel(
            final ScreenMessages screenMessages,
            final BoardFrame boardFrame,
            final Locale locale
    ) {
        this.screenMessages = screenMessages;
        try {
            // used to be able to change the location of the mouse pointer
            robot = new Robot();
        } catch (AWTException e) {
            FRLogger.warn("unable to create robot");
            robot = null;
        }
        this.boardFrame = boardFrame;
        this.scrollPane = this.boardFrame.scroll_pane;
        defaultInit(locale);
    }

    public BoardHandling getBoardHandling() {
        return boardHandling;
    }

    private void defaultInit(Locale locale) {
        setLayout(new BorderLayout());

        setBackground(new Color(0, 0, 0));
        setMaximumSize(new Dimension(30000, 20000));
        setMinimumSize(new Dimension(90, 60));
        setPreferredSize(new Dimension(1200, 900));
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent evt) {
                mouseDraggedAction(evt);
            }

            public void mouseMoved(MouseEvent evt) {
                mouseMovedAction(evt);
            }
        });
        addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent evt) {
                boardHandling.key_typed_action(evt.getKeyChar());
            }
        });
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                mouseClickedAction(evt);
            }

            public void mousePressed(MouseEvent evt) {
                mousePressedAction(evt);
            }

            public void mouseReleased(MouseEvent evt) {
                boardHandling.button_released();
                middleDragPosition = null;
            }
        });
        addMouseWheelListener(evt -> boardHandling.mouseWheelMoved(evt.getWheelRotation()));
        boardHandling = new BoardHandling(this, locale);
        setAutoscrolls(true);
        setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
    }

    void createPopupMenus() {
        popupMenuMain = new PopupMenuMain(boardFrame);
        popupMenuDynamicRoute = new PopupMenuDynamicRoute(boardFrame);
        popupMenuStitchRoute = new PopupMenuStitchRoute(boardFrame);
        popupMenuCornerItemConstruction = new PupupMenuCornerItemConstruction(boardFrame);
        popupMenuSelect = new PopupMenuSelectedItems(boardFrame);
        popupMenuInsertCancel = new PopupMenuInsertCancel(boardFrame);
        popupMenuCopy = new PopupMenuCopy(boardFrame);
        popupMenuMove = new PopupMenuMove(boardFrame);
    }


    /**
     * @param point2D
     * @param wheelRotation
     */
    public void zoomWithMouseWheel(Point2D point2D, int wheelRotation) {
        if (middleDragPosition != null || wheelRotation == 0) {
            return; // scrolling with the middle mouse butten in progress
        }
        double zoomFactor = 1 - 0.1 * wheelRotation;
        zoomFactor = Math.max(zoomFactor, 0.5);
        zoom(zoomFactor, point2D);
    }

    private void mousePressedAction(MouseEvent evt) {
        if (evt.getButton() == 1) {
            boardHandling.mousePressed(evt.getPoint());
        } else if (evt.getButton() == 2 && middleDragPosition == null) {
            middleDragPosition = new Point(evt.getPoint());
        }
    }

    private void mouseDraggedAction(MouseEvent evt) {
        if (middleDragPosition != null) {
            scrollMiddleMouse(evt);
        } else {
            boardHandling.mouse_dragged(evt.getPoint());
            scrollNearBorder(evt);
        }
    }

    private void mouseMovedAction(MouseEvent p_evt) {
        this.requestFocusInWindow(); // to enable keyboard aliases
        if (boardHandling != null) {
            boardHandling.mouse_moved(p_evt.getPoint());
        }
        if (customCursor != null) {
            customCursor.set_location(p_evt.getPoint());
            repaint();
        }
    }

    private void mouseClickedAction(MouseEvent evt) {
        if (evt.getButton() == 1) {
            boardHandling.leftButtonClicked(evt.getPoint());
        } else if (evt.getButton() == 3) {
            JPopupMenu curr_menu = boardHandling.get_current_popup_menu();
            if (curr_menu != null) {
                int curr_x = evt.getX();
                int curr_y = evt.getY();
                if (curr_menu == popupMenuDynamicRoute && boardFrame.is_web_start) {
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
            rightButtonClickLocation = evt.getPoint();
        }
    }

    /**
     * overwrites the paintComponent method to draw the routing board
     */
    public void paintComponent(Graphics p_g) {
        super.paintComponent(p_g);
        if (boardHandling != null) {
            boardHandling.draw(p_g);
        }
        if (customCursor != null) {
            customCursor.draw(p_g);
        }
    }

    /**
     * Returns the position of the viewport
     */
    public Point getViewportPosition() {
        JViewport viewport = scrollPane.getViewport();
        return viewport.getViewPosition();
    }

    /**
     * Sets the position of the viewport
     */
    void setViewportPosition(Point point) {
        JViewport viewport = scrollPane.getViewport();
        viewport.setViewPosition(point);
    }

    /**
     * zooms in at position
     */
    public void zoomIn(Point2D position) {
        zoom(C_ZOOM_FACTOR, position);
    }

    /**
     * zooms out at p_position
     */
    public void zoomOut(Point2D position) {
        double zoom_factor = 1 / C_ZOOM_FACTOR;
        zoom(zoom_factor, position);
    }

    /**
     * zooms to frame
     */
    public void zoomFrame(Point2D position1, Point2D position2) {
        double width_of_zoom_frame = Math.abs(position1.getX() - position2.getX());
        double height_of_zoom_frame = Math.abs(position1.getY() - position2.getY());

        double center_x = Math.min(position1.getX(), position2.getX()) + (width_of_zoom_frame / 2);
        double center_y = Math.min(position1.getY(), position2.getY()) + (height_of_zoom_frame / 2);

        Point2D center_point = new Point2D.Double(center_x, center_y);

        Rectangle display_rect = getViewportBounds();

        double width_factor = display_rect.getWidth() / width_of_zoom_frame;
        double height_factor = display_rect.getHeight() / height_of_zoom_frame;

        Point2D changed_location = zoom(Math.min(width_factor, height_factor), center_point);
        setViewportCenter(changed_location);
    }

    public void centerDisplay(Point2D newCenter) {
        Point delta = setViewportCenter(newCenter);
        Point2D new_center = getViewportCenter();
        Point new_mouse_location =
                new Point((int) (new_center.getX() - delta.getX()), (int) (new_center.getY() - delta.getY()));
        moveMouse(new_mouse_location);
        repaint();
        this.boardHandling.activityReplayFile.start_scope(ActivityReplayFileScope.CENTER_DISPLAY);
        eu.mihosoft.freerouting.geometry.planar.FloatPoint curr_corner = new eu.mihosoft.freerouting.geometry.planar.FloatPoint(newCenter.getX(), newCenter.getY());
        this.boardHandling.activityReplayFile.add_corner(curr_corner);
    }


    public Point2D getViewportCenter() {
        Point pos = getViewportPosition();
        Rectangle display_rect = getViewportBounds();
        return new Point2D.Double(pos.getX() + display_rect.getCenterX(), pos.getY() + display_rect.getCenterY());
    }


    /**
     * zooms the content of the board by p_factor Returns the change of the cursor location
     */
    public Point2D zoom(double p_factor, Point2D p_location) {
        final int max_panel_size = 10000000;
        Dimension old_size = this.getSize();
        Point2D old_center = getViewportCenter();

        if (p_factor > 1 && Math.max(old_size.getWidth(), old_size.getHeight()) >= max_panel_size) {
            return p_location; // to prevent an sun.dc.pr.PRException, which I do not know, how to handle; maybe a bug in Java.
        }
        int new_width = (int) Math.round(p_factor * old_size.getWidth());
        int new_height = (int) Math.round(p_factor * old_size.getHeight());
        Dimension new_size = new Dimension(new_width, new_height);
        boardHandling.graphicsContext.change_panel_size(new_size);
        setPreferredSize(new_size);
        setSize(new_size);
        revalidate();

        Point2D new_cursor = new Point2D.Double(p_location.getX() * p_factor, p_location.getY() * p_factor);
        double dx = new_cursor.getX() - p_location.getX();
        double dy = new_cursor.getY() - p_location.getY();
        Point2D new_center = new Point2D.Double(old_center.getX() + dx, old_center.getY() + dy);
        Point2D adjustment_vector = setViewportCenter(new_center);
        repaint();
        Point2D adjusted_new_cursor = new Point2D.Double(new_cursor.getX() + adjustment_vector.getX() + 0.5, new_cursor.getY() + adjustment_vector.getY() + 0.5);
        return adjusted_new_cursor;
    }

    /**
     * Returns the viewport bounds of the scroll pane
     */
    Rectangle getViewportBounds() {
        return scrollPane.getViewportBorderBounds();
    }

    /**
     * Sets the viewport center to p_point. Adjust the result, if p_point is near the border of the viewport. Returns
     * the adjustment vector
     */
    Point setViewportCenter(Point2D p_point) {
        Rectangle display_rect = getViewportBounds();
        double x_corner = p_point.getX() - display_rect.getWidth() / 2;
        double y_corner = p_point.getY() - display_rect.getHeight() / 2;
        Dimension panel_size = getSize();
        double adjusted_x_corner = Math.min(x_corner, panel_size.getWidth());
        adjusted_x_corner = Math.max(x_corner, 0);
        double adjusted_y_corner = Math.min(y_corner, panel_size.getHeight());
        adjusted_y_corner = Math.max(y_corner, 0);
        Point new_position = new Point((int) adjusted_x_corner, (int) adjusted_y_corner);
        setViewportPosition(new_position);
        Point adjustment_vector =
                new Point((int) (adjusted_x_corner - x_corner), (int) (adjusted_y_corner - y_corner));
        return adjustment_vector;
    }

    /**
     * Selects the p_signal_layer_no-th layer in the select_parameter_window.
     */
    public void setSelectedSignalLayer(int p_signal_layer_no) {
        if (boardFrame.select_parameter_window != null) {
            boardFrame.select_parameter_window.select(p_signal_layer_no);
            popupMenuDynamicRoute.disable_layer_item(p_signal_layer_no);
            popupMenuStitchRoute.disable_layer_item(p_signal_layer_no);
            popupMenuCopy.disable_layer_item(p_signal_layer_no);
        }
    }

    void initColors() {
        boardHandling.graphicsContext.item_color_table.addTableModelListener(new ColorTableListener());
        boardHandling.graphicsContext.other_color_table.addTableModelListener(new ColorTableListener());
        setBackground(boardHandling.graphicsContext.get_background_color());
    }

    private void scrollNearBorder(MouseEvent p_evt) {
        final int border_dist = 50;
        Rectangle r = new Rectangle(
                p_evt.getX() - border_dist,
                p_evt.getY() - border_dist,
                2 * border_dist,
                2 * border_dist
        );
        ((JPanel) p_evt.getSource()).scrollRectToVisible(r);
    }

    private void scrollMiddleMouse(MouseEvent p_evt) {
        double delta_x = middleDragPosition.x - p_evt.getX();
        double delta_y = middleDragPosition.y - p_evt.getY();

        Point view_position = getViewportPosition();

        double x = (view_position.x + delta_x);
        double y = (view_position.y + delta_y);

        Dimension panel_size = this.getSize();
        x = Math.min(x, panel_size.getWidth() - this.getViewportBounds().getWidth());
        y = Math.min(y, panel_size.getHeight() - this.getViewportBounds().getHeight());

        x = Math.max(x, 0);
        y = Math.max(y, 0);

        Point p = new Point((int) x, (int) y);
        setViewportPosition(p);
    }

    public void moveMouse(Point2D p_location) {
        if (robot == null) {
            return;
        }
        Point absolute_panel_location = boardFrame.absolute_panel_location();
        Point viewPosition = getViewportPosition();
        int x = (int) Math.round(absolute_panel_location.getX() - viewPosition.getX() + p_location.getX()) + 1;
        int y = (int) Math.round(absolute_panel_location.getY() - viewPosition.getY() + p_location.getY() + 1);
        robot.mouseMove(x, y);
    }

    /**
     * If value is true, the custom crosshair cursor will be used in display. Otherwise the standard Cursor will be
     * used. Using the custom cursor may slow down the display performance a lot.
     */
    public void setCustomCrosshairCursor(boolean value) {
        if (value) {
            customCursor = Cursor.get45DegreeCrossHairCursor();
        } else {
            customCursor = null;
        }
        boardFrame.refreshWindows();
        repaint();
    }

    /**
     * If the result is true, the custom crosshair cursor will be used in display. Otherwise the standard Cursor will be
     * used. Using the custom cursor may slow down the display performance a lot.
     */
    public boolean isCustomCrossHairCursor() {
        return customCursor != null;
    }


    private class ColorTableListener implements TableModelListener {
        public void tableChanged(TableModelEvent p_event) {
            //redisplay board because some colors have changed.
            setBackground(boardHandling.getGraphicsContext()
                                  .get_background_color()
            );
            repaint();
        }
    }
}
