package app.freerouting.api.v1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SystemControllerV1Test {

  @Test
  public void testGetCpuLoad() {
    double cpuLoad = SystemControllerV1.getCpuLoad();
    // It should either be a valid percentage [0.0, 100.0] or -1 if unsupported/undetected.
    assertTrue(cpuLoad == -1.0 || (cpuLoad >= 0.0 && cpuLoad <= 100.0),
        "CPU load should be -1 or between 0 and 100, but got: " + cpuLoad);

    // Call it again to check that multiple calls work and if a value is returned, it is rounded to 1 decimal place.
    double secondLoad = SystemControllerV1.getCpuLoad();
    assertTrue(secondLoad == -1.0 || (secondLoad >= 0.0 && secondLoad <= 100.0));
    if (secondLoad >= 0.0) {
      double rounded = Math.round(secondLoad * 10.0) / 10.0;
      assertEquals(rounded, secondLoad, "CPU load should be rounded to 1 decimal place");
    }
  }
}
