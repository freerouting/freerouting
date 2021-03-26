/*
 *   Copyright (C) 2014  Alfons Wirtz
 *   website www.freerouting.net
 *
 *   Copyright (C) 2017 Michael Hoffer <info@michaelhoffer.de>
 *   Website www.freerouting.mihosoft.eu
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
 * BoardMenuBar.java
 *
 * Created on 11. Februar 2005, 10:17
 */

package eu.mihosoft.freerouting.gui;

import javax.swing.*;

/**
 * Creates the menu bar of a board frame together with its menu items.
 *
 * @author Alfons Wirtz
 */
class BoardMenuBar extends JMenuBar {

    private final BoardMenuFile fileMenu;

    private BoardMenuBar(final BoardFrame boardFrame, boolean sessionFileOption) {
        fileMenu = BoardMenuFile.getInstance(boardFrame, sessionFileOption);
        this.add(fileMenu);
    }

    /**
     * Creates a new BoardMenuBar together with its menus
     */
    static BoardMenuBar getInstance(
            final BoardFrame boardFrame,
            final boolean helpSystemUsed,
            final boolean sessionFileOption
    ) {
        final BoardMenuBar menuBar = new BoardMenuBar(boardFrame, sessionFileOption);
        JMenu displayMenu = BoardMenuDisplay.get_instance(boardFrame);
        menuBar.add(displayMenu);
        JMenu parameterMenu = BoardMenuParameter.get_instance(boardFrame);
        menuBar.add(parameterMenu);
        JMenu rulesMenu = BoardMenuRules.get_instance(boardFrame);
        menuBar.add(rulesMenu);
        JMenu infoMenu = BoardMenuInfo.get_instance(boardFrame);
        menuBar.add(infoMenu);
        JMenu otherMenu = BoardMenuOther.get_instance(boardFrame);
        menuBar.add(otherMenu);
        final JMenu helpMenu;
        if (helpSystemUsed) {
            helpMenu = new BoardMenuHelp(boardFrame);
        } else {
            helpMenu = new BoardMenuHelpReduced(boardFrame);
        }
        menuBar.add(helpMenu);
        return menuBar;
    }

    void addDesignDependentItems() {
        fileMenu.addDesignDependentItems();
    }

}
