package app.freerouting.autoroute.decoupling;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.rules.Net;
import app.freerouting.rules.Nets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Single-pass index over a board's pins and components, computing CSR-indexed structures and
 * aggregated net evidence without performing expensive O(N*M) board scans.
 */
public final class BoardIndex {

  /** Topological and electrical evidence gathered for a specific net. */
  public record NetEvidence(
      int netNumber,
      String netName,
      int pinCount,
      int icPinCount,
      int icCount,
      int capDegree,
      boolean plane,
      boolean nameHint,
      int xtalLikePins,
      int connectorLikePins,
      double groundScore) {}

  private static final Pattern NON_CAP_NAME_PATTERN =
      Pattern.compile(
          "(?i)^(?:R|RN|RT|RP|L|FB|FL|BLM|IND|D|LED|Q|FET|TR|SW|S|BTN|KEY|TACT|PUSH|SWITCH|BUTTON|J|JP|HDR|P|TP|PIN|CONN|Y|X|XTAL|OSC|F|FUSE|RV|MOV|VR|RESET|K)[0-9_\\-].*");

  private final int[] componentIds;
  private final String[] componentNames;
  private final int[] componentPinOffsets;
  private final Pin[] componentPins;
  private final ComponentType[] componentTypes;
  private final boolean[] componentXtalLike;
  private final List<Integer> twoPinComponentIds;

  private final int[] netNumbers;
  private final int[] netPinOffsets;
  private final Pin[] netPins;
  private final NetEvidence[] netEvidences;

  private final int totalBoardComponents;
  private final int totalBoardPins;

  private BoardIndex(
      int[] componentIds,
      String[] componentNames,
      int[] componentPinOffsets,
      Pin[] componentPins,
      ComponentType[] componentTypes,
      boolean[] componentXtalLike,
      List<Integer> twoPinComponentIds,
      int[] netNumbers,
      int[] netPinOffsets,
      Pin[] netPins,
      NetEvidence[] netEvidences,
      int totalBoardComponents,
      int totalBoardPins) {
    this.componentIds = componentIds;
    this.componentNames = componentNames;
    this.componentPinOffsets = componentPinOffsets;
    this.componentPins = componentPins;
    this.componentTypes = componentTypes;
    this.componentXtalLike = componentXtalLike;
    this.twoPinComponentIds = Collections.unmodifiableList(twoPinComponentIds);
    this.netNumbers = netNumbers;
    this.netPinOffsets = netPinOffsets;
    this.netPins = netPins;
    this.netEvidences = netEvidences;
    this.totalBoardComponents = totalBoardComponents;
    this.totalBoardPins = totalBoardPins;
  }

