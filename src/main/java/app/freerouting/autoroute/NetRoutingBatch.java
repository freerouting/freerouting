package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.Via;
import java.util.*;

/**
 * Represents a batch of nets that can be routed in parallel.
 * Nets in the same batch share no pins or vias, so can be routed independently.
 */
public class NetRoutingBatch {

  public final List<Integer> netNumbers;
  public final List<Item> itemsToRoute;

  public NetRoutingBatch(List<Integer> p_netNumbers, List<Item> p_itemsToRoute) {
    this.netNumbers = p_netNumbers;
    this.itemsToRoute = p_itemsToRoute;
  }

  /**
   * Partition items into independent batches where nets in each batch don't share pins/vias.
   * Nets without shared connection points can be routed in parallel safely.
   */
  public static List<NetRoutingBatch> partitionIntoBatches(List<Item> p_items) {
    List<NetRoutingBatch> batches = new ArrayList<>();
    Set<Integer> assignedNets = new HashSet<>();
    Set<Integer> usedPins = new HashSet<>();

    // Greedy packing: for each unassigned net, try to add compatible nets to batch
    for (Item item : p_items) {
      for (int i = 0; i < item.net_count(); i++) {
        int netNo = item.get_net_no(i);
        if (assignedNets.contains(netNo)) {
          continue; // Already assigned
        }

        // Start new batch with this net
        List<Integer> batchNets = new ArrayList<>();
        List<Item> batchItems = new ArrayList<>();
        Set<Integer> batchUsedPins = new HashSet<>();

        batchNets.add(netNo);
        assignedNets.add(netNo);
        addNetItemsToBatch(item, netNo, batchItems, batchUsedPins);

        // Try to add more nets to this batch if they don't conflict
        for (Item candidate : p_items) {
          for (int j = 0; j < candidate.net_count(); j++) {
            int candidateNet = candidate.get_net_no(j);
            if (assignedNets.contains(candidateNet)) {
              continue;
            }

            // Check if candidate net shares pins with batch
            if (canAddNetToBatch(candidate, candidateNet, batchUsedPins)) {
              batchNets.add(candidateNet);
              assignedNets.add(candidateNet);
              addNetItemsToBatch(candidate, candidateNet, batchItems, batchUsedPins);
            }
          }
        }

        if (!batchNets.isEmpty()) {
          batches.add(new NetRoutingBatch(batchNets, batchItems));
        }
      }
    }

    return batches;
  }

  private static void addNetItemsToBatch(
      Item p_item, int p_netNo, List<Item> p_batchItems, Set<Integer> p_usedPins) {
    p_batchItems.add(p_item);

    // Track pin IDs used by this net to detect conflicts
    if (p_item instanceof Pin pin) {
      p_usedPins.add(pin.get_id_no());
    } else if (p_item instanceof Via via) {
      p_usedPins.add(via.get_id_no());
    }
  }

  private static boolean canAddNetToBatch(
      Item p_item, int p_netNo, Set<Integer> p_usedPins) {
    // Check if item shares pins with any already-batched net
    if (p_item instanceof Pin pin) {
      return !p_usedPins.contains(pin.get_id_no());
    } else if (p_item instanceof Via via) {
      return !p_usedPins.contains(via.get_id_no());
    }
    return true; // Unknown item type, assume compatible
  }
}
