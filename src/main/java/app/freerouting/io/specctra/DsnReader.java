package app.freerouting.io.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.io.specctra.parser.DsnFile;
import app.freerouting.io.specctra.parser.Keyword;
import app.freerouting.io.specctra.parser.ReadScopeParameter;
import app.freerouting.io.specctra.parser.ScopeKeyword;
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader;

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
 * {@link app.freerouting.io.specctra.parser.DsnFile#read} (now {@link Deprecated}).
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
      return new DsnReadResult.Success(board, null, par.getWarnings());
    } else if (!par.board_outline_ok) {
      return new DsnReadResult.OutlineMissing(board, null, par.getWarnings());
    } else {
      return new DsnReadResult.ParseError("(pcb", "DSN structure parsing failed");
    }
  }

  /**
   * Parses only the {@code (parser ...)}, {@code (resolution ...)}, and
   * {@code (structure (layer ...))} / {@code (structure (rule ...))} /
   * {@code (structure (autoroute_settings ...))} scopes. Does <em>not</em> construct full
   * board geometry, component placements, netlist items, or route traces.
   *
   * <p>This is significantly faster than {@link #readBoard} on large DSN files because the heavy
   * {@code (library ...)}, {@code (placement ...)}, {@code (network ...)}, and
   * {@code (wiring ...)} scopes are never parsed — the stream is closed immediately after the
   * {@code (structure ...)} scope ends.
   *
   * <p>The stream is <em>closed</em> by this method on return (success or failure).
   *
   * @param inputStream source — closed by this method on completion
   * @return {@link DsnReadResult.Success} with a populated {@link DsnMetadata} (board field may be
   *         {@code null} if the DSN had no valid outline), {@link DsnReadResult.ParseError} for
   *         malformed headers, or {@link DsnReadResult.IoError} for I/O failures during header
   *         scanning.
   */
  public static DsnReadResult readMetadata(InputStream inputStream) {
    if (inputStream == null) {
      return new DsnReadResult.ParseError("(pcb", "inputStream must not be null");
    }

    BoardObservers observers = new BoardObserverAdaptor();
    IdentificationNumberGenerator idGenerator = new ItemIdentificationNumberGenerator();
    SpecctraDsnStreamReader scanner = new SpecctraDsnStreamReader(inputStream);

    // -----------------------------------------------------------------------
    // Validate the "(pcb <name>" header — same three-token check as readBoard
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
    // Custom PCB-level loop — only parse metadata-relevant scopes.
    // We stop reading (and close the stream) as soon as (structure ...) ends,
    // skipping all subsequent heavy scopes.
    // -----------------------------------------------------------------------
    ReadScopeParameter par = new ReadScopeParameter(scanner, observers, idGenerator);
    Object nextToken = null;
    outer:
    for (;;) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.next_token();
      } catch (IOException e) {
        closeQuietly(inputStream);
        return new DsnReadResult.IoError(e);
      }
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break; // EOF or end of (pcb ...) scope
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.PARSER_SCOPE) {
          // Populates par.host_cad, par.host_version, par.string_quote
          Keyword.PARSER_SCOPE.read_scope(par);
        } else if (nextToken == Keyword.RESOLUTION_SCOPE) {
          // Populates par.unit, par.resolution
          Keyword.RESOLUTION_SCOPE.read_scope(par);
        } else if (nextToken == Keyword.STRUCTURE_SCOPE) {
          // Populates par.layer_structure, par.snap_angle, par.autoroute_settings
          // and creates the board via MinimalBoardManager (if a valid boundary exists).
          // Return value is ignored — we extract whatever was populated.
          Keyword.STRUCTURE_SCOPE.read_scope(par);
          break outer; // stop here — skip library, placement, network, wiring
        } else {
          ScopeKeyword.skip_scope(scanner);
        }
      }
    }

    closeQuietly(inputStream);

    // -----------------------------------------------------------------------
    // Build DsnMetadata from the parsed fields.
    // -----------------------------------------------------------------------
    int layerCount = 0;
    if (par.layer_structure != null) {
      layerCount = par.layer_structure.arr.length;
    } else if (par.getBoard() != null) {
      layerCount = par.getBoard().get_layer_count();
    }

    DsnMetadata metadata = new DsnMetadata(
        par.host_cad,
        par.host_version,
        layerCount,
        par.unit,
        par.resolution,
        par.snap_angle,
        par.autoroute_settings
    );

    return new DsnReadResult.Success(par.getBoard(), metadata, par.getWarnings());
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
