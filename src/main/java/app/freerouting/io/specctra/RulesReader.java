package app.freerouting.io.specctra;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.io.specctra.parser.IJFlexScanner;
import app.freerouting.io.specctra.parser.Keyword;
import app.freerouting.io.specctra.parser.LayerStructure;
import app.freerouting.io.specctra.parser.Library;
import app.freerouting.io.specctra.parser.NetClass;
import app.freerouting.io.specctra.parser.Network;
import app.freerouting.io.specctra.parser.Rule;
import app.freerouting.io.specctra.parser.ScopeKeyword;
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader;
import app.freerouting.io.specctra.parser.Structure;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Reads a Specctra {@code .rules} file and applies the parsed rules directly to a {@link
 * BasicBoard}, without any dependency on {@link app.freerouting.interactive.GuiBoardManager}.
 *
 * <p>Replaces the read path previously found in {@link
 * app.freerouting.io.specctra.parser.RulesFile} (now an empty shell).
 */
public final class RulesReader {

  private RulesReader() {}

  /**
   * Reads the rules from {@code in} and applies them to {@code board}.
   *
   * <p>The stream is closed by this method on return (success or failure).
   *
   * @param in source — closed by this method on completion
   * @param designName expected PCB design name in the rules header (mismatch is logged but does not
   *     abort the read)
   * @param board the board to which parsed rules are applied
   * @return {@code true} if the rules were parsed and applied successfully; {@code false} on any
   *     parse or I/O error
   */
  public static boolean read(InputStream in, String designName, BasicBoard board) {
    if (in == null) {
      FRLogger.warn("RulesReader.read: input stream is null");
      return false;
    }
    if (board == null) {
      FRLogger.warn("RulesReader.read: board is null");
      closeQuietly(in);
      return false;
    }

    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    try {
      // Validate the "(rules PCB <name>" header
      Object currToken = scanner.nextToken();
      if (currToken != Keyword.OPEN_BRACKET) {
        FRLogger.warn(
            "RulesReader.read: open bracket expected at '" + scanner.getScopeIdentifier() + "'");
        return false;
      }
      currToken = scanner.nextToken();
      if (currToken != Keyword.RULES) {
        FRLogger.warn(
            "RulesReader.read: keyword 'rules' expected at '" + scanner.getScopeIdentifier() + "'");
        return false;
      }
      currToken = scanner.nextToken();
      if (currToken != Keyword.PCB_SCOPE) {
        FRLogger.warn(
            "RulesReader.read: keyword 'pcb' expected at '" + scanner.getScopeIdentifier() + "'");
        return false;
      }
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      currToken = scanner.nextToken();
      if (!(currToken instanceof String) || !currToken.equals(designName)) {
        FRLogger.warn(
            "RulesReader.read: designName not matching at '"
                + scanner.getScopeIdentifier()
                + "' (expected '"
                + designName
                + "', got '"
                + currToken
                + "')");
        // non-fatal: continue reading
      }

      LayerStructure layerStructure = new LayerStructure(board.layerStructure);
      CoordinateTransform coordinateTransform = board.communication.coordinateTransform;

      // Parse all top-level scopes in the rules body
      Object nextToken = null;
      for (; ; ) {
        final Object prevToken = nextToken;
        try {
          nextToken = scanner.nextToken();
        } catch (IOException e) {
          FRLogger.error("RulesReader.read: IO error scanning rules body", e);
          return false;
        }
        if (nextToken == null) {
          FRLogger.warn(
              "RulesReader.read: unexpected end of file at '" + scanner.getScopeIdentifier() + "'");
          return false;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          // end of (rules ...) scope — success
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.RULE) {
            applyRules(Rule.readScope(scanner), board, null);
          } else if (nextToken == Keyword.LAYER) {
            applyLayerRules(scanner, board);
          } else if (nextToken == Keyword.PADSTACK) {
            Library.readPadstackScope(
                scanner, layerStructure, coordinateTransform, board.library.padstacks);
          } else if (nextToken == Keyword.VIA) {
            applyViaInfo(scanner, board);
          } else if (nextToken == Keyword.VIA_RULE) {
            applyViaRule(scanner, board);
          } else if (nextToken == Keyword.CLASS) {
            applyNetClass(scanner, layerStructure, board);
          } else if (nextToken == Keyword.SNAP_ANGLE) {
            AngleRestriction snapAngle = Structure.readSnapAngle(scanner);
            if (snapAngle != null) {
              board.rules.setTraceAngleRestriction(snapAngle);
            }
          } else {
            ScopeKeyword.skipScope(scanner);
          }
        }
      }
      return true;
    } catch (IOException e) {
      FRLogger.error("RulesReader.read: IO error scanning rules header", e);
      return false;
    } finally {
      closeQuietly(in);
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers (migrated from RulesFile)
  // -------------------------------------------------------------------------

  private static void applyRules(Collection<Rule> rules, BasicBoard board, String layerName) {
    if (rules == null) {
      return;
    }
    int layerNo = -1;
    if (layerName != null) {
      layerNo = board.layerStructure.getNo(layerName);
      if (layerNo < 0) {
        FRLogger.warn("RulesReader.applyRules: layer not found: '" + layerName + "'");
      }
    }
    CoordinateTransform coordinateTransform = board.communication.coordinateTransform;
    String stringQuote = board.communication.specctraParserInfo.stringQuote;
    for (Rule rule : rules) {
      if (rule instanceof Rule.WidthRule widthRule) {
        int traceHalfwidth = (int) Math.round(coordinateTransform.dsnToBoard(widthRule.value) / 2);
        if (layerNo < 0) {
          board.rules.setDefaultTraceHalfWidths(traceHalfwidth);
        } else {
          board.rules.setDefaultTraceHalfWidth(layerNo, traceHalfwidth);
        }
      } else if (rule instanceof Rule.ClearanceRule clearanceRule) {
        Structure.setClearanceRule(
            clearanceRule, layerNo, coordinateTransform, board.rules, stringQuote);
      }
    }
  }

  private static void applyLayerRules(IJFlexScanner scanner, BasicBoard board) {
    try {
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String layerString)) {
        FRLogger.warn(
            "RulesReader.applyLayerRules: String expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return;
      }
      nextToken = scanner.nextToken();
      while (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "RulesReader.applyLayerRules: '(' expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return;
        }
        nextToken = scanner.nextToken();
        if (nextToken == Keyword.RULE) {
          applyRules(Rule.readScope(scanner), board, layerString);
        } else {
          ScopeKeyword.skipScope(scanner);
        }
        nextToken = scanner.nextToken();
      }
    } catch (IOException e) {
      FRLogger.error("RulesReader.applyLayerRules: IO error scanning file", e);
    }
  }

  private static void applyViaInfo(IJFlexScanner scanner, BasicBoard board) {
    ViaInfo viaInfo = Network.readViaInfo(scanner, board);
    if (viaInfo == null) {
      return;
    }
    ViaInfo existing = board.rules.viaInfos.get(viaInfo.getName());
    if (existing != null) {
      board.rules.viaInfos.remove(existing);
    }
    board.rules.viaInfos.add(viaInfo);
  }

  private static void applyViaRule(IJFlexScanner scanner, BasicBoard board) {
    Collection<String> viaRule = Network.readViaRule(scanner, board);
    if (viaRule != null) {
      Network.addViaRule(viaRule, board);
    }
  }

  private static void applyNetClass(
      IJFlexScanner scanner, LayerStructure layerStructure, BasicBoard board) {
    NetClass netClass = NetClass.readScope(scanner);
    if (netClass == null) {
      return;
    }
    Network.insertNetClass(
        netClass, layerStructure, board, board.communication.coordinateTransform, false);
  }

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException _) {
      // ignore
    }
  }
}
