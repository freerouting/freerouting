package app.freerouting.designforms.specctra.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;

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
    var success = new DsnReadResult.Success(null, null);
    assertNull(success.board());
    assertNull(success.metadata());

    var missing = new DsnReadResult.OutlineMissing(null, null);
    assertNull(missing.board());
    assertNull(missing.metadata());
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
    DsnReadResult success      = new DsnReadResult.Success(null, null);
    DsnReadResult outlineMiss  = new DsnReadResult.OutlineMissing(null, null);
    DsnReadResult parseErr     = new DsnReadResult.ParseError("x", "y");
    DsnReadResult ioErr        = new DsnReadResult.IoError(new IOException());

    assertInstanceOf(DsnReadResult.Success.class,        success);
    assertInstanceOf(DsnReadResult.OutlineMissing.class, outlineMiss);
    assertInstanceOf(DsnReadResult.ParseError.class,     parseErr);
    assertInstanceOf(DsnReadResult.IoError.class,        ioErr);
  }
}

