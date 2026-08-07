package app.freerouting.board;

import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Communication information to host systems or host design formats. */
public class Communication implements Serializable {

  /** For coordinate transforms to a Specctra dsn file for example. */
  public final CoordinateTransform coordinateTransform;

  /** mil, inch or mm */
  public final Unit unit;

  /**
   * The resolution (1 / unitFactor) of the coordinate system, which is imported from the host
   * system.
   */
  public final int resolution;

  public final SpecctraParserInfo specctraParserInfo;
  public final IdentificationNumberGenerator idNoGenerator;
  public transient BoardObservers observers;

  /** Creates a new instance of BoardCommunication */
  public Communication(
      Unit p_unit,
      int p_resolution,
      SpecctraParserInfo p_specctra_parser_info,
      CoordinateTransform p_coordinate_transform,
      IdentificationNumberGenerator p_id_no_generator,
      BoardObservers p_observers) {
    coordinateTransform = p_coordinate_transform;
    unit = p_unit;
    resolution = p_resolution;
    specctraParserInfo = p_specctra_parser_info;
    idNoGenerator = p_id_no_generator;
    observers = p_observers;
  }

  /** Creates a new instance of BoardCommunication */
  public Communication() {
    this(
        Unit.MIL,
        1,
        new SpecctraParserInfo("\"", null, null, null, null, false),
        new CoordinateTransform(1, 0, 0),
        new ItemIdentificationNumberGenerator(),
        new BoardObserverAdaptor());
  }

  public boolean host_cad_is_eagle() {
    return specctraParserInfo != null
        && specctraParserInfo.hostCad != null
        && "CadSoft".equalsIgnoreCase(specctraParserInfo.hostCad);
  }

  public boolean host_is_old_kicad() {
    if ((specctraParserInfo == null)
        || (specctraParserInfo.hostCad == null)
        || (specctraParserInfo.hostVersion == null)) {
      return false;
    }

    if (specctraParserInfo.hostCad.toLowerCase().contains("kicad")) {
      String versionString = specctraParserInfo.hostVersion;
      Matcher matcher = Pattern.compile("\\d+").matcher(versionString);
      if (matcher.find()) {
        int versionNumber = Integer.parseInt(matcher.group());
        return versionNumber <= 5;
      }
    }

    return false;
  }

  public boolean host_cad_exists() {
    return specctraParserInfo != null && specctraParserInfo.hostCad != null;
  }

  /** Returns the resolution scaled to the input unit */
  public double get_resolution(Unit p_unit) {
    return Unit.scale(this.resolution, p_unit, this.unit);
  }

  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    p_stream.defaultReadObject();
    observers = new BoardObserverAdaptor();
  }

  /**
   * Information from the parser scope in a Specctra-dsn-file. The fields are optional and may be
   * null.
   */
  public static class SpecctraParserInfo implements Serializable {

    /** Character for quoting strings in a dsn-File. */
    public final String stringQuote;

    public final String hostCad;
    public final String hostVersion;
    public final Collection<String[]> constants;
    public final WriteResolution writeResolution;
    public final boolean dsnFileGeneratedByHost;

    public SpecctraParserInfo(
        String p_string_quote,
        String p_host_cad,
        String p_host_version,
        Collection<String[]> p_constants,
        WriteResolution p_write_resolution,
        boolean p_dsn_file_generated_by_host) {
      stringQuote = p_string_quote;
      hostCad = p_host_cad;
      hostVersion = p_host_version;
      constants = p_constants;
      writeResolution = p_write_resolution;
      dsnFileGeneratedByHost = p_dsn_file_generated_by_host;
    }

    public static class WriteResolution implements Serializable {

      public final String charName;
      public final int positiveInt;

      public WriteResolution(String p_char_name, int p_positive_int) {
        charName = p_char_name;
        positiveInt = p_positive_int;
      }
    }
  }
}
