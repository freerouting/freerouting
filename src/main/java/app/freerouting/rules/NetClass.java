package app.freerouting.rules;

import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.board.LayerStructure;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

/** Describes routing rules for individual nets. */
public class NetClass implements Serializable, ItemInfoPrinter.Printable {

  private final ClearanceMatrix clearanceMatrix;
  private final LayerStructure boardLayerStructure;
  private final int[] traceHalfWidthArr;
  private final boolean[] activeRoutingLayerArr;

  /**
   * The clearance classes of the item types if this net class comes from a class in a Specctra DSN
   * file. Should eventually be moved to {@code NetClass} and used only when reading a DSN file.
   */
  public DefaultItemClearanceClasses defaultItemClearanceClasses =
      new DefaultItemClearanceClasses();

  public boolean isIgnoredByAutorouter;
  private String name;
  private ViaRule viaRule;
  private int traceClearanceClass;

  /** If null, all signal layers may be used for routing. */
  private boolean shoveFixed;

  private boolean pullTight = true;
  private boolean ignoreCyclesWithAreas;
  private double minimumTraceLength = 0;
  private double maximumTraceLength = 0;

  /** Creates a new instance of {@code NetClass}. */
  public NetClass(
      String name,
      LayerStructure layerStructure,
      ClearanceMatrix clearanceMatrix,
      boolean ignoredByAutorouter) {
    this.name = name;
    this.boardLayerStructure = layerStructure;
    this.clearanceMatrix = clearanceMatrix;
    this.traceHalfWidthArr = new int[layerStructure.layers.length];
    this.activeRoutingLayerArr = new boolean[layerStructure.layers.length];
    for (int i = 0; i < layerStructure.layers.length; i++) {
      this.activeRoutingLayerArr[i] = layerStructure.layers[i].isSignal;
    }
    this.isIgnoredByAutorouter = ignoredByAutorouter;
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
  public void setName(String name) {
    this.name = name;
  }

  /** Sets the trace half-width used for routing to {@code value} on all layers. */
  public void setTraceHalfWidth(int value) {
    Arrays.fill(traceHalfWidthArr, value);
  }

  /** Sets the trace half-width used for routing to {@code value} on the input layer. */
  public void setTraceHalfWidth(int layer, int value) {
    traceHalfWidthArr[layer] = value;
  }

  /** Sets the trace half-width used for routing to {@code value} on all inner layers. */
  public void setTraceHalfWidthOnInner(int value) {
    for (int i = 1; i < traceHalfWidthArr.length - 1; i++) {
      traceHalfWidthArr[i] = value;
    }
  }

  /** Returns the number of layers in this net class. */
  public int layerCount() {
    return traceHalfWidthArr.length;
  }

  /** Gets the trace half-width used for routing on the input layer. */
  public int getTraceHalfWidth(int layer) {
    if (layer < 0 || layer >= traceHalfWidthArr.length) {
      FRLogger.warn(" NetClass.get_trace_half_width: layer out of range");
      return 0;
    }
    return traceHalfWidthArr[layer];
  }

  /** Gets the clearance class used for routing traces with this net class. */
  public int getTraceClearanceClass() {
    return this.traceClearanceClass;
  }

  /** Sets the clearance class used for routing traces with this net class. */
  public void setTraceClearanceClass(int clearanceClass) {
    this.traceClearanceClass = clearanceClass;
  }

  /** Gets the via rule of this net class. */
  public ViaRule getViaRule() {
    return this.viaRule;
  }

  /** Sets the via rule of this net class. */
  public void setViaRule(ViaRule viaRule) {
    this.viaRule = viaRule;
  }

  /** Returns whether traces and vias of this net class can be pushed. */
  public boolean isShoveFixed() {
    return this.shoveFixed;
  }

  /** Sets whether traces and vias of this net class can be pushed. */
  public void setShoveFixed(boolean value) {
    this.shoveFixed = value;
  }

  /** Returns whether traces of this net class are pulled tight. */
  public boolean getPullTight() {
    return this.pullTight;
  }

  /** Sets whether traces of this net class are pulled tight. */
  public void setPullTight(boolean value) {
    this.pullTight = value;
  }

  /** Returns whether the cycle removal algorithm ignores cycles involving conduction areas. */
  public boolean getIgnoreCyclesWithAreas() {
    return this.ignoreCyclesWithAreas;
  }

  /** Sets whether the cycle removal algorithm ignores cycles involving conduction areas. */
  public void setIgnoreCyclesWithAreas(boolean value) {
    this.ignoreCyclesWithAreas = value;
  }

  /**
   * Returns the minimum trace length of this net class. If the result is {@literal <}= 0, there is
   * no minimal trace length restriction.
   */
  public double getMinimumTraceLength() {
    return minimumTraceLength;
  }

  /**
   * Sets the minimum trace length of this net class to {@code value}. If {@code value} is {@literal
   * <}= 0, there is no minimal trace length restriction.
   */
  public void setMinimumTraceLength(double value) {
    minimumTraceLength = value;
  }

  /**
   * Returns the maximum trace length of this net class. If the result is {@literal <}= 0, there is
   * no maximal trace length restriction.
   */
  public double getMaximumTraceLength() {
    return maximumTraceLength;
  }

  /**
   * Sets the maximum trace length of this net class to {@code value}. If {@code value} is {@literal
   * <}= 0, there is no maximal trace length restriction.
   */
  public void setMaximumTraceLength(double value) {
    maximumTraceLength = value;
  }

  /** Returns whether the layer with the given index is active for routing. */
  public boolean isActiveRoutingLayer(int layerNumber) {
    if (layerNumber < 0 || layerNumber >= this.activeRoutingLayerArr.length) {
      return false;
    }
    return this.activeRoutingLayerArr[layerNumber];
  }

  /** Sets whether the layer with the given index is active for routing. */
  public void setActiveRoutingLayer(int layerNumber, boolean active) {
    if (layerNumber < 0 || layerNumber >= this.activeRoutingLayerArr.length) {
      return;
    }
    this.activeRoutingLayerArr[layerNumber] = active;
  }

  /** Activates or deactivates all layers for routing. */
  public void setAllLayersActive(boolean value) {
    Arrays.fill(this.activeRoutingLayerArr, value);
  }

  /** Activates or deactivates all inner layers for routing. */
  public void setAllInnerLayersActive(boolean value) {
    for (int i = 1; i < traceHalfWidthArr.length - 1; i++) {
      activeRoutingLayerArr[i] = value;
    }
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("net_class_2") + " ");
    printer.appendBold(this.name);
    printer.appendBold(":");
    printer.append(" " + tm.getText("traceClearanceClass") + " ");
    String clearanceName = clearanceMatrix.getName(this.traceClearanceClass);
    printer.append(
        clearanceName,
        tm.getText("trace_clearance_class_2"),
        clearanceMatrix.getRow(this.traceClearanceClass));
    if (this.shoveFixed) {
      printer.append(", " + tm.getText("shoveFixed"));
    }
    printer.append(", " + tm.getText("viaRule") + " ");
    printer.append(viaRule.name, tm.getText("via_rule_2"), viaRule);
    if (traceWidthIsLayerDependent()) {
      for (int i = 0; i < traceHalfWidthArr.length; i++) {
        printer.newline();
        printer.indent();
        printer.append(tm.getText("traceWidth") + " ");
        printer.append(2 * traceHalfWidthArr[i]);
        printer.append(" " + tm.getText("on_layer") + " ");
        printer.append(this.boardLayerStructure.layers[i].name);
      }
    } else {
      printer.append(", " + tm.getText("traceWidth") + " ");
      printer.append(2 * traceHalfWidthArr[0]);
    }
    printer.newline();
  }

  /** Returns true if the trace width of this class is not equal on all layers. */
  public boolean traceWidthIsLayerDependent() {
    int compareValue = traceHalfWidthArr[0];
    for (int i = 1; i < traceHalfWidthArr.length; i++) {
      if (this.boardLayerStructure.layers[i].isSignal) {
        if (traceHalfWidthArr[i] != compareValue) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns true if the trace width of this class is not equal on all inner layers. */
  public boolean traceWidthIsInnerLayerDependent() {

    if (traceHalfWidthArr.length <= 3) {
      return false;
    }
    int firstInnerLayerNo = 1;
    while (!this.boardLayerStructure.layers[firstInnerLayerNo].isSignal) {
      ++firstInnerLayerNo;
    }
    if (firstInnerLayerNo >= traceHalfWidthArr.length - 1) {
      return false;
    }
    int compareWidth = traceHalfWidthArr[firstInnerLayerNo];
    for (int i = firstInnerLayerNo + 1; i < traceHalfWidthArr.length - 1; i++) {
      if (this.boardLayerStructure.layers[i].isSignal) {
        if (traceHalfWidthArr[i] != compareWidth) {
          return true;
        }
      }
    }
    return false;
  }
}
