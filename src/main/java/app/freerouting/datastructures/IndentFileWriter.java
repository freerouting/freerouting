package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Handles the indenting of scopes while writing to an output text file. */
public class IndentFileWriter extends OutputStreamWriter {

  private static final String INDENT_STRING = "  ";
  private static final String BEGIN_SCOPE = "(";
  private static final String END_SCOPE = ")";
  private int currentIndentLevel;

  /** Creates a new instance of IndentFileWriter */
  public IndentFileWriter(OutputStream p_stream) {
    super(p_stream, StandardCharsets.UTF_8);
  }

  /** Begins a new scope. */
  public void start_scope(boolean newLine) {
    if (newLine) {
      new_line();
    }

    try {
      write(BEGIN_SCOPE);
    } catch (IOException e) {
      FRLogger.error("IndentFileWriter.start_scope: unable to write to file", e);
    }
    ++currentIndentLevel;
  }

  public void start_scope() {
    start_scope(true);
  }

  /** Closes the latest open scope. */
  public void end_scope() {
    --currentIndentLevel;
    new_line();
    try {
      write(END_SCOPE);
    } catch (IOException e) {
      FRLogger.error("IndentFileWriter.end_scope: unable to write to file", e);
    }
  }

  /** Starts a new line inside a scope. */
  public void new_line() {
    try {
      write("\n");
      for (int i = 0; i < currentIndentLevel; i++) {
        write(INDENT_STRING);
      }
    } catch (IOException e) {
      FRLogger.error("IndentFileWriter.newLine: unable to write to file", e);
    }
  }
}
