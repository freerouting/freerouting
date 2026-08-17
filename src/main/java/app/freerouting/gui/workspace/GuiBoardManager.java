package app.freerouting.gui.workspace;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdGenerator;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.gui.BoardPanel;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.io.BoardReadResult;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.util.TextManager;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JPopupMenu;

/** GUI workspace member. */
public class GuiBoardManager extends HeadlessBoardManager implements WorkspaceContract {

  /** GUI workspace member. */
  public final ScreenMessages screenMessages;

  /** GUI workspace member. */
  public final SettingsMerger settingsMerger;

  /** GUI workspace member. */
  private final BoardPanel panel;

  /** GUI workspace member. */
  private final TextManager tm;

  /** GUI workspace member. */
  private final GuiBoardEventBridge eventBridge;

  /** GUI workspace member. */
  private final Locale locale;

  /** GUI workspace member. */
  private final WorkspacePort sessionPort;

  /** GUI workspace member. */
  public GraphicsContext graphicsContext;

  /** GUI workspace member. */
  public CoordinateTransform coordinateTransform;

  /** GUI workspace member. */
  public ClearanceViolations clearanceViolations;

  /** GUI workspace member. */
  boolean paintImmediately;

  /** GUI workspace member. */
  private EditorStateController editorStateController;

  /** GUI workspace member. */
  private WorkspaceSettings workspaceSettings;

  /** GUI workspace member. */
  private app.freerouting.gui.BoardFrame boardFrame;

  /** GUI workspace member. */
  private final GuiBoardSessionState sessionState;

  /** GUI workspace member. */
  private final GuiBoardPersistence persistence;

  /** GUI workspace member. */
  private final GuiBoardInteractionController interactionController;

  /** GUI workspace member. */
  private final GuiBoardPresentationController presentationController;

  /** GUI workspace member. */
  private final GuiBoardAnalysisController analysisController;

  /** GUI workspace member. */
  private final GuiBoardRoutingSettings routingSettings;

  /** GUI workspace member. */
  private final GuiBoardLayerController layerController;

  /** GUI workspace member. */
  private final GuiBoardHistoryController historyController;

  /** GUI workspace member. */
  private final GuiBoardSessionModeController sessionMode;

  /** GUI workspace member. */
  private final GuiBoardLegacyEditActions legacyEditActions;

  /** GUI workspace member. */
  private final GuiBoardRoutingActions routingActions;

  /** GUI workspace member. */
  private InteractiveActionThread interactiveActionThread;

  /** Creates a GUI board manager. */
  public GuiBoardManager(
      BoardPanel panel,
      GlobalSettings globalSettings,
      RoutingJob routingJob,
      SettingsMerger settingsMerger) {
    this(panel, globalSettings, routingJob, settingsMerger, null);
  }

  /** Creates a manager with an explicit session port. The overload is used by */
  public GuiBoardManager(
      BoardPanel panel,
      GlobalSettings globalSettings,
      RoutingJob routingJob,
      SettingsMerger settingsMerger,
      WorkspacePortAdapter sessionPort) {
    super(routingJob);
    this.settingsMerger = settingsMerger;
    this.sessionState = new GuiBoardSessionState(globalSettings, routingJob);
    this.persistence = new GuiBoardPersistence(this);
    this.interactionController = new GuiBoardInteractionController(this);
    this.presentationController = new GuiBoardPresentationController(this);
    this.routingActions = new GuiBoardRoutingActions(this);
    this.locale = globalSettings.currentLocale;
    this.panel = panel;
    this.screenMessages = panel.screenMessages;
    this.tm = new TextManager(this.getClass(), globalSettings.currentLocale);
    this.analysisController = new GuiBoardAnalysisController(this, this.tm);
    this.routingSettings = new GuiBoardRoutingSettings(this);
    this.layerController = new GuiBoardLayerController(this);
    this.historyController = new GuiBoardHistoryController(this, this.tm);
    this.sessionMode = new GuiBoardSessionModeController(this);
    this.legacyEditActions = new GuiBoardLegacyEditActions();
    this.sessionPort =
        sessionPort != null
            ? sessionPort
            : new WorkspacePortAdapter(() -> this, () -> this.boardFrame, EdtExecutor.swing());

    this.eventBridge = new GuiBoardEventBridge(this);
  }

