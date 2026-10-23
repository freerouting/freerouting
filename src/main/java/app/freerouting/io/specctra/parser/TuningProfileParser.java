package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.TrackPropagation;
import app.freerouting.rules.TuningProfile;
import app.freerouting.rules.TuningProfileType;
import app.freerouting.rules.ViaDelayOverride;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and serializes Specctra DSN {@code (tuning_profile ...)} scopes and their child components
 * (track propagation, reference planes, via delays, and overrides).
 */
public final class TuningProfileParser {

  private TuningProfileParser() {}

  /**
   * Reads a {@link TuningProfile} scope from the DSN stream.
   *
   * @param scanner active DSN scanner
   * @param transform coordinate transform to convert lengths from DSN units to board units
   * @return populated TuningProfile or {@code null} on syntax/parse error
   */
  public static TuningProfile readScope(IJFlexScanner scanner, CoordinateTransform transform) {
    try {
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      String profileName = scanner.nextString();
      if (profileName == null || profileName.isBlank()) {
        FRLogger.warn(
            "TuningProfileParser.readScope: profile name missing at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }

      TuningProfileType type = TuningProfileType.SINGLE;
      boolean enableTimeDomainTuning = false;
      double targetImpedanceOhms = 0.0;
      List<TrackPropagation> trackPropagations = new ArrayList<>();
      double globalViaUnitDelay = 0.0;
      List<ViaDelayOverride> viaDelayOverrides = new ArrayList<>();

      Object nextToken = null;
      for (; ; ) {
        final Object prevToken = nextToken;
        nextToken = scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "TuningProfileParser.readScope: unexpected EOF at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }

        if (prevToken == Keyword.OPEN_BRACKET) {
          if (Keyword.matches(nextToken, Keyword.TYPE)
              || Keyword.matches(nextToken, Keyword.PATTERN_TYPE)) {
            String typeStr = readStringValue(scanner);
            if (typeStr != null) {
              type = TuningProfileType.parse(typeStr);
            }
          } else if (Keyword.matches(nextToken, Keyword.TIME_DOMAIN)) {
            Boolean td = readBooleanValue(scanner);
            if (td != null) {
              enableTimeDomainTuning = td;
            }
          } else if (Keyword.matches(nextToken, Keyword.TARGET_IMPEDANCE)) {
            Double imp = readDoubleValue(scanner);
            if (imp != null) {
              targetImpedanceOhms = imp;
            }
          } else if (Keyword.matches(nextToken, Keyword.TRACK_PROPAGATION)) {
            readTrackPropagationScope(scanner, transform, trackPropagations);
          } else if (Keyword.matches(nextToken, Keyword.VIA_PROPAGATION)) {
            globalViaUnitDelay =
                readViaPropagationScope(scanner, viaDelayOverrides, globalViaUnitDelay);
          } else {
            ScopeKeyword.skipScope(scanner);
          }
        }
      }

      return new TuningProfile(
          profileName,
          type,
          targetImpedanceOhms,
          enableTimeDomainTuning,
          trackPropagations,
          globalViaUnitDelay,
          viaDelayOverrides);
    } catch (IOException e) {
      FRLogger.error("TuningProfileParser.readScope: IO error scanning file", e);
      return null;
    }
  }

  /**
   * Reads an enclosing {@code (tuning_profiles ...)} scope containing one or more {@code
   * (tuning_profile ...)} definitions.
   *
   * @param scanner active DSN scanner
   * @param transform coordinate transform for trace width scaling
   * @return list of parsed TuningProfiles
   */
  public static List<TuningProfile> readMultipleProfilesScope(
      IJFlexScanner scanner, CoordinateTransform transform) {
    List<TuningProfile> result = new ArrayList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("TuningProfileParser.readMultipleProfilesScope: IO error", e);
        return result;
      }
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.TUNING_PROFILE)) {
          TuningProfile profile = readScope(scanner, transform);
          if (profile != null) {
            result.add(profile);
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    return result;
  }

  private static void readTrackPropagationScope(
      IJFlexScanner scanner,
      CoordinateTransform transform,
      List<TrackPropagation> trackPropagations)
      throws IOException {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = scanner.nextToken();
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.LAYER_RULE)) {
          readLayerRuleTrackPropagation(scanner, transform, trackPropagations);
        } else if (Keyword.matches(nextToken, new Keyword("entry"))) {
          readEntryTrackPropagation(scanner, transform, trackPropagations);
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
  }

