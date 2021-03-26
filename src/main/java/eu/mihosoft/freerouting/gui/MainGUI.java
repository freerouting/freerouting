package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.board.TestLevel;
import eu.mihosoft.freerouting.interactive.ThreadActionListener;
import eu.mihosoft.freerouting.logger.FRLogger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.ResourceBundle;

import static eu.mihosoft.freerouting.FreeRouting.VERSION_NUMBER_STRING;
import static java.util.ResourceBundle.getBundle;

/**
 * Main application for creating frames with new or existing board designs.
 */
public class MainGUI extends JFrame {

    private final ResourceBundle resources;
    private final JTextField messageField;
    private final JPanel mainPanel;
    private final String designDirName;
    private final boolean isTestVersion;
    private final Locale locale;
    /**
     * The list of open board frames
     */
    private final Collection<BoardFrame> boardFrames = new LinkedList<>();
    /**
     * A Frame with routing demonstrations in the net.
     */
    private WindowNetSamples windowNetDemonstrations;
    /**
     * A Frame with sample board designs in the net.
     */
    private WindowNetSamples windowNetSampleDesigns;

    private static final TestLevel DEBUG_LEVEL = TestLevel.CRITICAL_DEBUGGING_OUTPUT;

    /**
     * Creates new form MainApplication.
     * <p>
     * It takes the directory of the board designs as optional argument.
     *
     * @param startupOptions
     */
    public MainGUI(final StartupOptions startupOptions) {
        designDirName = startupOptions.getDesignDir();
        isTestVersion = startupOptions.isTestVersion();
        locale = startupOptions.getCurrentLocale();
        resources = getBundle("eu.mihosoft.freerouting.gui.MainApplication", locale);

        mainPanel = new JPanel();
        final GridBagLayout gridbag = new GridBagLayout();
        mainPanel.setLayout(gridbag);
        getContentPane().add(mainPanel);

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(10, 10, 10, 10);
        gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;

        setTitle(resources.getString("title") + " " + VERSION_NUMBER_STRING);

        final JButton openBoardButton = new JButton();
        openBoardButton.setText(resources.getString("open_own_design"));
        openBoardButton.setToolTipText(resources.getString("open_own_design_tooltip"));
        openBoardButton.addActionListener(this::openBoardDesignAction);
        gridbag.setConstraints(openBoardButton, gridBagConstraints);
        mainPanel.add(openBoardButton, gridBagConstraints);

        messageField = new JTextField();
        messageField.setText("Neither '-de <design file>' nor '-di <design directory>' are specified.");
        messageField.setPreferredSize(new Dimension(480, 40));
        messageField.setRequestFocusEnabled(false);
        gridbag.setConstraints(messageField, gridBagConstraints);
        mainPanel.add(messageField, gridBagConstraints);

        if (startupOptions.getDemoOption()) {
            addDemoButtons(gridbag, gridBagConstraints);
        }

        this.addWindowListener(new WindowStateListener());
        pack();
        setSize(620, 300);
    }

    private void addDemoButtons(final GridBagLayout gridbag, final GridBagConstraints gridBagConstraints) {
        final JButton demonstrationButton = new JButton();
        final JButton sampleBoardButton = new JButton();

        windowNetDemonstrations = new WindowNetDemonstrations(locale);
        final Point location = getLocation();
        windowNetDemonstrations.setLocation((int) location.getX() + 50, (int) location.getY() + 50);

        windowNetSampleDesigns = new WindowNetSampleDesigns(locale);
        windowNetSampleDesigns.setLocation((int) location.getX() + 90, (int) location.getY() + 90);

        demonstrationButton.setText(resources.getString("router_demonstrations"));
        demonstrationButton.setToolTipText(resources.getString("router_demonstrations_tooltip"));
        demonstrationButton.addActionListener((java.awt.event.ActionEvent evt) -> {
            windowNetDemonstrations.setVisible(true);
        });

        gridbag.setConstraints(demonstrationButton, gridBagConstraints);
        mainPanel.add(demonstrationButton, gridBagConstraints);

        sampleBoardButton.setText(resources.getString("sample_designs"));
        sampleBoardButton.setToolTipText(resources.getString("sample_designs_tooltip"));
        sampleBoardButton.addActionListener((java.awt.event.ActionEvent evt) -> {
            windowNetSampleDesigns.setVisible(true);
        });

        gridbag.setConstraints(sampleBoardButton, gridBagConstraints);
        mainPanel.add(sampleBoardButton, gridBagConstraints);
    }

