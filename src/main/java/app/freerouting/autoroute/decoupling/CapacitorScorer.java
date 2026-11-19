package app.freerouting.autoroute.decoupling;

import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.rules.Nets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Executes hard gating checks, two-pass relative scoring, and Pass B filtered-island detection to
 * classify two-pin SMD capacitor candidates.
 */
public final class CapacitorScorer {

  // Scoring constants
  public static final int SCORE_STRONG_GROUND = 30;
  public static final int SCORE_OWNER_IN_RADIUS = 20;
  public static final int SCORE_MULTI_IC_RAIL = 15;
  public static final int SCORE_PARALLEL_SIBLING = 10;
  public static final int SCORE_SHUNT_CAP_DEGREE = 10;
  public static final int SCORE_FILTERED_ISLAND = 25;

  public static final int PENALTY_XTAL = -30;
  public static final int PENALTY_CONNECTOR = -25;
  public static final int PENALTY_FEW_PINS = -25;
  public static final int PENALTY_AMBIGUOUS_GROUND = -20;

  public static final double GROUND_MARGIN_THRESHOLD = 15.0;

  private static final Pattern INDUCTOR_BEAD_PATTERN =
      Pattern.compile("(?i)^(L|FB|FL|BLM|IND)[0-9_].*");

  private static final Pattern RESISTOR_PATTERN = Pattern.compile("(?i)^(R|RN)[0-9_].*");

  private static final Pattern NON_CAP_NAME_PATTERN =
      Pattern.compile(
          "(?i)^(?:R|RN|RT|RP|L|FB|FL|BLM|IND|D|LED|Q|FET|TR|SW|S|BTN|KEY|TACT|PUSH|SWITCH|BUTTON|J|JP|HDR|P|TP|PIN|CONN|Y|X|XTAL|OSC|F|FUSE|RV|MOV|VR|RESET|K)[0-9_\\-].*");

  private static final Pattern POWER_NET_EXCLUDE_PATTERN =
      Pattern.compile(
          "(?i).*(?:PWR_FLAG|VSW|LINE|NEUT|EARTH|CHASSIS|GND|VSS|V-0|V-PE|V-[LN]|PRI_).*");

  private static final Pattern POWER_NET_PATTERN =
      Pattern.compile(
          "(?i)^(?:.*[._\\-/])?"
              + "(?:[+-]?(?:\\d+[vV][a-z0-9]*|\\d+\\.\\d+[vV][a-z0-9]*|\\d+[cfpCFP])"
              + "|V-[+-]"
              + "|[+-]?(?:BATT?(?:ERY)?|VDC|VEE|VDD[a-z0-9]*|VCC[a-z0-9]*|AVDD[a-z0-9]*|VDDA[a-z0-9]*|VDDF[a-z0-9]*"
              + "|VMEM[a-z0-9]*|VPP[a-z0-9]*|VCCQ[a-z0-9]*|VAA[a-z0-9]*|VAC|VCOM[a-z0-9]*|VREF[a-z0-9]*"
              + "|VBUS[a-z0-9]*|CORE[a-z0-9]*|POWER[a-z0-9_]*|PWR[a-z0-9_]*|VDRIVE[a-z0-9]*|VD|VS|HT)"
              + ")(?:[._\\-/].*)?$");

  /** Returns true if the given net name conforms to standard EDA power rail naming conventions. */
  public static boolean isPowerNetName(String netName) {
    if (netName == null || netName.isEmpty()) {
      return false;
    }
    if (POWER_NET_EXCLUDE_PATTERN.matcher(netName).matches()) {
      return false;
    }
    return POWER_NET_PATTERN.matcher(netName).matches();
  }

  /**
   * Returns true if the component reference designator indicates an explicit non-capacitor
   * (resistor, diode, switch, inductor, connector, etc.).
   */
  public static boolean isNonCapacitorName(String compName) {
    return compName != null
        && !compName.isEmpty()
        && NON_CAP_NAME_PATTERN.matcher(compName).matches();
  }

  private CapacitorScorer() {}

  /** Internal mutable evaluation holder during scoring passes. */
  public static final class EvaluationState {
    public final int componentId;
    public final Pin pinA;
    public final Pin pinB;
    public final int netA;
    public final int netB;
    public final int layer;
    public final long padAreaA;
    public final long padAreaB;

    public int railNet = -1;
    public int groundNet = -1;
    public int score = 0;
    public Verdict verdict = Verdict.REJECTED;
    public long reasonMask = 0L;
    public boolean gatePassed = false;
    public List<OwnerCandidate> owners = List.of();
    public AuditResult audit = AuditResult.NOT_RUN;