  /**
   * Constructs a new BoardIndex by performing a single linear pass over {@code board.getPins()}.
   *
   * @param board the routing board to index
   * @param config configuration parameters containing ground name patterns and overrides
   * @return indexed board topology
   */
  public static BoardIndex build(BasicBoard board, IdentifierConfig config) {
    Collection<Pin> pins = board != null ? board.getPins() : List.of();
    if (pins == null || pins.isEmpty()) {
      return empty();
    }

    int totalPins = pins.size();

    // 1. First pass: collect distinct component IDs and normal net numbers
    Set<Integer> uniqueCompIds = new HashSet<>();
    Set<Integer> uniqueNetNums = new HashSet<>();

    for (Pin pin : pins) {
      int compId = pin.getComponentId();
      if (compId > 0) {
        uniqueCompIds.add(compId);
      }
      for (int i = 0; i < pin.netCount(); i++) {
        int net = pin.getNetNumber(i);
        if (Nets.isNormalNetNumber(net)) {
          uniqueNetNums.add(net);
        }
      }
    }

    int[] compIds = uniqueCompIds.stream().mapToInt(Integer::intValue).toArray();
    Arrays.sort(compIds);

    int[] netNums = uniqueNetNums.stream().mapToInt(Integer::intValue).toArray();
    Arrays.sort(netNums);

    // 2. Count pins per component and per net
    int[] compPinCounts = new int[compIds.length];
    int[] netPinCounts = new int[netNums.length];
    int validCompPinsTotal = 0;
    int validNetPinsTotal = 0;

    for (Pin pin : pins) {
      int compId = pin.getComponentId();
      if (compId > 0) {
        int ci = Arrays.binarySearch(compIds, compId);
        if (ci >= 0) {
          compPinCounts[ci]++;
          validCompPinsTotal++;
        }
      }
      for (int i = 0; i < pin.netCount(); i++) {
        int net = pin.getNetNumber(i);
        if (Nets.isNormalNetNumber(net)) {
          int ni = Arrays.binarySearch(netNums, net);
          if (ni >= 0) {
            netPinCounts[ni]++;
            validNetPinsTotal++;
          }
        }
      }
    }

    // 3. Build CSR offsets
    int[] compOffsets = new int[compIds.length + 1];
    for (int i = 0; i < compIds.length; i++) {
      compOffsets[i + 1] = compOffsets[i] + compPinCounts[i];
    }

    int[] netOffsets = new int[netNums.length + 1];
    for (int i = 0; i < netNums.length; i++) {
      netOffsets[i + 1] = netOffsets[i] + netPinCounts[i];
    }

    // 4. Fill CSR pin arrays
    Pin[] compPins = new Pin[validCompPinsTotal];
    int[] currentCompWrite = Arrays.copyOf(compOffsets, compOffsets.length);

    Pin[] nPins = new Pin[validNetPinsTotal];
    int[] currentNetWrite = Arrays.copyOf(netOffsets, netOffsets.length);

    for (Pin pin : pins) {
      int compId = pin.getComponentId();
      if (compId > 0) {
        int ci = Arrays.binarySearch(compIds, compId);
        if (ci >= 0) {
          compPins[currentCompWrite[ci]++] = pin;
        }
      }
      for (int i = 0; i < pin.netCount(); i++) {
        int net = pin.getNetNumber(i);
        if (Nets.isNormalNetNumber(net)) {
          int ni = Arrays.binarySearch(netNums, net);
          if (ni >= 0) {
            nPins[currentNetWrite[ni]++] = pin;
          }
        }
      }
    }

    // Sort pins within each component by pin ID for deterministic ordering
    for (int ci = 0; ci < compIds.length; ci++) {
      int start = compOffsets[ci];
      int end = compOffsets[ci + 1];
      Arrays.sort(compPins, start, end, (p1, p2) -> Integer.compare(p1.getId(), p2.getId()));
    }

    // 5. Classify components and cache names
    String[] compNames = new String[compIds.length];
    for (int i = 0; i < compIds.length; i++) {
      Component comp =
          (board != null && board.components != null) ? board.components.get(compIds[i]) : null;
      compNames[i] = (comp != null && comp.name != null) ? comp.name.trim() : "";
    }

    ComponentType[] compTypes = new ComponentType[compIds.length];
    boolean[] compXtal = new boolean[compIds.length];
    List<Integer> twoPinList = new ArrayList<>();

    for (int ci = 0; ci < compIds.length; ci++) {
      int start = compOffsets[ci];
      int end = compOffsets[ci + 1];
      List<Pin> cPins = Arrays.asList(Arrays.copyOfRange(compPins, start, end));
      ComponentTyper.TypingResult tr = ComponentTyper.classify(cPins, compNames[ci]);
      compTypes[ci] = tr.type();
      compXtal[ci] = tr.isXtalLike();

      if (tr.type() == ComponentType.TWO_PIN_SMD) {
        twoPinList.add(compIds[ci]);
      }
    }

    // 6. Aggregate Net Evidence
    NetEvidence[] evidences = new NetEvidence[netNums.length];
    int maxCapDegree = 0;
    int[] pinCountsForPercentile = new int[netNums.length];

    for (int ni = 0; ni < netNums.length; ni++) {
      int netNum = netNums[ni];
      int start = netOffsets[ni];
      int end = netOffsets[ni + 1];
      int pinCount = end - start;
      pinCountsForPercentile[ni] = pinCount;

      int icPinCount = 0;
      Set<Integer> distinctIcs = new HashSet<>();
      Set<Integer> distinctTwoPinSmds = new HashSet<>();
      int xtalLikePins = 0;
      int connectorLikePins = 0;

      for (int pIdx = start; pIdx < end; pIdx++) {
        Pin p = nPins[pIdx];
        int compId = p.getComponentId();
        if (compId > 0) {
          int ci = Arrays.binarySearch(compIds, compId);
          if (ci >= 0) {
            ComponentType ct = compTypes[ci];
            if (ct == ComponentType.IC) {
              icPinCount++;
              distinctIcs.add(compId);
            } else if (ct == ComponentType.TWO_PIN_SMD) {
              String cName = compNames[ci];
              if (!NON_CAP_NAME_PATTERN.matcher(cName).matches()) {
                distinctTwoPinSmds.add(compId);
              }
            } else if (ct == ComponentType.CONNECTOR_LIKE) {
              connectorLikePins++;
            }
            if (compXtal[ci]) {
              xtalLikePins++;
            }
          }
        }
      }

      int capDegree = distinctTwoPinSmds.size();
      if (capDegree > maxCapDegree) {
        maxCapDegree = capDegree;
      }

      Net netObj =
          (board.rules != null && board.rules.nets != null) ? board.rules.nets.get(netNum) : null;
      boolean plane = netObj != null && netObj.containsPlane();
      String netName = netObj != null ? netObj.name : "";
      boolean nameHint = matchesGroundName(netName, config.groundNameHints());

      evidences[ni] =
          new NetEvidence(
              netNum,
              netName,
              pinCount,
              icPinCount,
              distinctIcs.size(),
              capDegree,
              plane,
              nameHint,
              xtalLikePins,
              connectorLikePins,
              0.0);
    }

    // Calculate 95th percentile pin count with floor of 4
    Arrays.sort(pinCountsForPercentile);
    int p95Index = (int) Math.round(0.95 * (pinCountsForPercentile.length - 1));
    int p95PinCount =
        pinCountsForPercentile.length > 0 ? Math.max(4, pinCountsForPercentile[p95Index]) : 4;

    // 7. Compute ground scores
    for (int ni = 0; ni < netNums.length; ni++) {
      NetEvidence e = evidences[ni];
      double groundScore;
      if (config.forcedGroundNets().contains(e.netNumber())) {
        groundScore = 100.0;
      } else {
        double capRatio =
            maxCapDegree > 0 ? Math.min(1.0, (double) e.capDegree() / maxCapDegree) : 0.0;
        double pinRatio =
            p95PinCount > 0 ? Math.min(1.0, (double) e.pinCount() / p95PinCount) : 0.0;
        groundScore =
            35.0 * (e.nameHint() ? 1.0 : 0.0)
                + 25.0 * (e.plane() ? 1.0 : 0.0)
                + 25.0 * capRatio
                + 15.0 * pinRatio;
        groundScore = Math.clamp(groundScore, 0.0, 100.0);
      }
      evidences[ni] =
          new NetEvidence(
              e.netNumber(),
              e.netName(),
              e.pinCount(),
              e.icPinCount(),
              e.icCount(),
              e.capDegree(),
              e.plane(),
              e.nameHint(),
              e.xtalLikePins(),
              e.connectorLikePins(),
              groundScore);
    }

    return new BoardIndex(
        compIds,
        compNames,
        compOffsets,
        compPins,
        compTypes,
        compXtal,
        twoPinList,
        netNums,
        netOffsets,
        nPins,
        evidences,
        compIds.length,
        totalPins);
  }

