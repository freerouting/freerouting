package app.freerouting.gui;

import app.freerouting.board.Component;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.Package;
import app.freerouting.core.Packages;

import java.util.*;

/**
 * Window displaying the library packages.
 */
public class WindowPackages extends WindowObjectListWithFilter
{

  /**
   * Creates a new instance of PackagesWindow
   */
  public WindowPackages(BoardFrame p_board_frame)
  {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("packages"));
  }

  /**
   * Fills the list with the library packages.
   */
  @Override
  protected void fill_list()
  {
    Packages packages = this.board_frame.board_panel.board_handling.get_routing_board().library.packages;
    Package[] sorted_arr = new Package[packages.count()];
    for (int i = 0; i < sorted_arr.length; ++i)
    {
      sorted_arr[i] = packages.get(i + 1);
    }
    Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i)
    {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(packages.count(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances()
  {
    List<Object> selected_packages = list.getSelectedValuesList();
    if (selected_packages.isEmpty())
    {
      return;
    }
    RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
    Set<Item> board_instances = new TreeSet<>();
    Collection<Item> board_items = routing_board.get_items();
    for (Item curr_item : board_items)
    {
      if (curr_item.get_component_no() > 0)
      {
        Component curr_component = routing_board.components.get(curr_item.get_component_no());
        Package curr_package = curr_component.get_package();
        boolean package_matches = false;
        for (int i = 0; i < selected_packages.size(); ++i)
        {
          if (curr_package == selected_packages.get(i))
          {
            package_matches = true;
            break;
          }
        }
        if (package_matches)
        {
          board_instances.add(curr_item);
        }
      }
    }
    board_frame.board_panel.board_handling.select_items(board_instances);
    board_frame.board_panel.board_handling.zoom_selection();
  }
}