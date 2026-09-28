package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FanoutRetryTest {

  @Test
  void retriesUntilTheComponentEscapesSomethingElse() {
    assertTrue(BatchFanout.retryFanout(null, 0));
    assertFalse(BatchFanout.retryFanout(0, 0));
    assertTrue(BatchFanout.retryFanout(0, 1));
  }
}
