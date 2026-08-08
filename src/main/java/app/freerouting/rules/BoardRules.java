package app.freerouting.rules;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Vector;

/** Contains the rules and constraints required for items to be inserted into a routing board */
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

  /** The smallest of all default trace half widths */
  private int minTraceHalfWidth;

  /** The biggest of all default trace half widths */
  private int maxTraceHalfWidth;

  /**
   * The minimum distance of the pad border to the first turn of a connected trace to a pin with
   * restricted exit directions. If the value is {@literal <}= 0, there are no exit restrictions.
   */
  private double pinEdgeToTurnDist;

  private boolean useSlowAutorouteAlgorithm;
  private int holeClearance;

  /** Creates a new instance of this class. */
  public BoardRules(LayerStructure pLayerStructure, ClearanceMatrix pClearanceMatrix) {
    layerStructure = pLayerStructure;
    clearanceMatrix = pClearanceMatrix;
    nets = new Nets();
    this.traceAngleRestriction = AngleRestriction.FORTYFIVE_DEGREE;

    this.minTraceHalfWidth = 100000;
    this.maxTraceHalfWidth = 100;
    this.holeClearance = 0;
  }

  /** Gets the default item clearance class */
  public static int defaultClearanceClass() {
    return 1;
  }

  /** For items with no clearances */
  public static int clearanceClassNone() {
    return 0;
  }

  /** Returns the trace halfwidth used for routing with the input net on the input layer. */
  public int getTraceHalfWidth(int pNetNo, int pLayer) {
    Net currNet = nets.get(pNetNo);
    return currNet.getNetClass().getTraceHalfWidth(pLayer);
  }

  /**
   * Returns true, if the trace widths used for routing for the input net are equal on all layers.
   * If p_net_no {@literal <} 0, the default trace widths for all nets are checked.
   */
  public boolean traceWidthsAreLayerDependent(int pNetNo) {
    int compareWidth = getTraceHalfWidth(pNetNo, 0);
    for (int i = 1; i < this.layerStructure.arr.length; i++) {
      if (getTraceHalfWidth(pNetNo, i) != compareWidth) {
        return true;
      }
    }
    return false;
  }

  /** Returns he smallest of all default trace half widths */
  public int getMinTraceHalfWidth() {
    return minTraceHalfWidth;
  }

  /** Returns he biggest of all default trace half widths */
  public int getMaxTraceHalfWidth() {
    return maxTraceHalfWidth;
  }

  public int getHoleClearance() {
    return holeClearance;
  }

  public void setHoleClearance(int pValue) {
    this.holeClearance = Math.max(0, pValue);
  }

  /** Changes the default trace halfwidth used for routing on the input layer. */
  public void setDefaultTraceHalfWidth(int pLayer, int pValue) {
    this.getDefaultNetClass().setTraceHalfWidth(pLayer, pValue);
    minTraceHalfWidth = Math.min(minTraceHalfWidth, pValue);
    maxTraceHalfWidth = Math.max(maxTraceHalfWidth, pValue);
  }

  public int getDefaultTraceHalfWidth(int pLayer) {
    return this.getDefaultNetClass().getTraceHalfWidth(pLayer);
  }

  /** Changes the default trace halfwidth used for routing on all layers to the input value. */
  public void setDefaultTraceHalfWidths(int pValue) {
    if (pValue <= 0) {
      FRLogger.warn("BoardRules.set_trace_half_widths: p_value out of range");
      return;
    }
    this.getDefaultNetClass().setTraceHalfWidth(pValue);
    minTraceHalfWidth = Math.min(minTraceHalfWidth, pValue);
    maxTraceHalfWidth = Math.max(maxTraceHalfWidth, pValue);
  }

  /** Returns the net rule used for all nets, for which no special rrule was set. */
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
  public NetClass getNewNetClass(String pName) {
    NetClass result =
        this.netClasses.append(pName, this.layerStructure, this.clearanceMatrix, false);
    result.setTraceClearanceClass(this.getDefaultNetClass().getTraceClearanceClass());
    result.setViaRule(this.getDefaultViaRule());
    result.setTraceHalfWidth(this.getDefaultNetClass().getTraceHalfWidth(0));
    return result;
  }

  /**
   * Create a default via rule for p_net_class with name p_name. If more than one via infos with the
   * same layer range are found, only the via info with the smallest pad size is inserted.
   */
  public void createDefaultViaRule(NetClass pNetClass, String pName) {
    if (this.viaInfos.count() == 0) {
      return;
    }
    // Add the rule  containing all vias.
    ViaRule defaultRule = new ViaRule(pName);
    int defaultViaClClass =
        pNetClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currViaInfo = this.viaInfos.get(i);
      if (currViaInfo.getClearanceClass() == defaultViaClClass) {
        Padstack currPadstack = currViaInfo.getPadstack();
        int currFromLayer = currPadstack.fromLayer();
        int currToLayer = currPadstack.toLayer();
        ViaInfo existingVia = defaultRule.getLayerRange(currFromLayer, currToLayer);
        if (existingVia != null) {
          ConvexShape newShape = currPadstack.getShape(currFromLayer);
          ConvexShape existingShape = existingVia.getPadstack().getShape(currFromLayer);
          if (newShape.maxWidth() < existingShape.maxWidth()) {
            // The via with the smallest pad shape is preferred
            defaultRule.removeVia(existingVia);
            defaultRule.appendVia(currViaInfo);
          }
        } else {
          defaultRule.appendVia(currViaInfo);
        }
      }
    }
    this.viaRules.add(defaultRule);
    pNetClass.setViaRule(defaultRule);
  }

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
   * p_name exists, this class is returned without appending a new class.
   */
  public NetClass appendNetClass(String pName) {
    NetClass foundClass = this.netClasses.get(pName);
    if (foundClass != null) {
      return foundClass;
    }
    NetClass newClass =
        this.netClasses.append(pName, this.layerStructure, this.clearanceMatrix, false);
    NetClass defaultClass = this.netClasses.get(0);
    newClass.defaultItemClearanceClasses =
        new DefaultItemClearanceClasses(defaultClass.defaultItemClearanceClasses);
    newClass.setViaRule(defaultClass.getViaRule());
    newClass.setTraceHalfWidth(defaultClass.getTraceHalfWidth(0));
    newClass.setTraceClearanceClass(defaultClass.getTraceClearanceClass());
    return newClass;
  }

  /** Returns the default via rule for routing or null, if no via rule exists. */
  public ViaRule getDefaultViaRule() {
    if (this.viaRules.isEmpty()) {
      return null;
    }
    return this.viaRules.getFirst();
  }

  /** Returns the via rule with name p_name, or null, if no such rule exists. */
  public ViaRule getViaRule(String pName) {
    for (ViaRule currRule : viaRules) {
      if (currRule.name.equals(pName)) {
        return currRule;
      }
    }
    return null;
  }

  /**
   * Changes the clearance class index of all objects on the board with index p_from_no to p_to_no.
   */
  public void changeClearanceClassNo(int pFromNo, int pToNo, Collection<Item> pBoardItems) {
    for (Item currItem : pBoardItems) {
      if (currItem.clearanceClassNo() == pFromNo) {
        currItem.setClearanceClassNo(pToNo);
      }
    }

    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currNetClass = this.netClasses.get(i);
      if (currNetClass.getTraceClearanceClass() == pFromNo) {
        currNetClass.setTraceClearanceClass(pToNo);
      }
      for (DefaultItemClearanceClasses.ItemClass currItemClass :
          DefaultItemClearanceClasses.ItemClass.values()) {
        if (currNetClass.defaultItemClearanceClasses.get(currItemClass) == pFromNo) {
          currNetClass.defaultItemClearanceClasses.set(currItemClass, pToNo);
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currVia = this.viaInfos.get(i);
      if (currVia.getClearanceClass() == pFromNo) {
        currVia.setClearanceClass(pToNo);
      }
    }
  }

  /**
   * Removes the clearance class with number p_index. Returns false, if that was not possible,
   * because there were still items assigned to this class.
   */
  public boolean removeClearanceClass(int pIndex, Collection<Item> pBoardItems) {
    for (Item currItem : pBoardItems) {
      if (currItem.clearanceClassNo() == pIndex) {
        return false;
      }
    }
    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currNetClass = this.netClasses.get(i);
      if (currNetClass.getTraceClearanceClass() == pIndex) {
        return false;
      }
      for (DefaultItemClearanceClasses.ItemClass currItemClass :
          DefaultItemClearanceClasses.ItemClass.values()) {
        if (currNetClass.defaultItemClearanceClasses.get(currItemClass) == pIndex) {
          return false;
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currVia = this.viaInfos.get(i);
      if (currVia.getClearanceClass() == pIndex) {
        return false;
      }
    }

    for (Item currItem : pBoardItems) {
      if (currItem.clearanceClassNo() > pIndex) {
        currItem.setClearanceClassNo(currItem.clearanceClassNo() - 1);
      }
    }

    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currNetClass = this.netClasses.get(i);
      if (currNetClass.getTraceClearanceClass() > pIndex) {
        currNetClass.setTraceClearanceClass(currNetClass.getTraceClearanceClass() - 1);
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class :
          DefaultItemClearanceClasses.ItemClass.values()) {
        int currClassNo = currNetClass.defaultItemClearanceClasses.get(curr_item_class);
        if (currClassNo > pIndex) {
          currNetClass.defaultItemClearanceClasses.set(curr_item_class, currClassNo - 1);
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currVia = this.viaInfos.get(i);
      if (currVia.getClearanceClass() > pIndex) {
        currVia.setClearanceClass(currVia.getClearanceClass() - 1);
      }
    }
    this.clearanceMatrix.removeClass(pIndex);
    return true;
  }

  /**
   * Returns the minimum distance between the pin border and the next corner of a connected trace
   * por a pin with connection restrictions. If the result is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public double getPinEdgeToTurnDist() {
    return this.pinEdgeToTurnDist;
  }

  /**
   * Sets he minimum distance between the pin border and the next corner of a connected trace por a
   * pin with connection restrictions. if p_value is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public void setPinEdgeToTurnDist(double pValue) {
    this.pinEdgeToTurnDist = pValue;
  }

  /** If true, the router ignores conduction areas. */
  public boolean getIgnoreConduction() {
    return this.ignoreConduction;
  }

  /** Tells the router, if conduction areas should be ignored. */
  public void setIgnoreConduction(boolean pValue) {
    this.ignoreConduction = pValue;
  }

  /** The angle restriction for traces: 90 degree, 45 degree or none. */
  public AngleRestriction getTraceAngleRestriction() {
    return this.traceAngleRestriction;
  }

  /** Sets the angle restriction for traces: 90 degree, 45 degree or none. */
  public void setTraceAngleRestriction(AngleRestriction pAngleRestriction) {
    this.traceAngleRestriction = pAngleRestriction;
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
  public void setUseSlowAutorouteAlgorithm(boolean pValue) {
    useSlowAutorouteAlgorithm = pValue;
  }

  /** Returns the Maximum of the diameter of the default via on its first and last layer. */
  public double getDefaultViaDiameter() {
    ViaRule defaultViaRule = this.getDefaultViaRule();
    if (defaultViaRule == null) {
      return 0;
    }
    if (defaultViaRule.viaCount() <= 0) {
      return 0;
    }
    Padstack viaPadstack = defaultViaRule.getVia(0).getPadstack();
    ConvexShape currShape = viaPadstack.getShape(viaPadstack.fromLayer());
    double result = currShape.maxWidth();
    currShape = viaPadstack.getShape(viaPadstack.toLayer());
    return Math.max(result, currShape.maxWidth());
  }

  /** Writes an instance of this class to a file */
  private void writeObject(ObjectOutputStream pStream) throws IOException {
    pStream.defaultWriteObject();
    pStream.writeInt(traceAngleRestriction.getValue());
  }

  /** Reads an instance of this class from a file */
  private void readObject(ObjectInputStream pStream) throws IOException, ClassNotFoundException {
    pStream.defaultReadObject();
    int snapAngleNo = pStream.readInt();
    this.traceAngleRestriction = AngleRestriction.valueOf(snapAngleNo);
  }
}
