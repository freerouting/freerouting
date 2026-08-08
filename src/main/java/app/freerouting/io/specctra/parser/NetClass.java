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
      String pName,
      String pTraceClearanceClass,
      Collection<String> pNetList,
      Collection<Rule> pRules,
      Collection<Rule.LayerRule> pLayerRules,
      Collection<String> pUseVia,
      Collection<String> pUseLayer,
      String pViaRule,
      boolean pShoveFixed,
      boolean pPullTight,
      double pMinTraceLength,
      double pMaxTraceLength) {
    name = pName;
    traceClearanceClass = pTraceClearanceClass;
    netList = pNetList;
    rules = pRules;
    layerRules = pLayerRules;
    useVia = pUseVia;
    useLayer = pUseLayer;
    viaRule = pViaRule;
    shoveFixed = pShoveFixed;
    pullTight = pPullTight;
    minTraceLength = pMinTraceLength;
    maxTraceLength = pMaxTraceLength;
  }

  public static NetClass readScope(IJFlexScanner pScanner) {

    try {
      // read the class name
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      String className = pScanner.nextString();

      Collection<String> netList = new LinkedList<>();
      boolean rulesMissing = false;
      // read the nets belonging to the class
      String[] netsInTheClass = pScanner.nextStringList();
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

      Object nextToken = pScanner.nextToken();
      if (!rulesMissing) {
        Object prevToken = nextToken;
        for (; ; ) {
          nextToken = pScanner.nextToken();
          if (nextToken == null) {
            FRLogger.warn(
                "NetClass.read_scope: unexpected end of file at '"
                    + pScanner.getScopeIdentifier()
                    + "'");
            return null;
          }
          if (nextToken == Keyword.CLOSED_BRACKET) {
            // end of scope
            break;
          }
          if (prevToken == Keyword.OPEN_BRACKET) {
            if (nextToken == Keyword.RULE) {
              rules.addAll(Rule.readScope(pScanner));
            } else if (nextToken == Keyword.LAYER_RULE) {
              layerRules.add(Rule.readLayerRuleScope(pScanner));
            } else if (nextToken == Keyword.VIA_RULE) {
              viaRule = DsnFile.readStringScope(pScanner);
            } else if (nextToken == Keyword.CIRCUIT) {
              Circuit.ReadScopeResult currRule = Circuit.readScope(pScanner);
              if (currRule != null) {
                maxTraceLength = currRule.maxLength;
                minTraceLength = currRule.minLength;
                useVia.addAll(currRule.useVia);
                useLayer.addAll(currRule.useLayer);
              }
            } else if (nextToken == Keyword.CLEARANCE_CLASS) {
              traceClearanceClass = DsnFile.readStringScope(pScanner);
              if (traceClearanceClass == null) {
                return null;
              }
            } else if (nextToken == Keyword.SHOVE_FIXED) {
              shoveFixed = DsnFile.readOnOffScope(pScanner);
            } else if (nextToken == Keyword.PULL_TIGHT) {
              pullTight = DsnFile.readOnOffScope(pScanner);
            } else {
              ScopeKeyword.skipScope(pScanner);
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

  public static ClassClass readClassClassScope(IJFlexScanner pScanner) {
    try {
      Collection<String> classes = new LinkedList<>();
      Collection<Rule> rules = new LinkedList<>();
      Collection<Rule.LayerRule> layerRules = new LinkedList<>();
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = pScanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "ClassClass.read_scope: unexpected end of file at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLASSES) {
            classes.addAll(Arrays.stream(DsnFile.readStringListScope(pScanner)).toList());
          } else if (nextToken == Keyword.RULE) {
            rules.addAll(Rule.readScope(pScanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            layerRules.add(Rule.readLayerRuleScope(pScanner));
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
        Collection<String> pClassNames,
        Collection<Rule> pRules,
        Collection<Rule.LayerRule> pLayerRules) {
      classNames = pClassNames;
      rules = pRules;
      layerRules = pLayerRules;
    }
  }
}
