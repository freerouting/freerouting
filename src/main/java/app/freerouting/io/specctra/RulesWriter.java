package app.freerouting.io.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.core.Padstack;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.specctra.parser.AutorouteSettings;
import app.freerouting.io.specctra.parser.Library;
import app.freerouting.io.specctra.parser.Network;
import app.freerouting.io.specctra.parser.Rule;
import app.freerouting.io.specctra.parser.Structure;
import app.freerouting.io.specctra.parser.WriteScopeParameter;
import app.freerouting.logger.FRLogger;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes board design rules to a Specctra {@code .rules} file without any
 * dependency on {@link app.freerouting.interactive.GuiBoardManager}.
 *
 * <p>Replaces the write path previously found in
 * {@link app.freerouting.io.specctra.parser.RulesFile#write} (now {@link Deprecated}).
 */
public final class RulesWriter {

  private RulesWriter() {
  }

  /**
   * Writes the design rules of {@code board} to {@code out} in Specctra rules format.
   *
   * <p>The stream is <em>not</em> closed by this method — the caller is responsible.
   *
   * @param board      the board whose rules are written
   * @param out        destination stream
   * @param designName the PCB design name written into the {@code (rules PCB ...)} header
   * @throws IOException if writing fails
   */
  public static void write(BasicBoard board, OutputStream out, String designName) throws IOException {
    IndentFileWriter outputFile = new IndentFileWriter(out);
    WriteScopeParameter par = new WriteScopeParameter(
        board,
        null,
        outputFile,
        board.communication.specctra_parser_info.string_quote,
        board.communication.coordinate_transform,
        false
    );
    writeRules(par, designName);
    outputFile.flush();
  }

  // -------------------------------------------------------------------------
  // Private helpers (migrated from RulesFile)
  // -------------------------------------------------------------------------

  private static void writeRules(WriteScopeParameter p_par, String p_design_name) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("rules PCB ");
    p_par.file.write(p_design_name);
    Structure.write_snap_angle(p_par.file, p_par.board.rules.get_trace_angle_restriction());
    if (p_par.autoroute_settings != null) {
      AutorouteSettings.write_scope(p_par.file, p_par.autoroute_settings,
          p_par.board.layer_structure, p_par.identifier_type);
    }
    // write the default rule using 0 as default layer
    Rule.write_default_rule(p_par, 0);
    // write the via padstacks
    for (int i = 1; i <= p_par.board.library.padstacks.count(); i++) {
      Padstack curr_padstack = p_par.board.library.padstacks.get(i);
      if (p_par.board.library.get_via_padstack(curr_padstack.name) != null) {
        Library.write_padstack_scope(p_par, curr_padstack);
      }
    }
    Network.write_via_infos(p_par.board.rules, p_par.file, p_par.identifier_type);
    Network.write_via_rules(p_par.board.rules, p_par.file, p_par.identifier_type);
    Network.write_net_classes(p_par);
    p_par.file.end_scope();
  }
}

