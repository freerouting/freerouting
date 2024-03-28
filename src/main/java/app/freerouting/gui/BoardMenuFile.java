package app.freerouting.gui;

import app.freerouting.logger.FRLogger;

import app.freerouting.management.FRAnalytics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ResourceBundle;

/** Creates the file menu of a board frame. */
public class BoardMenuFile extends JMenu {

  private final BoardFrame board_frame;
  private final ResourceBundle resources;

  private List<Consumer<File>> openEventListeners = new ArrayList<>();
  private List<Consumer<File>> saveAsEventListeners = new ArrayList<>();

  /** Creates a new instance of BoardFileMenu */
  private BoardMenuFile(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources = ResourceBundle.getBundle("app.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
  }

  /** Returns a new file menu for the board frame. */
  public static BoardMenuFile get_instance(BoardFrame p_board_frame, boolean p_disable_feature_macros)
  {
    final BoardMenuFile file_menu = new BoardMenuFile(p_board_frame);
    file_menu.setText(file_menu.resources.getString("file"));

    // File / Open...
    JMenuItem file_open_menuitem = new JMenuItem();
    file_open_menuitem.setText(file_menu.resources.getString("open"));
    file_open_menuitem.setToolTipText(file_menu.resources.getString("open_tooltip"));
    file_open_menuitem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    file_open_menuitem.addActionListener(
        evt -> {
          File selected_file = DesignFile.showOpenDialog(MainApplication.globalSettings.input_directory, file_menu.board_frame);

          file_menu.openEventListeners.forEach(listener -> listener.accept(selected_file));
        });
    file_open_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_open_menuitem", file_open_menuitem.getText()));
    file_menu.add(file_open_menuitem);


    // File / Save as .FRB
//    JMenuItem file_save_menuitem = new JMenuItem();
//    file_save_menuitem.setText(file_menu.resources.getString("save"));
//    file_save_menuitem.setToolTipText(file_menu.resources.getString("save_tooltip"));
//    file_save_menuitem.addActionListener(
//        evt -> {
//          boolean save_ok = file_menu.board_frame.save();
//          file_menu.board_frame.board_panel.board_handling.close_files();
//          if (save_ok) {
//            file_menu.board_frame.screen_messages.set_status_message(
//                file_menu.resources.getString("save_message"));
//          }
//        });
//    file_save_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_menuitem", file_save_menuitem.getText()));
//
//    file_menu.add(file_save_menuitem);


//    JMenuItem file_save_and_exit_menuitem = new JMenuItem();
//    file_save_and_exit_menuitem.setText(file_menu.resources.getString("save_and_exit"));
//    file_save_and_exit_menuitem.setToolTipText(file_menu.resources.getString("save_and_exit_tooltip"));
//    file_save_and_exit_menuitem.addActionListener(
//        evt -> {
//          if (file_menu.session_file_option) {
//            file_menu.board_frame.design_file.write_specctra_session_file(file_menu.board_frame);
//          } else {
//            file_menu.board_frame.save();
//          }
//          file_menu.board_frame.dispose();
//        });
//    file_save_and_exit_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_and_exit_menuitem", file_save_and_exit_menuitem.getText()));
//
//    file_menu.add(file_save_and_exit_menuitem);

    // File / Save as...

    JMenuItem file_save_as_menuitem = new JMenuItem();
    file_save_as_menuitem.setText(file_menu.resources.getString("save_as"));
    file_save_as_menuitem.setToolTipText(file_menu.resources.getString("save_as_tooltip"));
    file_save_as_menuitem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    file_save_as_menuitem.addActionListener(evt -> {
      File selected_file = file_menu.board_frame.design_file.showSaveAsDialog(MainApplication.globalSettings.input_directory, file_menu.board_frame);

      file_menu.saveAsEventListeners.forEach(listener -> listener.accept(selected_file));
    });
    file_save_as_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_as_menuitem", file_save_as_menuitem.getText()));

    file_menu.add(file_save_as_menuitem);

    if (!p_disable_feature_macros) {
      JMenuItem file_write_logfile_menuitem = new JMenuItem();
      file_write_logfile_menuitem.setText(file_menu.resources.getString("generate_logfile"));
      file_write_logfile_menuitem.setToolTipText(
          file_menu.resources.getString("generate_logfile_tooltip"));
      file_write_logfile_menuitem.addActionListener(evt -> file_menu.write_logfile_action());
      file_write_logfile_menuitem.addActionListener(
          evt ->
              FRAnalytics.buttonClicked(
                  "file_write_logfile_menuitem", file_write_logfile_menuitem.getText()));

      file_menu.add(file_write_logfile_menuitem);

      JMenuItem file_replay_logfile_menuitem = new JMenuItem();
      file_replay_logfile_menuitem.setText(file_menu.resources.getString("replay_logfile"));
      file_replay_logfile_menuitem.setToolTipText(
          file_menu.resources.getString("replay_logfile_tooltip"));
      file_replay_logfile_menuitem.addActionListener(evt -> file_menu.read_logfile_action());
      file_replay_logfile_menuitem.addActionListener(
          evt ->
              FRAnalytics.buttonClicked(
                  "file_replay_logfile_menuitem", file_replay_logfile_menuitem.getText()));

      file_menu.add(file_replay_logfile_menuitem);
    }

    // File / Export as Specctra Session File and Export as Eagle Session Script
    // file_menu.add_design_dependent_items();

    // File / Exit

    JMenuItem file_exit_menuitem = new JMenuItem();
    file_exit_menuitem.setText(file_menu.resources.getString("exit"));
    file_exit_menuitem.setToolTipText(file_menu.resources.getString("exit_tooltip"));
    file_exit_menuitem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
    file_exit_menuitem.addActionListener(evt -> file_menu.board_frame.dispose());
    file_exit_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_exit_menuitem", file_exit_menuitem.getText()));

    file_menu.add(file_exit_menuitem);

    return file_menu;
  }

  /** Adds a menu item for saving the current GUI settings as default. */
  private void add_save_settings_item() {
    JMenuItem file_save_settings_menuitem = new JMenuItem();
    file_save_settings_menuitem.setText(resources.getString("settings"));
    file_save_settings_menuitem.setToolTipText(resources.getString("settings_tooltip"));
    file_save_settings_menuitem.addActionListener(evt -> save_defaults_action());
    file_save_settings_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_settings_menuitem", file_save_settings_menuitem.getText()));
    add(file_save_settings_menuitem);
  }

  private void write_logfile_action() {
    JFileChooser file_chooser = new JFileChooser();
    File logfile_dir = board_frame.design_file.get_parent_file();
    file_chooser.setMinimumSize(new Dimension(500, 250));
    file_chooser.setCurrentDirectory(logfile_dir);
    file_chooser.setFileFilter(BoardFrame.logfile_filter);
    file_chooser.showOpenDialog(this);
    File filename = file_chooser.getSelectedFile();
    if (filename == null) {
      board_frame.screen_messages.set_status_message(resources.getString("message_8"));
    } else {

      board_frame.screen_messages.set_status_message(resources.getString("message_9"));
      board_frame.board_panel.board_handling.start_logfile(filename);
    }
  }

  private void read_logfile_action() {
    JFileChooser file_chooser = new JFileChooser();
    File logfile_dir = board_frame.design_file.get_parent_file();
    file_chooser.setMinimumSize(new Dimension(500, 250));
    file_chooser.setCurrentDirectory(logfile_dir);
    file_chooser.setFileFilter(BoardFrame.logfile_filter);
    file_chooser.showOpenDialog(this);

    File filename = file_chooser.getSelectedFile();
    if (filename == null) {
      board_frame.screen_messages.set_status_message(resources.getString("message_10"));
    } else {
      InputStream input_stream;
      try {
        input_stream = new FileInputStream(filename);
      } catch (FileNotFoundException e) {
        return;
      }
      board_frame.read_logfile(input_stream);
    }
  }

  private void save_defaults_action() {
    OutputStream output_stream;

    FRLogger.info("Saving '" + BoardFrame.GUI_DEFAULTS_FILE_NAME + "'...");
    File defaults_file = new File(board_frame.design_file.getInputFileDirectory2(), BoardFrame.GUI_DEFAULTS_FILE_NAME);
    if (defaults_file.exists()) {
      // Make a backup copy of the old defaults file.
      File defaults_file_backup = new File(board_frame.design_file.getInputFileDirectory2(), BoardFrame.GUI_DEFAULTS_FILE_BACKUP_NAME);
      if (defaults_file_backup.exists()) {
        defaults_file_backup.delete();
      }
      defaults_file.renameTo(defaults_file_backup);
    }
    try {
      output_stream = new FileOutputStream(defaults_file);
    } catch (Exception e) {
      output_stream = null;
    }

    boolean write_ok;
    if (output_stream == null) {
      write_ok = false;
    } else {
      write_ok = GUIDefaultsFile.write(board_frame, board_frame.board_panel.board_handling, output_stream);
    }
    if (write_ok) {
      board_frame.screen_messages.set_status_message(resources.getString("message_17"));
    } else {
      board_frame.screen_messages.set_status_message(resources.getString("message_18"));
    }
  }

  public void addOpenEventListener(Consumer<File> listener) {
    openEventListeners.add(listener);
  }

  public void addSaveAsEventListener(Consumer<File> listener) {
    saveAsEventListeners.add(listener);
  }

}
