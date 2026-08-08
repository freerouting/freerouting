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

  public static void writeScope(WriteScopeParameter pPar) throws IOException {
    LogicalParts logicalParts = pPar.board.library.logicalParts;
    if (logicalParts.count() <= 0) {
      return;
    }
    pPar.file.startScope();
    pPar.file.write("part_library");

    // write the logical part mappings

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.LogicalPart currPart = logicalParts.get(i);
      pPar.file.startScope();
      pPar.file.write("logical_part_mapping ");
      pPar.identifierType.write(currPart.name, pPar.file);
      pPar.file.newLine();
      pPar.file.write("(comp");
      for (int j = 1; j <= pPar.board.components.count(); j++) {
        Component currComponent = pPar.board.components.get(j);
        if (currComponent.getLogicalPart() == currPart) {
          pPar.file.write(" ");
          pPar.file.write(currComponent.name);
        }
      }
      pPar.file.write(")");
      pPar.file.endScope();
    }

    // write the logical parts.

    for (int i = 1; i <= logicalParts.count(); i++) {
      app.freerouting.core.LogicalPart currPart = logicalParts.get(i);

      pPar.file.startScope();
      pPar.file.write("logicalPart ");
      pPar.identifierType.write(currPart.name, pPar.file);
      pPar.file.newLine();
      for (int j = 0; j < currPart.pinCount(); j++) {
        pPar.file.newLine();
        app.freerouting.core.LogicalPart.PartPin currPin = currPart.getPin(j);
        pPar.file.write("(pin ");
        pPar.identifierType.write(currPin.pinName, pPar.file);
        pPar.file.write(" 0 ");
        pPar.identifierType.write(currPin.gateName, pPar.file);
        pPar.file.write(" ");
        int gateSwapCode = currPin.gateSwapCode;
        pPar.file.write(String.valueOf(gateSwapCode));
        pPar.file.write(" ");
        pPar.identifierType.write(currPin.gatePinName, pPar.file);
        pPar.file.write(" ");
        int gatePinSwapCode = currPin.gatePinSwapCode;
        pPar.file.write(String.valueOf(gatePinSwapCode));
        pPar.file.write(")");
      }
      pPar.file.endScope();
    }
    pPar.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter pPar) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pPar.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_scope: unexpected end of file at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == LOGICAL_PART_MAPPING) {
          LogicalPartMapping nextMapping = readLogicalPartMapping(pPar.scanner);
          if (nextMapping == null) {
            return false;
          }
          pPar.logicalPartMappings.add(nextMapping);
        } else if (nextToken == LOGICAL_PART) {
          LogicalPart nextPart = readLogicalPart(pPar.scanner);
          if (nextPart == null) {
            return false;
          }
          pPar.logicalParts.add(nextPart);
        } else {
          skipScope(pPar.scanner);
        }
      }
    }
    return true;
  }

  /** Reads the component list of a logical part mapping. Returns null, if an error occurred. */
  private LogicalPartMapping readLogicalPartMapping(IJFlexScanner pScanner) {
    try {
      Object nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: string expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != OPEN_BRACKET) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: open bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != COMPONENT_SCOPE) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: Keyword.COMPONENT_SCOPE expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      SortedSet<String> result = new TreeSet<>();
      for (; ; ) {
        pScanner.yybegin(SpecctraDsnStreamReader.NAME);
        nextToken = pScanner.nextToken();
        if (nextToken == CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "PartLibrary.read_logical_part_mapping: string expected at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        result.add((String) nextToken);
      }
      nextToken = pScanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "PartLibrary.read_logical_part_mapping: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return new LogicalPartMapping(name, result);
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_logical_part_mapping: IO error scanning file", e);
      return null;
    }
  }

  private LogicalPart readLogicalPart(IJFlexScanner pScanner) {
    Collection<PartPin> partPins = new LinkedList<>();
    Object nextToken;
    try {
      nextToken = pScanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e);
      return null;
    }
    if (!(nextToken instanceof String part_name)) {
      FRLogger.warn(
          "PartLibrary.read_logical_part: string expected at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return null;
    }
    pScanner.setScopeIdentifier(part_name);
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pScanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PartLibrary.read_logical_part: unexpected end of file at '"
                + pScanner.getScopeIdentifier()
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
          PartPin currPartPin = readPartPin(pScanner);
          if (currPartPin == null) {
            return null;
          }
          partPins.add(currPartPin);
        } else {
          skipScope(pScanner);
        }
      }
      if (!readOk) {
        return null;
      }
    }
    return new LogicalPart(part_name, partPins);
  }

  private PartPin readPartPin(IJFlexScanner pScanner) {
    try {
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String pinName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      pScanner.setScopeIdentifier(pinName);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String gateName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      pScanner.setScopeIdentifier(gateName);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int gateSwapCode = (Integer) nextToken;
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String gatePinName)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: string expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      pScanner.setScopeIdentifier(gatePinName);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "PartLibrary.read_part_pin: integer expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int gatePinSwapCode = (Integer) nextToken;
      // overread subgates
      do {
        nextToken = pScanner.nextToken();
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

    private LogicalPartMapping(String pName, SortedSet<String> pComponents) {
      name = pName;
      components = pComponents;
    }
  }

  public static final class PartPin {

    public final String pinName;
    public final String gateName;
    public final int gateSwapCode;
    public final String gatePinName;
    public final int gatePinSwapCode;

    private PartPin(
        String pPinName,
        String pGateName,
        int pGateSwapCode,
        String pGatePinName,
        int pGatePinSwapCode) {
      pinName = pPinName;
      gateName = pGateName;
      gateSwapCode = pGateSwapCode;
      gatePinName = pGatePinName;
      gatePinSwapCode = pGatePinSwapCode;
    }
  }

  public static final class LogicalPart {

    /** The name of the mapping. */
    public final String name;

    /** The pins of this logical part */
    public final Collection<PartPin> partPins;

    private LogicalPart(String pName, Collection<PartPin> pPartPins) {
      name = pName;
      partPins = pPartPins;
    }
  }
}
