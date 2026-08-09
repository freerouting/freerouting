package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.io.FileFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BoardStatisticsTest {

  @Test
  void boardStatisticsWithValidJson() {
    String jsonContent =
        """
        {
          "designName": "test-board",
          "layers": [{"index": 0, "name": "F.Cu"}, {"index": 1, "name": "B.Cu"}],
          "components": [{"reference": "R1"}, {"reference": "R2"}],
          "netClasses": [{"name": "default"}],
          "nets": [{"id": 1, "name": "N1"}, {"id": 2, "name": "N2"}],
          "traces": [{"netName": "N1"}],
          "vias": [{"netName": "N1"}]
        }
        """;

    byte[] data = jsonContent.getBytes(StandardCharsets.UTF_8);
    BoardStatistics stats = new BoardStatistics(data, FileFormat.KICAD_DESIGN_JSON);

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
