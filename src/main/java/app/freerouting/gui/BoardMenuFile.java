package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Creates the file menu of a board frame. */
public class BoardMenuFile extends JMenu {

  public final JMenuItem fileSaveAsMenuitem;
  private final TextManager tm;
  private final List<Consumer<File>> openEventListeners = new ArrayList<>();
  private final List<Consumer<File>> saveAsEventListeners = new ArrayList<>();

  /** Creates a new instance of BoardFileMenu. */
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
          File selectedFile = showOpenDialog(globalSettings.guiSettings.inputDirectory, boardFrame);

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

    // Accessibility (D22): stable, locale-independent locators + translated accessible names so the
    // a11y harness can find these controls by locator (never by translated label). Keys are the
    // same
    // ones used for the visible text, so no new resource-bundle keys are introduced.
    A11y.tag(this, GuiLocators.MENU_FILE);
    A11y.describe(this, tm.getText("file"), null);
    A11y.tag(fileOpenMenuitem, GuiLocators.MENU_FILE_OPEN);
    A11y.describe(fileOpenMenuitem, tm.getText("open"), null);
    A11y.tag(fileSaveAsMenuitem, GuiLocators.MENU_FILE_SAVE_AS);
    A11y.describe(fileSaveAsMenuitem, tm.getText("save_as"), null);
    A11y.tag(fileExitMenuitem, GuiLocators.MENU_FILE_EXIT);
    A11y.describe(fileExitMenuitem, tm.getText("exit"), null);
  }

  /**
   * Shows a file chooser for opening a design file (GUI layer owns file picking; SoC plan Phase 4).
   *
   * @param defaultDirectory the directory to open the chooser in; may be null
   * @param parent the parent component for the modal dialog; may be null
   * @return the selected file, or {@code null} if the user cancelled
   */
  private static File showOpenDialog(String defaultDirectory, Component parent) {
    JFileChooser fileChooser = new JFileChooser(defaultDirectory);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    // Add the file filter for SPECCTRA Design .DSN files
    FileNameExtensionFilter dsnFilter =
        new FileNameExtensionFilter("SPECCTRA Design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter =
        new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Add the file filter for KiCad JSON .JSON files
    FileNameExtensionFilter jsonFilter =
        new FileNameExtensionFilter("KiCad Design JSON file (*.json)", "json");
    fileChooser.addChoosableFileFilter(jsonFilter);

    // Set a file filter as the default one
    fileChooser.setFileFilter(dsnFilter);

    fileChooser.showOpenDialog(parent);
    return fileChooser.getSelectedFile();
  }

  /**
   * Registers a listener notified when a file is selected for opening.
   *
   * @param listener the listener to register
   */
  public void addOpenEventListener(Consumer<File> listener) {
    openEventListeners.add(listener);
  }

  /**
   * Registers a listener notified when a file is selected for saving.
   *
   * @param listener the listener to register
   */
  public void addSaveAsEventListener(Consumer<File> listener) {
    saveAsEventListeners.add(listener);
  }
}
