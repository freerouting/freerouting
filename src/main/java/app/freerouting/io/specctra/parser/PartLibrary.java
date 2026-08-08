package app.freerouting.io.specctra.parser;

import app.freerouting.board.Component;
import app.freerouting.core.LogicalParts;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

public class PartLibrary extends ScopeKeyword {

  /** Creates a new instance of PartLibrary */
  public PartLibrary() {
    super("part_library");
  }

  public static void writeScope(WriteScopeParameter p_par) throws IOException {
    LogicalParts logicalParts = p_par.board.library.logicalParts;
    if (logicalParts.count() <= 0) {
      return;
    }
    p_par.file.startScope();
    p_par.file.write("part_library");

    // write the logical part mappings

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.LogicalPart currPart = logicalParts.get(i);
      p_par.file.startScope();
      p_par.file.write("logical_part_mapping ");
      p_par.identifierType.write(currPart.name, p_par.file);
      p_par.file.newLine();
      p_par.file.write("(comp");
      for (int j = 1; j <= p_par.board.components.count(); j++) {
        Component currComponent = p_par.board.components.get(j);
        if (currComponent.getLogicalPart() == currPart) {
          p_par.file.write(" ");
          p_par.file.write(currComponent.name);
        }
      }
      p_par.file.write(")");
      p_par.file.endScope();
    }

    // write the logical parts.

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.LogicalPart currPart = logicalParts.get(i);

      p_par.file.startScope();
      p_par.file.write("logicalPart ");
      p_par.identifierType.write(currPart.name, p_par.file);
      p_par.file.newLine();
      for (int j = 0; j < currPart.pinCount(); j++) {
        p_par.file.newLine();
        app.freerouting.core.LogicalPart.PartPin currPin = currPart.getPin(j);
        p_par.file.write("(pin ");
        p_par.identifierType.write(currPin.pinName, p_par.file);
        p_par.file.write(" 0 ");
        p_par.identifierType.write(currPin.gateName, p_par.file);
        p_par.file.write(" ");
        int gateSwapCode = currPin.gateSwapCode;
        p_par.file.write(String.valueOf(gateSwapCode));
        p_par.file.write(" ");
        p_par.identifierType.write(currPin.gatePinName, p_par.file);
        p_par.file.write(" ");
        int gatePinSwapCode = currPin.gatePinSwapCode;
        p_par.file.write(String.valueOf(gatePinSwapCode));
        p_par.file.write(")");
      }
      p_par.file.endScope();
    }
    p_par.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter p_par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_scope: unexpected end of file at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == LOGICAL_PART_MAPPING) {
          LogicalPartMapping nextMapping = readLogicalPartMapping(p_par.scanner);
          if (nextMapping == null) {
            return false;
          }
          p_par.logicalPartMappings.add(nextMapping);
        } else if (nextToken == LOGICAL_PART) {
          LogicalPart nextPart = readLogicalPart(p_par.scanner);
          if (nextPart == null) {
            return false;
          }
          p_par.logicalParts.add(nextPart);
        } else {
          skipScope(p_par.scanner);
        }
      }
    }
    return true;
  }

  /** Reads the component list of a logical part mapping. Returns null, if an error occurred. */
  private LogicalPartMapping readLogicalPartMapping(IJFlexScanner p_scanner) {
    try {
      Object nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: string expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = p_scanner.nextToken();
      if (nextToken != OPEN_BRACKET) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: open bracket expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = p_scanner.nextToken();
      if (nextToken != COMPONENT_SCOPE) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: Keyword.COMPONENT_SCOPE expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      SortedSet<String> result = new TreeSet<>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
        nextToken = p_scanner.nextToken();
        if (nextToken == CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "PartLibrary.read_logical_part_mapping: string expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        result.add((String) nextToken);
      }
      nextToken = p_scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: closing bracket expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return new LogicalPartMapping(name, result);
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_logical_part_mapping: IO error scanning file", e);
      return null;
    }
  }

  private LogicalPart readLogicalPart(IJFlexScanner p_scanner) {
    Collection<PartPin> partPins = new LinkedList<>();
    Object nextToken;
    try {
      nextToken = p_scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e);
      return null;
    }
    if (!(nextToken instanceof String part_name)) {
      FRLogger.warn(
          "PartLibrary.read_logical_part: string expected at '"
              + p_scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    p_scanner.setScopeIdentifier(part_name);
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_logical_part: unexpected end of file at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      boolean readOk = true;
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == PIN) {
          PartPin currPartPin = readPartPin(p_scanner);
          if (currPartPin == null) {
            return null;
          }
          partPins.add(currPartPin);
        } else {
          skipScope(p_scanner);
        }
      }
      if (!readOk) {
        return null;
      }
    }
    return new LogicalPart(part_name, partPins);
  }

  private PartPin readPartPin(IJFlexScanner p_scanner) {
    try {
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String pinName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      p_scanner.setScopeIdentifier(pinName);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String gateName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      p_scanner.setScopeIdentifier(gateName);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int gateSwapCode = (Integer) nextToken;
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String gatePinName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      p_scanner.setScopeIdentifier(gatePinName);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int gatePinSwapCode = (Integer) nextToken;
      // overread subgates
      do {
        nextToken = p_scanner.nextToken();
      } while (nextToken != CLOSED_BRACKET);
      return new PartPin(pinName, gateName, gateSwapCode, gatePinName, gatePinSwapCode);
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_part_pin: IO error scanning file", e);
      return null;
    }
  }

  public static final class LogicalPartMapping {

    /** The name of the mapping. */
    public final String name;

    /** The components belonging to the mapping. */
    public final SortedSet<String> components;

    private LogicalPartMapping(String p_name, SortedSet<String> p_components) {
      name = p_name;
      components = p_components;
    }
  }

  public static final class PartPin {

    public final String pinName;
    public final String gateName;
    public final int gateSwapCode;
    public final String gatePinName;
    public final int gatePinSwapCode;

    private PartPin(
        String p_pin_name,
        String p_gate_name,
        int p_gate_swap_code,
        String p_gate_pin_name,
        int p_gate_pin_swap_code) {
      pinName = p_pin_name;
      gateName = p_gate_name;
      gateSwapCode = p_gate_swap_code;
      gatePinName = p_gate_pin_name;
      gatePinSwapCode = p_gate_pin_swap_code;
    }
  }

  public static final class LogicalPart {

    /** The name of the mapping. */
    public final String name;

    /** The pins of this logical part */
    public final Collection<PartPin> partPins;

    private LogicalPart(String p_name, Collection<PartPin> p_part_pins) {
      name = p_name;
      partPins = p_part_pins;
    }
  }
}
