package app.freerouting.gui;

import app.freerouting.board.*;
import app.freerouting.datastructures.FileFilter;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.designforms.specctra.RulesFile;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.ScreenMessages;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.management.FRAnalytics;
import app.freerouting.settings.DisabledFeaturesSettings;
import app.freerouting.settings.GlobalSettings;

import javax.swing.*;
import java.awt.Component;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Graphical frame containing the Menu, Toolbar, Canvas and Status bar.
 */
public class BoardFrame extends WindowBase
{
  /**
   * The windows above stored in an array
   */
  static final int SUBWINDOW_COUNT = 24;
  static final String[] log_file_extensions = {"log"};
  static final String GUI_DEFAULTS_FILE_NAME = "gui_defaults.par";
  static final String GUI_DEFAULTS_FILE_BACKUP_NAME = "gui_defaults.par.bak";
  static final FileFilter logfile_filter = new FileFilter(log_file_extensions);
  /**
   * The scroll pane for the panel of the routing board.
   */
  final JScrollPane scroll_pane;
  /**
   * The menubar of this frame
   */
  final BoardMenuBar menubar;
  final ScreenMessages screen_messages;
  /**
   * The panel with the toolbars
   */
  private final BoardToolbar toolbar_panel;
  /**
   * The toolbar used in the selected item state.
   */
  private final JToolBar select_toolbar;
  /**
   * The panel with the message line
   */
  private final BoardPanelStatus message_panel;
  private final Locale locale;
  private final List<Consumer<RoutingBoard>> boardLoadedEventListeners = new ArrayList<>();
  private final List<Consumer<RoutingBoard>> boardSavedEventListeners = new ArrayList<>();
  private final BoardObservers board_observers;
  /**
   * The panel with the graphical representation of the board.
   */
  BoardPanel board_panel = null;
  WindowAbout about_window;
  WindowRouteParameter route_parameter_window;
  WindowAutorouteParameter autoroute_parameter_window;
  WindowSelectParameter select_parameter_window;
  WindowMoveParameter move_parameter_window;
  WindowClearanceMatrix clearance_matrix_window;
  WindowVia via_window;
  WindowEditVias edit_vias_window;
  WindowNetClasses edit_net_rules_window;
  WindowAssignNetClass assign_net_classes_window;
  WindowPadstacks padstacks_window;
  WindowPackages packages_window;
  WindowIncompletes incompletes_window;
  WindowNets net_info_window;
  WindowClearanceViolations clearance_violations_window;
  WindowLengthViolations length_violations_window;
  WindowUnconnectedRoute unconnected_route_window;
  WindowRouteStubs route_stubs_window;
  WindowComponents components_window;
  WindowLayerVisibility layer_visibility_window;
  WindowObjectVisibility object_visibility_window;
  WindowDisplayMisc display_misc_window;
  WindowSnapshot snapshot_window;
  ColorManager color_manager;
  BoardSavableSubWindow[] permanent_subwindows = new BoardSavableSubWindow[SUBWINDOW_COUNT];
  Collection<BoardTemporarySubWindow> temporary_subwindows = new LinkedList<>();
  DesignFile design_file;
  private LocalDateTime intermediate_stage_file_last_saved_at;

  /**
   * Creates a new BoardFrame that is the GUI element containing the Menu, Toolbar, Canvas and Status bar.
   * If p_option = FROM_START_MENU this frame is created from a start
   * menu frame. If p_option = SINGLE_FRAME, this frame is created directly a single frame. If
   * p_option = Option.IN_SAND_BOX, no security sensitive actions like for example choosing If
   * p_option = Option.WEBSTART, the application has been started with Java Webstart. files are
   * allowed, so that the frame can be used in an applet. Currently, Option.EXTENDED_TOOL_BAR is used
   * only if a new board is created by the application from scratch. If p_test_level {@literal >}
   * RELEASE_VERSION, functionality not yet ready for release is included. Also, the warning output
   * depends on p_test_level.
   */
  public BoardFrame(DesignFile p_design, Locale p_locale, boolean p_save_intermediate_stages, float p_optimization_improvement_threshold, DisabledFeaturesSettings disabledFeatures)
  {
    this(p_design, new BoardObserverAdaptor(), p_locale, p_save_intermediate_stages, p_optimization_improvement_threshold, disabledFeatures);
  }

