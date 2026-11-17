package app.freerouting.autoroute.scope;

import app.freerouting.autoroute.decoupling.CapacitorCandidate;
import app.freerouting.autoroute.decoupling.DecouplingIdentifier;
import app.freerouting.autoroute.decoupling.IdentificationResult;
import app.freerouting.autoroute.decoupling.IdentifierConfig;
import app.freerouting.autoroute.decoupling.Verdict;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterScopeSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compiles {@link RouterScopeSettings} into an immutable {@link RoutingScopeMask} and
 * thread-confined {@link RoutingScopeContext}.
 */
public final class RoutingScopeCompiler {

  private RoutingScopeCompiler() {}

  /**
   * Compiles scope settings against the board.
   *
   * @param board the routing board
   * @param settings user-configured scope settings (may be null)
   * @return active {@link RoutingScopeContext}, {@link RoutingScopeContext#EMPTY_NO_OP}, or {@link
   *     RoutingScopeContext#UNSCOPED}
   */
  public static RoutingScopeContext compile(RoutingBoard board, RouterScopeSettings settings) {
    if (board == null
        || settings == null
        || settings.components == null
        || settings.components.length == 0) {
      return RoutingScopeContext.UNSCOPED;
    }

    // Clean and normalize requested component designator strings
    List<String> requestedNames = new ArrayList<>();
    for (String raw : settings.components) {
      if (raw != null) {
        String trimmed = raw.trim();
        if (!trimmed.isEmpty() && !"NONE".equalsIgnoreCase(trimmed)) {
          requestedNames.add(trimmed);
        }
      }
    }

    if (requestedNames.isEmpty()) {
      return RoutingScopeContext.UNSCOPED;
    }

    Set<Integer> selectedComponentIds = new HashSet<>();
    Set<String> matchedNames = new HashSet<>();
    int maxComponentId = 0;

    for (Component comp : board.components.getAll()) {
      if (comp == null) {
        continue;
      }
      maxComponentId = Math.max(maxComponentId, comp.id);
      for (String req : requestedNames) {
        if (comp.name != null && comp.name.equalsIgnoreCase(req)) {
          if (!comp.isPlaced()) {
            FRLogger.warn("Scope component '" + comp.name + "' is not placed on board; skipping.");
          } else {
            selectedComponentIds.add(comp.id);
            matchedNames.add(req.toUpperCase(Locale.ROOT));
          }
        }
      }
    }

    for (String req : requestedNames) {
      if (!matchedNames.contains(req.toUpperCase(Locale.ROOT))) {
        FRLogger.warn("Scope component '" + req + "' not found on board; ignoring.");
      }
    }

    if (selectedComponentIds.isEmpty()) {
      FRLogger.warn(
          "None of the requested scope components "
              + requestedNames
              + " were found or placed on board. No-op exit.");
      return RoutingScopeContext.EMPTY_NO_OP;
    }

    // Decoupling companion auto-enrollment
    if (Boolean.TRUE.equals(settings.includeDecouplingCompanions)) {
      try {
        IdentificationResult decoupling =
            DecouplingIdentifier.identify(board, IdentifierConfig.defaultConfig());
        if (decoupling != null && decoupling.candidates() != null) {
          for (CapacitorCandidate cand : decoupling.candidates()) {
            if (cand.verdict() == Verdict.DECOUPLING && !cand.owners().isEmpty()) {
              int ownerCompId = cand.owners().getFirst().componentId();
              if (selectedComponentIds.contains(ownerCompId)) {
                selectedComponentIds.add(cand.componentId());
                maxComponentId = Math.max(maxComponentId, cand.componentId());
                FRLogger.info(
                    "Auto-enrolled decoupling companion component ID "
                        + cand.componentId()
                        + " for selected IC component ID "
                        + ownerCompId);
              }
            }
          }
        }
      } catch (Exception e) {
        FRLogger.warn("Decoupling companion discovery failed: " + e.getMessage());
      }
    }

    // Enumerate anchor pins and candidate nets
    Collection<Item> allItems = board.getItems();
    int maxItemId = 0;
    for (Item item : allItems) {
      if (item != null) {
        maxItemId = Math.max(maxItemId, item.getId());
      }
    }

    int maxNetNo = board.rules.nets != null ? board.rules.nets.maxNetNumber() : 1024;
    long[] selectedComponentsBits = new long[(maxComponentId >>> 6) + 1];
    long[] anchorPinIdsBits = new long[(maxItemId >>> 6) + 1];
    long[] scopedNetsBits = new long[(maxNetNo >>> 6) + 1];
    Set<Integer> scopedNetNumbersSet = new HashSet<>();

    for (int compId : selectedComponentIds) {
      selectedComponentsBits[compId >>> 6] |= (1L << (compId & 63));
    }

    int anchorPinCount = 0;
    for (Item item : allItems) {
      if (item instanceof Pin pin && selectedComponentIds.contains(pin.getComponentId())) {
        int id = pin.getId();
        anchorPinIdsBits[id >>> 6] |= (1L << (id & 63));
        anchorPinCount++;
        for (int i = 0; i < pin.netCount(); i++) {
          int netNo = pin.getNetNumber(i);
          if (netNo > 0 && netNo <= maxNetNo) {
            scopedNetsBits[netNo >>> 6] |= (1L << (netNo & 63));
            scopedNetNumbersSet.add(netNo);
          }
        }
      }
    }

    if (anchorPinCount == 0) {
      FRLogger.warn("Selected components " + requestedNames + " have 0 routable pins. No-op exit.");
      return RoutingScopeContext.EMPTY_NO_OP;
    }

    int[] scopedNetNumbers =
        scopedNetNumbersSet.stream().mapToInt(Integer::intValue).sorted().toArray();

    // Snapshot pre-existing foreign baseline items on shared nets
    long[] foreignBaselineItemBits = new long[(maxItemId >>> 6) + 1];
    for (Item item : allItems) {
      if (item == null || item.netCount() <= 0) {
        continue;
      }
      int netNo = item.getNetNumber(0);
      if (netNo > 0
          && netNo <= maxNetNo
          && (scopedNetsBits[netNo >>> 6] & (1L << (netNo & 63))) != 0L) {
        boolean isAnchorPin =
            (item instanceof Pin pin) && selectedComponentIds.contains(pin.getComponentId());
        if (!isAnchorPin) {
          int id = item.getId();
          if (id > 0) {
            if ((id >>> 6) >= foreignBaselineItemBits.length) {
              foreignBaselineItemBits = Arrays.copyOf(foreignBaselineItemBits, (id >>> 6) + 1);
            }
            foreignBaselineItemBits[id >>> 6] |= (1L << (id & 63));
          }
        }
      }
    }

    // Snapshot baseline connected set counts
    int[] baselineConnectedSetCounts = new int[maxNetNo + 1];
    for (int netNo = 1; netNo <= maxNetNo; netNo++) {
      if (board.rules.nets != null && board.rules.nets.get(netNo) != null) {
        baselineConnectedSetCounts[netNo] = board.getConnectedSets(netNo).size();
      }
    }

    MultiPinScopePolicy multiPinPolicy = MultiPinScopePolicy.of(settings.multiPinPolicy);
    ForeignTracePolicy foreignTracePolicy = ForeignTracePolicy.of(settings.foreignTracePolicy);
    double foreignRipupMultiplier =
        settings.foreignRipupCostMultiplier != null ? settings.foreignRipupCostMultiplier : 10.0;
    int nudgeOnlyPasses = settings.nudgeOnlyPasses != null ? settings.nudgeOnlyPasses : 2;

    RoutingScopeMask mask =
        new RoutingScopeMask(
            true,
            selectedComponentsBits,
            anchorPinIdsBits,
            scopedNetsBits,
            scopedNetNumbers,
            multiPinPolicy,
            foreignTracePolicy,
            foreignRipupMultiplier,
            nudgeOnlyPasses);

    FRLogger.info(
        String.format(
            "Compiled selective routing scope: %d component(s), %d anchor pin(s), %d net(s) in scope (%s, %s, %dx ripup penalty)",
            selectedComponentIds.size(),
            anchorPinCount,
            scopedNetNumbers.length,
            multiPinPolicy,
            foreignTracePolicy,
            (int) foreignRipupMultiplier));

    return new RoutingScopeContext(
        mask, false, foreignBaselineItemBits, baselineConnectedSetCounts);
  }
}
