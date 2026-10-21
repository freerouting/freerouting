package app.freerouting.io.specctra;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.specctra.parser.IJFlexScanner;
import app.freerouting.io.specctra.parser.Keyword;
import app.freerouting.io.specctra.parser.LayerStructure;
import app.freerouting.io.specctra.parser.ScopeKeyword;
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * Reads a Specctra session (.ses) file and imports the routing data (wires and vias) into a {@link
 * BasicBoard}.
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class.
 *
 * <p>This class is the public read entry point for Specctra session files.
 */
public final class SesReader {

  private final IJFlexScanner scanner;
  private final BasicBoard board;
  private final LayerStructure specctraLayerStructure;
  private BigFraction sessionToBoardScale;
  private int wiresImported;
  private int viasImported;
  private int errorsEncountered;

  private SesReader(IJFlexScanner scanner, BasicBoard board, BigFraction scaleFactor) {
    this.scanner = scanner;
    this.board = board;
    this.specctraLayerStructure = new LayerStructure(board.layerStructure);
    this.sessionToBoardScale = scaleFactor;
  }

  /**
   * Reads a SES file and imports the routing data into {@code board}.
   *
   * <p>The stream is always closed by this method on return (success or failure).
   *
   * @param in the SES input stream — closed by this method on completion
   * @param board the board to which wires and vias are added
   * @return a summary describing how many wires and vias were imported and how many errors were
   *     encountered; a non-zero {@link SesImportSummary#errorsEncountered()} value means some items
   *     were skipped but the board is otherwise intact
   * @throws IOException if {@code in} is {@code null} or {@code board} is {@code null}, or if the
   *     stream does not start with a valid Specctra session header, or if an I/O error occurs while
   *     reading the stream
   */
  public static SesImportSummary read(InputStream in, BasicBoard board) throws IOException {
    if (in == null) {
      throw new IOException("SesReader.read: input stream must not be null");
    }
    if (board == null) {
      throw new IOException("SesReader.read: board must not be null");
    }

    SpecctraDsnStreamReader scanner = new SpecctraDsnStreamReader(in, true);

    // SES files use the DSN-to-board scale factor: dsn_to_board(1) / resolution
    BigFraction scaleFactor =
        SesNumbers.sessionScale(board, board.communication.unit, board.communication.resolution);

    SesReader reader = new SesReader(scanner, board, scaleFactor);

    try {
      reader.processSessionScope();
      FRLogger.info(
          "SES file import complete: "
              + reader.wiresImported
              + " wires, "
              + reader.viasImported
              + " vias imported"
              + (reader.errorsEncountered > 0 ? " (" + reader.errorsEncountered + " errors)" : ""));
      return new SesImportSummary(
          reader.wiresImported, reader.viasImported, reader.errorsEncountered);
    } finally {
      closeQuietly(in);
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers for session-file parsing.
  // ---------------------------------------------------------------------------

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException _) {
      // ignore — nothing useful to do here
    }
  }

  /** Transforms a Specctra session file into an Autodesk Fusion script file. */
  public static boolean saveSpecctraSessionSesAsFusionScriptScr(
      InputStream inputStream, OutputStream outputStream, BasicBoard board) {
    return app.freerouting.io.specctra.parser.SessionToFusion.getInstance(
        inputStream, outputStream, board);
  }

  /**
   * Processes the outermost scope of the session file.
   *
   * @throws IOException if the session header is malformed or an I/O error occurs
   */
  private void processSessionScope() throws IOException {
    // Validate the "(session <name>" header
    Object nextToken = null;
    for (int i = 0; i < 3; i++) {
      nextToken = this.scanner.nextToken();
      boolean keywordOk = true;
      if (i == 0) {
        keywordOk = nextToken == Keyword.OPEN_BRACKET;
      } else if (i == 1) {
        keywordOk = nextToken == Keyword.SESSION;
        this.scanner.yybegin(SpecctraDsnStreamReader.NAME); // consume the session name
      }
      if (!keywordOk) {
        throw new IOException(
            "SesReader: not a Specctra session file — expected '(session <name>' header, got: "
                + nextToken);
      }
    }

    // Read the direct subscopes of the session scope
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        // end of file
        return;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of session scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.ROUTES) {
          processRoutesScope();
        } else {
          // skip placement, was_is, and any other scopes we don't need
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
  }

