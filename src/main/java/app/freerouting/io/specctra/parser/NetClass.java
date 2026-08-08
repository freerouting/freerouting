package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/** Contains the information of a Specctra Class scope. */
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
  public final double minTraceLength;
  public final double maxTraceLength;

  /** Creates a new instance of NetClass */
  public NetClass(
      String p_name,
      String p_trace_clearance_class,
      Collection<String> p_net_list,
      Collection<Rule> p_rules,
      Collection<Rule.LayerRule> p_layer_rules,
      Collection<String> p_use_via,
      Collection<String> p_use_layer,
      String p_via_rule,
      boolean p_shove_fixed,
      boolean p_pull_tight,
      double p_min_trace_length,
      double p_max_trace_length) {
    name = p_name;
    traceClearanceClass = p_trace_clearance_class;
    netList = p_net_list;
    rules = p_rules;
    layerRules = p_layer_rules;
    useVia = p_use_via;
    useLayer = p_use_layer;
    viaRule = p_via_rule;
    shoveFixed = p_shove_fixed;
    pullTight = p_pull_tight;
    minTraceLength = p_min_trace_length;
    maxTraceLength = p_max_trace_length;
  }

  public static NetClass readScope(IJFlexScanner p_scanner) {

    try {
      // read the class name
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      String className = p_scanner.nextString();

      Collection<String> netList = new LinkedList<>();
      boolean rulesMissing = false;
      // read the nets belonging to the class
      String[] netsInTheClass = p_scanner.nextStringList();
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

      Object nextToken = p_scanner.nextToken();
      if (!rulesMissing) {
        Object prevToken = nextToken;
        for (; ; ) {
          nextToken = p_scanner.nextToken();
          if (nextToken == null) {
            FRLogger.warn(
                "NetClass.read_scope: unexpected end of file at '"
                    + p_scanner.getScopeIdentifier()
                    + "'");
            return null;
          }
          if (nextToken == Keyword.CLOSED_BRACKET) {
            // end of scope
            break;
          }
          if (prevToken == Keyword.OPEN_BRACKET) {
            if (nextToken == Keyword.RULE) {
              rules.addAll(Rule.readScope(p_scanner));
            } else if (nextToken == Keyword.LAYER_RULE) {
              layerRules.add(Rule.readLayerRuleScope(p_scanner));
            } else if (nextToken == Keyword.VIA_RULE) {
              viaRule = DsnFile.readStringScope(p_scanner);
            } else if (nextToken == Keyword.CIRCUIT) {
              Circuit.ReadScopeResult currRule = Circuit.readScope(p_scanner);
              if (currRule != null) {
                maxTraceLength = currRule.maxLength;
                minTraceLength = currRule.minLength;
                useVia.addAll(currRule.useVia);
                useLayer.addAll(currRule.useLayer);
              }
            } else if (nextToken == Keyword.CLEARANCE_CLASS) {
              traceClearanceClass = DsnFile.readStringScope(p_scanner);
              if (traceClearanceClass == null) {
                return null;
              }
            } else if (nextToken == Keyword.SHOVE_FIXED) {
              shoveFixed = DsnFile.readOnOffScope(p_scanner);
            } else if (nextToken == Keyword.PULL_TIGHT) {
              pullTight = DsnFile.readOnOffScope(p_scanner);
            } else {
              ScopeKeyword.skipScope(p_scanner);
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

  public static ClassClass readClassClassScope(IJFlexScanner p_scanner) {
    try {
      Collection<String> classes = new LinkedList<>();
      Collection<Rule> rules = new LinkedList<>();
      Collection<Rule.LayerRule> layerRules = new LinkedList<>();
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = p_scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "ClassClass.read_scope: unexpected end of file at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLASSES) {
            classes.addAll(Arrays.stream(DsnFile.readStringListScope(p_scanner)).toList());
          } else if (nextToken == Keyword.RULE) {
            rules.addAll(Rule.readScope(p_scanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            layerRules.add(Rule.readLayerRuleScope(p_scanner));
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
        Collection<String> p_class_names,
        Collection<Rule> p_rules,
        Collection<Rule.LayerRule> p_layer_rules) {
      classNames = p_class_names;
      rules = p_rules;
      layerRules = p_layer_rules;
    }
  }
}
