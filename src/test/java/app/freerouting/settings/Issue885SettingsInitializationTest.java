package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.util.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for Issue #885: 1. CLI -inc parsing into router.autorouter.ignore_net_classes.
 * 2. ReflectionUtil allows copying explicit empty arrays. 3. Board-specific optimization preserves
 * explicit per-layer trace costs (e.g. 0.25 / 0.50). 4. Board-specific optimization applies
 * ignoreNetClasses to board rules net classes.
 */
class Issue885SettingsInitializationTest {

  private RoutingBoard board;
  private RouterSettings settings;

  @BeforeEach
  void setUp() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    boardRules.appendNetClass("POWER");
    board =
        new RoutingBoard(
            new IntBox(0, 0, 2_000_000, 1_000_000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 2_000_000, 1_000_000)},
            0,
            boardRules,
            new Communication());

    settings = new RouterSettings();
    settings.setLayerCount(2);
  }

  @Test
  void cliSettingsParsesIncFlagIntoIgnoreNetClasses() {
    CliSettings cli = new CliSettings(new String[] {"-inc", "POWER,GND"});
    RouterSettings parsed = cli.getSettings();

    assertNotNull(parsed.autorouter);
    assertArrayEquals(new String[] {"POWER", "GND"}, parsed.autorouter.ignoreNetClasses);
  }

  @Test
  void reflectionUtilCopiesExplicitEmptyArray() {
    AutorouterSettings target = new AutorouterSettings();
    target.ignoreNetClasses = new String[] {"OLD_CLASS"};

    AutorouterSettings source = new AutorouterSettings();
    source.ignoreNetClasses = new String[0];

    int changed = ReflectionUtil.copyFields(source, target);

    assertTrue(changed > 0);
    assertNotNull(target.ignoreNetClasses);
    assertEquals(0, target.ignoreNetClasses.length);
  }

  @Test
  void applyBoardSpecificOptimizationsPreservesExplicitLayerTraceCosts() {
    settings.layers[0].preferredDirectionTraceCost = 0.25;
    settings.layers[0].undesiredDirectionTraceCost = 0.50;

    settings.applyBoardSpecificOptimizations(board);

    assertEquals(0.25, settings.getPreferredDirectionTraceCosts(0), 1e-6);
    assertEquals(0.50, settings.getAgainstPreferredDirectionTraceCosts(0), 1e-6);
    assertEquals(0.25, settings.layers[0].preferredDirectionTraceCost, 1e-6);
    assertEquals(0.50, settings.layers[0].undesiredDirectionTraceCost, 1e-6);
  }

  @Test
  void applyBoardSpecificOptimizationsAppliesNetClassExclusions() {
    NetClass powerClass = board.rules.netClasses.get("POWER");
    assertNotNull(powerClass);
    assertEquals(false, powerClass.isIgnoredByAutorouter);

    settings.autorouter.ignoreNetClasses = new String[] {"POWER"};
    settings.applyBoardSpecificOptimizations(board);

    assertTrue(powerClass.isIgnoredByAutorouter);
  }
}
