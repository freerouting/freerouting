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
 * BoardTemporarySubWindow.java
 *
 * Created on 20. Juni 2005, 08:19
 *
 */

package gui;

/**
 * Class for temporary subwindows of the boarrd frame
 * @author Alfons Wirtz
 */
public class BoardTemporarySubWindow extends BoardSubWindow
{
    
    /** Creates a new instance of BoardTemporarySubWindow */
    public BoardTemporarySubWindow(BoardFrame p_board_frame)
    {
        this.board_frame = p_board_frame;
        p_board_frame.temporary_subwindows.add(this);
        
        this.addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                dispose();
            }
        });
    }
    
    /** Used,  when the board frame with all the subwindows is disposed. */
    public void board_frame_disposed()
    {
        super.dispose();
    }
    
    public void dispose()
    {
        this.board_frame.temporary_subwindows.remove(this);
        super.dispose();
    }
    
    protected final BoardFrame board_frame;
}
