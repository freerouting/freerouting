package app.freerouting.board;

import app.freerouting.autoroute.ExpansionDrill;
import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

/**
 * Class describing the functionality of an electrical Item on the board, which may have a shape on
 * several layer, whose geometry is described by a padstack.
 */
public class Via extends DrillItem implements Serializable {

  /** True, if coppersharing of this via with smd pins of the same net is allowed. */
  public final boolean attachAllowed;

  /**
   * True if this is an escape via inserted by the escalation fanout phase to provide a layer
   * transition point from an SMD pin that could not be escaped on its primary layer. Escape vias
   * use SMD-to-SMD clearance rules on their SMD layer rather than normal conductor-to-conductor
   * clearance, because they sit inside the SMD pad copper footprint.
   */
  public boolean isEscapeVia;

  /**
   * The SMD layer (primary layer) on which this escape via connects to an SMD pin. Only relevant
   * when isEscapeVia is true. -1 if not an escape via.
   */
  public int escapeViaSmdLayer = -1;

  private Padstack padstack;
  private transient Shape[] precalculatedShapes;

  /** Temporary data used in the autoroute algorithm. */
  private transient ExpansionDrill autorouteDrillInfo;

  /** Creates a new instance of Via with the input parameters */
  public Via(
      Padstack p_padstack,
      Point p_center,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_group_no,
      FixedState p_fixed_state,
      boolean p_attach_allowed,
      BasicBoard p_board) {
    super(p_center, p_net_no_arr, p_clearance_type, p_id_no, p_group_no, p_fixed_state, p_board);
    this.padstack = p_padstack;
    this.attachAllowed = p_attach_allowed;
  }

  @Override
  public Item copy(int p_id_no) {
    Via copy =
        new Via(
            padstack,
            get_center(),
            netNoArr,
            clearance_class_no(),
            p_id_no,
            get_component_no(),
            get_fixed_state(),
            attachAllowed,
            board);
    copy.isEscapeVia = this.isEscapeVia;
    copy.escapeViaSmdLayer = this.escapeViaSmdLayer;
    return copy;
  }

  @Override
  public java.util.Collection<app.freerouting.drc.ClearanceViolation> clearance_violations() {
    java.util.Collection<app.freerouting.drc.ClearanceViolation> rawViolations =
        super.clearance_violations();
    if (!this.isEscapeVia || this.escapeViaSmdLayer < 0) {
      return rawViolations;
    }
    java.util.Collection<app.freerouting.drc.ClearanceViolation> filteredViolations =
        new java.util.LinkedList<>();
    for (app.freerouting.drc.ClearanceViolation violation : rawViolations) {
      if (violation.layer == this.escapeViaSmdLayer) {
        Item other = null;
        if (violation.firstItem == this) {
          other = violation.secondItem;
        } else if (violation.secondItem == this) {
          other = violation.firstItem;
        }
        if (other != null && other.shares_net(this)) {
          continue;
        }
      }
      filteredViolations.add(violation);
    }
    return filteredViolations;
  }

  @Override
  public Shape get_shape(int p_index) {
    if (padstack == null) {
      FRLogger.warn("Via.get_shape: padstack is null");
      return null;
    }
    if (this.precalculatedShapes == null) {
      this.precalculatedShapes = new Shape[padstack.to_layer() - padstack.from_layer() + 1];
      for (int i = 0; i < this.precalculatedShapes.length; i++) {
        int padstackLayer = i + this.first_layer();
        Vector translateVector = get_center().difference_by(Point.ZERO);
        Shape currShape = padstack.get_shape(padstackLayer);

        if (currShape == null) {
          this.precalculatedShapes[i] = null;
        } else {
          this.precalculatedShapes[i] = (Shape) currShape.translate_by(translateVector);
        }
      }
    }
    return this.precalculatedShapes[p_index];
  }

  @Override
  public Padstack get_padstack() {
    return padstack;
  }

  public void set_padstack(Padstack p_padstack) {
    padstack = p_padstack;
  }

