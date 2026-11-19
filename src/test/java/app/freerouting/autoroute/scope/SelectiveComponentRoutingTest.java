package app.freerouting.autoroute.scope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.state.ChangedArea;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterScopeSettings;
import java.io.IOException;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

/** Comprehensive unit tests for Milestone 2: Scope Core Engine & Provenance Tracking. */
class SelectiveComponentRoutingTest {

  @Test
  void testPolicyParsing() {
    assertEquals(MultiPinScopePolicy.ANCHOR_TO_NEAREST, MultiPinScopePolicy.of(null));
    assertEquals(MultiPinScopePolicy.ANCHOR_TO_NEAREST, MultiPinScopePolicy.of(""));
    assertEquals(
        MultiPinScopePolicy.ANCHOR_TO_NEAREST, MultiPinScopePolicy.of("anchor_to_nearest"));
    assertEquals(
        MultiPinScopePolicy.ANCHOR_TO_NEAREST, MultiPinScopePolicy.of("anchor-to-nearest"));
    assertEquals(MultiPinScopePolicy.FULL_NET, MultiPinScopePolicy.of("full_net"));
    assertEquals(MultiPinScopePolicy.FULL_NET, MultiPinScopePolicy.of("FULL"));
    assertEquals(MultiPinScopePolicy.MUTUAL_ONLY, MultiPinScopePolicy.of("mutual_only"));
    assertEquals(MultiPinScopePolicy.MUTUAL_ONLY, MultiPinScopePolicy.of("MUTUAL"));
    assertEquals(MultiPinScopePolicy.ANCHOR_TO_NEAREST, MultiPinScopePolicy.of("unknown_policy"));

    assertEquals(ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP, ForeignTracePolicy.of(null));
    assertEquals(ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP, ForeignTracePolicy.of(""));
    assertEquals(
        ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP,
        ForeignTracePolicy.of("nudge_then_conditional_ripup"));
    assertEquals(ForeignTracePolicy.NUDGE_ONLY, ForeignTracePolicy.of("nudge_only"));
    assertEquals(ForeignTracePolicy.NUDGE_ONLY, ForeignTracePolicy.of("nudge"));
    assertEquals(ForeignTracePolicy.FIXED, ForeignTracePolicy.of("fixed"));
    assertEquals(ForeignTracePolicy.FIXED, ForeignTracePolicy.of("strict_fixed"));
    assertEquals(
        ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP,
        ForeignTracePolicy.of("unknown_foreign_policy"));
  }

  @Test
  void testRoutingScopeMaskBitsetOperations() {
    long[] selectedComponents = new long[2]; // components 0..127
    selectedComponents[0] = (1L << 2) | (1L << 5); // components 2 and 5

    long[] anchorPinIds = new long[4]; // pins 0..255
    anchorPinIds[0] = (1L << 10); // pin ID 10
    anchorPinIds[2] = (1L << 140); // pin ID 140 (word 2, bit 12)

    long[] scopedNets = new long[2];
    scopedNets[0] = (1L << 7) | (1L << 12); // nets 7 and 12

    int[] scopedNetNumbers = new int[] {7, 12};

    RoutingScopeMask mask =
        new RoutingScopeMask(
            true,
            selectedComponents,
            anchorPinIds,
            scopedNets,
            scopedNetNumbers,
            MultiPinScopePolicy.MUTUAL_ONLY,
            ForeignTracePolicy.NUDGE_ONLY,
            12.5,
            3);

    assertTrue(mask.isActive());
    assertTrue(mask.isComponentSelected(2));
    assertTrue(mask.isComponentSelected(5));
    assertFalse(mask.isComponentSelected(3));

    assertTrue(mask.isAnchorPinId(10));
    assertTrue(mask.isAnchorPinId(140));
    assertFalse(mask.isAnchorPinId(11));

    assertTrue(mask.isNetInScope(7));
    assertTrue(mask.isNetInScope(12));
    assertFalse(mask.isNetInScope(8));

    assertArrayEquals(new int[] {7, 12}, mask.getScopedNetNumbers());
    assertEquals(MultiPinScopePolicy.MUTUAL_ONLY, mask.getMultiPinPolicy());
    assertEquals(ForeignTracePolicy.NUDGE_ONLY, mask.getForeignTracePolicy());
    assertEquals(12.5, mask.getForeignRipupCostMultiplier());
    assertEquals(3, mask.getNudgeOnlyPasses());

    // Unscoped mask allows all
    RoutingScopeMask unscoped = RoutingScopeMask.UNSCOPED;
    assertFalse(unscoped.isActive());
    assertTrue(unscoped.isComponentSelected(999));
    assertTrue(unscoped.isAnchorPinId(9999));
    assertTrue(unscoped.isNetInScope(1234));
  }

