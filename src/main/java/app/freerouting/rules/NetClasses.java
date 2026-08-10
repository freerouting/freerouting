package app.freerouting.rules;

import app.freerouting.board.LayerStructure;
import java.io.Serializable;
import java.util.Vector;

/** Contains the array of net classes for interactive routing. */
public class NetClasses implements Serializable {

  private final Vector<NetClass> classArr = new Vector<>();

  /** Returns the number of classes in this array. */
  public int count() {
    return classArr.size();
  }

  /** Returns the net class with the given index. */
  public NetClass get(int index) {
    assert index >= 0 && index <= classArr.size() - 1;
    return classArr.get(index);
  }

  /** Returns the net class with the given name, or null if no such class exists. */
  public NetClass get(String name) {
    for (NetClass currClass : this.classArr) {
      if (currClass.getName().equals(name)) {
        return currClass;
      }
    }
    return null;
  }

  /** Appends a new empty class with the given name to the class array. */
  public NetClass append(
      String name,
      LayerStructure layerStructure,
      ClearanceMatrix clearanceMatrix,
      boolean ignoredByAutorouter) {
    NetClass newClass =
        new NetClass(name, layerStructure, clearanceMatrix, ignoredByAutorouter);
    classArr.add(newClass);
    return newClass;
  }

  /** Appends a new empty class to the class array with an internally generated name. */
  public NetClass append(LayerStructure layerStructure, ClearanceMatrix clearanceMatrix) {
    String newName;
    int index = 0;
    do {
      ++index;
      newName = "class" + index;
    } while (this.get(newName) != null);
    return append(newName, layerStructure, clearanceMatrix, false);
  }

  /**
   * Looks for a net class with trace half-widths equal to {@code traceHalfWidth}, a trace clearance
   * class equal to {@code traceClearanceClass}, and the given via rule.
   * Returns null if no such net class was found.
   */
  public NetClass find(int traceHalfWidth, int traceClearanceClass, ViaRule viaRule) {
    for (NetClass currClass : this.classArr) {
      if (currClass.getTraceClearanceClass() == traceClearanceClass
          && currClass.getViaRule() == viaRule) {
        boolean traceWidthsEqual = true;
        for (int i = 0; i < currClass.layerCount(); i++) {
          if (currClass.getTraceHalfWidth(i) != traceHalfWidth) {
            traceWidthsEqual = false;
            break;
          }
        }
        if (traceWidthsEqual) {
          return currClass;
        }
      }
    }
    return null;
  }

  /**
   * Looks for a net class whose trace half-width at each layer equals the corresponding value in
   * {@code traceHalfWidthArr}, whose trace clearance class equals {@code traceClearanceClass}, and
   * whose via rule equals {@code viaRule}. Returns null if no such net class was found.
   */
  public NetClass find(int[] traceHalfWidthArr, int traceClearanceClass, ViaRule viaRule) {
    for (NetClass currClass : this.classArr) {
      if (currClass.getTraceClearanceClass() == traceClearanceClass
          && currClass.getViaRule() == viaRule
          && traceHalfWidthArr.length == currClass.layerCount()) {
        boolean traceWidthsEqual = true;
        for (int i = 0; i < currClass.layerCount(); i++) {
          if (currClass.getTraceHalfWidth(i) != traceHalfWidthArr[i]) {
            traceWidthsEqual = false;
            break;
          }
        }
        if (traceWidthsEqual) {
          return currClass;
        }
      }
    }
    return null;
  }

  /**
   * Removes {@code netClass} from this list. Returns false if it was not contained in the list.
   */
  public boolean remove(NetClass netClass) {
    return this.classArr.remove(netClass);
  }
}
