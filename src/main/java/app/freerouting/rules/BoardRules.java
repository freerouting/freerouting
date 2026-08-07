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
  public BoardRules(LayerStructure p_layer_structure, ClearanceMatrix p_clearance_matrix) {
    layerStructure = p_layer_structure;
    clearanceMatrix = p_clearance_matrix;
    nets = new Nets();
    this.traceAngleRestriction = AngleRestriction.FORTYFIVE_DEGREE;

    this.minTraceHalfWidth = 100000;
    this.maxTraceHalfWidth = 100;
    this.holeClearance = 0;
  }

  /** Gets the default item clearance class */
  public static int default_clearance_class() {
    return 1;
  }

  /** For items with no clearances */
  public static int clearance_class_none() {
    return 0;
  }

  /** Returns the trace halfwidth used for routing with the input net on the input layer. */
  public int get_trace_half_width(int p_net_no, int p_layer) {
    Net currNet = nets.get(p_net_no);
    return currNet.getNetClass().get_trace_half_width(p_layer);
  }

  /**
   * Returns true, if the trace widths used for routing for the input net are equal on all layers.
   * If p_net_no {@literal <} 0, the default trace widths for all nets are checked.
   */
  public boolean trace_widths_are_layer_dependent(int p_net_no) {
    int compareWidth = get_trace_half_width(p_net_no, 0);
    for (int i = 1; i < this.layerStructure.arr.length; i++) {
      if (get_trace_half_width(p_net_no, i) != compareWidth) {
        return true;
      }
    }
    return false;
  }

  /** Returns he smallest of all default trace half widths */
  public int get_min_trace_half_width() {
    return minTraceHalfWidth;
  }

  /** Returns he biggest of all default trace half widths */
  public int get_max_trace_half_width() {
    return maxTraceHalfWidth;
  }

  public int get_hole_clearance() {
    return holeClearance;
  }

  public void set_hole_clearance(int p_value) {
    this.holeClearance = Math.max(0, p_value);
  }

  /** Changes the default trace halfwidth used for routing on the input layer. */
  public void set_default_trace_half_width(int p_layer, int p_value) {
    this.get_default_net_class().set_trace_half_width(p_layer, p_value);
    minTraceHalfWidth = Math.min(minTraceHalfWidth, p_value);
    maxTraceHalfWidth = Math.max(maxTraceHalfWidth, p_value);
  }

  public int get_default_trace_half_width(int p_layer) {
    return this.get_default_net_class().get_trace_half_width(p_layer);
  }

  /** Changes the default trace halfwidth used for routing on all layers to the input value. */
  public void set_default_trace_half_widths(int p_value) {
    if (p_value <= 0) {
      FRLogger.warn("BoardRules.set_trace_half_widths: p_value out of range");
      return;
    }
    this.get_default_net_class().set_trace_half_width(p_value);
    minTraceHalfWidth = Math.min(minTraceHalfWidth, p_value);
    maxTraceHalfWidth = Math.max(maxTraceHalfWidth, p_value);
  }

  /** Returns the net rule used for all nets, for which no special rrule was set. */
  public NetClass get_default_net_class() {
    if (this.netClasses.count() <= 0) {
      // net rules not yet initialized
      this.create_default_net_class();
    }
    return this.netClasses.get(0);
  }

  /** Returns an empty new net rule with an internally created name. */
  public NetClass get_new_net_class() {
    NetClass result = this.netClasses.append(this.layerStructure, this.clearanceMatrix);
    result.set_trace_clearance_class(this.get_default_net_class().get_trace_clearance_class());
    result.set_via_rule(this.get_default_via_rule());
    result.set_trace_half_width(this.get_default_net_class().get_trace_half_width(0));
    return result;
  }

  /** Returns an empty new net rule with an internally created name. */
  public NetClass get_new_net_class(String p_name) {
    NetClass result =
        this.netClasses.append(p_name, this.layerStructure, this.clearanceMatrix, false);
    result.set_trace_clearance_class(this.get_default_net_class().get_trace_clearance_class());
    result.set_via_rule(this.get_default_via_rule());
    result.set_trace_half_width(this.get_default_net_class().get_trace_half_width(0));
    return result;
  }

  /**
   * Create a default via rule for p_net_class with name p_name. If more than one via infos with the
   * same layer range are found, only the via info with the smallest pad size is inserted.
   */
  public void create_default_via_rule(NetClass p_net_class, String p_name) {
    if (this.viaInfos.count() == 0) {
      return;
    }
    // Add the rule  containing all vias.
    ViaRule defaultRule = new ViaRule(p_name);
    int defaultViaClClass =
        p_net_class.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currViaInfo = this.viaInfos.get(i);
      if (currViaInfo.get_clearance_class() == defaultViaClClass) {
        Padstack currPadstack = currViaInfo.get_padstack();
        int currFromLayer = currPadstack.from_layer();
        int currToLayer = currPadstack.to_layer();
        ViaInfo existingVia = defaultRule.get_layer_range(currFromLayer, currToLayer);
        if (existingVia != null) {
          ConvexShape newShape = currPadstack.get_shape(currFromLayer);
          ConvexShape existingShape = existingVia.get_padstack().get_shape(currFromLayer);
          if (newShape.max_width() < existingShape.max_width()) {
            // The via with the smallest pad shape is preferred
            defaultRule.remove_via(existingVia);
            defaultRule.append_via(currViaInfo);
          }
        } else {
          defaultRule.append_via(currViaInfo);
        }
      }
    }
    this.viaRules.add(defaultRule);
    p_net_class.set_via_rule(defaultRule);
  }

  public void create_default_net_class() {
    // add the default net rule
    NetClass defaultNetClass =
        this.netClasses.append("default", this.layerStructure, this.clearanceMatrix, false);
    int defaultTraceHalfWidth = 1500;
    defaultNetClass.set_trace_half_width(defaultTraceHalfWidth);
    defaultNetClass.set_trace_clearance_class(1);
  }

  /** Appends a new net class initialized with default data and a default name. */
  public NetClass append_net_class() {
    NetClass newClass = this.netClasses.append(this.layerStructure, this.clearanceMatrix);
    NetClass defaultClass = this.netClasses.get(0);
    newClass.set_via_rule(defaultClass.get_via_rule());
    newClass.set_trace_half_width(defaultClass.get_trace_half_width(0));
    newClass.set_trace_clearance_class(defaultClass.get_trace_clearance_class());
    return newClass;
  }

  /**
   * Appends a new net class initialized with default data and returns that class. If a class with
   * p_name exists, this class is returned without appending a new class.
   */
  public NetClass append_net_class(String p_name) {
    NetClass foundClass = this.netClasses.get(p_name);
    if (foundClass != null) {
      return foundClass;
    }
    NetClass newClass =
        this.netClasses.append(p_name, this.layerStructure, this.clearanceMatrix, false);
    NetClass defaultClass = this.netClasses.get(0);
    newClass.defaultItemClearanceClasses =
        new DefaultItemClearanceClasses(defaultClass.defaultItemClearanceClasses);
    newClass.set_via_rule(defaultClass.get_via_rule());
    newClass.set_trace_half_width(defaultClass.get_trace_half_width(0));
    newClass.set_trace_clearance_class(defaultClass.get_trace_clearance_class());
    return newClass;
  }

  /** Returns the default via rule for routing or null, if no via rule exists. */
  public ViaRule get_default_via_rule() {
    if (this.viaRules.isEmpty()) {
      return null;
    }
    return this.viaRules.getFirst();
  }

  /** Returns the via rule with name p_name, or null, if no such rule exists. */
  public ViaRule get_via_rule(String p_name) {
    for (ViaRule currRule : viaRules) {
      if (currRule.name.equals(p_name)) {
        return currRule;
      }
    }
    return null;
  }

  /**
   * Changes the clearance class index of all objects on the board with index p_from_no to p_to_no.
   */
  public void change_clearance_class_no(
      int p_from_no, int p_to_no, Collection<Item> p_board_items) {
    for (Item currItem : p_board_items) {
      if (currItem.clearance_class_no() == p_from_no) {
        currItem.set_clearance_class_no(p_to_no);
      }
    }

    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currNetClass = this.netClasses.get(i);
      if (currNetClass.get_trace_clearance_class() == p_from_no) {
        currNetClass.set_trace_clearance_class(p_to_no);
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class :
          DefaultItemClearanceClasses.ItemClass.values()) {
        if (currNetClass.defaultItemClearanceClasses.get(curr_item_class) == p_from_no) {
          currNetClass.defaultItemClearanceClasses.set(curr_item_class, p_to_no);
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currVia = this.viaInfos.get(i);
      if (currVia.get_clearance_class() == p_from_no) {
        currVia.set_clearance_class(p_to_no);
      }
    }
  }

  /**
   * Removes the clearance class with number p_index. Returns false, if that was not possible,
   * because there were still items assigned to this class.
   */
  public boolean remove_clearance_class(int p_index, Collection<Item> p_board_items) {
    for (Item currItem : p_board_items) {
      if (currItem.clearance_class_no() == p_index) {
        return false;
      }
    }
    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currNetClass = this.netClasses.get(i);
      if (currNetClass.get_trace_clearance_class() == p_index) {
        return false;
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class :
          DefaultItemClearanceClasses.ItemClass.values()) {
        if (currNetClass.defaultItemClearanceClasses.get(curr_item_class) == p_index) {
          return false;
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currVia = this.viaInfos.get(i);
      if (currVia.get_clearance_class() == p_index) {
        return false;
      }
    }

    for (Item currItem : p_board_items) {
      if (currItem.clearance_class_no() > p_index) {
        currItem.set_clearance_class_no(currItem.clearance_class_no() - 1);
      }
    }

    for (int i = 0; i < this.netClasses.count(); i++) {
      NetClass currNetClass = this.netClasses.get(i);
      if (currNetClass.get_trace_clearance_class() > p_index) {
        currNetClass.set_trace_clearance_class(currNetClass.get_trace_clearance_class() - 1);
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class :
          DefaultItemClearanceClasses.ItemClass.values()) {
        int currClassNo = currNetClass.defaultItemClearanceClasses.get(curr_item_class);
        if (currClassNo > p_index) {
          currNetClass.defaultItemClearanceClasses.set(curr_item_class, currClassNo - 1);
        }
      }
    }

    for (int i = 0; i < this.viaInfos.count(); i++) {
      ViaInfo currVia = this.viaInfos.get(i);
      if (currVia.get_clearance_class() > p_index) {
        currVia.set_clearance_class(currVia.get_clearance_class() - 1);
      }
    }
    this.clearanceMatrix.remove_class(p_index);
    return true;
  }

  /**
   * Returns the minimum distance between the pin border and the next corner of a connected trace
   * por a pin with connection restrictions. If the result is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public double get_pin_edge_to_turn_dist() {
    return this.pinEdgeToTurnDist;
  }

  /**
   * Sets he minimum distance between the pin border and the next corner of a connected trace por a
   * pin with connection restrictions. if p_value is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public void set_pin_edge_to_turn_dist(double p_value) {
    this.pinEdgeToTurnDist = p_value;
  }

  /** If true, the router ignores conduction areas. */
  public boolean get_ignore_conduction() {
    return this.ignoreConduction;
  }

  /** Tells the router, if conduction areas should be ignored. */
  public void set_ignore_conduction(boolean p_value) {
    this.ignoreConduction = p_value;
  }

  /** The angle restriction for traces: 90 degree, 45 degree or none. */
  public AngleRestriction get_trace_angle_restriction() {
    return this.traceAngleRestriction;
  }

  /** Sets the angle restriction for traces: 90 degree, 45 degree or none. */
  public void set_trace_angle_restriction(AngleRestriction p_angle_restriction) {
    this.traceAngleRestriction = p_angle_restriction;
  }

  /**
   * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes
   * of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45
   * degree autorouting.
   */
  public boolean get_use_slow_autoroute_algorithm() {
    return useSlowAutorouteAlgorithm;
  }

  /**
   * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes
   * of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45
   * degree autorouting.
   */
  public void set_use_slow_autoroute_algorithm(boolean p_value) {
    useSlowAutorouteAlgorithm = p_value;
  }

  /** Returns the Maximum of the diameter of the default via on its first and last layer. */
  public double get_default_via_diameter() {
    ViaRule defaultViaRule = this.get_default_via_rule();
    if (defaultViaRule == null) {
      return 0;
    }
    if (defaultViaRule.via_count() <= 0) {
      return 0;
    }
    Padstack viaPadstack = defaultViaRule.get_via(0).get_padstack();
    ConvexShape currShape = viaPadstack.get_shape(viaPadstack.from_layer());
    double result = currShape.max_width();
    currShape = viaPadstack.get_shape(viaPadstack.to_layer());
    return Math.max(result, currShape.max_width());
  }

  /** Writes an instance of this class to a file */
  private void writeObject(ObjectOutputStream p_stream) throws IOException {
    p_stream.defaultWriteObject();
    p_stream.writeInt(traceAngleRestriction.getValue());
  }

  /** Reads an instance of this class from a file */
  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    p_stream.defaultReadObject();
    int snapAngleNo = p_stream.readInt();
    this.traceAngleRestriction = AngleRestriction.valueOf(snapAngleNo);
  }
}
