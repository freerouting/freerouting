package app.freerouting.io;

import app.freerouting.board.BasicBoard;
import java.io.IOException;
import java.util.List;

/**
 * Sealed result type for all outcomes of a board read operation (DSN, JSON, or any other format).
 *
 * <p>Callers should use a pattern-matching {@code switch} to handle every case exhaustively:
 *
 * <pre>{@code
 * BoardReadResult result = DsnReader.readBoard(stream, null, null);
 * switch (result) {
 *     case BoardReadResult.Success s        -> route(s.board());
 *     case BoardReadResult.OutlineMissing o -> warnAndRoute(o.board());
 *     case BoardReadResult.ParseError e     -> fail(e.location(), e.detail());
 *     case BoardReadResult.IoError io       -> fail(io.cause());
 * }
 * }</pre>
 */
public sealed interface BoardReadResult
    permits BoardReadResult.Success,
            BoardReadResult.OutlineMissing,
            BoardReadResult.ParseError,
            BoardReadResult.IoError {

  /**
   * Full board + metadata are available. The board is fully constructed and routable.
   *
   * @param warnings non-fatal issues encountered during loading (e.g. degenerate wires, duplicate
   *                 vias, missing nets). Never {@code null}; may be empty.
   */
  record Success(BasicBoard board, BoardMetadata metadata, List<String> warnings) implements BoardReadResult {}

  /**
   * The board was constructed but the outline (boundary) scope was
   * absent from the input file. The board reference is still valid and may be used with caution.
   *
   * @param warnings non-fatal issues encountered during loading. Never {@code null}; may be empty.
   */
  record OutlineMissing(BasicBoard board, BoardMetadata metadata, List<String> warnings) implements BoardReadResult {}

  /**
   * The input did not conform to the expected grammar/format.
   *
   * @param location a human-readable description of where in the input the error was detected
   * @param detail   a short description of the specific problem
   */
  record ParseError(String location, String detail) implements BoardReadResult {}

  /** An {@link IOException} occurred while reading the underlying stream. */
  record IoError(IOException cause) implements BoardReadResult {}
}