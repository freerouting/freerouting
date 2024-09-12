package app.freerouting.board;

import app.freerouting.core.RoutingJob;
import app.freerouting.gui.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import jakarta.json.bind.annotation.JsonbProperty;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public class BoardFileDetails
{
  // The size of the file in bytes
  @SerializedName("size")
  public long size = 0;
  // The CRC32 checksum of the data
  @SerializedName("crc32")
  public long crc32 = 0;
  // The format of the file
  @SerializedName("format")
  public FileFormat format = FileFormat.UNKNOWN;
  @SerializedName("layer_count")
  public int layerCount = 0;
  @SerializedName("component_count")
  public int componentCount = 0;
  @SerializedName("netclass_count")
  public int netclassCount = 0;
  @SerializedName("net_count")
  public int netCount = 0;
  @SerializedName("track_count")
  public int trackCount = 0;
  @SerializedName("trace_count")
  public int traceCount = 0;
  @SerializedName("via_count")
  public int viaCount = 0;
  // The filename only without the path
  @SerializedName("filename")
  protected String filename = "";
  // The absolute path to the directory of the file
  @SerializedName("path")
  private String directoryPath = "";

  // Getter for viaCount
  public int getViaCount() {
    return viaCount;
  }

  private transient byte[] data = new byte[0];

  // Constructor
  public BoardFileDetails() {}

  // Copy constructor
  public BoardFileDetails(BoardFileDetails other) {
    this.size = other.size;
    this.crc32 = other.crc32;
    this.format = other.format;
    this.layerCount = other.layerCount;
    this.componentCount = other.componentCount;
    this.netclassCount = other.netclassCount;
    this.netCount = other.netCount;
    this.trackCount = other.trackCount;
    this.traceCount = other.traceCount;
    this.viaCount = other.viaCount;
    this.filename = other.filename;
    this.directoryPath = other.directoryPath;
    this.data = other.data.clone();
  }

  // Getter methods for private fields
  public long getSize() {
    return size;
  }

  public long getCrc32() {
    return crc32;
  }

  public FileFormat getFormat() {
    return format;
  }

  public int getLayerCount() {
    return layerCount;
  }

  public int getComponentCount() {
    return componentCount;
  }

  public int getNetclassCount() {
    return netclassCount;
  }

  public int getNetCount() {
    return netCount;
  }

  public int getTrackCount() {
    return trackCount;
  }

  public int getTraceCount() {
    return traceCount;
  }

  public String getFilename() {
    return filename;
  }

  public String getDirectoryPath() {
    return directoryPath;
  }

  /**
   * Creates a new BoardDetails object from a file.
   */
  public BoardFileDetails(File file) {
    this.setFilename(file.getAbsolutePath());

    try (FileInputStream fis = new FileInputStream(file)) {
      this.setData(fis.readAllBytes());
    } catch (IOException e) {
      // Ignore the exception and continue with the default values
      FRLogger.error("Failed to read file contents.", e);
    }

    if ((this.format == FileFormat.SES) || (this.format == FileFormat.DSN)) {
      String content = "";
      try {
        // read the content of the output file as text
        content = Files.readString(file.toPath());
      } catch (IOException e) {
        // Ignore the exception and continue with the default values
      }

      if (this.format == FileFormat.SES) {
        // get the number of components and nets in the SES file
        this.layerCount = 0;
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = 0;
        this.netCount = content.split("\\(net").length - 1;
        this.trackCount = 0;
        this.traceCount = 0;
        this.viaCount = 0;
        return;
      } else if (this.format == FileFormat.DSN) {
        // get the number of layers and nets in the DSN file
        this.layerCount = content.split("\\(layer").length - 1;
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = content.split("\\(class").length - 1;
        this.netCount = content.split("\\(net").length - 1;
        this.trackCount = content.split("\\(wire").length - 1;
        this.traceCount = 0;
        this.viaCount = content.split("\\(via").length - 1;
        return;
      }
    }

    this.layerCount = 0;
    this.componentCount = 0;
    this.netclassCount = 0;
    this.netCount = 0;
    this.trackCount = 0;
    this.traceCount = 0;
    this.viaCount = 0;
  }

  /**
   * Creates a new BoardDetails object from a RoutingBoard object.
   */
  public BoardFileDetails(BasicBoard board) {
    this.layerCount = board.get_layer_count();
    this.componentCount = board.components.count();
    this.netclassCount = 0;
    this.netCount = 0;
    this.trackCount = 0;
    this.traceCount = board.get_traces().size();
    this.viaCount = board.get_vias().size();
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
    return Path.of(this.directoryPath, this.filename).toString();
  }

  public ByteArrayInputStream getData() {
    return new ByteArrayInputStream(this.data);
  }

  public void setData(byte[] data) {
    this.data = data;
    this.size = data.length;
    InputStream inputStream = new ByteArrayInputStream(this.data);
    this.crc32 = BoardFileDetails.calculateCrc32(inputStream).getValue();

    // read the file contents to determine the file format
    this.format = RoutingJob.getFileFormat(this.data);
  }

  /**
   * Returns a JSON representation of this object.
   */
  public String toString() {
    return GsonProvider.GSON.toJson(this);
  }

  public File getFile() {
    if (!this.filename.isEmpty()) {
      return new File(Path.of(this.directoryPath, this.filename).toString());
    }
    return null;
  }

  /**
   * Sets both the filename and the path.*
   *
   * @param filename The filename to set, optionally with its path.
   */
  public void setFilename(String filename) {
    if (filename == null || filename.trim().isEmpty()) {
      this.directoryPath = "";
      this.filename = "";
      return;
    }

    Path path = Path.of(filename).toAbsolutePath().normalize();

    if (path.getParent() != null) {
      this.directoryPath = path.getParent().toString();
    } else {
      this.directoryPath = "";
    }

    this.filename = path.getFileName().toString();

    if (this.format == FileFormat.UNKNOWN) {
      this.format = RoutingJob.getFileFormat(path);
    }

    if (this.format != FileFormat.UNKNOWN && !this.filename.contains(".")) {
      this.filename += "." + getDefaultExtension(this.format);
    }
  }

  private String getDefaultExtension(FileFormat format) {
    switch (format) {
      case SES: return "ses";
      case DSN: return "dsn";
      case FRB: return "frb";
      case RULES: return "rules";
      case SCR: return "scr";
      default: return "";
    }
  }

  public String getFilenameWithoutExtension() {
    if (this.filename.contains(".")) {
      return this.filename.substring(0, this.filename.lastIndexOf('.'));
    }
    return this.filename;
  }

  public String getFileExtension() {
    int lastIndexOf = this.filename.lastIndexOf(".");
    if (lastIndexOf == -1) {
      return ""; // empty extension
    }
    return this.filename.substring(lastIndexOf + 1);
  }

  public void setSize(long size) {
    this.size = size;
  }

  public void setCrc32(long crc32) {
    this.crc32 = crc32;
  }

  public void setFormat(FileFormat format) {
    this.format = format;
  }

  public void setLayerCount(int layerCount) {
    this.layerCount = layerCount;
  }

  public void setComponentCount(int componentCount) {
    this.componentCount = componentCount;
  }

  public void setNetclassCount(int netclassCount) {
    this.netclassCount = netclassCount;
  }

  public void setNetCount(int netCount) {
    this.netCount = netCount;
  }

  public void setTrackCount(int trackCount) {
    this.trackCount = trackCount;
  }

  public void setTraceCount(int traceCount) {
    this.traceCount = traceCount;
  }

  public void setViaCount(int viaCount) {
    this.viaCount = viaCount;
  }

  public void setDirectoryPath(String directoryPath) {
    this.directoryPath = directoryPath;
  }
}
