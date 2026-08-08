package app.freerouting.rules;

import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

/** Describes routing rules for individual nets. */
public class NetClass implements Serializable, ObjectInfoPanel.Printable {

  private final ClearanceMatrix clearanceMatrix;
  private final LayerStructure boardLayerStructure;
  private final int[] traceHalfWidthArr;
  private final boolean[] activeRoutingLayerArr;

  /**
   * The clearance classes of the item types, if this net class comes from a class in a Specctra
   * dsn-file Should eventually be moved to NetClass and used only when reading a dsn-file.
   */
  public DefaultItemClearanceClasses defaultItemClearanceClasses =
      new DefaultItemClearanceClasses();

  public boolean isIgnoredByAutorouter;
  private String name;
  private ViaRule viaRule;
  private int traceClearanceClass;

  /** if null, all signal layers may be used for routing */
  private boolean shoveFixed;

  private boolean pullTight = true;
  private boolean ignoreCyclesWithAreas;
  private double minimumTraceLength = 0;
  private double maximumTraceLength = 0;

  /** Creates a new instance of NetClass */
  public NetClass(
      String pName,
      LayerStructure pLayerStructure,
      ClearanceMatrix pClearanceMatrix,
      boolean pIsIgnoredByAutorouter) {
    this.name = pName;
    this.boardLayerStructure = pLayerStructure;
    this.clearanceMatrix = pClearanceMatrix;
    this.traceHalfWidthArr = new int[pLayerStructure.arr.length];
    this.activeRoutingLayerArr = new boolean[pLayerStructure.arr.length];
    for (int i = 0; i < pLayerStructure.arr.length; i++) {
      this.activeRoutingLayerArr[i] = pLayerStructure.arr[i].isSignal;
    }
    this.isIgnoredByAutorouter = pIsIgnoredByAutorouter;
  }

  @Override
  public String toString() {
    return this.name;
  }

  /** Gets the name of this net class. */
  public String getName() {
    return this.name;
  }

  /** Changes the name of this net class. */
  public void setName(String pName) {
    this.name = pName;
  }

  /** Sets the trace half width used for routing to p_value on all layers. */
  public void setTraceHalfWidth(int pValue) {
    Arrays.fill(traceHalfWidthArr, pValue);
  }

  /** Sets the trace half width used for routing to p_value on all inner layers. */
  public void setTraceHalfWidthOnInner(int pValue) {
    for (int i = 1; i < traceHalfWidthArr.length - 1; i++) {
      traceHalfWidthArr[i] = pValue;
    }
  }

  /** Sets the trace half width used for routing to p_value on the input layer. */
  public void setTraceHalfWidth(int pLayer, int pValue) {
    traceHalfWidthArr[pLayer] = pValue;
  }

  public int layerCount() {
    return traceHalfWidthArr.length;
  }

  /** Gets the trace half width used for routing on the input layer. */
  public int getTraceHalfWidth(int pLayer) {
    if (pLayer < 0 || pLayer >= traceHalfWidthArr.length) {
      FRLogger.warn(" NetClass.get_trace_half_width: p_layer out of range");
      return 0;
    }
    return traceHalfWidthArr[pLayer];
  }

  /** Gets the clearance class used for routing traces with this net class. */
  public int getTraceClearanceClass() {
    return this.traceClearanceClass;
  }

  /** Sets the clearance class used for routing traces with this net rclass. */
  public void setTraceClearanceClass(int pClearanceClassNo) {
    this.traceClearanceClass = pClearanceClassNo;
  }

  /** Gets the via rule of this net rule. */
  public ViaRule getViaRule() {
    return this.viaRule;
  }

  /** Sets the via rule of this net class. */
  public void setViaRule(ViaRule pViaRule) {
    this.viaRule = pViaRule;
  }

  /** Returns, if traces and vias of this net class can be pushed. */
  public boolean isShoveFixed() {
    return this.shoveFixed;
  }

  /** Sets, if traces and vias of this net class can be pushed. */
  public void setShoveFixed(boolean pValue) {
    this.shoveFixed = pValue;
  }

  /** Returns, if traces of this nets class are pulled tight. */
  public boolean getPullTight() {
    return this.pullTight;
  }

  /** Sets, if traces of this nets class are pulled tight. */
  public void setPullTight(boolean pValue) {
    this.pullTight = pValue;
  }

  /** Returns, if the cycle remove algorithm ignores cycles, where conduction areas are involved */
  public boolean getIgnoreCyclesWithAreas() {
    return this.ignoreCyclesWithAreas;
  }

  /** Sets, if the cycle remove algorithm ignores cycles, where conduction areas are involved */
  public void setIgnoreCyclesWithAreas(boolean pValue) {
    this.ignoreCyclesWithAreas = pValue;
  }

