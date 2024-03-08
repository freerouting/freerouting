package app.freerouting.gui;

import app.freerouting.designforms.specctra.RulesFile;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.logger.FRLogger;

import javax.swing.JFileChooser;
import java.awt.Component;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ResourceBundle;
import java.util.zip.CRC32;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * File functionality with security restrictions used, when the application is opened with Java
 * Webstart
 */
public class DesignFile {
  public static final String dsn_file_extension = "dsn";
  public static final String binary_file_extension = "frb";
  private static final String RULES_FILE_EXTENSION = "rules";
  public FileFormat inputFileFormat = FileFormat.DSN;
  private final File input_file;
  private final File intermediate_snapshot_file;
  private File output_file;

  /**
   * Creates a new instance of DesignFile and prepares the intermediate file handling.
   */
  public DesignFile(File p_design_file) {
    this.input_file = p_design_file;
    this.output_file = p_design_file;

    if (p_design_file == null) {
      this.intermediate_snapshot_file = null;
      return;
    }

    String filename = p_design_file.getName();
    String[] name_parts = filename.split("\\.");

    // Check if the file has an extension
    String extension = null;
    if (name_parts.length > 1)
    {
      extension = name_parts[name_parts.length - 1].toLowerCase();
      filename = filename.substring(0, filename.length() - extension.length() - 1);
    }

    switch (extension) {
      case dsn_file_extension:
        this.inputFileFormat = FileFormat.DSN;
        break;
      case binary_file_extension:
        this.inputFileFormat = FileFormat.FRB;
        break;
      default:
        this.inputFileFormat = FileFormat.UNKNOWN;
        break;
    }

    // Set the binary output file name
    if (extension.equals(binary_file_extension)) {
      String binary_output_file_name = filename + "." + binary_file_extension;
      this.output_file = new File(p_design_file.getParent(), binary_output_file_name);
    }

    // Set the intermediate snapshot file name

    // Calculate the CRC32 checksum of the input file
    long crc32_checksum;
    try (FileInputStream inputStream = new FileInputStream(this.input_file.getAbsoluteFile())) {
      CRC32 crc = new CRC32();
      int cnt;
      while ((cnt = inputStream.read()) != -1) {
        crc.update(cnt);
      }
      crc32_checksum = crc.getValue();
    } catch (IOException e) {
      crc32_checksum = 0;
    }

    // We have a valid checksum, we can generate the intermediate snapshot file
    if (crc32_checksum == 0) {
      this.intermediate_snapshot_file = null;
      return;
    }

    String temp_folder_path = System.getProperty("java.io.tmpdir");
    String intermediate_snapshot_file_name = "freerouting-" + Long.toHexString(crc32_checksum) + "." + DesignFile.binary_file_extension;
    this.intermediate_snapshot_file = new File(temp_folder_path + File.separator + intermediate_snapshot_file_name);
  }

  public static DesignFile get_instance(String p_design_file_name) {
    if (p_design_file_name == null) {
      return null;
    }
    return new DesignFile(new File(p_design_file_name));
  }

