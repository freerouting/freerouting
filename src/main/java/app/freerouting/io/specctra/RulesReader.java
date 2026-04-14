package app.freerouting.io.specctra;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.io.specctra.parser.CoordinateTransform;
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
 * Reads a Specctra {@code .rules} file and applies the parsed rules directly to a
 * {@link BasicBoard}, without any dependency on
 * {@link app.freerouting.interactive.GuiBoardManager}.
 *
 * <p>Replaces the read path previously found in
 * {@link app.freerouting.io.specctra.parser.RulesFile#read} (now {@link Deprecated}).
 */
public final class RulesReader {

  private RulesReader() {
  }

  /**
   * Reads the rules from {@code in} and applies them to {@code board}.
   *
   * <p>The stream is closed by this method on return (success or failure).
   *
   * @param in         source — closed by this method on completion
   * @param designName expected PCB design name in the rules header (mismatch is logged
   *                   but does not abort the read)
   * @param board      the board to which parsed rules are applied
   * @return {@code true} if the rules were parsed and applied successfully;
   *         {@code false} on any parse or I/O error
   */
  public static boolean read(InputStream in, String designName, BasicBoard board) {
    IJFlexScanner scanner = new SpecctraDsnStreamReader(in);
    try {
      // Validate the "(rules PCB <name>" header
      Object currToken = scanner.next_token();
      if (currToken != Keyword.OPEN_BRACKET) {
        FRLogger.warn("RulesReader.read: open bracket expected at '"
            + scanner.get_scope_identifier() + "'");
        return false;
      }
      currToken = scanner.next_token();
      if (currToken != Keyword.RULES) {
        FRLogger.warn("RulesReader.read: keyword 'rules' expected at '"
            + scanner.get_scope_identifier() + "'");
        return false;
      }
      currToken = scanner.next_token();
      if (currToken != Keyword.PCB_SCOPE) {
        FRLogger.warn("RulesReader.read: keyword 'pcb' expected at '"
            + scanner.get_scope_identifier() + "'");
        return false;
      }
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      currToken = scanner.next_token();
      if (!(currToken instanceof String) || !currToken.equals(designName)) {
        FRLogger.warn("RulesReader.read: design_name not matching at '"
            + scanner.get_scope_identifier() + "' (expected '" + designName
            + "', got '" + currToken + "')");
        // non-fatal: continue reading
      }
    } catch (IOException e) {
      FRLogger.error("RulesReader.read: IO error scanning rules header", e);
      closeQuietly(in);
      return false;
    }

    LayerStructure layerStructure = new LayerStructure(board.layer_structure);
    CoordinateTransform coordinateTransform = board.communication.coordinate_transform;

    // Parse all top-level scopes in the rules body
    Object nextToken = null;
    for (;;) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("RulesReader.read: IO error scanning rules body", e);
        closeQuietly(in);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn("RulesReader.read: unexpected end of file at '"
            + scanner.get_scope_identifier() + "'");
        closeQuietly(in);
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of (rules ...) scope — success
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.RULE) {
          applyRules(Rule.read_scope(scanner), board, null);
        } else if (nextToken == Keyword.LAYER) {
          applyLayerRules(scanner, board);
        } else if (nextToken == Keyword.PADSTACK) {
          Library.read_padstack_scope(scanner, layerStructure, coordinateTransform,
              board.library.padstacks);
        } else if (nextToken == Keyword.VIA) {
          applyViaInfo(scanner, board);
        } else if (nextToken == Keyword.VIA_RULE) {
          applyViaRule(scanner, board);
        } else if (nextToken == Keyword.CLASS) {
          applyNetClass(scanner, layerStructure, board);
        } else if (nextToken == Keyword.SNAP_ANGLE) {
          AngleRestriction snapAngle = Structure.read_snap_angle(scanner);
          if (snapAngle != null) {
            board.rules.set_trace_angle_restriction(snapAngle);
          }
        } else {
          ScopeKeyword.skip_scope(scanner);
        }
      }
    }

    closeQuietly(in);
    return true;
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
      layerNo = board.layer_structure.get_no(layerName);
      if (layerNo < 0) {
        FRLogger.warn("RulesReader.applyRules: layer not found: '" + layerName + "'");
      }
    }
    CoordinateTransform coordinateTransform = board.communication.coordinate_transform;
    String stringQuote = board.communication.specctra_parser_info.string_quote;
    for (Rule rule : rules) {
      if (rule instanceof Rule.WidthRule widthRule) {
        int traceHalfwidth = (int) Math.round(coordinateTransform.dsn_to_board(widthRule.value) / 2);
        if (layerNo < 0) {
          board.rules.set_default_trace_half_widths(traceHalfwidth);
        } else {
          board.rules.set_default_trace_half_width(layerNo, traceHalfwidth);
        }
      } else if (rule instanceof Rule.ClearanceRule clearanceRule) {
        Structure.set_clearance_rule(clearanceRule, layerNo, coordinateTransform,
            board.rules, stringQuote);
      }
    }
  }

  private static void applyLayerRules(IJFlexScanner scanner, BasicBoard board) {
    try {
      Object nextToken = scanner.next_token();
      if (!(nextToken instanceof String layerString)) {
        FRLogger.warn("RulesReader.applyLayerRules: String expected at '"
            + scanner.get_scope_identifier() + "'");
        return;
      }
      nextToken = scanner.next_token();
      while (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn("RulesReader.applyLayerRules: '(' expected at '"
              + scanner.get_scope_identifier() + "'");
          return;
        }
        nextToken = scanner.next_token();
        if (nextToken == Keyword.RULE) {
          applyRules(Rule.read_scope(scanner), board, layerString);
        } else {
          ScopeKeyword.skip_scope(scanner);
        }
        nextToken = scanner.next_token();
      }
    } catch (IOException e) {
      FRLogger.error("RulesReader.applyLayerRules: IO error scanning file", e);
    }
  }

  private static void applyViaInfo(IJFlexScanner scanner, BasicBoard board) {
    ViaInfo viaInfo = Network.read_via_info(scanner, board);
    if (viaInfo == null) {
      return;
    }
    ViaInfo existing = board.rules.via_infos.get(viaInfo.get_name());
    if (existing != null) {
      board.rules.via_infos.remove(existing);
    }
    board.rules.via_infos.add(viaInfo);
  }

  private static void applyViaRule(IJFlexScanner scanner, BasicBoard board) {
    Collection<String> viaRule = Network.read_via_rule(scanner, board);
    if (viaRule != null) {
      Network.add_via_rule(viaRule, board);
    }
  }

  private static void applyNetClass(IJFlexScanner scanner, LayerStructure layerStructure,
      BasicBoard board) {
    NetClass netClass = NetClass.read_scope(scanner);
    if (netClass == null) {
      return;
    }
    Network.insert_net_class(netClass, layerStructure, board,
        board.communication.coordinate_transform, false);
  }

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException _) {
      // ignore
    }
  }
}

