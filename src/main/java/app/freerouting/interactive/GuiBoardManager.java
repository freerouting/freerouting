package app.freerouting.interactive;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.board.*;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.designforms.specctra.SessionToEagle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.gui.BoardPanel;
import app.freerouting.gui.ComboBoxLayer;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.management.TextManager;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.GlobalSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the routing board operations with a graphical user interface.
 * This class extends the functionality of HeadlessBoardManager by adding GUI-specific interactions and logic, enabling users to visually interact with the routing board and perform tasks such as auto-routing, item selection, and more.
 */
public class GuiBoardManager extends HeadlessBoardManager
{

  // The interval in milliseconds between two repaints of the board panel (sets the effective frame rate)
  private static final long repaint_interval = 1000;
  // The time of the last repaint of the board panel
  private static long last_repainted_time = 0;
  /**
   * The text message fields displayed on the screen
   */
  public final ScreenMessages screen_messages;
  /**
   * The graphical panel used for displaying the board.
   */
  private final BoardPanel panel;
  private final TextManager tm;
  private final List<Consumer<Boolean>> readOnlyEventListeners = new ArrayList<>();
  private final GlobalSettings globalSettings;
  /**
   * The graphical context for drawing the board.
   */
  public GraphicsContext graphics_context;
  /**
   * For transforming coordinates between the user and the board coordinate space
   */
  public CoordinateTransform coordinate_transform;
  /**
   * The currently active interactive state.
   */
  InteractiveState interactive_state;
  /**
   * To repaint the board immediately for example when reading a logfile.
   */
  boolean paint_immediately = false;
  /**
   * thread pool size
   */
  private int num_threads;
  private BoardUpdateStrategy board_update_strategy;
  private String hybrid_ratio;
  private ItemSelectionStrategy item_selection_strategy;
  /**
   * Used for running an interactive action in a separate thread.
   */
  private InteractiveActionThread interactive_action_thread;
  /**
   * To display all incomplete connections on the screen.
   */
  private RatsNest ratsnest;
  /**
   * To display all clearance violations between items on the screen.
   */
  private ClearanceViolations clearance_violations;
  /**
   * True if currently a logfile is being processed. Used to prevent interactive changes of the
   * board database in this case.
   */
  private boolean board_is_read_only = false;
  /**
   * The current position of the mouse pointer.
   */
  private FloatPoint current_mouse_position;

  /**
   * Creates a new BoardHandling
   */
  public GuiBoardManager(BoardPanel p_panel, GlobalSettings globalSettings, RoutingJob routingJob)
  {
    super(globalSettings.currentLocale, routingJob);
    this.globalSettings = globalSettings;
    this.panel = p_panel;
    this.screen_messages = p_panel.screen_messages;
    this.set_interactive_state(RouteMenuState.get_instance(this, activityReplayFile));

    this.tm = new TextManager(this.getClass(), globalSettings.currentLocale);

    LogEntries.LogEntryAddedListener listener = this::logEntryAdded;
    FRLogger
        .getLogEntries()
        .addLogEntryAddedListener(listener);
  }

  private void logEntryAdded(LogEntry logEntry)
  {
    if ((logEntry.getType() == LogEntryType.Error) || (logEntry.getType() == LogEntryType.Warning))
    {
      LogEntries entries = FRLogger.getLogEntries();
      screen_messages.set_error_and_warning_count(entries.getErrorCount(), entries.getWarningCount());
    }
  }

  /**
   * Return true, if the board is set to read only.
   */
  public boolean is_board_read_only()
  {
    return this.board_is_read_only;
  }

  /**
   * Sets the board to read only for example when running a separate action thread to avoid
   * unsynchronized change of the board.
   */
  public void set_board_read_only(boolean p_value)
  {
    this.board_is_read_only = p_value;
    this.settings.set_read_only(p_value);

    // Raise an event to notify the observers that the board read only property changed
    this.readOnlyEventListeners.forEach(listener -> listener.accept(p_value));
  }

  /**
   * Return the current language for the GUI messages.
   */
  @Override
  public Locale get_locale()
  {
    return this.locale;
  }

  /**
   * returns the number of layers of the board design.
   */
  public int get_layer_count()
  {
    if (board == null)
    {
      return 0;
    }
    return board.get_layer_count();
  }

  /**
   * Returns the current position of the mouse pointer.
   */
  public FloatPoint get_current_mouse_position()
  {
    return this.current_mouse_position;
  }

  /**
   * Sets the current mouse position to the input point. Used while reading a logfile.
   */
  void set_current_mouse_position(FloatPoint p_point)
  {
    this.current_mouse_position = p_point;
  }

  /**
   * Tells the router, if conduction areas should be ignored.
   */
  public void set_ignore_conduction(boolean p_value)
  {
    if (board_is_read_only)
    {
      return;
    }
    board.change_conduction_is_obstacle(!p_value);

    activityReplayFile.start_scope(ActivityReplayFileScope.SET_IGNORE_CONDUCTION, p_value);
  }

  public void set_pin_edge_to_turn_dist(double p_value)
  {
    if (board_is_read_only)
    {
      return;
    }
    double edge_to_turn_dist = this.coordinate_transform.user_to_board(p_value);
    if (edge_to_turn_dist != board.rules.get_pin_edge_to_turn_dist())
    {
      // unfix the pin exit stubs
      Collection<Pin> pin_list = board.get_pins();
      for (Pin curr_pin : pin_list)
      {
        if (curr_pin.has_trace_exit_restrictions())
        {
          Collection<Item> contact_list = curr_pin.get_normal_contacts();
          for (Item curr_contact : contact_list)
          {
            if ((curr_contact instanceof PolylineTrace) && curr_contact.get_fixed_state() == FixedState.SHOVE_FIXED)
            {
              if (((PolylineTrace) curr_contact).corner_count() == 2)
              {
                curr_contact.set_fixed_state(FixedState.NOT_FIXED);
              }
            }
          }
        }
      }
    }
    board.rules.set_pin_edge_to_turn_dist(edge_to_turn_dist);
  }

