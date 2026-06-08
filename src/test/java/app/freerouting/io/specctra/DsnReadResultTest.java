package app.freerouting.io.specctra;

import app.freerouting.io.BoardReadResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DsnReadResultTest {

  @Test
  void patternMatchExhaustive() {
    BoardReadResult result = new BoardReadResult.ParseError("(pcb", "unexpected EOF");

    // Must compile — verifies sealed hierarchy is exhaustive
    String msg = switch (result) {
      case BoardReadResult.Success _        -> "ok";
      case BoardReadResult.OutlineMissing _ -> "outline";
      case BoardReadResult.ParseError e     -> e.detail();
      case BoardReadResult.IoError _        -> "io";
    };
    assertEquals("unexpected EOF", msg);
  }

  @Test
  void parseErrorAccessors() {
    var error = new BoardReadResult.ParseError("(structure", "missing layer");
    assertEquals("(structure", error.location());
    assertEquals("missing layer", error.detail());
  }

  @Test
  void ioErrorWrapsException() {
    var cause = new IOException("disk full");
    var ioError = new BoardReadResult.IoError(cause);
    assertSame(cause, ioError.cause());
  }

  @Test
  void successAndOutlineMissingHoldNullBoard() {
    // Board is allowed to be null at the data-model level (parser wires it later)
    var success = new BoardReadResult.Success(null, null, List.of());
    assertNull(success.board());
    assertNull(success.metadata());
    assertTrue(success.warnings().isEmpty());

    var missing = new BoardReadResult.OutlineMissing(null, null, List.of());
    assertNull(missing.board());
    assertNull(missing.metadata());
    assertTrue(missing.warnings().isEmpty());
  }

  @Test
  void warningsAreExposed() {
    var warnings = List.of("Wiring: degenerate wire skipped", "Wiring: duplicate via skipped at (100, 200)");
    var success = new BoardReadResult.Success(null, null, warnings);
    assertEquals(2, success.warnings().size());
    assertTrue(success.warnings().get(0).contains("degenerate wire"));
  }

  @Test
  void recordEquality() {
    var a = new BoardReadResult.ParseError("(pcb", "unexpected EOF");
    var b = new BoardReadResult.ParseError("(pcb", "unexpected EOF");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void instanceOfChecks() {
    BoardReadResult success      = new BoardReadResult.Success(null, null, List.of());
    BoardReadResult outlineMiss  = new BoardReadResult.OutlineMissing(null, null, List.of());
    BoardReadResult parseErr     = new BoardReadResult.ParseError("x", "y");
    BoardReadResult ioErr        = new BoardReadResult.IoError(new IOException());

    assertInstanceOf(BoardReadResult.Success.class,        success);
    assertInstanceOf(BoardReadResult.OutlineMissing.class, outlineMiss);
    assertInstanceOf(BoardReadResult.ParseError.class,     parseErr);
    assertInstanceOf(BoardReadResult.IoError.class,        ioErr);
  }
}