  /**
   * Shows a file chooser for opening a design file.
   */
  public static File open_dialog(String p_default_directory, Component p_parent) {
    JFileChooser fileChooser = new JFileChooser(p_default_directory);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    // Add the file filter for SPECCTRA Design .DSN files
    FileNameExtensionFilter dsnFilter = new FileNameExtensionFilter("SPECCTRA Design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter = new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Set a file filter as the default one
    fileChooser.setFileFilter(dsnFilter);

    fileChooser.showOpenDialog(p_parent);
    return fileChooser.getSelectedFile();
  }

  public static boolean read_rules_file(
      String p_design_name,
      String p_parent_name,
      String rules_file_name,
      BoardHandling p_board_handling,
      String p_confirm_message) {

    boolean dsn_file_generated_by_host =
        p_board_handling.get_routing_board()
            .communication
            .specctra_parser_info
            .dsn_file_generated_by_host;

    try {
      File rules_file = new File(p_parent_name, rules_file_name);
      FRLogger.info("Opening '" + rules_file_name + "'...");
      InputStream input_stream = new FileInputStream(rules_file);
      if (dsn_file_generated_by_host && WindowMessage.confirm(p_confirm_message)) {
        return RulesFile.read(input_stream, p_design_name, p_board_handling);
      }
    } catch (IOException e) {
      FRLogger.error("File '" + rules_file_name + "' was not found.", null);
    }
    return false;
  }

  /** Gets an InputStream from the file. Returns null, if the algorithm failed. */
  public InputStream get_input_stream() {
    if (this.input_file == null) {
      return null;
    }
    try {
      return new FileInputStream(this.input_file);
    } catch (Exception e) {
      FRLogger.error(e.getLocalizedMessage(), e);
    }
    return null;
  }

  /** Gets the file name as a String. Returns null on failure. */
  public String get_name() {
    if (this.input_file != null) {
      return this.input_file.getName();
    }
    return "";
  }

  public File save_as_dialog(String p_default_directory, Component p_parent)
  {
    String directoryName;
    if (this.output_file == null) {
      directoryName = p_default_directory;
    } else {
      directoryName = this.output_file.getParent();
    }

    JFileChooser fileChooser = new JFileChooser(directoryName);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    // Add the file filter for SPECCTRA Session .SES files
    FileNameExtensionFilter sesFilter = new FileNameExtensionFilter("SPECCTRA Session file (*.ses)", "ses");
    fileChooser.addChoosableFileFilter(sesFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter = new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Add the file filter for Eagle script .SCR files
    FileNameExtensionFilter scrFilter = new FileNameExtensionFilter("Eagle Session Script file (*.scr)", "scr");
    fileChooser.addChoosableFileFilter(scrFilter);

    // Set a file filter as the default one
    fileChooser.setFileFilter(sesFilter);

    fileChooser.showSaveDialog(p_parent);

    return fileChooser.getSelectedFile();
  }

  public void saveAs(File file, BoardFrame p_board_frame)
  {
    final ResourceBundle resources = ResourceBundle.getBundle("app.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());

//    if (file == null) {
//      throw new IllegalArgumentException("File must be non-null");
//      p_board_frame.screen_messages.set_status_message(resources.getString("message_1"));
//      return;
//    }
//
//    String new_file_name = file.getName();
//    FRLogger.info("Saving '" + new_file_name + "'...");
//    String[] new_name_parts = new_file_name.split("\\.");
//    String found_file_extension = new_name_parts[new_name_parts.length - 1].toLowerCase();
//
//    switch (found_file_extension)
//    {
//      case binary_file_extension:
//        // Save as binary file
//        p_board_frame.screen_messages.set_status_message(resources.getString("message_2") + " " + file.getName());
//        this.output_file = file;
//        p_board_frame.saveAsBinary();
//        break;
//      case dsn_file_extension:
//        OutputStream output_stream;
//        try {
//          output_stream = new FileOutputStream(file);
//        } catch (Exception e) {
//          output_stream = null;
//        }
//
//        String design_name = file.toString();
//        boolean couldSaveAsDSN = p_board_frame.board_panel.board_handling.export_to_dsn_file(output_stream, design_name, false);
//
//        if (couldSaveAsDSN) {
//          p_board_frame.screen_messages.set_status_message(
//              resources.getString("message_4")
//                  + " "
//                  + new_file_name
//                  + " "
//                  + resources.getString("message_5"));
//        } else {
//          p_board_frame.screen_messages.set_status_message(
//              resources.getString("message_6")
//                  + " "
//                  + new_file_name
//                  + " "
//                  + resources.getString("message_7"));
//        }
//        break;
//      default:
//        p_board_frame.screen_messages.set_status_message(resources.getString("message_3"));
//        break;
//    }
  }

  /**
   * Writes a Specctra Session File to update the design file in the host system. Returns false, if
   * the write failed
   */
  public boolean write_specctra_session_file(BoardFrame p_board_frame) {
    final ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
    String design_file_name = this.get_name();
    String[] file_name_parts = design_file_name.split("\\.", 2);
    String design_name = file_name_parts[0];

    String output_file_name = design_name + ".ses";
    FRLogger.info("Saving '" + output_file_name + "'...");
    File curr_output_file = new File(get_parent(), output_file_name);
    OutputStream output_stream;
    try {
      output_stream = new FileOutputStream(curr_output_file);
    } catch (Exception e) {
      output_stream = null;
    }

    if (!p_board_frame.board_panel.board_handling.export_specctra_session_file(
        design_file_name, output_stream)) {
      p_board_frame.screen_messages.set_status_message(
          resources.getString("message_13")
              + " "
              + output_file_name
              + " "
              + resources.getString("message_7"));
      return false;
    }

    p_board_frame.screen_messages.set_status_message(
        resources.getString("message_11")
            + " "
            + output_file_name
            + " "
            + resources.getString("message_12"));

    if (WindowMessage.confirm(resources.getString("confirm"))) {
      return write_rules_file(design_name, p_board_frame.board_panel.board_handling);
    }
    return true;
  }

  /** Saves the board rule to file, so that they can be reused later on. */
  private boolean write_rules_file(
      String p_design_name, BoardHandling p_board_handling) {
    String rules_file_name = p_design_name + "." + RULES_FILE_EXTENSION;
    OutputStream output_stream;

    FRLogger.info("Saving '" + rules_file_name + "'...");

    File rules_file = new File(this.get_parent(), rules_file_name);
    try {
      output_stream = new FileOutputStream(rules_file);
    } catch (IOException e) {
      FRLogger.error("unable to create rules file", e);
      return false;
    }

    RulesFile.write(p_board_handling, output_stream, p_design_name);
    return true;
  }

  public void update_eagle(BoardFrame p_board_frame) {
    final ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
    String design_file_name = get_name();
    ByteArrayOutputStream session_output_stream = new ByteArrayOutputStream();
    if (!p_board_frame.board_panel.board_handling.export_specctra_session_file(
        design_file_name, session_output_stream)) {
      return;
    }
    InputStream input_stream =
        new ByteArrayInputStream(session_output_stream.toByteArray());

    String[] file_name_parts = design_file_name.split("\\.", 2);
    String design_name = file_name_parts[0];
    String output_file_name = design_name + ".scr";
    FRLogger.info("Saving '" + output_file_name + "'...");

    {
      File curr_output_file = new File(get_parent(), output_file_name);
      OutputStream output_stream;
      try {
        output_stream = new FileOutputStream(curr_output_file);
      } catch (Exception e) {
        output_stream = null;
      }

      if (p_board_frame.board_panel.board_handling.export_eagle_session_file(
          input_stream, output_stream)) {
        p_board_frame.screen_messages.set_status_message(
            resources.getString("message_14")
                + " "
                + output_file_name
                + " "
                + resources.getString("message_15"));
      } else {
        p_board_frame.screen_messages.set_status_message(
            resources.getString("message_16")
                + " "
                + output_file_name
                + " "
                + resources.getString("message_7"));
      }
    }
    if (WindowMessage.confirm(resources.getString("confirm"))) {
      write_rules_file(design_name, p_board_frame.board_panel.board_handling);
    }
  }

  public File get_output_file() {
    return this.output_file;
  }

  public File get_input_file() {
    return this.input_file;
  }

  public File get_snapshot_file() {
    return this.intermediate_snapshot_file;
  }

  // Returns the directory of the design file
  public String get_parent() {
    if (input_file != null) {
      return input_file.getParent();
    }
    return null;
  }

  public File get_parent_file() {
    if (input_file != null) {
      return input_file.getParentFile();
    }
    return null;
  }

  public boolean is_created_from_text_file() {
    return inputFileFormat.equals(FileFormat.DSN);
  }

  public String get_directory()
  {
    if (input_file == null) {
      return "";
    }

    // Get the absolut path without the filename
    return input_file.getParent();
  }
}
