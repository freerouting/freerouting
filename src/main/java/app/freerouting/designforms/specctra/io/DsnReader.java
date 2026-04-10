package app.freerouting.designforms.specctra.io;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.designforms.specctra.Keyword;
import app.freerouting.designforms.specctra.ReadScopeParameter;
import app.freerouting.designforms.specctra.SpecctraDsnStreamReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a Specctra DSN file and returns a fully constructed
 * {@link app.freerouting.board.BasicBoard} wrapped in a typed {@link DsnReadResult}.
 *
 * <p>This class has <em>no</em> dependency on {@link app.freerouting.interactive.BoardManager},
 * {@link app.freerouting.core.RoutingJob}, or any GUI class. Board construction happens
 * internally via an anonymous minimal shim embedded in {@link ReadScopeParameter}.
 *
 * <p>Replaces the read path previously found in
 * {@link app.freerouting.designforms.specctra.DsnFile#read} (now {@link Deprecated}).
 */
public final class DsnReader {

  private DsnReader() {
  }

  /**
   * Reads a DSN stream and returns a fully constructed board or a typed failure.
   *
   * <p>The stream is <em>closed</em> by this method once reading completes (successfully or not).
   *
   * @param inputStream source — closed by this method on completion
   * @param observers   nullable; passed through to board items for host-system embedding
   * @param idGenerator nullable; for consistent item identification in host-system embedding
   * @return one of {@link DsnReadResult.Success}, {@link DsnReadResult.OutlineMissing},
   *         {@link DsnReadResult.ParseError}, or {@link DsnReadResult.IoError}
   */
  public static DsnReadResult readBoard(
      InputStream inputStream,
      BoardObservers observers,
      IdentificationNumberGenerator idGenerator) {

    if (inputStream == null) {
      return new DsnReadResult.ParseError("(pcb", "inputStream must not be null");
    }

    // Apply default implementations for nullable parameters so the board's
    // Communication object is fully initialised even in lightweight test scenarios.
    if (observers == null) {
      observers = new BoardObserverAdaptor();
    }
    if (idGenerator == null) {
      idGenerator = new ItemIdentificationNumberGenerator();
    }

    SpecctraDsnStreamReader scanner = new SpecctraDsnStreamReader(inputStream);

    // -----------------------------------------------------------------------
    // Validate the "(pcb <name>" header — identical check to DsnFile.read
    // -----------------------------------------------------------------------
    for (int i = 0; i < 3; i++) {
      Object token;
      try {
        token = scanner.next_token();
      } catch (IOException e) {
        closeQuietly(inputStream);
        return new DsnReadResult.IoError(e);
      }
      boolean ok = true;
      if (i == 0) {
        ok = (token == Keyword.OPEN_BRACKET);
      } else if (i == 1) {
        ok = (token == Keyword.PCB_SCOPE);
        // switch the scanner to NAME mode so the pcb-name token is consumed cleanly
        scanner.yybegin(SpecctraDsnStreamReader.NAME);
      }
      if (!ok) {
        closeQuietly(inputStream);
        return new DsnReadResult.ParseError(
            "(pcb",
            "Not a Specctra DSN file: expected '(pcb <name>' header");
      }
    }

    // -----------------------------------------------------------------------
    // Parse the body — board is constructed inside ReadScopeParameter's shim
    // -----------------------------------------------------------------------
    ReadScopeParameter par = new ReadScopeParameter(scanner, observers, idGenerator);
    boolean readOk = Keyword.PCB_SCOPE.read_scope(par);

    BasicBoard board = par.getBoard();

    closeQuietly(inputStream);

    if (readOk) {
      // Apply power-plane autoroute settings if the DSN had no (autoroute ...) scope
      if (par.autoroute_settings == null) {
        DsnFile.adjustPlaneAutorouteSettings(board);
      }
      return new DsnReadResult.Success(board, null);
    } else if (!par.board_outline_ok) {
      return new DsnReadResult.OutlineMissing(board, null);
    } else {
      return new DsnReadResult.ParseError("(pcb", "DSN structure parsing failed");
    }
  }

  // -------------------------------------------------------------------------

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException _) {
      // ignore — nothing useful to do here
    }
  }
}