    /**
     * opens a board design from a binary file or a specctra dsn file.
     */
    private void openBoardDesignAction(ActionEvent evt) {
        DesignFile designFile = DesignFile.openDialog(designDirName);

        if (designFile == null) {
            messageField.setText(resources.getString("message_3"));
            return;
        }
        FRLogger.info("Opening '" + designFile.getName() + "'...");
        BoardFrame.Option option;
//        if (this.isWebstart) { //todo check thi logic branch
//            option = BoardFrame.Option.WEBSTART;
//        } else {
        option = BoardFrame.Option.FROM_START_MENU;
//        }
        String message = resources.getString("loading_design") + " " + designFile.getName();
        messageField.setText(message);
        WindowMessage welcomeWindow = WindowMessage.show(message);
        welcomeWindow.setTitle(message);
        BoardFrame newFrame = createBoardFrame(
                designFile,
                messageField,
                option,
                isTestVersion,
                locale,
                null
        );
        welcomeWindow.dispose();
        if (newFrame == null) {
            return;
        }
        messageField.setText(resources.getString("message_4") + " " + designFile.getName() + " " + resources.getString("message_5"));
        boardFrames.add(newFrame);
        newFrame.addWindowListener(new BoardFrameWindowListener(newFrame));
    }

    /**
     * Exit the Application
     */
    private void exitForm(WindowEvent evt) {
        System.exit(0);
    }

    /**
     * Creates a new board frame containing the data of the input design file. Returns null, if an error occurred.
     */
    private static BoardFrame createBoardFrame(
            final DesignFile designFile,
            final JTextField messageField,
            final BoardFrame.Option option,
            final boolean isTestVersion,
            final Locale locale,
            final String designRulesFile
    ) {
        final ResourceBundle resources = getBundle("eu.mihosoft.freerouting.gui.MainApplication", locale);

        final InputStream inputStream = designFile.getInputStream();
        if (inputStream == null) {
            if (messageField != null) {
                messageField.setText(resources.getString("message_8") + " " + designFile.getName());
            }
            return null;
        }

        final TestLevel testLevel;
        if (isTestVersion) {
            testLevel = DEBUG_LEVEL;
        } else {
            testLevel = TestLevel.RELEASE_VERSION;
        }

        BoardFrame newFrame = new BoardFrame(designFile, option, testLevel, locale, !isTestVersion);

        boolean createdFromTextFile = designFile.isCreatedFromTextFile();

        boolean readOk = newFrame.read(inputStream, createdFromTextFile, messageField);
        if (!readOk) {
            return null;
        }
        newFrame.getMenubar().addDesignDependentItems();

        if (createdFromTextFile) {
            // Read the file  with the saved rules, if it is existing.

            final String designFileName = designFile.getName();
            final String[] nameParts = designFileName.split("\\.");
            final String designName = nameParts[0];

            String parentFolderName = null;
            final String rulesFileName;
            String confirmImportRulesMessage = null;

            if (designRulesFile == null) {
                parentFolderName = designFile.get_parent();
                rulesFileName = designName + ".rules";
                confirmImportRulesMessage = resources.getString("confirm_import_rules");
            } else {
                rulesFileName = designRulesFile;
            }

            DesignFile.readRulesFile(
                    designName,
                    parentFolderName,
                    rulesFileName,
                    newFrame.getBoardPanel().getBoardHandling(),
                    confirmImportRulesMessage
            );

            newFrame.refreshWindows();
        }
        return newFrame;
    }


    private class BoardFrameWindowListener extends WindowAdapter {

        private BoardFrame boardFrame;

        public BoardFrameWindowListener(BoardFrame boardFrame) {
            this.boardFrame = boardFrame;
        }

        @Override
        public void windowClosed(WindowEvent evt) {
            if (boardFrame != null) {
                // remove this board_frame from the list of board frames
                boardFrame.dispose();
                boardFrames.remove(boardFrame);
                boardFrame = null;
            }
        }
    }

    private class WindowStateListener extends WindowAdapter {

        @Override
        public void windowClosing(WindowEvent evt) {
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            boolean exitProgram = true;
            if (!isTestVersion && !boardFrames.isEmpty()) {
                int option = JOptionPane.showConfirmDialog(
                        null,
                        resources.getString("confirm_cancel"),
                        null,
                        JOptionPane.YES_NO_OPTION
                );
                if (option == JOptionPane.NO_OPTION) {
                    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
                    exitProgram = false;
                }
            }
            if (exitProgram) {
                exitForm(evt);
            }
        }

        @Override
        public void windowIconified(WindowEvent evt) {
            windowNetSampleDesigns.parent_iconified();
        }

        @Override
        public void windowDeiconified(WindowEvent evt) {
            windowNetSampleDesigns.parent_deiconified();
        }
    }


