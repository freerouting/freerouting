package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/** Contains the information of a Specctra Class scope. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class NetClass {

  public final String name;
  public final String traceClearanceClass;
  public final Collection<String> netList;
  public final Collection<Rule> rules;
  public final Collection<Rule.LayerRule> layerRules;
  public final Collection<String> useVia;
  public final Collection<String> useLayer;
  public final String viaRule;
  public final boolean shoveFixed;
  public final boolean pullTight;
  public double minTraceLength;
  public double maxTraceLength;

  /** Creates a new instance of NetClass. */
  public NetClass(
      String name,
      String traceClearanceClass,
      Collection<String> netList,
      Collection<Rule> rules,
      Collection<Rule.LayerRule> layerRules,
      Collection<String> useVia,
      Collection<String> useLayer,
      String viaRule,
      boolean shoveFixed,
      boolean pullTight,
      double minTraceLength,
      double maxTraceLength) {
    this.name = name;
    this.traceClearanceClass = traceClearanceClass;
    this.netList = netList;
    this.rules = rules;
    this.layerRules = layerRules;
    this.useVia = useVia;
    this.useLayer = useLayer;
    this.viaRule = viaRule;
    this.shoveFixed = shoveFixed;
    this.pullTight = pullTight;
    this.minTraceLength = minTraceLength;
    this.maxTraceLength = maxTraceLength;
  }

  public static NetClass readScope(IJFlexScanner scanner) {

    try {
      // read the class name
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      String className = scanner.nextString();

      Collection<String> netList = new LinkedList<>();
      boolean rulesMissing = false;
      // read the nets belonging to the class
      String[] netsInTheClass = scanner.nextStringList();
      netList.addAll(List.of(netsInTheClass));

      Collection<Rule> rules = new LinkedList<>();
      Collection<Rule.LayerRule> layerRules = new LinkedList<>();
      Collection<String> useVia = new LinkedList<>();
      Collection<String> useLayer = new LinkedList<>();
      String viaRule = null;
      String traceClearanceClass = null;
      boolean pullTight = true;
      boolean shoveFixed = false;
      double minTraceLength = 0;
      double maxTraceLength = 0;

      Object nextToken = scanner.nextToken();
      if (!rulesMissing) {
        Object prevToken = nextToken;
        for (; ; ) {
          nextToken = scanner.nextToken();
          if (nextToken == null) {
            FRLogger.warn(
                "NetClass.read_scope: unexpected end of file at '"
                    + scanner.getScopeIdentifier()
                    + "'");
            return null;
          }
          if (nextToken == Keyword.CLOSED_BRACKET) {
            // end of scope
            break;
          }
          if (prevToken == Keyword.OPEN_BRACKET) {
            if (nextToken == Keyword.RULE) {
              rules.addAll(Rule.readScope(scanner));
            } else if (nextToken == Keyword.LAYER_RULE) {
              layerRules.add(Rule.readLayerRuleScope(scanner));
            } else if (nextToken == Keyword.VIA_RULE) {
              viaRule = DsnFile.readStringScope(scanner);
            } else if (nextToken == Keyword.CIRCUIT) {
              Circuit.ReadScopeResult currRule = Circuit.readScope(scanner);
              if (currRule != null) {
                maxTraceLength = currRule.maxLength;
                minTraceLength = currRule.minLength;
                useVia.addAll(currRule.useVia);
                useLayer.addAll(currRule.useLayer);
              }
            } else if (nextToken == Keyword.CLEARANCE_CLASS) {
              traceClearanceClass = DsnFile.readStringScope(scanner);
              if (traceClearanceClass == null) {
                return null;
              }
            } else if (nextToken == Keyword.SHOVE_FIXED) {
              shoveFixed = DsnFile.readOnOffScope(scanner);
            } else if (nextToken == Keyword.PULL_TIGHT) {
              pullTight = DsnFile.readOnOffScope(scanner);
            } else {
              ScopeKeyword.skipScope(scanner);
            }
          }
          prevToken = nextToken;
        }
      }
      return new NetClass(
          className,
          traceClearanceClass,
          netList,
          rules,
          layerRules,
          useVia,
          useLayer,
          viaRule,
          shoveFixed,
          pullTight,
          minTraceLength,
          maxTraceLength);
    } catch (IOException e) {
      FRLogger.error("NetClass.read_scope: IO error while scanning file", e);
      return null;
    }
  }

  public static ClassClass readClassClassScope(IJFlexScanner scanner) {
    try {
      Collection<String> classes = new LinkedList<>();
      Collection<Rule> rules = new LinkedList<>();
      Collection<Rule.LayerRule> layerRules = new LinkedList<>();
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "ClassClass.read_scope: unexpected end of file at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLASSES) {
            classes.addAll(Arrays.stream(DsnFile.readStringListScope(scanner)).toList());
          } else if (nextToken == Keyword.RULE) {
            rules.addAll(Rule.readScope(scanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            layerRules.add(Rule.readLayerRuleScope(scanner));
          }
        }
        prevToken = nextToken;
      }
      return new ClassClass(classes, rules, layerRules);
    } catch (IOException e) {
      FRLogger.error("NetClass.read_scope: IO error while scanning file", e);
      return null;
    }
  }

  public static class ClassClass {

    public final Collection<String> classNames;
    public final Collection<Rule> rules;
    public final Collection<Rule.LayerRule> layerRules;

    public ClassClass(
        Collection<String> classNames,
        Collection<Rule> rules,
        Collection<Rule.LayerRule> layerRules) {
      this.classNames = classNames;
      this.rules = rules;
      this.layerRules = layerRules;
    }
  }
}