  /**
   * Creates new form BoardFrame. The parameters p_item_observers and p_item_id_no_generator are
   * used for synchronizing purposes, if the frame is embedded into a host system,
   */
  BoardFrame(DesignFile p_design, BoardObservers p_observers, Locale p_locale, boolean p_save_intermediate_stages, float p_optimization_improvement_threshold, DisabledFeaturesSettings disabledFeatures)
  {
    super(800, 150);

    this.design_file = p_design;

    this.board_observers = p_observers;
    this.locale = p_locale;
    this.setLanguage(p_locale);

    // Set the menu bar of this frame.
    this.menubar = new BoardMenuBar(this, disabledFeatures);

    this.menubar.fileMenu.addOpenEventListener((File selectedFile) ->
    {
      if (selectedFile == null)
      {
        // There was no file selected in the dialog, so we do nothing
        return;
      }

      // Let's categorize the file based on its extension
      if (!p_design.tryToSetInputFile(selectedFile))
      {
        // The file is not in a valid format
        return;
      }

      // Set the input directory in the global settings
      if (p_design.getInputFile() != null)
      {
        MainApplication.globalSettings.input_directory = design_file.getInputFileDirectory();

        try
        {
          GlobalSettings.save(MainApplication.globalSettings);
        } catch (Exception e)
        {
          // it's ok if we can't save the configuration file
          FRLogger.error("Couldn't update the input directory in the configuration file", e);
        }
      }

      // Load the file into the frame based on its recognised format
      if ((board_panel != null) && (board_panel.board_handling != null) && (p_design.inputFileFormat != FileFormat.UNKNOWN))
      {
        switch (p_design.inputFileFormat)
        {
          case DSN:
            this.load(p_design.get_input_stream(), true, null);
            FRAnalytics.buttonClicked("fileio_loaddsn", this.design_file.getInputFileDetails());
            break;
          case FRB:
            this.load(p_design.get_input_stream(), false, null);
            FRAnalytics.buttonClicked("fileio_loadfrb", this.design_file.getInputFileDetails());
            break;
          default:
            // The file format is not supported
            FRLogger.warn("Loading the board failed, because the selected file format is not supported.");
            break;
        }
      }
    });

    this.menubar.fileMenu.addSaveAsEventListener((File selectedFile) ->
    {
      if (selectedFile == null)
      {
        // There was no file selected in the dialog, so we do nothing
        return;
      }

      // Let's categorize the file based on its extension
      if (!p_design.tryToSetOutputFile(selectedFile))
      {
        // The file is not in a valid format
        return;
      }

      switch (p_design.outputFileFormat)
      {
        case SES:
          // Save the file as a Specctra SES file
          boolean sesFileSaved = this.saveAsSpecctraSessionSes(this.design_file.getOutputFile(), design_file.get_name());
          // Save the rules file as well, if the user wants to
          if (sesFileSaved && WindowMessage.confirm(tm.getText("confirm_rules_save"), JOptionPane.NO_OPTION))
          {
            saveRulesAs(design_file.getRulesFile(), design_file.get_name(), board_panel.board_handling);
          }
          FRAnalytics.buttonClicked("fileio_saveses", new BoardDetails(design_file.getRulesFile()).toString());
          break;
        case DSN:
          // Save the file as a Specctra DSN file
          this.saveAsSpecctraDesignDsn(this.design_file.getOutputFile(), design_file.get_name(), false);
          FRAnalytics.buttonClicked("fileio_savedsn", this.design_file.getOutputFileDetails());
          break;
        case FRB:
          // Save the file as a freerouting binary file
          this.saveAsBinary(this.design_file.getOutputFile());
          FRAnalytics.buttonClicked("fileio_savefrb", this.design_file.getOutputFileDetails());
          break;
        case SCR:
          //  Save the file as an Eagle script file
          this.saveAsEagleScriptScr(this.design_file.getEagleScriptFile(), design_file.get_name());
          FRAnalytics.buttonClicked("fileio_savescr", new BoardDetails(design_file.getEagleScriptFile()).toString());
          break;
        default:
          // The file format is not supported
          FRLogger.warn("Saving the board failed, because the selected file format is not supported.");
          break;
      }
    });

    setJMenuBar(this.menubar);

    // Set the toolbar panel to the top of the frame, just above the canvas.
    this.toolbar_panel = new BoardToolbar(this, disabledFeatures.selectMode);
    this.add(this.toolbar_panel, BorderLayout.NORTH);

    // Create and move the status bar one-liners (like current layer, cursor position, etc.) below the canvas.
    this.message_panel = new BoardPanelStatus(this.locale);
    this.add(this.message_panel, BorderLayout.SOUTH);

    this.message_panel.addErrorOrWarningLabelClickedListener(() ->
    {
      LogEntries logEntries = FRLogger.getLogEntries();

      // Filter the log entries that are not errors or warnings
      LogEntries filteredLogEntries = new LogEntries();
      for (LogEntry entry : logEntries.getEntries())
      {
        if (entry.getType() == LogEntryType.Error || entry.getType() == LogEntryType.Warning || entry.getType() == LogEntryType.Info)
        {
          filteredLogEntries.add(entry.getType(), entry.getMessage());
        }
      }

      // Show a dialog box with the latest log entries
      JTextArea textArea = new JTextArea(filteredLogEntries.getAsString());
      JScrollPane scrollPane = new JScrollPane(textArea);
      scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
      scrollPane.setPreferredSize(new Dimension(1000, 600));

      int messageType = (filteredLogEntries.getErrorCount() > 0) ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;

      JOptionPane.showMessageDialog(null, scrollPane, tm.getText("logs_window_title"), messageType);

    });

    // DEPRECATED: we don't use this toolbar anymore
    this.select_toolbar = new BoardToolbarSelectedItem(this);

    // Screen messages are displayed in the status bar, below the canvas.
    this.screen_messages = new ScreenMessages(this.message_panel.errorLabel, this.message_panel.warningLabel, this.message_panel.statusMessage, this.message_panel.additionalMessage, this.message_panel.currentLayer, this.message_panel.mousePosition, this.message_panel.unitLabel, this.locale);

    // The scroll pane for the canvas of the routing board.
    this.scroll_pane = new JScrollPane();
    this.scroll_pane.setPreferredSize(new Dimension(1150, 800));
    this.scroll_pane.setVerifyInputWhenFocusTarget(false);
    this.add(scroll_pane, BorderLayout.CENTER);

    this.board_panel = new BoardPanel(screen_messages, this, p_locale, p_save_intermediate_stages, p_optimization_improvement_threshold);
    this.scroll_pane.setViewportView(board_panel);

    this.addWindowListener(new WindowStateListener());

    this.addBoardLoadedEventListener((RoutingBoard board) ->
    {
      boolean isBoardEmpty = (board == null) || (board.components.count() == 0);
      this.menubar.fileMenu.file_save_as_menuitem.setEnabled(!isBoardEmpty);
      this.menubar.appereanceMenu.setEnabled(!isBoardEmpty);
      this.menubar.settingsMenu.setEnabled(!isBoardEmpty);
      this.menubar.rulesMenu.setEnabled(!isBoardEmpty);
      this.menubar.infoMenu.setEnabled(!isBoardEmpty);

      this.toolbar_panel.setEnabled(!isBoardEmpty);
    });

    this.updateTexts();
    this.pack();
  }

