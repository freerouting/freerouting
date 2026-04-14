package app.freerouting.designforms.specctra.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DsnReadResultTest {

  @Test
  void patternMatchExhaustive() {
    DsnReadResult result = new DsnReadResult.ParseError("(pcb", "unexpected EOF");

    // Must compile — verifies sealed hierarchy is exhaustive
    String msg = switch (result) {
      case DsnReadResult.Success _        -> "ok";
      case DsnReadResult.OutlineMissing _ -> "outline";
      case DsnReadResult.ParseError e     -> e.detail();
      case DsnReadResult.IoError _        -> "io";
    };
    assertEquals("unexpected EOF", msg);
  }

  @Test
  void parseErrorAccessors() {
    var error = new DsnReadResult.ParseError("(structure", "missing layer");
    assertEquals("(structure", error.location());
    assertEquals("missing layer", error.detail());
  }

  @Test
  void ioErrorWrapsException() {
    var cause = new IOException("disk full");
    var ioError = new DsnReadResult.IoError(cause);
    assertSame(cause, ioError.cause());
  }

  @Test
  void successAndOutlineMissingHoldNullBoard() {
    // Board is allowed to be null at the data-model level (parser wires it later)
    var success = new DsnReadResult.Success(null, null, List.of());
    assertNull(success.board());
    assertNull(success.metadata());
    assertTrue(success.warnings().isEmpty());

    var missing = new DsnReadResult.OutlineMissing(null, null, List.of());
    assertNull(missing.board());
    assertNull(missing.metadata());
    assertTrue(missing.warnings().isEmpty());
  }

  @Test
  void warningsAreExposed() {
    var warnings = List.of("Wiring: degenerate wire skipped", "Wiring: duplicate via skipped at (100, 200)");
    var success = new DsnReadResult.Success(null, null, warnings);
    assertEquals(2, success.warnings().size());
    assertTrue(success.warnings().get(0).contains("degenerate wire"));
  }

  @Test
  void recordEquality() {
    var a = new DsnReadResult.ParseError("(pcb", "unexpected EOF");
    var b = new DsnReadResult.ParseError("(pcb", "unexpected EOF");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void instanceOfChecks() {
    DsnReadResult success      = new DsnReadResult.Success(null, null, List.of());
    DsnReadResult outlineMiss  = new DsnReadResult.OutlineMissing(null, null, List.of());
    DsnReadResult parseErr     = new DsnReadResult.ParseError("x", "y");
    DsnReadResult ioErr        = new DsnReadResult.IoError(new IOException());

    assertInstanceOf(DsnReadResult.Success.class,        success);
    assertInstanceOf(DsnReadResult.OutlineMissing.class, outlineMiss);
    assertInstanceOf(DsnReadResult.ParseError.class,     parseErr);
    assertInstanceOf(DsnReadResult.IoError.class,        ioErr);
  }
}