  private static boolean matchesGroundName(String name, List<Pattern> patterns) {
    if (name == null || name.isEmpty() || patterns == null) {
      return false;
    }
    for (Pattern pattern : patterns) {
      if (pattern.matcher(name).matches()) {
        return true;
      }
    }
    return false;
  }

  private static BoardIndex empty() {
    return new BoardIndex(
        new int[0],
        new String[0],
        new int[1],
        new Pin[0],
        new ComponentType[0],
        new boolean[0],
        List.of(),
        new int[0],
        new int[1],
        new Pin[0],
        new NetEvidence[0],
        0,
        0);
  }

  /** Returns all component IDs typed as TWO_PIN_SMD. */
  public List<Integer> getTwoPinComponentIds() {
    return twoPinComponentIds;
  }

  /** Returns the reference designator/name for a component, or an empty string if not found. */
  public String getComponentName(int componentId) {
    int ci = Arrays.binarySearch(componentIds, componentId);
    return (ci >= 0 && ci < componentNames.length) ? componentNames[ci] : "";
  }

  /** Returns all pins belonging to a component, or an empty list if not found. */
  public List<Pin> getPinsOfComponent(int componentId) {
    int ci = Arrays.binarySearch(componentIds, componentId);
    if (ci < 0) {
      return List.of();
    }
    int start = componentPinOffsets[ci];
    int end = componentPinOffsets[ci + 1];
    return Arrays.asList(Arrays.copyOfRange(componentPins, start, end));
  }

  /** Returns the structural classification for a component. */
  public ComponentType getComponentType(int componentId) {
    int ci = Arrays.binarySearch(componentIds, componentId);
    if (ci < 0) {
      return ComponentType.OTHER;
    }
    return componentTypes[ci];
  }

  /** Returns whether a component was identified as XTAL_LIKE. */
  public boolean isComponentXtalLike(int componentId) {
    int ci = Arrays.binarySearch(componentIds, componentId);
    if (ci < 0) {
      return false;
    }
    return componentXtalLike[ci];
  }

  /** Returns the aggregated evidence for a net number, or null if the net has no pins. */
  public NetEvidence getNetEvidence(int netNumber) {
    int ni = Arrays.binarySearch(netNumbers, netNumber);
    if (ni < 0) {
      return null;
    }
    return netEvidences[ni];
  }

  /** Returns the ground score (0 to 100) for a net number. */
  public double getGroundScore(int netNumber) {
    NetEvidence e = getNetEvidence(netNumber);
    return e != null ? e.groundScore() : 0.0;
  }

  /** Returns all pins connected to a net. */
  public List<Pin> getPinsOfNet(int netNumber) {
    int ni = Arrays.binarySearch(netNumbers, netNumber);
    if (ni < 0) {
      return List.of();
    }
    int start = netPinOffsets[ni];
    int end = netPinOffsets[ni + 1];
    return Arrays.asList(Arrays.copyOfRange(netPins, start, end));
  }

  /** Total count of distinct components on the board with valid pins. */
  public int totalComponents() {
    return totalBoardComponents;
  }

  /** Total pins on the board. */
  public int totalPins() {
    return totalBoardPins;
  }
}
