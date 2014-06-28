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
 * MainApplication.java
 *
 * Created on 19. Oktober 2002, 17:58
 *
 */
package gui;

import board.TestLevel;

/**
 *
 * Main application for creating frames with new or existing board designs.
 *
 * @author  Alfons Wirtz
 */
public class MainApplication extends javax.swing.JFrame
{

    /**
     * Main function of the Application
     */
    public static void main(String p_args[])
    {
        boolean single_design_option = false;
        boolean test_version_option = false;
        boolean session_file_option = false;
        boolean webstart_option = false;
        String design_file_name = null;
        String design_dir_name = null;
        java.util.Locale current_locale = java.util.Locale.ENGLISH;
        for (int i = 0; i < p_args.length; ++i)
        {
            if (p_args[i].startsWith("-de"))
            // the design file is provided
            {
                if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
                {
                    single_design_option = true;
                    design_file_name = p_args[i + 1];
                }
            }
            else if (p_args[i].startsWith("-di"))
            // the design directory is provided
            {
                if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
                {
                    design_dir_name = p_args[i + 1];
                }
            }
            else if (p_args[i].startsWith("-l"))
            // the locale is provided
            {
                if (p_args.length > i + 1 && p_args[i + 1].startsWith("d"))
                {
                    current_locale = java.util.Locale.GERMAN;
                }
            }
            else if (p_args[i].startsWith("-s"))
            {
                session_file_option = true;
            }
            else if (p_args[i].startsWith("-w"))
            {
                webstart_option = true;
            }
            else if (p_args[i].startsWith("-test"))
            {
                test_version_option = true;
            }
        }
        if (!(OFFLINE_ALLOWED || webstart_option))
        {
            Runtime.getRuntime().exit(1);
        }

        if (single_design_option)
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("gui.resources.MainApplication", current_locale);
            BoardFrame.Option board_option;
            if (session_file_option)
            {
                board_option = BoardFrame.Option.SESSION_FILE;
            }
            else
            {
                board_option = BoardFrame.Option.SINGLE_FRAME;
            }
            DesignFile design_file = DesignFile.get_instance(design_file_name, false);
            if (design_file == null)
            {
                System.out.print(resources.getString("message_6") + " ");
                System.out.print(design_file_name);
                System.out.println(" " + resources.getString("message_7"));
                return;
            }
            String message = resources.getString("loading_design") + " " + design_file_name;
            WindowMessage welcome_window = WindowMessage.show(message);
            final BoardFrame new_frame =
                    create_board_frame(design_file, null, board_option, test_version_option, current_locale);
            welcome_window.dispose();
            if (new_frame == null)
            {
                Runtime.getRuntime().exit(1);
            }
            new_frame.addWindowListener(new java.awt.event.WindowAdapter()
            {

                public void windowClosed(java.awt.event.WindowEvent evt)
                {
                    Runtime.getRuntime().exit(0);
                }
            });
        }
        else
        {
            new MainApplication(design_dir_name, test_version_option, webstart_option, current_locale).setVisible(true);
        }
    }

    /**
     * Creates new form MainApplication
     * It takes the directory of the board designs as optional argument.
     */
    public MainApplication(String p_design_dir, boolean p_is_test_version,
            boolean p_webstart_option, java.util.Locale p_current_locale)
    {
        this.design_dir_name = p_design_dir;
        this.is_test_version = p_is_test_version;
        this.is_webstart = p_webstart_option;
        this.locale = p_current_locale;
        this.resources =
                java.util.ResourceBundle.getBundle("gui.resources.MainApplication", p_current_locale);
        main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);

        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.insets = new java.awt.Insets(10, 10, 10, 10);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;

        demonstration_button = new javax.swing.JButton();
        sample_board_button = new javax.swing.JButton();
        open_board_button = new javax.swing.JButton();
        restore_defaults_button = new javax.swing.JButton();
        message_field = new javax.swing.JTextField();
        message_field.setText("");
        this.window_net_demonstrations = new WindowNetDemonstrations(p_current_locale);
        java.awt.Point location = getLocation();
        this.window_net_demonstrations.setLocation((int) location.getX() + 50, (int) location.getY() + 50);
        this.window_net_sample_designs = new WindowNetSampleDesigns(p_current_locale);
        this.window_net_sample_designs.setLocation((int) location.getX() + 90, (int) location.getY() + 90);

        setTitle(resources.getString("title"));
        boolean add_buttons = true;

        if (p_webstart_option)
        {

            if (add_buttons)
            {
                demonstration_button.setText(resources.getString("router_demonstrations"));
                demonstration_button.setToolTipText(resources.getString("router_demonstrations_tooltip"));
                demonstration_button.addActionListener(new java.awt.event.ActionListener()
                {

                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        window_net_demonstrations.setVisible(true);
                    }
                });

                gridbag.setConstraints(demonstration_button, gridbag_constraints);
                main_panel.add(demonstration_button, gridbag_constraints);

                sample_board_button.setText(resources.getString("sample_designs"));
                sample_board_button.setToolTipText(resources.getString("sample_designs_tooltip"));
                sample_board_button.addActionListener(new java.awt.event.ActionListener()
                {

                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        window_net_sample_designs.setVisible(true);
                    }
                });

                gridbag.setConstraints(sample_board_button, gridbag_constraints);
                main_panel.add(sample_board_button, gridbag_constraints);
            }
        }

        open_board_button.setText(resources.getString("open_own_design"));
        open_board_button.setToolTipText(resources.getString("open_own_design_tooltip"));
        open_board_button.addActionListener(new java.awt.event.ActionListener()
        {

            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                open_board_design_action(evt);
            }
        });

        gridbag.setConstraints(open_board_button, gridbag_constraints);
        if (add_buttons)
        {
            main_panel.add(open_board_button, gridbag_constraints);
        }

        if (p_webstart_option && add_buttons)
        {
            restore_defaults_button.setText(resources.getString("restore_defaults"));
            restore_defaults_button.setToolTipText(resources.getString("restore_defaults_tooltip"));
            restore_defaults_button.addActionListener(new java.awt.event.ActionListener()
            {

                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    if (is_webstart)
                    {
                        restore_defaults_action(evt);
                    }
                }
            });

            gridbag.setConstraints(restore_defaults_button, gridbag_constraints);
            main_panel.add(restore_defaults_button, gridbag_constraints);
        }

        message_field.setPreferredSize(new java.awt.Dimension(230, 20));
        message_field.setRequestFocusEnabled(false);
        gridbag.setConstraints(message_field, gridbag_constraints);
        main_panel.add(message_field, gridbag_constraints);

        this.addWindowListener(new WindowStateListener());
        pack();
    }

    /** opens a board design from a binary file or a specctra dsn file. */
    private void open_board_design_action(java.awt.event.ActionEvent evt)
    {

        DesignFile design_file = DesignFile.open_dialog(this.is_webstart, this.design_dir_name);

        if (design_file == null)
        {
            message_field.setText(resources.getString("message_3"));
            return;
        }

        BoardFrame.Option option;
        if (this.is_webstart)
        {
            option = BoardFrame.Option.WEBSTART;
        }
        else
        {
            option = BoardFrame.Option.FROM_START_MENU;
        }
        String message = resources.getString("loading_design") + " " + design_file.get_name();
        message_field.setText(message);
        WindowMessage welcome_window = WindowMessage.show(message);
        welcome_window.setTitle(message);
        BoardFrame new_frame =
                create_board_frame(design_file, message_field, option, this.is_test_version, this.locale);
        welcome_window.dispose();
        if (new_frame == null)
        {
            return;
        }
        message_field.setText(resources.getString("message_4") + " " + design_file.get_name() + " " + resources.getString("message_5"));
        board_frames.add(new_frame);
        new_frame.addWindowListener(new BoardFrameWindowListener(new_frame));
    }

    /** Exit the Application */
    private void exitForm(java.awt.event.WindowEvent evt)
    {
        System.exit(0);
    }

    /** deletes the setting stored by the user if the application is run by Java Web Start */
    private void restore_defaults_action(java.awt.event.ActionEvent evt)
    {
        if (!is_webstart)
        {
            return;
        }
        boolean file_deleted = WebStart.delete_files(BoardFrame.GUI_DEFAULTS_FILE_NAME, resources.getString("confirm_delete"));
        if (file_deleted)
        {
            message_field.setText(resources.getString("defaults_restored"));
        }
        else
        {
            message_field.setText(resources.getString("nothing_to_restore"));
        }
    }

    /**
     * Creates a new board frame containing the data of the input design file.
     * Returns null, if an error occured.
     */
    static private BoardFrame create_board_frame(DesignFile p_design_file, javax.swing.JTextField p_message_field,
            BoardFrame.Option p_option, boolean p_is_test_version, java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.MainApplication", p_locale);

        java.io.InputStream input_stream = p_design_file.get_input_stream();
        if (input_stream == null)
        {
            if (p_message_field != null)
            {
                p_message_field.setText(resources.getString("message_8") + " " + p_design_file.get_name());
            }
            return null;
        }

        TestLevel test_level;
        if (p_is_test_version)
        {
            test_level = DEBUG_LEVEL;
        }
        else
        {
            test_level = TestLevel.RELEASE_VERSION;
        }
        BoardFrame new_frame = new BoardFrame(p_design_file, p_option, test_level, p_locale, !p_is_test_version);
        boolean read_ok = new_frame.read(input_stream, p_design_file.is_created_from_text_file(), p_message_field);
        if (!read_ok)
        {
            return null;
        }
        new_frame.menubar.add_design_dependent_items();
        if (p_design_file.is_created_from_text_file())
        {
            // Read the file  with the saved rules, if it is existing.

            String file_name = p_design_file.get_name();
            String[] name_parts = file_name.split("\\.");
            String confirm_import_rules_message = resources.getString("confirm_import_rules");
            DesignFile.read_rules_file(name_parts[0], p_design_file.get_parent(),
                    new_frame.board_panel.board_handling, p_option == BoardFrame.Option.WEBSTART,
                    confirm_import_rules_message);
            new_frame.refresh_windows();
        }
        return new_frame;
    }
    private final java.util.ResourceBundle resources;
    private final javax.swing.JButton demonstration_button;
    private final javax.swing.JButton sample_board_button;
    private final javax.swing.JButton open_board_button;
    private final javax.swing.JButton restore_defaults_button;
    private javax.swing.JTextField message_field;
    private javax.swing.JPanel main_panel;
    /**
     * A Frame with routing demonstrations in the net.
     */
    private final WindowNetSamples window_net_demonstrations;
    /**
     * A Frame with sample board designs in the net.
     */
    private final WindowNetSamples window_net_sample_designs;
    /** The list of open board frames */
    private java.util.Collection<BoardFrame> board_frames = new java.util.LinkedList<BoardFrame>();
    private String design_dir_name = null;
    private final boolean is_test_version;
    private final boolean is_webstart;
    private final java.util.Locale locale;
    private static final TestLevel DEBUG_LEVEL = TestLevel.CRITICAL_DEBUGGING_OUTPUT;

    private class BoardFrameWindowListener extends java.awt.event.WindowAdapter
    {

        public BoardFrameWindowListener(BoardFrame p_board_frame)
        {
            this.board_frame = p_board_frame;
        }

        public void windowClosed(java.awt.event.WindowEvent evt)
        {
            if (board_frame != null)
            {
                // remove this board_frame from the list of board frames
                board_frame.dispose();
                board_frames.remove(board_frame);
                board_frame = null;
            }
        }
        private BoardFrame board_frame;
    }

    private class WindowStateListener extends java.awt.event.WindowAdapter
    {

        public void windowClosing(java.awt.event.WindowEvent evt)
        {
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            boolean exit_program = true;
            if (!is_test_version && board_frames.size() > 0)
            {
                int option = javax.swing.JOptionPane.showConfirmDialog(null, resources.getString("confirm_cancel"),
                        null, javax.swing.JOptionPane.YES_NO_OPTION);
                if (option == javax.swing.JOptionPane.NO_OPTION)
                {
                    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
                    exit_program = false;
                }
            }
            if (exit_program)
            {
                exitForm(evt);
            }
        }

        public void windowIconified(java.awt.event.WindowEvent evt)
        {
            window_net_sample_designs.parent_iconified();
        }

        public void windowDeiconified(java.awt.event.WindowEvent evt)
        {
            window_net_sample_designs.parent_deiconified();
        }
    }
    static final String WEB_FILE_BASE_NAME = "http://www.freerouting.net/java/";
    private static final boolean OFFLINE_ALLOWED = true;
    /**
     * Change this string when creating a new version
     */
    static final String VERSION_NUMBER_STRING = "1.2.43";
}