  /** GUI workspace member. */
  public void setEditorStateController(EditorStateController controller) {
    this.editorStateController = controller;
  }

  /** GUI workspace member. */
  public EditorStateHandle getEditorState() {
    return editorStateController == null ? null : editorStateController.currentState();
  }

  /** GUI workspace member. */
  public void setEditorState(EditorStateHandle state) {
    if (editorStateController != null) {
      editorStateController.setState(state);
    }
  }

  /** GUI workspace member. */
  public void setBoardFrame(app.freerouting.gui.BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
  }

  /** GUI workspace member. */
  EditorStateController getInteractionStateController() {
    return editorStateController;
  }

  /** GUI workspace member. */
  CoordinateTransform getCoordinateTransform() {
    return coordinateTransform;
  }

  /** GUI workspace member. */
  GraphicsContext getGraphicsContext() {
    return graphicsContext;
  }

  /** GUI workspace member. */
  void setInteractionMousePosition(FloatPoint position) {
    interactionController.setCurrentMousePosition(position);
  }

  /** GUI workspace member. */
  @Override
  public RoutingJob getCurrentRoutingJob() {
    return this.routingJob;
  }

  /** GUI workspace member. */
  public boolean isBoardReadOnly() {
    return sessionMode.isBoardReadOnly();
  }

  /** GUI workspace member. */
  public void setBoardReadOnly(boolean value) {
    sessionMode.setBoardReadOnly(value);
  }

  /** GUI workspace member. */
  public Locale getLocale() {
    return this.locale;
  }

  /** GUI workspace member. */
  public int getLayerCount() {
    if (board == null) {
      return 0;
    }
    return board.getLayerCount();
  }

  /** GUI workspace member. */
  public FloatPoint getCurrentMousePosition() {
    return interactionController.getCurrentMousePosition();
  }

  /** GUI workspace member. */
  public void setIgnoreConduction(boolean value) {
    routingSettings.setIgnoreConduction(value);
  }

  /** GUI workspace member. */
  public void setPinEdgeToTurnDist(double value) {
    routingSettings.setPinEdgeToTurnDist(value);
  }

  /** GUI workspace member. */
  public void setLayerVisibility(int layer, double value) {
    layerController.setLayerVisibility(layer, value);
  }

  /** GUI workspace member. */
  public int getTraceHalfwidth(int netNumber, int layer) {
    return routingSettings.getTraceHalfwidth(netNumber, layer);
  }

  /** GUI workspace member. */
  public boolean isActiveRoutingLayer(int netNumber, int layer) {
    return routingSettings.isActiveRoutingLayer(netNumber, layer);
  }

  /** GUI workspace member. */
  public int getTraceClearanceClass(int netNumber) {
    return routingSettings.getTraceClearanceClass(netNumber);
  }

  /** GUI workspace member. */
  public ViaRule getViaRule(int netNumber) {
    return routingSettings.getViaRule(netNumber);
  }

  /** GUI workspace member. */
  public void setDefaultTraceHalfwidth(int layer, int value) {
    routingSettings.setDefaultTraceHalfwidth(layer, value);
  }

  /** GUI workspace member. */
  public void setClearanceCompensation(boolean value) {
    routingSettings.setClearanceCompensation(value);
  }

  /** GUI workspace member. */
  public void setCurrentSnapAngle(AngleRestriction snapAngle) {
    routingSettings.setCurrentSnapAngle(snapAngle);
  }

  /** GUI workspace member. */
  public void setCurrentLayer(int layer) {
    layerController.setCurrentLayer(layer);
  }

  /** GUI workspace member. */
  public void setLayer(int layerIndex) {
    layerController.setLayer(layerIndex);
  }

  /** GUI workspace member. */
  public void displayLayerMessage() {
    layerController.displayLayerMessage();
  }