  /**
   * Returns the minimum trace length of this net class. If the result is {@literal <}= 0, there is
   * no minimal trace length restriction.
   */
  public double getMinimumTraceLength() {
    return minimumTraceLength;
  }

  /**
   * Sets the minimum trace length of this net class to p_value. If p_value is {@literal <}= 0,
   * there is no minimal trace length restriction.
   */
  public void setMinimumTraceLength(double pValue) {
    minimumTraceLength = pValue;
  }

  /**
   * Returns the maximum trace length of this net class. If the result is {@literal <}= 0, there is
   * no maximal trace length restriction.
   */
  public double getMaximumTraceLength() {
    return maximumTraceLength;
  }

  /**
   * Sets the maximum trace length of this net class to p_value. If p_value is {@literal <}= 0,
   * there is no maximal trace length restriction.
   */
  public void setMaximumTraceLength(double pValue) {
    maximumTraceLength = pValue;
  }

  /** Returns if the layer with index p_layer_no is active for routing */
  public boolean isActiveRoutingLayer(int pLayerNo) {
    if (pLayerNo < 0 || pLayerNo >= this.activeRoutingLayerArr.length) {
      return false;
    }
    return this.activeRoutingLayerArr[pLayerNo];
  }

  /** Sets the layer with index p_layer_no to p_active. */
  public void setActiveRoutingLayer(int pLayerNo, boolean pActive) {
    if (pLayerNo < 0 || pLayerNo >= this.activeRoutingLayerArr.length) {
      return;
    }
    this.activeRoutingLayerArr[pLayerNo] = pActive;
  }

  /** Activates or deactivates all layers for routing */
  public void setAllLayersActive(boolean pValue) {
    Arrays.fill(this.activeRoutingLayerArr, pValue);
  }

  /** Activates or deactivates all inner layers for routing */
  public void setAllInnerLayersActive(boolean pValue) {
    for (int i = 1; i < traceHalfWidthArr.length - 1; i++) {
      activeRoutingLayerArr[i] = pValue;
    }
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("net_class_2") + " ");
    pWindow.appendBold(this.name);
    pWindow.appendBold(":");
    pWindow.append(" " + tm.getText("traceClearanceClass") + " ");
    String clName = clearanceMatrix.getName(this.traceClearanceClass);
    pWindow.append(
        clName,
        tm.getText("trace_clearance_class_2"),
        clearanceMatrix.getRow(this.traceClearanceClass));
    if (this.shoveFixed) {
      pWindow.append(", " + tm.getText("shoveFixed"));
    }
    pWindow.append(", " + tm.getText("viaRule") + " ");
    pWindow.append(viaRule.name, tm.getText("via_rule_2"), viaRule);
    if (traceWidthIsLayerDependent()) {
      for (int i = 0; i < traceHalfWidthArr.length; i++) {
        pWindow.newline();
        pWindow.indent();
        pWindow.append(tm.getText("traceWidth") + " ");
        pWindow.append(2 * traceHalfWidthArr[i]);
        pWindow.append(" " + tm.getText("on_layer") + " ");
        pWindow.append(this.boardLayerStructure.arr[i].name);
      }
    } else {
      pWindow.append(", " + tm.getText("traceWidth") + " ");
      pWindow.append(2 * traceHalfWidthArr[0]);
    }
    pWindow.newline();
  }

  /** Returns true, if the trace width of this class is not equal on all layers. */
  public boolean traceWidthIsLayerDependent() {
    int compareValue = traceHalfWidthArr[0];
    for (int i = 1; i < traceHalfWidthArr.length; i++) {
      if (this.boardLayerStructure.arr[i].isSignal) {
        if (traceHalfWidthArr[i] != compareValue) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns true, if the trace width of this class is not equal on all inner layers. */
  public boolean traceWidthIsInnerLayerDependent() {

    if (traceHalfWidthArr.length <= 3) {
      return false;
    }
    int firstInnerLayerNo = 1;
    while (!this.boardLayerStructure.arr[firstInnerLayerNo].isSignal) {
      ++firstInnerLayerNo;
    }
    if (firstInnerLayerNo >= traceHalfWidthArr.length - 1) {
      return false;
    }
    int compareWidth = traceHalfWidthArr[firstInnerLayerNo];
    for (int i = firstInnerLayerNo + 1; i < traceHalfWidthArr.length - 1; i++) {
      if (this.boardLayerStructure.arr[i].isSignal) {
        if (traceHalfWidthArr[i] != compareWidth) {
          return true;
        }
      }
    }
    return false;
  }
}
