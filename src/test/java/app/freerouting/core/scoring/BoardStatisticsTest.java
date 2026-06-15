package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.freerouting.io.FileFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BoardStatisticsTest {

  @Test
  void testBoardStatisticsWithValidJson() {
    String jsonContent = "{\n"
        + "  \"designName\": \"test-board\",\n"
        + "  \"layers\": [{\"index\": 0, \"name\": \"F.Cu\"}, {\"index\": 1, \"name\": \"B.Cu\"}],\n"
        + "  \"components\": [{\"reference\": \"R1\"}, {\"reference\": \"R2\"}],\n"
        + "  \"netClasses\": [{\"name\": \"default\"}],\n"
        + "  \"nets\": [{\"id\": 1, \"name\": \"N1\"}, {\"id\": 2, \"name\": \"N2\"}],\n"
        + "  \"traces\": [{\"netName\": \"N1\"}],\n"
        + "  \"vias\": [{\"netName\": \"N1\"}]\n"
        + "}";

    byte[] data = jsonContent.getBytes(StandardCharsets.UTF_8);
    BoardStatistics stats = new BoardStatistics(data, FileFormat.JSON);

    assertNotNull(stats.layers);
    assertEquals(2, stats.layers.totalCount);
    assertEquals(2, stats.components.totalCount);
    assertEquals(1, stats.nets.classCount);
    assertEquals(2, stats.nets.totalCount);
    assertEquals(1, stats.traces.totalCount);
    assertEquals(1, stats.vias.totalCount);
    assertEquals("KiCad JSON,test-board", stats.host);
  }
}

