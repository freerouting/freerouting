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
  public BoardLibrary(Padstacks p_padstacks, Packages p_packages) {
    padstacks = p_padstacks;
    packages = p_packages;
    logicalParts = new LogicalParts();
  }

  /** Creates a new instance of BoardLibrary */
  public BoardLibrary() {}

  /** The count of padstacks from this.padstacks, which can be used in routing */
  public int via_padstack_count() {
    if (this.viaPadstacks == null) {
      return 0;
    }
    return this.viaPadstacks.size();
  }

  /** Gets the via padstack for routing with index p_no */
  public Padstack get_via_padstack(int p_no) {
    if (this.viaPadstacks == null || p_no < 0 || p_no >= this.viaPadstacks.size()) {
      return null;
    }
    return this.viaPadstacks.get(p_no);
  }

  /** Gets the via padstack with name p_name, or null, if no such padstack exists. */
  public Padstack get_via_padstack(String p_name) {
    if (this.viaPadstacks == null) {
      return null;
    }
    for (Padstack currPadstack : this.viaPadstacks) {
      if (currPadstack.name.equals(p_name)) {
        return currPadstack;
      }
    }
    return null;
  }

  /** Returns the via padstacks, which can be used for routing. */
  public Padstack[] get_via_padstacks() {
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
  public void set_via_padstacks(Padstack[] p_padstacks) {

    this.viaPadstacks = new Vector<>(Arrays.asList(p_padstacks));
  }

  /**
   * Appends p_padstack to the list of via padstacks. Returns false, if the list contains already a
   * padstack with p_padstack.name.
   */
  public boolean add_via_padstack(Padstack p_padstack) {
    if (get_via_padstack(p_padstack.name) != null) {
      return false;
    }

    if (this.viaPadstacks == null) {
      this.viaPadstacks = new Vector<>();
    }

    this.viaPadstacks.add(p_padstack);
    return true;
  }

  /**
   * Removes p_padstack from the via padstack list. Returns false, if p_padstack was not found in
   * the list. If the padstack is no more used on the board, it will also be removed from the board
   * padstacks.
   */
  public boolean remove_via_padstack(Padstack p_padstack, BasicBoard p_board) {
    return viaPadstacks.remove(p_padstack);
  }

  /**
   * Gets the via padstack mirrored to the back side of the board. Returns null, if no such via
   * padstack exists.
   */
  public Padstack get_mirrored_via_padstack(Padstack p_via_padstack) {
    int layerCount = this.padstacks.boardLayerStructure.arr.length;
    if (p_via_padstack.from_layer() == 0 && p_via_padstack.to_layer() == layerCount - 1) {
      return p_via_padstack;
    }
    int newFromLayer = layerCount - p_via_padstack.to_layer() - 1;
    int newToLayer = layerCount - p_via_padstack.from_layer() - 1;
    for (Padstack currViaPadstack : viaPadstacks) {
      if (currViaPadstack.from_layer() == newFromLayer
          && currViaPadstack.to_layer() == newToLayer) {
        return currViaPadstack;
      }
    }
    return null;
  }

  /** Looks, if the input padstack is used on p_board in a Package or in drill. */
  public boolean is_used(Padstack p_padstack, BasicBoard p_board) {
    Iterator<UndoableObjects.UndoableObjectNode> it = p_board.itemList.start_read_object();
    for (; ; ) {
      UndoableObjects.Storable currItem = p_board.itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof DrillItem item) {
        if (item.get_padstack() == p_padstack) {
          return true;
        }
      }
    }
    for (int i = 1; i <= this.packages.count(); i++) {
      Package currPackage = this.packages.get(i);
      for (int j = 0; j < currPackage.pin_count(); j++) {
        if (currPackage.get_pin(j).padstackNo == p_padstack.no) {
          return true;
        }
      }
    }
    return false;
  }
}
