package app.freerouting.gui;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.Component;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceContract;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves component scope from GUI selection states and synchronizes with {@link
 * app.freerouting.gui.workspace.WorkspaceSettings}.
 */
public final class SelectionScopeResolver {

  private SelectionScopeResolver() {}

  /**
   * Extracts distinct component designators from the currently selected items on the board.
   *
   * @param boardHandling GUI board manager
   * @return array of unique component designators sorted alphabetically, or empty array
   */
  public static String[] extractSelectedComponentNames(GuiBoardManager boardHandling) {
    if (boardHandling == null) {
      return new String[0];
    }
    RoutingBoard board = boardHandling.getRoutingBoard();
    if (board == null || board.components == null) {
      return new String[0];
    }
    Collection<Item> selectedItems = boardHandling.getSelectedItems();
    return extractComponentNames(selectedItems, board);
  }

  /**
   * Extracts distinct sorted component designators from an item collection.
   *
   * @param items collection of items
   * @param board routing board
   * @return array of unique component designators sorted alphabetically, or empty array
   */
  public static String[] extractComponentNames(Collection<Item> items, RoutingBoard board) {
    if (items == null || items.isEmpty() || board == null || board.components == null) {
      return new String[0];
    }
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (Item item : items) {
      if (item != null && item.getComponentId() > 0) {
        Component comp = board.components.get(item.getComponentId());
        if (comp != null && comp.name != null && !comp.name.isBlank()) {
          names.add(comp.name.trim());
        }
      }
    }
    return names.toArray(new String[0]);
  }

  /**
   * Applies the selected components to {@link app.freerouting.gui.workspace.WorkspaceSettings}.
   *
   * @param boardHandling GUI board manager
   * @return true if components were extracted and applied, false otherwise
   */
  public static boolean applySelectionToScope(GuiBoardManager boardHandling) {
    if (boardHandling instanceof WorkspaceContract gui) {
      String[] selectedNames = extractSelectedComponentNames(boardHandling);
      if (selectedNames.length > 0) {
        gui.getWorkspaceSettings().setScopeComponents(selectedNames);
        return true;
      }
    }
    return false;
  }

  /**
   * Applies selected components to scope and starts the autorouter immediately.
   *
   * @param boardFrame the active board frame
   */
  public static void routeSelectedComponents(BoardFrame boardFrame) {
    if (boardFrame == null
        || boardFrame.boardPanel == null
        || boardFrame.boardPanel.boardHandling == null) {
      return;
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    String[] selectedNames = extractSelectedComponentNames(boardHandling);
    if (selectedNames.length > 0 && boardHandling instanceof WorkspaceContract gui) {
      gui.getWorkspaceSettings().setScopeComponents(selectedNames);
      boardHandling.startAutorouterAndRouteOptimizer(null);
    }
  }
}
