package app.freerouting.io.specctra.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.rules.NetMeanderConstraint;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class MeanderRuleParserTest {

  private Collection<Rule> parseRuleScope(String snippet) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(snippet.getBytes(StandardCharsets.UTF_8));
    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    Object open = scanner.nextToken();
    assertEquals(Keyword.OPEN_BRACKET, open);
    Object ruleToken = scanner.nextToken();
    assertEquals(Keyword.RULE, ruleToken);
    return Rule.readScope(scanner);
  }

  private Circuit.ReadScopeResult parseCircuitScope(String snippet) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(snippet.getBytes(StandardCharsets.UTF_8));
    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    Object open = scanner.nextToken();
    assertEquals(Keyword.OPEN_BRACKET, open);
    Object circuitToken = scanner.nextToken();
    assertEquals(Keyword.CIRCUIT, circuitToken);
    return Circuit.readScope(scanner);
  }

  @Test
  void parseLengthAmplitudeStandardTwoArgs() throws IOException {
    Collection<Rule> rules = parseRuleScope("(rule (length_amplitude 1.5 0.3))");
    assertNotNull(rules);
    assertEquals(1, rules.size());
    Rule rule = rules.iterator().next();
    assertInstanceOf(Rule.LengthAmplitudeRule.class, rule);

    Rule.LengthAmplitudeRule ampRule = (Rule.LengthAmplitudeRule) rule;
    assertEquals(1.5, ampRule.maxAmplitude, 1e-6);
    assertEquals(0.3, ampRule.minAmplitude, 1e-6);
    assertFalse(ampRule.singleSided);
    assertEquals(NetMeanderConstraint.CornerStyle.AUTO, ampRule.cornerStyle);
  }

  @Test
  void parseLengthAmplitudeSingleArgDefaultsMinToZero() throws IOException {
    Collection<Rule> rules = parseRuleScope("(rule (length_amplitude 2.0))");
    assertNotNull(rules);
    assertEquals(1, rules.size());

    Rule.LengthAmplitudeRule ampRule = (Rule.LengthAmplitudeRule) rules.iterator().next();
    assertEquals(2.0, ampRule.maxAmplitude, 1e-6);
    assertEquals(0.0, ampRule.minAmplitude, 1e-6);
  }

  @Test
  void parseLengthAmplitudeProhibitSentinelZero() throws IOException {
    Collection<Rule> rules = parseRuleScope("(rule (length_amplitude 0))");
    assertNotNull(rules);
    assertEquals(1, rules.size());

    Rule.LengthAmplitudeRule ampRule = (Rule.LengthAmplitudeRule) rules.iterator().next();
    assertEquals(0.0, ampRule.maxAmplitude, 1e-6);
    assertEquals(0.0, ampRule.minAmplitude, 1e-6);
  }

  @Test
  void parseLengthAmplitudeUnboundedSentinel() throws IOException {
    Collection<Rule> rules = parseRuleScope("(rule (length_amplitude -1 0.4))");
    assertNotNull(rules);
    assertEquals(1, rules.size());

    Rule.LengthAmplitudeRule ampRule = (Rule.LengthAmplitudeRule) rules.iterator().next();
    assertEquals(-1.0, ampRule.maxAmplitude, 1e-6);
    assertEquals(0.4, ampRule.minAmplitude, 1e-6);
  }

  @Test
  void parseLengthGapStandard() throws IOException {
    Collection<Rule> rules = parseRuleScope("(rule (length_gap 0.75))");
    assertNotNull(rules);
    assertEquals(1, rules.size());

    Rule.LengthGapRule gapRule = (Rule.LengthGapRule) rules.iterator().next();
    assertEquals(0.75, gapRule.gap, 1e-6);
  }

  @Test
  void parseAmplitudeAndGapTogetherInRuleScope() throws IOException {
    Collection<Rule> rules = parseRuleScope("(rule (length_amplitude 1.8 0.2) (length_gap 0.6))");
    assertNotNull(rules);
    assertEquals(2, rules.size());

    Rule.LengthAmplitudeRule ampRule = null;
    Rule.LengthGapRule gapRule = null;
    for (Rule r : rules) {
      if (r instanceof Rule.LengthAmplitudeRule a) {
        ampRule = a;
      } else if (r instanceof Rule.LengthGapRule g) {
        gapRule = g;
      }
    }
    assertNotNull(ampRule);
    assertNotNull(gapRule);
    assertEquals(1.8, ampRule.maxAmplitude, 1e-6);
    assertEquals(0.2, ampRule.minAmplitude, 1e-6);
    assertEquals(0.6, gapRule.gap, 1e-6);
  }

  @Test
  void parseAmplitudeWithSubscopesForTromboneAndChamfer() throws IOException {
    String snippet =
        "(rule (length_amplitude 1.5 0.3 (type trombone) (corner chamfered) (radius 75)))";
    Collection<Rule> rules = parseRuleScope(snippet);
    assertNotNull(rules);
    assertEquals(1, rules.size());

    Rule.LengthAmplitudeRule ampRule = (Rule.LengthAmplitudeRule) rules.iterator().next();
    assertEquals(1.5, ampRule.maxAmplitude, 1e-6);
    assertEquals(0.3, ampRule.minAmplitude, 1e-6);
    assertTrue(ampRule.singleSided);
    assertEquals(NetMeanderConstraint.CornerStyle.CHAMFERED_45, ampRule.cornerStyle);
    assertEquals(75, ampRule.cornerRadiusPercentage);
  }

  @Test
  void parseAmplitudeAndGapInCircuitScope() throws IOException {
    String snippet = "(circuit (length 100 50) (length_amplitude 1.6 0.25) (length_gap 0.55))";
    Circuit.ReadScopeResult result = parseCircuitScope(snippet);
    assertNotNull(result);
    assertEquals(100.0, result.maxLength, 1e-6);
    assertEquals(50.0, result.minLength, 1e-6);

    assertNotNull(result.amplitudeRule);
    assertEquals(1.6, result.amplitudeRule.maxAmplitude, 1e-6);
    assertEquals(0.25, result.amplitudeRule.minAmplitude, 1e-6);

    assertNotNull(result.gapRule);
    assertEquals(0.55, result.gapRule.gap, 1e-6);
  }

  @Test
  void testToConstraintWithCoordinateTransform() {
    app.freerouting.io.CoordinateTransform transform =
        new app.freerouting.io.CoordinateTransform(1000.0, 0, 0);

    Rule.LengthAmplitudeRule ampRule =
        new Rule.LengthAmplitudeRule(
            1.5, 0.3, true, NetMeanderConstraint.CornerStyle.FILLETED_ROUND, 60);
    NetMeanderConstraint ampConstraint = ampRule.toConstraint(transform);
    assertEquals(1500.0, ampConstraint.maxAmplitude(), 1e-6);
    assertEquals(300.0, ampConstraint.minAmplitude(), 1e-6);
    assertEquals(NetMeanderConstraint.UNSPECIFIED, ampConstraint.gap(), 1e-6);
    assertTrue(ampConstraint.singleSided());
    assertEquals(NetMeanderConstraint.CornerStyle.FILLETED_ROUND, ampConstraint.cornerStyle());
    assertEquals(60, ampConstraint.cornerRadiusPercentage());

    Rule.LengthGapRule gapRule = new Rule.LengthGapRule(0.8);
    NetMeanderConstraint gapConstraint = gapRule.toConstraint(transform);
    assertEquals(NetMeanderConstraint.UNSPECIFIED, gapConstraint.maxAmplitude(), 1e-6);
    assertEquals(800.0, gapConstraint.gap(), 1e-6);

    NetMeanderConstraint merged = ampConstraint.mergeWith(gapConstraint);
    assertEquals(1500.0, merged.maxAmplitude(), 1e-6);
    assertEquals(300.0, merged.minAmplitude(), 1e-6);
    assertEquals(800.0, merged.gap(), 1e-6);
    assertTrue(merged.singleSided());
    assertEquals(NetMeanderConstraint.CornerStyle.FILLETED_ROUND, merged.cornerStyle());
    assertEquals(60, merged.cornerRadiusPercentage());
  }

  @Test
  void testBuildMeanderConstraintAggregatesRules() throws IOException {
    app.freerouting.io.CoordinateTransform transform =
        new app.freerouting.io.CoordinateTransform(1000.0, 0, 0);
    String snippet =
        "(rule (length_amplitude 2.5 0.5 (type trombone) (corner orthogonal)) (length_gap 1.2))";
    Collection<Rule> rules = parseRuleScope(snippet);

    NetMeanderConstraint constraint = Rule.buildMeanderConstraint(null, null, rules, transform);
    assertNotNull(constraint);
    assertEquals(2500.0, constraint.maxAmplitude(), 1e-6);
    assertEquals(500.0, constraint.minAmplitude(), 1e-6);
    assertEquals(1200.0, constraint.gap(), 1e-6);
    assertTrue(constraint.singleSided());
    assertEquals(NetMeanderConstraint.CornerStyle.ORTHOGONAL_90, constraint.cornerStyle());
  }
}
