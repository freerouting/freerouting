package app.freerouting.gui;

import app.freerouting.board.TestLevel;
import app.freerouting.constants.Constants;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.ThreadActionListener;
import app.freerouting.logger.FRLogger;
import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 *
 * Main application for creating frames with new or existing board designs.
 *
 */
public class MainApplication extends javax.swing.JFrame
{
    /**
     * Main function of the Application
     * @param args
     */
    public static void main(String[] args)
    {
        FRLogger.traceEntry("MainApplication.main()");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException ex) {
            FRLogger.error(ex.getLocalizedMessage(), ex);
        } catch (InstantiationException ex) {
            FRLogger.error(ex.getLocalizedMessage(), ex);
        } catch (IllegalAccessException ex) {
            FRLogger.error(ex.getLocalizedMessage(), ex);
        } catch (UnsupportedLookAndFeelException ex) {
            FRLogger.error(ex.getLocalizedMessage(), ex);
        }

        FRLogger.info("Freerouting " + VERSION_NUMBER_STRING);

        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());
        StartupOptions startupOptions = StartupOptions.parse(args);

        if (startupOptions.show_help_option)
        {
            FRLogger.info("" +
                    "-de [design input file]: loads up a Specctra .dsn file at startup\n" +
                    "-di [design input directory]: if the GUI is used, this sets the default folder for the open design dialogs\n" +
                    "-dr [design rules file]: reads the rules from a previously saved .rules file\n" +
                    "-do [design output file]: saves a Specctra board (.dsn), a Specctra session file (.ses) or Eagle session script file (.scr) when the routing is finished\n" +
                    "-mp [number of passes]: sets the upper limit of the number of auto-router passes that will be performed\n" +
                    "-l [language]: \"de\" for German, \"zh\" for Simplified Chinese, otherwise it's English\n" +
                    "-mt [number of threads]: sets thread pool size for route optimization. The default is 32.\n" +
                    "-us [greedy | global | hybrid]: sets board updating strategy for route optimization: greedy, global optimal or hybrid. The default is greedy. When hybrid is selected, another option \"hr\" specifies hybrid ratio.\n" +
                    "-hr [m:n]: sets hybrid ratio in the format of #_global_optiomal_passes:#_prioritized_passes. The default is 1:1. It's only effective when hybrid strategy is selected.\n" +
                    "-is [sequential | random | prioritized]: sets item selection strategy for route optimization: sequential, random, prioritized. The default is prioritized. Prioritied stragegy selects items based on scores calculated in previous round.\n" +
                    "-h: shows help" +
                    "");

            System.exit(0);
            return;
        }

        if (startupOptions.single_design_option)
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("app.freerouting.gui.MainApplication", startupOptions.current_locale);
            BoardFrame.Option board_option;
            if (startupOptions.session_file_option)
            {
                board_option = BoardFrame.Option.SESSION_FILE;
            }
            else
            {
                board_option = BoardFrame.Option.SINGLE_FRAME;
            }

            FRLogger.info("Opening '"+startupOptions.design_input_filename+"'...");
            DesignFile design_file = DesignFile.get_instance(startupOptions.design_input_filename, false);
            if (design_file == null)
            {
                FRLogger.warn(resources.getString("message_6") + " " +  startupOptions.design_input_filename + " " + resources.getString("message_7"));
                return;
            }
            String message = resources.getString("loading_design") + " "
                    + startupOptions.design_input_filename;
            WindowMessage welcome_window = WindowMessage.show(message);
            final BoardFrame new_frame =
                    create_board_frame(design_file, null, board_option,
                            startupOptions.test_version_option, 
                            startupOptions.current_locale,
                            startupOptions.design_rules_filename);
            welcome_window.dispose();
            if (new_frame == null)
            {
                FRLogger.warn("Couldn't create window frame");
                System.exit(1);
                return;
            }

            new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no() + startupOptions.max_passes - 1);
            new_frame.board_panel.board_handling.set_num_threads(startupOptions.num_threads);
            new_frame.board_panel.board_handling.set_board_update_strategy(startupOptions.board_update_strategy);
            new_frame.board_panel.board_handling.set_hybrid_ratio(startupOptions.hybrid_ratio);
            new_frame.board_panel.board_handling.set_item_selection_strategy(startupOptions.item_selection_strategy);

            // start the auto-router automatically if both input and output files were passed as a parameter
            if ((startupOptions.design_input_filename != null) && (startupOptions.design_output_filename != null))
            {
                InteractiveActionThread thread = new_frame.board_panel.board_handling.start_batch_autorouter();

                thread.addListener(new ThreadActionListener() {
                    @Override
                    public void autorouterStarted() {
                    }

                    @Override
                    public void autorouterAborted() {
                        ExportBoardToFile(startupOptions.design_output_filename);
                    }

                    @Override
                    public void autorouterFinished() {
                        ExportBoardToFile(startupOptions.design_output_filename);
                    }

                    private void ExportBoardToFile(String filename) {
                        if ((filename != null)
                                && ((filename.toLowerCase().endsWith(".dsn"))
                                || (filename.toLowerCase().endsWith(".ses"))
                                || (filename.toLowerCase().endsWith(".scr")))) {

                            FRLogger.info("Saving '" + filename + "'...");
                            try {
                                String filename_only = new File(filename).getName();
                                String design_name = filename_only.substring(0, filename_only.length() - 4);

                                java.io.OutputStream output_stream = new java.io.FileOutputStream(filename);

                                if (filename.toLowerCase().endsWith(".dsn")) {
                                    new_frame.board_panel.board_handling.export_to_dsn_file(output_stream, design_name, false);
                                } else if (filename.toLowerCase().endsWith(".ses")) {
                                    new_frame.board_panel.board_handling.export_specctra_session_file(design_name, output_stream);
                                } else if (filename.toLowerCase().endsWith(".scr")) {
                                    java.io.ByteArrayOutputStream session_output_stream = new ByteArrayOutputStream();
                                    new_frame.board_panel.board_handling.export_specctra_session_file(filename, session_output_stream);
                                    java.io.InputStream input_stream = new ByteArrayInputStream(session_output_stream.toByteArray());
                                    new_frame.board_panel.board_handling.export_eagle_session_file(input_stream, output_stream);
                                }

                                Runtime.getRuntime().exit(0);
                            } catch (Exception e) {
                                FRLogger.error("Couldn't export board to file", e);
                            }
                        } else {
                            FRLogger.warn("Couldn't export board to '" + filename + "'.");
                        }
                    }
                });
            }

            new_frame.addWindowListener(new java.awt.event.WindowAdapter()
            {
                @Override
                public void windowClosed(java.awt.event.WindowEvent evt)
                {
                    Runtime.getRuntime().exit(0);
                }
            });
        }
        else
        {
            new MainApplication(startupOptions).setVisible(true);
        }

        FRLogger.traceExit("MainApplication.main()");
    }

    /**
     * Creates new form MainApplication
     * It takes the directory of the board designs as optional argument.
     * @param startupOptions
     */
    public MainApplication(StartupOptions startupOptions)
    {
        this.design_dir_name = startupOptions.getDesignDir();
        this.max_passes = startupOptions.getMaxPasses();
        this.num_threads = startupOptions.getNumThreads();
        this.board_update_strategy = startupOptions.getBoardUpdateStrategy();
        this.hybrid_ratio = startupOptions.getHybridRatio();
        this.item_selection_strategy = startupOptions.getItemSelectionStrategy();
        this.is_test_version = startupOptions.isTestVersion();
        this.is_webstart = startupOptions.getWebstartOption();
        this.locale = startupOptions.getCurrentLocale();
        this.resources =
                java.util.ResourceBundle.getBundle("app.freerouting.gui.MainApplication", locale);
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
        message_field.setText(resources.getString("command_line_missing_input"));
        this.window_net_demonstrations = new WindowNetDemonstrations(locale);
        java.awt.Point location = getLocation();
        this.window_net_demonstrations.setLocation((int) location.getX() + 50, (int) location.getY() + 50);
        this.window_net_sample_designs = new WindowNetSampleDesigns(locale);
        this.window_net_sample_designs.setLocation((int) location.getX() + 90, (int) location.getY() + 90);

        setTitle(resources.getString("title") + " " + VERSION_NUMBER_STRING);
        boolean add_buttons = true;

        if (startupOptions.getWebstartOption())
        {

            if (add_buttons)
            {
                demonstration_button.setText(resources.getString("router_demonstrations"));
                demonstration_button.setToolTipText(resources.getString("router_demonstrations_tooltip"));
                demonstration_button.addActionListener((java.awt.event.ActionEvent evt) -> {
                    window_net_demonstrations.setVisible(true);
                });

                gridbag.setConstraints(demonstration_button, gridbag_constraints);
                main_panel.add(demonstration_button, gridbag_constraints);

                sample_board_button.setText(resources.getString("sample_designs"));
                sample_board_button.setToolTipText(resources.getString("sample_designs_tooltip"));
                sample_board_button.addActionListener((java.awt.event.ActionEvent evt) -> {
                    window_net_sample_designs.setVisible(true);
                });

                gridbag.setConstraints(sample_board_button, gridbag_constraints);
                main_panel.add(sample_board_button, gridbag_constraints);
            }
        }

        open_board_button.setText(resources.getString("open_own_design"));
        open_board_button.setToolTipText(resources.getString("open_own_design_tooltip"));
        open_board_button.addActionListener((java.awt.event.ActionEvent evt) -> {
            open_board_design_action(evt);
        });

        gridbag.setConstraints(open_board_button, gridbag_constraints);
        if (add_buttons)
        {
            main_panel.add(open_board_button, gridbag_constraints);
        }

        if (startupOptions.getWebstartOption() && add_buttons)
        {
            restore_defaults_button.setText(resources.getString("restore_defaults"));
            restore_defaults_button.setToolTipText(resources.getString("restore_defaults_tooltip"));
            restore_defaults_button.addActionListener((java.awt.event.ActionEvent evt) -> {
                if (is_webstart)
                {
                    restore_defaults_action(evt);
                }
            });

            gridbag.setConstraints(restore_defaults_button, gridbag_constraints);
            main_panel.add(restore_defaults_button, gridbag_constraints);
        }

        message_field.setPreferredSize(new java.awt.Dimension(400, 20));
        message_field.setRequestFocusEnabled(false);
        gridbag.setConstraints(message_field, gridbag_constraints);
        main_panel.add(message_field, gridbag_constraints);

        this.addWindowListener(new WindowStateListener());
        pack();
        setSize(620,300);
    }

    /** opens a board design from a binary file or a specctra dsn file. */
    private void open_board_design_action(java.awt.event.ActionEvent evt)
    {
        DesignFile design_file = DesignFile.open_dialog(this.design_dir_name);

        if (design_file == null)
        {
            message_field.setText(resources.getString("message_3"));
            return;
        }

        FRLogger.info("Opening '"+design_file.get_name()+"'...");

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
                create_board_frame(design_file, message_field, option, this.is_test_version, this.locale, null);
        welcome_window.dispose();
        if (new_frame == null)
        {
            return;
        }
        
        new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no() + this.max_passes - 1);
        new_frame.board_panel.board_handling.set_num_threads(this.num_threads);
        new_frame.board_panel.board_handling.set_board_update_strategy(this.board_update_strategy);
        new_frame.board_panel.board_handling.set_hybrid_ratio(this.hybrid_ratio);
        new_frame.board_panel.board_handling.set_item_selection_strategy(this.item_selection_strategy);
        
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
        // webstart is gone, nothing to do
        // TODO maybe add alternative
    }

    /**
     * Creates a new board frame containing the data of the input design file.
     * Returns null, if an error occurred.
     */
    static private BoardFrame create_board_frame(DesignFile p_design_file, javax.swing.JTextField p_message_field,
            BoardFrame.Option p_option, boolean p_is_test_version, java.util.Locale p_locale, String p_design_rules_file)
    {
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("app.freerouting.gui.MainApplication", p_locale);

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

            String design_name = name_parts[0];

            String parent_folder_name = null;
            String rules_file_name = null;
            String confirm_import_rules_message = null;
            if (p_design_rules_file == null) {
                parent_folder_name = p_design_file.get_parent();
                rules_file_name = design_name + ".rules";
                confirm_import_rules_message = resources.getString("confirm_import_rules");
            } else {
                rules_file_name = p_design_rules_file;
            }

            DesignFile.read_rules_file(design_name, parent_folder_name, rules_file_name,
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
    private final javax.swing.JTextField message_field;
    private final javax.swing.JPanel main_panel;
    /**
     * A Frame with routing demonstrations in the net.
     */
    private final WindowNetSamples window_net_demonstrations;
    /**
     * A Frame with sample board designs in the net.
     */
    private final WindowNetSamples window_net_sample_designs;
    /** The list of open board frames */
    private final java.util.Collection<BoardFrame> board_frames 
            = new java.util.LinkedList<>();
    private String design_dir_name = null;
    
    private int max_passes;
    private BoardUpdateStrategy board_update_strategy; 
    private String hybrid_ratio;
    private ItemSelectionStrategy item_selection_strategy;
    private int num_threads;  
    // Issue: adding a new field into AutorouteSettings caused exception when loading 
    // an existing design: "Couldn't read design file", "InvalidClassException", incompatible with serialized data
    // so choose to pass this parameter through BoardHandling 
    
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

        @Override
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

        @Override
        public void windowClosing(java.awt.event.WindowEvent evt)
        {
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            boolean exit_program = true;
            if (!is_test_version && board_frames.size() > 0)
            {
                int option = javax.swing.JOptionPane.showConfirmDialog(null,
                        resources.getString("confirm_cancel"),
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

        @Override
        public void windowIconified(java.awt.event.WindowEvent evt)
        {
            window_net_sample_designs.parent_iconified();
        }

        @Override
        public void windowDeiconified(java.awt.event.WindowEvent evt)
        {
            window_net_sample_designs.parent_deiconified();
        }
    }
    static final String WEB_FILE_BASE_NAME = "http://www.freerouting.app";

    static final String VERSION_NUMBER_STRING = 
        "v" + Constants.FREEROUTING_VERSION
            + " (build-date: "
            + Constants.FREEROUTING_BUILD_DATE +")";
}
