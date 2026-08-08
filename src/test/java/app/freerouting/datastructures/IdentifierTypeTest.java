package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.junit.jupiter.api.Test;

class IdentifierTypeTest {

  @Test
  void testWrite() throws IOException {
    String[] reservedChars = {"(", ")", " ", "-"};
    String stringQuote = "\"";
    IdentifierType identifierType = new IdentifierType(reservedChars, stringQuote);

    // Test with a numeric string
    ByteArrayOutputStream baosNumeric = new ByteArrayOutputStream();
    OutputStreamWriter oswNumeric = new OutputStreamWriter(baosNumeric);
    identifierType.write("600", oswNumeric);
    oswNumeric.flush();
    assertEquals("\"600\"", baosNumeric.toString());

    // Test with a negative numeric string
    ByteArrayOutputStream baosNegNumeric = new ByteArrayOutputStream();
    OutputStreamWriter oswNegNumeric = new OutputStreamWriter(baosNegNumeric);
    identifierType.write("-600", oswNegNumeric);
    oswNegNumeric.flush();
    assertEquals("\"-600\"", baosNegNumeric.toString());

    // Test with a normal string
    ByteArrayOutputStream baosNormal = new ByteArrayOutputStream();
    OutputStreamWriter oswNormal = new OutputStreamWriter(baosNormal);
    identifierType.write("test", oswNormal);
    oswNormal.flush();
    assertEquals("test", baosNormal.toString());

    // Test with a string with reserved characters
    ByteArrayOutputStream baosReserved = new ByteArrayOutputStream();
    OutputStreamWriter oswReserved = new OutputStreamWriter(baosReserved);
    identifierType.write("test-with-reserved", oswReserved);
    oswReserved.flush();
    assertEquals("\"test-with-reserved\"", baosReserved.toString());

    // Test with a string that starts with a number
    ByteArrayOutputStream baosStartWithNumber = new ByteArrayOutputStream();
    OutputStreamWriter oswStartWithNumber = new OutputStreamWriter(baosStartWithNumber);
    identifierType.write("600a", oswStartWithNumber);
    oswStartWithNumber.flush();
    assertEquals("\"600a\"", baosStartWithNumber.toString());
  }
}