  @Override
  public void updateTexts()
  {
    if ((this.design_file == null) || (this.design_file.getOutputFile() == null))
    {
      this.setTitle(tm.getText("title", MainApplication.globalSettings.version));
    }
    else
    {
      this.setTitle(design_file.get_name() + " - " + tm.getText("title", MainApplication.globalSettings.version));
    }
  }

  /**
   * Reads interactive actions from a logfile.
   */
  void read_logfile(InputStream p_input_stream)
  {
    board_panel.board_handling.read_logfile(p_input_stream);
  }

  /**
   * Reads an existing board design from file. If isSpecctraDsn, the design is read from a specctra
   * dsn file. Returns false, if the file is invalid.
   */
  boolean load(InputStream p_input_stream, boolean isSpecctraDsn, JTextField p_message_field)
  {
    Point viewport_position = null;
    DsnFile.ReadResult read_result = null;

    board_panel.reset_board_handling();

    if (isSpecctraDsn)
    {
      read_result = board_panel.board_handling.loadFromSpecctraDsn(p_input_stream, this.board_observers, new ItemIdNoGenerator());

      // If the file was read successfully, initialize the windows
      if (read_result == DsnFile.ReadResult.OK)
      {
        viewport_position = new Point(0, 0);
        initialize_windows();

        // Raise an event to notify the observers that a new board has been loaded
        this.boardLoadedEventListeners.forEach(listener -> listener.accept(board_panel.board_handling.get_routing_board()));
      }
    }
    else
    {
      ObjectInputStream object_stream;
      try
      {
        object_stream = new ObjectInputStream(p_input_stream);
      } catch (IOException e)
      {
        return false;
      }
      boolean read_ok = board_panel.board_handling.loadFromBinary(object_stream);
      if (!read_ok)
      {
        return false;
      }

      // Raise an event to notify the observers that a new board has been loaded
      this.boardLoadedEventListeners.forEach(listener -> listener.accept(board_panel.board_handling.get_routing_board()));

      // Read and set the GUI settings from the binary file
      Point frame_location;
      Rectangle frame_bounds;
      try
      {
        viewport_position = (Point) object_stream.readObject();
        frame_location = (Point) object_stream.readObject();
        frame_bounds = (Rectangle) object_stream.readObject();
      } catch (Exception e)
      {
        return false;
      }
      this.setLocation(frame_location);
      this.setBounds(frame_bounds);

      allocate_permanent_subwindows();

      for (int i = 0; i < this.permanent_subwindows.length; ++i)
      {
        this.permanent_subwindows[i].read(object_stream);
      }
    }

    try
    {
      p_input_stream.close();
    } catch (IOException e)
    {
      return false;
    }

    return update_gui(isSpecctraDsn, read_result, viewport_position, p_message_field);
  }

