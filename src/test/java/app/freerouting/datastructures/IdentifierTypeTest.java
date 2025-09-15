package app.freerouting.datastructures;

import org.junit.jupiter.api.Test;
import java.io.StringWriter;
import java.io.OutputStreamWriter;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentifierTypeTest {

    @Test
    void testWrite() throws java.io.IOException {
        String[] reserved_chars = {"(", ")", " ", "-"};
        String string_quote = "\"";
        IdentifierType identifierType = new IdentifierType(reserved_chars, string_quote);

        // Test with a numeric string
        java.io.ByteArrayOutputStream baos_numeric = new java.io.ByteArrayOutputStream();
        OutputStreamWriter osw_numeric = new OutputStreamWriter(baos_numeric);
        identifierType.write("600", osw_numeric);
        osw_numeric.flush();
        assertEquals("\"600\"", baos_numeric.toString());

        // Test with a negative numeric string
        java.io.ByteArrayOutputStream baos_neg_numeric = new java.io.ByteArrayOutputStream();
        OutputStreamWriter osw_neg_numeric = new OutputStreamWriter(baos_neg_numeric);
        identifierType.write("-600", osw_neg_numeric);
        osw_neg_numeric.flush();
        assertEquals("\"-600\"", baos_neg_numeric.toString());

        // Test with a normal string
        java.io.ByteArrayOutputStream baos_normal = new java.io.ByteArrayOutputStream();
        OutputStreamWriter osw_normal = new OutputStreamWriter(baos_normal);
        identifierType.write("test", osw_normal);
        osw_normal.flush();
        assertEquals("test", baos_normal.toString());

        // Test with a string with reserved characters
        java.io.ByteArrayOutputStream baos_reserved = new java.io.ByteArrayOutputStream();
        OutputStreamWriter osw_reserved = new OutputStreamWriter(baos_reserved);
        identifierType.write("test-with-reserved", osw_reserved);
        osw_reserved.flush();
        assertEquals("\"test-with-reserved\"", baos_reserved.toString());
    }
}
