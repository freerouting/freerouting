package app.freerouting.io.specctra.parser;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdGenerator;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class that contains some structured properties and helper functions for the DSN parser.
 */
public class ReadScopeParameter {

  final IJFlexScanner scanner;
  final BoardParserCallback boardHandling;
  final NetList netlist = new NetList();
  final BoardObservers observers;
  final IdGenerator idGenerator;

  /**
   * Warnings collected during DSN parsing (e.g. skipped wires, missing padstacks, degenerate
   * geometry). Callers can retrieve these via {@link #getWarnings()} after the read completes.
   */
  public final List<String> warnings = new ArrayList<>();

  /**
   * Collection of elements of class PlaneInfo. The plane cannot be inserted directly into the
   * boards, because the layers may not be read completely.
   */
  final Collection<PlaneInfo> planeList = new LinkedList<>();

  /**
   * Component placement information. It is filled while reading the placement scope and can be
   * evaluated after reading the library and network scope.
   */
  final Collection<ComponentPlacement> placementList = new LinkedList<>();

  final Collection<String[]> constants = new LinkedList<>();

  /**
   * The names of the via padstacks filled while reading the structure scope and evaluated after
   * reading the library scope.
   */
  Collection<String> viaPadstackNames;

  boolean viaAtSmdAllowed;
  public AngleRestriction snapAngle = AngleRestriction.FORTYFIVE_DEGREE;

  /** The logical parts are used for pin and gate swaw. */
  Collection<PartLibrary.LogicalPartMapping> logicalPartMappings = new LinkedList<>();

  Collection<PartLibrary.LogicalPart> logicalParts = new LinkedList<>();

  /** The following objects are from the parser scope. */
  public String stringQuote = "\"";

  public String hostCad;
  public String hostVersion;

  boolean dsnFileGeneratedByHost = true;

  /** Set to {@code false} by the structure reader when the board outline is absent. */
  public boolean boardOutlineOk = true;

  public Communication.SpecctraParserInfo.WriteResolution writeResolution;

  /** The following objects will be initialised when the structure scope is read. */
  public CoordinateTransform coordinateTransform;

  public LayerStructure layerStructure;

  /** Nullable — only populated when an {@code (autoroute ...)} scope is present in the DSN file. */
  public RouterSettings autorouteSettings;

  public Unit unit = Unit.MIL;
  public int resolution = 100; // default resolution

  /**
   * Creates a new instance of ReadScopeParameter without an external board manager. An internal
   * minimal shim is constructed to receive the parsed board. Use this constructor from {@link
   * app.freerouting.io.specctra.DsnReader#readBoard}.
   *
   * @param scanner the token scanner over the DSN input stream
   * @param observers nullable; for host-system embedding
   * @param idGenerator nullable; for host-system embedding
   */
  public ReadScopeParameter(
      IJFlexScanner scanner, BoardObservers observers, IdGenerator idGenerator) {
    this.scanner = scanner;
    boardHandling = new MinimalBoardManager();
    this.observers = observers;
    this.idGenerator = idGenerator;
  }

  /**
   * Returns the board that was created during parsing, or {@code null} if parsing has not yet
   * reached the board-construction step.
   */
  public BasicBoard getBoard() {
    return boardHandling.getRoutingBoard();
  }

  /**
   * Returns an unmodifiable view of the warnings collected during DSN parsing. The list is
   * populated as the file is read; call this method after the read completes.
   */
  public List<String> getWarnings() {
    return java.util.Collections.unmodifiableList(warnings);
  }

  // -------------------------------------------------------------------------
  // Minimal internal shim — satisfies the BoardParserCallback contract during
  // parsing without requiring a HeadlessBoardManager or a RoutingJob.
  // -------------------------------------------------------------------------

  private static final class MinimalBoardManager implements BoardParserCallback {

    private RoutingBoard board;

    @Override
    public RoutingBoard getRoutingBoard() {
      return board;
    }

    @Override
    public void createBoard(
        IntBox boundingBox,
        app.freerouting.board.LayerStructure layerStructure,
        PolylineShape[] outlineShapes,
        String outlineClearanceClassName,
        BoardRules rules,
        Communication boardCommunication) {
      int outlineClearanceNo = 0;
      if (rules != null) {
        if (outlineClearanceClassName != null && rules.clearanceMatrix != null) {
          outlineClearanceNo = Math.max(0, rules.clearanceMatrix.getNo(outlineClearanceClassName));
        } else {
          outlineClearanceNo =
              rules
                  .getDefaultNetClass()
                  .defaultItemClearanceClasses
                  .get(DefaultItemClearanceClasses.ItemClass.AREA);
        }
      }
      board =
          new RoutingBoard(
              boundingBox,
              layerStructure,
              outlineShapes,
              outlineClearanceNo,
              rules,
              boardCommunication);
    }

    @Override
    public RoutingJob getCurrentRoutingJob() {
      return null;
    }
  }

  // -------------------------------------------------------------------------

  /** Information for inserting a plane. */
  static class PlaneInfo {

    final Shape.ReadAreaScopeResult area;
    final String netName;

    PlaneInfo(Shape.ReadAreaScopeResult area, String netName) {
      this.area = area;
      this.netName = netName;
    }
  }
}
