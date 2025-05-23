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
import java.util.Locale;
import java.util.Vector;

/**
 * Contains the rules and constraints required for items to be inserted into a routing board
 */
public class BoardRules implements Serializable
{
  /**
   * The matrix describing the spacing restrictions between item clearance classes.
   */
  public final ClearanceMatrix clearance_matrix;
  /**
   * Describes the electrical nets on the board.
   */
  public final Nets nets;
  public final ViaInfos via_infos = new ViaInfos();
  public final Vector<ViaRule> via_rules = new Vector<>();
  public final NetClasses net_classes = new NetClasses();
  private final LayerStructure layer_structure;
  /**
   * The angle restriction for traces: 90 degree, 45 degree or none.
   */
  private transient AngleRestriction trace_angle_restriction;
  /**
   * If true, the router ignores conduction areas.
   */
  private boolean ignore_conduction = true;
  /**
   * The smallest of all default trace half widths
   */
  private int min_trace_half_width;
  /**
   * The biggest of all default trace half widths
   */
  private int max_trace_half_width;
  /**
   * The minimum distance of the pad border to the first turn of a connected trace to a pin with
   * restricted exit directions. If the value is {@literal <}= 0, there are no exit restrictions.
   */
  private double pin_edge_to_turn_dist;
  private boolean use_slow_autoroute_algorithm = false;

  /**
   * Creates a new instance of this class.
   */
  public BoardRules(LayerStructure p_layer_structure, ClearanceMatrix p_clearance_matrix)
  {
    layer_structure = p_layer_structure;
    clearance_matrix = p_clearance_matrix;
    nets = new Nets();
    this.trace_angle_restriction = AngleRestriction.FORTYFIVE_DEGREE;

    this.min_trace_half_width = 100000;
    this.max_trace_half_width = 100;
  }

  /**
   * Gets the default item clearance class
   */
  public static int default_clearance_class()
  {
    return 1;
  }

  /**
   * For items with no clearances
   */
  public static int clearance_class_none()
  {
    return 0;
  }

  /**
   * Returns the trace halfwidth used for routing with the input net on the input layer.
   */
  public int get_trace_half_width(int p_net_no, int p_layer)
  {
    Net curr_net = nets.get(p_net_no);
    return curr_net
        .get_class()
        .get_trace_half_width(p_layer);
  }

