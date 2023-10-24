package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.TestLevel;
import app.freerouting.logger.FRLogger;

import app.freerouting.management.FRAnalytics;
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
  private final boolean session_file_option;
  private final ResourceBundle resources;

  /** Creates a new instance of BoardFileMenu */
  private BoardMenuFile(BoardFrame p_board_frame, boolean p_session_file_option) {
    session_file_option = p_session_file_option;
    board_frame = p_board_frame;
    resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
  }

  /** Returns a new file menu for the board frame. */
  public static BoardMenuFile get_instance(
      BoardFrame p_board_frame, boolean p_session_file_option) {
    final BoardMenuFile file_menu = new BoardMenuFile(p_board_frame, p_session_file_option);
    file_menu.setText(file_menu.resources.getString("file"));

    // Create the menu items.

    if (!p_session_file_option) {
      JMenuItem file_save_menuitem = new JMenuItem();
      file_save_menuitem.setText(file_menu.resources.getString("save"));
      file_save_menuitem.setToolTipText(file_menu.resources.getString("save_tooltip"));
      file_save_menuitem.addActionListener(
          evt -> {
            boolean save_ok = file_menu.board_frame.save();
            file_menu.board_frame.board_panel.board_handling.close_files();
            if (save_ok) {
              file_menu.board_frame.screen_messages.set_status_message(
                  file_menu.resources.getString("save_message"));
            }
          });
      file_save_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_menuitem", file_save_menuitem.getText()));

      file_menu.add(file_save_menuitem);
    }


    JMenuItem file_save_and_exit_menuitem = new JMenuItem();
    file_save_and_exit_menuitem.setText(file_menu.resources.getString("save_and_exit"));
    file_save_and_exit_menuitem.setToolTipText(file_menu.resources.getString("save_and_exit_tooltip"));
    file_save_and_exit_menuitem.addActionListener(
        evt -> {
          if (file_menu.session_file_option) {
            file_menu.board_frame.design_file.write_specctra_session_file(
                file_menu.board_frame);
          } else {
            file_menu.board_frame.save();
          }
          file_menu.board_frame.dispose();
        });
    file_save_and_exit_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_and_exit_menuitem", file_save_and_exit_menuitem.getText()));

    file_menu.add(file_save_and_exit_menuitem);

    JMenuItem file_cancel_and_exit_menuitem = new JMenuItem();
    file_cancel_and_exit_menuitem.setText(file_menu.resources.getString("cancel_and_exit"));
    file_cancel_and_exit_menuitem.setToolTipText(file_menu.resources.getString("cancel_and_exit_tooltip"));
    file_cancel_and_exit_menuitem.addActionListener(evt -> file_menu.board_frame.dispose());
    file_cancel_and_exit_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_cancel_and_exit_menuitem", file_cancel_and_exit_menuitem.getText()));

    file_menu.add(file_cancel_and_exit_menuitem);

    if (!file_menu.session_file_option) {
      JMenuItem file_save_as_menuitem = new JMenuItem();
      file_save_as_menuitem.setText(file_menu.resources.getString("save_as"));
      file_save_as_menuitem.setToolTipText(file_menu.resources.getString("save_as_tooltip"));
      file_save_as_menuitem.addActionListener(evt -> file_menu.save_as_action());
      file_save_as_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_as_menuitem", file_save_as_menuitem.getText()));

      file_menu.add(file_save_as_menuitem);


      JMenuItem file_write_logfile_menuitem = new JMenuItem();
      file_write_logfile_menuitem.setText(file_menu.resources.getString("generate_logfile"));
      file_write_logfile_menuitem.setToolTipText(
          file_menu.resources.getString("generate_logfile_tooltip"));
      file_write_logfile_menuitem.addActionListener(evt -> file_menu.write_logfile_action());
      file_write_logfile_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_write_logfile_menuitem", file_write_logfile_menuitem.getText()));

      file_menu.add(file_write_logfile_menuitem);

      JMenuItem file_replay_logfile_menuitem = new JMenuItem();
      file_replay_logfile_menuitem.setText(file_menu.resources.getString("replay_logfile"));
      file_replay_logfile_menuitem.setToolTipText(file_menu.resources.getString("replay_logfile_tooltip"));
      file_replay_logfile_menuitem.addActionListener(evt -> file_menu.read_logfile_action());
      file_replay_logfile_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_replay_logfile_menuitem", file_replay_logfile_menuitem.getText()));

      file_menu.add(file_replay_logfile_menuitem);
    }

    file_menu.add_save_settings_item();

    return file_menu;
  }

  public void add_design_dependent_items() {
    if (this.session_file_option) {
      return;
    }
    BasicBoard routing_board =
        this.board_frame.board_panel.board_handling.get_routing_board();
    boolean host_cad_is_eagle = routing_board.communication.host_cad_is_eagle();

    JMenuItem file_write_session_file_menuitem = new JMenuItem();
    file_write_session_file_menuitem.setText(resources.getString("session_file"));
    file_write_session_file_menuitem.setToolTipText(resources.getString("session_file_tooltip"));
    file_write_session_file_menuitem.addActionListener(evt -> board_frame.design_file.write_specctra_session_file(board_frame));
    file_write_session_file_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_write_session_file_menuitem", file_write_session_file_menuitem.getText()));


    if ((routing_board.get_test_level() != TestLevel.RELEASE_VERSION
        || !host_cad_is_eagle)) {
      this.add(file_write_session_file_menuitem);
    }

    JMenuItem file_write_eagle_session_script_menuitem = new JMenuItem();
    file_write_eagle_session_script_menuitem.setText(resources.getString("eagle_script"));
    file_write_eagle_session_script_menuitem.setToolTipText(resources.getString("eagle_script_tooltip"));
    file_write_eagle_session_script_menuitem.addActionListener(evt -> board_frame.design_file.update_eagle(board_frame));
    file_write_eagle_session_script_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_write_eagle_session_script_menuitem", file_write_eagle_session_script_menuitem.getText()));

    if (routing_board.get_test_level() != TestLevel.RELEASE_VERSION
        || host_cad_is_eagle) {
      this.add(file_write_eagle_session_script_menuitem);
    }
  }

  /** Adds a menu item for saving the current interactive settings as default. */
  private void add_save_settings_item() {
    JMenuItem file_save_settings_menuitem = new JMenuItem();
    file_save_settings_menuitem.setText(resources.getString("settings"));
    file_save_settings_menuitem.setToolTipText(resources.getString("settings_tooltip"));
    file_save_settings_menuitem.addActionListener(evt -> save_defaults_action());
    file_save_settings_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("file_save_settings_menuitem", file_save_settings_menuitem.getText()));
    add(file_save_settings_menuitem);
  }

  private void save_as_action() {
    if (this.board_frame.design_file != null) {
      this.board_frame.design_file.save_as_dialog(this, this.board_frame);
    }
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
    File defaults_file = new File(board_frame.design_file.get_parent(), BoardFrame.GUI_DEFAULTS_FILE_NAME);
    if (defaults_file.exists()) {
      // Make a backup copy of the old defaults file.
      File defaults_file_backup = new File(board_frame.design_file.get_parent(), BoardFrame.GUI_DEFAULTS_FILE_BACKUP_NAME);
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
}
