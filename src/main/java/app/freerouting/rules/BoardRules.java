package app.freerouting.rules;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Vector;

/** Contains the rules and constraints required for items to be inserted into a routing board. */
public class BoardRules implements Serializable {

  /** The matrix describing the spacing restrictions between item clearance classes. */
  public final ClearanceMatrix clearanceMatrix;

  /** Describes the electrical nets on the board. */
  public final Nets nets;

  public final ViaInfos viaInfos = new ViaInfos();
  public final Vector<ViaRule> viaRules = new Vector<>();
  public final NetClasses netClasses = new NetClasses();
  private final LayerStructure layerStructure;

  /** The angle restriction for traces: 90 degree, 45 degree or none. */
  private transient AngleRestriction traceAngleRestriction;

  /** If true, the router ignores conduction areas. */
  private boolean ignoreConduction = true;

  /** The smallest of all default trace half-widths. */
  private int minTraceHalfWidth;

  /** The biggest of all default trace half-widths. */
  private int maxTraceHalfWidth;

  /**
   * The minimum distance of the pad border to the first turn of a connected trace to a pin with
   * restricted exit directions. If the value is {@literal <}= 0, there are no exit restrictions.
   */
  private double pinEdgeToTurnDist;

  private boolean useSlowAutorouteAlgorithm;
  private int holeClearance;

  /** Creates a new instance of this class. */
  public BoardRules(LayerStructure layerStructure, ClearanceMatrix clearanceMatrix) {
    this.layerStructure = layerStructure;
    this.clearanceMatrix = clearanceMatrix;
    nets = new Nets();
    this.traceAngleRestriction = AngleRestriction.FORTYFIVE_DEGREE;

    this.minTraceHalfWidth = 100000;
    this.maxTraceHalfWidth = 100;
    this.holeClearance = 0;
  }

  /** Gets the default item clearance class. */
  public static int defaultClearanceClass() {
    return 1;
  }

  /** Returns the clearance class used for items with no clearances. */
  public static int clearanceClassNone() {
    return 0;
  }

  /** Returns the trace half-width used for routing with the input net on the input layer. */
  public int getTraceHalfWidth(int netNumber, int layer) {
    Net currentNet = nets.get(netNumber);
    return currentNet.getNetClass().getTraceHalfWidth(layer);
  }

  /**
   * Returns true, if the trace widths used for routing for the input net are equal on all layers.
   * If {@code netNumber} {@literal <} 0, the default trace widths for all nets are checked.
   */
  public boolean traceWidthsAreLayerDependent(int netNumber) {
    int compareWidth = getTraceHalfWidth(netNumber, 0);
    for (int i = 1; i < this.layerStructure.layers.length; i++) {
      if (getTraceHalfWidth(netNumber, i) != compareWidth) {
        return true;
      }
    }
    return false;
  }

  /** Returns the smallest of all default trace half-widths. */
  public int getMinTraceHalfWidth() {
    return minTraceHalfWidth;
  }

  /** Returns the biggest of all default trace half-widths. */
  public int getMaxTraceHalfWidth() {
    return maxTraceHalfWidth;
  }

  /** Returns the clearance around drilled holes. */
  public int getHoleClearance() {
    return holeClearance;
  }

  /** Sets the clearance around drilled holes. */
  public void setHoleClearance(int value) {
    this.holeClearance = Math.max(0, value);
  }

  /** Changes the default trace half-width used for routing on the input layer. */
  public void setDefaultTraceHalfWidth(int layer, int value) {
    this.getDefaultNetClass().setTraceHalfWidth(layer, value);
    minTraceHalfWidth = Math.min(minTraceHalfWidth, value);
    maxTraceHalfWidth = Math.max(maxTraceHalfWidth, value);
  }

  /** Returns the default trace half-width used on the input layer. */
  public int getDefaultTraceHalfWidth(int layer) {
    return this.getDefaultNetClass().getTraceHalfWidth(layer);
  }

  /** Changes the default trace half-width used for routing on all layers to the input value. */
  public void setDefaultTraceHalfWidths(int value) {
    if (value <= 0) {
      FRLogger.warn("BoardRules.set_trace_half_widths: value out of range");
      return;
    }
    this.getDefaultNetClass().setTraceHalfWidth(value);
    minTraceHalfWidth = Math.min(minTraceHalfWidth, value);
    maxTraceHalfWidth = Math.max(maxTraceHalfWidth, value);
  }

  /** Returns the net rule used for all nets for which no special rule was set. */
  public NetClass getDefaultNetClass() {
    if (this.netClasses.count() <= 0) {
      // net rules not yet initialized
      this.createDefaultNetClass();
    }
    return this.netClasses.get(0);
  }

  /** Returns an empty new net rule with an internally created name. */
  public NetClass getNewNetClass() {
    NetClass result = this.netClasses.append(this.layerStructure, this.clearanceMatrix);
    result.setTraceClearanceClass(this.getDefaultNetClass().getTraceClearanceClass());
    result.setViaRule(this.getDefaultViaRule());
    result.setTraceHalfWidth(this.getDefaultNetClass().getTraceHalfWidth(0));
    return result;
  }