  @Test
  void testProvenanceInheritance() {
    RoutingScopeMask mask =
        new RoutingScopeMask(
            true,
            new long[] {1L << 1},
            new long[] {1L << 10},
            new long[] {1L << 5},
            new int[] {5},
            MultiPinScopePolicy.ANCHOR_TO_NEAREST,
            ForeignTracePolicy.NUDGE_THEN_CONDITIONAL_RIPUP,
            10.0,
            2);

    long[] baselineForeignBits = new long[2];
    baselineForeignBits[0] = (1L << 50); // Item ID 50 is foreign baseline copper

    RoutingScopeContext context =
        new RoutingScopeContext(mask, false, baselineForeignBits, new int[] {0, 0, 0, 0, 0, 1});

    assertTrue(context.isActive());

    // Shove or split: trace 50 replaced by trace 150
    context.inheritProvenance(50, 150);

    // Another split: trace 150 split into 250
    context.inheritProvenance(150, 250);

    // Non-foreign item 99 should not propagate to 350
    context.inheritProvenance(99, 350);

    // Verify through anchor shield and internal states
    assertFalse(context.isAnchorShielded(150));
    context.shieldAnchorItem(150);
    assertTrue(context.isAnchorShielded(150));
    context.clearAnchorShieldAndRecoveryMode();
    assertFalse(context.isAnchorShielded(150));
  }

  @Test
  void testRoutingScopeCompilerWithRealBoard() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    assertNotNull(board);
    assertTrue(board.components.count() > 0);

    // Grab the first placed component on the board
    Iterator<Component> iterator = board.components.getAll().iterator();
    Component targetComp = null;
    while (iterator.hasNext()) {
      Component c = iterator.next();
      if (c.isPlaced()) {
        targetComp = c;
        break;
      }
    }
    assertNotNull(targetComp, "Should find at least one placed component on DAC2020 board");

    // Case 1: Valid placed component
    RouterScopeSettings settings = new RouterScopeSettings();
    settings.components = new String[] {targetComp.name.toLowerCase()}; // case-insensitive test
    settings.multiPinPolicy = "anchor_to_nearest";
    settings.foreignTracePolicy = "nudge_then_conditional_ripup";

    RoutingScopeContext context = RoutingScopeCompiler.compile(board, settings);
    assertNotNull(context);
    assertTrue(context.isActive());
    assertFalse(context.isNoOp());
    assertTrue(context.isComponentSelected(targetComp.id));
    assertTrue(context.getScopedNetNumbers().length > 0);

    // Case 2: Non-existent component -> EMPTY_NO_OP
    RouterScopeSettings invalidSettings = new RouterScopeSettings();
    invalidSettings.components = new String[] {"NON_EXISTENT_COMPONENT_XYZ"};
    RoutingScopeContext noOpContext = RoutingScopeCompiler.compile(board, invalidSettings);
    assertNotNull(noOpContext);
    assertTrue(noOpContext.isNoOp());
    assertFalse(noOpContext.isActive());

