package app.freerouting.gui.interactive;

import app.freerouting.autoroute.expansion.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.expansion.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.autoroute.maze.MazeSearchEngine;
import app.freerouting.autoroute.path.FoundConnectionInserter;
import app.freerouting.autoroute.path.FoundConnectionLocator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.gui.rendering.AutorouteDiagnosticRenderer;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** State for testing the expanding algorithm of the autorouter. */
public final class ExpandTestState extends InteractiveState {

  private boolean inAutoroute;
  private MazeSearchEngine mazeSearchAlgo;
  private FoundConnectionLocator autorouteResult;
  private AutorouteControl controlSettings;
  private AutorouteEngine autorouteEngine;

  /** Creates a new instance of ExpandTestState. */
  private ExpandTestState(
      FloatPoint location, InteractiveState returnState, GuiBoardManager boardHandling) {
    super(returnState, boardHandling);
    init(location);
  }

  /** Returns a new instance of ExpandTestState. */
  public static ExpandTestState getInstance(
      FloatPoint location, InteractiveState returnState, GuiBoardManager boardHandling) {
    return new ExpandTestState(location, returnState, boardHandling);
  }

  @Override
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState result;
    if (keyChar == 'n') {
      if (inAutoroute) {
        if (!this.mazeSearchAlgo.occupyNextElement()) {
          // to display the backtack rooms
          completeAutoroute();
          hdlg.screenMessages.setStatusMessage("expansion completed");
        }
      } else {
        boolean completingSucceeded = false;
        while (!completingSucceeded) {
          IncompleteFreeSpaceExpansionRoom nextRoom =
              this.autorouteEngine.getFirstIncompleteExpansionRoom();
          if (nextRoom == null) {
            hdlg.screenMessages.setStatusMessage("expansion completed");
            break;
          }
          completingSucceeded = completeExpansionRoom(nextRoom);
        }
      }
      // hdlg.get_routing_board().autoroute_data().validate();
      result = this;
    } else if (keyChar == 'a') {
      if (inAutoroute) {
        completeAutoroute();
      } else {
        IncompleteFreeSpaceExpansionRoom nextRoom =
            this.autorouteEngine.getFirstIncompleteExpansionRoom();
        while (nextRoom != null) {
          completeExpansionRoom(nextRoom);
          nextRoom = this.autorouteEngine.getFirstIncompleteExpansionRoom();
        }
      }
      result = this;
      // hdlg.get_routing_board().autoroute_data().validate();
    } else if (Character.isDigit(keyChar)) {
      // next 10^keyChar expansions
      int d = Character.digit(keyChar, 10);
      final int maxCount = (int) Math.pow(10, d);
      if (inAutoroute) {
        for (int i = 0; i < maxCount; i++) {
          if (!this.mazeSearchAlgo.occupyNextElement()) {
            // to display the backtack rooms
            completeAutoroute();
            hdlg.screenMessages.setStatusMessage("expansion completed");
            break;
          }
        }
      } else {
        int currentCount = 0;
        IncompleteFreeSpaceExpansionRoom nextRoom =
            this.autorouteEngine.getFirstIncompleteExpansionRoom();
        while (nextRoom != null && currentCount < maxCount) {
          completeExpansionRoom(nextRoom);
          nextRoom = this.autorouteEngine.getFirstIncompleteExpansionRoom();
          ++currentCount;
        }
      }
      result = this;
      // hdlg.get_routing_board().autoroute_data().validate();
    } else {
      autorouteEngine.clear();
      result = super.keyTyped(keyChar);
    }
    hdlg.repaint();
    return result;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return cancel();
  }

  @Override
  public InteractiveState cancel() {
    autorouteEngine.clear();
    return this.returnState;
  }

  @Override
  public InteractiveState complete() {
    return cancel();
  }

  @Override
  public void draw(Graphics graphics) {
    var diagnosticSink = AutorouteDiagnosticRenderer.createSink(graphics, hdlg.graphicsContext);
    autorouteEngine.emitDiagnostics(diagnosticSink, 0.1);
    if (this.autorouteResult != null) {
      this.autorouteResult.emitDiagnostics(diagnosticSink, 0.2);
    }
  }

  private void init(FloatPoint location) {
    // look if an autoroute can be started at the input location
    RoutingBoard board = hdlg.getRoutingBoard();
    int layer = hdlg.getWorkspaceSettings().getLayer();
    Collection<Item> foundItems = board.pickItems(location.round(), layer, null);
    Item routeItem = null;
    int routeNetNo = 0;
    for (Item currentObject : foundItems) {
      if (currentObject instanceof Connectable) {
        Item currentItem = currentObject;
        if (currentItem.netCount() == 1 && currentItem.getNetNumber(0) > 0) {
          routeItem = currentItem;
          routeNetNo = currentItem.getNetNumber(0);
          break;
        }
      }
    }
    this.controlSettings =
        new AutorouteControl(
            hdlg.getRoutingBoard(), routeNetNo, hdlg.getCurrentRoutingJob().routerSettings);
    // this.controlSettings.ripupAllowed = true;
    // this.controlSettings.isFanout = true;
    this.controlSettings.ripupPassNo = 1; // Expand test always starts from pass 1
    this.controlSettings.ripupCosts =
        this.controlSettings.ripupPassNo
            * hdlg.getCurrentRoutingJob().routerSettings.getStartRipupCosts();
    this.controlSettings.viasAllowed = false;
    this.autorouteEngine =
        new AutorouteEngine(board, this.controlSettings.traceClearanceClassIndex, false);
    this.autorouteEngine.initConnection(routeNetNo, null, null);
    if (routeItem == null) {
      // create an expansion room in the empty space
      TileShape containedShape = TileShape.getInstance(location.round());
      IncompleteFreeSpaceExpansionRoom expansionRoom =
          autorouteEngine.addIncompleteExpansionRoom(null, layer, containedShape);
      hdlg.screenMessages.setStatusMessage("expansion test started");
      completeExpansionRoom(expansionRoom);
      return;
    }
    Set<Item> routeStartSet = routeItem.getConnectedSet(routeNetNo);
    Set<Item> routeDestSet = routeItem.getUnconnectedSet(routeNetNo);
    if (!routeDestSet.isEmpty()) {
      hdlg.screenMessages.setStatusMessage("app.freerouting.autoroute test started");
      this.mazeSearchAlgo =
          MazeSearchEngine.getInstance(
              routeStartSet, routeDestSet, autorouteEngine, controlSettings);
      this.inAutoroute = this.mazeSearchAlgo != null;
    }
  }

  private void completeAutoroute() {
    MazeSearchEngine.Result searchResult = this.mazeSearchAlgo.findConnection();
    if (searchResult != null) {
      SortedSet<Item> rippedItemList = new TreeSet<>();
      this.autorouteResult =
          FoundConnectionLocator.getInstance(
              searchResult,
              controlSettings,
              this.autorouteEngine.autorouteSearchTree,
              hdlg.getRoutingBoard().rules.getTraceAngleRestriction(),
              rippedItemList,
              null);
      hdlg.getRoutingBoard().generateSnapshot();
      SortedSet<Item> rippedConnections = new TreeSet<>();
      for (Item currentRippedItem : rippedItemList) {
        rippedConnections.addAll(
            currentRippedItem.getConnectionItems(Item.StopConnectionOption.VIA));
      }
      hdlg.getRoutingBoard().removeItems(rippedConnections);
      FoundConnectionInserter.getInstance(autorouteResult, hdlg.getRoutingBoard(), controlSettings);
    }
  }

  /** Returns true, if the completion succeeded. */
  private boolean completeExpansionRoom(IncompleteFreeSpaceExpansionRoom incompleteRoom) {
    Collection<CompleteFreeSpaceExpansionRoom> completedRooms =
        autorouteEngine.completeExpansionRoom(incompleteRoom);
    return !completedRooms.isEmpty();
  }
}
