package app.freerouting.io.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.io.BoardMetadata;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.parser.DsnFile;
import app.freerouting.io.specctra.parser.Keyword;
import app.freerouting.io.specctra.parser.ReadScopeParameter;
import app.freerouting.io.specctra.parser.ScopeKeyword;
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a Specctra DSN file and returns a fully constructed {@link
 * app.freerouting.board.BasicBoard} wrapped in a typed {@link BoardReadResult}.
 *
 * <p>This class has <em>no</em> dependency on {@link app.freerouting.management.BoardManager},
 * {@link app.freerouting.core.RoutingJob}, or any GUI class. Board construction happens internally
 * via an anonymous minimal shim embedded in {@link ReadScopeParameter}.
 *
 * <p>Replaces the read path previously found in {@link app.freerouting.io.specctra.parser.DsnFile}
 * (now an empty shell).
 */
public final class DsnReader {

  private DsnReader() {}

  /**
   * Convenience overload — equivalent to {@link #readBoard(InputStream, BoardObservers,
   * IdentificationNumberGenerator, String)} with {@code null} for the design-name hint.
   */
  public static BoardReadResult readBoard(
      InputStream inputStream,
      BoardObservers observers,
      IdentificationNumberGenerator idGenerator) {
    return readBoard(inputStream, observers, idGenerator, null);
  }