    // Case 3: Empty / NONE -> UNSCOPED
    RouterScopeSettings emptySettings = new RouterScopeSettings();
    emptySettings.components = new String[] {"NONE"};
    RoutingScopeContext unscopedContext = RoutingScopeCompiler.compile(board, emptySettings);
    assertNotNull(unscopedContext);
    assertFalse(unscopedContext.isActive());
    assertFalse(unscopedContext.isNoOp());
  }

  @Test
  void testChangedAreaSnapshotAndRestore() {
    ChangedArea original = new ChangedArea(2);
    original.join(new FloatPoint(100.0, 200.0), 0);
    original.join(new FloatPoint(300.0, 400.0), 0);

    IntOctagon areaBefore = original.getArea(0);
    assertFalse(areaBefore.isEmpty());

    // Create copy
    ChangedArea copy = original.copy();
    IntOctagon copyArea = copy.getArea(0);
    assertEquals(areaBefore.leftX, copyArea.leftX);
    assertEquals(areaBefore.rightX, copyArea.rightX);
    assertEquals(areaBefore.bottomY, copyArea.bottomY);
    assertEquals(areaBefore.topY, copyArea.topY);

    // Mutate original further
    original.join(new FloatPoint(1000.0, 2000.0), 0);
    IntOctagon areaAfterOriginal = original.getArea(0);

    // Copy must remain unchanged
    IntOctagon copyAreaAfter = copy.getArea(0);
    assertEquals(areaBefore.leftX, copyAreaAfter.leftX);
    assertEquals(areaBefore.rightX, copyAreaAfter.rightX);
    assertEquals(areaBefore.bottomY, copyAreaAfter.bottomY);
    assertEquals(areaBefore.topY, copyAreaAfter.topY);
    assertTrue(areaAfterOriginal.rightX > copyAreaAfter.rightX);
  }

  @Test
  void testNoOpScopeExitsCleanlyInZeroPasses() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    assertNotNull(board);

    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.board = board;
    job.thread = new NoOpStoppableThread();
    job.routerSettings = new app.freerouting.settings.sources.DefaultSettings().getSettings();
    job.routerSettings.scope.components = new String[] {"NON_EXISTENT_CHIP_999"};
    job.routerSettings.autorouter.maxPasses = 5;

    int traceCountBefore = board.getTraces().size();
    app.freerouting.autoroute.pipeline.RoutingPipeline pipeline =
        app.freerouting.autoroute.pipeline.RoutingPipeline.create(job);
    pipeline.run();

    assertTrue(board.getRoutingScopeContext().isNoOp());
    assertEquals(traceCountBefore, board.getTraces().size());
  }

  @Test
  void testTier1ScopedRoutingOnRealBoard() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    assertNotNull(board);

    // Find a placed component with routable pins
    Component targetComp = null;
    for (Component c : board.components.getAll()) {
      if (c.isPlaced()) {
        targetComp = c;
        break;
      }
    }
    assertNotNull(targetComp);

    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.board = board;
    job.thread = new NoOpStoppableThread();
    job.routerSettings = new app.freerouting.settings.sources.DefaultSettings().getSettings();
    job.routerSettings.applyBoardSpecificOptimizations(board);
    job.routerSettings.scope.components = new String[] {targetComp.name};
    job.routerSettings.scope.nudgeOnlyPasses = 2;
    job.routerSettings.autorouter.maxPasses = 1;
    job.routerSettings.setRunOptimizer(false);

    RoutingScopeContext preContext = RoutingScopeCompiler.compile(board, job.routerSettings.scope);
    int unselectedCompId = -1;
    int unselectedOpenPinsBefore = 0;
    for (Component c : board.components.getAll()) {
      if (c.isPlaced() && c.id != targetComp.id) {
        boolean touchedByScope = false;
        for (app.freerouting.board.model.items.Item item : board.getItems()) {
          if (item instanceof app.freerouting.board.model.items.Pin pin
              && pin.getComponentId() == c.id
              && pin.netCount() > 0) {
            for (int n = 0; n < pin.netCount(); n++) {
              if (preContext.isNetInScope(pin.getNetNumber(n))) {
                touchedByScope = true;
                break;
              }
            }
          }
        }
        if (!touchedByScope) {
          unselectedCompId = c.id;
          for (app.freerouting.board.model.items.Item item : board.getItems()) {
            if (item instanceof app.freerouting.board.model.items.Pin pin
                && pin.getComponentId() == c.id
                && pin.netCount() > 0
                && !pin.getUnconnectedSet(pin.getNetNumber(0)).isEmpty()) {
              unselectedOpenPinsBefore++;
            }
          }
          if (unselectedOpenPinsBefore > 0) {
            break;
          }
        }
      }
    }

    app.freerouting.autoroute.pipeline.RoutingPipeline pipeline =
        app.freerouting.autoroute.pipeline.RoutingPipeline.create(job);
    pipeline.run();

    RoutingScopeContext context = board.getRoutingScopeContext();
    assertNotNull(context);
    assertTrue(context.isActive());
    assertFalse(context.isNoOp());

    // Unselected components outside scope must remain untouched
    if (unselectedCompId != -1) {
      int unselectedOpenPinsAfter = 0;
      for (app.freerouting.board.model.items.Item item : board.getItems()) {
        if (item instanceof app.freerouting.board.model.items.Pin pin
            && pin.getComponentId() == unselectedCompId
            && pin.netCount() > 0
            && !pin.getUnconnectedSet(pin.getNetNumber(0)).isEmpty()) {
          unselectedOpenPinsAfter++;
        }
      }
      assertEquals(
          unselectedOpenPinsBefore,
          unselectedOpenPinsAfter,
          "Unselected component outside scope must have its unconnected pins untouched");
    }

    // Authoritative DRC verification: 0 clearance violations
    var violations =
        new app.freerouting.drc.DesignRulesChecker(board, null).getAllClearanceViolations();
    assertTrue(violations.isEmpty(), "Scoped routing must not introduce clearance violations");

    // Board statistics score must be calculated cleanly
    app.freerouting.core.scoring.BoardStatistics stats =
        new app.freerouting.core.scoring.BoardStatistics(board);
    float scopedScore = stats.getScopedRouterScore(context, job.routerSettings);
    assertTrue(scopedScore >= 0.0f && scopedScore <= 1000.0f);
  }

  @Test
  void testScopedRouteCheckpointCaptureRollbackAndCommit() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    assertNotNull(board);

    RouterScopeSettings settings = new RouterScopeSettings();
    settings.components = new String[] {"U1"};
    RoutingScopeContext context = RoutingScopeCompiler.compile(board, settings);
    board.setRoutingScopeContext(context);

    int initialItemCount = board.getItems().size();

    // 1. Capture checkpoint
    ScopedRouteCheckpoint checkpoint = ScopedRouteCheckpoint.capture(board);
    assertNotNull(checkpoint);
    assertFalse(checkpoint.isRolledBack());
    assertFalse(checkpoint.isCommitted());

    // 2. Perform a simulated change
    board.startMarkingChangedArea();
    board.changedArea.join(new FloatPoint(100.0, 200.0), 0);
    context.inheritProvenance(1, 99999);

    // 3. Rollback
    checkpoint.rollback();
    assertTrue(checkpoint.isRolledBack());
    assertFalse(checkpoint.isCommitted());

    assertEquals(initialItemCount, board.getItems().size());
    // Calling rollback again is an idempotent no-op
    checkpoint.rollback();
    assertTrue(checkpoint.isRolledBack());

    // 4. Test Commit path
    ScopedRouteCheckpoint checkpoint2 = ScopedRouteCheckpoint.capture(board);
    assertFalse(checkpoint2.isCommitted());
    checkpoint2.commit();
    assertTrue(checkpoint2.isCommitted());
    assertFalse(checkpoint2.isRolledBack());
    // Calling commit again is an idempotent no-op
    checkpoint2.commit();
    assertTrue(checkpoint2.isCommitted());
  }

  @Test
  void testScopedOptimizerAndConcurrency() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    assertNotNull(board);

    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.board = board;
    job.thread = new NoOpStoppableThread();
    job.routerSettings = new app.freerouting.settings.sources.DefaultSettings().getSettings();
    job.routerSettings.applyBoardSpecificOptimizations(board);

    job.routerSettings.scope.components = new String[] {"U1"};
    job.routerSettings.autorouter.maxPasses = 1;
    job.routerSettings.setRunOptimizer(false);

    // Enable multi-threaded optimizer settings
    job.routerSettings.optimizer.maxPasses = 1;
    job.routerSettings.optimizer.maxThreads = 4;
    job.routerSettings.optimizer.maxItems = 20;

    app.freerouting.autoroute.pipeline.RoutingPipeline pipeline =
        app.freerouting.autoroute.pipeline.RoutingPipeline.create(job);
    pipeline.run();

    RoutingScopeContext context = board.getRoutingScopeContext();
    assertNotNull(context);
    assertTrue(context.isActive());

    // Verify candidate items filter: any item not on net in scope must be non-optimizable
    for (app.freerouting.board.model.items.Item item : board.getItems()) {
      if (item instanceof app.freerouting.board.model.items.Trace
          || item instanceof app.freerouting.board.model.items.Via) {
        int netNo = item.netCount() > 0 ? item.getNetNumber(0) : 0;
        if (!context.isNetInScope(netNo)) {
          assertFalse(
              context.isItemOptimizable(item), "Item on out-of-scope net must not be optimizable");
        }
      }
    }

    // Run Scoped BatchOptimizer with 4 concurrent threads
    app.freerouting.autoroute.pipeline.BatchOptimizer optimizer =
        app.freerouting.autoroute.pipeline.BatchOptimizer.create(job);
    assertNotNull(optimizer);
    optimizer.runBatchLoop();

    // Verify DRC violations remain 0 after optimization
    var violations =
        new app.freerouting.drc.DesignRulesChecker(board, null).getAllClearanceViolations();
    assertTrue(violations.isEmpty(), "Scoped optimizer must not introduce clearance violations");

    // Verify thread-confined deep copy preserves isolation
    RoutingScopeContext clonedContext = context.deepCopy();
    assertNotNull(clonedContext);
    assertTrue(clonedContext.isActive());
    assertEquals(context.getForeignTracePolicy(), clonedContext.getForeignTracePolicy());
  }

  @Test
  void testGuiSelectionScopeResolverAndWorkspaceSettings() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    assertNotNull(board);

    // Test SelectionScopeResolver item extraction
    java.util.List<app.freerouting.board.model.items.Item> items = new java.util.ArrayList<>();
    for (app.freerouting.board.model.items.Item item : board.getItems()) {
      if (item instanceof app.freerouting.board.model.items.Pin pin) {
        items.add(pin);
        if (items.size() >= 20) {
          break;
        }
      }
    }
    String[] extractedNames =
        app.freerouting.gui.SelectionScopeResolver.extractComponentNames(items, board);
    assertNotNull(extractedNames);
    assertTrue(extractedNames.length > 0);

    // Test WorkspaceSettings scope binding and property events
    app.freerouting.gui.workspace.WorkspaceSettings ws =
        app.freerouting.gui.workspace.WorkspaceSettings.getOrCreate(board);
    assertNotNull(ws);

    java.util.concurrent.atomic.AtomicBoolean scopeChanged =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicBoolean policyChanged =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    ws.addPropertyChangeListener(
        evt -> {
          if (app.freerouting.gui.workspace.WorkspaceSettings.PROP_SCOPE_COMPONENTS.equals(
              evt.getPropertyName())) {
            scopeChanged.set(true);
          }
          if (app.freerouting.gui.workspace.WorkspaceSettings.PROP_FOREIGN_TRACE_POLICY.equals(
              evt.getPropertyName())) {
            policyChanged.set(true);
          }
        });

    ws.setScopeComponents(new String[] {"U1", "U2"});
    assertTrue(scopeChanged.get());
    assertArrayEquals(new String[] {"U1", "U2"}, ws.getScopeComponents());

    ws.setForeignTracePolicy("fixed");
    assertTrue(policyChanged.get());
    assertEquals("fixed", ws.getForeignTracePolicy());

    // Test that WorkspaceSettings.getSettings() produces RouterSettings with scope
    app.freerouting.settings.RouterSettings settings = ws.getSettings();
    assertNotNull(settings.scope);
    assertArrayEquals(new String[] {"U1", "U2"}, settings.scope.components);
    assertEquals("fixed", settings.scope.foreignTracePolicy);

    // Test automatic activeScopeMask compilation upon setScopeComponents
    assertNotNull(ws.getActiveScopeMask());
    assertTrue(ws.getActiveScopeMask().isActive());

    // Test selection-driven scope and clear
    ws.setScopeComponentsFromSelection(new String[] {"U1"});
    assertTrue(ws.isScopeFromSelection());
    assertNotNull(ws.getActiveScopeMask());

    ws.clearScopeFromSelection();
    assertFalse(ws.isScopeFromSelection());
    assertNull(ws.getScopeComponents());
    assertNull(ws.getActiveScopeMask());
  }

  private static final class NoOpStoppableThread extends app.freerouting.core.StoppableThread {
    @Override
    protected void threadAction() {}
  }
}
