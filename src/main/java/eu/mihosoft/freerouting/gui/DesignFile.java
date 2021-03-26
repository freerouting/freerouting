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
 * DesignFile.java
 *
 * Created on 25. Oktober 2006, 07:48
 *
 */
package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.datastructures.FileFilter;
import eu.mihosoft.freerouting.designforms.specctra.RulesFile;
import eu.mihosoft.freerouting.interactive.BoardHandling;
import eu.mihosoft.freerouting.logger.FRLogger;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * File functionality with security restrictions used, when the application is opened with Java Webstart
 *
 * @author Alfons Wirtz
 */
public class DesignFile {

    public static final String[] ALL_FILE_EXTENSIONS = {"bin", "dsn"};
    public static final String[] TEXT_FILE_EXTENSIONS = {"dsn"};
    public static final String BINARY_FILE_EXTENSION = "bin";

    /**
     * Used, if the application is run without Java Web Start.
     */
    private File outputFile;
    private final File inputFile;
    private JFileChooser fileChooser;
    private static final String RULES_FILE_EXTENSION = ".rules";

    public static DesignFile getInstance(String designFileName) {
        if (designFileName == null) {
            return null;
        }
        return new DesignFile(new File(designFileName), null);
    }

    /**
     * Shows a file chooser for opening a design file. If p_is_webstart there are security restrictions because the
     * application was opened with java web start.
     */
    public static DesignFile openDialog(final String pDesignDirName) {
        JFileChooser fileChooser = new JFileChooser(pDesignDirName);
        FileFilter file_filter = new FileFilter(ALL_FILE_EXTENSIONS);
        fileChooser.setFileFilter(file_filter);
        fileChooser.showOpenDialog(null);
        File currDesignFile = fileChooser.getSelectedFile();
        if (currDesignFile == null) {
            return null;
        }
        return new DesignFile(currDesignFile, fileChooser);
    }

    /**
     * Creates a new instance of DesignFile.
     */
    private DesignFile(
            File designFile,
            JFileChooser fileChooser
    ) {
        this.fileChooser = fileChooser;
        this.inputFile = designFile;
        this.outputFile = designFile;
        if (designFile != null) {
            String file_name = designFile.getName();
            String[] nameParts = file_name.split("\\.");
            if (nameParts[nameParts.length - 1].compareToIgnoreCase(BINARY_FILE_EXTENSION) != 0) {
                String binfileName = nameParts[0] + "." + BINARY_FILE_EXTENSION;
                this.outputFile = new File(designFile.getParent(), binfileName);
            }
        }
    }

    /**
     * Gets an InputStream from the file. Returns null, if the algorithm failed.
     */
    public InputStream getInputStream() {
        if (this.inputFile == null) {
            return null;
        }
        try {
            return new FileInputStream(this.inputFile);
        } catch (Exception e) {
            FRLogger.error(e.getLocalizedMessage(), e);
            return null;
        }
    }

    /**
     * Gets the file name as a String. Returns null on failure.
     */
    public String getName() {
        return inputFile != null ?
                inputFile.getName() :
                null;
    }

    public void save_as_dialog(java.awt.Component p_parent, BoardFrame p_board_frame) {
        final java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("eu.mihosoft.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
        String[] file_name_parts = this.getName().split("\\.", 2);
        String design_name = file_name_parts[0];

        if (this.fileChooser == null) {
            String design_dir_name;
            if (this.outputFile == null) {
                design_dir_name = null;
            } else {
                design_dir_name = this.outputFile.getParent();
            }
            this.fileChooser = new javax.swing.JFileChooser(design_dir_name);
            FileFilter file_filter = new FileFilter(ALL_FILE_EXTENSIONS);
            this.fileChooser.setFileFilter(file_filter);
        }

        this.fileChooser.showSaveDialog(p_parent);
        java.io.File new_file = fileChooser.getSelectedFile();
        if (new_file == null) {
            p_board_frame.screen_messages.set_status_message(resources.getString("message_1"));
            return;
        }
        String new_file_name = new_file.getName();
        FRLogger.info("Saving '" + new_file_name + "'...");
        String[] new_name_parts = new_file_name.split("\\.");
        String found_file_extension = new_name_parts[new_name_parts.length - 1];
        if (found_file_extension.compareToIgnoreCase(BINARY_FILE_EXTENSION) == 0) {
            p_board_frame.screen_messages.set_status_message(resources.getString("message_2") + " " + new_file.getName());
            this.outputFile = new_file;
            p_board_frame.save();
        } else {
            if (found_file_extension.compareToIgnoreCase("dsn") != 0) {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_3"));
                return;
            }
            java.io.OutputStream output_stream;
            try {
                output_stream = new java.io.FileOutputStream(new_file);
            } catch (Exception e) {
                output_stream = null;
            }
            if (p_board_frame.boardPanel.boardHandling.exportToDsnFile(output_stream, design_name, false)) {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_4") + " " + new_file_name + " " + resources.getString("message_5"));
            } else {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_6") + " " + new_file_name + " " + resources.getString("message_7"));
            }
        }
    }

    /**
     * Writes a Specctra Session File to update the design file in the host system. Returns false, if the write failed
     */
    public boolean write_specctra_session_file(BoardFrame p_board_frame) {
        final java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("eu.mihosoft.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
        String design_file_name = this.getName();
        String[] file_name_parts = design_file_name.split("\\.", 2);
        String design_name = file_name_parts[0];

        {
            String output_file_name = design_name + ".ses";
            FRLogger.info("Saving '" + output_file_name + "'...");
            java.io.File curr_output_file = new java.io.File(get_parent(), output_file_name);
            java.io.OutputStream output_stream;
            try {
                output_stream = new java.io.FileOutputStream(curr_output_file);
            } catch (Exception e) {
                output_stream = null;
            }

            if (p_board_frame.boardPanel.boardHandling.exportSpecctraSessionFile(design_file_name, output_stream)) {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_11") + " " +
                                                                 output_file_name + " " + resources.getString("message_12"));
            } else {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_13") + " " +
                                                                 output_file_name + " " + resources.getString("message_7"));
                return false;
            }
        }
        if (WindowMessage.confirm(resources.getString("confirm"))) {
            return write_rules_file(design_name, p_board_frame.boardPanel.boardHandling);
        }
        return true;
    }

