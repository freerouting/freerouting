package app.freerouting.designforms.specctra.io;

import app.freerouting.Freerouting;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

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
  void readMetadataIsFasterThanReadBoard() {
    // Just proves the path terminates in a reasonable time on a large DSN
    long t0 = System.currentTimeMillis();
    DsnReader.readMetadata(DsnTestFixtures.openResource("Issue187-processor.Z80.dsn"));
    long metaMs = System.currentTimeMillis() - t0;

    t0 = System.currentTimeMillis();
    DsnReader.readBoard(DsnTestFixtures.openResource("Issue187-processor.Z80.dsn"), null, null);
    long boardMs = System.currentTimeMillis() - t0;

    assertTrue(metaMs < boardMs,
        "readMetadata (%dms) should be faster than readBoard (%dms)".formatted(metaMs, boardMs));
  }

  @Test
  void readMetadataReturnsSuccessWithNullMetadataForNullStream() {
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

