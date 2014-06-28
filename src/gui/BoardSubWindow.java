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
 * BoardSubwindow.java
 *
 * Created on 20. Juni 2005, 08:02
 *
 */

package gui;

/**
 * Subwindows of the board frame.
 *
 * @author Alfons Wirtz
 */
public class BoardSubWindow extends javax.swing.JFrame
{
  
    public void parent_iconified()
    {
        this.visible_before_iconifying = this.isVisible();
        this.setVisible(false);
    }
    
    public void parent_deiconified()
    {
        this.setVisible(this.visible_before_iconifying);
    }
    
    
    private boolean visible_before_iconifying = false; 
}
