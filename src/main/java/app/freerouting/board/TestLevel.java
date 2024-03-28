package app.freerouting.board;

/**
 * If {@literal >} RELEASE, some features may be used, which are still in experimental state. Also,
 * warnings for debugging may be printed depending on the test_level.
 * DEPRECATED: TestLevel was mainly used for debugging purposes. We should use FRLogger for debugging related logging instead.
 */
@Deprecated
public enum TestLevel {
  // Info, warning and error messages should be logged.
  RELEASE_IWE,
  // Debug, info, warning and error messages should be logged.
  EXPERIMENTAL_DIWE
}
