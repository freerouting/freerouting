package app.freerouting.drc;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Incremental incomplete-connection counts. A full {@link
 * DesignRulesChecker#calculateAllIncompletes()} scan builds the ledger once. Later inserts and
 * removals recompute only the touched nets, using the same {@link NetIncompletes} count the full
 * scan uses, so router scores stay on the serial path.
 */
public final class NetRoutingLedger {

  private final BasicBoard board;
  private boolean built;
  private List<Item>[] itemsByNet;
  private int[] incompleteByNet = new int[0];
  private int incompleteTotal;
  private int maximumConnections;
  private final Set<Integer> dirtyNets = new TreeSet<>();

  public NetRoutingLedger(BasicBoard board) {
    this.board = board;
  }

  /** Drops the cache. The next read rebuilds it from the current item list. */
  public synchronized void invalidate() {
    built = false;
    dirtyNets.clear();
    itemsByNet = emptyLists();
    incompleteByNet = new int[0];
    incompleteTotal = 0;
    maximumConnections = 0;
  }

  /** Records an insertion. Ignored until the ledger has been built. */
  public synchronized void noteInserted(Item item) {
    if (!built || !(item instanceof Connectable)) {
      return;
    }
    addItem(item);
  }

  /** Records a removal. Ignored until the ledger has been built. */
  public synchronized void noteRemoved(Item item) {
    if (!built || !(item instanceof Connectable)) {
      return;
    }
    removeItem(item);
  }

  /** Returns the total incomplete-connection count, recomputing dirty nets first. */
  public synchronized int incompleteCount() {
    ensureBuilt();
    flushDirty();
    return incompleteTotal;
  }

  /** Returns the stable maximum connection count from pins and conduction areas. */
  public synchronized int maximumConnections() {
    ensureBuilt();
    return maximumConnections;
  }

  /** Returns every net that still has at least one incomplete connection. */
  public synchronized Set<Integer> incompleteNetNumbers() {
    ensureBuilt();
    flushDirty();
    Set<Integer> result = new TreeSet<>();
    for (int netNumber = 1; netNumber < incompleteByNet.length; netNumber++) {
      if (incompleteByNet[netNumber] > 0) {
        result.add(netNumber);
      }
    }
    return result;
  }

  private void ensureBuilt() {
    if (built) {
      return;
    }
    int maxNet = board.rules.nets.maxNetNumber();
    @SuppressWarnings("unchecked")
    List<Item>[] lists = new List[maxNet + 1];
    itemsByNet = lists;
    incompleteByNet = new int[maxNet + 1];
    for (int netNumber = 1; netNumber <= maxNet; netNumber++) {
      itemsByNet[netNumber] = new ArrayList<>();
    }
    for (Item item : board.getItems()) {
      if (item instanceof Connectable) {
        addItemToLists(item);
      }
    }
    maximumConnections = countMaximumConnections();
    incompleteTotal = 0;
    for (int netNumber = 1; netNumber <= maxNet; netNumber++) {
      int count = countNet(netNumber);
      incompleteByNet[netNumber] = count;
      incompleteTotal += count;
    }
    dirtyNets.clear();
    built = true;
  }

  private void flushDirty() {
    if (dirtyNets.isEmpty()) {
      return;
    }
    for (int netNumber : new ArrayList<>(dirtyNets)) {
      int count = countNet(netNumber);
      incompleteTotal += count - incompleteByNet[netNumber];
      incompleteByNet[netNumber] = count;
    }
    dirtyNets.clear();
    maximumConnections = countMaximumConnections();
  }

  private int countNet(int netNumber) {
    if (netNumber < 1 || netNumber >= itemsByNet.length) {
      return 0;
    }
    return new NetIncompletes(netNumber, new ArrayList<>(itemsByNet[netNumber]), board).count();
  }

  private int countMaximumConnections() {
    int maximum = 0;
    for (int netNumber = 1; netNumber < itemsByNet.length; netNumber++) {
      long endpoints = 0;
      for (Item item : itemsByNet[netNumber]) {
        if (item instanceof Pin || item instanceof ConductionArea) {
          endpoints++;
        }
      }
      maximum += (int) Math.max(0, endpoints - 1);
    }
    return maximum;
  }

  private void addItem(Item item) {
    addItemToLists(item);
    markNets(item);
  }

  private void removeItem(Item item) {
    for (int index = 0; index < item.netCount(); index++) {
      int netNumber = item.getNetNumber(index);
      if (netNumber >= 1 && netNumber < itemsByNet.length) {
        itemsByNet[netNumber].remove(item);
        dirtyNets.add(netNumber);
      }
    }
  }

  private void addItemToLists(Item item) {
    for (int index = 0; index < item.netCount(); index++) {
      int netNumber = item.getNetNumber(index);
      if (netNumber >= 1
          && netNumber < itemsByNet.length
          && !itemsByNet[netNumber].contains(item)) {
        itemsByNet[netNumber].add(item);
      }
    }
  }

  private void markNets(Item item) {
    for (int index = 0; index < item.netCount(); index++) {
      int netNumber = item.getNetNumber(index);
      if (netNumber >= 1 && netNumber < itemsByNet.length) {
        dirtyNets.add(netNumber);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Item>[] emptyLists() {
    return new List[0];
  }
}
