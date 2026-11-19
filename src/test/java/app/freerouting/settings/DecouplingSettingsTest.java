package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class DecouplingSettingsTest {

  @Test
  void testNullabilityInvariant() throws IllegalAccessException {
    DecouplingSettings settings = new DecouplingSettings();
    for (Field field : DecouplingSettings.class.getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      field.setAccessible(true);
      assertNull(
          field.get(settings),
          "Field '"
              + field.getName()
              + "' in DecouplingSettings must be null by default to preserve merger priority.");
    }
  }

  @Test
  void testDefaultSettingsValues() {
    DefaultSettings defaultsSource = new DefaultSettings();
    RouterSettings defaults = defaultsSource.getSettings();

    assertNotNull(defaults.decoupling);
    assertFalse(
        defaults.decoupling.enabled, "Decoupling must be disabled by default for non-regression");
    assertFalse(defaults.isDecouplingEnabled());
    assertEquals(DecouplingLockMode.SOFT, defaults.decoupling.lockMode);
    assertEquals(8.0, defaults.decoupling.maxOwnerRadiusMm);
    assertEquals(4.0, defaults.decoupling.maxStrapLengthMm);
    assertEquals(1.5, defaults.decoupling.traceWidthScale);
    assertEquals(5.0, defaults.decoupling.ripupCostMultiplier);
    assertEquals(250, defaults.decoupling.maxDoorExpansions);
    assertFalse(defaults.decoupling.includeReviewCandidates);
    assertTrue(defaults.decoupling.mutualInductanceBias);
    assertEquals(0.2, defaults.decoupling.mutualInductanceBiasWeight);
    assertTrue(defaults.decoupling.oppositeSideRouting);
    assertNotNull(defaults.powerNets);
    assertEquals(0, defaults.powerNets.length);
    assertNotNull(defaults.groundNets);
    assertEquals(0, defaults.groundNets.length);
    assertNotNull(defaults.decoupling.powerNets);
    assertEquals(0, defaults.decoupling.powerNets.length);
    assertNotNull(defaults.decoupling.groundNets);
    assertEquals(0, defaults.decoupling.groundNets.length);
  }

  @Test
  void testCliArgumentsParsing() {
    String[] args = {
      "--router.power_nets=+3V3_AUX,CUSTOM_RAIL",
      "--router.ground_nets=ANODE_RETURN,CHASSIS_EARTH",
      "--router.decoupling.enabled=true",
      "--router.decoupling.lock_mode=NONE",
      "--router.decoupling.max_strap_length_mm=2.5",
      "--router.decoupling.max_owner_radius_mm=5.0",
      "--router.decoupling.ripup_cost_multiplier=8.0",
      "--router.decoupling.max_door_expansions=300",
      "--router.decoupling.include_review_candidates=true",
      "--router.decoupling.mutual_inductance_bias=false",
      "--router.decoupling.mutual_inductance_bias_weight=0.35",
      "--router.decoupling.opposite_side_routing=false",
      "--router.decoupling.power_nets=MY_DECOUPLING_VCC,CORE_EXT",
      "--router.decoupling.ground_nets=MY_DECOUPLING_GND"
    };

    CliSettings cli = new CliSettings(args);
    RouterSettings cliSettings = cli.getSettings();

    assertNotNull(cliSettings.powerNets);
    assertEquals(2, cliSettings.powerNets.length);
    assertEquals("+3V3_AUX", cliSettings.powerNets[0]);
    assertEquals("CUSTOM_RAIL", cliSettings.powerNets[1]);

    assertNotNull(cliSettings.groundNets);
    assertEquals(2, cliSettings.groundNets.length);
    assertEquals("ANODE_RETURN", cliSettings.groundNets[0]);
    assertEquals("CHASSIS_EARTH", cliSettings.groundNets[1]);

    assertNotNull(cliSettings.decoupling);
    assertTrue(cliSettings.decoupling.enabled);
    assertEquals(DecouplingLockMode.NONE, cliSettings.decoupling.lockMode);
    assertEquals(2.5, cliSettings.decoupling.maxStrapLengthMm);
    assertEquals(5.0, cliSettings.decoupling.maxOwnerRadiusMm);
    assertEquals(8.0, cliSettings.decoupling.ripupCostMultiplier);
    assertEquals(300, cliSettings.decoupling.maxDoorExpansions);
    assertTrue(cliSettings.decoupling.includeReviewCandidates);
    assertFalse(cliSettings.decoupling.mutualInductanceBias);
    assertEquals(0.35, cliSettings.decoupling.mutualInductanceBiasWeight);
    assertFalse(cliSettings.decoupling.oppositeSideRouting);

    assertNotNull(cliSettings.decoupling.powerNets);
    assertEquals(2, cliSettings.decoupling.powerNets.length);
    assertEquals("MY_DECOUPLING_VCC", cliSettings.decoupling.powerNets[0]);
    assertEquals("CORE_EXT", cliSettings.decoupling.powerNets[1]);

    assertNotNull(cliSettings.decoupling.groundNets);
    assertEquals(1, cliSettings.decoupling.groundNets.length);
    assertEquals("MY_DECOUPLING_GND", cliSettings.decoupling.groundNets[0]);
  }

  @Test
  void testSettingsMergerPrecedence() {
    DefaultSettings defaultsSource = new DefaultSettings();
    RouterSettings base = defaultsSource.getSettings();
    assertFalse(base.isDecouplingEnabled());

    RouterSettings override = new RouterSettings();
    override.decoupling = new DecouplingSettings();
    override.decoupling.enabled = true;
    override.decoupling.maxStrapLengthMm = 3.2;

    base.applyNewValuesFrom(override);

    assertTrue(base.isDecouplingEnabled());
    assertEquals(3.2, base.decoupling.maxStrapLengthMm);
    // Unset fields in override remain defaults
    assertEquals(DecouplingLockMode.SOFT, base.decoupling.lockMode);
    assertEquals(8.0, base.decoupling.maxOwnerRadiusMm);
  }

  @Test
  void testRouterSettingsClone() {
    RouterSettings original = new RouterSettings();
    original.decoupling = new DecouplingSettings();
    original.decoupling.enabled = true;
    original.decoupling.lockMode = DecouplingLockMode.HARD;
    original.decoupling.maxStrapLengthMm = 3.5;
    original.powerNets = new String[] {"VCC_SYS"};
    original.groundNets = new String[] {"GND_SYS"};
    original.decoupling.powerNets = new String[] {"VCC_LOCAL"};
    original.decoupling.groundNets = new String[] {"GND_LOCAL"};

    RouterSettings cloned = original.clone();

    assertNotNull(cloned.decoupling);
    assertNotSame(original.decoupling, cloned.decoupling);
    assertEquals(original.decoupling.enabled, cloned.decoupling.enabled);
    assertEquals(original.decoupling.lockMode, cloned.decoupling.lockMode);
    assertEquals(original.decoupling.maxStrapLengthMm, cloned.decoupling.maxStrapLengthMm);

    assertNotSame(original.powerNets, cloned.powerNets);
    assertEquals(1, cloned.powerNets.length);
    assertEquals("VCC_SYS", cloned.powerNets[0]);

    assertNotSame(original.groundNets, cloned.groundNets);
    assertEquals(1, cloned.groundNets.length);
    assertEquals("GND_SYS", cloned.groundNets[0]);

    assertNotSame(original.decoupling.powerNets, cloned.decoupling.powerNets);
    assertEquals(1, cloned.decoupling.powerNets.length);
    assertEquals("VCC_LOCAL", cloned.decoupling.powerNets[0]);

    assertNotSame(original.decoupling.groundNets, cloned.decoupling.groundNets);
    assertEquals(1, cloned.decoupling.groundNets.length);
    assertEquals("GND_LOCAL", cloned.decoupling.groundNets[0]);

    cloned.decoupling.enabled = false;
    assertTrue(original.decoupling.enabled);
  }
}
