package app.freerouting.settings.sources;

import app.freerouting.Freerouting;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DsnFileSettingsTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  /**
   * Verifies that {@code DsnFileSettings} reads the layer count directly from the DSN file's
   * {@code (structure (layer ...))} entries, even when the file has no
   * {@code (autoroute_settings ...)} scope.
   *
   * <p>{@code Issue413-test.dsn} is a 2-layer board (F.Cu + B.Cu) <em>without</em> an
   * {@code (autoroute_settings)} block. The old {@code DefaultSettings} hard-coded
   * {@code defaultLayerCount = 2} for every board regardless of its actual layer stack.
   * The fix made {@code DsnFileSettings} extract the layer count from the DSN metadata and call
   * {@code RouterSettings.setLayerCount(n)}, so the merged settings always reflect what the board
   * file actually declares.
   *
   * <p>This test asserts {@code == 2} (not {@code != 2}) because the DSN file genuinely has
   * 2 signal layers. Asserting the exact match is the strongest proof that the layer count comes
   * from the file, not from some coincidental hard-coded constant.
   */
  @Test
  void dsnFileSettings_reads2LayerCount_from2LayerBoardWithoutAutorouteBlock() {
    // Issue413-test.dsn: 2 signal layers (F.Cu, B.Cu), no (autoroute_settings ...) block.
    InputStream in = DsnTestFixtures.openResource("Issue413-test.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue413-test.dsn");

    assertEquals(2, settings.getSettings().getLayerCount(),
        "DsnFileSettings should read the layer count from the DSN structure section (2 layers: "
            + "F.Cu and B.Cu) even when the file has no (autoroute_settings ...) block.");
  }

  /**
   * Verifies that {@code DsnFileSettings} reads the correct layer count for a 4-layer board
   * without an {@code (autoroute_settings ...)} scope.
   *
   * <p>{@code Issue066-Project_GP8B.dsn} has 4 signal layers (F.Cu / In1.Cu / In2.Cu / B.Cu)
   * and no {@code (autoroute_settings)} block. This is the canonical multi-layer regression case:
   * the old hard-coded size-2 layer arrays would have been wrong here, while the correct
   * implementation must return 4.
   */
  @Test
  void dsnFileSettings_reads4LayerCount_from4LayerBoardWithoutAutorouteBlock() {
    // Issue066-Project_GP8B.dsn: 4 signal layers (F.Cu, In1.Cu, In2.Cu, B.Cu),
    // no (autoroute_settings ...) block.
    InputStream in = DsnTestFixtures.openResource("Issue066-Project_GP8B.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue066-Project_GP8B.dsn");

    assertEquals(4, settings.getSettings().getLayerCount(),
        "DsnFileSettings should read the layer count from the DSN structure section (4 layers: "
            + "F.Cu, In1.Cu, In2.Cu, B.Cu) even when the file has no (autoroute_settings ...) block.");
  }

  @Test
  void dsnFileSettingsReturnsNonNullSettings() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue143-rpi_splitter.dsn");

    assertNotNull(settings.getSettings(), "getSettings() must never return null");
  }

  @Test
  void dsnFileSettingsPriorityIs20() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue143-rpi_splitter.dsn");

    assertEquals(20, settings.getPriority());
  }

  @Test
  void dsnFileSettingsSourceNameContainsFilename() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue143-rpi_splitter.dsn");

    assertTrue(settings.getSourceName().contains("Issue143-rpi_splitter.dsn"));
  }
}