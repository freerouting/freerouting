package app.freerouting.datastructures;

/**
 * Used in the file chooser to filter all files which do not have an extension from the input array.
 */
public class FileFilter extends javax.swing.filechooser.FileFilter {
  private final String[] extensions;

  /** Creates a new FileFilter for the input extension */
  public FileFilter(String[] p_extensions) {
    extensions = p_extensions;
  }

  public String getDescription() {
    String message = "files with the extensions";
    for (int i = 0; i < extensions.length; ++i) {
      message += " ." + extensions[i];
      if (i == extensions.length - 2) {
        message += " or ";
      } else if (i < extensions.length - 2) {
        message += ", ";
      }
    }
    return message;
  }

  public boolean accept(java.io.File p_file) {
    if (p_file.isDirectory()) {
      return true;
    }
    String file_name = p_file.getName();
    String[] name_parts = file_name.split("\\.");
    if (name_parts.length < 2) {
      return false;
    }
    String found_extension = name_parts[name_parts.length - 1];
    for (int i = 0; i < extensions.length; ++i) {
      if (found_extension.compareToIgnoreCase(extensions[i]) == 0) {
        return true;
      }
    }
    return false;
  }
}
