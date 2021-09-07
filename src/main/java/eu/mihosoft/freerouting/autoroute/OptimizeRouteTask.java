/*
 *   Copyright (C) 2021
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 */
package eu.mihosoft.freerouting.autoroute;

import java.util.Iterator;

import eu.mihosoft.freerouting.board.Item;
import eu.mihosoft.freerouting.datastructures.UndoableObjects;
import eu.mihosoft.freerouting.datastructures.UndoableObjects.UndoableObjectNode;
import eu.mihosoft.freerouting.interactive.InteractiveActionThread;
import eu.mihosoft.freerouting.interactive.RatsNest;
import eu.mihosoft.freerouting.logger.FRLogger;

public class OptimizeRouteTask extends BatchOptRoute implements Runnable {
	private Item curr_item;
	private int pass_no;
	private boolean with_prefered_directions; 
	private ItemRouteResult route_result;
    private BatchOptRouteMT optimizer;
    
	public OptimizeRouteTask(BatchOptRouteMT p_optimizer, int item_id, 
			                 int p_pass_no, boolean p_with_prefered_directions,
			                 double p_min_cumulative_trace_length) {
		super(p_optimizer.thread, true);

		optimizer = p_optimizer;
		
		curr_item = findItemOnBoard(item_id);  
		//curr_item.board = this.routing_board;
		
		pass_no = p_pass_no;
		with_prefered_directions = p_with_prefered_directions;
		this.min_cumulative_trace_length_before = p_min_cumulative_trace_length;
	}
	
	private Item findItemOnBoard(int item_id) {
		boolean found = false;
		
		Iterator<UndoableObjectNode> it = this.routing_board.item_list.start_read_object();
		
		while (it.hasNext()) {
			UndoableObjects.Storable curr_ob = routing_board.item_list.read_object(it);
			
			if (curr_ob instanceof Item) {
				Item item = (Item)curr_ob;
				
				if (item.get_id_no() == item_id) {
					return item;
				}
			}
		}
		
		return null;
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		
		//FRLogger.info("Start to run OptimizeRouteTask on pass " + pass_no + " with item id: " + curr_item.get_id_no() );
			
		route_result = opt_route_item(curr_item, pass_no, with_prefered_directions);
		
		boolean winning_candidate = optimizer.is_winning_candidate(this);
		
		long duration = System.currentTimeMillis() - startTime;
		long minutes = duration / 60000;
		float sec = (duration % 60000) /1000.0F;
		
		FRLogger.info("Finished 1 task (" + optimizer.get_num_tasks_finished() + 
				       " of " + optimizer.get_num_tasks() + 
				       ") on pass " + pass_no + " with item id: " +
		              curr_item.get_id_no() + " in " +  minutes + " m " + sec + "s" +
				      " won: " + winning_candidate + 
		              " Improved: " + route_result.improved()+
		              " via-: " + route_result.via_count_reduced() +
		              (winning_candidate? (" len-: " + route_result.length_reduced()) : "") +
		              " incomplete(" + route_result.incomplete_count_before() +
		              ", " + route_result.incomplete_count() + ")");
		
		if (!winning_candidate) { clean(); }
	}
	
	public ItemRouteResult getRouteResult() { return this.route_result; }
	
	public Item getItem() { return curr_item; }
	
	public void clean() { // try to speed up memory release
		curr_item.board = null;
		curr_item = null;
		
		this.sorted_route_items = null;
		this.routing_board = null;
	}

	@Override
    protected void remove_ratsnest()
    {
    	// do nothing as we create a new instance of ratsnest every time
		// assume it'll be only called twice: before and after routing
    }
    
	@Override
    protected RatsNest get_ratsnest()
    {
    	return new RatsNest(this.routing_board, this.thread.hdlg.get_locale());
    }
}
