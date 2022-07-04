package app.freerouting.designforms.specctra;

/** Class for writing placement scopes from dsn-files. */
public class Placement extends ScopeKeyword {

  /** Creates a new instance of Placemet */
  public Placement() {
    super("placement");
  }

  public static void write_scope(WriteScopeParameter p_par) throws java.io.IOException {
    p_par.file.start_scope();
    p_par.file.write("placement");
    if (p_par.board.components.get_flip_style_rotate_first()) {
      p_par.file.new_line();
      p_par.file.write("(place_control (flip_style rotate_first))");
    }
    for (int i = 1; i <= p_par.board.library.packages.count(); ++i) {
      app.freerouting.designforms.specctra.Package.write_placement_scope(
          p_par, p_par.board.library.packages.get(i));
    }
    p_par.file.end_scope();
  }
}