  /** GUI workspace member. */
  public void setManualTraceHalfWidth(int layerIndex, int value) {
    routingSettings.setManualTraceHalfWidth(layerIndex, value);
  }

  /** GUI workspace member. */
  public void setSelectable(ItemSelectionFilter.SelectableChoices itemType, boolean value) {
    routingSettings.setSelectable(itemType, value);
  }

  /** GUI workspace member. */
  public void toggleRatsnest() {
    analysisController.toggleRatsnest();
  }

  /** GUI workspace member. */
  public void toggleClearanceViolations() {
    analysisController.toggleClearanceViolations();
  }

  /** GUI workspace member. */
  public void createRatsnest() {
    analysisController.createRatsnest();
  }

  /** GUI workspace member. */
  public void attachPreparedRatsNest(RatsNest preparedRatsNest) {
    analysisController.attachPreparedRatsNest(preparedRatsNest);
  }

  /** GUI workspace member. */
  public void createRatsnestIfAbsent() {
    analysisController.createRatsnestIfAbsent();
  }

  /** GUI workspace member. */
  public void updateRatsnest(int netNumber) {
    analysisController.updateRatsnest(netNumber);
  }

  /** GUI workspace member. */
  public void updateRatsnest(int netNumber, Collection<Item> itemList) {
    analysisController.updateRatsnest(netNumber, itemList);
  }

  /** GUI workspace member. */
  public void updateRatsnest() {
    analysisController.updateRatsnest();
  }

  /** GUI workspace member. */
  public void hideRatsnest() {
    analysisController.hideRatsnest();
  }

  /** GUI workspace member. */
  public void showRatsnest() {
    analysisController.showRatsnest();
  }

  /** GUI workspace member. */
  public void removeRatsnest() {
    analysisController.removeRatsnest();
  }

  /** GUI workspace member. */
  public RatsNest getRatsnest() {
    return analysisController.getRatsnest();
  }

  /** GUI workspace member. */
  public void recalculateLengthViolations() {
    analysisController.recalculateLengthViolations();
  }

  /** GUI workspace member. */
  public void setIncompletesFilter(int netNumber, boolean value) {
    analysisController.setIncompletesFilter(netNumber, value);
  }

  /** GUI workspace member. */
  @Override
  public void createBoard(
      IntBox boundingBox,
      LayerStructure layerStructure,
      PolylineShape[] outlineShapes,
      String outlineClearanceClassName,
      BoardRules rules,
      Communication boardCommunication) {
    super.createBoard(
        boundingBox,
        layerStructure,
        outlineShapes,
        outlineClearanceClassName,
        rules,
        boardCommunication);

    // Reset and rebind the GUI-session singleton for the newly created board.
    this.workspaceSettings = WorkspaceSettings.reset(this.board, this.routingJob.routerSettings);
    this.initializeManualTraceHalfWidths();

    // create the interactive/GUI settings with default values
    double unitFactor = boardCommunication.coordinateTransform.boardToDsn(1);
    this.coordinateTransform =
        new CoordinateTransform(1, boardCommunication.unit, unitFactor, boardCommunication.unit);

    // create a graphics context for the board
    Dimension panelSize = panel.getPreferredSize();
    graphicsContext = new GraphicsContext(boundingBox, panelSize, layerStructure, this.locale);
  }

  /** GUI workspace member. */
  @Override
  public WorkspaceSettings getWorkspaceSettings() {
    return workspaceSettings;
  }

  /** GUI workspace member. */
  @Override
  public void initializeManualTraceHalfWidths() {
    if (workspaceSettings == null || this.board == null) {
      return;
    }
    for (int i = 0; i < workspaceSettings.getLayerCount(); i++) {
      workspaceSettings.manualTraceHalfWidthArr[i] =
          this.board.rules.getDefaultNetClass().getTraceHalfWidth(i);
    }
  }

  /** Re-subscribes all permanent GUI subwindows as needed. */
  public void refreshGuiFromSettings() {
    presentationController.refreshGuiFromSettings();
  }

  /** GUI workspace member. */
  public void changeUserUnit(Unit unit) {
    layerController.changeUserUnit(unit);
  }

