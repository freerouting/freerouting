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
      Unit pUnit,
      int pResolution,
      SpecctraParserInfo pSpecctraParserInfo,
      CoordinateTransform pCoordinateTransform,
      IdentificationNumberGenerator pIdNoGenerator,
      BoardObservers pObservers) {
    coordinateTransform = pCoordinateTransform;
    unit = pUnit;
    resolution = pResolution;
    specctraParserInfo = pSpecctraParserInfo;
    idNoGenerator = pIdNoGenerator;
    observers = pObservers;
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

  public boolean hostCadIsEagle() {
    return specctraParserInfo != null
        && specctraParserInfo.hostCad != null
        && "CadSoft".equalsIgnoreCase(specctraParserInfo.hostCad);
  }

  public boolean hostIsOldKicad() {
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

  public boolean hostCadExists() {
    return specctraParserInfo != null && specctraParserInfo.hostCad != null;
  }

  /** Returns the resolution scaled to the input unit */
  public double getResolution(Unit pUnit) {
    return Unit.scale(this.resolution, pUnit, this.unit);
  }

  private void readObject(ObjectInputStream pStream) throws IOException, ClassNotFoundException {
    pStream.defaultReadObject();
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
        String pStringQuote,
        String pHostCad,
        String pHostVersion,
        Collection<String[]> pConstants,
        WriteResolution pWriteResolution,
        boolean pDsnFileGeneratedByHost) {
      stringQuote = pStringQuote;
      hostCad = pHostCad;
      hostVersion = pHostVersion;
      constants = pConstants;
      writeResolution = pWriteResolution;
      dsnFileGeneratedByHost = pDsnFileGeneratedByHost;
    }

    public static class WriteResolution implements Serializable {

      public final String charName;
      public final int positiveInt;

      public WriteResolution(String pCharName, int pPositiveInt) {
        charName = pCharName;
        positiveInt = pPositiveInt;
      }
    }
  }
}
