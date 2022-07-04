package app.freerouting.board;

import app.freerouting.datastructures.IdNoGenerator;
import app.freerouting.designforms.specctra.CoordinateTransform;

/** Communication information to host systems or host design formats. */
public class Communication implements java.io.Serializable {

  /** For coordinate tramsforms to a Specctra dsn file for example. */
  public final CoordinateTransform coordinate_transform;
  /** mil, inch or mm */
  public final Unit unit;
  /**
   * The resolution (1 / unit_factor) of the coordinate system, which is imported from the host
   * system.
   */
  public final int resolution;
  public final SpecctraParserInfo specctra_parser_info;
  public final IdNoGenerator id_no_generator;
  public transient BoardObservers observers;

  /** Creates a new instance of BoardCommunication */
  public Communication(
      Unit p_unit,
      int p_resolution,
      SpecctraParserInfo p_specctra_parser_info,
      CoordinateTransform p_coordinate_transform,
      IdNoGenerator p_id_no_generator,
      BoardObservers p_observers) {
    coordinate_transform = p_coordinate_transform;
    unit = p_unit;
    resolution = p_resolution;
    specctra_parser_info = p_specctra_parser_info;
    id_no_generator = p_id_no_generator;
    observers = p_observers;
  }

  /** Creates a new instance of BoardCommunication */
  public Communication() {
    this(
        Unit.MIL,
        1,
        new SpecctraParserInfo("\"", null, null, null, null, false),
        new CoordinateTransform(1, 0, 0),
        new app.freerouting.board.ItemIdNoGenerator(),
        new BoardObserverAdaptor());
  }

  public boolean host_cad_is_eagle() {
    return specctra_parser_info != null
        && specctra_parser_info.host_cad != null
        && specctra_parser_info.host_cad.equalsIgnoreCase("CadSoft");
  }

  public boolean host_cad_exists() {
    return specctra_parser_info != null && specctra_parser_info.host_cad != null;
  }

  /** Returns the resolution scaled to the input unit */
  public double get_resolution(Unit p_unit) {
    return Unit.scale(this.resolution, p_unit, this.unit);
  }

  private void readObject(java.io.ObjectInputStream p_stream)
      throws java.io.IOException, java.lang.ClassNotFoundException {
    p_stream.defaultReadObject();
    observers = new BoardObserverAdaptor();
  }

  /**
   * Information from the parser scope in a Specctra-dsn-file. The fields are optional and may be
   * null.
   */
  public static class SpecctraParserInfo implements java.io.Serializable {
    /** Character for quoting strings in a dsn-File. */
    public final String string_quote;
    public final String host_cad;
    public final String host_version;
    public final java.util.Collection<String[]> constants;
    public final WriteResolution write_resolution;
    public final boolean dsn_file_generated_by_host;

    public SpecctraParserInfo(
        String p_string_quote,
        String p_host_cad,
        String p_host_version,
        java.util.Collection<String[]> p_constants,
        WriteResolution p_write_resolution,
        boolean p_dsn_file_generated_by_host) {
      string_quote = p_string_quote;
      host_cad = p_host_cad;
      host_version = p_host_version;
      constants = p_constants;
      write_resolution = p_write_resolution;
      dsn_file_generated_by_host = p_dsn_file_generated_by_host;
    }

    public static class WriteResolution implements java.io.Serializable {
      public final String char_name;
      public final int positive_int;
      public WriteResolution(String p_char_name, int p_positive_int) {
        char_name = p_char_name;
        positive_int = p_positive_int;
      }
    }
  }
}
