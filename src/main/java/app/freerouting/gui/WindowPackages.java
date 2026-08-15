package app.freerouting.gui;

import app.freerouting.board.Component;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Window displaying the library packages. */
public class WindowPackages extends WindowObjectListWithFilter {

  /** Creates a new instance of PackagesWindow. */
  public WindowPackages(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("packages"));
  }

  /** Fills the list with the library packages. */
  @Override
  protected void fillList() {
    Packages packages = this.boardFrame.boardPanel.boardHandling.getRoutingBoard().library.packages;
    Package[] sortedArr = new Package[packages.count()];
    for (int i = 0; i < sortedArr.length; i++) {
      sortedArr[i] = packages.get(i + 1);
    }
    Arrays.sort(sortedArr);
    for (int i = 0; i < sortedArr.length; i++) {
      this.addToList(sortedArr[i]);
    }
    this.list.setVisibleRowCount(Math.min(packages.count(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedPackages = list.getSelectedValuesList();
    if (selectedPackages.isEmpty()) {
      return;
    }
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
    Set<Item> boardInstances = new TreeSet<>();
    Collection<Item> boardItems = routingBoard.getItems();
    for (Item currentItem : boardItems) {
      if (currentItem.getComponentNo() > 0) {
        Component currentComponent = routingBoard.components.get(currentItem.getComponentNo());
        Package currentPackage = currentComponent.getPackage();
        boolean packageMatches = false;
        for (int i = 0; i < selectedPackages.size(); i++) {
          if (currentPackage == selectedPackages.get(i)) {
            packageMatches = true;
            break;
          }
        }
        if (packageMatches) {
          boardInstances.add(currentItem);
        }
      }
    }
    boardFrame.boardPanel.boardHandling.selectItems(boardInstances);
    boardFrame.boardPanel.boardHandling.zoomSelection();
  }
}
