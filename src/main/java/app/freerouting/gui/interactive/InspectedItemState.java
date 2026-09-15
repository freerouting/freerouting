package app.freerouting.gui.interactive;

import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.windows.board.WindowObjectInfo;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.progress.ClearanceViolations;
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
    Set<Integer> currentNetNoSet = new TreeSet<>();
    for (Item currentItem : itemList) {
      if (currentItem instanceof Connectable) {
        for (int i = 0; i < currentItem.netCount(); i++) {
          currentNetNoSet.add(currentItem.getNetNumber(i));
        }
      }
    }
    Set<Item> newSelectedItems = new TreeSet<>();
    for (int currentNetNumber : currentNetNoSet) {
      newSelectedItems.addAll(hdlg.getRoutingBoard().getConnectableItems(currentNetNumber));
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
    Set<Integer> currentGroupNoSet = new TreeSet<>();
    for (Item currentItem : itemList) {
      if (currentItem.getComponentId() > 0) {
        currentGroupNoSet.add(currentItem.getComponentId());
      }
    }
    Set<Item> newSelectedItems = new TreeSet<>(itemList);
    for (int currentGroupNo : currentGroupNoSet) {
      newSelectedItems.addAll(hdlg.getRoutingBoard().getComponentItems(currentGroupNo));
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
    for (Item currentItem : this.itemList) {
      if (currentItem instanceof Connectable) {
        newSelectedItems.addAll(currentItem.getConnectedSet(-1));
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
    for (Item currentItem : this.itemList) {
      if (currentItem instanceof Connectable) {
        newSelectedItems.addAll(currentItem.getConnectionItems());
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
      String currentMessage = violationCount + " " + tm.getText("clearance_violations_found");
      hdlg.screenMessages.setStatusMessage(currentMessage);
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

    for (Item currentItem : itemList) {
      BoardRenderer.drawOverlayItem(
          currentItem,
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