  /** GUI workspace member. */
  public void repaint() {
    presentationController.repaint();
  }

  /** GUI workspace member. */
  public void repaint(Rectangle rectangle) {
    presentationController.repaint(rectangle);
  }

  /** GUI workspace member. */
  boolean isInInteractiveDrag() {
    return editorStateController != null && editorStateController.isInteractiveDrag();
  }

  app.freerouting.gui.BoardFrame getBoardFrame() {
    return boardFrame;
  }

  boolean isPaintImmediately() {
    return paintImmediately;
  }

  RoutingBoard getPresentationBoard() {
    return board;
  }

  GraphicsContext getPresentationGraphicsContext() {
    return graphicsContext;
  }

  RatsNest getPresentationRatsNest() {
    return analysisController.getExistingRatsnest();
  }

  ClearanceViolations getPresentationClearanceViolations() {
    return clearanceViolations;
  }

  ClearanceViolations getAnalysisClearanceViolations() {
    return clearanceViolations;
  }

  void setAnalysisClearanceViolations(ClearanceViolations value) {
    clearanceViolations = value;
  }

  EditorStateController getPresentationEditorStateController() {
    return editorStateController;
  }

  InteractiveActionThread getPresentationInteractiveActionThread() {
    return interactiveActionThread;
  }

  Point[] getImpactedPoints() {
    return eventBridge.getImpactedPoints();
  }

  /** GUI workspace member. */
  public BoardPanel getPanel() {
    return this.panel;
  }

  /** GUI workspace member. */
  public JPopupMenu getCurrentPopupMenu() {
    return editorStateController == null ? null : editorStateController.popupMenu();
  }

  /** GUI workspace member. */
  public void draw(Graphics graphics) {
    presentationController.draw(graphics);
  }

  /** GUI workspace member. */
  public void generateSnapshot() {
    historyController.generateSnapshot();
  }

  /** GUI workspace member. */
  public void undo() {
    historyController.undo();
  }

  /** GUI workspace member. */
  public void redo() {
    historyController.redo();
  }

  /** GUI workspace member. */
  public void leftButtonClicked(Point2D point) {
    interactionController.leftButtonClicked(point);
  }

  /** GUI workspace member. */
  public void mouseMoved(Point2D point) {
    interactionController.mouseMoved(point);
  }

  /** GUI workspace member. */
  public void mousePressed(Point2D point) {
    interactionController.mousePressed(point);
  }

  /** GUI workspace member. */
  public void mouseDragged(Point2D point) {
    interactionController.mouseDragged(point);
  }

  /** GUI workspace member. */
  public void buttonReleased() {
    interactionController.buttonReleased();
  }

  /** GUI workspace member. */
  public void mouseWheelMoved(Point2D point, int rotation) {
    interactionController.mouseWheelMoved(point, rotation);
  }

  /** GUI workspace member. */
  public void keyTypedAction(char keyChar) {
    interactionController.keyTypedAction(keyChar);
  }

  /** GUI workspace member. */
  public void returnFromState() {
    interactionController.returnFromState();
  }

  /** GUI workspace member. */
  public void cancelState() {
    interactionController.cancelState();
  }

  /** GUI workspace member. */
  public boolean changeLayerAction(int newLayer) {
    return interactionController.changeLayerAction(newLayer);
  }

  /** GUI workspace member. */
  public void setInspectMenuState() {
    interactionController.setInspectMenuState();
  }

  /** GUI workspace member. */
  public void setRouteMenuState() {
    interactionController.setRouteMenuState();
  }

  /** GUI workspace member. */
  public void setDragMenuState() {
    interactionController.setDragMenuState();
  }

  /** GUI workspace member. */
  public boolean isBoardChanged() {
    return calculateCrc32() != originalBoardChecksum;
  }

  /** GUI workspace member. */
  public boolean loadFromBinary(ObjectInputStream design) {
    return persistence.loadFromBinary(design);
  }

