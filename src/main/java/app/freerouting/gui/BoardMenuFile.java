package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/** Creates the file menu of a board frame. */
public class BoardMenuFile extends JMenu {

  public final JMenuItem fileSaveAsMenuitem;
  private final TextManager tm;
  private final List<Consumer<File>> openEventListeners = new ArrayList<>();
  private final List<Consumer<File>> saveAsEventListeners = new ArrayList<>();

  /** Creates a new instance of BoardFileMenu */
  public BoardMenuFile(BoardFrame boardFrame) {
    tm = new TextManager(this.getClass(), boardFrame.get_locale());

    setText(tm.getText("file"));

    // File / Open...
    JMenuItem fileOpenMenuitem = new JMenuItem();
    fileOpenMenuitem.setText(tm.getText("open"));
    fileOpenMenuitem.setToolTipText(tm.getText("open_tooltip"));
    fileOpenMenuitem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    fileOpenMenuitem.addActionListener(
        _ -> {
          File selectedFile =
              RoutingJob.showOpenDialog(globalSettings.guiSettings.inputDirectory, boardFrame);

          openEventListeners.forEach(listener -> listener.accept(selectedFile));
        });
    fileOpenMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("fileOpenMenuitem", fileOpenMenuitem.getText()));
    add(fileOpenMenuitem);

    // File / Save as...
    fileSaveAsMenuitem = new JMenuItem();
    fileSaveAsMenuitem.setText(tm.getText("save_as"));
    fileSaveAsMenuitem.setToolTipText(tm.getText("save_as_tooltip"));
    fileSaveAsMenuitem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    fileSaveAsMenuitem.addActionListener(
        _ -> {
          File selectedFile =
              boardFrame.showSaveAsDialog(
                  globalSettings.guiSettings.inputDirectory, boardFrame.routingJob.output);

          saveAsEventListeners.forEach(listener -> listener.accept(selectedFile));
        });
    fileSaveAsMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("fileSaveAsMenuitem", fileSaveAsMenuitem.getText()));

    add(fileSaveAsMenuitem);

    // File / Exit
    JMenuItem fileExitMenuitem = new JMenuItem();
    fileExitMenuitem.setText(tm.getText("exit"));
    fileExitMenuitem.setToolTipText(tm.getText("exit_tooltip"));
    fileExitMenuitem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
    fileExitMenuitem.addActionListener(_ -> boardFrame.dispose());
    fileExitMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("fileExitMenuitem", fileExitMenuitem.getText()));

    add(fileExitMenuitem);
  }

  public void addOpenEventListener(Consumer<File> listener) {
    openEventListeners.add(listener);
  }

  public void addSaveAsEventListener(Consumer<File> listener) {
    saveAsEventListeners.add(listener);
  }
}
