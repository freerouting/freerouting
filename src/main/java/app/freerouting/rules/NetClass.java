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
      String p_name,
      LayerStructure p_layer_structure,
      ClearanceMatrix p_clearance_matrix,
      boolean p_is_ignored_by_autorouter) {
    this.name = p_name;
    this.boardLayerStructure = p_layer_structure;
    this.clearanceMatrix = p_clearance_matrix;
    this.traceHalfWidthArr = new int[p_layer_structure.arr.length];
    this.activeRoutingLayerArr = new boolean[p_layer_structure.arr.length];
    for (int i = 0; i < p_layer_structure.arr.length; i++) {
      this.activeRoutingLayerArr[i] = p_layer_structure.arr[i].isSignal;
    }
    this.isIgnoredByAutorouter = p_is_ignored_by_autorouter;
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
  public void setName(String p_name) {
    this.name = p_name;
  }

  /** Sets the trace half width used for routing to p_value on all layers. */
  public void setTraceHalfWidth(int p_value) {
    Arrays.fill(traceHalfWidthArr, p_value);
  }

  /** Sets the trace half width used for routing to p_value on all inner layers. */
  public void setTraceHalfWidthOnInner(int p_value) {
    for (int i = 1; i < traceHalfWidthArr.length - 1; i++) {
      traceHalfWidthArr[i] = p_value;
    }
  }

  /** Sets the trace half width used for routing to p_value on the input layer. */
  public void setTraceHalfWidth(int p_layer, int p_value) {
    traceHalfWidthArr[p_layer] = p_value;
  }

  public int layerCount() {
    return traceHalfWidthArr.length;
  }

  /** Gets the trace half width used for routing on the input layer. */
  public int getTraceHalfWidth(int p_layer) {
    if (p_layer < 0 || p_layer >= traceHalfWidthArr.length) {
      FRLogger.warn(" NetClass.get_trace_half_width: p_layer out of range");
      return 0;
    }
    return traceHalfWidthArr[p_layer];
  }

  /** Gets the clearance class used for routing traces with this net class. */
  public int getTraceClearanceClass() {
    return this.traceClearanceClass;
  }

  /** Sets the clearance class used for routing traces with this net rclass. */
  public void setTraceClearanceClass(int p_clearance_class_no) {
    this.traceClearanceClass = p_clearance_class_no;
  }

  /** Gets the via rule of this net rule. */
  public ViaRule getViaRule() {
    return this.viaRule;
  }

  /** Sets the via rule of this net class. */
  public void setViaRule(ViaRule p_via_rule) {
    this.viaRule = p_via_rule;
  }

  /** Returns, if traces and vias of this net class can be pushed. */
  public boolean isShoveFixed() {
    return this.shoveFixed;
  }

  /** Sets, if traces and vias of this net class can be pushed. */
  public void setShoveFixed(boolean p_value) {
    this.shoveFixed = p_value;
  }

  /** Returns, if traces of this nets class are pulled tight. */
  public boolean getPullTight() {
    return this.pullTight;
  }

  /** Sets, if traces of this nets class are pulled tight. */
  public void setPullTight(boolean p_value) {
    this.pullTight = p_value;
  }

  /** Returns, if the cycle remove algorithm ignores cycles, where conduction areas are involved */
  public boolean getIgnoreCyclesWithAreas() {
    return this.ignoreCyclesWithAreas;
  }

  /** Sets, if the cycle remove algorithm ignores cycles, where conduction areas are involved */
  public void setIgnoreCyclesWithAreas(boolean p_value) {
    this.ignoreCyclesWithAreas = p_value;
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
  public void setMinimumTraceLength(double p_value) {
    minimumTraceLength = p_value;
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
  public void setMaximumTraceLength(double p_value) {
    maximumTraceLength = p_value;
  }

  /** Returns if the layer with index p_layer_no is active for routing */
  public boolean isActiveRoutingLayer(int p_layer_no) {
    if (p_layer_no < 0 || p_layer_no >= this.activeRoutingLayerArr.length) {
      return false;
    }
    return this.activeRoutingLayerArr[p_layer_no];
  }

  /** Sets the layer with index p_layer_no to p_active. */
  public void setActiveRoutingLayer(int p_layer_no, boolean p_active) {
    if (p_layer_no < 0 || p_layer_no >= this.activeRoutingLayerArr.length) {
      return;
    }
    this.activeRoutingLayerArr[p_layer_no] = p_active;
  }

  /** Activates or deactivates all layers for routing */
  public void setAllLayersActive(boolean p_value) {
    Arrays.fill(this.activeRoutingLayerArr, p_value);
  }

  /** Activates or deactivates all inner layers for routing */
  public void setAllInnerLayersActive(boolean p_value) {
    for (int i = 1; i < traceHalfWidthArr.length - 1; i++) {
      activeRoutingLayerArr[i] = p_value;
    }
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("net_class_2") + " ");
    p_window.appendBold(this.name);
    p_window.appendBold(":");
    p_window.append(" " + tm.getText("traceClearanceClass") + " ");
    String clName = clearanceMatrix.getName(this.traceClearanceClass);
    p_window.append(
        clName,
        tm.getText("trace_clearance_class_2"),
        clearanceMatrix.getRow(this.traceClearanceClass));
    if (this.shoveFixed) {
      p_window.append(", " + tm.getText("shoveFixed"));
    }
    p_window.append(", " + tm.getText("viaRule") + " ");
    p_window.append(viaRule.name, tm.getText("via_rule_2"), viaRule);
    if (traceWidthIsLayerDependent()) {
      for (int i = 0; i < traceHalfWidthArr.length; i++) {
        p_window.newline();
        p_window.indent();
        p_window.append(tm.getText("traceWidth") + " ");
        p_window.append(2 * traceHalfWidthArr[i]);
        p_window.append(" " + tm.getText("on_layer") + " ");
        p_window.append(this.boardLayerStructure.arr[i].name);
      }
    } else {
      p_window.append(", " + tm.getText("traceWidth") + " ");
      p_window.append(2 * traceHalfWidthArr[0]);
    }
    p_window.newline();
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