  @Override
  public boolean is_routable() {
    return !is_user_fixed() && (this.net_count() > 0);
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    if (p_other == this || p_other instanceof ComponentObstacleArea) {
      return false;
    }
    if ((p_other instanceof ConductionArea area) && !area.get_is_obstacle()) {
      return false;
    }
    if (!p_other.shares_net(this)) {
      return true;
    }
    if (p_other instanceof Trace) {
      return false;
    }
    return !this.attachAllowed || !(p_other instanceof Pin) || !((Pin) p_other).drill_allowed();
  }

  /** Checks, if the Via has contacts on at most 1 layer. */
  @Override
  public boolean is_tail() {
    Collection<Item> contactList = this.get_normal_contacts();
    if (contactList.size() <= 1) {
      return true;
    }
    Iterator<Item> it = contactList.iterator();
    Item currContactItem = it.next();
    int firstContactFirstLayer = currContactItem.first_layer();
    int firstContactLastLayer = currContactItem.last_layer();
    while (it.hasNext()) {
      currContactItem = it.next();
      if (currContactItem.first_layer() != firstContactFirstLayer
          || currContactItem.last_layer() != firstContactLastLayer) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void change_placement_side(IntPoint p_pole) {
    if (this.board == null) {
      return;
    }
    Padstack newPadstack = this.board.library.get_mirrored_via_padstack(this.padstack);
    if (newPadstack == null) {
      return;
    }
    this.padstack = newPadstack;
    super.change_placement_side(p_pole);
    clear_derived_data();
  }

  public ExpansionDrill get_autoroute_drill_info(ShapeSearchTree p_autoroute_tree) {
    if (this.autorouteDrillInfo == null) {
      ItemAutorouteInfo viaAutorouteInfo = this.get_autoroute_info();
      TileShape currDrillShape = TileShape.get_instance(this.get_center());
      this.autorouteDrillInfo =
          new ExpansionDrill(
              currDrillShape, this.get_center(), this.first_layer(), this.last_layer());
      int viaLayerCount = this.last_layer() - this.first_layer() + 1;
      for (int i = 0; i < viaLayerCount; i++) {
        this.autorouteDrillInfo.roomArr[i] =
            viaAutorouteInfo.get_expansion_room(i, p_autoroute_tree);
      }
    }
    return this.autorouteDrillInfo;
  }

  @Override
  public void clear_derived_data() {
    super.clear_derived_data();
    this.precalculatedShapes = null;
    this.autorouteDrillInfo = null;
  }

  @Override
  public void clear_autoroute_info() {
    super.clear_autoroute_info();
    this.autorouteDrillInfo = null;
  }

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.VIAS);
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    Color[] result;
    if (this.net_count() == 0) {
      // display unconnected vias as obstacles
      result = p_graphics_context.get_obstacle_colors();
    } else {
      result = p_graphics_context.get_trace_colors(this.is_user_fixed());
    }
    return result;
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    double result;
    if (this.net_count() == 0) {
      // display unconnected vias as obstacles
      result = p_graphics_context.get_obstacle_color_intensity();

    } else if (this.first_layer() >= this.last_layer()) {
      // display vias with only one layer as pins
      result = p_graphics_context.get_pin_color_intensity();
    } else {
      result = p_graphics_context.get_via_color_intensity();
    }
    return result;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("via"));
    p_window.append(" " + tm.getText("at") + " ");
    p_window.append(this.get_center().to_float());
    p_window.append(", " + tm.getText("padstack"));
    p_window.append(padstack.name, tm.getText("padstack_info"), padstack);
    this.print_connectable_item_info(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public String get_hover_info(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    String fromLayer = this.board.layerStructure.arr[this.first_layer()].name;
    String toLayer = this.board.layerStructure.arr[this.last_layer()].name;
    String padstackName = padstack.name;
    String connInfo = this.get_connectable_item_hover_info(p_locale);

    return tm.getText("via_hover_info", padstackName, fromLayer, toLayer, connInfo);
  }

  @Override
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