  /** Processes the {@code (routes ...)} scope containing network data. */
  private void processRoutesScope() throws IOException {
    Object nextToken = null;
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        FRLogger.warn(
            "SesReader.processRoutesScope: unexpected end of file at '"
                + this.scanner.getScopeIdentifier()
                + "'");
        return;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.NETWORK_OUT) {
          processNetworkScope();
        } else if (nextToken == Keyword.RESOLUTION_SCOPE) {
          readSessionResolution();
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
  }

  /**
   * Reads the resolution declaration in a session routes scope and calculates the session-to-board
   * coordinate scale factor.
   */
  private void readSessionResolution() throws IOException {
    Object unitToken = this.scanner.nextToken();
    Object resolutionToken = this.scanner.nextToken();
    Object close = this.scanner.nextToken();
    int resolution;
    try {
      resolution = exactNumber(resolutionToken).intValueExact();
    } catch (ArithmeticException invalid) {
      throw new IOException("SesReader: session resolution must be a positive integer", invalid);
    }
    if (!(unitToken instanceof String name)
        || app.freerouting.board.model.structure.Unit.fromString(name) == null
        || resolution <= 0
        || close != Keyword.CLOSED_BRACKET) {
      throw new IOException(
          "SesReader: invalid routes resolution; expected (resolution <unit> <positive integer>)");
    }
    app.freerouting.board.model.structure.Unit sessionUnit =
        app.freerouting.board.model.structure.Unit.fromString(name);
    this.sessionToBoardScale = SesNumbers.sessionScale(board, sessionUnit, resolution);
  }

  /** Resolves the item's net class, falling back to the board default net class. */
  private app.freerouting.rules.NetClass netClassOf(int[] netNumbers) {
    Net net =
        netNumbers != null && netNumbers.length > 0
            ? this.board.rules.nets.get(netNumbers[0])
            : null;
    return net != null ? net.getNetClass() : this.board.rules.getDefaultNetClass();
  }

  /** Processes the {@code (network_out ...)} scope containing individual nets. */
  private void processNetworkScope() throws IOException {
    Object nextToken = null;
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        FRLogger.warn(
            "SesReader.processNetworkScope: unexpected end of file at '"
                + this.scanner.getScopeIdentifier()
                + "'");
        return;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.NET) {
          processNetScope();
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
  }

  /** Processes a single {@code (net ...)} scope containing wires and vias. */
  private void processNetScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    if (!(nextToken instanceof String netName)) {
      FRLogger.warn(
          "SesReader.processNetScope: String expected at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      errorsEncountered++;
      return;
    }
    this.scanner.setScopeIdentifier(netName);

    Net net = board.rules.nets.get(netName, 1);
    if (net == null) {
      FRLogger.warn("SesReader: net not found: '" + netName + "' — skipping");
      errorsEncountered++;
      ScopeKeyword.skipScope(this.scanner);
      return;
    }
    int netNumber = net.netNumber;
    int[] netNumbers = new int[] {netNumber};

    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        return;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.WIRE) {
          if (!processWireScope(netNumbers)) {
            errorsEncountered++;
          }
        } else if (nextToken == Keyword.VIA) {
          if (!processViaScope(netNumbers)) {
            errorsEncountered++;
          }
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
  }

  // ---------------------------------------------------------------------------

  /**
   * Processes a {@code (wire ...)} scope and inserts the trace into the board.
   *
   * @return {@code true} if the wire was successfully imported; {@code false} on a parse or
   *     geometry error (the caller increments {@link #errorsEncountered})
   */
  private boolean processWireScope(int[] netNumbers) throws IOException {
    SessionPath wirePath = null;
    boolean sawPath = false;
    Object nextToken = null;
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        FRLogger.warn(
            "SesReader.processWireScope: unexpected end of file at '"
                + this.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.POLYGON_PATH) {
          sawPath = true;
          wirePath = readExactPath();
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }

    if (wirePath == null) {
      // conduction areas have no polygon_path — silently skip
      return !sawPath;
    }

    try {
      int layerIndex = wirePath.layer();
      if (layerIndex < 0 || layerIndex >= board.getLayerCount()) {
        throw new IOException("Invalid SES wire layer");
      }
      Polyline polyline = SesPathGeometry.read(wirePath.coordinates(), sessionToBoardScale);
      int halfWidth = SesPathGeometry.halfWidth(wirePath.width(), sessionToBoardScale);

      int clearanceClass =
          netClassOf(netNumbers)
              .defaultItemClearanceClasses
              .get(app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);

      // Import fixed copper exactly; normalization must not erase point copper or short segments.
      var trace =
          board.insertTraceWithoutCleaning(
              polyline, layerIndex, halfWidth, netNumbers, clearanceClass, FixedState.USER_FIXED);
      if (trace == null) {
        return false;
      }
      wiresImported++;
      return true;

    } catch (Exception e) {
      FRLogger.warn(
          "SesReader.processWireScope: failed to import wire on net #"
              + netNumbers[0]
              + " at "
              + wirePath.coordinates()[0]
              + ","
              + wirePath.coordinates()[1]
              + " — "
              + e.getMessage());
      return false;
    }
  }

