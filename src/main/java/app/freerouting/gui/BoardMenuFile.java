package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Creates the file menu of a board frame. */
public class BoardMenuFile extends javax.swing.JMenu {

  private final BoardFrame board_frame;
  private final boolean session_file_option;
  private final java.util.ResourceBundle resources;

  /** Creates a new instance of BoardFileMenu */
  private BoardMenuFile(BoardFrame p_board_frame, boolean p_session_file_option) {
    session_file_option = p_session_file_option;
    board_frame = p_board_frame;
    resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
  }

  /** Returns a new file menu for the board frame. */
  public static BoardMenuFile get_instance(
      BoardFrame p_board_frame, boolean p_session_file_option) {
    final BoardMenuFile file_menu = new BoardMenuFile(p_board_frame, p_session_file_option);
    file_menu.setText(file_menu.resources.getString("file"));

    // Create the menu items.

    if (!p_session_file_option && !p_board_frame.is_web_start) {
      javax.swing.JMenuItem save_item = new javax.swing.JMenuItem();
      save_item.setText(file_menu.resources.getString("save"));
      save_item.setToolTipText(file_menu.resources.getString("save_tooltip"));
      save_item.addActionListener(
          new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
              boolean save_ok = file_menu.board_frame.save();
              file_menu.board_frame.board_panel.board_handling.close_files();
              if (save_ok) {
                file_menu.board_frame.screen_messages.set_status_message(
                    file_menu.resources.getString("save_message"));
              }
            }
          });

