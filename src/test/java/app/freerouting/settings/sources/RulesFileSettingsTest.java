package app.freerouting.settings.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class RulesFileSettingsTest {

  @Test
  void priorityIs40() {
    RulesFileSettings source = new RulesFileSettings("dummy.rules");
    assertEquals(40, source.getPriority());
    assertEquals("RULES file: dummy.rules", source.getSourceName());
  }

  @Test
  void parsesAutorouteSettingsFromProcessorRules() throws Exception {
    File rulesFile = new File("fixtures/Issue191-processor.Z80/processor.rules");
    assertTrue(rulesFile.exists(), "processor.rules must exist");

    try (InputStream in = new FileInputStream(rulesFile)) {
      RulesFileSettings source = new RulesFileSettings(in, "processor.rules");
      RouterSettings settings = source.getSettings();
      assertNotNull(settings);

      assertEquals(true, settings.getViasAllowed());
      assertEquals(50, settings.getViaCosts());
      assertEquals(5, settings.getPlaneViaCosts());
      assertEquals(100, settings.getStartRipupCosts());
      assertEquals(true, settings.getRunRouter());
      assertEquals(true, settings.getRunOptimizer());
      assertEquals(2, settings.getLayerCount());

      assertTrue(settings.getLayerActive(0));
      assertTrue(settings.getLayerActive(1));

      // Layer 0: F.Cu horizontal, cost 1.0 / 2.5
      assertTrue(settings.getPreferredDirectionIsHorizontal(0));
      assertEquals(1.0, settings.getPreferredDirectionTraceCosts(0));
      assertEquals(2.5, settings.getAgainstPreferredDirectionTraceCosts(0));

      // Layer 1: B.Cu vertical, cost 1.0 / 1.7
      assertFalse(settings.getPreferredDirectionIsHorizontal(1));
      assertEquals(1.0, settings.getPreferredDirectionTraceCosts(1));
      assertEquals(1.7, settings.getAgainstPreferredDirectionTraceCosts(1));
    }
  }

  @Test
  void parsesAutorouteSettingsFromHw48naRules() throws Exception {
    File rulesFile = new File("fixtures/Issue029-hw48na_valid.rules");
    assertTrue(rulesFile.exists(), "hw48na_valid.rules must exist");

    RulesFileSettings source = new RulesFileSettings(rulesFile);
    RouterSettings settings = source.getSettings();
    assertNotNull(settings);

    assertEquals(true, settings.getViasAllowed());
    assertEquals(50, settings.getViaCosts());
    assertEquals(5, settings.getPlaneViaCosts());
    assertEquals(100, settings.getStartRipupCosts());
    assertEquals(2, settings.getLayerCount());

    // Layer 0: F.Cu vertical, cost 1.0 / 2.0
    assertFalse(settings.getPreferredDirectionIsHorizontal(0));
    assertEquals(1.0, settings.getPreferredDirectionTraceCosts(0));
    assertEquals(2.0, settings.getAgainstPreferredDirectionTraceCosts(0));

    // Layer 1: B.Cu horizontal, cost 1.0 / 2.0
    assertTrue(settings.getPreferredDirectionIsHorizontal(1));
    assertEquals(1.0, settings.getPreferredDirectionTraceCosts(1));
    assertEquals(2.0, settings.getAgainstPreferredDirectionTraceCosts(1));
  }
}