  private record SessionPath(int layer, BigDecimal width, BigDecimal[] coordinates) {}

  private static BigDecimal exactNumber(Object token) throws IOException {
    if (token instanceof BigDecimal decimal) {
      return decimal;
    }
    throw new IOException("A decimal SES number was expected");
  }

  private SessionPath readExactPath() throws IOException {
    String layer = scanner.nextString();
    List<BigDecimal> numbers = new ArrayList<>();
    boolean invalid = false;
    for (; ; ) {
      Object token = scanner.nextToken();
      if (token == null) {
        throw new IOException("Unexpected end of SES path");
      }
      if (token == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (token == Keyword.OPEN_BRACKET) {
        ScopeKeyword.skipScope(scanner);
        continue;
      }
      try {
        numbers.add(exactNumber(token));
      } catch (IOException invalidNumber) {
        invalid = true;
      }
    }
    if (invalid || numbers.size() < 3 || (numbers.size() - 1) % 2 != 0) {
      FRLogger.warn("SesReader: invalid wire path on layer " + layer);
      return null;
    }
    BigDecimal[] coordinates = numbers.subList(1, numbers.size()).toArray(BigDecimal[]::new);
    if (coordinates.length == 2) {
      // A single vertex represents point copper; keep two equal endpoints in the trace model.
      coordinates =
          new BigDecimal[] {coordinates[0], coordinates[1], coordinates[0], coordinates[1]};
    }
    return new SessionPath(specctraLayerStructure.getNo(layer), numbers.getFirst(), coordinates);
  }

  /**
   * Processes a {@code (via ...)} scope and inserts the via into the board.
   *
   * @return {@code true} if the via was successfully imported; {@code false} on a parse or geometry
   *     error (the caller increments {@link #errorsEncountered})
   */
  private boolean processViaScope(int[] netNumbers) throws IOException {
    Object nextToken = this.scanner.nextToken();
    if (!(nextToken instanceof String padstackName)) {
      FRLogger.warn(
          "SesReader.processViaScope: padstack name expected at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    this.scanner.setScopeIdentifier(padstackName);

    BigDecimal[] location = new BigDecimal[2];
    for (int i = 0; i < 2; i++) {
      nextToken = this.scanner.nextToken();
      try {
        location[i] = exactNumber(nextToken);
      } catch (IOException invalidNumber) {
        FRLogger.warn("SesReader.processViaScope: " + invalidNumber.getMessage());
        return false;
      }
    }

    // Skip any additional sub-scopes (e.g. type, lock_type)
    nextToken = this.scanner.nextToken();
    while (nextToken == Keyword.OPEN_BRACKET) {
      ScopeKeyword.skipScope(this.scanner);
      nextToken = this.scanner.nextToken();
    }

    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "SesReader.processViaScope: closing bracket expected at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      return false;
    }

    try {
      Padstack viaPadstack = this.board.library.padstacks.get(padstackName);
      if (viaPadstack == null && padstackName != null) {
        viaPadstack = this.board.library.padstacks.get(padstackName.replaceAll("\\.\\d+", ""));
      }
      if (viaPadstack == null) {
        FRLogger.warn("SesReader.processViaScope: via padstack not found: " + padstackName);
        return false;
      }

      BigFraction x = SesNumbers.recoverInteger(location[0], sessionToBoardScale);
      BigFraction y = SesNumbers.recoverInteger(location[1], sessionToBoardScale);
      if (!x.getDenominator().equals(java.math.BigInteger.ONE)
          || !y.getDenominator().equals(java.math.BigInteger.ONE)) {
        throw new IOException("The SES via center is not an integer board position");
      }
      Point viaLocation =
          Point.getInstance(x.getNumerator().intValueExact(), y.getNumerator().intValueExact())
              .toIntegerExact();

      int clearanceClass =
          netClassOf(netNumbers)
              .defaultItemClearanceClasses
              .get(app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.VIA);

      board.insertVia(
          viaPadstack, viaLocation, netNumbers, clearanceClass, FixedState.USER_FIXED, true);

      viasImported++;
      return true;

    } catch (Exception e) {
      FRLogger.warn("SesReader.processViaScope: failed to import via — " + e.getMessage());
      return false;
    }
  }
}