      file_menu.add(save_item);
    }

    if (!p_board_frame.is_web_start) {
      javax.swing.JMenuItem save_and_exit_item = new javax.swing.JMenuItem();
      save_and_exit_item.setText(file_menu.resources.getString("save_and_exit"));
      save_and_exit_item.setToolTipText(file_menu.resources.getString("save_and_exit_tooltip"));
      save_and_exit_item.addActionListener(
          new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
              if (file_menu.session_file_option) {
                file_menu.board_frame.design_file.write_specctra_session_file(
                    file_menu.board_frame);
              } else {
                file_menu.board_frame.save();
              }
              file_menu.board_frame.dispose();
            }
          });

      file_menu.add(save_and_exit_item);
    }

    javax.swing.JMenuItem cancel_and_exit_item = new javax.swing.JMenuItem();
    cancel_and_exit_item.setText(file_menu.resources.getString("cancel_and_exit"));
    cancel_and_exit_item.setToolTipText(file_menu.resources.getString("cancel_and_exit_tooltip"));
    cancel_and_exit_item.addActionListener(
        new java.awt.event.ActionListener() {

          public void actionPerformed(java.awt.event.ActionEvent evt) {
            file_menu.board_frame.dispose();
          }
        });

    file_menu.add(cancel_and_exit_item);

    if (!file_menu.session_file_option) {
      javax.swing.JMenuItem save_as_item = new javax.swing.JMenuItem();
      save_as_item.setText(file_menu.resources.getString("save_as"));
      save_as_item.setToolTipText(file_menu.resources.getString("save_as_tooltip"));
      save_as_item.addActionListener(
          new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
              file_menu.save_as_action();
            }
          });

      file_menu.add(save_as_item);

      if (!p_board_frame.is_web_start) {
        javax.swing.JMenuItem write_logfile_item = new javax.swing.JMenuItem();
        write_logfile_item.setText(file_menu.resources.getString("generate_logfile"));
        write_logfile_item.setToolTipText(
            file_menu.resources.getString("generate_logfile_tooltip"));
        write_logfile_item.addActionListener(
            new java.awt.event.ActionListener() {

              public void actionPerformed(java.awt.event.ActionEvent evt) {
                file_menu.write_logfile_action();
              }
            });

        file_menu.add(write_logfile_item);

        javax.swing.JMenuItem replay_logfile_item = new javax.swing.JMenuItem();
        replay_logfile_item.setText(file_menu.resources.getString("replay_logfile"));
        replay_logfile_item.setToolTipText(file_menu.resources.getString("replay_logfile_tooltip"));
        replay_logfile_item.addActionListener(
            new java.awt.event.ActionListener() {

              public void actionPerformed(java.awt.event.ActionEvent evt) {
                file_menu.read_logfile_action();
              }
            });

        file_menu.add(replay_logfile_item);
      }
    }

    file_menu.add_save_settings_item();

    return file_menu;
  }

  public void add_design_dependent_items() {
    if (this.session_file_option) {
      return;
    }
    app.freerouting.board.BasicBoard routing_board =
        this.board_frame.board_panel.board_handling.get_routing_board();
    boolean host_cad_is_eagle = routing_board.communication.host_cad_is_eagle();

    javax.swing.JMenuItem write_session_file_item = new javax.swing.JMenuItem();
    write_session_file_item.setText(resources.getString("session_file"));
    write_session_file_item.setToolTipText(resources.getString("session_file_tooltip"));
    write_session_file_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            // The user selected the .SES file export function from the File menu
            board_frame.design_file.write_specctra_session_file(board_frame);
          }
        });

    if ((routing_board.get_test_level() != app.freerouting.board.TestLevel.RELEASE_VERSION
        || !host_cad_is_eagle)) {
      this.add(write_session_file_item);
    }

    javax.swing.JMenuItem write_eagle_session_script_item = new javax.swing.JMenuItem();
    write_eagle_session_script_item.setText(resources.getString("eagle_script"));
    write_eagle_session_script_item.setToolTipText(resources.getString("eagle_script_tooltip"));
    write_eagle_session_script_item.addActionListener(
        new java.awt.event.ActionListener() {

          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_frame.design_file.update_eagle(board_frame);
          }
        });

    if (routing_board.get_test_level() != app.freerouting.board.TestLevel.RELEASE_VERSION
        || host_cad_is_eagle) {
      this.add(write_eagle_session_script_item);
    }
  }

  /** Adds a menu item for saving the current interactive settings as default. */
  private void add_save_settings_item() {
    javax.swing.JMenuItem save_settings_item = new javax.swing.JMenuItem();
    save_settings_item.setText(resources.getString("settings"));
    save_settings_item.setToolTipText(resources.getString("settings_tooltip"));
    save_settings_item.addActionListener(
        new java.awt.event.ActionListener() {

          public void actionPerformed(java.awt.event.ActionEvent evt) {
            save_defaults_action();
          }
        });
    add(save_settings_item);
  }

  private void save_as_action() {
    if (this.board_frame.design_file != null) {
      this.board_frame.design_file.save_as_dialog(this, this.board_frame);
    }
  }

  private void write_logfile_action() {
    javax.swing.JFileChooser file_chooser = new javax.swing.JFileChooser();
    java.io.File logfile_dir = board_frame.design_file.get_parent_file();
    file_chooser.setMinimumSize(new java.awt.Dimension(500, 250));
    file_chooser.setCurrentDirectory(logfile_dir);
    file_chooser.setFileFilter(BoardFrame.logfile_filter);
    file_chooser.showOpenDialog(this);
    java.io.File filename = file_chooser.getSelectedFile();
    if (filename == null) {
      board_frame.screen_messages.set_status_message(resources.getString("message_8"));
    } else {

      board_frame.screen_messages.set_status_message(resources.getString("message_9"));
      board_frame.board_panel.board_handling.start_logfile(filename);
    }
  }

  private void read_logfile_action() {
    javax.swing.JFileChooser file_chooser = new javax.swing.JFileChooser();
    java.io.File logfile_dir = board_frame.design_file.get_parent_file();
    file_chooser.setMinimumSize(new java.awt.Dimension(500, 250));
    file_chooser.setCurrentDirectory(logfile_dir);
    file_chooser.setFileFilter(BoardFrame.logfile_filter);
    file_chooser.showOpenDialog(this);

    java.io.File filename = file_chooser.getSelectedFile();
    if (filename == null) {
      board_frame.screen_messages.set_status_message(resources.getString("message_10"));
    } else {
      java.io.InputStream input_stream = null;
      try {
        input_stream = new java.io.FileInputStream(filename);
      } catch (java.io.FileNotFoundException e) {
        return;
      }
      board_frame.read_logfile(input_stream);
    }
  }

  private void save_defaults_action() {
    java.io.OutputStream output_stream = null;

    FRLogger.info("Saving '" + BoardFrame.GUI_DEFAULTS_FILE_NAME + "'...");
    java.io.File defaults_file =
        new java.io.File(board_frame.design_file.get_parent(), BoardFrame.GUI_DEFAULTS_FILE_NAME);
    if (defaults_file.exists()) {
      // Make a backup copy of the old defaulds file.
      java.io.File defaults_file_backup =
          new java.io.File(
              board_frame.design_file.get_parent(), BoardFrame.GUI_DEFAULTS_FILE_BACKUP_NAME);
      if (defaults_file_backup.exists()) {
        defaults_file_backup.delete();
      }
      defaults_file.renameTo(defaults_file_backup);
    }
    try {
      output_stream = new java.io.FileOutputStream(defaults_file);
    } catch (Exception e) {
      output_stream = null;
    }

    boolean write_ok;
    if (output_stream == null) {
      write_ok = false;
    } else {
      write_ok =
          GUIDefaultsFile.write(board_frame, board_frame.board_panel.board_handling, output_stream);
    }
    if (write_ok) {
      board_frame.screen_messages.set_status_message(resources.getString("message_17"));
    } else {
      board_frame.screen_messages.set_status_message(resources.getString("message_18"));
    }
  }
}