    public EvaluationState(
        int componentId,
        Pin pinA,
        Pin pinB,
        int netA,
        int netB,
        int layer,
        long padAreaA,
        long padAreaB) {
      this.componentId = componentId;
      this.pinA = pinA;
      this.pinB = pinB;
      this.netA = netA;
      this.netB = netB;
      this.layer = layer;
      this.padAreaA = padAreaA;
      this.padAreaB = padAreaB;
    }

    public CapacitorCandidate toCandidate() {
      return new CapacitorCandidate(
          componentId,
          pinA.getId(),
          pinB.getId(),
          netA,
          netB,
          railNet,
          groundNet,
          layer,
          padAreaA,
          padAreaB,
          score,
          verdict,
          reasonMask,
          audit,
          owners);
    }
  }

  /**
   * Evaluates all two-pin SMD candidates on the board through gates and Pass A scoring.
   *
   * @param boardIndex the indexed board topology
   * @param config scoring thresholds and overrides
   * @param resolutionMm internal board units per millimeter
   * @return list of evaluated states
   */
  public static List<EvaluationState> evaluatePassA(
      BoardIndex boardIndex, IdentifierConfig config, double resolutionMm) {
    List<Integer> compIds = boardIndex.getTwoPinComponentIds();
    List<EvaluationState> states = new ArrayList<>(compIds.size());

    // 1. Evaluate hard gates
    for (int compId : compIds) {
      List<Pin> pins = boardIndex.getPinsOfComponent(compId);
      if (pins.size() != 2) {
        continue;
      }

      Pin p0 = pins.get(0);
      Pin p1 = pins.get(1);
      // Guarantee deterministic pin ordering pinIdA < pinIdB
      Pin pinA = p0.getId() < p1.getId() ? p0 : p1;
      Pin pinB = p0.getId() < p1.getId() ? p1 : p0;

      int netA = pinA.netCount() > 0 ? pinA.getNetNumber(0) : 0;
      int netB = pinB.netCount() > 0 ? pinB.getNetNumber(0) : 0;
      int layer = pinA.firstLayer();

      long padAreaA = getPadArea(pinA);
      long padAreaB = getPadArea(pinB);

      EvaluationState state =
          new EvaluationState(compId, pinA, pinB, netA, netB, layer, padAreaA, padAreaB);

      // Gate G0: Explicit non-capacitor reference designators (resistors, switches, diodes, etc.)
      String compName = boardIndex.getComponentName(compId);
      if (isNonCapacitorName(compName)) {
        state.reasonMask |= ReasonCode.USER_EXCLUDED.mask();
        states.add(state);
        continue;
      }

      // Gate G5: User exclusions
      if (config.forcedRejectComponents().contains(compId)) {
        state.reasonMask |= ReasonCode.USER_EXCLUDED.mask();
        states.add(state);
        continue;
      }

      // Gate G1: SMD on same layer
      boolean isSmd =
          pinA.firstLayer() == pinA.lastLayer()
              && pinB.firstLayer() == pinB.lastLayer()
              && pinA.firstLayer() == pinB.firstLayer();
      if (!isSmd) {
        state.reasonMask |= ReasonCode.NOT_TWO_PIN_SMD.mask();
        states.add(state);
        continue;
      }

      // Gate G2: Valid, distinct normal nets
      if (!Nets.isNormalNetNumber(netA) || !Nets.isNormalNetNumber(netB) || netA == netB) {
        state.reasonMask |= ReasonCode.SAME_OR_NO_NET.mask();
        states.add(state);
        continue;
      }

      // Gate G3: Pad symmetry
      long minArea = Math.min(padAreaA, padAreaB);
      long maxArea = Math.max(padAreaA, padAreaB);
      if (maxArea <= 0 || ((double) minArea / (double) maxArea) < config.minPadAreaRatio()) {
        state.reasonMask |= ReasonCode.ASYMMETRIC_PADS.mask();
        states.add(state);
        continue;
      }

      // Gate G4: Center-to-center pitch within bounds
      long pitchSq = computeDistanceSquared(pinA.getCenter(), pinB.getCenter());
      long minPitchUnits = Math.round(config.minPitchMm() * resolutionMm);
      long maxPitchUnits = Math.round(config.maxPitchMm() * resolutionMm);
      if (pitchSq < (minPitchUnits * minPitchUnits) || pitchSq > (maxPitchUnits * maxPitchUnits)) {
        state.reasonMask |= ReasonCode.PITCH_OUT_OF_RANGE.mask();
        states.add(state);
        continue;
      }

      state.gatePassed = true;
      states.add(state);
    }

    // 2. Count parallel sibling pairs for gate survivors and tally rail capacitor counts
    Map<Long, Integer> pairCounts = new HashMap<>();
    Map<Integer, Integer> railCapCounts = new HashMap<>();

    for (EvaluationState state : states) {
      if (state.gatePassed) {
        long key = packNetPair(state.netA, state.netB);
        pairCounts.put(key, pairCounts.getOrDefault(key, 0) + 1);

        double groundScoreA = boardIndex.getGroundScore(state.netA);
        double groundScoreB = boardIndex.getGroundScore(state.netB);
        double groundDiff = groundScoreA - groundScoreB;
        if (Math.abs(groundDiff) >= GROUND_MARGIN_THRESHOLD) {
          int railNet = groundDiff > 0 ? state.netB : state.netA;
          railCapCounts.put(railNet, railCapCounts.getOrDefault(railNet, 0) + 1);
        }
      }
    }

    // 3. Score Pass A
    for (EvaluationState state : states) {
      if (!state.gatePassed) {
        state.verdict = Verdict.REJECTED;
        state.score = 0;
        continue;
      }

      int score = 0;
      long reasons = 0L;

      // Ground resolution
      double groundScoreA = boardIndex.getGroundScore(state.netA);
      double groundScoreB = boardIndex.getGroundScore(state.netB);
      double groundDiff = groundScoreA - groundScoreB;
      double margin = Math.abs(groundDiff);

      boolean ambiguousGround = false;
      if (margin >= GROUND_MARGIN_THRESHOLD) {
        if (groundDiff > 0) {
          state.groundNet = state.netA;
          state.railNet = state.netB;
        } else {
          state.groundNet = state.netB;
          state.railNet = state.netA;
        }
        score += SCORE_STRONG_GROUND;
        reasons |= ReasonCode.STRONG_GROUND.mask();
      } else {
        ambiguousGround = true;
        reasons |= ReasonCode.AMBIGUOUS_GROUND.mask();
        score += PENALTY_AMBIGUOUS_GROUND;
        // Assign provisional sides for evidence lookup
        if (groundDiff >= 0) {
          state.groundNet = state.netA;
          state.railNet = state.netB;
        } else {
          state.groundNet = state.netB;
          state.railNet = state.netA;
        }
      }

      // Parallel siblings
      long key = packNetPair(state.netA, state.netB);
      if (pairCounts.getOrDefault(key, 0) >= 2) {
        score += SCORE_PARALLEL_SIBLING;
        reasons |= ReasonCode.PARALLEL_SIBLING.mask();
      }

      // Rail-side evidence
      BoardIndex.NetEvidence railEvidence = boardIndex.getNetEvidence(state.railNet);
      if (railEvidence != null) {
        if (railEvidence.icCount() >= 2 || railEvidence.plane()) {
          score += SCORE_MULTI_IC_RAIL;
          reasons |= ReasonCode.MULTI_IC_RAIL.mask();
        }
        if (railEvidence.capDegree() >= 2) {
          score += SCORE_SHUNT_CAP_DEGREE;
          reasons |= ReasonCode.SHUNT_CAP_DEGREE.mask();
        }
        if (railEvidence.xtalLikePins() > 0
            && railEvidence.icCount() <= 1
            && !railEvidence.plane()) {
          score += PENALTY_XTAL;
          reasons |= ReasonCode.XTAL_PENALTY.mask();
        }
        if (railEvidence.connectorLikePins() > 0 && railEvidence.icPinCount() <= 1) {
          score += PENALTY_CONNECTOR;
          reasons |= ReasonCode.CONNECTOR_PENALTY.mask();
        }
        if (railEvidence.icPinCount() <= 1
            && railEvidence.pinCount() <= 3
            && !railEvidence.plane()) {
          score += PENALTY_FEW_PINS;
          reasons |= ReasonCode.FEW_PINS_PENALTY.mask();
        }
      }

      state.score = Math.clamp(score, 0, 100);
      state.reasonMask = reasons;

      if (ambiguousGround) {
        // Ambiguous ground candidates cannot achieve DECOUPLING verdict
        state.verdict = state.score >= config.reviewThreshold() ? Verdict.REVIEW : Verdict.REJECTED;
        state.groundNet = -1;
        state.railNet = -1;
      } else if (state.score >= config.decouplingThreshold()) {
        state.verdict = Verdict.DECOUPLING;
      } else if (state.score >= config.reviewThreshold()) {
        state.verdict = Verdict.REVIEW;
      } else {
        state.verdict = Verdict.REJECTED;
      }
    }

    return states;
  }

