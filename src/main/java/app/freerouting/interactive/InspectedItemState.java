package app.freerouting.interactive;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.WindowObjectInfo;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JPopupMenu;

/** Class implementing actions on the currently selected items. */
public final class InspectedItemState extends InteractiveState {

  private Set<Item> itemList;
  private ClearanceViolations clearanceViolations;

  /** Creates a new instance of InspectedItemState */
  private InspectedItemState(
      Set<Item> pItemList, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
    itemList = pItemList;
  }

  /**
   * Creates a new InspectedItemState with the items in p_item_list selected. Returns null, if
   * p_item_list is empty.
   */
  public static InspectedItemState getInstance(
      Set<Item> pItemList, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    if (pItemList.isEmpty()) {
      return null;
    }
    return new InspectedItemState(pItemList, pParentState, pBoardHandling);
  }

  /** Gets the list of the currently selected items. */
  public Collection<Item> getItemList() {
    return itemList;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint pLocation) {
    return toggleSelect(pLocation);
  }

  @Override
  public InteractiveState mouseDragged(FloatPoint pPoint) {
    return InspectItemsInRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char pKeyChar) {
    InteractiveState result = this;

    switch (pKeyChar) {
      case 'e' -> result = this.extentToWholeConnections();
      case 'i' -> result = this.info();
      case 'n' -> this.extentToWholeNets();
      case 'r' -> result = ZoomRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
      case 's' -> result = this.extentToWholeConnectedSets();
      case 'v' -> this.toggleClearanceViolations();
      case 'w' -> this.hdlg.zoomSelection();
      default -> result = super.keyTyped(pKeyChar);
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
   * Picks item at p_point. Removes it from the selectedItems list, if it is already in there,
   * otherwise adds it to the list. Returns true (to change to the returnState) if nothing was
   * picked.
   */
  public InteractiveState toggleSelect(FloatPoint pPoint) {
    Collection<Item> pickedItems = hdlg.pickItems(pPoint);
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
    itemList = hdlg.getInteractiveSettings().getItemSelectionFilter().filter(itemList);
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
  public void draw(Graphics pGraphics) {
    if (itemList == null) {
      return;
    }

    for (Item currItem : itemList) {
      currItem.draw(
          pGraphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.getHilightColor(),
          hdlg.graphicsContext.getHilightColorIntensity());
    }
    if (clearanceViolations != null) {
      clearanceViolations.draw(pGraphics, hdlg.graphicsContext);
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
