package app.freerouting.core;

import app.freerouting.board.BasicBoard;
import app.freerouting.core.events.BoardFileDetailsUpdatedEvent;
import app.freerouting.core.events.BoardFileDetailsUpdatedEventListener;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.gui.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public class BoardFileDetails implements Serializable {

  protected final transient List<BoardFileDetailsUpdatedEventListener> updatedEventListeners = new ArrayList<>();
  // The size of the file in bytes
  @SerializedName("size")
  public long size;
  // The CRC32 checksum of the data
  @SerializedName("crc32")
  public long crc32;
  // The format of the file
  @SerializedName("format")
  public FileFormat format = FileFormat.UNKNOWN;
  @SerializedName("statistics")
  public BoardStatistics statistics = new BoardStatistics();
  // The filename only without the path
  @SerializedName("filename")
  protected String filename = "";
  // The absolute path to the directory of the file
  @SerializedName("path")
  protected String directoryPath = "";
  protected transient byte[] dataBytes = new byte[0];


  public BoardFileDetails() {
  }

  /**
   * Creates a new BoardDetails object from a file.
   */
  public BoardFileDetails(File file) {
    this.setFilename(file.getAbsolutePath());

    try (FileInputStream fis = new FileInputStream(file)) {
      this.setData(fis.readAllBytes());
    } catch (IOException _) {
      // Ignore the exception and continue with the default values
    }
  }

  /**
   * Creates a new BoardDetails object from a RoutingBoard object.
   */
  public BoardFileDetails(BasicBoard board) {
    this.statistics = new BoardStatistics(board);
  }

  public static CRC32 calculateCrc32(InputStream inputStream) {
    CRC32 crc = new CRC32();
    try {
      int cnt;
      while ((cnt = inputStream.read()) != -1) {
        crc.update(cnt);
      }
    } catch (IOException e) {
      FRLogger.error(e.getLocalizedMessage(), e);
    }
    return crc;
  }

  /**
   * Saves this object to a UTF-8 JSON file.
   */
  public void saveAs(String filename) throws IOException {
    try (Writer writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8)) {
      writer.write(this.toString());
    }
  }

  public String getAbsolutePath() {
    return Path
        .of(this.directoryPath, this.filename)
        .toString();
  }

  public ByteArrayInputStream getData() {
    return new ByteArrayInputStream(this.dataBytes);
  }

  public void setData(byte[] data) {
    this.dataBytes = data;
    this.size = data.length;
    InputStream inputStream = new ByteArrayInputStream(this.dataBytes);
    this.crc32 = BoardFileDetails
        .calculateCrc32(inputStream)
        .getValue();

    // read the file contents to determine the file format
    this.format = RoutingJob.getFileFormat(this.dataBytes);

    // set the statistical data based on the file content
    this.statistics = new BoardStatistics(this.dataBytes, this.format);

    fireUpdatedEvent();
  }

  /**
   * Returns a JSON representation of this object.
   */
  public String toString() {
    return GsonProvider.GSON.toJson(this);
  }

  public File getFile() {
    if (!this.filename.isEmpty()) {
      return new File(Path
          .of(this.directoryPath, this.filename)
          .toString());
    }
    return null;
  }

  public String getDirectoryPath() {
    return this.directoryPath;
  }

  public String getFilename() {
    return this.filename;
  }

  /**
   * Sets both the filename and the path.*
   *
   * @param filename The filename to set, optionally with its path.
   */
  public void setFilename(String filename) {
    if (filename == null) {
      this.directoryPath = "";
      this.filename = "";
      return;
    }

    var path = Path
        .of(filename)
        .toAbsolutePath();

    if (filename.contains(File.separator)) {
      // separate the filename into its absolute path and its filename only
      this.directoryPath = path
          .getParent()
          .toString();
      // replace the redundant "\.\" with a simple "\"
      this.directoryPath = this.directoryPath.replace("\\.\\", "\\");
      // remove the "/", "\" from the end of the directory path
      this.directoryPath = this.directoryPath.replaceAll("[/\\\\]+$", "");
      // remove the "\." from the end of the directory path
      this.directoryPath = this.directoryPath.replaceAll("\\\\.$", "");
    } else {
      this.directoryPath = "";
    }

    // set the filename only
    this.filename = path
        .getFileName()
        .toString();

    if (this.format == FileFormat.UNKNOWN) {
      // try to read the file contents to determine the file format
      this.format = RoutingJob.getFileFormat(Path.of(this.filename));
    }

    // add the default file extension if it is missing
    if ((this.format != FileFormat.UNKNOWN) && (!this.filename.contains("."))) {
      String extension = "";
      switch (this.format) {
        case SES:
          extension = "ses";
          break;
        case DSN:
          extension = "dsn";
          break;
        case FRB:
          extension = "frb";
          break;
        case RULES:
          extension = "rules";
          break;
        case SCR:
          extension = "scr";
          break;
        default:
          break;
      }

      if (!extension.isEmpty()) {
        this.filename = this.filename + "." + extension;
      }
    }

    fireUpdatedEvent();
  }

  public String getFilenameWithoutExtension() {
    if (this.filename.contains(".")) {
      return this.filename.substring(0, this.filename.lastIndexOf('.'));
    }
    return this.filename;
  }

  public void addUpdatedEventListener(BoardFileDetailsUpdatedEventListener listener) {
    updatedEventListeners.add(listener);
  }

  public void fireUpdatedEvent() {
    BoardFileDetailsUpdatedEvent event = new BoardFileDetailsUpdatedEvent(this, this);
    for (BoardFileDetailsUpdatedEventListener listener : updatedEventListeners) {
      listener.onBoardFileDetailsUpdated(event);
    }
  }

}