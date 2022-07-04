package app.freerouting.gui;

import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipInputStream;

/** Window with a list for selecting samples in the net. */
public abstract class WindowNetSamples extends BoardSubWindow {

  protected final java.util.ResourceBundle resources;
  protected final java.util.Locale locale;
  protected final javax.swing.JList<SampleDesignListElement> list;
  protected javax.swing.DefaultListModel<SampleDesignListElement> list_model =
      new javax.swing.DefaultListModel<>();

  /** Creates a new instance of WindowNetSampleDesigns */
  public WindowNetSamples(
      java.util.Locale p_locale, String p_title, String p_button_name, int p_row_count) {
    this.locale = p_locale;
    this.resources =
        java.util.ResourceBundle.getBundle("app.freerouting.gui.WindowNetSamples", p_locale);
    this.setTitle(resources.getString(p_title));

    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    // create main panel
    final javax.swing.JPanel main_panel = new javax.swing.JPanel();
    this.add(main_panel);
    main_panel.setLayout(new java.awt.BorderLayout());
    javax.swing.border.Border panel_border =
        javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10);
    main_panel.setBorder(panel_border);

    // create open button
    javax.swing.JButton open_button = new javax.swing.JButton(resources.getString(p_button_name));
    open_button.addActionListener(new OpenListener());
    main_panel.add(open_button, java.awt.BorderLayout.SOUTH);

    // create list with the sample designs
    this.list = new javax.swing.JList<>(this.list_model);
    fill_list();
    this.list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    this.list.setSelectedIndex(0);
    this.list.setVisibleRowCount(p_row_count);
    this.list.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseClicked(java.awt.event.MouseEvent evt) {
            if (evt.getClickCount() > 1) {
              button_pushed();
            }
          }
        });

    javax.swing.JScrollPane list_scroll_pane = new javax.swing.JScrollPane(this.list);
    list_scroll_pane.setPreferredSize(new java.awt.Dimension(200, 20 * p_row_count));
    main_panel.add(list_scroll_pane, java.awt.BorderLayout.CENTER);
    this.pack();
  }

  /**
   * Opens a zipped archive from an URL in the net. Returns a zipped input stream, who is positioned
   * at the start of p_file_name, or null, if an error occured,
   */
  protected static ZipInputStream open_zipped_file(String p_archive_name, String p_file_name) {
    String archive_path_name = MainApplication.WEB_FILE_BASE_NAME + p_archive_name + ".zip";
    URL archive_url = null;
    try {
      archive_url = new URL(archive_path_name);
    } catch (java.net.MalformedURLException e) {
      return null;
    }
    java.io.InputStream input_stream = null;
    ZipInputStream zip_input_stream = null;
    URLConnection net_connection = null;
    try {
      net_connection = archive_url.openConnection();
    } catch (Exception e) {
      return null;
    }
    try {
      input_stream = net_connection.getInputStream();
    } catch (java.io.IOException e) {
      return null;
    } catch (java.security.AccessControlException e) {
      return null;
    }
    try {
      zip_input_stream = new ZipInputStream(input_stream);
    } catch (Exception e) {
      WindowMessage.show("unable to get zip input stream");
      return null;
    }
    String compare_name = p_archive_name + "/" + p_file_name;
    java.util.zip.ZipEntry curr_entry = null;
    for (; ; ) {
      try {
        curr_entry = zip_input_stream.getNextEntry();
      } catch (Exception E) {
        return null;
      }
      if (curr_entry == null) {
        return null;
      }
      String design_name = curr_entry.getName();
      if (design_name.equals(compare_name)) {
        break;
      }
    }
    return zip_input_stream;
  }

  /** Opens a sample design on the website. */
  protected static BoardFrame open_design(
      String p_archive_name,
      String p_design_name,
      java.util.Locale p_locale,
      boolean p_save_intermediate_stages,
      float p_optimization_improvement_threshold) {
    ZipInputStream zip_input_stream = open_zipped_file(p_archive_name, p_design_name);
    if (zip_input_stream == null) {
      return null;
    }
    DesignFile design_file = DesignFile.get_instance("sharc_routed.dsn", true);
    BoardFrame new_frame =
        new BoardFrame(
            design_file,
            BoardFrame.Option.WEBSTART,
            app.freerouting.board.TestLevel.RELEASE_VERSION,
            p_locale,
            false,
            p_save_intermediate_stages,
            p_optimization_improvement_threshold);
    boolean read_ok = new_frame.read(zip_input_stream, true, null);
    if (!read_ok) {
      return null;
    }
    new_frame.setVisible(true);
    return new_frame;
  }

  /** Replays a zipped logfile from an URL in the net. */
  private static void read_zipped_logfile(
      BoardFrame p_board_frame, String p_archive_name, String p_logfile_name) {
    if (p_board_frame == null) {
      return;
    }
    ZipInputStream zip_input_stream = open_zipped_file(p_archive_name, p_logfile_name);
    if (zip_input_stream == null) {
      return;
    }
    p_board_frame.read_logfile(zip_input_stream);
  }

  /** Fill the list with the examples. */
  protected abstract void fill_list();

  /** Action to be perfomed. when the button is pushed after selecting an item in the list. */
  protected abstract void button_pushed();

  /** Adds an element to the list. */
  protected void add(
      String p_design_name, String p_message_name, AdditionalAction p_additional_action) {
    list_model.addElement(
        new SampleDesignListElement(
            p_design_name, resources.getString(p_message_name), p_additional_action));
  }

  /** Adds an element to the list. */
  protected void add(String p_design_name) {
    list_model.addElement(new SampleDesignListElement(p_design_name, "", AdditionalAction.NONE));
  }

  /** Additional Action to be performed after opening the board. */
  protected enum AdditionalAction {
    READ_LOGFILE {
      void perform(BoardFrame p_board_frame, String p_archive_name) {
        String logfile_archive_name = "route_" + p_archive_name;
        read_zipped_logfile(p_board_frame, logfile_archive_name, logfile_archive_name + ".log");
      }
    },

    AUTOROUTE {
      void perform(BoardFrame p_board_frame, String p_archive_name) {
        p_board_frame.board_panel.board_handling.start_batch_autorouter();
      }
    },

    NONE {
      void perform(BoardFrame p_board_frame, String p_archive_name) {}
    };

    abstract void perform(BoardFrame p_board_frame, String p_archive_name);
  }

  /**
   * Structure of the elements in the list For every instance in a String has to be added to the
   * resource file WindowNetSamples or the String in the field message_name.
   */
  protected static class SampleDesignListElement {
    public final String design_name;
    public final WindowNetDemonstrations.AdditionalAction additional_action;
    private final String message_name;
    SampleDesignListElement(
        String p_design_name,
        String p_message_name,
        WindowNetDemonstrations.AdditionalAction p_additional_action) {
      design_name = p_design_name;
      message_name = p_message_name;
      additional_action = p_additional_action;
    }

    public String toString() {
      return message_name;
    }
  }

  private class OpenListener implements java.awt.event.ActionListener {
    public void actionPerformed(java.awt.event.ActionEvent p_evt) {
      button_pushed();
    }
  }
}