  /**
   * Changes the visibility of the input layer to the input value. p_value is expected between 0 and
   * 1
   */
  public void set_layer_visibility(int p_layer, double p_value)
  {
    if (p_layer >= 0 && p_layer < graphics_context.layer_count())
    {
      graphics_context.set_layer_visibility(p_layer, p_value);
      if (p_value == 0 && settings.layer == p_layer)
      {
        // change the current layer to the best visible layer, if it becomes invisible;
        double best_visibility = 0;
        int best_visible_layer = 0;
        for (int i = 0; i < graphics_context.layer_count(); ++i)
        {
          if (graphics_context.get_layer_visibility(i) > best_visibility)
          {
            best_visibility = graphics_context.get_layer_visibility(i);
            best_visible_layer = i;
          }
        }
        settings.layer = best_visible_layer;
      }
    }
  }

  /**
   * Gets the trace half width used in interactive routing for the input net on the input layer.
   */
  public int get_trace_halfwidth(int p_net_no, int p_layer)
  {
    int result;
    if (settings.manual_rule_selection)
    {
      result = settings.manual_trace_half_width_arr[p_layer];
    }
    else
    {
      result = board.rules.get_trace_half_width(p_net_no, p_layer);
    }
    return result;
  }

  /**
   * Returns if p_layer is active for interactive routing of traces.
   */
  public boolean is_active_routing_layer(int p_net_no, int p_layer)
  {
    if (settings.manual_rule_selection)
    {
      return true;
    }
    Net curr_net = this.board.rules.nets.get(p_net_no);
    if (curr_net == null)
    {
      return true;
    }
    NetClass curr_net_class = curr_net.get_class();
    if (curr_net_class == null)
    {
      return true;
    }
    return curr_net_class.is_active_routing_layer(p_layer);
  }

  /**
   * Gets the trace clearance class used in interactive routing.
   */
  public int get_trace_clearance_class(int p_net_no)
  {
    int result;
    if (settings.manual_rule_selection)
    {
      result = settings.manual_trace_clearance_class;
    }
    else
    {
      result = board.rules.nets
          .get(p_net_no)
          .get_class()
          .get_trace_clearance_class();
    }
    return result;
  }

  /**
   * Gets the via rule used in interactive routing.
   */
  public ViaRule get_via_rule(int p_net_no)
  {
    ViaRule result = null;
    if (settings.manual_rule_selection)
    {
      result = board.rules.via_rules.get(this.settings.manual_via_rule_index);
    }
    if (result == null)
    {
      result = board.rules.nets
          .get(p_net_no)
          .get_class()
          .get_via_rule();
    }
    return result;
  }

  /**
   * Changes the default trace halfwidth currently used in interactive routing on the input layer.
   */
  public void set_default_trace_halfwidth(int p_layer, int p_value)
  {
    if (board_is_read_only)
    {
      return;
    }
    if (p_layer >= 0 && p_layer <= board.get_layer_count())
    {
      board.rules.set_default_trace_half_width(p_layer, p_value);
      activityReplayFile.start_scope(ActivityReplayFileScope.SET_TRACE_HALF_WIDTH, p_layer);
      activityReplayFile.add_int(p_value);
    }
  }

  /**
   * Switches clearance compensation on or off.
   */
  public void set_clearance_compensation(boolean p_value)
  {
    if (board_is_read_only)
    {
      return;
    }
    board.search_tree_manager.set_clearance_compensation_used(p_value);
    activityReplayFile.start_scope(ActivityReplayFileScope.SET_CLEARANCE_COMPENSATION, p_value);
  }

  /**
   * Changes the current snap angle in the interactive board handling.
   */
  public void set_current_snap_angle(AngleRestriction p_snap_angle)
  {
    if (board_is_read_only)
    {
      return;
    }
    board.rules.set_trace_angle_restriction(p_snap_angle);
    activityReplayFile.start_scope(ActivityReplayFileScope.SET_SNAP_ANGLE, p_snap_angle.getValue());
  }

  /**
   * Changes the current layer in the interactive board handling.
   */
  public void set_current_layer(int p_layer)
  {
    if (board_is_read_only)
    {
      return;
    }
    int layer = Math.max(p_layer, 0);
    layer = Math.min(layer, board.get_layer_count() - 1);
    set_layer(layer);
    activityReplayFile.start_scope(ActivityReplayFileScope.SET_LAYER, p_layer);
  }

  /**
   * Changes the current layer without saving the change to logfile. Only for internal use inside
   * this package.
   */
  void set_layer(int p_layer_no)
  {
    Layer curr_layer = board.layer_structure.arr[p_layer_no];
    screen_messages.set_layer(curr_layer.name);
    settings.layer = p_layer_no;

    // Change the selected layer in the select parameter window.
    if ((!this.board_is_read_only) && (curr_layer.is_signal))
    {
      this.panel.set_selected_signal_layer(p_layer_no);
    }

    // make the layer visible, if it is invisible
    if (graphics_context.get_layer_visibility(p_layer_no) == 0)
    {
      graphics_context.set_layer_visibility(p_layer_no, 1);
      panel.board_frame.refresh_windows();
    }
    graphics_context.set_fully_visible_layer(p_layer_no);
    repaint();
  }

