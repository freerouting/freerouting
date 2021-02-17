/*
 *   Copyright (C) 2021  Bob Fu
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

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import eu.mihosoft.freerouting.autoroute.BatchOptRoute.ReadSortedRouteItems;
import eu.mihosoft.freerouting.board.Item;
import eu.mihosoft.freerouting.interactive.InteractiveActionThread;
import eu.mihosoft.freerouting.logger.FRLogger;

/**
 * @author Bob Fu
 *
 */
public class BatchOptRouteMT extends BatchOptRoute  {
	private BoardUpdateStrategy board_update_strategy;
	private ThreadPoolExecutor pool;
	private RouteResult best_route_result;
	private OptimizeRouteTask winning_candidate;
	private int thread_pool_size;
	private int num_tasks = 0;
	private int num_tasks_finished = 0;
	private int update_count = 0;
	private CountDownLatch task_completion_signal = new CountDownLatch(1);
	
	/**
	 * @param p_thread
	 */
	public BatchOptRouteMT(InteractiveActionThread p_thread, int p_thread_pool_size,
                           BoardUpdateStrategy p_board_update_strategy) {
		super(p_thread);
		
		this.thread_pool_size = p_thread_pool_size;
        this.board_update_strategy = p_board_update_strategy;
		
		best_route_result = new RouteResult(false);
		winning_candidate = null;
	}
	
	public int get_num_tasks()          { return num_tasks;          }
	public int get_num_tasks_finished() { return num_tasks_finished; }
	
	synchronized void prepare_task_completion_signal() 
	{
		if (task_completion_signal.getCount() <= 0) {
			task_completion_signal = new CountDownLatch(1); 
			// no other way to increase the count for repeated use
			// It's still simpler than general wait/notify
		}		
	}
	
	synchronized public boolean is_winning_candidate(OptimizeRouteTask task) 
	{
		++num_tasks_finished;
		
		RouteResult r = task.getRouteResult();
		
		boolean won = false;
		
		if (r.improved) {	
			if (winning_candidate == null) {
				won = true;
				winning_candidate = task;
				best_route_result = r;
				
			} else {		
				if (r.incomplete_count_after < best_route_result.incomplete_count_after  
					|| (   r.incomplete_count_after == best_route_result.incomplete_count_after
						&& (r.via_count_after < best_route_result.via_count_after 
					         || (    r.via_count_after == best_route_result.via_count_after
						          && r.trace_length_after < best_route_result.trace_length_after
						        )
					       )
					   ) 
				   ) {
					won = true;

					winning_candidate.clean();
					
					winning_candidate = task;
					best_route_result = r;							
				}
			}
		}
		
		if (won && this.board_update_strategy == BoardUpdateStrategy.GREEDY) { 
			update_master_routing_board(); // new tasks will copy the updated board
		}

		task_completion_signal.countDown();
		return won;
	}
	
	private void update_master_routing_board() 
	{
    	this.thread.hdlg.update_routing_board(winning_candidate.routing_board);
    	this.routing_board = this.thread.hdlg.get_routing_board(); 
    	
        double new_trace_length = this.thread.hdlg.coordinate_transform.board_to_user(this.routing_board.cumulative_trace_length());
        this.thread.hdlg.screen_messages.set_post_route_info(this.routing_board.get_vias().size(), new_trace_length);
        
        ++update_count;
	}
	