  /** Returns an empty new net rule with an internally created name. */
  public NetClass getNewNetClass(String name) {
    NetClass result =
        this.netClasses.append(name, this.layerStructure, this.clearanceMatrix, false);
    result.setTraceClearanceClass(this.getDefaultNetClass().getTraceClearanceClass());
    result.setViaRule(this.getDefaultViaRule());
    result.setTraceHalfWidth(this.getDefaultNetClass().getTraceHalfWidth(0));
    return result;
  }

  /**
   * Creates a default via rule for {@code netClass} with name {@code name}. If more than one via
   * info with the same layer range is found, only the via info with the smallest pad size is
   * inserted.
   */
  public void createDefaultViaRule(NetClass netClass, String name) {
    if (this.viaInfos.count() == 0) {
      return;
    }
    // Add the rule  containing all vias.
    ViaRule defaultRule = new ViaRule(name);
    int defaultViaClClass =
        netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currentViaInfo = this.viaInfos.get(i);
      if (currentViaInfo.getClearanceClass() == defaultViaClClass) {
        Padstack currentPadstack = currentViaInfo.getPadstack();
        int currentFromLayer = currentPadstack.fromLayer();
        int currentToLayer = currentPadstack.toLayer();
        ViaInfo existingVia = defaultRule.getLayerRange(currentFromLayer, currentToLayer);
        if (existingVia != null) {
          ConvexShape newShape = currentPadstack.getShape(currentFromLayer);
          ConvexShape existingShape = existingVia.getPadstack().getShape(currentFromLayer);
          if (newShape.maxWidth() < existingShape.maxWidth()) {
            // The via with the smallest pad shape is preferred
            defaultRule.removeVia(existingVia);
            defaultRule.appendVia(currentViaInfo);
          }
        } else {
          defaultRule.appendVia(currentViaInfo);
        }
      }
    }
    this.viaRules.add(defaultRule);
    netClass.setViaRule(defaultRule);
  }

  /** Creates the default net class. */
  public void createDefaultNetClass() {
    // add the default net rule
    NetClass defaultNetClass =
        this.netClasses.append("default", this.layerStructure, this.clearanceMatrix, false);
    int defaultTraceHalfWidth = 1500;
    defaultNetClass.setTraceHalfWidth(defaultTraceHalfWidth);
    defaultNetClass.setTraceClearanceClass(1);
  }

  /** Appends a new net class initialized with default data and a default name. */
  public NetClass appendNetClass() {
    NetClass newClass = this.netClasses.append(this.layerStructure, this.clearanceMatrix);
    NetClass defaultClass = this.netClasses.get(0);
    newClass.setViaRule(defaultClass.getViaRule());
    newClass.setTraceHalfWidth(defaultClass.getTraceHalfWidth(0));
    newClass.setTraceClearanceClass(defaultClass.getTraceClearanceClass());
    return newClass;
  }

  /**
   * Appends a new net class initialized with default data and returns that class. If a class with
   * {@code name} exists, this class is returned without appending a new class.
   */
  public NetClass appendNetClass(String name) {
    NetClass foundClass = this.netClasses.get(name);
    if (foundClass != null) {
      return foundClass;
    }
    NetClass newClass =
        this.netClasses.append(name, this.layerStructure, this.clearanceMatrix, false);
    NetClass defaultClass = this.netClasses.get(0);
    newClass.defaultItemClearanceClasses =
        new DefaultItemClearanceClasses(defaultClass.defaultItemClearanceClasses);
    newClass.setViaRule(defaultClass.getViaRule());
    newClass.setTraceHalfWidth(defaultClass.getTraceHalfWidth(0));
    newClass.setTraceClearanceClass(defaultClass.getTraceClearanceClass());
    return newClass;
  }

  /** Returns the default via rule for routing, or null if no via rule exists. */
  public ViaRule getDefaultViaRule() {
    if (this.viaRules.isEmpty()) {
      return null;
    }
    return this.viaRules.getFirst();
  }

  /** Returns the via rule with the given name, or null if no such rule exists. */
  public ViaRule getViaRule(String name) {
    for (ViaRule currentRule : viaRules) {
      if (currentRule.name.equals(name)) {
        return currentRule;
      }
    }
    return null;
  }

  /**
   * Changes the clearance class index of all objects on the board with index {@code fromNo} to
   * {@code toNo}.
   */
  public void changeClearanceClassNo(int fromNo, int toNo, Collection<Item> boardItems) {
    for (Item currentItem : boardItems) {
      if (currentItem.clearanceClassIndex() == fromNo) {
        currentItem.setClearanceClassNo(toNo);
      }
    }

    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currentNetClass = this.netClasses.get(i);
      if (currentNetClass.getTraceClearanceClass() == fromNo) {
        currentNetClass.setTraceClearanceClass(toNo);
      }
      for (DefaultItemClearanceClasses.ItemClass currentItemClass :
          DefaultItemClearanceClasses.ItemClass.values()) {
        if (currentNetClass.defaultItemClearanceClasses.get(currentItemClass) == fromNo) {
          currentNetClass.defaultItemClearanceClasses.set(currentItemClass, toNo);
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currentVia = this.viaInfos.get(i);
      if (currentVia.getClearanceClass() == fromNo) {
        currentVia.setClearanceClass(toNo);
      }
    }
  }

  /**
   * Removes the clearance class with number {@code index}. Returns false if that was not possible
   * because there were still items assigned to this class.
   */
  public boolean removeClearanceClass(int index, Collection<Item> boardItems) {
    for (Item currentItem : boardItems) {
      if (currentItem.clearanceClassIndex() == index) {
        return false;
      }
    }
    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currentNetClass = this.netClasses.get(i);
      if (currentNetClass.getTraceClearanceClass() == index) {
        return false;
      }
      for (DefaultItemClearanceClasses.ItemClass currentItemClass :
          DefaultItemClearanceClasses.ItemClass.values()) {
        if (currentNetClass.defaultItemClearanceClasses.get(currentItemClass) == index) {
          return false;
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currentVia = this.viaInfos.get(i);
      if (currentVia.getClearanceClass() == index) {
        return false;
      }
    }

    for (Item currentItem : boardItems) {
      if (currentItem.clearanceClassIndex() > index) {
        currentItem.setClearanceClassNo(currentItem.clearanceClassIndex() - 1);
      }
    }

    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currentNetClass = this.netClasses.get(i);
      if (currentNetClass.getTraceClearanceClass() > index) {
        currentNetClass.setTraceClearanceClass(currentNetClass.getTraceClearanceClass() - 1);
      }
      for (DefaultItemClearanceClasses.ItemClass currentItemClass :
          DefaultItemClearanceClasses.ItemClass.values()) {
        int currentClassNo = currentNetClass.defaultItemClearanceClasses.get(currentItemClass);
        if (currentClassNo > index) {
          currentNetClass.defaultItemClearanceClasses.set(currentItemClass, currentClassNo - 1);
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currentVia = this.viaInfos.get(i);
      if (currentVia.getClearanceClass() > index) {
        currentVia.setClearanceClass(currentVia.getClearanceClass() - 1);
      }
    }
    this.clearanceMatrix.removeClass(index);
    return true;
  }

  /**
   * Returns the minimum distance between the pin border and the next corner of a connected trace
   * for a pin with connection restrictions. If the result is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public double getPinEdgeToTurnDist() {
    return this.pinEdgeToTurnDist;
  }

  /**
   * Sets the minimum distance between the pin border and the next corner of a connected trace for a
   * pin with connection restrictions. If {@code value} is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public void setPinEdgeToTurnDist(double value) {
    this.pinEdgeToTurnDist = value;
  }

  /** Returns whether the router ignores conduction areas. */
  public boolean getIgnoreConduction() {
    return this.ignoreConduction;
  }

  /** Sets whether the router should ignore conduction areas. */
  public void setIgnoreConduction(boolean value) {
    this.ignoreConduction = value;
  }

  /** The angle restriction for traces: 90 degree, 45 degree or none. */
  public AngleRestriction getTraceAngleRestriction() {
    return this.traceAngleRestriction;
  }

  /** Sets the angle restriction for traces: 90 degree, 45 degree or none. */
  public void setTraceAngleRestriction(AngleRestriction angleRestriction) {
    this.traceAngleRestriction = angleRestriction;
  }

  /**
   * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes
   * of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45
   * degree autorouting.
   */
  public boolean getUseSlowAutorouteAlgorithm() {
    return useSlowAutorouteAlgorithm;
  }

  /**
   * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes
   * of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45
   * degree autorouting.
   */
  public void setUseSlowAutorouteAlgorithm(boolean value) {
    useSlowAutorouteAlgorithm = value;
  }

  /** Returns the maximum diameter of the default via on its first and last layer. */
  public double getDefaultViaDiameter() {
    ViaRule defaultViaRule = this.getDefaultViaRule();
    if (defaultViaRule == null) {
      return 0;
    }
    if (defaultViaRule.viaCount() <= 0) {
      return 0;
    }
    Padstack viaPadstack = defaultViaRule.getVia(0).getPadstack();
    ConvexShape currentShape = viaPadstack.getShape(viaPadstack.fromLayer());
    double result = currentShape.maxWidth();
    currentShape = viaPadstack.getShape(viaPadstack.toLayer());
    return Math.max(result, currentShape.maxWidth());
  }

  /** Writes an instance of this class to a file. */
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    stream.writeInt(traceAngleRestriction.getValue());
  }

  /** Reads an instance of this class from a file. */
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int snapAngleNo = stream.readInt();
    this.traceAngleRestriction = AngleRestriction.valueOf(snapAngleNo);
  }
}
