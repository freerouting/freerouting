package app.freerouting.core.library;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.datastructures.UndoableObjects;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/** Describes a board library of packages and padstacks. */
public class BoardLibrary implements Serializable {

  public Padstacks padstacks;
  public Packages packages;

  /** Contains information for gate swap and pin swap in the Specctra-dsn format. */
  public LogicalParts logicalParts = new LogicalParts();

  /**
   * The subset of padstacks in the board library, which can be used in routing for inserting vias.
   */
  private List<Padstack> viaPadstacks;

  /** Creates a new instance of BoardLibrary. */
  public BoardLibrary(Padstacks padstacks, Packages packages) {
    this.padstacks = padstacks;
    this.packages = packages;
    logicalParts = new LogicalParts();
  }

  /** Creates a new instance of BoardLibrary. */
  public BoardLibrary() {}

  /** The count of padstacks from this.padstacks, which can be used in routing. */
  public int viaPadstackCount() {
    if (this.viaPadstacks == null) {
      return 0;
    }
    return this.viaPadstacks.size();
  }

  /** Gets the via padstack for routing with the specified index. */
  public Padstack getViaPadstack(int no) {
    if (this.viaPadstacks == null || no < 0 || no >= this.viaPadstacks.size()) {
      return null;
    }
    return this.viaPadstacks.get(no);
  }

  /** Gets the via padstack with name name, or null, if no such padstack exists. */
  public Padstack getViaPadstack(String name) {
    if (this.viaPadstacks == null) {
      return null;
    }
    for (Padstack currentPadstack : this.viaPadstacks) {
      if (currentPadstack.name.equals(name)) {
        return currentPadstack;
      }
    }
    return null;
  }

  /** Returns the via padstacks, which can be used for routing. */
  public Padstack[] getViaPadstacks() {
    if (this.viaPadstacks == null) {
      return new Padstack[0];
    }
    Padstack[] result = new Padstack[viaPadstacks.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = viaPadstacks.get(i);
    }
    return result;
  }

  /**
   * Sets the subset of padstacks from this.padstacks, which can be used in routing for inserting
   * vias.
   */
  public void setViaPadstacks(Padstack[] padstacks) {

    this.viaPadstacks = new Vector<>(Arrays.asList(padstacks));
  }

  /**
   * Appends a padstack to the list of via padstacks. Returns false if the list already contains a
   * padstack with the same name.
   */
  public boolean addViaPadstack(Padstack padstack) {
    if (getViaPadstack(padstack.name) != null) {
      return false;
    }

    if (this.viaPadstacks == null) {
      this.viaPadstacks = new Vector<>();
    }

    this.viaPadstacks.add(padstack);
    return true;
  }

  /** Removes a padstack from the via padstack list. Returns false if it was not found. */
  public boolean removeViaPadstack(Padstack padstack, BasicBoard board) {
    return viaPadstacks.remove(padstack);
  }

  /**
   * Gets the via padstack mirrored to the back side of the board. Returns null, if no such via
   * padstack exists.
   */
  public Padstack getMirroredViaPadstack(Padstack viaPadstack) {
    int layerCount = this.padstacks.boardLayerStructure.layers.length;
    if (viaPadstack.fromLayer() == 0 && viaPadstack.toLayer() == layerCount - 1) {
      return viaPadstack;
    }
    int newFromLayer = layerCount - viaPadstack.toLayer() - 1;
    int newToLayer = layerCount - viaPadstack.fromLayer() - 1;
    for (Padstack currentViaPadstack : viaPadstacks) {
      if (currentViaPadstack.fromLayer() == newFromLayer
          && currentViaPadstack.toLayer() == newToLayer) {
        return currentViaPadstack;
      }
    }
    return null;
  }

  /** Returns whether the input padstack is used on the board in a package or drill. */
  public boolean isUsed(Padstack padstack, BasicBoard board) {
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentItem = board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof DrillItem item) {
        if (item.getPadstack() == padstack) {
          return true;
        }
      }
    }
    for (int i = 1; i <= this.packages.count(); i++) {
      Package currentPackage = this.packages.get(i);
      for (int j = 0; j < currentPackage.pinCount(); j++) {
        if (currentPackage.getPin(j).padstackId == padstack.id) {
          return true;
        }
      }
    }
    return false;
  }
}
