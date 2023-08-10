package app.freerouting.gui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Creates the display menu of a board frame. */
public class BoardMenuDisplay extends JMenu {
  private final BoardFrame board_frame;
  private final ResourceBundle resources;

  /** Creates a new instance of BoardDisplayMenu */
  private BoardMenuDisplay(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuDisplay", p_board_frame.get_locale());
  }

  /** Returns a new display menu for the board frame. */
  public static BoardMenuDisplay get_instance(BoardFrame p_board_frame) {
    final BoardMenuDisplay display_menu = new BoardMenuDisplay(p_board_frame);
    display_menu.setText(display_menu.resources.getString("display"));

    JMenuItem itemvisibility = new JMenuItem();
    itemvisibility.setText(display_menu.resources.getString("object_visibility"));
    itemvisibility.setToolTipText(display_menu.resources.getString("object_visibility_tooltip"));
    itemvisibility.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            display_menu.board_frame.object_visibility_window.setVisible(true);
          }
        });

    display_menu.add(itemvisibility);

    JMenuItem layervisibility = new JMenuItem();
    layervisibility.setText(display_menu.resources.getString("layer_visibility"));
    layervisibility.setToolTipText(display_menu.resources.getString("layer_visibility_tooltip"));
    layervisibility.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            display_menu.board_frame.layer_visibility_window.setVisible(true);
          }
        });

    display_menu.add(layervisibility);

    JMenuItem colors = new JMenuItem();
    colors.setText(display_menu.resources.getString("colors"));
    colors.setToolTipText(display_menu.resources.getString("colors_tooltip"));
    colors.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            display_menu.board_frame.color_manager.setVisible(true);
          }
        });

    display_menu.add(colors);

    JMenuItem miscellaneous = new JMenuItem();
    miscellaneous.setText(display_menu.resources.getString("miscellaneous"));
    miscellaneous.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            display_menu.board_frame.display_misc_window.setVisible(true);
          }
        });

    display_menu.add(miscellaneous);

    return display_menu;
  }
}