  /**
   * Applies Pass B filtered-island bonus (+25 points) to candidates on local supply islands fed by
   * a series passive from a strong Pass A rail.
   *
   * @param states the evaluated states from Pass A
   * @param boardIndex the indexed board topology
   * @param config scoring thresholds
   */
  public static void applyPassB(
      List<EvaluationState> states, BoardIndex boardIndex, IdentifierConfig config) {
    // 1. Identify all strong rails from Pass A DECOUPLING candidates
    Set<Integer> strongRails = new HashSet<>();
    for (EvaluationState state : states) {
      if (state.verdict == Verdict.DECOUPLING && state.railNet > 0) {
        strongRails.add(state.railNet);
      }
    }

    if (strongRails.isEmpty()) {
      return;
    }

    // Map each component to its evaluation state and tally rail capacitor counts
    Map<Integer, EvaluationState> stateByCompId = new HashMap<>();
    Map<Integer, Integer> railCapCounts = new HashMap<>();
    for (EvaluationState state : states) {
      stateByCompId.put(state.componentId, state);
      if (state.gatePassed && state.railNet > 0 && state.groundNet > 0) {
        railCapCounts.put(state.railNet, railCapCounts.getOrDefault(state.railNet, 0) + 1);
      }
    }

    // 2. Evaluate candidates in REVIEW or REJECTED that have a resolved ground
    for (EvaluationState state : states) {
      if (state.verdict != Verdict.DECOUPLING
          && state.gatePassed
          && state.groundNet > 0
          && state.railNet > 0) {
        // Check 2-pin passives on this rail net (excluding the candidate itself)
        List<Pin> railPins = boardIndex.getPinsOfNet(state.railNet);
        int seriesPassiveCount = 0;
        int seriesPassiveCompId = -1;
        boolean connectedToStrongRail = false;

        for (Pin pin : railPins) {
          int compId = pin.getComponentId();
          if (compId > 0 && compId != state.componentId) {
            if (boardIndex.getComponentType(compId) == ComponentType.TWO_PIN_SMD) {
              seriesPassiveCount++;
              seriesPassiveCompId = compId;
              List<Pin> otherPins = boardIndex.getPinsOfComponent(compId);
              for (Pin otherPin : otherPins) {
                for (int i = 0; i < otherPin.netCount(); i++) {
                  int otherNet = otherPin.getNetNumber(i);
                  if (otherNet != state.railNet && strongRails.contains(otherNet)) {
                    connectedToStrongRail = true;
                  }
                }
              }
            }
          }
        }

        // Exactly one series passive connecting this rail net to a strong rail
        if (seriesPassiveCount == 1 && connectedToStrongRail && seriesPassiveCompId > 0) {
          String seriesCompName = boardIndex.getComponentName(seriesPassiveCompId);
          boolean isInductorOrBead = INDUCTOR_BEAD_PATTERN.matcher(seriesCompName).matches();
          boolean isResistor = RESISTOR_PATTERN.matcher(seriesCompName).matches();

          BoardIndex.NetEvidence railEv = boardIndex.getNetEvidence(state.railNet);
          String railNetName = (railEv != null && railEv.netName() != null) ? railEv.netName() : "";
          int railCapCount = railCapCounts.getOrDefault(state.railNet, 0);
          boolean hasPowerCharacteristics =
              railEv != null
                  && (railEv.capDegree() >= 2
                      || railEv.icCount() >= 2
                      || railEv.plane()
                      || isPowerNetName(railNetName));

          boolean qualifies =
              isInductorOrBead
                  || (!isResistor
                      && (hasPowerCharacteristics || (railEv != null && railEv.icPinCount() >= 1)))
                  || (isResistor && hasPowerCharacteristics);

          if (qualifies) {
            state.score = Math.clamp(state.score + SCORE_FILTERED_ISLAND, 0, 100);
            state.reasonMask |= ReasonCode.FILTERED_ISLAND.mask();

            if (state.score >= config.decouplingThreshold()) {
              state.verdict = Verdict.DECOUPLING;
            } else if (state.score >= config.reviewThreshold()) {
              state.verdict = Verdict.REVIEW;
            }
          }
        }
      }
    }
  }

  private static long packNetPair(int net1, int net2) {
    int min = Math.min(net1, net2);
    int max = Math.max(net1, net2);
    return (((long) min) << 32) | (((long) max) & 0xFFFFFFFFL);
  }

  private static long getPadArea(Pin pin) {
    IntBox box = pin.boundingBox();
    return box != null ? (long) box.area() : 0L;
  }

  private static long computeDistanceSquared(Point p1, Point p2) {
    if (p1 == null || p2 == null) {
      return 0L;
    }
    long dx = getCoord(p1, true) - getCoord(p2, true);
    long dy = getCoord(p1, false) - getCoord(p2, false);
    return dx * dx + dy * dy;
  }

  private static long getCoord(Point p, boolean isX) {
    if (p instanceof IntPoint ip) {
      return isX ? ip.x : ip.y;
    }
    return Math.round(isX ? p.toFloat().x : p.toFloat().y);
  }
}
