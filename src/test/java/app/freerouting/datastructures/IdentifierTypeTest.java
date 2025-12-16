package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.junit.jupiter.api.Test;

class IdentifierTypeTest {

  @Test
  void testWrite() throws IOException {
    String[] reserved_chars = {"(", ")", " ", "-"};
    String string_quote = "\"";
    IdentifierType identifierType = new IdentifierType(reserved_chars, string_quote);

    // Test with a numeric string
    ByteArrayOutputStream baos_numeric = new ByteArrayOutputStream();
    OutputStreamWriter osw_numeric = new OutputStreamWriter(baos_numeric);
    identifierType.write("600", osw_numeric);
    osw_numeric.flush();
    assertEquals("\"600\"", baos_numeric.toString());

    // Test with a negative numeric string
    ByteArrayOutputStream baos_neg_numeric = new ByteArrayOutputStream();
    OutputStreamWriter osw_neg_numeric = new OutputStreamWriter(baos_neg_numeric);
    identifierType.write("-600", osw_neg_numeric);
    osw_neg_numeric.flush();
    assertEquals("\"-600\"", baos_neg_numeric.toString());

    // Test with a normal string
    ByteArrayOutputStream baos_normal = new ByteArrayOutputStream();
    OutputStreamWriter osw_normal = new OutputStreamWriter(baos_normal);
    identifierType.write("test", osw_normal);
    osw_normal.flush();
    assertEquals("test", baos_normal.toString());

    // Test with a string with reserved characters
    ByteArrayOutputStream baos_reserved = new ByteArrayOutputStream();
    OutputStreamWriter osw_reserved = new OutputStreamWriter(baos_reserved);
    identifierType.write("test-with-reserved", osw_reserved);
    osw_reserved.flush();
    assertEquals("\"test-with-reserved\"", baos_reserved.toString());

    // Test with a string that starts with a number
    ByteArrayOutputStream baos_start_with_number = new ByteArrayOutputStream();
    OutputStreamWriter osw_start_with_number = new OutputStreamWriter(baos_start_with_number);
    identifierType.write("600a", osw_start_with_number);
    osw_start_with_number.flush();
    assertEquals("\"600a\"", baos_start_with_number.toString());
  }
}