  private boolean update_gui(boolean isSpecctraDsn, DsnFile.ReadResult read_result, Point viewport_position, JTextField p_message_field)
  {
    if (isSpecctraDsn)
    {
      if (read_result != DsnFile.ReadResult.OK)
      {
        if (p_message_field != null)
        {
          if (read_result == DsnFile.ReadResult.OUTLINE_MISSING)
          {
            p_message_field.setText(tm.getText("error_7"));
          }
          else
          {
            p_message_field.setText(tm.getText("error_6"));
          }
        }
        return false;
      }
    }

    Dimension panel_size = board_panel.board_handling.graphics_context.get_panel_size();
    board_panel.setSize(panel_size);
    board_panel.setPreferredSize(panel_size);
    if (viewport_position != null)
    {
      board_panel.set_viewport_position(viewport_position);
    }
    board_panel.create_popup_menus();
    board_panel.init_colors();
    board_panel.board_handling.create_ratsnest();
    this.setToolbarModeSelectionPanelValue(board_panel.board_handling.get_interactive_state());
    this.setToolbarUnitSelectionPanelValue(board_panel.board_handling.coordinate_transform.user_unit);
    this.setVisible(true);
    if (isSpecctraDsn)
    {
      // Read the default gui settings, if gui default file exists.
      InputStream input_stream = null;
      boolean defaults_file_found;

      File defaults_file = new File(this.design_file.getInputFileDirectoryOrNull(), GUI_DEFAULTS_FILE_NAME);
      defaults_file_found = true;
      try
      {
        input_stream = new FileInputStream(defaults_file);
      } catch (FileNotFoundException e)
      {
        defaults_file_found = false;
      }

      if (defaults_file_found)
      {
        boolean read_ok = GUIDefaultsFile.read(this, board_panel.board_handling, input_stream);
        if (!read_ok)
        {
          screen_messages.set_status_message(tm.getText("error_1"));
        }
        try
        {
          input_stream.close();
        } catch (IOException e)
        {
          return false;
        }
      }
      this.zoom_all();
    }
    return true;
  }

  public boolean load_intermediate_stage_file()
  {
    try
    {
      FileInputStream input_stream = new FileInputStream(this.design_file.getSnapshotFile());
      return this.load(input_stream, false, null);
    } catch (IOException e)
    {
      screen_messages.set_status_message(tm.getText("error_2"));
      return false;
    } catch (Exception e)
    {
      screen_messages.set_status_message(tm.getText("error_3"));
      return false;
    }
  }


  public boolean save_intermediate_stage_file()
  {
    if ((intermediate_stage_file_last_saved_at != null) && (intermediate_stage_file_last_saved_at.plusSeconds(30).isAfter(LocalDateTime.now())))
    {
      return false;
    }

    intermediate_stage_file_last_saved_at = LocalDateTime.now();
    return saveAsBinary(this.design_file.getSnapshotFile());
  }

  public boolean delete_intermediate_stage_file()
  {
    return this.design_file.getSnapshotFile().delete();
  }

  public boolean is_intermediate_stage_file_available()
  {
    return (this.design_file.getSnapshotFile().exists() && this.design_file.getSnapshotFile().canRead());
  }