  /**
   * Displays the current layer in the layer message field, and clears the field for the additional
   * message.
   */
  public void display_layer_message()
  {
    screen_messages.clear_add_field();
    Layer curr_layer = board.layer_structure.arr[this.settings.layer];
    screen_messages.set_layer(curr_layer.name);
  }

  /**
   * Sets the manual trace half width used in interactive routing. If p_layer_no {@literal <} 0, the
   * manual trace half width is changed on all layers.
   */
  public void set_manual_trace_half_width(int p_layer_no, int p_value)
  {
    if (p_layer_no == ComboBoxLayer.ALL_LAYER_INDEX)
    {
      for (int i = 0; i < settings.manual_trace_half_width_arr.length; ++i)
      {
        this.settings.set_manual_trace_half_width(i, p_value);
      }
    }
    else if (p_layer_no == ComboBoxLayer.INNER_LAYER_INDEX)
    {
      for (int i = 1; i < settings.manual_trace_half_width_arr.length - 1; ++i)
      {
        this.settings.set_manual_trace_half_width(i, p_value);
      }
    }
    else
    {
      this.settings.set_manual_trace_half_width(p_layer_no, p_value);
    }
  }

  /**
   * Changes the interactive selectability of p_item_type.
   */
  public void set_selectable(ItemSelectionFilter.SelectableChoices p_item_type, boolean p_value)
  {
    settings.set_selectable(p_item_type, p_value);
    if (!p_value && this.interactive_state instanceof SelectedItemState)
    {
      set_interactive_state(((SelectedItemState) interactive_state).filter());
    }
  }

  /**
   * Displays all incomplete connections, if they are not visible, or hides them, if they are
   * visible.
   */
  public void toggle_ratsnest()
  {
    if (ratsnest == null || ratsnest.is_hidden())
    {
      create_ratsnest();
    }
    else
    {
      ratsnest = null;
    }
    repaint();
  }

  public void toggle_clearance_violations()
  {
    if (clearance_violations == null)
    {
      clearance_violations = new ClearanceViolations(this.board.get_items());
      Integer violation_count = (clearance_violations.list.size() + 1) / 2;
      String curr_message = violation_count + " " + tm.getText("clearance_violations_found");
      screen_messages.set_status_message(curr_message);
    }
    else
    {
      clearance_violations = null;
      screen_messages.set_status_message("");
    }
    repaint();
  }

  /**
   * Displays all incomplete connections.
   */
  public void create_ratsnest()
  {
    ratsnest = new RatsNest(this.board);
    Integer incomplete_count = ratsnest.incomplete_count();
    int length_violation_count = ratsnest.length_violation_count();
    String curr_message;
    if (length_violation_count == 0)
    {
      curr_message = incomplete_count + " " + tm.getText("incomplete_connections_to_route");
    }
    else
    {
      curr_message = incomplete_count + " " + tm.getText("incompletes") + " " + length_violation_count + " " + tm.getText("length_violations");
    }
    screen_messages.set_status_message(curr_message);
  }

  /**
   * Recalculates the incomplete connections for the input net.
   */
  void update_ratsnest(int p_net_no)
  {
    if (ratsnest != null && p_net_no > 0)
    {
      ratsnest.recalculate(p_net_no, this.board);
      ratsnest.show();
    }
  }

  /**
   * Recalculates the incomplete connections for the input net for the items in p_item_list.
   */
  void update_ratsnest(int p_net_no, Collection<Item> p_item_list)
  {
    if (ratsnest != null && p_net_no > 0)
    {
      ratsnest.recalculate(p_net_no, p_item_list, this.board);
      ratsnest.show();
    }
  }

  /**
   * Recalculates the incomplete connections, if the ratsnest is active.
   */
  void update_ratsnest()
  {
    if (ratsnest != null)
    {
      ratsnest = new RatsNest(this.board);
    }
  }

  /**
   * Hides the incomplete connections on the screen.
   */
  public void hide_ratsnest()
  {
    if (ratsnest != null)
    {
      ratsnest.hide();
    }
  }

  /**
   * Shows the incomplete connections on the screen, if the ratsnest is active.
   */
  public void show_ratsnest()
  {
    if (ratsnest != null)
    {
      ratsnest.show();
    }
  }

  /**
   * Removes the incomplete connections.
   */
  public void remove_ratsnest()
  {
    // TODO: test these two versions combined with get_ratsnest() method

    // Version A
    ratsnest = null;

    // Version B
    // do nothing as we create a new instance of ratsnest every time
  }

  /**
   * Returns the ratsnest with the information about the incomplete connections.
   */
  public RatsNest get_ratsnest()
  {
    // TODO: test these two versions combined with remove_ratsnest() method

    // Version A
    if (ratsnest == null)
    {
      ratsnest = new RatsNest(this.board);
    }
    return this.ratsnest;

    // Version B
    // return new RatsNest(this.board);
  }

  public void recalculate_length_violations()
  {
    if (this.ratsnest != null)
    {
      if (this.ratsnest.recalculate_length_violations())
      {
        if (!this.ratsnest.is_hidden())
        {
          this.repaint();
        }
      }
    }
  }

  /**
   * Sets the visibility filter for the incompletes of the input net.
   */
  public void set_incompletes_filter(int p_net_no, boolean p_value)
  {
    if (ratsnest != null)
    {
      ratsnest.set_filter(p_net_no, p_value);
    }
  }