    /**
     * Saves the board rule to file, so that they can be reused later on.
     */
    private boolean write_rules_file(String p_design_name, eu.mihosoft.freerouting.interactive.BoardHandling p_board_handling) {
        String rules_file_name = p_design_name + RULES_FILE_EXTENSION;
        java.io.OutputStream output_stream;

        FRLogger.info("Saving '" + rules_file_name + "'...");

        java.io.File rules_file = new java.io.File(this.get_parent(), rules_file_name);
        try {
            output_stream = new java.io.FileOutputStream(rules_file);
        } catch (java.io.IOException e) {
            FRLogger.error("unable to create rules file", e);
            return false;
        }

        RulesFile.write(p_board_handling, output_stream, p_design_name);
        return true;
    }

    /**
     *
     * @param designName
     * @param parentName
     * @param rulesFileName
     * @param boardHandling
     * @param confirmMessage
     * @return
     */
    public static boolean readRulesFile(
            final String designName,
            final String parentName,
            final String rulesFileName,
            final BoardHandling boardHandling,
            final String confirmMessage
    ) {
        boolean dsnFileGeneratedByHost = boardHandling.get_routing_board()
                .communication
                .specctra_parser_info
                .dsn_file_generated_by_host;

        if (dsnFileGeneratedByHost &&
            (confirmMessage == null || WindowMessage.confirm(confirmMessage))
        ) {
            FRLogger.info("Opening '" + rulesFileName + "'...");
            return RulesFile.read(parentName, rulesFileName, designName, boardHandling);
        } else {
            return false;
        }
    }

    public void update_eagle(BoardFrame p_board_frame) {
        final java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("eu.mihosoft.freerouting.gui.BoardMenuFile", p_board_frame.get_locale());
        String design_file_name = getName();
        java.io.ByteArrayOutputStream session_output_stream = new java.io.ByteArrayOutputStream();
        if (!p_board_frame.boardPanel.boardHandling.exportSpecctraSessionFile(design_file_name, session_output_stream)) {
            return;
        }
        java.io.InputStream input_stream = new java.io.ByteArrayInputStream(session_output_stream.toByteArray());

        String[] file_name_parts = design_file_name.split("\\.", 2);
        String design_name = file_name_parts[0];
        String output_file_name = design_name + ".scr";
        FRLogger.info("Saving '" + output_file_name + "'...");

        {
            java.io.File curr_output_file = new java.io.File(get_parent(), output_file_name);
            java.io.OutputStream output_stream;
            try {
                output_stream = new java.io.FileOutputStream(curr_output_file);
            } catch (Exception e) {
                output_stream = null;
            }

            if (p_board_frame.boardPanel.boardHandling.exportEagleSessionFile(input_stream, output_stream)) {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_14") + " " + output_file_name + " " + resources.getString("message_15"));
            } else {
                p_board_frame.screen_messages.set_status_message(resources.getString("message_16") + " " + output_file_name + " " + resources.getString("message_7"));
            }
        }
        if (WindowMessage.confirm(resources.getString("confirm"))) {
            write_rules_file(design_name, p_board_frame.boardPanel.boardHandling);
        }
    }

    /**
     * Gets the binary file for saving or null, if the design file is not available because the application is run with
     * Java Web Start.
     */
    public java.io.File get_output_file() {
        return this.outputFile;
    }

    public java.io.File get_input_file() {
        return this.inputFile;
    }

    public String get_parent() {
        if (inputFile != null) {
            return inputFile.getParent();
        }
        return null;
    }

    public java.io.File get_parent_file() {
        if (inputFile != null) {
            return inputFile.getParentFile();
        }
        return null;
    }

    public boolean isCreatedFromTextFile() {
        return this.inputFile != this.outputFile;
    }
}
