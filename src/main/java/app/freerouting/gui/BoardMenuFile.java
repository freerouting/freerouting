package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates the file menu of a board frame.
 */
public class BoardMenuFile extends JMenu
{
  public final JMenuItem file_save_as_menuitem;
  private final TextManager tm;
  private final List<Consumer<File>> openEventListeners = new ArrayList<>();
  private final List<Consumer<File>> saveAsEventListeners = new ArrayList<>();

  /**
   * Creates a new instance of BoardFileMenu
   */
  public BoardMenuFile(BoardFrame board_frame, boolean p_disable_feature_macros)
  {
    tm = new TextManager(this.getClass(), board_frame.get_locale());

    setText(tm.getText("file"));

    // File / Open...
    JMenuItem file_open_menuitem = new JMenuItem();
    file_open_menuitem.setText(tm.getText("open"));
    file_open_menuitem.setToolTipText(tm.getText("open_tooltip"));
    file_open_menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    file_open_menuitem.addActionListener(_ ->
    {
      File selected_file = RoutingJob.showOpenDialog(globalSettings.guiSettings.inputDirectory, board_frame);

      openEventListeners.forEach(listener -> listener.accept(selected_file));
    });
    file_open_menuitem.addActionListener(_ -> FRAnalytics.buttonClicked("file_open_menuitem", file_open_menuitem.getText()));
    add(file_open_menuitem);

    // File / Save as...
    file_save_as_menuitem = new JMenuItem();
    file_save_as_menuitem.setText(tm.getText("save_as"));
    file_save_as_menuitem.setToolTipText(tm.getText("save_as_tooltip"));
    file_save_as_menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    file_save_as_menuitem.addActionListener(_ ->
    {
      File selected_file = board_frame.showSaveAsDialog(globalSettings.guiSettings.inputDirectory, board_frame.routingJob.output);

      saveAsEventListeners.forEach(listener -> listener.accept(selected_file));
    });
    file_save_as_menuitem.addActionListener(_ -> FRAnalytics.buttonClicked("file_save_as_menuitem", file_save_as_menuitem.getText()));

    add(file_save_as_menuitem);

    if (!p_disable_feature_macros)
    {
      JMenuItem file_write_logfile_menuitem = new JMenuItem();
      file_write_logfile_menuitem.setText(tm.getText("generate_logfile"));
      file_write_logfile_menuitem.setToolTipText(tm.getText("generate_logfile_tooltip"));
      file_write_logfile_menuitem.addActionListener(_ -> write_logfile_action(board_frame));
      file_write_logfile_menuitem.addActionListener(_ -> FRAnalytics.buttonClicked("file_write_logfile_menuitem", file_write_logfile_menuitem.getText()));

      add(file_write_logfile_menuitem);

      JMenuItem file_replay_logfile_menuitem = new JMenuItem();
      file_replay_logfile_menuitem.setText(tm.getText("replay_logfile"));
      file_replay_logfile_menuitem.setToolTipText(tm.getText("replay_logfile_tooltip"));
      file_replay_logfile_menuitem.addActionListener(_ -> read_logfile_action(board_frame));
      file_replay_logfile_menuitem.addActionListener(_ -> FRAnalytics.buttonClicked("file_replay_logfile_menuitem", file_replay_logfile_menuitem.getText()));

      add(file_replay_logfile_menuitem);
    }

    // File / Exit
    JMenuItem file_exit_menuitem = new JMenuItem();
    file_exit_menuitem.setText(tm.getText("exit"));
    file_exit_menuitem.setToolTipText(tm.getText("exit_tooltip"));
    file_exit_menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
    file_exit_menuitem.addActionListener(_ -> board_frame.dispose());
    file_exit_menuitem.addActionListener(_ -> FRAnalytics.buttonClicked("file_exit_menuitem", file_exit_menuitem.getText()));

    add(file_exit_menuitem);
  }

  private void write_logfile_action(BoardFrame board_frame)
  {
    JFileChooser file_chooser = new JFileChooser();
    File logfile_dir = new File(board_frame.routingJob.input.getDirectoryPath());
    file_chooser.setMinimumSize(new Dimension(500, 250));
    file_chooser.setCurrentDirectory(logfile_dir);
    file_chooser.setFileFilter(BoardFrame.logfile_filter);
    file_chooser.showOpenDialog(this);
    File filename = file_chooser.getSelectedFile();
    if (filename == null)
    {
      board_frame.screen_messages.set_status_message(tm.getText("message_8"));
    }
    else
    {

      board_frame.screen_messages.set_status_message(tm.getText("message_9"));
      board_frame.board_panel.board_handling.start_logfile(filename);
    }
  }

  private void read_logfile_action(BoardFrame board_frame)
  {
    JFileChooser file_chooser = new JFileChooser();
    File logfile_dir = new File(board_frame.routingJob.input.getDirectoryPath());
    file_chooser.setMinimumSize(new Dimension(500, 250));
    file_chooser.setCurrentDirectory(logfile_dir);
    file_chooser.setFileFilter(BoardFrame.logfile_filter);
    file_chooser.showOpenDialog(this);

    File filename = file_chooser.getSelectedFile();
    if (filename == null)
    {
      board_frame.screen_messages.set_status_message(tm.getText("message_10"));
    }
    else
    {
      InputStream input_stream;
      try
      {
        input_stream = new FileInputStream(filename);
      } catch (FileNotFoundException _)
      {
        return;
      }
      board_frame.read_logfile(input_stream);
    }
  }

  public void addOpenEventListener(Consumer<File> listener)
  {
    openEventListeners.add(listener);
  }

  public void addSaveAsEventListener(Consumer<File> listener)
  {
    saveAsEventListeners.add(listener);
  }

}
