package app.freerouting.designforms.specctra.io;

import app.freerouting.board.BasicBoard;
import java.io.IOException;
import java.util.List;

/**
 * Sealed result type for all outcomes of a DSN read operation.
 *
 * <p>Callers should use a pattern-matching {@code switch} to handle every case exhaustively:
 *
 * <pre>{@code
 * DsnReadResult result = DsnReader.readBoard(stream, null, null);
 * switch (result) {
 *     case DsnReadResult.Success s        -> route(s.board());
 *     case DsnReadResult.OutlineMissing o -> warnAndRoute(o.board());
 *     case DsnReadResult.ParseError e     -> fail(e.location(), e.detail());
 *     case DsnReadResult.IoError io       -> fail(io.cause());
 * }
 * }</pre>
 */
public sealed interface DsnReadResult
    permits DsnReadResult.Success,
            DsnReadResult.OutlineMissing,
            DsnReadResult.ParseError,
            DsnReadResult.IoError {

  /**
   * Full board + metadata are available. The board is fully constructed and routable.
   *
   * @param warnings non-fatal issues encountered during loading (e.g. degenerate wires, duplicate
   *                 vias, missing nets). Never {@code null}; may be empty.
   */
  record Success(BasicBoard board, DsnMetadata metadata, List<String> warnings) implements DsnReadResult {}

  /**
   * The board was constructed but the {@code (structure (boundary ...))} (outline) scope was
   * absent from the DSN file. The board reference is still valid and may be used with caution.
   *
   * @param warnings non-fatal issues encountered during loading. Never {@code null}; may be empty.
   */
  record OutlineMissing(BasicBoard board, DsnMetadata metadata, List<String> warnings) implements DsnReadResult {}

  /**
   * The token stream did not conform to the expected Specctra DSN grammar.
   *
   * @param location a human-readable description of where in the file the error was detected
   *                 (e.g. the enclosing scope keyword such as {@code "(pcb"})
   * @param detail   a short description of the specific problem
   */
  record ParseError(String location, String detail) implements DsnReadResult {}

  /** An {@link IOException} occurred while reading the underlying stream. */
  record IoError(IOException cause) implements DsnReadResult {}
}