  private static void readLayerRuleTrackPropagation(
      IJFlexScanner scanner,
      CoordinateTransform transform,
      List<TrackPropagation> trackPropagations)
      throws IOException {
    scanner.yybegin(SpecctraDsnStreamReader.LAYER_NAME);
    String signalLayer = scanner.nextString();
    if (signalLayer == null || signalLayer.isBlank()) {
      ScopeKeyword.skipScope(scanner);
      return;
    }

    String topRef = null;
    String bottomRef = null;
    double trackWidth = 0.0;
    double unitDelay = 0.0;

    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = scanner.nextToken();
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.RULE) {
          RuleParameters rules = readRuleParameters(scanner, transform);
          if (rules.width > 0) {
            trackWidth = rules.width;
          }
          if (rules.timeLengthFactor > 0) {
            unitDelay = rules.timeLengthFactor;
          }
        } else if (Keyword.matches(nextToken, Keyword.TOP_REFERENCE)) {
          topRef = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.BOTTOM_REFERENCE)) {
          bottomRef = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.WIDTH)) {
          Double w = readDoubleValue(scanner);
          if (w != null && w > 0) {
            trackWidth = (transform != null) ? transform.dsnToBoard(w) : w;
          }
        } else if (Keyword.matches(nextToken, Keyword.TIME_LENGTH_FACTOR)
            || Keyword.matches(nextToken, Keyword.UNIT_DELAY)) {
          Double d = readDoubleValue(scanner);
          if (d != null && d > 0) {
            unitDelay = d;
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }

    trackPropagations.add(
        new TrackPropagation(signalLayer, topRef, bottomRef, trackWidth, unitDelay));
  }

  private static void readEntryTrackPropagation(
      IJFlexScanner scanner,
      CoordinateTransform transform,
      List<TrackPropagation> trackPropagations)
      throws IOException {
    String signalLayer = null;
    String topRef = null;
    String bottomRef = null;
    double trackWidth = 0.0;
    double unitDelay = 0.0;

    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = scanner.nextToken();
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.SIGNAL_LAYER)
            || Keyword.matches(nextToken, Keyword.LAYER)) {
          signalLayer = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.TOP_REFERENCE)) {
          topRef = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.BOTTOM_REFERENCE)) {
          bottomRef = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.WIDTH)) {
          Double w = readDoubleValue(scanner);
          if (w != null && w > 0) {
            trackWidth = (transform != null) ? transform.dsnToBoard(w) : w;
          }
        } else if (Keyword.matches(nextToken, Keyword.TIME_LENGTH_FACTOR)
            || Keyword.matches(nextToken, Keyword.UNIT_DELAY)) {
          Double d = readDoubleValue(scanner);
          if (d != null && d > 0) {
            unitDelay = d;
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }

    if (signalLayer != null && !signalLayer.isBlank()) {
      trackPropagations.add(
          new TrackPropagation(signalLayer, topRef, bottomRef, trackWidth, unitDelay));
    }
  }

  private static double readViaPropagationScope(
      IJFlexScanner scanner, List<ViaDelayOverride> overrides, double currentGlobalDelay)
      throws IOException {
    double globalDelay = currentGlobalDelay;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = scanner.nextToken();
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.GLOBAL_UNIT_DELAY)) {
          Double d = readDoubleValue(scanner);
          if (d != null && d >= 0) {
            globalDelay = d;
          }
        } else if (Keyword.matches(nextToken, Keyword.OVERRIDE)
            || Keyword.matches(nextToken, Keyword.VIA_DELAY_OVERRIDE)) {
          ViaDelayOverride override = readViaDelayOverride(scanner);
          if (override != null) {
            overrides.add(override);
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    return globalDelay;
  }

  private static ViaDelayOverride readViaDelayOverride(IJFlexScanner scanner) throws IOException {
    String sigFrom = null;
    String sigTo = null;
    String viaFrom = null;
    String viaTo = null;
    double delay = 0.0;

    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = scanner.nextToken();
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.SIGNAL_LAYERS)) {
          String[] layers = readStringPair(scanner);
          if (layers != null) {
            sigFrom = layers[0];
            sigTo = layers[1];
          }
        } else if (Keyword.matches(nextToken, Keyword.VIA_LAYERS)) {
          String[] layers = readStringPair(scanner);
          if (layers != null) {
            viaFrom = layers[0];
            viaTo = layers[1];
          }
        } else if (Keyword.matches(nextToken, Keyword.SIGNAL_LAYER_FROM)
            || Keyword.matches(nextToken, Keyword.SIGNAL_FROM)) {
          sigFrom = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.SIGNAL_LAYER_TO)
            || Keyword.matches(nextToken, Keyword.SIGNAL_TO)) {
          sigTo = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.VIA_LAYER_FROM)
            || Keyword.matches(nextToken, Keyword.VIA_FROM)) {
          viaFrom = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.VIA_LAYER_TO)
            || Keyword.matches(nextToken, Keyword.VIA_TO)) {
          viaTo = readStringValue(scanner);
        } else if (Keyword.matches(nextToken, Keyword.DELAY)) {
          Double d = readDoubleValue(scanner);
          if (d != null) {
            delay = d;
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }

    if (sigFrom != null && sigTo != null && viaFrom != null && viaTo != null) {
      return new ViaDelayOverride(sigFrom, sigTo, viaFrom, viaTo, delay);
    }
    return null;
  }

  private static String[] readStringPair(IJFlexScanner scanner) {
    scanner.yybegin(SpecctraDsnStreamReader.NAME);
    String first = scanner.nextString();
    scanner.yybegin(SpecctraDsnStreamReader.NAME);
    String second = scanner.nextString();
    skipToClosedBracket(scanner);
    if (first != null && second != null) {
      return new String[] {first, second};
    }
    return null;
  }

  private static String readStringValue(IJFlexScanner scanner) {
    scanner.yybegin(SpecctraDsnStreamReader.NAME);
    String str = scanner.nextString();
    skipToClosedBracket(scanner);
    return str;
  }

  private static Double readDoubleValue(IJFlexScanner scanner) {
    try {
      Object token = scanner.nextToken();
      Double val = null;
      if (token instanceof Double d) {
        val = d;
      } else if (token instanceof Integer i) {
        val = i.doubleValue();
      }
      skipToClosedBracket(scanner);
      return val;
    } catch (IOException e) {
      FRLogger.error("TuningProfileParser.readDoubleValue: error", e);
      return null;
    }
  }

  private static Boolean readBooleanValue(IJFlexScanner scanner) {
    String str = readStringValue(scanner);
    if (str == null) {
      return null;
    }
    return str.equalsIgnoreCase("on")
        || str.equalsIgnoreCase("true")
        || str.equalsIgnoreCase("yes")
        || str.equalsIgnoreCase("1");
  }

  private static void skipToClosedBracket(IJFlexScanner scanner) {
    for (; ; ) {
      try {
        Object token = scanner.nextToken();
        if (token == null || token == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (token == Keyword.OPEN_BRACKET) {
          ScopeKeyword.skipScope(scanner);
        }
      } catch (IOException e) {
        FRLogger.error("TuningProfileParser.skipToClosedBracket: IO error", e);
        break;
      }
    }
  }

  private static RuleParameters readRuleParameters(
      IJFlexScanner scanner, CoordinateTransform transform) throws IOException {
    double width = 0.0;
    double timeFactor = 0.0;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = scanner.nextToken();
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.WIDTH)) {
          Double w = readDoubleValue(scanner);
          if (w != null && w > 0) {
            width = (transform != null) ? transform.dsnToBoard(w) : w;
          }
        } else if (Keyword.matches(nextToken, Keyword.TIME_LENGTH_FACTOR)
            || Keyword.matches(nextToken, Keyword.UNIT_DELAY)) {
          Double d = readDoubleValue(scanner);
          if (d != null && d > 0) {
            timeFactor = d;
          }
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    return new RuleParameters(width, timeFactor);
  }

  private record RuleParameters(double width, double timeLengthFactor) {}

  /**
   * Serializes a tuning profile into Specctra DSN format.
   *
   * @param profile profile to write
   * @param file writer destination
   * @param identifierType identifier quoting handler
   * @param transform coordinate transform for trace width scaling
   * @throws IOException on write error
   */
  public static void writeScope(
      TuningProfile profile,
      IndentFileWriter file,
      IdentifierType identifierType,
      CoordinateTransform transform)
      throws IOException {
    if (profile == null) {
      return;
    }

    file.startScope();
    file.write("tuning_profile ");
    identifierType.write(profile.name(), file);
    file.newLine();

    String typeStr = profile.type() == TuningProfileType.DIFFERENTIAL ? "differential" : "single";
    file.write("(type " + typeStr + ")");
    file.newLine();

    file.write("(time_domain " + (profile.enableTimeDomainTuning() ? "on" : "off") + ")");
    file.newLine();

    if (profile.targetImpedanceOhms() > 0) {
      file.write("(target_impedance " + profile.targetImpedanceOhms() + ")");
      file.newLine();
    }

    if (!profile.trackPropagations().isEmpty()) {
      file.startScope();
      file.write("track_propagation");
      for (TrackPropagation tp : profile.trackPropagations()) {
        file.newLine();
        file.startScope();
        file.write("layer_rule ");
        identifierType.write(tp.signalLayer(), file);
        file.newLine();

        file.startScope();
        file.write("rule");
        if (tp.trackWidth() > 0) {
          double dsnWidth =
              (transform != null) ? transform.boardToDsn(tp.trackWidth()) : tp.trackWidth();
          file.write(" (width " + dsnWidth + ")");
        }
        if (tp.timeLengthFactor() > 0) {
          file.write(" (time_length_factor " + tp.timeLengthFactor() + ")");
        }
        file.endScope();

        file.newLine();
        file.write("(top_reference ");
        if (tp.topReference() != null) {
          identifierType.write(tp.topReference(), file);
        } else {
          file.write("none");
        }
        file.write(")");
        file.newLine();
        file.write("(bottom_reference ");
        if (tp.bottomReference() != null) {
          identifierType.write(tp.bottomReference(), file);
        } else {
          file.write("none");
        }
        file.write(")");
        file.endScope();
      }
      file.endScope();
      file.newLine();
    }

    if (profile.globalViaUnitDelay() > 0 || !profile.viaDelayOverrides().isEmpty()) {
      file.startScope();
      file.write("via_propagation");
      if (profile.globalViaUnitDelay() > 0) {
        file.newLine();
        file.write("(global_unit_delay " + profile.globalViaUnitDelay() + ")");
      }
      for (ViaDelayOverride vo : profile.viaDelayOverrides()) {
        file.newLine();
        file.startScope();
        file.write("override");
        file.newLine();
        file.write("(signal_layers ");
        identifierType.write(vo.signalLayerFrom(), file);
        file.write(" ");
        identifierType.write(vo.signalLayerTo(), file);
        file.write(")");
        file.newLine();
        file.write("(via_layers ");
        identifierType.write(vo.viaLayerFrom(), file);
        file.write(" ");
        identifierType.write(vo.viaLayerTo(), file);
        file.write(")");
        file.newLine();
        file.write("(delay " + vo.delayPs() + ")");
        file.endScope();
      }
      file.endScope();
    }

    file.endScope();
  }
}
