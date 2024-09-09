package app.freerouting.interactive;

import app.freerouting.board.*;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Manages the routing board operations in a headless mode, where no graphical user interface is involved.
 * This class handles the core logic and interactions required for auto-routing and other board-related tasks in a non-interactive environment.
 */
public class HeadlessBoardManager implements IBoardManager
{
  /**
   * The file used for logging interactive action, so that they can be replayed later
   */
  public final ActivityReplayFile activityReplayFile = new ActivityReplayFile();
  /**
   * The current settings for interactive actions on the board
   */
  public Settings settings;
  /**
   * The listener for the autorouter thread
   */
  public ThreadActionListener autorouter_listener;
  /**
   * The board object that contains all the data for the board
   */
  protected RoutingBoard board;
  protected Locale locale;
  protected RoutingJob routingJob;
  // The board checksum is used to detect changes in the board database
  protected long originalBoardChecksum = 0;

  public HeadlessBoardManager(Locale p_locale, RoutingJob routingJob)
  {
    this.locale = p_locale;
    this.routingJob = routingJob;
  }

  /**
   * Gets the routing board of this board handling.
   */
  @Override
  public RoutingBoard get_routing_board()
  {
    return this.board;
  }

  public synchronized void update_routing_board(RoutingBoard routing_board)
  {
    this.board = routing_board;
  }

  @Override
  public Settings get_settings()
  {
    return settings;
  }

  /**
   * Initializes the manual trace widths from the default trace widths in the board rules.
   */
  @Override
  public void initialize_manual_trace_half_widths()
  {
    for (int i = 0; i < settings.manual_trace_half_width_arr.length; ++i)
    {
      settings.manual_trace_half_width_arr[i] = this.board.rules.get_default_net_class().get_trace_half_width(i);
    }
  }

  @Override
  public void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name, BoardRules p_rules, Communication p_board_communication)
  {
    if (this.board != null)
    {
      FRLogger.warn(" BoardHandling.create_board: board already created");
    }
    int outline_cl_class_no = 0;

    if (p_rules != null)
    {
      if (p_outline_clearance_class_name != null && p_rules.clearance_matrix != null)
      {
        outline_cl_class_no = p_rules.clearance_matrix.get_no(p_outline_clearance_class_name);
        outline_cl_class_no = Math.max(outline_cl_class_no, 0);
      }
      else
      {
        outline_cl_class_no = p_rules.get_default_net_class().default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA);
      }
    }
    this.board = new RoutingBoard(p_bounding_box, p_layer_structure, p_outline_shapes, outline_cl_class_no, p_rules, p_board_communication);

    this.settings = new Settings(this.board, this.activityReplayFile);
  }

  @Override
  public Locale get_locale()
  {
    return this.locale;
  }

  //* Returns the checksum of the board. This checksum is used to detect changes in the board database.
  public long calculateCrc32()
  {
    // Create a memory stream
    ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
    DsnFile.write(this, memoryStream, "N/A", false);

    // Transform the output stream to an input stream
    InputStream inputStream = new ByteArrayInputStream(memoryStream.toByteArray());

    return BoardFileDetails.calculateCrc32(inputStream).getValue();
  }

  /**
   * Imports a board design from a Specctra dsn-file. The parameters p_item_observers and
   * p_item_id_no_generator are used, in case the board is embedded into a host system. Returns
   * false, if the dsn-file is corrupted.
   */
  public DsnFile.ReadResult loadFromSpecctraDsn(InputStream inputStream, BoardObservers boardObservers, IdentificationNumberGenerator identificationNumberGenerator)
  {
    if (inputStream == null)
    {
      return DsnFile.ReadResult.ERROR;
    }

    DsnFile.ReadResult read_result;
    try
    {
      // TODO: we should have a returned object that represent the DSN file, and we should create a RoutingBoard/BasicBoard based on that as a next step
      // we create the board inside the DSN file reader instead at the moment, and save it in the board field of the BoardHandling class
      read_result = DsnFile.read(inputStream, this, boardObservers, identificationNumberGenerator);
    } catch (Exception e)
    {
      read_result = DsnFile.ReadResult.ERROR;
      FRLogger.error("There was an error while reading DSN file.", e);
    }
    if (read_result == DsnFile.ReadResult.OK)
    {
      FRAnalytics.fileLoaded("DSN", this.board.communication.specctra_parser_info.host_cad + "," + this.board.communication.specctra_parser_info.host_version);
      this.board.reduce_nets_of_route_items();
      originalBoardChecksum = calculateCrc32();
      FRAnalytics.boardLoaded(this.board.communication.specctra_parser_info.host_cad, this.board.communication.specctra_parser_info.host_version, this.board.get_layer_count(), this.board.components.count(), this.board.rules.nets.max_net_no());
    }

    try
    {
      inputStream.close();
    } catch (IOException e)
    {
      read_result = DsnFile.ReadResult.ERROR;
    }
    return read_result;
  }
}