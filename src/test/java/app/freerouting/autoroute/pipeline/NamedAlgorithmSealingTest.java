package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Verifies the closed direct-subclass surface of the algorithm metadata base class. */
class NamedAlgorithmSealingTest {

  @Test
  void namedAlgorithmPermitsOnlyCurrentAndOptimizerBranches() {
    assertTrue(NamedAlgorithm.class.isSealed());
    assertEquals(
        Set.of(BatchAutorouter.class, BatchOptimizer.class),
        permittedClasses(NamedAlgorithm.class));
    assertTrue(BatchOptimizerMultiThreaded.class.getSuperclass() == BatchOptimizer.class);
  }

  private static Set<Class<?>> permittedClasses(Class<?> type) {
    return Arrays.stream(type.getPermittedSubclasses()).collect(Collectors.toUnmodifiableSet());
  }
}
