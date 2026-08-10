package app.freerouting.io.specctra.parser;

import app.freerouting.board.Component;
import app.freerouting.core.LogicalParts;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class PartLibrary extends ScopeKeyword {

  /** Creates a new instance of PartLibrary. */
  public PartLibrary() {
    super("part_library");
  }

  public static void writeScope(WriteScopeParameter par) throws IOException {
    LogicalParts logicalParts = par.board.library.logicalParts;
    if (logicalParts.count() <= 0) {
      return;
    }
    par.file.startScope();
    par.file.write("part_library");

    // write the logical part mappings

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.LogicalPart currPart = logicalParts.get(i);
      par.file.startScope();
      par.file.write("logical_part_mapping ");
      par.identifierType.write(currPart.name, par.file);
      par.file.newLine();
      par.file.write("(comp");
      for (int j = 1; j <= par.board.components.count(); j++) {
        Component currComponent = par.board.components.get(j);
        if (currComponent.getLogicalPart() == currPart) {
          par.file.write(" ");
          par.file.write(currComponent.name);
        }
      }
      par.file.write(")");
      par.file.endScope();
    }

    // write the logical parts.

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.LogicalPart currPart = logicalParts.get(i);

      par.file.startScope();
      par.file.write("logicalPart ");
      par.identifierType.write(currPart.name, par.file);
      par.file.newLine();
      for (int j = 0; j < currPart.pinCount(); j++) {
        par.file.newLine();
        app.freerouting.core.LogicalPart.PartPin currPin = currPart.getPin(j);
        par.file.write("(pin ");
        par.identifierType.write(currPin.pinName, par.file);
        par.file.write(" 0 ");
        par.identifierType.write(currPin.gateName, par.file);
        par.file.write(" ");
        final int gateSwapCode = currPin.gateSwapCode;
        par.file.write(String.valueOf(gateSwapCode));
        par.file.write(" ");
        par.identifierType.write(currPin.gatePinName, par.file);
        par.file.write(" ");
        int gatePinSwapCode = currPin.gatePinSwapCode;
        par.file.write(String.valueOf(gatePinSwapCode));
        par.file.write(")");
      }
      par.file.endScope();
    }
    par.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == LOGICAL_PART_MAPPING) {
          LogicalPartMapping nextMapping = readLogicalPartMapping(par.scanner);
          if (nextMapping == null) {
            return false;
          }
          par.logicalPartMappings.add(nextMapping);
        } else if (nextToken == LOGICAL_PART) {
          LogicalPart nextPart = readLogicalPart(par.scanner);
          if (nextPart == null) {
            return false;
          }
          par.logicalParts.add(nextPart);
        } else {
          skipScope(par.scanner);
        }
      }
    }
    return true;
  }

  /** Reads the component list of a logical part mapping. Returns null, if an error occurred. */
  private LogicalPartMapping readLogicalPartMapping(IJFlexScanner scanner) {
    try {
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: string expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = scanner.nextToken();
      if (nextToken != OPEN_BRACKET) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: open bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = scanner.nextToken();
      if (nextToken != COMPONENT_SCOPE) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: Keyword.COMPONENT_SCOPE expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      SortedSet<String> result = new TreeSet<>();
      for (; ; ) {
        scanner.yybegin(SpecctraDsnStreamReader.NAME);
        nextToken = scanner.nextToken();
        if (nextToken == CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "PartLibrary.read_logical_part_mapping: string expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        result.add((String) nextToken);
      }
      nextToken = scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return new LogicalPartMapping(name, result);
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_logical_part_mapping: IO error scanning file", e);
      return null;
    }
  }

  private LogicalPart readLogicalPart(IJFlexScanner scanner) {
    final Collection<PartPin> partPins = new LinkedList<>();
    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e);
      return null;
    }
    if (!(nextToken instanceof String partName)) {
      FRLogger.warn(
          "PartLibrary.read_logical_part: string expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    scanner.setScopeIdentifier(partName);
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_logical_part: unexpected end of file at '"
                + scanner.getScopeIdentifier()
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
          PartPin currPartPin = readPartPin(scanner);
          if (currPartPin == null) {
            return null;
          }
          partPins.add(currPartPin);
        } else {
          skipScope(scanner);
        }
      }
      if (!readOk) {
        return null;
      }
    }
    return new LogicalPart(partName, partPins);
  }

  private PartPin readPartPin(IJFlexScanner scanner) {
    try {
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String pinName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      scanner.setScopeIdentifier(pinName);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof String gateName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      scanner.setScopeIdentifier(gateName);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      final int gateSwapCode = (Integer) nextToken;
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof String gatePinName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      scanner.setScopeIdentifier(gatePinName);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int gatePinSwapCode = (Integer) nextToken;
      // overread subgates
      do {
        nextToken = scanner.nextToken();
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

    private LogicalPartMapping(String name, SortedSet<String> components) {
      this.name = name;
      this.components = components;
    }
  }

  public static final class PartPin {

    public final String pinName;
    public final String gateName;
    public final int gateSwapCode;
    public final String gatePinName;
    public final int gatePinSwapCode;

    private PartPin(
        String pinName,
        String gateName,
        int gateSwapCode,
        String gatePinName,
        int gatePinSwapCode) {
      this.pinName = pinName;
      this.gateName = gateName;
      this.gateSwapCode = gateSwapCode;
      this.gatePinName = gatePinName;
      this.gatePinSwapCode = gatePinSwapCode;
    }
  }

  public static final class LogicalPart {

    /** The name of the mapping. */
    public final String name;

    /** The pins of this logical part. */
    public final Collection<PartPin> partPins;

    private LogicalPart(String name, Collection<PartPin> partPins) {
      this.name = name;
      this.partPins = partPins;
    }
  }
}
