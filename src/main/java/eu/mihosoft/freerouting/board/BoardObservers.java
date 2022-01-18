package eu.mihosoft.freerouting.board;

import eu.mihosoft.freerouting.datastructures.Observers;

public interface BoardObservers extends Observers<Item>
{
    /**
     * Enable the observers to syncronize the moved component.
     */
    void notify_moved(Component p_component);
}