  public LocalDateTime get_intermediate_stage_file_modification_time()
  {
    long lastModified = this.design_file.getSnapshotFile().lastModified();
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault());
  }

  /**
   * Saves the board, GUI settings and subwindows to disk as a binary file.
   * Returns false, if the save failed.
   */
  private boolean saveAsBinary(File outputFile)
  {
    if (outputFile == null)
    {
      return false;
    }

    OutputStream output_stream;
    ObjectOutputStream object_stream;
    try
    {
      FRLogger.info("Saving '" + outputFile.getPath() + "'...");

      output_stream = new FileOutputStream(outputFile);
      object_stream = new ObjectOutputStream(output_stream);
    } catch (IOException e)
    {
      screen_messages.set_status_message(tm.getText("error_2"));
      return false;
    } catch (Exception e)
    {
      screen_messages.set_status_message(tm.getText("error_3"));
      return false;
    }

    // (1) Save the board as binary file
    boolean save_ok = board_panel.board_handling.saveAsBinary(object_stream);
    if (!save_ok)
    {
      return false;
    }

    // (2) Save the GUI settings as binary file
    try
    {
      object_stream.writeObject(board_panel.get_viewport_position());
      object_stream.writeObject(this.getLocation());
      object_stream.writeObject(this.getBounds());
    } catch (IOException e)
    {
      screen_messages.set_status_message(tm.getText("message_gui_settings_save_failed", outputFile.getPath()));
      return false;
    }

    // (3) Save the permanent subwindows as binary file
    for (int i = 0; i < this.permanent_subwindows.length; ++i)
    {
      this.permanent_subwindows[i].save(object_stream);
    }

    // (4) Flush the binary file
    try
    {
      object_stream.flush();
      output_stream.close();
    } catch (IOException e)
    {
      screen_messages.set_status_message(tm.getText("message_binary_file_save_failed", outputFile.getPath()));
      return false;
    }
    return true;
  }

  /**
   * Writes a Specctra Session File (SES). Returns false, if write operation fails.
   */
  public boolean saveAsSpecctraSessionSes(File outputFile, String designName)
  {
    if (outputFile == null)
    {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    OutputStream output_stream;
    try
    {
      output_stream = new FileOutputStream(outputFile);
    } catch (Exception e)
    {
      output_stream = null;
    }

    if (!board_panel.board_handling.saveAsSpecctraSessionSes(output_stream, designName))
    {

      this.screen_messages.set_status_message(tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
      return false;
    }

    this.screen_messages.set_status_message(tm.getText("message_specctra_ses_saved", outputFile.getPath()));

    return true;
  }

  /**
   * Saves the board rule to file, so that they can be reused later on.
   */
  private boolean saveRulesAs(File rulesFile, String designName, BoardHandling p_board_handling)
  {
    FRLogger.info("Saving '" + rulesFile.getPath() + "'...");

    OutputStream outputStream;
    try
    {
      outputStream = new FileOutputStream(rulesFile);
    } catch (IOException e)
    {
      FRLogger.error("unable to create rules file", e);
      return false;
    }

    RulesFile.write(p_board_handling, outputStream, designName);
    return true;
  }

  public void saveAsEagleScriptScr(File outputFile, String design_name)
  {
    ByteArrayOutputStream sesOutputStream = new ByteArrayOutputStream();
    if (!board_panel.board_handling.saveAsSpecctraSessionSes(sesOutputStream, design_name))
    {
      return;
    }
    InputStream sesInputStream = new ByteArrayInputStream(sesOutputStream.toByteArray());

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");

    OutputStream output_stream;
    try
    {
      output_stream = new FileOutputStream(outputFile);
    } catch (Exception e)
    {
      output_stream = null;
    }

    if (board_panel.board_handling.saveSpecctraSessionSesAsEagleScriptScr(sesInputStream, output_stream))
    {
      screen_messages.set_status_message(tm.getText("message_eagle_saved", outputFile.getPath()));
    }
    else
    {
      screen_messages.set_status_message(tm.getText("message_eagle_save_failed", outputFile.getPath()));
    }
  }

  /**
   * Writes a Specctra Design File (DSN). Returns false, if write operation fails.
   */
  public boolean saveAsSpecctraDesignDsn(File outputFile, String designName, boolean compatibilityMode)
  {
    if (outputFile == null)
    {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    OutputStream output_stream;
    try
    {
      output_stream = new FileOutputStream(outputFile);
    } catch (Exception e)
    {
      output_stream = null;
    }

    return board_panel.board_handling.saveAsSpecctraDesignDsn(output_stream, designName, compatibilityMode);
  }

  /**
   * Sets contexts sensitive help for the input component, if the help system is used.
   */
  @Deprecated
  public void set_context_sensitive_help(Component p_component, String p_help_id)
  {
    if (p_component == null)
    {
      throw new NullPointerException("p_component");
    }

    //throw new UnsupportedOperationException("Context sensitive help is disabled.");
  }

  /**
   * Sets the toolbar to the buttons of the selected item state.
   */
  public void set_select_toolbar()
  {
    getContentPane().remove(toolbar_panel);
    getContentPane().add(select_toolbar, BorderLayout.NORTH);
    repaint();
  }

  /**
   * Sets the toolbar buttons to the select. route and drag menu buttons of the main menu.
   */
  public void set_menu_toolbar()
  {
    getContentPane().remove(select_toolbar);
    getContentPane().add(toolbar_panel, BorderLayout.NORTH);
    repaint();
  }

  /**
   * Calculates the absolute location of the board frame in his outmost parent frame.
   */
  Point absolute_panel_location()
  {
    int x = this.scroll_pane.getX();
    int y = this.scroll_pane.getY();
    Container curr_parent = this.scroll_pane.getParent();
    while (curr_parent != null)
    {
      x += curr_parent.getX();
      y += curr_parent.getY();
      curr_parent = curr_parent.getParent();
    }
    return new Point(x, y);
  }

  /**
   * Sets the displayed region to the whole board.
   */
  public void zoom_all()
  {
    board_panel.board_handling.adjust_design_bounds();
    Rectangle display_rect = board_panel.get_viewport_bounds();
    Rectangle design_bounds = board_panel.board_handling.graphics_context.get_design_bounds();
    double width_factor = display_rect.getWidth() / design_bounds.getWidth();
    double height_factor = display_rect.getHeight() / design_bounds.getHeight();
    double zoom_factor = Math.min(width_factor, height_factor);
    Point2D zoom_center = board_panel.board_handling.graphics_context.get_design_center();
    board_panel.zoom(zoom_factor, zoom_center);
    Point2D new_vieport_center = board_panel.board_handling.graphics_context.get_design_center();
    board_panel.set_viewport_center(new_vieport_center);
  }

  /**
   * Actions to be taken when this frame vanishes.
   */
  @Override
  public void dispose()
  {
    for (int i = 0; i < this.permanent_subwindows.length; ++i)
    {
      if (this.permanent_subwindows[i] != null)
      {
        this.permanent_subwindows[i].dispose();
        this.permanent_subwindows[i] = null;
      }
    }
    for (BoardTemporarySubWindow curr_subwindow : this.temporary_subwindows)
    {
      if (curr_subwindow != null)
      {
        curr_subwindow.board_frame_disposed();
      }
    }
    if (board_panel.board_handling != null)
    {
      board_panel.board_handling.dispose();
      board_panel.board_handling = null;
    }
    super.dispose();
  }

  private void allocate_permanent_subwindows()
  {
    this.color_manager = new ColorManager(this);
    this.permanent_subwindows[0] = this.color_manager;
    this.object_visibility_window = WindowObjectVisibility.get_instance(this);
    this.permanent_subwindows[1] = this.object_visibility_window;
    this.layer_visibility_window = WindowLayerVisibility.get_instance(this);
    this.permanent_subwindows[2] = this.layer_visibility_window;
    this.display_misc_window = new WindowDisplayMisc(this);
    this.permanent_subwindows[3] = this.display_misc_window;
    this.snapshot_window = new WindowSnapshot(this);
    this.permanent_subwindows[4] = this.snapshot_window;
    this.route_parameter_window = new WindowRouteParameter(this);
    this.permanent_subwindows[5] = this.route_parameter_window;
    this.select_parameter_window = new WindowSelectParameter(this);
    this.permanent_subwindows[6] = this.select_parameter_window;
    this.clearance_matrix_window = new WindowClearanceMatrix(this);
    this.permanent_subwindows[7] = this.clearance_matrix_window;
    this.padstacks_window = new WindowPadstacks(this);
    this.permanent_subwindows[8] = this.padstacks_window;
    this.packages_window = new WindowPackages(this);
    this.permanent_subwindows[9] = this.packages_window;
    this.components_window = new WindowComponents(this);
    this.permanent_subwindows[10] = this.components_window;
    this.incompletes_window = new WindowIncompletes(this);
    this.permanent_subwindows[11] = this.incompletes_window;
    this.clearance_violations_window = new WindowClearanceViolations(this);
    this.permanent_subwindows[12] = this.clearance_violations_window;
    this.net_info_window = new WindowNets(this);
    this.permanent_subwindows[13] = this.net_info_window;
    this.via_window = new WindowVia(this);
    this.permanent_subwindows[14] = this.via_window;
    this.edit_vias_window = new WindowEditVias(this);
    this.permanent_subwindows[15] = this.edit_vias_window;
    this.edit_net_rules_window = new WindowNetClasses(this);
    this.permanent_subwindows[16] = this.edit_net_rules_window;
    this.assign_net_classes_window = new WindowAssignNetClass(this);
    this.permanent_subwindows[17] = this.assign_net_classes_window;
    this.length_violations_window = new WindowLengthViolations(this);
    this.permanent_subwindows[18] = this.length_violations_window;
    this.about_window = new WindowAbout(this.locale);
    this.permanent_subwindows[19] = this.about_window;
    this.move_parameter_window = new WindowMoveParameter(this);
    this.permanent_subwindows[20] = this.move_parameter_window;
    this.unconnected_route_window = new WindowUnconnectedRoute(this);
    this.permanent_subwindows[21] = this.unconnected_route_window;
    this.route_stubs_window = new WindowRouteStubs(this);
    this.permanent_subwindows[22] = this.route_stubs_window;
    this.autoroute_parameter_window = new WindowAutorouteParameter(this);
    this.permanent_subwindows[23] = this.autoroute_parameter_window;
  }

  /**
   * Creates the additional frames of the board frame.
   */
  private void initialize_windows()
  {
    allocate_permanent_subwindows();

    this.setLocation(120, 0);

    this.select_parameter_window.setLocation(0, 0);
    this.select_parameter_window.setVisible(true);

    this.route_parameter_window.setLocation(0, 100);
    this.autoroute_parameter_window.setLocation(0, 200);
    this.move_parameter_window.setLocation(0, 50);
    this.clearance_matrix_window.setLocation(0, 150);
    this.via_window.setLocation(50, 150);
    this.edit_vias_window.setLocation(100, 150);
    this.edit_net_rules_window.setLocation(100, 200);
    this.assign_net_classes_window.setLocation(100, 250);
    this.padstacks_window.setLocation(100, 30);
    this.packages_window.setLocation(200, 30);
    this.components_window.setLocation(300, 30);
    this.incompletes_window.setLocation(400, 30);
    this.clearance_violations_window.setLocation(500, 30);
    this.length_violations_window.setLocation(550, 30);
    this.net_info_window.setLocation(350, 30);
    this.unconnected_route_window.setLocation(650, 30);
    this.route_stubs_window.setLocation(600, 30);
    this.snapshot_window.setLocation(0, 250);
    this.layer_visibility_window.setLocation(0, 450);
    this.object_visibility_window.setLocation(0, 550);
    this.display_misc_window.setLocation(0, 350);
    this.color_manager.setLocation(0, 600);
    this.about_window.setLocation(200, 200);
  }

  /**
   * Returns the currently used locale for the language dependent output.
   */
  public Locale get_locale()
  {
    return this.locale;
  }

  /**
   * Sets the background of the board panel
   */
  public void set_board_background(Color p_color)
  {
    this.board_panel.setBackground(p_color);
  }

  /**
   * Refreshes all displayed coordinates after the user unit has changed.
   */
  public void refresh_windows()
  {
    for (int i = 0; i < this.permanent_subwindows.length; ++i)
    {
      if (permanent_subwindows[i] != null)
      {
        permanent_subwindows[i].refresh();
      }
    }
  }

  /**
   * Sets the mode value on mode selection component of the toolbar
   */
  public void setToolbarModeSelectionPanelValue(InteractiveState interactiveState)
  {
    this.toolbar_panel.setModeSelectionPanelValue(interactiveState);
  }

  private void setToolbarUnitSelectionPanelValue(Unit unit)
  {
    this.toolbar_panel.setUnitSelectionPanelValue(unit);
  }


  /**
   * Restore the selected snapshot in the snapshot window.
   */
  public void goto_selected_snapshot()
  {
    if (this.snapshot_window != null)
    {
      this.snapshot_window.goto_selected();
    }
  }

  /**
   * Selects the snapshot, which is previous to the current selected snapshot. Thecurent selected
   * snapshot will be no more selected.
   */
  public void select_previous_snapshot()
  {
    if (this.snapshot_window != null)
    {
      this.snapshot_window.select_previous_item();
    }
  }

  /**
   * Selects the snapshot, which is next to the current selected snapshot. Thecurent selected
   * snapshot will be no more selected.
   */
  public void select_next_snapshot()
  {
    if (this.snapshot_window != null)
    {
      this.snapshot_window.select_next_item();
    }
  }

  /**
   * Used for storing the subwindowfilters in a snapshot.
   */
  public SubwindowSelections get_snapshot_subwindow_selections()
  {
    SubwindowSelections result = new SubwindowSelections();
    result.incompletes_selection = this.incompletes_window.get_snapshot_info();
    result.packages_selection = this.packages_window.get_snapshot_info();
    result.nets_selection = this.net_info_window.get_snapshot_info();
    result.components_selection = this.components_window.get_snapshot_info();
    result.padstacks_selection = this.padstacks_window.get_snapshot_info();
    return result;
  }

  /**
   * Used for restoring the subwindowfilters from a snapshot.
   */
  public void set_snapshot_subwindow_selections(SubwindowSelections p_filters)
  {
    this.incompletes_window.set_snapshot_info(p_filters.incompletes_selection);
    this.packages_window.set_snapshot_info(p_filters.packages_selection);
    this.net_info_window.set_snapshot_info(p_filters.nets_selection);
    this.components_window.set_snapshot_info(p_filters.components_selection);
    this.padstacks_window.set_snapshot_info(p_filters.padstacks_selection);
  }

  /**
   * Repaints this board frame and all the subwindows of the board.
   */
  public void repaint_all()
  {
    this.repaint();
    for (int i = 0; i < permanent_subwindows.length; ++i)
    {
      permanent_subwindows[i].repaint();
    }
  }

  public void addBoardLoadedEventListener(Consumer<RoutingBoard> listener)
  {
    boardLoadedEventListeners.add(listener);
  }

  public void addReadOnlyEventListener(Consumer<RoutingBoard> listener)
  {
    boardSavedEventListeners.add(listener);
  }

  /**
   * Used for storing the subwindow filters in a snapshot.
   */
  public static class SubwindowSelections implements Serializable
  {
    private WindowObjectListWithFilter.SnapshotInfo incompletes_selection;
    private WindowObjectListWithFilter.SnapshotInfo packages_selection;
    private WindowObjectListWithFilter.SnapshotInfo nets_selection;
    private WindowObjectListWithFilter.SnapshotInfo components_selection;
    private WindowObjectListWithFilter.SnapshotInfo padstacks_selection;
  }

  private class WindowStateListener extends WindowAdapter
  {
    @Override
    public void windowClosing(WindowEvent evt)
    {
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      boolean wasBoardChanged = board_panel.board_handling.isBoardChanged();
      if (wasBoardChanged)
      {
        // Create a JOptionPane with a warning icon and set the default option to NO
        Object[] options = {tm.getText("confirm_exit_yes"), tm.getText("confirm_exit_no")};
        JOptionPane optionPane = new JOptionPane(tm.getText("confirm_cancel"), JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, options[1] // Default to "No"
        );
        JDialog dialog = optionPane.createDialog(null, "Warning");
        dialog.setVisible(true);

        // Check the user's choice
        Object selectedValue = optionPane.getValue();
        if (selectedValue == null || selectedValue.equals(tm.getText("confirm_exit_no")))
        {
          setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
          FRAnalytics.buttonClicked("board_confirm_exit_dialog_no", tm.getText("confirm_cancel"));
        }
        else
        {
          try
          {
            MainApplication.saveSettings();
          } catch (IOException e)
          {
            FRLogger.error("Error saving settings to the freerouting.json file.", e);
          }

          FRAnalytics.appClosed();
        }
      }
    }

    @Override
    public void windowIconified(WindowEvent evt)
    {
      for (int i = 0; i < permanent_subwindows.length; ++i)
      {
        permanent_subwindows[i].parent_iconified();
      }
      for (BoardSubWindow curr_subwindow : temporary_subwindows)
      {
        if (curr_subwindow != null)
        {
          curr_subwindow.parent_iconified();
        }
      }
    }

    @Override
    public void windowDeiconified(WindowEvent evt)
    {
      for (BoardSavableSubWindow permanentSubwindow : permanent_subwindows)
      {
        if (permanentSubwindow != null)
        {
          permanentSubwindow.parent_deiconified();
        }
      }
      for (BoardSubWindow curr_subwindow : temporary_subwindows)
      {
        if (curr_subwindow != null)
        {
          curr_subwindow.parent_deiconified();
        }
      }
    }
  }
}