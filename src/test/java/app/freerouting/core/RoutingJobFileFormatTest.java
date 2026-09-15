package app.freerouting.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.io.FileFormat;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RoutingJobFileFormatTest {

  @Test
  void testDetectScrFromBytes() {
    byte[] scr1 =
        "GRID MIC;\nSET WIRE_BEND 2;\nWIRE 'GND' 250 (0 0) (100 100);"
            .getBytes(StandardCharsets.ISO_8859_1);
    assertEquals(FileFormat.SCR, RoutingJob.getFileFormat(scr1));

    byte[] scr2 = "  \n\tSET OPTIMIZING OFF;\nLAYER 1;\n".getBytes(StandardCharsets.ISO_8859_1);
    assertEquals(FileFormat.SCR, RoutingJob.getFileFormat(scr2));

    byte[] scr3 =
        "CHANGE LAYER 1;\nWIRE 'VCC' 500 (10 20) (30 40);".getBytes(StandardCharsets.ISO_8859_1);
    assertEquals(FileFormat.SCR, RoutingJob.getFileFormat(scr3));
  }

  @Test
  void testBoardFileDetailsPreservesScrFormatOnSetData() {
    File scrFile = new File("test_board.scr");
    BoardFileDetails details = new BoardFileDetails();
    details.setFilename(scrFile.getAbsolutePath());
    details.format = FileFormat.SCR;

    byte[] scrData =
        "GRID MIC;\nWIRE 'SIG' 300 (10 10) (20 20);\n".getBytes(StandardCharsets.ISO_8859_1);
    details.setData(scrData);

    assertEquals(FileFormat.SCR, details.format);
    assertEquals(scrData.length, details.size);
  }

  @Test
  void testBoardFileDetailsFallbackToFilenameExtensionWhenUnknown() {
    BoardFileDetails details = new BoardFileDetails();
    details.setFilename("output_board.scr");

    byte[] unknownData = new byte[] {1, 2, 3, 4};
    details.setData(unknownData);

    assertEquals(FileFormat.SCR, details.format);
  }
}
