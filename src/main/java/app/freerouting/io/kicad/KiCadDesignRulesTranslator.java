package app.freerouting.io.kicad;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.io.kicad.KiCadRuleCondition.ItemType;
import app.freerouting.io.kicad.KiCadRuleCondition.Tri;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Applies the clearance rules of a KiCad {@code .kicad_dru} file to a board loaded from a Specctra
 * design that KiCad exported without them.
 *
 * <p>The copper items of the board (pins, and per net its traces, vias and conduction areas) are
 * sorted into groups that every rule condition treats alike. For each pair of groups and each
 * layer, the rules are applied as KiCad applies them: the last matching rule sets the clearance,
 * and without a matching rule the clearance of the design (net classes) stays. A group whose
 * clearances change gets its own clearance class, a copy of the class it had; a net class whose
 * nets end up in different groups is split, with copies of its via rule. Groups that behave the
 * same share one class.
 *
 * <p>Where a condition cannot be evaluated (see {@link KiCadRuleCondition}), the rule is taken as
 * matching wherever it may match and the largest of the possible clearances is used: the board is
 * never less strict than KiCad, only more. Everything that is not translated is reported in {@link
 * Result#warnings()}.
 */
public final class KiCadDesignRulesTranslator {

  /** Name suffix of the clearance classes, via infos and via rules made by the translation. */
  public static final String SUFFIX = "#dru";

  /**
   * What the translation did.
   *
   * @param translatedRules the clearance rules applied to the board
   * @param addedClearanceClasses the number of clearance classes added
   * @param splitNetClasses for each split net class, the names of its parts
   * @param warnings what is not translated, or translated more strictly than written
   */
  public record Result(
      int translatedRules,
      int addedClearanceClasses,
      Map<String, List<String>> splitNetClasses,
      List<String> warnings) {}

  private enum Kind {
    TRACE,
    VIA,
    PIN,
    AREA
  }

  /** A set of copper items with the same kind, net, clearance class and KiCad attributes. */
  private record EntityKey(Kind kind, int net, int clearanceClass, int pinItemId) {}

  private record Entity(EntityKey key, KiCadRuleCondition.Item item) {}

  private record ClearanceRule(
      String name, boolean[] layers, KiCadRuleCondition condition, int value) {}

  private final BasicBoard board;
  private final ClearanceMatrix matrix;
  private final int layerCount;
  private final List<String> warnings = new ArrayList<>();

  private KiCadDesignRulesTranslator(BasicBoard board) {
    this.board = board;
    this.matrix = board.rules.clearanceMatrix;
    this.layerCount = matrix.getLayerCount();
  }

  /** Applies the clearance rules to the board. */
  public static Result apply(BasicBoard board, KiCadDesignRules designRules) {
    return new KiCadDesignRulesTranslator(board).run(designRules);
  }

  private Result run(KiCadDesignRules designRules) {
    String source = designRules.sourceName();
    for (String warning : designRules.warnings()) {
      warnings.add(source + ": " + warning);
    }
    List<ClearanceRule> rules = clearanceRules(designRules);
    if (rules.isEmpty()) {
      return new Result(0, 0, Map.of(), List.copyOf(warnings));
    }

    // 1. The copper items, by entity
    Map<EntityKey, Entity> entities = new LinkedHashMap<>();
    Map<Item, EntityKey> itemEntities = new LinkedHashMap<>();
    collectEntities(entities, itemEntities);

    // 2. Cells: entities that every condition treats alike, with the same clearance class
    List<Entity> entityList = new ArrayList<>(entities.values());
    Map<String, Integer> cellOfSignature = new LinkedHashMap<>();
    List<Entity> cellRepresentatives = new ArrayList<>();
    Map<EntityKey, Integer> cellOfEntity = new HashMap<>();
    for (Entity entity : entityList) {
      StringBuilder signature = new StringBuilder().append(entity.key().clearanceClass());
      for (ClearanceRule rule : rules) {
        signature.append('|');
        if (rule.condition() != null) {
          signature.append(rule.condition().signature(entity.item()));
        }
      }
      int cell =
          cellOfSignature.computeIfAbsent(
              signature.toString(),
              s -> {
                cellRepresentatives.add(entity);
                return cellRepresentatives.size() - 1;
              });
      cellOfEntity.put(entity.key(), cell);
    }

    // 3. The clearance KiCad requires between two cells, where it differs from the design
    int cells = cellRepresentatives.size();
    int[][][] custom = new int[cells][cells][];
    for (int i = 0; i < cells; i++) {
      for (int j = i; j < cells; j++) {
        int[] values =
            customClearance(rules, cellRepresentatives.get(i), cellRepresentatives.get(j));
        custom[i][j] = values;
        custom[j][i] = values;
      }
    }

    // 4. Groups of cells with the same clearance class and the same clearances to every cell
    Map<String, Integer> groupOfSignature = new LinkedHashMap<>();
    int[] groupOfCell = new int[cells];
    List<Integer> groupFirstCell = new ArrayList<>();
    for (int i = 0; i < cells; i++) {
      StringBuilder signature =
          new StringBuilder().append(cellRepresentatives.get(i).key().clearanceClass());
      for (int j = 0; j < cells; j++) {
        signature.append('|').append(custom[i][j] == null ? "" : Arrays.toString(custom[i][j]));
      }
      final int cell = i;
      groupOfCell[i] =
          groupOfSignature.computeIfAbsent(
              signature.toString(),
              s -> {
                groupFirstCell.add(cell);
                return groupFirstCell.size() - 1;
              });
    }

    // 5. A new clearance class for each group whose clearances change
    int groups = groupFirstCell.size();
    int[] classOfGroup = new int[groups];
    int added = 0;
    for (int g = 0; g < groups; g++) {
      int cell = groupFirstCell.get(g);
      int base = cellRepresentatives.get(cell).key().clearanceClass();
      boolean changed = false;
      for (int j = 0; j < cells && !changed; j++) {
        changed = custom[cell][j] != null;
      }
      classOfGroup[g] = changed ? copyClass(base) : base;
      added += changed ? 1 : 0;
    }
    for (int g = 0; g < groups; g++) {
      for (int h = g; h < groups; h++) {
        int[] values = custom[groupFirstCell.get(g)][groupFirstCell.get(h)];
        if (values == null) {
          continue;
        }
        for (int layer = 0; layer < layerCount; layer++) {
          if (values[layer] >= 0) {
            matrix.setValue(classOfGroup[g], classOfGroup[h], layer, values[layer]);
            matrix.setValue(classOfGroup[h], classOfGroup[g], layer, values[layer]);
          }
        }
      }
    }
    Map<EntityKey, Integer> newClass = new HashMap<>();
    for (Map.Entry<EntityKey, Integer> entry : cellOfEntity.entrySet()) {
      newClass.put(entry.getKey(), classOfGroup[groupOfCell[entry.getValue()]]);
    }

    // 6. The items, and the net classes for the items the router will add
    for (Map.Entry<Item, EntityKey> entry : itemEntities.entrySet()) {
      int target = newClass.get(entry.getValue());
      if (entry.getKey().clearanceClassIndex() != target) {
        entry.getKey().setClearanceClassIndex(target);
      }
    }
    Map<String, List<String>> split = updateNetClasses(newClass);
    if (added > 0) {
      board.searchTreeManager.reinsertTreeItems();
    }
    return new Result(rules.size(), added, split, List.copyOf(warnings));
  }

  // ------------------------------------------------------------------------------------------

  private List<ClearanceRule> clearanceRules(KiCadDesignRules designRules) {
    List<ClearanceRule> result = new ArrayList<>();
    for (KiCadDesignRules.Rule rule : designRules.rules()) {
      String prefix = "rule '" + rule.name() + "': ";
      for (String warning : rule.warnings()) {
        warnings.add(prefix + warning);
      }
      for (String constraint : rule.otherConstraints()) {
        if (constraint.startsWith("creepage") && rule.clearanceUm() != null) {
          warnings.add(
              prefix
                  + "constraint "
                  + constraint
                  + " not translated (Specctra has no creepage), only the clearance of "
                  + KiCadDesignRules.formatMm(rule.clearanceUm())
                  + " of the rule applies");
        } else {
          warnings.add(
              prefix
                  + "constraint "
                  + constraint
                  + " not translated (only minimum clearances are translated)");
        }
      }
      if (rule.clearanceUm() == null) {
        continue;
      }
      if (rule.ignored()) {
        warnings.add(prefix + "severity ignore: the clearance is not relaxed, the rule is skipped");
        continue;
      }
      boolean[] layers = new boolean[layerCount];
      if (rule.layer() == null) {
        Arrays.fill(layers, true);
      } else if ("outer".equalsIgnoreCase(rule.layer())) {
        layers[0] = true;
        layers[layerCount - 1] = true;
      } else if ("inner".equalsIgnoreCase(rule.layer())) {
        for (int layer = 1; layer < layerCount - 1; layer++) {
          layers[layer] = true;
        }
      } else {
        Pattern pattern = KiCadRuleCondition.wildcard(rule.layer());
        boolean any = false;
        for (int layer = 0; layer < layerCount; layer++) {
          layers[layer] = pattern.matcher(board.layerStructure.layers[layer].name).matches();
          any |= layers[layer];
        }
        if (!any) {
          warnings.add(
              prefix + "layer '" + rule.layer() + "' is not a copper layer of the design, skipped");
          continue;
        }
      }
      if (rule.condition() != null && !rule.condition().unsupportedParts().isEmpty()) {
        warnings.add(
            prefix
                + String.join(", ", rule.condition().unsupportedParts())
                + " cannot be evaluated: the rule is applied wherever it may match");
      }
      int value = (int) Math.ceil(board.communication.physicalToBoard(rule.clearanceUm(), Unit.UM));
      result.add(new ClearanceRule(rule.name(), layers, rule.condition(), value));
    }
    return result;
  }

  /**
   * KiCad's name of a net class of the design: KiCad exports its Default class as kicad_default.
   */
  private static String kicadClassName(NetClass netClass) {
    if (netClass == null || "kicad_default".equals(netClass.getName())) {
      return "Default";
    }
    return netClass.getName();
  }

  private void collectEntities(Map<EntityKey, Entity> entities, Map<Item, EntityKey> items) {
    // The traces and vias the router may still add: the classes of the net classes
    for (int netNo = 1; netNo <= board.rules.nets.maxNetNumber(); netNo++) {
      Net net = board.rules.nets.get(netNo);
      if (net == null || net.getNetClass() == null) {
        continue;
      }
      NetClass netClass = net.getNetClass();
      addEntity(entities, Kind.TRACE, net, netClass.getTraceClearanceClass(), null);
      addEntity(
          entities, Kind.VIA, net, netClass.defaultItemClearanceClasses.get(ItemClass.VIA), null);
      ViaRule viaRule = netClass.getViaRule();
      for (int i = 0; viaRule != null && i < viaRule.viaCount(); i++) {
        addEntity(entities, Kind.VIA, net, viaRule.getVia(i).getClearanceClassIndex(), null);
      }
    }
    // The items of the board
    for (Item item : board.getItems()) {
      Kind kind;
      if (item instanceof Pin) {
        kind = Kind.PIN;
      } else if (item instanceof Trace) {
        kind = Kind.TRACE;
      } else if (item instanceof Via) {
        kind = Kind.VIA;
      } else if (item instanceof ConductionArea && item.netCount() > 0) {
        kind = Kind.AREA;
      } else {
        continue;
      }
      Net net = item.netCount() > 0 ? board.rules.nets.get(item.getNetNumber(0)) : null;
      if (kind != Kind.PIN && net == null) {
        continue;
      }
      items.put(item, addEntity(entities, kind, net, item.clearanceClassIndex(), item));
    }
  }

  private EntityKey addEntity(
      Map<EntityKey, Entity> entities, Kind kind, Net net, int clearanceClass, Item pinItem) {
    int netNo = net == null ? 0 : net.netNumber;
    int pinId = kind == Kind.PIN ? pinItem.getId() : 0;
    EntityKey key = new EntityKey(kind, netNo, clearanceClass, pinId);
    entities.computeIfAbsent(key, k -> new Entity(k, describe(kind, net, pinItem)));
    return key;
  }

  private KiCadRuleCondition.Item describe(Kind kind, Net net, Item pinItem) {
    String netName = net == null ? "" : net.name;
    String netClass = kicadClassName(net == null ? null : net.getNetClass());
    if (kind != Kind.PIN) {
      ItemType type =
          switch (kind) {
            case TRACE -> ItemType.TRACK;
            case VIA -> ItemType.VIA;
            default -> ItemType.ZONE;
          };
      return new KiCadRuleCondition.Item(type, netName, netClass, null, null, false);
    }
    Pin pin = (Pin) pinItem;
    Component component = board.components.get(pin.getComponentId());
    String padNumber = pin.name();
    if (padNumber != null && padNumber.indexOf('@') > 0) {
      padNumber = padNumber.substring(0, padNumber.indexOf('@')); // duplicated pad numbers
    }
    return new KiCadRuleCondition.Item(
        ItemType.PAD,
        netName,
        netClass,
        component == null ? null : component.name,
        padNumber,
        pin.firstLayer() == pin.lastLayer(),
        libraryId(component));
  }

  /**
   * The KiCad library identifier of a component, null if unknown: the name of its Specctra image,
   * less the suffix ({@code ::1}, {@code ::2}...) the KiCad export gives to footprint variants.
   */
  static String libraryId(Component component) {
    if (component == null || component.getPackage() == null) {
      return null;
    }
    String name = component.getPackage().name;
    int variant = name.lastIndexOf("::");
    if (variant > 0 && variant + 2 < name.length()) {
      boolean digits = true;
      for (int i = variant + 2; i < name.length(); i++) {
        digits &= Character.isDigit(name.charAt(i));
      }
      if (digits) {
        name = name.substring(0, variant);
      }
    }
    return name;
  }

  /**
   * The clearance per layer between two cells after the rules, or null where it does not differ
   * from the clearance of their classes; -1 on the layers where it does not.
   */
  private int[] customClearance(List<ClearanceRule> rules, Entity a, Entity b) {
    int classA = a.key().clearanceClass();
    int classB = b.key().clearanceClass();
    int[] result = null;
    Tri[] matches = new Tri[rules.size()];
    for (int r = 0; r < rules.size(); r++) {
      ClearanceRule rule = rules.get(r);
      matches[r] =
          rule.condition() == null ? Tri.TRUE : rule.condition().evaluate(a.item(), b.item());
    }
    for (int layer = 0; layer < layerCount; layer++) {
      int base = matrix.getValue(classA, classB, layer, false);
      // The possible outcomes of the rules on this layer: the clearance of the design, unless a
      // rule surely matches, and the value of every rule that may match after it.
      boolean designPossible = true;
      int best = -1;
      for (int r = 0; r < rules.size(); r++) {
        ClearanceRule rule = rules.get(r);
        if (!rule.layers()[layer]) {
          continue;
        }
        Tri match = matches[r];
        if (match == Tri.TRUE) {
          designPossible = false;
          best = rule.value();
        } else if (match == Tri.UNKNOWN) {
          best = Math.max(best, rule.value());
        }
      }
      if (best < 0) {
        continue;
      }
      int value = designPossible ? Math.max(best, base) : best;
      // the matrix stores even values, rounded up
      if ((value + 1) / 2 * 2 == base) {
        continue;
      }
      if (result == null) {
        result = new int[layerCount];
        Arrays.fill(result, -1);
      }
      result[layer] = value;
    }
    return result;
  }

  private int copyClass(int source) {
    String baseName = matrix.getName(source) + SUFFIX;
    int index = 1;
    while (matrix.getNo(baseName + index) >= 0) {
      index++;
    }
    String name = baseName + index;
    matrix.appendClass(name);
    int result = matrix.getNo(name);
    for (int layer = 0; layer < layerCount; layer++) {
      for (int other = 1; other < result; other++) {
        int value = matrix.getValue(source, other, layer, false);
        matrix.setValue(result, other, layer, value);
        matrix.setValue(other, result, layer, value);
      }
      matrix.setValue(result, result, layer, matrix.getValue(source, source, layer, false));
    }
    return result;
  }

  /** Points the net classes at the new classes, splitting those whose nets now differ. */
  private Map<String, List<String>> updateNetClasses(Map<EntityKey, Integer> newClass) {
    Map<String, List<String>> split = new LinkedHashMap<>();
    Map<NetClass, Map<List<Integer>, List<Net>>> partsOfClass = new LinkedHashMap<>();
    for (int netNo = 1; netNo <= board.rules.nets.maxNetNumber(); netNo++) {
      Net net = board.rules.nets.get(netNo);
      if (net == null || net.getNetClass() == null) {
        continue;
      }
      NetClass netClass = net.getNetClass();
      List<Integer> classes = new ArrayList<>();
      classes.add(newClass.get(key(Kind.TRACE, net, netClass.getTraceClearanceClass())));
      classes.add(
          newClass.get(
              key(Kind.VIA, net, netClass.defaultItemClearanceClasses.get(ItemClass.VIA))));
      ViaRule viaRule = netClass.getViaRule();
      for (int i = 0; viaRule != null && i < viaRule.viaCount(); i++) {
        classes.add(newClass.get(key(Kind.VIA, net, viaRule.getVia(i).getClearanceClassIndex())));
      }
      partsOfClass
          .computeIfAbsent(netClass, c -> new LinkedHashMap<>())
          .computeIfAbsent(classes, c -> new ArrayList<>())
          .add(net);
    }
    for (Map.Entry<NetClass, Map<List<Integer>, List<Net>>> entry : partsOfClass.entrySet()) {
      NetClass original = entry.getKey();
      List<String> names = new ArrayList<>();
      int part = 0;
      for (Map.Entry<List<Integer>, List<Net>> netsOfPart : entry.getValue().entrySet()) {
        List<Integer> classes = netsOfPart.getKey();
        NetClass target = original;
        if (part > 0) {
          String name = original.getName() + "_" + (part + 1);
          while (board.rules.netClasses.get(name) != null) {
            name += "_";
          }
          target =
              board.rules.netClasses.append(
                  name, board.layerStructure, matrix, original.isIgnoredByAutorouter);
          target.copySettingsFrom(original);
        }
        names.add(target.getName());
        applyClasses(original, target, classes);
        if (target != original) {
          for (Net net : netsOfPart.getValue()) {
            net.setClass(target);
          }
        }
        part++;
      }
      if (names.size() > 1) {
        split.put(original.getName(), names);
      }
    }
    return split;
  }

  private static EntityKey key(Kind kind, Net net, int clearanceClass) {
    return new EntityKey(kind, net.netNumber, clearanceClass, 0);
  }

  /** Sets the trace and via classes of a part of a net class (classes as in updateNetClasses). */
  private void applyClasses(NetClass original, NetClass target, List<Integer> classes) {
    target.setTraceClearanceClass(classes.get(0));
    target.defaultItemClearanceClasses.set(ItemClass.TRACE, classes.get(0));
    target.defaultItemClearanceClasses.set(ItemClass.VIA, classes.get(1));
    ViaRule viaRule = original.getViaRule();
    if (viaRule == null) {
      return;
    }
    boolean changed = false;
    for (int i = 0; i < viaRule.viaCount(); i++) {
      changed |= viaRule.getVia(i).getClearanceClassIndex() != classes.get(i + 2);
    }
    if (!changed) {
      target.setViaRule(viaRule);
      return;
    }
    String ruleName = viaRule.name + SUFFIX + "_" + target.getName();
    while (board.rules.getViaRule(ruleName) != null) {
      ruleName += "_";
    }
    ViaRule newRule = new ViaRule(ruleName);
    for (int i = 0; i < viaRule.viaCount(); i++) {
      ViaInfo via = viaRule.getVia(i);
      int clearanceClass = classes.get(i + 2);
      if (via.getClearanceClassIndex() != clearanceClass) {
        String viaName = via.getName() + SUFFIX + "_" + matrix.getName(clearanceClass);
        while (board.rules.viaInfos.nameExists(viaName)) {
          viaName += "_";
        }
        via =
            new ViaInfo(
                viaName, via.getPadstack(), clearanceClass, via.attachSmdAllowed(), board.rules);
        board.rules.viaInfos.add(via);
      }
      newRule.appendVia(via);
    }
    board.rules.viaRules.add(newRule);
    target.setViaRule(newRule);
  }

  /** A short description of the result, for the log. */
  public static String summary(Result result, String sourceName) {
    StringBuilder text =
        new StringBuilder(
            String.format(
                Locale.ROOT,
                "KiCad design rules '%s': %d clearance rule(s) applied, %d clearance class(es)"
                    + " added",
                sourceName,
                result.translatedRules(),
                result.addedClearanceClasses()));
    for (Map.Entry<String, List<String>> entry : result.splitNetClasses().entrySet()) {
      text.append(", net class '")
          .append(entry.getKey())
          .append("' split into ")
          .append(entry.getValue());
    }
    if (!result.warnings().isEmpty()) {
      text.append(", ").append(result.warnings().size()).append(" warning(s)");
    }
    return text.append('.').toString();
  }
}
