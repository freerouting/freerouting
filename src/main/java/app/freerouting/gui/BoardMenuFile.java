package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import app.freerouting.core.RoutingJob;

/**
 * Creates the file menu of a board frame.
 */
public class BoardMenuFile extends JMenu {

  public final JMenuItem file_save_as_menuitem;
  private final TextManager tm;
  private final List<Consumer<File>> openEventListeners = new ArrayList<>();
  private final List<Consumer<File>> saveAsEventListeners = new ArrayList<>();

  /**
   * Creates a new instance of BoardFileMenu
   */
  public BoardMenuFile(BoardFrame board_frame) {
    tm = new TextManager(this.getClass(), board_frame.get_locale());

    setText(tm.getText("file"));

    // File / Open...
    JMenuItem file_open_menuitem = new JMenuItem();
    file_open_menuitem.setText(tm.getText("open"));
    file_open_menuitem.setToolTipText(tm.getText("open_tooltip"));
    file_open_menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    file_open_menuitem.addActionListener(_ -> {
      File selected_file = RoutingJob.showOpenDialog(globalSettings.guiSettings.inputDirectory, board_frame);

      openEventListeners.forEach(listener -> listener.accept(selected_file));
    });
    file_open_menuitem
        .addActionListener(_ -> FRAnalytics.buttonClicked("file_open_menuitem", file_open_menuitem.getText()));
    add(file_open_menuitem);

    // File / Save as...
    file_save_as_menuitem = new JMenuItem();
    file_save_as_menuitem.setText(tm.getText("save_as"));
    file_save_as_menuitem.setToolTipText(tm.getText("save_as_tooltip"));
    file_save_as_menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    file_save_as_menuitem.addActionListener(_ -> {
      File selected_file = board_frame.showSaveAsDialog(globalSettings.guiSettings.inputDirectory,
          board_frame.routingJob.output);

      saveAsEventListeners.forEach(listener -> listener.accept(selected_file));
    });
    file_save_as_menuitem
        .addActionListener(_ -> FRAnalytics.buttonClicked("file_save_as_menuitem", file_save_as_menuitem.getText()));

    add(file_save_as_menuitem);

    // File / Exit
    JMenuItem file_exit_menuitem = new JMenuItem();
    file_exit_menuitem.setText(tm.getText("exit"));
    file_exit_menuitem.setToolTipText(tm.getText("exit_tooltip"));
    file_exit_menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
    file_exit_menuitem.addActionListener(_ -> board_frame.dispose());
    file_exit_menuitem
        .addActionListener(_ -> FRAnalytics.buttonClicked("file_exit_menuitem", file_exit_menuitem.getText()));

    add(file_exit_menuitem);
  }

  public void addOpenEventListener(Consumer<File> listener) {
    openEventListeners.add(listener);
  }

  public void addSaveAsEventListener(Consumer<File> listener) {
    saveAsEventListeners.add(listener);
  }

}