  /**
   * Reads a DSN stream and returns a fully constructed board or a typed failure.
   *
   * <p>The stream is <em>closed</em> by this method once reading completes (successfully or not).
   *
   * @param inputStream source — closed by this method on completion
   * @param observers nullable; passed through to board items for host-system embedding
   * @param idGenerator nullable; for consistent item identification in host-system embedding
   * @param designName optional caller-supplied filename (without path) to use in log messages; when
   *     {@code null} or blank the pcb-name token from the DSN header is used
   * @return one of {@link BoardReadResult.Success}, {@link BoardReadResult.OutlineMissing}, {@link
   *     BoardReadResult.ParseError}, or {@link BoardReadResult.IoError}
   */
  public static BoardReadResult readBoard(
      InputStream inputStream,
      BoardObservers observers,
      IdentificationNumberGenerator idGenerator,
      String designName) {

    if (inputStream == null) {
      return new BoardReadResult.ParseError("(pcb", "inputStream must not be null");
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
    String pcbTokenName = null;
    for (int i = 0; i < 3; i++) {
      Object token;
      try {
        token = scanner.nextToken();
      } catch (IOException e) {
        closeQuietly(inputStream);
        return new BoardReadResult.IoError(e);
      }
      boolean ok = true;
      if (i == 0) {
        ok = token == Keyword.OPEN_BRACKET;
      } else if (i == 1) {
        ok = token == Keyword.PCB_SCOPE;
        // switch the scanner to NAME mode so the pcb-name token is consumed cleanly
        scanner.yybegin(SpecctraDsnStreamReader.NAME);
      } else {
        // i == 2: the design name string immediately following "(pcb"
        if (token instanceof String s) {
          pcbTokenName = s;
        }
      }
      if (!ok) {
        closeQuietly(inputStream);
        return new BoardReadResult.ParseError(
            "(pcb", "Not a Specctra DSN file: expected '(pcb <name>' header");
      }
    }

    // Resolve the effective design name for log messages:
    // prefer the caller-supplied filename, then the pcb-name token, then a fallback.
    final String effectiveDesignName;
    if (designName != null && !designName.isBlank()) {
      effectiveDesignName = Path.of(designName).getFileName().toString();
    } else if (pcbTokenName != null && !pcbTokenName.isBlank()) {
      effectiveDesignName = pcbTokenName;
    } else {
      effectiveDesignName = "unknown";
    }

    // -----------------------------------------------------------------------
    // Parse the body — board is constructed inside ReadScopeParameter's shim
    // -----------------------------------------------------------------------
    ReadScopeParameter scopeParameter = new ReadScopeParameter(scanner, observers, idGenerator);
    boolean readOk = Keyword.PCB_SCOPE.readScope(scopeParameter);

    BasicBoard board = scopeParameter.getBoard();

    closeQuietly(inputStream);

    if (readOk) {
      // Apply power-plane autoroute settings if the DSN had no (autoroute ...) scope
      if (scopeParameter.autorouteSettings == null) {
        DsnFile.adjustPlaneAutorouteSettings(board);
      }
      List<String> warnings = scopeParameter.getWarnings();
      if (!warnings.isEmpty()) {
        FRLogger.warn(
            "DSN file '"
                + effectiveDesignName
                + "' was loaded with "
                + warnings.size()
                + " warning(s).");
      }
      return new BoardReadResult.Success(board, null, warnings);
    } else if (!scopeParameter.boardOutlineOk) {
      List<String> warnings = scopeParameter.getWarnings();
      if (!warnings.isEmpty()) {
        FRLogger.warn(
            "DSN file '"
                + effectiveDesignName
                + "' was loaded with "
                + warnings.size()
                + " warning(s).");
      }
      return new BoardReadResult.OutlineMissing(board, null, warnings);
    } else {
      return new BoardReadResult.ParseError("(pcb", "DSN structure parsing failed");
    }
  }

  /**
   * Parses only the {@code (parser ...)}, {@code (resolution ...)}, and {@code (structure (layer
   * ...))} / {@code (structure (rule ...))} / {@code (structure (autorouteSettings ...))} scopes.
   * Does <em>not</em> construct full board geometry, component placements, netlist items, or route
   * traces.
   *
   * <p>This is significantly faster than {@link #readBoard} on large DSN files because the heavy
   * {@code (library ...)}, {@code (placement ...)}, {@code (network ...)}, and {@code (wiring ...)}
   * scopes are never parsed — the stream is closed immediately after the {@code (structure ...)}
   * scope ends.
   *
   * <p>The stream is <em>closed</em> by this method on return (success or failure).
   *
   * @param inputStream source — closed by this method on completion
   * @return {@link BoardReadResult.Success} with a populated {@link BoardMetadata} (board field may
   *     be {@code null} if the DSN had no valid outline), {@link BoardReadResult.ParseError} for
   *     malformed headers, or {@link BoardReadResult.IoError} for I/O failures during header
   *     scanning.
   */
  public static BoardReadResult readMetadata(InputStream inputStream) {
    if (inputStream == null) {
      return new BoardReadResult.ParseError("(pcb", "inputStream must not be null");
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
        token = scanner.nextToken();
      } catch (IOException e) {
        closeQuietly(inputStream);
        return new BoardReadResult.IoError(e);
      }
      boolean ok = true;
      if (i == 0) {
        ok = token == Keyword.OPEN_BRACKET;
      } else if (i == 1) {
        ok = token == Keyword.PCB_SCOPE;
        scanner.yybegin(SpecctraDsnStreamReader.NAME);
      }
      if (!ok) {
        closeQuietly(inputStream);
        return new BoardReadResult.ParseError(
            "(pcb", "Not a Specctra DSN file: expected '(pcb <name>' header");
      }
    }

    // -----------------------------------------------------------------------
    // Custom PCB-level loop — only parse metadata-relevant scopes.
    // We stop reading (and close the stream) as soon as (structure ...) ends,
    // skipping all subsequent heavy scopes.
    // -----------------------------------------------------------------------
    ReadScopeParameter scopeParameter = new ReadScopeParameter(scanner, observers, idGenerator);
    Object nextToken = null;
    outer:
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        closeQuietly(inputStream);
        return new BoardReadResult.IoError(e);
      }
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break; // EOF or end of (pcb ...) scope
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.PARSER_SCOPE) {
          // Populates scopeParameter.hostCad, scopeParameter.hostVersion,
          // scopeParameter.stringQuote
          Keyword.PARSER_SCOPE.readScope(scopeParameter);
        } else if (nextToken == Keyword.RESOLUTION_SCOPE) {
          // Populates scopeParameter.unit, scopeParameter.resolution
          Keyword.RESOLUTION_SCOPE.readScope(scopeParameter);
        } else if (nextToken == Keyword.STRUCTURE_SCOPE) {
          // Populates scopeParameter.layerStructure, scopeParameter.snapAngle,
          // scopeParameter.autorouteSettings
          // and creates the board via MinimalBoardManager (if a valid boundary exists).
          // Return value is ignored — we extract whatever was populated.
          Keyword.STRUCTURE_SCOPE.readScope(scopeParameter);
          break outer; // stop here — skip library, placement, network, wiring
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }

    closeQuietly(inputStream);

    // -----------------------------------------------------------------------
    // Build BoardMetadata from the parsed fields.
    // -----------------------------------------------------------------------
    int layerCount = 0;
    if (scopeParameter.layerStructure != null) {
      layerCount = scopeParameter.layerStructure.arr.length;
    } else if (scopeParameter.getBoard() != null) {
      layerCount = scopeParameter.getBoard().getLayerCount();
    }

    BoardMetadata metadata =
        new BoardMetadata(
            scopeParameter.hostCad,
            scopeParameter.hostVersion,
            layerCount,
            scopeParameter.unit,
            scopeParameter.resolution,
            scopeParameter.snapAngle,
            scopeParameter.autorouteSettings);

    return new BoardReadResult.Success(
        scopeParameter.getBoard(), metadata, scopeParameter.getWarnings());
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