  /**
   * Creates the routing board, the graphic context and the interactive settings.
   */
  @Override
  public void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name, BoardRules p_rules, Communication p_board_communication)
  {
    super.create_board(p_bounding_box, p_layer_structure, p_outline_shapes, p_outline_clearance_class_name, p_rules, p_board_communication);

    // create the interactive/GUI settings with default values
    double unit_factor = p_board_communication.coordinate_transform.board_to_dsn(1);
    this.coordinate_transform = new CoordinateTransform(1, p_board_communication.unit, unit_factor, p_board_communication.unit);

    // create a graphics context for the board
    Dimension panel_size = panel.getPreferredSize();
    graphics_context = new GraphicsContext(p_bounding_box, panel_size, p_layer_structure, this.locale);
  }

  /**
   * Changes the user unit.
   */
  public void change_user_unit(Unit p_unit)
  {
    screen_messages.set_unit_label(p_unit.toString());
    CoordinateTransform old_transform = this.coordinate_transform;
    this.coordinate_transform = new CoordinateTransform(old_transform.user_unit_factor, p_unit, old_transform.board_unit_factor, old_transform.board_unit);
  }

  /**
   * From here on the interactive actions are written to a logfile.
   */
  public void start_logfile(File p_filename)
  {
    if (board_is_read_only)
    {
      return;
    }
    activityReplayFile.start_write(p_filename);
  }

  /**
   * Repaints the board panel on the screen.
   */
  public void repaint()
  {
    if (this.paint_immediately)
    {
      final Rectangle MAX_RECTAMGLE = new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
      panel.paintImmediately(MAX_RECTAMGLE);
    }
    else
    {
      if (last_repainted_time < System.currentTimeMillis() - repaint_interval)
      {
        last_repainted_time = System.currentTimeMillis();

        panel.repaint();
      }
    }
  }

  /**
   * Repaints a rectangle of board panel on the screen.
   */
  public void repaint(Rectangle p_rect)
  {
    if (this.paint_immediately)
    {
      panel.paintImmediately(p_rect);
    }
    else
    {
      panel.repaint(p_rect);
    }
  }

  /**
   * Gets the panel for graphical display of the board.
   */
  public BoardPanel get_panel()
  {
    return this.panel;
  }

  /**
   * Gets the popup menu used in the current interactive state. Returns null, if the current state
   * uses no popup menu.
   */
  public JPopupMenu get_current_popup_menu()
  {
    JPopupMenu result;
    if (interactive_state != null)
    {
      result = interactive_state.get_popup_menu();
    }
    else
    {
      result = null;
    }
    return result;
  }

  /**
   * Draws the board and all temporary construction graphics in the current interactive state.
   */
  public void draw(Graphics p_graphics)
  {
    if (board == null)
    {
      return;
    }
    board.draw(p_graphics, graphics_context);

    if (ratsnest != null)
    {
      ratsnest.draw(p_graphics, graphics_context);
    }
    if (clearance_violations != null)
    {
      clearance_violations.draw(p_graphics, graphics_context);
    }
    if (interactive_state != null)
    {
      interactive_state.draw(p_graphics);
    }
    if (interactive_action_thread != null)
    {
      interactive_action_thread.draw(p_graphics);
    }
  }

  public void generate_snapshot()
  {
    if (board_is_read_only)
    {
      return;
    }
    board.generate_snapshot();
    activityReplayFile.start_scope(ActivityReplayFileScope.GENERATE_SNAPSHOT);
  }

  /**
   * Restores the situation before the previous snapshot.
   */
  public void undo()
  {
    if (board_is_read_only || !(interactive_state instanceof MenuState))
    {
      return;
    }
    Set<Integer> changed_nets = new TreeSet<>();
    if (board.undo(changed_nets))
    {
      for (Integer changed_net : changed_nets)
      {
        this.update_ratsnest(changed_net);
      }
      if (!changed_nets.isEmpty())
      {
        // reset the start pass number in the autorouter in case
        // a batch autorouter is undone.
        this.settings.autoroute_settings.set_start_pass_no(1);
      }
      screen_messages.set_status_message(tm.getText("undo"));
    }
    else
    {
      screen_messages.set_status_message(tm.getText("no_more_undo_possible"));
    }
    activityReplayFile.start_scope(ActivityReplayFileScope.UNDO);
    repaint();
  }

  /**
   * Restores the situation before the last undo.
   */
  public void redo()
  {
    if (board_is_read_only || !(interactive_state instanceof MenuState))
    {
      return;
    }
    Set<Integer> changed_nets = new TreeSet<>();
    if (board.redo(changed_nets))
    {
      for (Integer changed_net : changed_nets)
      {
        this.update_ratsnest(changed_net);
      }
      screen_messages.set_status_message(tm.getText("redo"));
    }
    else
    {
      screen_messages.set_status_message(tm.getText("no_more_redo_possible"));
    }
    activityReplayFile.start_scope(ActivityReplayFileScope.REDO);
    repaint();
  }

  /**
   * Actions to be taken in the current interactive state when the left mouse button is clicked.
   */
  public void left_button_clicked(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // We are currently busy working on the board and the user clicked on the canvas with the left mouse button.
      this.stop_autorouter_and_route_optimizer();
      return;
    }
    if (interactive_state != null && graphics_context != null)
    {
      FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
      InteractiveState return_state = interactive_state.left_button_clicked(location);
      if (return_state != interactive_state && return_state != null)
      {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Actions to be taken in the current interactive state when the mouse pointer has moved.
   */
  public void mouse_moved(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    if (interactive_state != null && graphics_context != null)
    {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);
      InteractiveState return_state = interactive_state.mouse_moved();
      Set<Item> hover_item = pick_items(this.current_mouse_position);
      if (hover_item.size() == 1)
      {
        String hover_info = hover_item
            .iterator()
            .next()
            .get_hover_info(locale);
        this.panel.setToolTipText(hover_info);
      }
      else
      {
        this.panel.setToolTipText(null);
      }
      // An automatic repaint here would slow down the display
      // performance in interactive route.
      // If a repaint is necessary, it should be done in the individual mouse_moved
      // method of the class derived from InteractiveState
      if (return_state != this.interactive_state)
      {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Actions to be taken when the mouse button is pressed.
   */
  public void mouse_pressed(Point2D p_point)
  {
    if (interactive_state != null && graphics_context != null)
    {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);
      set_interactive_state(interactive_state.mouse_pressed(this.current_mouse_position));
    }
  }

  /**
   * Actions to be taken in the current interactive state when the mouse is dragged.
   */
  public void mouse_dragged(Point2D p_point)
  {
    if (interactive_state != null && graphics_context != null)
    {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);
      InteractiveState return_state = interactive_state.mouse_dragged(this.current_mouse_position);
      if (return_state != interactive_state)
      {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Actions to be taken in the current interactive state when a mouse button is released.
   */
  public void button_released()
  {
    if (interactive_state != null)
    {
      InteractiveState return_state = interactive_state.button_released();
      if (return_state != interactive_state)
      {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Actions to be taken in the current interactive state when the mouse wheel is moved
   */
  public void mouse_wheel_moved(int p_rotation)
  {
    if (interactive_state != null)
    {
      InteractiveState return_state = interactive_state.mouse_wheel_moved(p_rotation);
      if (return_state != interactive_state)
      {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Action to be taken in the current interactive state when a key on the keyboard is typed.
   */
  public void key_typed_action(char p_key_char)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    InteractiveState return_state = interactive_state.key_typed(p_key_char);
    if (return_state != null && return_state != interactive_state)
    {
      set_interactive_state(return_state);
      panel.board_frame.setToolbarModeSelectionPanelValue(get_interactive_state());
      repaint();
    }
  }

  /**
   * Completes the current interactive state and returns to its return state.
   */
  public void return_from_state()
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }

    InteractiveState new_state = interactive_state.complete();
    {
      if (new_state != interactive_state)
      {
        set_interactive_state(new_state);
        repaint();
      }
    }
  }

  /**
   * Cancels the current interactive state.
   */
  public void cancel_state()
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }

    InteractiveState new_state = interactive_state.cancel();
    {
      if (new_state != interactive_state)
      {
        set_interactive_state(new_state);
        repaint();
      }
    }
  }

  /**
   * Actions to be taken in the current interactive state when the current board layer is changed.
   * Returns false, if the layer change failed.
   */
  public boolean change_layer_action(int p_new_layer)
  {
    boolean result = true;
    if (interactive_state != null && !board_is_read_only)
    {
      result = interactive_state.change_layer_action(p_new_layer);
    }
    return result;
  }

  /**
   * Sets the interactive state to SelectMenuState
   */
  public void set_select_menu_state()
  {
    this.interactive_state = SelectMenuState.get_instance(this, activityReplayFile);
    screen_messages.set_status_message(tm.getText("select_menu"));
  }

  /**
   * Sets the interactive state to RouteMenuState
   */
  public void set_route_menu_state()
  {
    this.interactive_state = RouteMenuState.get_instance(this, activityReplayFile);
    screen_messages.set_status_message(tm.getText("route_menu"));
  }

  /**
   * Sets the interactive state to DragMenuState
   */
  public void set_drag_menu_state()
  {
    this.interactive_state = DragMenuState.get_instance(this, activityReplayFile);
    screen_messages.set_status_message(tm.getText("drag_menu"));
  }

  public boolean isBoardChanged()
  {
    return calculateCrc32() != originalBoardChecksum;
  }

  /**
   * Reads an existing board design from the input stream. Returns false, if the input stream does
   * not contain a legal board design.
   */
  public boolean loadFromBinary(ObjectInputStream p_design)
  {
    try
    {
      board = (RoutingBoard) p_design.readObject();
      settings = (Settings) p_design.readObject();
      settings.set_logfile(this.activityReplayFile);
      coordinate_transform = (CoordinateTransform) p_design.readObject();
      graphics_context = (GraphicsContext) p_design.readObject();
      originalBoardChecksum = calculateCrc32();
    } catch (Exception e)
    {
      routingJob.logError("Couldn't read design file", e);
      return false;
    }
    screen_messages.set_layer(board.layer_structure.arr[settings.layer].name);
    return true;
  }

  /**
   * Writes the currently edited board design to a text file in the Specctra DSN format. If
   * p_compat_mode is true, only standard specctra dsn scopes are written, so that any host system
   * with a specctra interface can read them.
   */
  public boolean saveAsSpecctraDesignDsn(OutputStream p_output_stream, String p_design_name, boolean p_compat_mode)
  {
    if (board_is_read_only || p_output_stream == null)
    {
      return false;
    }

    boolean wasSaveSuccessful = DsnFile.write(this, p_output_stream, p_design_name, p_compat_mode);

    if (wasSaveSuccessful)
    {
      originalBoardChecksum = calculateCrc32();
    }

    return wasSaveSuccessful;
  }

  /**
   * Writes a .SES session file in the Specctra ses-format.
   */
  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName)
  {
    if (board_is_read_only)
    {
      return false;
    }

    return super.saveAsSpecctraSessionSes(outputStream, designName);
  }

  /**
   * Writes a session file in the Eagle SCR format.
   */
  public boolean saveSpecctraSessionSesAsEagleScriptScr(InputStream p_input_stream, OutputStream p_output_stream)
  {
    if (board_is_read_only)
    {
      return false;
    }
    return SessionToEagle.get_instance(p_input_stream, p_output_stream, this.board);
  }

  @Override
  public DsnFile.ReadResult loadFromSpecctraDsn(InputStream inputStream, BoardObservers boardObservers, IdentificationNumberGenerator identificationNumberGenerator)
  {
    var result = super.loadFromSpecctraDsn(inputStream, boardObservers, identificationNumberGenerator);
    this.set_layer(0);
    return result;
  }

  /**
   * Saves the currently edited board design to p_design_file.
   */
  public boolean saveAsBinary(ObjectOutputStream p_object_stream)
  {
    boolean result = true;
    try
    {
      p_object_stream.writeObject(board);
      p_object_stream.writeObject(settings);
      p_object_stream.writeObject(coordinate_transform);
      p_object_stream.writeObject(graphics_context);

      originalBoardChecksum = calculateCrc32();
    } catch (Exception e)
    {
      screen_messages.set_status_message(tm.getText("save_error"));
      result = false;
    }
    return result;
  }

  /**
   * Processes the actions stored in the input logfile.
   */
  public void read_logfile(InputStream p_input_stream)
  {
    if (board_is_read_only || !(interactive_state instanceof MenuState))
    {
      return;
    }
    this.interactive_action_thread = InteractiveActionThread.get_read_logfile_instance(this, routingJob, p_input_stream);
    this.interactive_action_thread.start();
  }

  /**
   * Closes all currently used files so that the file buffers are written to disk.
   */
  public void close_files()
  {
    activityReplayFile.close_output();
  }

  /**
   * Starts interactive routing at the input location.
   */
  public void start_route(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState new_state = RouteState.get_instance(location, this.interactive_state, this, activityReplayFile);
    set_interactive_state(new_state);
  }

  /**
   * Selects board items at the input location.
   */
  public void select_items(Point2D p_point)
  {
    if (board_is_read_only || !(this.interactive_state instanceof MenuState))
    {
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState return_state = ((MenuState) interactive_state).select_items(location);
    set_interactive_state(return_state);
  }

  /**
   * Selects all items in an interactive defined rectangle.
   */
  public void select_items_in_region()
  {
    if (board_is_read_only || !(this.interactive_state instanceof MenuState))
    {
      return;
    }
    set_interactive_state(SelectItemsInRegionState.get_instance(this.interactive_state, this, activityReplayFile));
  }

  /**
   * Selects all items in the input collection.
   */
  public void select_items(Set<Item> p_items)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    this.display_layer_message();
    if (interactive_state instanceof MenuState)
    {
      set_interactive_state(SelectedItemState.get_instance(p_items, interactive_state, this, activityReplayFile));
    }
    else if (interactive_state instanceof SelectedItemState)
    {
      ((SelectedItemState) interactive_state)
          .get_item_list()
          .addAll(p_items);
      repaint();
    }
  }

  /**
   * Looks for a swappable pin at p_location. Prepares for pin swap if a swappable pin was found.
   */
  public void swap_pin(Point2D p_location)
  {
    if (board_is_read_only || !(this.interactive_state instanceof MenuState))
    {
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_location);
    InteractiveState return_state = ((MenuState) interactive_state).swap_pin(location);
    set_interactive_state(return_state);
  }

  /**
   * Displays a window containing all selected items.
   */
  public void zoom_selection()
  {
    if (!(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    IntBox bounding_box = this.board.get_bounding_box(((SelectedItemState) interactive_state).get_item_list());
    bounding_box = bounding_box.offset(this.board.rules.get_max_trace_half_width());
    Point2D lower_left = this.graphics_context.coordinate_transform.board_to_screen(bounding_box.ll.to_float());
    Point2D upper_right = this.graphics_context.coordinate_transform.board_to_screen(bounding_box.ur.to_float());
    this.panel.zoom_frame(lower_left, upper_right);
  }

  /**
   * Picks item at p_point. Removes it from the selected_items list, if it is already in there,
   * otherwise adds it to the list. Changes to the selected items state, if something was selected.
   */
  public void toggle_select_action(Point2D p_point)
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState return_state = ((SelectedItemState) interactive_state).toggle_select(location);
    if (return_state != this.interactive_state)
    {
      set_interactive_state(return_state);
      repaint();
    }
  }

  /**
   * Fixes the selected items.
   */
  public void fix_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    ((SelectedItemState) interactive_state).fix_items();
  }

  /**
   * Unfixes the selected items.
   */
  public void unfix_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    ((SelectedItemState) interactive_state).unfix_items();
  }

  /**
   * Displays information about the selected item into a graphical text window.
   */
  public void display_selected_item_info()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    ((SelectedItemState) interactive_state).info();
  }

  /**
   * Makes all selected items connectable and assigns them to a new net.
   */
  public void assign_selected_to_new_net()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    InteractiveState new_state = ((SelectedItemState) interactive_state).assign_items_to_new_net();
    set_interactive_state(new_state);
  }

  /**
   * Assigns all selected items to a new group ( new component for example)
   */
  public void assign_selected_to_new_group()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    InteractiveState new_state = ((SelectedItemState) interactive_state).assign_items_to_new_group();
    set_interactive_state(new_state);
  }

  /**
   * Deletes all unfixed selected items.
   */
  public void delete_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    InteractiveState new_state = ((SelectedItemState) interactive_state).delete_items();
    set_interactive_state(new_state);
  }

  /**
   * Deletes all unfixed selected traces and vias inside a rectangle.
   */
  public void cutout_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    InteractiveState new_state = ((SelectedItemState) interactive_state).cutout_items();
    set_interactive_state(new_state);
  }

  /**
   * Assigns the input clearance class to the selected items
   */
  public void assign_clearance_classs_to_selected_items(int p_cl_class_index)
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    InteractiveState new_state = ((SelectedItemState) interactive_state).assign_clearance_class(p_cl_class_index);
    set_interactive_state(new_state);
  }

  /**
   * Moves or rotates the selected items
   */
  public void move_selected_items(Point2D p_from_location)
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState curr_state))
    {
      return;
    }
    Collection<Item> item_list = curr_state.get_item_list();
    FloatPoint from_location = graphics_context.coordinate_transform.screen_to_board(p_from_location);
    InteractiveState new_state = MoveItemState.get_instance(from_location, item_list, interactive_state, this, activityReplayFile);
    set_interactive_state(new_state);
    repaint();
  }

  /**
   * Copies all selected items.
   */
  public void copy_selected_items(Point2D p_from_location)
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState curr_state))
    {
      return;
    }
    curr_state.extent_to_whole_components();
    Collection<Item> item_list = curr_state.get_item_list();
    FloatPoint from_location = graphics_context.coordinate_transform.screen_to_board(p_from_location);
    InteractiveState new_state = CopyItemState.get_instance(from_location, item_list, interactive_state.return_state, this, activityReplayFile);
    set_interactive_state(new_state);
  }

  /**
   * Optimizes the selected items.
   */
  public void optimize_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    board.generate_snapshot();
    this.interactive_action_thread = InteractiveActionThread.get_pull_tight_instance(this, routingJob);
    this.interactive_action_thread.start();
  }

  /**
   * Autoroute the selected items.
   */
  public void autoroute_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    board.generate_snapshot();
    this.interactive_action_thread = InteractiveActionThread.get_autoroute_instance(this, routingJob);
    this.interactive_action_thread.start();
  }

  /**
   * Fanouts the selected items.
   */
  public void fanout_selected_items()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    board.generate_snapshot();
    this.interactive_action_thread = InteractiveActionThread.get_fanout_instance(this, routingJob);
    this.interactive_action_thread.start();
  }

  /**
   * Start the auto-router and route optimizer on the whole board
   */
  public InteractiveActionThread start_autorouter_and_route_optimizer(RoutingJob job)
  {
    // The auto-router and route optimizer can only be started if the board is not read only
    if (board_is_read_only)
    {
      return null;
    }

    // Generate a snapshot of the board before starting the autorouter
    board.generate_snapshot();

    // Start the auto-router and route optimizer
    // TODO: ideally we should only pass the board and the routerSettings to the thread, and let the thread create the router and optimizer
    this.interactive_action_thread = InteractiveActionThread.get_autorouter_and_route_optimizer_instance(this, job);
    this.interactive_action_thread.start();

    return this.interactive_action_thread;
  }

  /**
   * Stops the auto-router and route optimizer
   */
  public void stop_autorouter_and_route_optimizer()
  {
    if (this.interactive_action_thread != null)
    {
      // The left button is used to stop the interactive action thread.
      this.interactive_action_thread.requestStop();
    }

    this.set_board_read_only(false);
  }

  /**
   * Selects also all items belonging to a net of a currently selected item.
   */
  public void extend_selection_to_whole_nets()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    set_interactive_state(((SelectedItemState) interactive_state).extent_to_whole_nets());
  }

  /**
   * Selects also all items belonging to a component of a currently selected item.
   */
  public void extend_selection_to_whole_components()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    set_interactive_state(((SelectedItemState) interactive_state).extent_to_whole_components());
  }

  /**
   * Selects also all items belonging to a connected set of a currently selected item.
   */
  public void extend_selection_to_whole_connected_sets()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    set_interactive_state(((SelectedItemState) interactive_state).extent_to_whole_connected_sets());
  }

  /**
   * Selects also all items belonging to a connection of a currently selected item.
   */
  public void extend_selection_to_whole_connections()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    set_interactive_state(((SelectedItemState) interactive_state).extent_to_whole_connections());
  }

  /**
   * Shows or hides the clearance violations of the selected items.
   */
  public void toggle_selected_item_violations()
  {
    if (board_is_read_only || !(interactive_state instanceof SelectedItemState))
    {
      return;
    }
    ((SelectedItemState) interactive_state).toggle_clearance_violations();
  }

  public void turn_45_degree(int p_factor)
  {
    if (board_is_read_only || !(interactive_state instanceof MoveItemState))
    {
      // no interactive action when logfile is running
      return;
    }
    ((MoveItemState) interactive_state).turn_45_degree(p_factor);
  }

  public void change_placement_side()
  {
    if (board_is_read_only || !(interactive_state instanceof MoveItemState))
    {
      // no interactive action when logfile is running
      return;
    }
    ((MoveItemState) interactive_state).change_placement_side();
  }

  /**
   * Zooms display to an interactive defined rectangle.
   */
  public void zoom_region()
  {
    interactive_state = ZoomRegionState.get_instance(this.interactive_state, this, this.activityReplayFile);
  }

  /**
   * Start interactively creating a circle obstacle.
   */
  public void start_circle(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    set_interactive_state(CircleConstructionState.get_instance(location, this.interactive_state, this, activityReplayFile));
  }

  /**
   * Start interactively creating a tile shaped obstacle.
   */
  public void start_tile(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    set_interactive_state(TileConstructionState.get_instance(location, this.interactive_state, this, activityReplayFile));
  }

  /**
   * Start interactively creating a polygon shaped obstacle.
   */
  public void start_polygonshape_item(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    set_interactive_state(PolygonShapeConstructionState.get_instance(location, this.interactive_state, this, activityReplayFile));
  }

  /**
   * Actions to be taken, when adding a hole to an existing obstacle shape on the board is started.
   */
  public void start_adding_hole(Point2D p_point)
  {
    if (board_is_read_only)
    {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState new_state = HoleConstructionState.get_instance(location, this.interactive_state, this, activityReplayFile);
    set_interactive_state(new_state);
  }

  /**
   * Gets a surrounding rectangle of the area, where an update of the graphics is needed caused by
   * the previous interactive actions.
   */
  Rectangle get_graphics_update_rectangle()
  {
    Rectangle result;
    IntBox update_box = board.get_graphics_update_box();
    if (update_box == null || update_box.is_empty())
    {
      result = new Rectangle(0, 0, 0, 0);
    }
    else
    {
      IntBox offset_box = update_box.offset(board.get_max_trace_half_width());
      result = graphics_context.coordinate_transform.board_to_screen(offset_box);
    }
    return result;
  }

  /**
   * Gets all items at p_location on the active board layer. If nothing is found on the active layer
   * and settings.select_on_all_layers is true, all layers are selected.
   */
  Set<Item> pick_items(FloatPoint p_location)
  {
    return pick_items(p_location, settings.item_selection_filter);
  }

  /**
   * Gets all items at p_location on the active board layer with the input item filter. If nothing
   * is found on the active layer and settings.select_on_all_layers is true, all layers are
   * selected.
   */
  Set<Item> pick_items(FloatPoint p_location, ItemSelectionFilter p_item_filter)
  {
    IntPoint location = p_location.round();
    Set<Item> result = board.pick_items(location, settings.layer, p_item_filter);
    if (result.isEmpty() && settings.select_on_all_visible_layers)
    {
      for (int i = 0; i < graphics_context.layer_count(); ++i)
      {
        if (i == settings.layer || graphics_context.get_layer_visibility(i) <= 0)
        {
          continue;
        }
        result.addAll(board.pick_items(location, i, p_item_filter));
      }
    }
    return result;
  }

  /**
   * Moves the mouse pointer to p_to_location.
   */
  void move_mouse(FloatPoint p_to_location)
  {
    if (!board_is_read_only)
    {
      panel.move_mouse(graphics_context.coordinate_transform.board_to_screen(p_to_location));
    }
  }

  /**
   * Gets the current interactive mode: select, route or drag.
   */
  public InteractiveState get_interactive_state()
  {
    return this.interactive_state;
  }

  public void set_interactive_state(InteractiveState p_state)
  {
    if (p_state != null && p_state != interactive_state)
    {
      this.interactive_state = p_state;
      if (!this.board_is_read_only)
      {
        p_state.set_toolbar();
      }
    }
  }

  /**
   * Adjust the design bounds, so that also all items being still placed outside the board outline
   * are contained in the new bounds.
   */
  public void adjust_design_bounds()
  {
    IntBox new_bounding_box = this.board.get_bounding_box();
    Collection<Item> board_items = this.board.get_items();
    for (Item curr_item : board_items)
    {
      IntBox curr_bounding_box = curr_item.bounding_box();
      if (curr_bounding_box.ur.x < Integer.MAX_VALUE)
      {
        new_bounding_box = new_bounding_box.union(curr_bounding_box);
      }
    }
    this.graphics_context.change_design_bounds(new_bounding_box);
  }

  /**
   * Sets all references inside this class to null, so that it can be recycled by the garbage
   * collector.
   */
  public void dispose()
  {
    close_files();
    graphics_context = null;
    coordinate_transform = null;
    settings = null;
    interactive_state = null;
    ratsnest = null;
    clearance_violations = null;
    board = null;
  }

  public BoardUpdateStrategy get_board_update_strategy()
  {
    return board_update_strategy;
  }

  public void set_board_update_strategy(BoardUpdateStrategy p_board_update_strategy)
  {
    board_update_strategy = p_board_update_strategy;
  }

  public String get_hybrid_ratio()
  {
    return hybrid_ratio;
  }

  public void set_hybrid_ratio(String p_hybrid_ratio)
  {
    hybrid_ratio = p_hybrid_ratio;
  }

  public ItemSelectionStrategy get_item_selection_strategy()
  {
    return item_selection_strategy;
  }

  public void set_item_selection_strategy(ItemSelectionStrategy p_item_selection_strategy)
  {
    item_selection_strategy = p_item_selection_strategy;
  }

  public int get_num_threads()
  {
    if ((num_threads > 1) && (!globalSettings.featureFlags.multiThreading))
    {
      routingJob.logInfo("Multi-threading is disabled in the settings. Using single thread.");
      num_threads = 1;
    }

    return num_threads;
  }

  public void set_num_threads(int p_value)
  {
    num_threads = p_value;
  }

  public void addReadOnlyEventListener(Consumer<Boolean> listener)
  {
    readOnlyEventListeners.add(listener);
  }

}