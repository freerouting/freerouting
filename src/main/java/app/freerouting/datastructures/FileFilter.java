package app.freerouting.datastructures;

import java.io.File;

/**
 * Used in the file chooser to filter all files which do not have an extension from the input array.
 */
public class FileFilter extends javax.swing.filechooser.FileFilter {

  private final String[] extensions;

  /** Creates a new FileFilter for the input extension */
  public FileFilter(String[] pExtensions) {
    extensions = pExtensions;
  }

  @Override
  public String getDescription() {
    StringBuilder message = new StringBuilder("Files with the extensions");
    for (int i = 0; i < extensions.length; i++) {
      message.append(" .").append(extensions[i]);
      if (i == extensions.length - 2) {
        message.append(" or");
      } else if (i < extensions.length - 2) {
        message.append(",");
      }
    }
    return message.toString();
  }

  @Override
  public boolean accept(File pFile) {
    if (pFile.isDirectory()) {
      return true;
    }
    String fileName = pFile.getName();
    String[] nameParts = fileName.split("\\.");
    if (nameParts.length < 2) {
      return false;
    }
    String foundExtension = nameParts[nameParts.length - 1];
    for (int i = 0; i < extensions.length; i++) {
      if (foundExtension.equalsIgnoreCase(extensions[i])) {
        return true;
      }
    }
    return false;
  }
}
