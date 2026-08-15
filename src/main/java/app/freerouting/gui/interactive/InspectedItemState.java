package app.freerouting.gui.interactive;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.WindowObjectInfo;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.workspace.ClearanceViolations;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JPopupMenu;

/** Class implementing actions on the currently selected items. */
public final class InspectedItemState extends InteractiveState {

  private Set<Item> itemList;
  private ClearanceViolations clearanceViolations;

  /** Creates a new instance of InspectedItemState. */
  private InspectedItemState(
      Set<Item> itemList, InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    this.itemList = itemList;
  }

  /**
   * Creates a new InspectedItemState with the supplied items selected. Returns {@code null} if the
   * item set is empty.
   */
  public static InspectedItemState getInstance(
      Set<Item> itemList, InteractiveState parentState, GuiBoardManager boardHandling) {
    if (itemList.isEmpty()) {
      return null;
    }
    return new InspectedItemState(itemList, parentState, boardHandling);
  }

  /** Gets the list of the currently selected items. */
  public Collection<Item> getItemList() {
    return itemList;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return toggleSelect(location);
  }

  @Override
  public InteractiveState mouseDragged(FloatPoint point) {
    return InspectItemsInRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState result = this;

    switch (keyChar) {
      case 'e' -> result = this.extentToWholeConnections();
      case 'i' -> result = this.info();
      case 'n' -> this.extentToWholeNets();
      case 'r' -> result = ZoomRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
      case 's' -> result = this.extentToWholeConnectedSets();
      case 'v' -> this.toggleClearanceViolations();
      case 'w' -> this.hdlg.zoomSelection();
      default -> result = super.keyTyped(keyChar);
    }
    return result;
  }

  /** Select also all items belonging to any net of the current selected items. */
  public InteractiveState extentToWholeNets() {

    // collect all net numbers of the selected items
    Set<Integer> currNetNoSet = new TreeSet<>();
    for (Item currItem : itemList) {
      if (currItem instanceof Connectable) {
        for (int i = 0; i < currItem.netCount(); i++) {
          currNetNoSet.add(currItem.getNetNo(i));
        }
      }
    }
    Set<Item> newSelectedItems = new TreeSet<>();
    for (int currNetNo : currNetNoSet) {
      newSelectedItems.addAll(hdlg.getRoutingBoard().getConnectableItems(currNetNo));
    }
    this.itemList = newSelectedItems;
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    filter();
    hdlg.repaint();
    return this;
  }

  /** Select also all items belonging to any group of the current selected items. */
  public InteractiveState extentToWholeComponents() {

    // collect all group numbers of the selected items
    Set<Integer> currGroupNoSet = new TreeSet<>();
    for (Item currItem : itemList) {
      if (currItem.getComponentNo() > 0) {
        currGroupNoSet.add(currItem.getComponentNo());
      }
    }
    Set<Item> newSelectedItems = new TreeSet<>(itemList);
    for (int currGroupNo : currGroupNoSet) {
      newSelectedItems.addAll(hdlg.getRoutingBoard().getComponentItems(currGroupNo));
    }
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    this.itemList = newSelectedItems;
    hdlg.repaint();
    return this;
  }

  /** Select also all items belonging to any connected set of the current selected items. */
  public InteractiveState extentToWholeConnectedSets() {
    Set<Item> newSelectedItems = new TreeSet<>();
    for (Item currItem : this.itemList) {
      if (currItem instanceof Connectable) {
        newSelectedItems.addAll(currItem.getConnectedSet(-1));
      }
    }
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    this.itemList = newSelectedItems;
    filter();
    hdlg.repaint();
    return this;
  }

  /** Select also all items belonging to any connection of the current selected items. */
  public InteractiveState extentToWholeConnections() {
    Set<Item> newSelectedItems = new TreeSet<>();
    for (Item currItem : this.itemList) {
      if (currItem instanceof Connectable) {
        newSelectedItems.addAll(currItem.getConnectionItems());
      }
    }
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    this.itemList = newSelectedItems;
    filter();
    hdlg.repaint();
    return this;
  }

  /**
   * Picks the item at the specified point. Removes it from the selected-items list if it is already
   * there; otherwise, adds it to the list.
   */
  public InteractiveState toggleSelect(FloatPoint point) {
    Collection<Item> pickedItems = hdlg.pickItems(point);
    boolean stateEnded = pickedItems.isEmpty();
    if (pickedItems.size() == 1) {
      Item pickedItem = pickedItems.iterator().next();
      if (this.itemList.contains(pickedItem)) {
        this.itemList.remove(pickedItem);
        if (this.itemList.isEmpty()) {
          stateEnded = true;
        }
      } else {
        this.itemList.add(pickedItem);
      }
    }
    hdlg.repaint();
    InteractiveState result;
    if (stateEnded) {
      result = this.returnState;
    } else {
      result = this;
    }
    return result;
  }

  /** Shows or hides the clearance violations of the selected items. */
  public void toggleClearanceViolations() {
    if (clearanceViolations == null) {
      clearanceViolations = new ClearanceViolations(this.itemList);
      Integer violationCount = clearanceViolations.list.size();
      String currMessage = violationCount + " " + tm.getText("clearance_violations_found");
      hdlg.screenMessages.setStatusMessage(currMessage);
    } else {
      clearanceViolations = null;
      hdlg.screenMessages.setStatusMessage("");
    }
    hdlg.repaint();
  }

  /** Removes items not selected by the current interactive filter from the selected item list. */
  public InteractiveState filter() {
    itemList = hdlg.getWorkspaceSettings().getItemSelectionFilter().filter(itemList);
    InteractiveState result = this;
    if (itemList.isEmpty()) {
      result = this.returnState;
    }
    hdlg.repaint();
    return result;
  }

  /** Prints information about the selected item into a graphical text window. */
  public InspectedItemState info() {
    WindowObjectInfo.display(
        this.itemList,
        hdlg.getPanel().boardFrame,
        hdlg.coordinateTransform,
        new java.awt.Point(100, 100));
    return this;
  }

  @Override
  public String getHelpId() {
    return "InspectedItemState";
  }

  @Override
  public void draw(Graphics graphics) {
    if (itemList == null) {
      return;
    }

    for (Item currItem : itemList) {
      BoardRenderer.drawOverlayItem(
          currItem,
          graphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.getHighlightColor(),
          hdlg.graphicsContext.getHighlightColorIntensity());
    }
    if (clearanceViolations != null) {
      clearanceViolations.draw(graphics, hdlg.graphicsContext);
    }
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuSelect;
  }

  @Override
  public void setToolbar() {
    hdlg.getPanel().boardFrame.setInspectToolbar();
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("in_inspect_item_mode"));
  }
}
