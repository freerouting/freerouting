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

  /** Returns the net class with index p_index. */
  public NetClass get(int pIndex) {
    assert pIndex >= 0 && pIndex <= classArr.size() - 1;
    return classArr.get(pIndex);
  }

  /** Returns the net class with name p_name, or null, if no such class exists. */
  public NetClass get(String pName) {
    for (NetClass currClass : this.classArr) {
      if (currClass.getName().equals(pName)) {
        return currClass;
      }
    }
    return null;
  }

  /** Appends a new empty class with name p_name to the class array */
  public NetClass append(
      String pName,
      LayerStructure pLayerStructure,
      ClearanceMatrix pClearanceMatrix,
      boolean pIsIgnoredByAutorouter) {
    NetClass newClass =
        new NetClass(pName, pLayerStructure, pClearanceMatrix, pIsIgnoredByAutorouter);
    classArr.add(newClass);
    return newClass;
  }

  /** Appends a new empty class to the class array. A name for the class is created internally */
  public NetClass append(LayerStructure pLayerStructure, ClearanceMatrix pClearanceMatrix) {
    String newName;
    int index = 0;
    do {
      ++index;
      newName = "class" + index;
    } while (this.get(newName) != null);
    return append(newName, pLayerStructure, pClearanceMatrix, false);
  }

  /**
   * Looks, if the list contains a net class with trace half widths all equal to p_trace_half_width,
   * trace clearance class equal to p_trace_clearance_class and via rule equal to p_cia_rule.
   * Returns null, if no such net class was found.
   */
  public NetClass find(int pTraceHalfWidth, int pTraceClearanceClass, ViaRule pViaRule) {
    for (NetClass currClass : this.classArr) {
      if (currClass.getTraceClearanceClass() == pTraceClearanceClass
          && currClass.getViaRule() == pViaRule) {
        boolean traceWidthsEqual = true;
        for (int i = 0; i < currClass.layerCount(); i++) {
          if (currClass.getTraceHalfWidth(i) != pTraceHalfWidth) {
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
   * Looks, if the list contains a net class with trace half width[i] all equal to
   * p_trace_half_width_arr[i] for 0 {@literal <}= i {@literal <} layerCount, trace clearance class
   * equal to p_trace_clearance_class and via rule equal to p_via_rule. Returns null, if no such net
   * class was found.
   */
  public NetClass find(int[] pTraceHalfWidthArr, int pTraceClearanceClass, ViaRule pViaRule) {
    for (NetClass currClass : this.classArr) {
      if (currClass.getTraceClearanceClass() == pTraceClearanceClass
          && currClass.getViaRule() == pViaRule
          && pTraceHalfWidthArr.length == currClass.layerCount()) {
        boolean traceWidthsEqual = true;
        for (int i = 0; i < currClass.layerCount(); i++) {
          if (currClass.getTraceHalfWidth(i) != pTraceHalfWidthArr[i]) {
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
   * Removes p_net_class from this list. Returns false, if p_net_class was not contained in the
   * list.
   */
  public boolean remove(NetClass pNetClass) {
    return this.classArr.remove(pNetClass);
  }
}
