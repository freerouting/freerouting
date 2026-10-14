package app.freerouting.io.specctra.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CircuitLengthParserTest {

  private Circuit.ReadScopeResult parseCircuit(String dsnSnippet) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(dsnSnippet.getBytes(StandardCharsets.UTF_8));
    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    // Position scanner inside circuit scope (after '(' and 'circuit')
    Object open = scanner.nextToken();
    assertEquals(Keyword.OPEN_BRACKET, open);
    Object circuitToken = scanner.nextToken();
    assertEquals(Keyword.CIRCUIT, circuitToken);
    return Circuit.readScope(scanner);
  }

  @Test
  void parseLengthWithBothMaxAndMin() throws IOException {
    Circuit.ReadScopeResult result = parseCircuit("(circuit (length 100 50))");
    assertNotNull(result);
    assertEquals(100.0, result.maxLength, 1e-6);
    assertEquals(50.0, result.minLength, 1e-6);
  }

  @Test
  void parseLengthWithMaxOnly() throws IOException {
    Circuit.ReadScopeResult result = parseCircuit("(circuit (length 100))");
    assertNotNull(result);
    assertEquals(100.0, result.maxLength, 1e-6);
    assertEquals(0.0, result.minLength, 1e-6);
  }

  @Test
  void parseLengthWithMinOnlyUsingNegativeSentinel() throws IOException {
    Circuit.ReadScopeResult result = parseCircuit("(circuit (length -1 50))");
    assertNotNull(result);
    assertEquals(-1.0, result.maxLength, 1e-6);
    assertEquals(50.0, result.minLength, 1e-6);
  }

  @Test
  void parseLengthWithSubScopeTypeActual() throws IOException {
    Circuit.ReadScopeResult result = parseCircuit("(circuit (length 100.5 45.2 (type actual)))");
    assertNotNull(result);
    assertEquals(100.5, result.maxLength, 1e-6);
    assertEquals(45.2, result.minLength, 1e-6);
  }

  @Test
  void parseLengthMaxOnlyWithSubScopeTypeActual() throws IOException {
    Circuit.ReadScopeResult result = parseCircuit("(circuit (length 100.0 (type actual)))");
    assertNotNull(result);
    assertEquals(100.0, result.maxLength, 1e-6);
    assertEquals(0.0, result.minLength, 1e-6);
  }

  @Test
  void parseCircuitWithUseLayerAndLength() throws IOException {
    Circuit.ReadScopeResult result = parseCircuit("(circuit (use_layer F.Cu B.Cu) (length 80 20))");
    assertNotNull(result);
    assertEquals(80.0, result.maxLength, 1e-6);
    assertEquals(20.0, result.minLength, 1e-6);
    assertTrue(result.useLayer.contains("F.Cu"));
    assertTrue(result.useLayer.contains("B.Cu"));
  }
}
