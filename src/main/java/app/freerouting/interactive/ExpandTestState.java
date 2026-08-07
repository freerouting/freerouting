package app.freerouting.interactive;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.autoroute.AutorouteEngine;
import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.InsertFoundConnectionAlgo;
import app.freerouting.autoroute.LocateFoundConnectionAlgo;
import app.freerouting.autoroute.MazeSearchAlgo;
import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.TileShape;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** State for testing the expanding algorithm of the autorouter. */
public final class ExpandTestState extends InteractiveState {

  private boolean inAutoroute;
  private MazeSearchAlgo mazeSearchAlgo;
  private LocateFoundConnectionAlgo autorouteResult;
  private AutorouteControl controlSettings;
  private AutorouteEngine autorouteEngine;

  /** Creates a new instance of ExpandTestState */
  private ExpandTestState(
      FloatPoint p_location, InteractiveState p_return_state, GuiBoardManager p_board_handling) {
    super(p_return_state, p_board_handling);
    init(p_location);
  }

  public static ExpandTestState get_instance(
      FloatPoint p_location, InteractiveState p_return_state, GuiBoardManager p_board_handling) {
    return new ExpandTestState(p_location, p_return_state, p_board_handling);
  }

  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState result;
    if (p_key_char == 'n') {
      if (inAutoroute) {
        if (!this.mazeSearchAlgo.occupy_next_element()) {
          // to display the backtack rooms
          complete_autoroute();
          hdlg.screenMessages.set_status_message("expansion completed");
        }
      } else {
        boolean completingSucceeded = false;
        while (!completingSucceeded) {
          IncompleteFreeSpaceExpansionRoom nextRoom =
              this.autorouteEngine.get_first_incomplete_expansion_room();
          if (nextRoom == null) {
            hdlg.screenMessages.set_status_message("expansion completed");
            break;
          }
          completingSucceeded = complete_expansion_room(nextRoom);
        }
      }
      // hdlg.get_routing_board().autoroute_data().validate();
      result = this;
    } else if (p_key_char == 'a') {
      if (inAutoroute) {
        complete_autoroute();
      } else {
        IncompleteFreeSpaceExpansionRoom nextRoom =
            this.autorouteEngine.get_first_incomplete_expansion_room();
        while (nextRoom != null) {
          complete_expansion_room(nextRoom);
          nextRoom = this.autorouteEngine.get_first_incomplete_expansion_room();
        }
      }
      result = this;
      // hdlg.get_routing_board().autoroute_data().validate();
    } else if (Character.isDigit(p_key_char)) {
      // next 10^p_key_char expansions
      int d = Character.digit(p_key_char, 10);
      final int maxCount = (int) Math.pow(10, d);
      if (inAutoroute) {
        for (int i = 0; i < maxCount; i++) {
          if (!this.mazeSearchAlgo.occupy_next_element()) {
            // to display the backtack rooms
            complete_autoroute();
            hdlg.screenMessages.set_status_message("expansion completed");
            break;
          }
        }
      } else {
        int currCount = 0;
        IncompleteFreeSpaceExpansionRoom nextRoom =
            this.autorouteEngine.get_first_incomplete_expansion_room();
        while (nextRoom != null && currCount < maxCount) {
          complete_expansion_room(nextRoom);
          nextRoom = this.autorouteEngine.get_first_incomplete_expansion_room();
          ++currCount;
        }
      }
      result = this;
      // hdlg.get_routing_board().autoroute_data().validate();
    } else {
      autorouteEngine.clear();
      result = super.key_typed(p_key_char);
    }
    hdlg.repaint();
    return result;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
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
  public void draw(Graphics p_graphics) {
    autorouteEngine.draw(p_graphics, hdlg.graphicsContext, 0.1);
    if (this.autorouteResult != null) {
      this.autorouteResult.draw(p_graphics, hdlg.graphicsContext);
    }
  }

  private void init(FloatPoint p_location) {
    // look if an autoroute can be started at the input location
    RoutingBoard board = hdlg.get_routing_board();
    int layer = hdlg.getInteractiveSettings().get_layer();
    Collection<Item> foundItems = board.pick_items(p_location.round(), layer, null);
    Item routeItem = null;
    int routeNetNo = 0;
    for (Item currOb : foundItems) {
      if (currOb instanceof Connectable) {
        Item currItem = currOb;
        if (currItem.net_count() == 1 && currItem.get_net_no(0) > 0) {
          routeItem = currItem;
          routeNetNo = currItem.get_net_no(0);
          break;
        }
      }
    }
    this.controlSettings =
        new AutorouteControl(
            hdlg.get_routing_board(), routeNetNo, hdlg.getCurrentRoutingJob().routerSettings);
    // this.controlSettings.ripupAllowed = true;
    // this.controlSettings.isFanout = true;
    this.controlSettings.ripupPassNo = 1; // Expand test always starts from pass 1
    this.controlSettings.ripupCosts =
        this.controlSettings.ripupPassNo
            * hdlg.getCurrentRoutingJob().routerSettings.get_start_ripup_costs();
    this.controlSettings.viasAllowed = false;
    this.autorouteEngine =
        new AutorouteEngine(board, this.controlSettings.traceClearanceClassNo, false);
    this.autorouteEngine.init_connection(routeNetNo, null, null);
    if (routeItem == null) {
      // create an expansion room in the empty space
      TileShape containedShape = TileShape.get_instance(p_location.round());
      IncompleteFreeSpaceExpansionRoom expansionRoom =
          autorouteEngine.add_incomplete_expansion_room(null, layer, containedShape);
      hdlg.screenMessages.set_status_message("expansion test started");
      complete_expansion_room(expansionRoom);
      return;
    }
    Set<Item> routeStartSet = routeItem.get_connected_set(routeNetNo);
    Set<Item> routeDestSet = routeItem.get_unconnected_set(routeNetNo);
    if (!routeDestSet.isEmpty()) {
      hdlg.screenMessages.set_status_message("app.freerouting.autoroute test started");
      this.mazeSearchAlgo =
          MazeSearchAlgo.get_instance(
              routeStartSet, routeDestSet, autorouteEngine, controlSettings);
      this.inAutoroute = this.mazeSearchAlgo != null;
    }
  }

  private void complete_autoroute() {
    MazeSearchAlgo.Result searchResult = this.mazeSearchAlgo.find_connection();
    if (searchResult != null) {
      SortedSet<Item> rippedItemList = new TreeSet<>();
      this.autorouteResult =
          LocateFoundConnectionAlgo.get_instance(
              searchResult,
              controlSettings,
              this.autorouteEngine.autorouteSearchTree,
              hdlg.get_routing_board().rules.get_trace_angle_restriction(),
              rippedItemList,
              null);
      hdlg.get_routing_board().generate_snapshot();
      SortedSet<Item> rippedConnections = new TreeSet<>();
      for (Item curr_ripped_item : rippedItemList) {
        rippedConnections.addAll(
            curr_ripped_item.get_connection_items(Item.StopConnectionOption.VIA));
      }
      hdlg.get_routing_board().remove_items(rippedConnections);
      InsertFoundConnectionAlgo.get_instance(
          autorouteResult, hdlg.get_routing_board(), controlSettings);
    }
  }

  /** Returns true, if the completion succeeded. */
  private boolean complete_expansion_room(IncompleteFreeSpaceExpansionRoom p_incomplete_room) {
    Collection<CompleteFreeSpaceExpansionRoom> completedRooms =
        autorouteEngine.complete_expansion_room(p_incomplete_room);
    return !completedRooms.isEmpty();
  }
}
