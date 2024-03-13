package app.freerouting.board;

/**
 * If {@literal >} RELEASE, some features may be used, which are still in experimental state. Also,
 * warnings for debugging may be printed depending on the test_level.
 * DEPRECATED: TestLevel was mainly used for debugging purposes. We should use FRLogger for debugging related logging instead.
 */
public enum TestLevel {
  RELEASE_VERSION,
  TEST_VERSION,
  CRITICAL_DEBUGGING_OUTPUT,
  ALL_DEBUGGING_OUTPUT
}