  /** GUI workspace member. */
  public boolean saveAsSpecctraDesignDsn(
      OutputStream outputStream, String designName, boolean compatMode) {
    return persistence.saveAsSpecctraDesignDsn(outputStream, designName, compatMode);
  }

  /** GUI workspace member. */
  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName) {
    return persistence.saveAsSpecctraSessionSes(outputStream, designName);
  }

  /** GUI workspace member. */
  public boolean saveSpecctraSessionSesAsEagleScriptScr(
      InputStream inputStream, OutputStream outputStream) {
    return persistence.saveSpecctraSessionSesAsEagleScriptScr(inputStream, outputStream);
  }

  /** GUI workspace member. */
  @Override
  public BoardReadResult applyParsedBoardResult(
      BoardReadResult dsnResult, String inputFilename, String analyticsFormat) {
    BoardReadResult result =
        super.applyParsedBoardResult(dsnResult, inputFilename, analyticsFormat);
    setupGuiAfterBoardLoad(result);
    return result;
  }

  /** GUI workspace member. */
  @Override
  public BoardReadResult loadFromSpecctraDsn(
      InputStream inputStream, BoardObservers boardObservers, IdGenerator idGenerator) {
    var result = super.loadFromSpecctraDsn(inputStream, boardObservers, idGenerator);
    scheduleGuiRefreshAfterLoad(result);
    return result;
  }

  @Override
  public BoardReadResult loadFromKiCadJson(
      InputStream inputStream, BoardObservers boardObservers, IdGenerator idGenerator) {
    var result = super.loadFromKiCadJson(inputStream, boardObservers, idGenerator);
    scheduleGuiRefreshAfterLoad(result);
    return result;
  }

  private void setupGuiAfterBoardLoad(BoardReadResult result) {
    if (!(result instanceof BoardReadResult.Success
            || result instanceof BoardReadResult.OutlineMissing)
        || this.board == null) {
      return;
    }

    this.workspaceSettings = WorkspaceSettings.reset(this.board, this.routingJob.routerSettings);
    this.initializeManualTraceHalfWidths();
    this.settingsMerger.addOrReplaceSources(this.workspaceSettings);

    double unitFactor = this.board.communication.coordinateTransform.boardToDsn(1);
    this.coordinateTransform =
        new CoordinateTransform(
            1, this.board.communication.unit, unitFactor, this.board.communication.unit);
    Dimension panelSize = panel != null ? panel.getPreferredSize() : new Dimension(800, 600);
    this.graphicsContext =
        new GraphicsContext(
            this.board.boundingBox, panelSize, this.board.layerStructure, this.locale);
    this.setLayer(0);
  }

  private void scheduleGuiRefreshAfterLoad(BoardReadResult result) {
    if ((result instanceof BoardReadResult.Success
            || result instanceof BoardReadResult.OutlineMissing)
        && this.board != null) {
      javax.swing.SwingUtilities.invokeLater(this::refreshGuiFromSettings);
    }
  }

  /** GUI workspace member. */
  public boolean saveAsBinary(ObjectOutputStream objectStream) {
    return persistence.saveAsBinary(objectStream);
  }

  // Package-private persistence seam; the public manager façade remains unchanged.
  RoutingJob getPersistenceRoutingJob() {
    return routingJob;
  }

  RoutingBoard getPersistenceBoard() {
    return board;
  }

  void setPersistenceBoard(RoutingBoard value) {
    board = value;
  }

  WorkspaceSettings getPersistenceWorkspaceSettings() {
    return workspaceSettings;
  }

  void setPersistenceWorkspaceSettings(WorkspaceSettings value) {
    workspaceSettings = value;
  }

  CoordinateTransform getPersistenceCoordinateTransform() {
    return coordinateTransform;
  }

  void setPersistenceCoordinateTransform(CoordinateTransform value) {
    coordinateTransform = value;
  }

  GraphicsContext getPersistenceGraphicsContext() {
    return graphicsContext;
  }

  void setPersistenceGraphicsContext(GraphicsContext value) {
    graphicsContext = value;
  }

  void setOriginalBoardChecksum(long value) {
    originalBoardChecksum = value;
  }

  boolean isBoardReadOnlyForPersistence() {
    return isBoardReadOnly();
  }

  String getPersistenceText(String key) {
    return tm.getText(key);
  }

  boolean saveHeadlessSpecctraSessionSes(OutputStream outputStream, String designName) {
    return super.saveAsSpecctraSessionSes(outputStream, designName);
  }

  /** GUI workspace member. */
  public void closeFiles() {}

  /** GUI workspace member. */
  public void startRoute(Point2D point) {
    interactionController.startRoute(point);
  }

  /** GUI workspace member. */
  public void selectItems(Point2D point) {
    interactionController.selectItems(point);
  }

  /** GUI workspace member. */
  public void selectItems(Set<Item> items) {
    interactionController.selectItems(items);
  }

  /** GUI workspace member. */
  public void selectItemsInRegion() {
    interactionController.selectItemsInRegion();
  }

  /** GUI workspace member. */
  public void swapPins(Point2D location) {
    interactionController.swapPins(location);
  }

  /** GUI workspace member. */
  public void zoomSelection() {
    interactionController.zoomSelection();
  }

  /** GUI workspace member. */
  public void toggleSelectAction(Point2D point) {
    interactionController.toggleSelectAction(point);
  }

  /** GUI workspace member. */
  public void fixSelectedItems() {
    legacyEditActions.fixSelectedItems();
  }

  /** GUI workspace member. */
  public void unfixSelectedItems() {
    legacyEditActions.unfixSelectedItems();
  }

  /** GUI workspace member. */
  public void displaySelectedItemInfo() {
    interactionController.displaySelectedItemInfo();
  }

  /** GUI workspace member. */
  public void assignSelectedToNewNet() {
    legacyEditActions.assignSelectedToNewNet();
  }

  /** GUI workspace member. */
  public void assignSelectedToNewGroup() {
    legacyEditActions.assignSelectedToNewGroup();
  }

  /** GUI workspace member. */
  public void deleteSelectedItems() {
    legacyEditActions.deleteSelectedItems();
  }

  /** GUI workspace member. */
  public void cutoutSelectedItems() {
    legacyEditActions.cutoutSelectedItems();
  }

  /** GUI workspace member. */
  public void assignClearanceClasssToSelectedItems(int clearanceClassIndex) {
    legacyEditActions.assignClearanceClasssToSelectedItems(clearanceClassIndex);
  }

  /** GUI workspace member. */
  public void moveSelectedItems(Point2D fromLocation) {
    legacyEditActions.moveSelectedItems(fromLocation);
  }

  /** GUI workspace member. */
  public void copySelectedItems(Point2D fromLocation) {
    legacyEditActions.copySelectedItems(fromLocation);
  }

  /** GUI workspace member. */
  public void optimizeSelectedItems() {
    legacyEditActions.optimizeSelectedItems();
  }

  /** GUI workspace member. */
  public void autorouteSelectedItems() {
    legacyEditActions.autorouteSelectedItems();
  }

  /** GUI workspace member. */
  public InteractiveActionThread startAutorouterAndRouteOptimizer(RoutingJob job) {
    return routingActions.start(job);
  }

  /** GUI workspace member. */
  public void stopAutorouterAndRouteOptimizer() {
    routingActions.stop();
  }

  /** GUI workspace member. */
  void requestStopFromSessionPort() {
    if (this.interactiveActionThread != null) {
      this.interactiveActionThread.requestStop();
    }
  }

  void setInteractiveActionThread(InteractiveActionThread worker) {
    this.interactiveActionThread = worker;
  }

  /** GUI workspace member. */
  public WorkspacePort getSessionPort() {
    return sessionPort;
  }

  /** GUI workspace member. */
  public void extendSelectionToWholeNets() {
    interactionController.extendSelectionToWholeNets();
  }

  /** Extends the selection to include all items belonging to the same components as selected. */
  public void extendSelectionToWholeComponents() {
    interactionController.extendSelectionToWholeComponents();
  }

  /** GUI workspace member. */
  public void extendSelectionToWholeConnectedSets() {
    interactionController.extendSelectionToWholeConnectedSets();
  }

  /** GUI workspace member. */
  public void extendSelectionToWholeConnections() {
    interactionController.extendSelectionToWholeConnections();
  }

  /** GUI workspace member. */
  public void toggleSelectedItemViolations() {
    interactionController.toggleSelectedItemViolations();
  }

  /** GUI workspace member. */
  public void turn45Degree(int factor) {
    interactionController.turn45Degree(factor);
  }

  /** GUI workspace member. */
  public void changePlacementSide() {
    interactionController.changePlacementSide();
  }

  /** GUI workspace member. */
  public void resetRotation() {
    interactionController.resetRotation();
  }

  /** GUI workspace member. */
  public void zoomRegion() {
    interactionController.zoomRegion();
  }

  /** GUI workspace member. */
  public void startCircle(Point2D point) {
    interactionController.startCircle(point);
  }

  /** GUI workspace member. */
  public void startTile(Point2D point) {
    interactionController.startTile(point);
  }

  /** GUI workspace member. */
  public void startPolygonshapeItem(Point2D point) {
    interactionController.startPolygonshapeItem(point);
  }

  /** GUI workspace member. */
  public void startAddingHole(Point2D point) {
    interactionController.startAddingHole(point);
  }

  /** GUI workspace member. */
  public Rectangle getGraphicsUpdateRectangle() {
    return presentationController.getGraphicsUpdateRectangle();
  }

  /** GUI workspace member. */
  public Set<Item> pickItems(FloatPoint location) {
    return interactionController.pickItems(location);
  }

  /** GUI workspace member. */
  public Set<Item> pickItems(FloatPoint point, ItemSelectionFilter itemFilter) {
    return interactionController.pickItems(point, itemFilter);
  }

  /** GUI workspace member. */
  public void moveMouse(FloatPoint toLocation) {
    interactionController.moveMouse(toLocation);
  }

  /** GUI workspace member. */
  public void adjustDesignBounds() {
    presentationController.adjustDesignBounds();
  }

  /** GUI workspace member. */
  public void dispose() {
    requestStopFromSessionPort();
    if (sessionPort instanceof WorkspacePortAdapter adapter) {
      adapter.invalidateRun();
    }
    eventBridge.dispose();
    closeFiles();
    graphicsContext = null;
    coordinateTransform = null;
    // Clear the instance field and the static singleton so that a subsequent
    // getOrCreate/reset call (e.g. when reopening the application) starts fresh.
    workspaceSettings = null;
    WorkspaceSettings.resetForTesting();
    editorStateController = null;
    analysisController.removeRatsnest();
    clearanceViolations = null;
    board = null;
  }

  /** GUI workspace member. */
  public BoardUpdateStrategy getBoardUpdateStrategy() {
    return sessionState.getBoardUpdateStrategy();
  }

  /** GUI workspace member. */
  public void setBoardUpdateStrategy(BoardUpdateStrategy boardUpdateStrategy) {
    sessionState.setBoardUpdateStrategy(boardUpdateStrategy);
  }

  /** GUI workspace member. */
  public String getHybridRatio() {
    return sessionState.getHybridRatio();
  }

  /** GUI workspace member. */
  public void setHybridRatio(String hybridRatio) {
    sessionState.setHybridRatio(hybridRatio);
  }

  /** GUI workspace member. */
  public ItemSelectionStrategy getItemSelectionStrategy() {
    return sessionState.getItemSelectionStrategy();
  }

  /** GUI workspace member. */
  public void setItemSelectionStrategy(ItemSelectionStrategy itemSelectionStrategy) {
    sessionState.setItemSelectionStrategy(itemSelectionStrategy);
  }

  /** GUI workspace member. */
  public int getNumThreads() {
    return sessionState.getNumThreads();
  }

  /** GUI workspace member. */
  public void setNumThreads(int value) {
    sessionState.setNumThreads(value);
  }

  /** GUI workspace member. */
  public void addReadOnlyEventListener(Consumer<Boolean> listener) {
    sessionMode.addReadOnlyEventListener(listener);
  }
}
