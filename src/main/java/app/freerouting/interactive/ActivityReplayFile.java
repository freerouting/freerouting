package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

/**
 * ActivityReplayFile to track the actions in the interactive board handling for automatic replay.
 */
public class ActivityReplayFile {
  private ActivityReplayFileScanner scanner = null;
  private FileWriter file_writer = null;
  private boolean write_enabled = false;
  private Object pending_token = null;

  /** opens the ActivityReplayFile for reading */
  public boolean start_read(InputStream p_input_stream) {
    this.scanner = new ActivityReplayFileScanner(p_input_stream);
    return true;
  }

  /**
   * Reads the next corner from the ActivityReplayFile. Return null, if no valid corner is found.
   */
  public FloatPoint read_corner() {
    double x = 0;
    double y = 0;
    for (int i = 0; i < 2; ++i) {
      Object curr_ob = this.next_token();
      if (!(curr_ob instanceof Double)) {
        this.pending_token = curr_ob;
        return null;
      }
      double f = ((Double) curr_ob).doubleValue();
      if (i == 0) {
        x = f;
      } else {
        y = f;
      }
    }
    return new FloatPoint(x, y);
  }

  /** closes the ActivityReplayFile after writing */
  public void close_output() {
    if (this.file_writer != null) {
      try {
        this.file_writer.close();
      } catch (IOException e) {
        FRLogger.error("Unable to close the file", e);
      }
    }
    this.write_enabled = false;
  }

  /** opens a ActivityReplayFile for writing */
  public boolean start_write(File p_file) {
    try {
      this.file_writer = new FileWriter(p_file);
    } catch (IOException e) {
      FRLogger.error("Unable to create the file", e);
      return false;
    }
    write_enabled = true;
    return true;
  }

  /** Marks the beginning of a new item in the output stream */
  public void start_scope(ActivityReplayFileScope p_scope) {
    if (write_enabled) {
      try {
        this.file_writer.write(p_scope.name);
        this.file_writer.write("\n");
      } catch (IOException e) {
        FRLogger.error("ActivityReplayFile.start_scope: write failed", e);
      }
    }
  }

  /** Marks the beginning of a new scope in the output stream Writes also an integer value. */
  public void start_scope(ActivityReplayFileScope p_scope, int p_int_value) {
    start_scope(p_scope);
    add_int(p_int_value);
  }

  /**
   * Marks the beginning of a new scope in the output stream Writes also 1, if p_boolean_value is
   * true, or 0, if p_boolean_value is false;
   */
  public void start_scope(ActivityReplayFileScope p_scope, boolean p_boolean_value) {
    start_scope(p_scope);
    int int_value;
    if (p_boolean_value) {
      int_value = 1;
    } else {
      int_value = 0;
    }
    add_int(int_value);
  }

  /** Marks the beginning of a new item in the output stream Writes also the start corner. */
  public void start_scope(ActivityReplayFileScope p_scope, FloatPoint p_start_corner) {
    start_scope(p_scope);
    add_corner(p_start_corner);
  }

  /**
   * Reads the next scope identifier from the ActivityReplayFile. Returns null if no more item scope
   * was found.
   */
  public ActivityReplayFileScope start_read_scope() {
    Object curr_ob = this.next_token();
    if (curr_ob == null) {
      return null;
    }
    if (!(curr_ob instanceof String)) {
      FRLogger.warn("ActivityReplayFile.start_read_scope: String expected");
      this.pending_token = curr_ob;
      return null;
    }
    ActivityReplayFileScope result = ActivityReplayFileScope.get_scope((String) curr_ob);
    return result;
  }

  /** adds an int to the ActivityReplayFile */
  public void add_int(int p_int) {

    if (write_enabled) {
      try {
        this.file_writer.write((Integer.valueOf(p_int)).toString());
        this.file_writer.write("\n");
      } catch (IOException e) {
        FRLogger.error("Unable to write integer to the file", e);
      }
    }
  }

  /** Reads the next int from the ActivityReplayFile. Returns -1, if no valid integer was found. */
  public int read_int() {
    Object curr_ob = this.next_token();
    if (!(curr_ob instanceof Integer)) {
      FRLogger.warn("ActivityReplayFile.read_int: Integer expected");
      this.pending_token = curr_ob;
      return -1;
    }
    return (((Integer) curr_ob).intValue());
  }

  /** adds a FloatPoint to the ActivityReplayFile */
  public void add_corner(FloatPoint p_corner) {
    if (write_enabled) {
      if (p_corner == null) {
        FRLogger.warn("ActivityReplayFile.add_corner: p_corner is null");
        return;
      }
      try {
        this.file_writer.write((Double.valueOf(p_corner.x)).toString());
        this.file_writer.write(" ");
        this.file_writer.write((Double.valueOf(p_corner.y)).toString());
        this.file_writer.write("\n");
      } catch (IOException e) {
        FRLogger.error("Unable to write to the file  while adding corner", e);
      }
    }
  }

  private Object next_token() {
    if (this.pending_token != null) {
      Object result = this.pending_token;
      this.pending_token = null;
      return result;
    }
    try {
      Object result = this.scanner.next_token();
      return result;
    } catch (IOException e) {
      FRLogger.error("ActivityReplayFile.next_token: IO error scanning file", e);
      return null;
    }
  }
}
