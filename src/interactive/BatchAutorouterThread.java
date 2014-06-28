/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
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
 *
 * BatchAutorouterThread.java
 *
 * Created on 25. April 2006, 07:58
 *
 */
package interactive;

import geometry.planar.FloatPoint;
import geometry.planar.FloatLine;

import board.Unit;

import autoroute.BatchAutorouter;
import autoroute.BatchFanout;
import autoroute.BatchOptRoute;

/**
 * Thread for the batch autorouter.
 *
 * @author Alfons Wirtz
 */
public class BatchAutorouterThread extends InteractiveActionThread
{

    /** Creates a new instance of BatchAutorouterThread */
    protected BatchAutorouterThread(BoardHandling p_board_handling)
    {
        super(p_board_handling);
        AutorouteSettings autoroute_settings = p_board_handling.settings.autoroute_settings;
        this.batch_autorouter = new BatchAutorouter(this, !autoroute_settings.get_with_fanout(), true, autoroute_settings.get_start_ripup_costs());
        this.batch_opt_route = new BatchOptRoute(this);

    }

    protected void thread_action()
    {
        try
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("interactive.resources.InteractiveState", hdlg.get_locale());
            boolean saved_board_read_only = hdlg.is_board_read_only();
            hdlg.set_board_read_only(true);
            boolean ratsnest_hidden_before = hdlg.get_ratsnest().is_hidden();
            if (!ratsnest_hidden_before)
            {
                hdlg.get_ratsnest().hide();
            }
            String start_message = resources.getString("batch_autorouter") + " " + resources.getString("stop_message");
            hdlg.screen_messages.set_status_message(start_message);
            boolean fanout_first =
                    hdlg.settings.autoroute_settings.get_with_fanout() &&
                    hdlg.settings.autoroute_settings.get_pass_no() <= 1;
            if (fanout_first)
            {
                BatchFanout.fanout_board(this);
            }
            if (hdlg.settings.autoroute_settings.get_with_autoroute() && !this.is_stop_requested())
            {
                batch_autorouter.autoroute_passes();
            }
            hdlg.get_routing_board().finish_autoroute();
            if (hdlg.settings.autoroute_settings.get_with_postroute() && !this.is_stop_requested())
            {
                String opt_message = resources.getString("batch_optimizer") + " " + resources.getString("stop_message");
                hdlg.screen_messages.set_status_message(opt_message);
                this.batch_opt_route.optimize_board();
                String curr_message;
                if (this.is_stop_requested())
                {
                    curr_message = resources.getString("interrupted");
                }
                else
                {
                    curr_message = resources.getString("completed");
                }
                String end_message = resources.getString("postroute") + " " + curr_message;
                hdlg.screen_messages.set_status_message(end_message);
            }
            else
            {
                hdlg.screen_messages.clear();
                String curr_message;
                if (this.is_stop_requested())
                {
                    curr_message = resources.getString("interrupted");
                }
                else
                {
                    curr_message = resources.getString("completed");
                }
                Integer incomplete_count = hdlg.get_ratsnest().incomplete_count();
                String end_message = resources.getString("autoroute") + " " + curr_message + ", " + incomplete_count.toString() +
                        " " + resources.getString("connections_not_found");
                hdlg.screen_messages.set_status_message(end_message);
            }

            hdlg.set_board_read_only(saved_board_read_only);
            hdlg.update_ratsnest();
            if (!ratsnest_hidden_before)
            {
                hdlg.get_ratsnest().show();
            }

            hdlg.get_panel().board_frame.refresh_windows();
            if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == board.AngleRestriction.FORTYFIVE_DEGREE && hdlg.get_routing_board().get_test_level() != board.TestLevel.RELEASE_VERSION)
            {
                tests.Validate.multiple_of_45_degree("after autoroute: ", hdlg.get_routing_board());
            }
        } catch (Exception e)
        {

        }
    }

    public void draw(java.awt.Graphics p_graphics)
    {
        FloatLine curr_air_line = batch_autorouter.get_air_line();
        if (curr_air_line != null)
        {
            FloatPoint[] draw_line = new FloatPoint[2];
            draw_line[0] = curr_air_line.a;
            draw_line[1] = curr_air_line.b;
            // draw the incomplete
            java.awt.Color draw_color = this.hdlg.graphics_context.get_incomplete_color();
            double draw_width = Math.min (this.hdlg.get_routing_board().communication.get_resolution(Unit.MIL) * 3, 300);  // problem with low resolution on Kicad300;
            this.hdlg.graphics_context.draw(draw_line, draw_width, draw_color, p_graphics, 1);
        }
        FloatPoint current_opt_position = batch_opt_route.get_current_position();
        int radius = 10 * this.hdlg.get_routing_board().rules.get_default_trace_half_width(0);
        if (current_opt_position != null)
        {
            final int draw_width = 1;
            java.awt.Color draw_color = this.hdlg.graphics_context.get_incomplete_color();
            FloatPoint[] draw_points = new FloatPoint[2];
            draw_points[0] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y - radius);
            draw_points[1] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y + radius);
            this.hdlg.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
            draw_points[0] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y - radius);
            draw_points[1] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y + radius);
            this.hdlg.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
            this.hdlg.graphics_context.draw_circle(current_opt_position, radius, draw_width, draw_color, p_graphics, 1);
        }
    }
    private final BatchAutorouter batch_autorouter;
    private final BatchOptRoute batch_opt_route;
}
