package app.freerouting.io.specctra.parser;

import app.freerouting.core.library.LogicalParts;
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

  public static void writeScope(WriteScopeParameter scopeParameter) throws IOException {
    LogicalParts logicalParts = scopeParameter.board.library.logicalParts;
    if (logicalParts.count() <= 0) {
      return;
    }
    scopeParameter.file.startScope();
    scopeParameter.file.write("part_library");

    // write the logical part mappings

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.library.LogicalPart currentPart = logicalParts.get(i);
      scopeParameter.file.startScope();
      scopeParameter.file.write("logical_part_mapping ");
      scopeParameter.identifierType.write(currentPart.name, scopeParameter.file);
      scopeParameter.file.newLine();
      scopeParameter.file.write("(comp");
      for (int j = 1; j <= scopeParameter.board.components.count(); j++) {
        app.freerouting.board.model.structure.Component currentComponent =
            scopeParameter.board.components.get(j);
        if (currentComponent.getLogicalPart() == currentPart) {
          scopeParameter.file.write(" ");
          scopeParameter.file.write(currentComponent.name);
        }
      }
      scopeParameter.file.write(")");
      scopeParameter.file.endScope();
    }

    // write the logical parts.

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.library.LogicalPart currentPart = logicalParts.get(i);

      scopeParameter.file.startScope();
      scopeParameter.file.write("logicalPart ");
      scopeParameter.identifierType.write(currentPart.name, scopeParameter.file);
      scopeParameter.file.newLine();
      for (int j = 0; j < currentPart.pinCount(); j++) {
        scopeParameter.file.newLine();
        app.freerouting.core.library.LogicalPart.PartPin currentPin = currentPart.getPin(j);
        scopeParameter.file.write("(pin ");
        scopeParameter.identifierType.write(currentPin.pinName, scopeParameter.file);
        scopeParameter.file.write(" 0 ");
        scopeParameter.identifierType.write(currentPin.gateName, scopeParameter.file);
        scopeParameter.file.write(" ");
        final int gateSwapCode = currentPin.gateSwapCode;
        scopeParameter.file.write(String.valueOf(gateSwapCode));
        scopeParameter.file.write(" ");
        scopeParameter.identifierType.write(currentPin.gatePinName, scopeParameter.file);
        scopeParameter.file.write(" ");
        int gatePinSwapCode = currentPin.gatePinSwapCode;
        scopeParameter.file.write(String.valueOf(gatePinSwapCode));
        scopeParameter.file.write(")");
      }
      scopeParameter.file.endScope();
    }
    scopeParameter.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter scopeParameter) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scopeParameter.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_scope: unexpected end of file at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == LOGICAL_PART_MAPPING) {
          LogicalPartMapping nextMapping = readLogicalPartMapping(scopeParameter.scanner);
          if (nextMapping == null) {
            return false;
          }
          scopeParameter.logicalPartMappings.add(nextMapping);
        } else if (nextToken == LOGICAL_PART) {
          LogicalPart nextPart = readLogicalPart(scopeParameter.scanner);
          if (nextPart == null) {
            return false;
          }
          scopeParameter.logicalParts.add(nextPart);
        } else {
          skipScope(scopeParameter.scanner);
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
          PartPin currentPartPin = readPartPin(scanner);
          if (currentPartPin == null) {
            return null;
          }
          partPins.add(currentPartPin);
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
            "PartLibrary.read_part_pin: string expected at '" + scanner.getScopeIdentifier() + "'");
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
            "PartLibrary.read_part_pin: string expected at '" + scanner.getScopeIdentifier() + "'");
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
            "PartLibrary.read_part_pin: string expected at '" + scanner.getScopeIdentifier() + "'");
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
