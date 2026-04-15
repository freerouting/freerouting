package app.freerouting.io.specctra;

import app.freerouting.Freerouting;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DsnReaderMetadataTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void readMetadataExtractsLayerCount() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnReadResult result = DsnReader.readMetadata(in);

    assertInstanceOf(DsnReadResult.Success.class, result);
    assertEquals(2, ((DsnReadResult.Success) result).metadata().layerCount());
  }

  @Test
  void readMetadataExtractsHostCad() {
    InputStream in = DsnTestFixtures.openResource("Issue508-DAC2020_bm01.dsn");
    DsnReadResult result = DsnReader.readMetadata(in);

    assertInstanceOf(DsnReadResult.Success.class, result);
    DsnMetadata meta = ((DsnReadResult.Success) result).metadata();
    assertNotNull(meta.hostCad(), "hostCad must not be null"); // exact value depends on the fixture
  }

  @Test
  void readMetadataCompletesWithinReasonableTimeOnLargeDsn() {
    assertTimeoutPreemptively(Duration.ofSeconds(5),
        () -> DsnReader.readMetadata(DsnTestFixtures.openResource("Issue187-processor.Z80.dsn")));
  }

  @Test
  void readMetadataReturnsParseErrorForNullStream() {
    DsnReadResult result = DsnReader.readMetadata(null);
    assertInstanceOf(DsnReadResult.ParseError.class, result);
  }

  @Test
  void readMetadataPopulatesUnit() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnReadResult result = DsnReader.readMetadata(in);

    assertInstanceOf(DsnReadResult.Success.class, result);
    DsnMetadata meta = ((DsnReadResult.Success) result).metadata();
    assertNotNull(meta.unit(), "unit must be set from (resolution ...) scope");
  }
}
