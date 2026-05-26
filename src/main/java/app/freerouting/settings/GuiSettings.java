package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GuiSettings implements Serializable {

  @SerializedName("enabled")
  public Boolean isEnabled = true;
  @SerializedName("running")
  public transient Boolean isRunning = false;
  @SerializedName("input_directory")
  public String inputDirectory = "";
  @SerializedName("dialog_confirmation_timeout")
  public int dialogConfirmationTimeout = 5;
  public transient boolean exitWhenFinished;
  private static final int MAX_RECENT_FILES = 10;
  @SerializedName("recent_files")
  public List<String> recentFiles = new ArrayList<>();
  
  public List<String> getRecentFiles() {
    if (recentFiles == null) {
      recentFiles = new ArrayList<>();
    }
    return recentFiles;
  }
  
  public void addRecentFile(String filePath) {
    if ((filePath == null) || filePath.isBlank()) {
      return;
    }
  
    List<String> files = getRecentFiles();
    String normalizedPath = filePath.trim();
    files.remove(normalizedPath);
    files.add(0, normalizedPath);
  
    if (files.size() > MAX_RECENT_FILES) {
      files.subList(MAX_RECENT_FILES, files.size()).clear();
    }
  }
}