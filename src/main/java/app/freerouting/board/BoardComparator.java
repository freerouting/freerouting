package app.freerouting.board;

import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.logger.FRLogger;
import app.freerouting.datastructures.UndoableObjects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BoardComparator {

  public static class ComparisonResult {
    public final boolean areEqual;
    public final String report;

    public ComparisonResult(boolean areEqual, String report) {
      this.areEqual = areEqual;
      this.report = report;
    }
  }

  public static ComparisonResult compare(RoutingBoard board1, RoutingBoard board2, double epsilonMm) {
    StringBuilder report = new StringBuilder();
    boolean equal = true;

    report.append("=== Freerouting Board Parity Report ===\n");
    report.append(String.format("Comparing Board 1 (from DSN/JSON) and Board 2 (from JSON/DSN) with epsilon = %.6f mm\n\n", epsilonMm));

    // 1. Check units and scaling factors
    double scale1 = getMmFactor(board1);
    double scale2 = getMmFactor(board2);

    // 2. Bounding Box
    IntBox box1 = board1.get_bounding_box();
    IntBox box2 = board2.get_bounding_box();
    double box1_ll_x = box1.ll.x * scale1;
    double box1_ll_y = box1.ll.y * scale1;
    double box1_ur_x = box1.ur.x * scale1;
    double box1_ur_y = box1.ur.y * scale1;

    double box2_ll_x = box2.ll.x * scale2;
    double box2_ll_y = box2.ll.y * scale2;
    double box2_ur_x = box2.ur.x * scale2;
    double box2_ur_y = box2.ur.y * scale2;

    if (Math.abs(box1_ll_x - box2_ll_x) > epsilonMm || Math.abs(box1_ll_y - box2_ll_y) > epsilonMm ||
        Math.abs(box1_ur_x - box2_ur_x) > epsilonMm || Math.abs(box1_ur_y - box2_ur_y) > epsilonMm) {
      equal = false;
      report.append(String.format("[-] Bounding Box mismatch:\n" +
          "    Board 1: LL(%.4f, %.4f) UR(%.4f, %.4f) mm\n" +
          "    Board 2: LL(%.4f, %.4f) UR(%.4f, %.4f) mm\n\n",
          box1_ll_x, box1_ll_y, box1_ur_x, box1_ur_y,
          box2_ll_x, box2_ll_y, box2_ur_x, box2_ur_y));
    } else {
      report.append("[+] Bounding Boxes match.\n");
    }

    // 3. Layers
    int layersCount1 = board1.get_layer_count();
    int layersCount2 = board2.get_layer_count();
    if (layersCount1 != layersCount2) {
      equal = false;
      report.append(String.format("[-] Layer count mismatch: Board 1 = %d, Board 2 = %d\n", layersCount1, layersCount2));
    } else {
      report.append(String.format("[+] Layer counts match: %d\n", layersCount1));
      for (int i = 0; i < layersCount1; i++) {
        String name1 = board1.layer_structure.arr[i].name;
        String name2 = board2.layer_structure.arr[i].name;
        if (!name1.equalsIgnoreCase(name2)) {
          equal = false;
          report.append(String.format("[-] Layer %d name mismatch: Board 1 = '%s', Board 2 = '%s'\n", i, name1, name2));
        }
      }
    }

    // 4. Components
    int compCount1 = board1.components.count();
    int compCount2 = board2.components.count();
    if (compCount1 != compCount2) {
      equal = false;
      report.append(String.format("[-] Component count mismatch: Board 1 = %d, Board 2 = %d\n", compCount1, compCount2));
    } else {
      report.append(String.format("[+] Component counts match: %d\n", compCount1));
    }

    Map<String, Component> compMap1 = new HashMap<>();
    Map<String, Component> compMap2 = new HashMap<>();
    for (Component c : board1.components.get_all()) {
      compMap1.put(c.name, c);
    }
    for (Component c : board2.components.get_all()) {
      compMap2.put(c.name, c);
    }

    for (String compName : compMap1.keySet()) {
      Component c1 = compMap1.get(compName);
      Component c2 = compMap2.get(compName);
      if (c2 == null) {
        equal = false;
        report.append(String.format("[-] Component '%s' is present in Board 1 but missing in Board 2\n", compName));
        continue;
      }
      // Compare details
      Point p1 = c1.get_location();
      Point p2 = c2.get_location();
      if (p1 == null || p2 == null) {
        if (p1 != p2) {
          equal = false;
          report.append(String.format("[-] Component '%s' placement mismatch (one is placed, one is not)\n", compName));
        }
      } else {
        double x1 = p1.to_float().x * scale1;
        double y1 = p1.to_float().y * scale1;
        double x2 = p2.to_float().x * scale2;
        double y2 = p2.to_float().y * scale2;
        if (Math.abs(x1 - x2) > epsilonMm || Math.abs(y1 - y2) > epsilonMm) {
          equal = false;
          report.append(String.format("[-] Component '%s' position mismatch: Board 1 = (%.4f, %.4f), Board 2 = (%.4f, %.4f) mm\n",
              compName, x1, y1, x2, y2));
        }
      }
      double rot1 = c1.get_rotation_in_degree();
      double rot2 = c2.get_rotation_in_degree();
      if (Math.abs(rot1 - rot2) > 0.01) {
        equal = false;
        report.append(String.format("[-] Component '%s' rotation mismatch: Board 1 = %.2f, Board 2 = %.2f\n",
            compName, rot1, rot2));
      }
      if (c1.placed_on_front() != c2.placed_on_front()) {
        equal = false;
        report.append(String.format("[-] Component '%s' layer side mismatch: Board 1 Front = %b, Board 2 Front = %b\n",
            compName, c1.placed_on_front(), c2.placed_on_front()));
      }
    }

    // 5. Pins
    Collection<Pin> pins1 = board1.get_pins();
    Collection<Pin> pins2 = board2.get_pins();
    if (pins1.size() != pins2.size()) {
      equal = false;
      report.append(String.format("[-] Pin count mismatch: Board 1 = %d, Board 2 = %d\n", pins1.size(), pins2.size()));
    }

    Map<String, Pin> pinMap1 = new HashMap<>();
    Map<String, Pin> pinMap2 = new HashMap<>();
    for (Pin p : pins1) {
      Component comp = board1.components.get(p.get_component_no());
      if (comp != null) {
        pinMap1.put(comp.name + "." + p.name(), p);
      }
    }
    for (Pin p : pins2) {
      Component comp = board2.components.get(p.get_component_no());
      if (comp != null) {
        pinMap2.put(comp.name + "." + p.name(), p);
      }
    }

    for (String pinKey : pinMap1.keySet()) {
      Pin p1 = pinMap1.get(pinKey);
      Pin p2 = pinMap2.get(pinKey);
      if (p2 == null) {
        equal = false;
        report.append(String.format("[-] Pin '%s' is present in Board 1 but missing in Board 2\n", pinKey));
        continue;
      }
      Point c1 = p1.get_center();
      Point c2 = p2.get_center();
      double x1 = c1.to_float().x * scale1;
      double y1 = c1.to_float().y * scale1;
      double x2 = c2.to_float().x * scale2;
      double y2 = c2.to_float().y * scale2;
      if (Math.abs(x1 - x2) > epsilonMm || Math.abs(y1 - y2) > epsilonMm) {
        equal = false;
        report.append(String.format("[-] Pin '%s' center mismatch: Board 1 = (%.4f, %.4f), Board 2 = (%.4f, %.4f) mm\n",
            pinKey, x1, y1, x2, y2));
      }
    }

    // 6. Net Classes (order-independent: match by name)
    Map<String, NetClass> netClassMap1 = buildNetClassMap(board1);
    Map<String, NetClass> netClassMap2 = buildNetClassMap(board2);
    if (netClassMap1.size() != netClassMap2.size()) {
      equal = false;
      report.append(String.format("[-] Net class count mismatch: Board 1 = %d, Board 2 = %d\n",
          netClassMap1.size(), netClassMap2.size()));
    } else {
      report.append(String.format("[+] Net class counts match: %d\n", netClassMap1.size()));
    }
    equal &= reportMissingKeys("Net class", netClassMap1.keySet(), netClassMap2.keySet(), report);

    // 7. Nets (order-independent: match by name)
    Map<String, Net> netsMap1 = buildNetMap(board1);
    Map<String, Net> netsMap2 = buildNetMap(board2);
    if (netsMap1.size() != netsMap2.size()) {
      equal = false;
      report.append(String.format("[-] Net count mismatch: Board 1 = %d, Board 2 = %d\n",
          netsMap1.size(), netsMap2.size()));
    } else {
      report.append(String.format("[+] Net counts match: %d\n", netsMap1.size()));
    }
    equal &= reportMissingKeys("Net", netsMap1.keySet(), netsMap2.keySet(), report);

    for (String netName : netsMap1.keySet()) {
      Net n1 = netsMap1.get(netName);
      Net n2 = netsMap2.get(netName);
      if (n2 == null) {
        continue;
      }
      if (n1.get_pins().size() != n2.get_pins().size()) {
        equal = false;
        report.append(String.format("[-] Net '%s' pin count mismatch: Board 1 = %d, Board 2 = %d\n",
            netName, n1.get_pins().size(), n2.get_pins().size()));
      }
      String class1 = n1.get_class().get_name();
      String class2 = n2.get_class().get_name();
      if (!Objects.equals(class1, class2)) {
        equal = false;
        report.append(String.format("[-] Net class mismatch for net '%s': Board 1 = '%s', Board 2 = '%s'\n",
            netName, class1, class2));
      }
    }

    // 8. Board Items (traces, vias, conduction areas, obstacles)
    List<Trace> tr1 = new ArrayList<>(board1.get_traces());
    List<Trace> tr2 = new ArrayList<>(board2.get_traces());
    if (tr1.size() != tr2.size()) {
      equal = false;
      report.append(String.format("[-] Trace count mismatch: Board 1 = %d, Board 2 = %d\n", tr1.size(), tr2.size()));
    }

    List<Via> vias1 = new ArrayList<>(board1.get_vias());
    List<Via> vias2 = new ArrayList<>(board2.get_vias());
    if (vias1.size() != vias2.size()) {
      equal = false;
      report.append(String.format("[-] Via count mismatch: Board 1 = %d, Board 2 = %d\n", vias1.size(), vias2.size()));
    }

    // Compare conduction areas & obstacles count
    int condCount1 = 0, condCount2 = 0;
    int obstCount1 = 0, obstCount2 = 0;
    Iterator<UndoableObjects.UndoableObjectNode> it1 = board1.item_list.start_read_object();
    for (;;) {
      Item item = (Item) board1.item_list.read_object(it1);
      if (item == null) break;
      if (item instanceof ConductionArea) condCount1++;
      else if (item instanceof ObstacleArea && !(item instanceof ConductionArea) && !(item instanceof Pin) && !(item instanceof ComponentOutline)) obstCount1++;
    }
    Iterator<UndoableObjects.UndoableObjectNode> it2 = board2.item_list.start_read_object();
    for (;;) {
      Item item = (Item) board2.item_list.read_object(it2);
      if (item == null) break;
      if (item instanceof ConductionArea) condCount2++;
      else if (item instanceof ObstacleArea && !(item instanceof ConductionArea) && !(item instanceof Pin) && !(item instanceof ComponentOutline)) obstCount2++;
    }

    if (condCount1 != condCount2) {
      equal = false;
      report.append(String.format("[-] Conduction Area count mismatch: Board 1 = %d, Board 2 = %d\n", condCount1, condCount2));
    }
    if (obstCount1 != obstCount2) {
      equal = false;
      report.append(String.format("[-] Keepout/Obstacle Area count mismatch: Board 1 = %d, Board 2 = %d\n", obstCount1, obstCount2));
    }

    // Compare Padstack names (order-independent)
    Set<String> padstackNames1 = collectPadstackNames(board1);
    Set<String> padstackNames2 = collectPadstackNames(board2);
    if (padstackNames1.size() != padstackNames2.size()) {
      equal = false;
      report.append(String.format("[-] Padstack count mismatch: Board 1 = %d, Board 2 = %d\n",
          padstackNames1.size(), padstackNames2.size()));
    } else {
      report.append(String.format("[+] Padstack counts match: %d\n", padstackNames1.size()));
    }
    equal &= reportMissingKeys("Padstack", padstackNames1, padstackNames2, report);

    // Compare Package names (order-independent; front/back side disambiguates same name)
    Set<String> packageKeys1 = collectPackageKeys(board1);
    Set<String> packageKeys2 = collectPackageKeys(board2);
    if (packageKeys1.size() != packageKeys2.size()) {
      equal = false;
      report.append(String.format("[-] Package count mismatch: Board 1 = %d, Board 2 = %d\n",
          packageKeys1.size(), packageKeys2.size()));
    } else {
      report.append(String.format("[+] Package counts match: %d\n", packageKeys1.size()));
    }
    equal &= reportMissingKeys("Package", packageKeys1, packageKeys2, report);

    // 8. Trace lengths match
    double traceLength1 = 0.0;
    for (app.freerouting.board.Item item : board1.get_items()) {
      if (item instanceof app.freerouting.board.Trace trace) {
        traceLength1 += trace.get_length() * scale1;
      }
    }
    double traceLength2 = 0.0;
    for (app.freerouting.board.Item item : board2.get_items()) {
      if (item instanceof app.freerouting.board.Trace trace) {
        traceLength2 += trace.get_length() * scale2;
      }
    }
    if (Math.abs(traceLength1 - traceLength2) > epsilonMm) {
      equal = false;
      report.append(String.format("[-] Trace length sum mismatch: Board 1 = %.4f mm, Board 2 = %.4f mm\n", traceLength1, traceLength2));
    } else {
      report.append("[+] Trace length sums match.\n");
    }

    // Conduction zone shapes and fill status match
    Collection<ConductionArea> conds1 = board1.get_conduction_areas();
    Collection<ConductionArea> conds2 = board2.get_conduction_areas();
    for (ConductionArea area1 : conds1) {
      ConductionArea matchArea2 = null;
      for (ConductionArea area2 : conds2) {
        if (area2.get_layer() == area1.get_layer() &&
            Math.abs(area2.bounding_box().ll.x * scale2 - area1.bounding_box().ll.x * scale1) < epsilonMm &&
            Math.abs(area2.bounding_box().ll.y * scale2 - area1.bounding_box().ll.y * scale1) < epsilonMm) {
          matchArea2 = area2;
          break;
        }
      }
      if (matchArea2 == null) {
        equal = false;
        report.append(String.format("[-] Conduction Area on layer %d at (%d,%d) in Board 1 has no match in Board 2.\n",
            area1.get_layer(), area1.bounding_box().ll.x, area1.bounding_box().ll.y));
      } else {
        if (area1.get_is_filled() != matchArea2.get_is_filled()) {
          equal = false;
          report.append(String.format("[-] Conduction Area fill status mismatch on layer %d: Board 1 = %b, Board 2 = %b\n",
              area1.get_layer(), area1.get_is_filled(), matchArea2.get_is_filled()));
        }
      }
    }

    if (equal) {
      report.append("\n[+] Success: Boards are identical within the coordinate tolerance threshold.\n");
    } else {
      report.append("\n[-] Failure: Differences were detected between the two boards.\n");
    }

    return new ComparisonResult(equal, report.toString());
  }

  private static double getMmFactor(RoutingBoard board) {
    return app.freerouting.board.Unit.scale(1.0, board.communication.unit, app.freerouting.board.Unit.MM)
        / (board.communication.resolution > 0 ? board.communication.resolution : 1);
  }

  private static Map<String, NetClass> buildNetClassMap(RoutingBoard board) {
    Map<String, NetClass> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (int i = 0; i < board.rules.net_classes.count(); i++) {
      NetClass netClass = board.rules.net_classes.get(i);
      if (netClass != null) {
        map.put(netClass.get_name(), netClass);
      }
    }
    return map;
  }

  private static Map<String, Net> buildNetMap(RoutingBoard board) {
    Map<String, Net> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
      Net net = board.rules.nets.get(i);
      if (net != null) {
        map.put(net.name, net);
      }
    }
    return map;
  }

  private static Set<String> collectPadstackNames(RoutingBoard board) {
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (int i = 1; i <= board.library.padstacks.count(); i++) {
      app.freerouting.core.Padstack padstack = board.library.padstacks.get(i);
      if (padstack != null) {
        names.add(padstack.name);
      }
    }
    return names;
  }

  private static Set<String> collectPackageKeys(RoutingBoard board) {
    Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (int i = 1; i <= board.library.packages.count(); i++) {
      app.freerouting.core.Package pkg = board.library.packages.get(i);
      if (pkg != null) {
        keys.add(packageKey(pkg));
      }
    }
    return keys;
  }

  private static String packageKey(app.freerouting.core.Package pkg) {
    return pkg.name + (pkg.is_front ? ":front" : ":back");
  }

  private static boolean reportMissingKeys(String itemLabel, Set<String> keys1, Set<String> keys2, StringBuilder report) {
    boolean matched = true;
    for (String key : keys1) {
      if (!keys2.contains(key)) {
        matched = false;
        report.append(String.format("[-] %s '%s' is present in Board 1 but missing in Board 2\n", itemLabel, key));
      }
    }
    for (String key : keys2) {
      if (!keys1.contains(key)) {
        matched = false;
        report.append(String.format("[-] %s '%s' is present in Board 2 but missing in Board 1\n", itemLabel, key));
      }
    }
    return matched;
  }
}
