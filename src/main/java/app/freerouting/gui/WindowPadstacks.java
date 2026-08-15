package app.freerouting.gui;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.datastructures.UndoableObjects;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Window displaying the library padstacks. */
public class WindowPadstacks extends WindowObjectListWithFilter {

  /** Creates a new instance of PadstacksWindow. */
  public WindowPadstacks(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("padstacks"));
  }

  /** Fills the list with the library padstacks. */
  @Override
  protected void fillList() {
    Padstacks padstacks =
        this.boardFrame.boardPanel.boardHandling.getRoutingBoard().library.padstacks;
    Padstack[] sortedArr = new Padstack[padstacks.count()];
    for (int i = 0; i < sortedArr.length; i++) {
      sortedArr[i] = padstacks.get(i + 1);
    }
    Arrays.sort(sortedArr);
    for (int i = 0; i < sortedArr.length; i++) {
      this.addToList(sortedArr[i]);
    }
    this.list.setVisibleRowCount(Math.min(padstacks.count(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedPadstacks = list.getSelectedValuesList();
    if (selectedPadstacks.isEmpty()) {
      return;
    }
    Collection<Padstack> padstackList = new LinkedList<>();
    for (int i = 0; i < selectedPadstacks.size(); i++) {
      padstackList.add((Padstack) selectedPadstacks.get(i));
    }
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
    Set<Item> boardInstances = new TreeSet<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = routingBoard.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentObject = routingBoard.itemList.readObject(it);
      if (currentObject == null) {
        break;
      }
      if (currentObject instanceof DrillItem item) {
        Padstack currentPadstack = item.getPadstack();
        for (Padstack currentSelectedPadstack : padstackList) {
          if (currentPadstack == currentSelectedPadstack) {
            boardInstances.add((Item) currentObject);
            break;
          }
        }
      }
    }
    boardFrame.boardPanel.boardHandling.selectItems(boardInstances);
    boardFrame.boardPanel.boardHandling.zoomSelection();
  }
}
