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
 * MainApplication.java
 *
 * Created on 19. Oktober 2002, 17:58
 *
 */
package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.logger.FRLogger;

import javax.swing.*;

/**
 * Main application entry point
 *
 * @author Alfons Wirtz
 */
public class MainApplication {

    public static void main(String[] args) {
        FRLogger.traceEntry("MainApplication.main()");
        setNativeLookAndFeel();
        FRLogger.info("Freerouting application is started.");
        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

        final StartupOptions startupOptions = new StartupOptions(args);
        if (startupOptions.isSingleDesignOption()) {
            MainGUI.startApplicationForSingleFileOnly(startupOptions);
        } else {
            new MainGUI(startupOptions).setVisible(true);
        }

        FRLogger.traceExit("MainApplication.main()");
    }

    private static void setNativeLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            FRLogger.error(ex.getLocalizedMessage(), ex);
        }
    }

}
