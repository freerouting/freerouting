package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the {@code StackOverflowError} in {@link
 * app.freerouting.board.PolylineTrace#combine()}.
 *
 * <p>Before the fix, {@code combine()} called itself once per successful merge
 * ({@code combine_at_start} / {@code combine_at_end}), so reading a net made of a long chain of
 * connected collinear 2-point segments recursed once per segment and overflowed the stack during
 * import — before routing even started. This is a different method than the {@code normalize()}
 * recursion that was bounded in v2.2.4.
 *
 * <p>The fixture {@code Issue723-CombineStackOverflow.dsn} is a single net (GND) built from ~4000
 * connected collinear 2-point wire segments. The read runs on a thread with a small, fixed stack
 * so the O(n)-depth recursion of the unfixed {@code combine()} overflows deterministically on a
 * modest fixture, independent of the host JVM's default {@code -Xss}. The fixed O(1)-stack loop
 * reads the same board without overflowing.
 */
public class CombineStackOverflowTest {

  /** Small, fixed stack so the recursion depth needed to overflow is a few hundred, not tens of
   * thousands — keeps the fixture (and the test) small while staying host-independent. */
  private static final long STACK_SIZE_BYTES = 256L * 1024L;

  private static final String FIXTURE = "Issue723-CombineStackOverflow.dsn";

  @Test
  public void combineDoesNotOverflowOnLongCollinearTrace() throws Exception {
    byte[] dsnBytes = Files.readAllBytes(fixturePath(FIXTURE));

    final BoardReadResult[] result = new BoardReadResult[1];
    final Throwable[] error = new Throwable[1];

    Runnable readTask = () -> {
      try (InputStream in = new ByteArrayInputStream(dsnBytes)) {
        result[0] = DsnReader.readBoard(in, null, null);
      } catch (Throwable t) {
        // Includes StackOverflowError, which is an Error, not an Exception.
        error[0] = t;
      }
    };

    Thread reader = new Thread(null, readTask, "combine-stackoverflow-reader", STACK_SIZE_BYTES);
    reader.start();
    reader.join(30_000);
    if (reader.isAlive()) {
      reader.interrupt();
      fail("Timed out while reading the DSN fixture; possible deadlock or infinite loop in DSN import.");
    }
    if (error[0] instanceof StackOverflowError) {
      fail("PolylineTrace.combine() overflowed the stack while reading a long collinear trace. "
          + "The self-recursion in combine() must be converted to an iterative loop. Cause: "
          + error[0]);
    }
    assertNull(error[0], "Unexpected error while reading the board: " + error[0]);
    assertInstanceOf(BoardReadResult.Success.class, result[0],
        "Expected a successful board read but got: " + result[0]);
  }

  /** Locate a file under a {@code fixtures/} directory, walking up from the working directory. */
  private static Path fixturePath(String filename) {
    Path dir = Path.of(".").toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve("fixtures").resolve(filename);
      if (Files.exists(candidate)) {
        return candidate;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("Could not locate fixtures/" + filename);
  }
}