    @Override
    protected boolean opt_route_pass(int p_pass_no, boolean p_with_prefered_directions)
    {
    	long startTime = System.currentTimeMillis();
    	update_count = 0;

		if (winning_candidate != null) 
		{
			winning_candidate.clean();    winning_candidate = null;
		}

    	boolean route_improved = false;
        int via_count_before = this.routing_board.get_vias().size();
        double trace_length_before = this.thread.hdlg.coordinate_transform.board_to_user(this.routing_board.cumulative_trace_length());
        this.thread.hdlg.screen_messages.set_post_route_info(via_count_before, trace_length_before);
        this.min_cumulative_trace_length_before = calc_weighted_trace_length(routing_board);
        
        this.sorted_route_items = new ReadSortedRouteItems();

        // retrieve all items first to allow concurrent item access
        ArrayList<Item> route_items = new ArrayList<Item>();
        for (Item item = sorted_route_items.next(); item != null; item = sorted_route_items.next()) 
        {   
        	route_items.add(item);
        }
        this.sorted_route_items = null;
                
        FRLogger.info("\n=====================================================================================\n");
        FRLogger.info("Start to run optimization pass " + p_pass_no + " with " + route_items.size() + " items" + " and " + via_count_before + " vias");
		
        num_tasks = route_items.size();    num_tasks_finished = 0;
        
		best_route_result = new RouteResult(false);
		winning_candidate = null;
		
		pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(thread_pool_size, 
				r -> {
						 Thread t = new Thread(r);
						 t.setUncaughtExceptionHandler(
						     (t1, e) -> {
							      FRLogger.error("Exception in thead pool worker thread: " + t1.toString(),  e);
						 });
						 return t;
			         }
			   );
		
		for (int t = 0; t < route_items.size();  ) {
			//if (p_pass_no == 1 && route_items.get(t).get_id_no() != 95999) { ++t; continue; }  // temp test
			
			// each task needs a copy of routing_board, so schedule just enough tasks
			// to keep workers busy in order not to exhaust JVM memory so that  
			// it can run on systems without huge amount of RAM
			if (pool.getActiveCount() < thread_pool_size) {
				int item_id = route_items.get(t).get_id_no();
				FRLogger.info("Scheduling #" + t + " of " + num_tasks + " tasks for item No. " + item_id + " ......");
				
	        	pool.execute(new OptimizeRouteTask(this, item_id, 
	        			     p_pass_no, p_with_prefered_directions, 
     			             this.min_cumulative_trace_length_before));
                ++t;

			} else {
				try {
					//TimeUnit.SECONDS.sleep(3);
					prepare_task_completion_signal();
					task_completion_signal.await(3, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					FRLogger.error("Wait failed", e);
				}
			}
		}
        
        boolean interrupted = false;
        pool.shutdown();
                
        FRLogger.info("--------- Closed task queue of thread pool, not accepting new tasks --------");
         
        try {
	        while (!pool.awaitTermination(1, TimeUnit.MINUTES)) 
	        {
	        	FRLogger.info("<---> After 1 round of wait, CompletedTaskCount: " + pool.getCompletedTaskCount() +
	        			      ", ActiveCount: " + pool.getActiveCount() + 
	        			      ", TaskCount: " + pool.getTaskCount());
	        	
	            if (this.thread.is_stop_requested())
	            {
	            	pool.shutdownNow();
	                return best_route_result.improved;
	            }
	        }
        } 
        catch (InterruptedException ie) 
        {
        	FRLogger.error("Exception with pool.awaitTermination", ie);
        	
        	interrupted = true;
            pool.shutdownNow();
            
            //Thread.currentThread().interrupt(); // Preserve interrupt status
        }
        
        pool = null;
        
        route_improved = best_route_result.improved;
        
        if (!interrupted && route_improved &&
        	this.board_update_strategy == BoardUpdateStrategy.GLOBAL_OPTIMAL) 
        { 
			update_master_routing_board(); 
        }
        
        if (this.use_increased_ripup_costs && !route_improved)
        {
            this.use_increased_ripup_costs = false;
            route_improved = true; // to keep the optimizer going with lower ripup costs
        }
    
		long duration = System.currentTimeMillis() - startTime;
		long minutes = duration / 60000;
		float sec = (duration % 60000) /1000.0F;
		
		FRLogger.info("-------- Finished 1 opt_route_pass on pass " + p_pass_no + 
				      " in " +  minutes + " minutes " + sec + " s with " +
				      update_count + " board updates using " + 
				      thread_pool_size + " threads and " +
				      (board_update_strategy == BoardUpdateStrategy.GLOBAL_OPTIMAL 
				       ? "global_optimal" : "greedy") + " strategy");
        FRLogger.info("interrupted: " + interrupted + ", Improved: " + best_route_result.improved + 
  		      ", via-: " + (via_count_before - best_route_result.via_count_after) + 
  		      ", len-: " + (trace_length_before - best_route_result.trace_length_after));
				
        return route_improved;
    }
}