    /**
     * Starts application if command line has option -de, and -do
     * <p>
     * design input filename must also be.
     * <p>
     * design output filename must also be.
     * <p>
     * Constructs all necessary frames, boards and buttons to process SINGLE design file.
     * <p>
     * After it finished, it writes result into a file - only if -mp [max passes] number presented in command line
     * options and -mp is less than 99999
     *
     * @param startupOptions
     */
    public static void startApplicationForSingleFileOnly(final StartupOptions startupOptions) {
        final ResourceBundle resources = getBundle("eu.mihosoft.freerouting.gui.MainApplication", startupOptions.getCurrentLocale());

        final String designOutputFilename = startupOptions.getDesignOutputFilename();
        if (designOutputFilename == null ||
            !(designOutputFilename.toLowerCase().endsWith(".dsn") ||
              designOutputFilename.toLowerCase().endsWith(".ses") ||
              designOutputFilename.toLowerCase().endsWith(".scr"))
        ) {
            FRLogger.warn(resources.getString("message_6") + " "
                          + designOutputFilename + " "
                          + resources.getString("message_7")
            );
            FRLogger.warn("Job can't be started because at the end of a very long job there couldn't export board to '" + designOutputFilename + "'.");
            return;
        }

        final BoardFrame.Option boardOption = startupOptions.isSessionFileOption() ?
                BoardFrame.Option.SESSION_FILE :
                BoardFrame.Option.SINGLE_FRAME;

        FRLogger.info("Opening '" + startupOptions.getDesignInputFilename() + "'...");

        final DesignFile designFile = DesignFile.getInstance(startupOptions.getDesignInputFilename());

        if (designFile == null) {
            FRLogger.warn(resources.getString("message_6") + " "
                          + startupOptions.getDesignInputFilename() + " "
                          + resources.getString("message_7")
            );
            return;
        }
        final String message = resources.getString("loading_design") + " " + startupOptions.getDesignInputFilename();

        final WindowMessage welcomeWindow = WindowMessage.show(message);

        final BoardFrame newFrame = MainGUI.createBoardFrame(
                designFile,
                null,
                boardOption,
                startupOptions.isTestVersion(),
                startupOptions.getCurrentLocale(),
                startupOptions.getDesignRulesFilename()
        );
        welcomeWindow.dispose();
        if (newFrame == null) {
            FRLogger.warn("Couldn't create window frame");
            System.exit(1);
            return;
        }

        newFrame.getBoardPanel().
                getBoardHandling().
                getSettings().
                getAutorouteSettings().
                setStopPassNo(
                        newFrame.getBoardPanel().
                                getBoardHandling().
                                getSettings().
                                getAutorouteSettings().getStartPassNo() + startupOptions.getMaxPasses() - 1
                );

        if (startupOptions.getMaxPasses() < 99999) {
            newFrame.getBoardPanel().
                    getBoardHandling().
                    startBatchAutorouterWithListener(new ThreadActionListener() {
                        @Override
                        public void autorouterStarted() {
                            FRLogger.info("Processing '" + startupOptions.getDesignInputFilename() + "' started...");
                        }

                        @Override
                        public void autorouterAborted() {
                            exportBoardToFile(designOutputFilename);
                        }

                        @Override
                        public void autorouterFinished() {
                            exportBoardToFile(designOutputFilename);
                        }

                        private void exportBoardToFile(final String filename) {
                            FRLogger.info("Saving job results to '" + filename + "'...");

                            try (final OutputStream fileOutputStream = new FileOutputStream(filename)) {
                                final String filenameOnly = new File(filename).getName();
                                final String designName = filenameOnly.substring(0, filenameOnly.length() - 4);

                                if (filename.toLowerCase().endsWith(".dsn")) {

                                    newFrame.getBoardPanel().
                                            getBoardHandling().
                                            exportToDsnFile(fileOutputStream, designName, false);

                                } else if (filename.toLowerCase().endsWith(".ses")) {

                                    newFrame.getBoardPanel().
                                            getBoardHandling().
                                            exportSpecctraSessionFile(designName, fileOutputStream);

                                } else if (filename.toLowerCase().endsWith(".scr")) {

                                    ByteArrayOutputStream sessionOutputStream = new ByteArrayOutputStream();
                                    newFrame.getBoardPanel().
                                            getBoardHandling().
                                            exportSpecctraSessionFile(filename, sessionOutputStream);

                                    InputStream inputStream = new ByteArrayInputStream(sessionOutputStream.toByteArray());
                                    newFrame.getBoardPanel().
                                            getBoardHandling().
                                            exportEagleSessionFile(inputStream, fileOutputStream);
                                }
                                FRLogger.info("Job is done.");
                                exitGoodFromRuntime();
                            } catch (Exception e) {
                                FRLogger.error("Couldn't export board to file", e);
                            }
                        }
                    });
        } else {
            FRLogger.info("Number of passes are too much for processing in auto mode");
        }

        newFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
                exitGoodFromRuntime();
            }
        });
    }

    private static void exitGoodFromRuntime() {
        Runtime.getRuntime().exit(0);
    }

}
