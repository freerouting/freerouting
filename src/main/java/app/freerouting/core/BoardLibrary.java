package app.freerouting.core;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.DrillItem;
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

  /** Creates a new instance of BoardLibrary */
  public BoardLibrary(Padstacks pPadstacks, Packages pPackages) {
    padstacks = pPadstacks;
    packages = pPackages;
    logicalParts = new LogicalParts();
  }

  /** Creates a new instance of BoardLibrary */
  public BoardLibrary() {}

  /** The count of padstacks from this.padstacks, which can be used in routing */
  public int viaPadstackCount() {
    if (this.viaPadstacks == null) {
      return 0;
    }
    return this.viaPadstacks.size();
  }

  /** Gets the via padstack for routing with index p_no */
  public Padstack getViaPadstack(int pNo) {
    if (this.viaPadstacks == null || pNo < 0 || pNo >= this.viaPadstacks.size()) {
      return null;
    }
    return this.viaPadstacks.get(pNo);
  }

  /** Gets the via padstack with name p_name, or null, if no such padstack exists. */
  public Padstack getViaPadstack(String pName) {
    if (this.viaPadstacks == null) {
      return null;
    }
    for (Padstack currPadstack : this.viaPadstacks) {
      if (currPadstack.name.equals(pName)) {
        return currPadstack;
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
  public void setViaPadstacks(Padstack[] pPadstacks) {

    this.viaPadstacks = new Vector<>(Arrays.asList(pPadstacks));
  }

  /**
   * Appends p_padstack to the list of via padstacks. Returns false, if the list contains already a
   * padstack with p_padstack.name.
   */
  public boolean addViaPadstack(Padstack pPadstack) {
    if (getViaPadstack(pPadstack.name) != null) {
      return false;
    }

    if (this.viaPadstacks == null) {
      this.viaPadstacks = new Vector<>();
    }

    this.viaPadstacks.add(pPadstack);
    return true;
  }

  /**
   * Removes p_padstack from the via padstack list. Returns false, if p_padstack was not found in
   * the list. If the padstack is no more used on the board, it will also be removed from the board
   * padstacks.
   */
  public boolean removeViaPadstack(Padstack pPadstack, BasicBoard pBoard) {
    return viaPadstacks.remove(pPadstack);
  }

  /**
   * Gets the via padstack mirrored to the back side of the board. Returns null, if no such via
   * padstack exists.
   */
  public Padstack getMirroredViaPadstack(Padstack pViaPadstack) {
    int layerCount = this.padstacks.boardLayerStructure.arr.length;
    if (pViaPadstack.fromLayer() == 0 && pViaPadstack.toLayer() == layerCount - 1) {
      return pViaPadstack;
    }
    int newFromLayer = layerCount - pViaPadstack.toLayer() - 1;
    int newToLayer = layerCount - pViaPadstack.fromLayer() - 1;
    for (Padstack currViaPadstack : viaPadstacks) {
      if (currViaPadstack.fromLayer() == newFromLayer && currViaPadstack.toLayer() == newToLayer) {
        return currViaPadstack;
      }
    }
    return null;
  }

  /** Looks, if the input padstack is used on p_board in a Package or in drill. */
  public boolean isUsed(Padstack pPadstack, BasicBoard pBoard) {
    Iterator<UndoableObjects.UndoableObjectNode> it = pBoard.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currItem = pBoard.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof DrillItem item) {
        if (item.getPadstack() == pPadstack) {
          return true;
        }
      }
    }
    for (int i = 1; i <= this.packages.count(); i++) {
      Package currPackage = this.packages.get(i);
      for (int j = 0; j < currPackage.pinCount(); j++) {
        if (currPackage.getPin(j).padstackNo == pPadstack.no) {
          return true;
        }
      }
    }
    return false;
  }
}
