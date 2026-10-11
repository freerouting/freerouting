package app.freerouting.io.specctra.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.rules.CornerStyle;
import app.freerouting.rules.MeanderPatternType;
import app.freerouting.rules.NetMeanderConstraint;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class CircuitMeanderParserTest {

  private Circuit.ReadScopeResult parseCircuit(String dsnSnippet) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(dsnSnippet.getBytes(StandardCharsets.UTF_8));
    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    Object open = scanner.nextToken();
    assertEquals(Keyword.OPEN_BRACKET, open);
    Object circuitToken = scanner.nextToken();
    assertEquals(Keyword.CIRCUIT, circuitToken);
    return Circuit.readScope(scanner);
  }

  private Collection<Rule> parseRule(String dsnSnippet) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(dsnSnippet.getBytes(StandardCharsets.UTF_8));
    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    Object open = scanner.nextToken();
    assertEquals(Keyword.OPEN_BRACKET, open);
    Object ruleToken = scanner.nextToken();
    assertEquals(Keyword.RULE, ruleToken);
    return Rule.readScope(scanner);
  }

  @Test
  void parseCircuitWithStandardLengthAmplitudeAndGap() throws IOException {
    Circuit.ReadScopeResult result =
        parseCircuit("(circuit (length_amplitude 800 200) (length_gap 600))");
    assertNotNull(result);
    assertTrue(result.hasMeanderConstraint());
    assertEquals(800.0, result.maxAmplitude, 1e-6);
    assertEquals(200.0, result.minAmplitude, 1e-6);
    assertEquals(600.0, result.spacing, 1e-6);

    NetMeanderConstraint constraint =
        result.toMeanderConstraint(MeanderPatternType.SINGLE_TRACK, null);
    assertNotNull(constraint);
    assertEquals(800.0, constraint.maxAmplitude(), 1e-6);
    assertEquals(200.0, constraint.minAmplitude(), 1e-6);
    assertEquals(600.0, constraint.spacing(), 1e-6);
    assertEquals(CornerStyle.FILLET, constraint.cornerStyle());
    assertEquals(80.0, constraint.radiusPercentage(), 1e-6);
    assertFalse(constraint.singleSided());
  }

  @Test
  void parseCircuitWithExtensionAttributes() throws IOException {
    Circuit.ReadScopeResult result =
        parseCircuit(
            "(circuit (length_amplitude 1000 250) (length_gap 750) (corner_style chamfer)"
                + " (radius 65.0) (single_sided on))");
    assertNotNull(result);
    assertTrue(result.hasMeanderConstraint());
    assertEquals(1000.0, result.maxAmplitude, 1e-6);
    assertEquals(250.0, result.minAmplitude, 1e-6);
    assertEquals(750.0, result.spacing, 1e-6);
    assertEquals(CornerStyle.CHAMFER, result.cornerStyle);
    assertEquals(65.0, result.radiusPercentage, 1e-6);
    assertTrue(result.singleSided);

    NetMeanderConstraint constraint =
        result.toMeanderConstraint(MeanderPatternType.SINGLE_TRACK, null);
    assertNotNull(constraint);
    assertEquals(CornerStyle.CHAMFER, constraint.cornerStyle());
    assertEquals(65.0, constraint.radiusPercentage(), 1e-6);
    assertTrue(constraint.singleSided());
  }

  @Test
  void parseCircuitWithMeanderPatternScope() throws IOException {
    Circuit.ReadScopeResult result =
        parseCircuit(
            "(circuit (meander_pattern (pattern_type diff_pair) (length_amplitude 1200 300)"
                + " (length_gap 1000) (corner_style fillet) (radius 90.0) (single_sided off)))");
    assertNotNull(result);
    assertTrue(result.hasMeanderConstraint());
    assertEquals(MeanderPatternType.DIFF_PAIR, result.patternType);
    assertEquals(1200.0, result.maxAmplitude, 1e-6);
    assertEquals(300.0, result.minAmplitude, 1e-6);
    assertEquals(1000.0, result.spacing, 1e-6);
    assertEquals(CornerStyle.FILLET, result.cornerStyle);
    assertEquals(90.0, result.radiusPercentage, 1e-6);
    assertFalse(result.singleSided);

    NetMeanderConstraint constraint =
        result.toMeanderConstraint(MeanderPatternType.SINGLE_TRACK, null);
    assertNotNull(constraint);
    assertEquals(1200.0, constraint.maxAmplitude(), 1e-6);
    assertEquals(300.0, constraint.minAmplitude(), 1e-6);
    assertEquals(1000.0, constraint.spacing(), 1e-6);
  }

  @Test
  void parseCircuitWithSingleAmplitudeOnly() throws IOException {
    Circuit.ReadScopeResult result =
        parseCircuit("(circuit (length_amplitude 500) (length_gap 400))");
    assertNotNull(result);
    assertEquals(500.0, result.maxAmplitude, 1e-6);
    assertEquals(0.0, result.minAmplitude, 1e-6);
    assertEquals(400.0, result.spacing, 1e-6);
  }

  @Test
  void parseCircuitWithInvertedAmplitudesNormalizes() throws IOException {
    Circuit.ReadScopeResult result =
        parseCircuit("(circuit (length_amplitude 200 800) (length_gap 500))");
    assertNotNull(result);
    assertEquals(800.0, result.maxAmplitude, 1e-6);
    assertEquals(200.0, result.minAmplitude, 1e-6);
    assertEquals(500.0, result.spacing, 1e-6);
  }

  @Test
  void parseRuleWithMeanderPatternScope() throws IOException {
    Collection<Rule> rules =
        parseRule(
            "(rule (meander_pattern (length_amplitude 900 150) (length_gap 550)"
                + " (corner_style chamfer) (radius 70.0) (single_sided on)))");
    assertNotNull(rules);
    assertEquals(1, rules.size());
    Rule rule = rules.iterator().next();
    assertInstanceOf(Rule.MeanderRule.class, rule);

    Rule.MeanderRule meanderRule = (Rule.MeanderRule) rule;
    assertEquals(900.0, meanderRule.maxAmplitude, 1e-6);
    assertEquals(150.0, meanderRule.minAmplitude, 1e-6);
    assertEquals(550.0, meanderRule.spacing, 1e-6);
    assertEquals(CornerStyle.CHAMFER, meanderRule.cornerStyle);
    assertEquals(70.0, meanderRule.radiusPercentage, 1e-6);
    assertTrue(meanderRule.singleSided);

    NetMeanderConstraint constraint =
        meanderRule.toMeanderConstraint(MeanderPatternType.SINGLE_TRACK, null);
    assertNotNull(constraint);
    assertEquals(900.0, constraint.maxAmplitude(), 1e-6);
    assertEquals(150.0, constraint.minAmplitude(), 1e-6);
    assertEquals(550.0, constraint.spacing(), 1e-6);
    assertEquals(CornerStyle.CHAMFER, constraint.cornerStyle());
    assertEquals(70.0, constraint.radiusPercentage(), 1e-6);
    assertTrue(constraint.singleSided());
  }

  @Test
  void parseRuleWithMultiClauseMeanderAttributes() throws IOException {
    Collection<Rule> rules =
        parseRule(
            "(rule (length_amplitude 800 200) (length_gap 600) (corner_style chamfer) (radius 75.0) (single_sided on))");
    assertNotNull(rules);
    assertEquals(1, rules.size());
    Rule rule = rules.iterator().next();
    assertInstanceOf(Rule.MeanderRule.class, rule);

    Rule.MeanderRule meanderRule = (Rule.MeanderRule) rule;
    assertEquals(800.0, meanderRule.maxAmplitude, 1e-6);
    assertEquals(200.0, meanderRule.minAmplitude, 1e-6);
    assertEquals(600.0, meanderRule.spacing, 1e-6);
    assertEquals(CornerStyle.CHAMFER, meanderRule.cornerStyle);
    assertEquals(75.0, meanderRule.radiusPercentage, 1e-6);
    assertTrue(meanderRule.singleSided);
  }

  @Test
  void parseRuleWithInvertedAmplitudesNormalizes() throws IOException {
    Collection<Rule> rules = parseRule("(rule (length_amplitude 150 900) (length_gap 400))");
    assertNotNull(rules);
    assertEquals(1, rules.size());
    Rule rule = rules.iterator().next();
    assertInstanceOf(Rule.MeanderRule.class, rule);

    Rule.MeanderRule meanderRule = (Rule.MeanderRule) rule;
    assertEquals(900.0, meanderRule.maxAmplitude, 1e-6);
    assertEquals(150.0, meanderRule.minAmplitude, 1e-6);
    assertEquals(400.0, meanderRule.spacing, 1e-6);
  }
}
