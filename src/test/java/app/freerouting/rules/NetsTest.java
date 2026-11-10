package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.LogicalPart;
import app.freerouting.core.library.LogicalParts;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests case sensitivity, null safety, and Comparable contracts for Nets, Net, Package, Padstack,
 * and LogicalParts.
 */
class NetsTest {

  private RoutingBoard board;
  private Nets nets;

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    Communication communication = new Communication();

    RoutingBoard result =
        new RoutingBoard(
            new IntBox(0, 0, 2000000, 2000000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
            0,
            boardRules,
            communication);
    result.library.padstacks = new Padstacks(layerStructure);
    result.library.packages = new Packages(result.library.padstacks);
    return result;
  }

  @BeforeEach
  void setUp() {
    board = createTestBoard();
    nets = board.rules.nets;
  }

  @Test
  void netsDifferingOnlyInCaseStayDistinct() {
    Net netUpper = nets.add("F", 1, false);
    Net netLower = nets.add("f", 1, false);

    assertNotNull(netUpper);
    assertNotNull(netLower);
    assertNotEquals(netUpper.netNumber, netLower.netNumber);

    assertEquals(netUpper, nets.get("F", 1));
    assertEquals(netLower, nets.get("f", 1));

    Collection<Net> uppers = nets.get("F");
    assertEquals(1, uppers.size());
    assertTrue(uppers.contains(netUpper));
    assertFalse(uppers.contains(netLower));

    Collection<Net> lowers = nets.get("f");
    assertEquals(1, lowers.size());
    assertTrue(lowers.contains(netLower));
    assertFalse(lowers.contains(netUpper));
  }

  @Test
  void defaultPadNetsDifferingOnlyInCaseStayDistinct() {
    Net padNetUpper = nets.add("Net-(SW1-PadA)", 1, false);
    Net padNetLower = nets.add("Net-(SW1-Pada)", 1, false);

    assertNotNull(padNetUpper);
    assertNotNull(padNetLower);
    assertNotEquals(padNetUpper.netNumber, padNetLower.netNumber);

    assertEquals(padNetUpper, nets.get("Net-(SW1-PadA)", 1));
    assertEquals(padNetLower, nets.get("Net-(SW1-Pada)", 1));
  }

  @Test
  void nullAndNonExistentNetsHandledSafely() {
    assertNull(nets.get(null, 1));
    assertTrue(nets.get(null).isEmpty());

    assertNull(nets.get("NON_EXISTENT", 1));
    assertTrue(nets.get("NON_EXISTENT").isEmpty());
  }

  @Test
  void netCompareToContractGuaranteesTotalOrdering() {
    Net netA = nets.add("A", 1, false);
    Net neta = nets.add("a", 1, false);
    Net netA_sub2 = nets.add("A", 2, false);

    assertEquals(0, netA.compareTo(netA));
    assertNotEquals(0, netA.compareTo(neta));
    assertNotEquals(0, netA.compareTo(netA_sub2));

    // Must not be dropped when inserted into a TreeSet
    TreeSet<Net> set = new TreeSet<>();
    set.add(netA);
    set.add(neta);
    set.add(netA_sub2);
    assertEquals(3, set.size());

    assertThrows(NullPointerException.class, () -> netA.compareTo(null));
  }

  @Test
  void packageCompareToContractGuaranteesTotalOrdering() {
    Packages packages = board.library.packages;
    Package pkgA =
        packages.add("DIP8", new Package.Pin[0], new Shape[0], null, null, null, null, null, true);
    Package pkga =
        packages.add("dip8", new Package.Pin[0], new Shape[0], null, null, null, null, null, true);

    assertEquals(0, pkgA.compareTo(pkgA));
    assertNotEquals(0, pkgA.compareTo(pkga));

    TreeSet<Package> set = new TreeSet<>();
    set.add(pkgA);
    set.add(pkga);
    assertEquals(2, set.size());

    assertThrows(NullPointerException.class, () -> pkgA.compareTo(null));
  }

  @Test
  void padstackCompareToContractGuaranteesTotalOrdering() {
    Padstacks padstacks = board.library.padstacks;
    Padstack padP = padstacks.add("RoundPad", new ConvexShape[0], true, false);
    Padstack padp = padstacks.add("roundpad", new ConvexShape[0], true, false);

    assertEquals(0, padP.compareTo(padP));
    assertNotEquals(0, padP.compareTo(padp));

    TreeSet<Padstack> set = new TreeSet<>();
    set.add(padP);
    set.add(padp);
    assertEquals(2, set.size());

    assertThrows(NullPointerException.class, () -> padP.compareTo(null));
  }

  @Test
  void logicalPartsExactMatchFirstPrecedence() {
    LogicalParts parts = board.library.logicalParts;
    LogicalPart partUpper = parts.add("PART", new LogicalPart.PartPin[0]);
    LogicalPart partLower = parts.add("part", new LogicalPart.PartPin[0]);

    assertEquals(partUpper, parts.get("PART"));
    assertEquals(partLower, parts.get("part"));
    assertNull(parts.get(null));

    // Fallback when exact match doesn't exist
    assertEquals(partUpper, parts.get("Part"));
  }
}