  /**
   * Returns true, if the trace widths used for routing for the input net are equal on all layers.
   * If p_net_no {@literal <} 0, the default trace widths for all nets are checked.
   */
  public boolean trace_widths_are_layer_dependent(int p_net_no)
  {
    int compare_width = get_trace_half_width(p_net_no, 0);
    for (int i = 1; i < this.layer_structure.arr.length; ++i)
    {
      if (get_trace_half_width(p_net_no, i) != compare_width)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns he smallest of all default trace half widths
   */
  public int get_min_trace_half_width()
  {
    return min_trace_half_width;
  }

  /**
   * Returns he biggest of all default trace half widths
   */
  public int get_max_trace_half_width()
  {
    return max_trace_half_width;
  }

  /**
   * Changes the default trace halfwidth used for routing on the input layer.
   */
  public void set_default_trace_half_width(int p_layer, int p_value)
  {
    this
        .get_default_net_class()
        .set_trace_half_width(p_layer, p_value);
    min_trace_half_width = Math.min(min_trace_half_width, p_value);
    max_trace_half_width = Math.max(max_trace_half_width, p_value);
  }

  public int get_default_trace_half_width(int p_layer)
  {
    return this
        .get_default_net_class()
        .get_trace_half_width(p_layer);
  }

  /**
   * Changes the default trace halfwidth used for routing on all layers to the input value.
   */
  public void set_default_trace_half_widths(int p_value)
  {
    if (p_value <= 0)
    {
      FRLogger.warn("BoardRules.set_trace_half_widths: p_value out of range");
      return;
    }
    this
        .get_default_net_class()
        .set_trace_half_width(p_value);
    min_trace_half_width = Math.min(min_trace_half_width, p_value);
    max_trace_half_width = Math.max(max_trace_half_width, p_value);
  }

  /**
   * Returns the net rule used for all nets, for which no special rrule was set.
   */
  public NetClass get_default_net_class()
  {
    if (this.net_classes.count() <= 0)
    {
      // net rules not yet initialized
      this.create_default_net_class();
    }
    return this.net_classes.get(0);
  }

  /**
   * Returns an empty new net rule with an internally created name.
   */
  public NetClass get_new_net_class(Locale p_locale)
  {
    NetClass result = this.net_classes.append(this.layer_structure, this.clearance_matrix, p_locale);
    result.set_trace_clearance_class(this
        .get_default_net_class()
        .get_trace_clearance_class());
    result.set_via_rule(this.get_default_via_rule());
    result.set_trace_half_width(this
        .get_default_net_class()
        .get_trace_half_width(0));
    return result;
  }

  /**
   * Returns an empty new net rule with an internally created name.
   */
  public NetClass get_new_net_class(String p_name)
  {
    NetClass result = this.net_classes.append(p_name, this.layer_structure, this.clearance_matrix, false);
    result.set_trace_clearance_class(this
        .get_default_net_class()
        .get_trace_clearance_class());
    result.set_via_rule(this.get_default_via_rule());
    result.set_trace_half_width(this
        .get_default_net_class()
        .get_trace_half_width(0));
    return result;
  }

  /**
   * Create a default via rule for p_net_class with name p_name. If more than one via infos with the
   * same layer range are found, only the via info with the smallest pad size is inserted.
   */
  public void create_default_via_rule(NetClass p_net_class, String p_name)
  {
    if (this.via_infos.count() == 0)
    {
      return;
    }
    // Add the rule  containing all vias.
    ViaRule default_rule = new ViaRule(p_name);
    int default_via_cl_class = p_net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (int i = 0; i < this.via_infos.count(); ++i)
    {
      ViaInfo curr_via_info = this.via_infos.get(i);
      if (curr_via_info.get_clearance_class() == default_via_cl_class)
      {
        Padstack curr_padstack = curr_via_info.get_padstack();
        int curr_from_layer = curr_padstack.from_layer();
        int curr_to_layer = curr_padstack.to_layer();
        ViaInfo existing_via = default_rule.get_layer_range(curr_from_layer, curr_to_layer);
        if (existing_via != null)
        {
          ConvexShape new_shape = curr_padstack.get_shape(curr_from_layer);
          ConvexShape existing_shape = existing_via
              .get_padstack()
              .get_shape(curr_from_layer);
          if (new_shape.max_width() < existing_shape.max_width())
          {
            // The via with the smallest pad shape is preferred
            default_rule.remove_via(existing_via);
            default_rule.append_via(curr_via_info);
          }
        }
        else
        {
          default_rule.append_via(curr_via_info);
        }
      }
    }
    this.via_rules.add(default_rule);
    p_net_class.set_via_rule(default_rule);
  }

  public void create_default_net_class()
  {
    // add the default net rule
    NetClass default_net_class = this.net_classes.append("default", this.layer_structure, this.clearance_matrix, false);
    int default_trace_half_width = 1500;
    default_net_class.set_trace_half_width(default_trace_half_width);
    default_net_class.set_trace_clearance_class(1);
  }

  /**
   * Appends a new net class initialized with default data and a default name.
   */
  public NetClass append_net_class(Locale p_locale)
  {
    NetClass new_class = this.net_classes.append(this.layer_structure, this.clearance_matrix, p_locale);
    NetClass default_class = this.net_classes.get(0);
    new_class.set_via_rule(default_class.get_via_rule());
    new_class.set_trace_half_width(default_class.get_trace_half_width(0));
    new_class.set_trace_clearance_class(default_class.get_trace_clearance_class());
    return new_class;
  }

  /**
   * Appends a new net class initialized with default data and returns that class. If a class with
   * p_name exists, this class is returned without appending a new class.
   */
  public NetClass append_net_class(String p_name)
  {
    NetClass found_class = this.net_classes.get(p_name);
    if (found_class != null)
    {
      return found_class;
    }
    NetClass new_class = this.net_classes.append(p_name, this.layer_structure, this.clearance_matrix, false);
    NetClass default_class = this.net_classes.get(0);
    new_class.default_item_clearance_classes = new DefaultItemClearanceClasses(default_class.default_item_clearance_classes);
    new_class.set_via_rule(default_class.get_via_rule());
    new_class.set_trace_half_width(default_class.get_trace_half_width(0));
    new_class.set_trace_clearance_class(default_class.get_trace_clearance_class());
    return new_class;
  }

  /**
   * Returns the default via rule for routing or null, if no via rule exists.
   */
  public ViaRule get_default_via_rule()
  {
    if (this.via_rules.isEmpty())
    {
      return null;
    }
    return this.via_rules.get(0);
  }

  /**
   * Returns the via rule wit name p_name, or null, if no such rule exists.
   */
  public ViaRule get_via_rule(String p_name)
  {
    for (ViaRule curr_rule : via_rules)
    {
      if (curr_rule.name.equals(p_name))
      {
        return curr_rule;
      }
    }
    return null;
  }

  /**
   * Changes the clearance class index of all objects on the board with index p_from_no to p_to_no.
   */
  public void change_clearance_class_no(int p_from_no, int p_to_no, Collection<Item> p_board_items)
  {
    for (Item curr_item : p_board_items)
    {
      if (curr_item.clearance_class_no() == p_from_no)
      {
        curr_item.set_clearance_class_no(p_to_no);
      }
    }

    for (int i = 0; i < this.net_classes.count(); ++i)
    {
      NetClass curr_net_class = this.net_classes.get(i);
      if (curr_net_class.get_trace_clearance_class() == p_from_no)
      {
        curr_net_class.set_trace_clearance_class(p_to_no);
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class : DefaultItemClearanceClasses.ItemClass.values())
      {
        if (curr_net_class.default_item_clearance_classes.get(curr_item_class) == p_from_no)
        {
          curr_net_class.default_item_clearance_classes.set(curr_item_class, p_to_no);
        }
      }
    }

    for (int i = 0; i < this.via_infos.count(); ++i)
    {
      ViaInfo curr_via = this.via_infos.get(i);
      if (curr_via.get_clearance_class() == p_from_no)
      {
        curr_via.set_clearance_class(p_to_no);
      }
    }
  }

  /**
   * Removes the clearance class with number p_index. Returns false, if that was not possible,
   * because there were still items assigned to this class.
   */
  public boolean remove_clearance_class(int p_index, Collection<Item> p_board_items)
  {
    for (Item curr_item : p_board_items)
    {
      if (curr_item.clearance_class_no() == p_index)
      {
        return false;
      }
    }
    for (int i = 0; i < this.net_classes.count(); ++i)
    {
      NetClass curr_net_class = this.net_classes.get(i);
      if (curr_net_class.get_trace_clearance_class() == p_index)
      {
        return false;
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class : DefaultItemClearanceClasses.ItemClass.values())
      {
        if (curr_net_class.default_item_clearance_classes.get(curr_item_class) == p_index)
        {
          return false;
        }
      }
    }

    for (int i = 0; i < this.via_infos.count(); ++i)
    {
      ViaInfo curr_via = this.via_infos.get(i);
      if (curr_via.get_clearance_class() == p_index)
      {
        return false;
      }
    }

    for (Item curr_item : p_board_items)
    {
      if (curr_item.clearance_class_no() > p_index)
      {
        curr_item.set_clearance_class_no(curr_item.clearance_class_no() - 1);
      }
    }

    for (int i = 0; i < this.net_classes.count(); ++i)
    {
      NetClass curr_net_class = this.net_classes.get(i);
      if (curr_net_class.get_trace_clearance_class() > p_index)
      {
        curr_net_class.set_trace_clearance_class(curr_net_class.get_trace_clearance_class() - 1);
      }
      for (DefaultItemClearanceClasses.ItemClass curr_item_class : DefaultItemClearanceClasses.ItemClass.values())
      {
        int curr_class_no = curr_net_class.default_item_clearance_classes.get(curr_item_class);
        if (curr_class_no > p_index)
        {
          curr_net_class.default_item_clearance_classes.set(curr_item_class, curr_class_no - 1);
        }
      }
    }

    for (int i = 0; i < this.via_infos.count(); ++i)
    {
      ViaInfo curr_via = this.via_infos.get(i);
      if (curr_via.get_clearance_class() > p_index)
      {
        curr_via.set_clearance_class(curr_via.get_clearance_class() - 1);
      }
    }
    this.clearance_matrix.remove_class(p_index);
    return true;
  }

  /**
   * Returns the minimum distance between the pin border and the next corner of a connected trace
   * por a pin with connection restrictions. If the result is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public double get_pin_edge_to_turn_dist()
  {
    return this.pin_edge_to_turn_dist;
  }

  /**
   * Sets he minimum distance between the pin border and the next corner of a connected trace por a
   * pin with connection restrictions. if p_value is {@literal <}= 0, there are no exit
   * restrictions.
   */
  public void set_pin_edge_to_turn_dist(double p_value)
  {
    this.pin_edge_to_turn_dist = p_value;
  }

  /**
   * If true, the router ignores conduction areas.
   */
  public boolean get_ignore_conduction()
  {
    return this.ignore_conduction;
  }

  /**
   * Tells the router, if conduction areas should be ignored.
   */
  public void set_ignore_conduction(boolean p_value)
  {
    this.ignore_conduction = p_value;
  }

  /**
   * The angle restriction for traces: 90 degree, 45 degree or none.
   */
  public AngleRestriction get_trace_angle_restriction()
  {
    return this.trace_angle_restriction;
  }

  /**
   * Sets the angle restriction for traces: 90 degree, 45 degree or none.
   */
  public void set_trace_angle_restriction(AngleRestriction p_angle_restriction)
  {
    this.trace_angle_restriction = p_angle_restriction;
  }

  /**
   * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes
   * of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45
   * degree autorouting.
   */
  public boolean get_use_slow_autoroute_algorithm()
  {
    return use_slow_autoroute_algorithm;
  }

  /**
   * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes
   * of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45
   * degree autorouting.
   */
  public void set_use_slow_autoroute_algorithm(boolean p_value)
  {
    use_slow_autoroute_algorithm = p_value;
  }

  /**
   * Returns the Maximum of the diameter of the default via on its first and last layer.
   */
  public double get_default_via_diameter()
  {
    ViaRule default_via_rule = this.get_default_via_rule();
    if (default_via_rule == null)
    {
      return 0;
    }
    if (default_via_rule.via_count() <= 0)
    {
      return 0;
    }
    Padstack via_padstack = default_via_rule
        .get_via(0)
        .get_padstack();
    ConvexShape curr_shape = via_padstack.get_shape(via_padstack.from_layer());
    double result = curr_shape.max_width();
    curr_shape = via_padstack.get_shape(via_padstack.to_layer());
    result = Math.max(result, curr_shape.max_width());
    return result;
  }

  /**
   * Writes an instance of this class to a file
   */
  private void writeObject(ObjectOutputStream p_stream) throws IOException
  {
    p_stream.defaultWriteObject();
    p_stream.writeInt(trace_angle_restriction.getValue());
  }

  /**
   * Reads an instance of this class from a file
   */
  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException
  {
    p_stream.defaultReadObject();
    int snap_angle_no = p_stream.readInt();
    this.trace_angle_restriction = AngleRestriction.valueOf(snap_angle_no);
  }
}