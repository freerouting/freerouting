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
            getCenter(),
            netNoArr,
            clearanceClassNo(),
            p_id_no,
            getComponentNo(),
            getFixedState(),
            attachAllowed,
            board);
    copy.isEscapeVia = this.isEscapeVia;
    copy.escapeViaSmdLayer = this.escapeViaSmdLayer;
    return copy;
  }

  @Override
  public java.util.Collection<app.freerouting.drc.ClearanceViolation> clearanceViolations() {
    java.util.Collection<app.freerouting.drc.ClearanceViolation> rawViolations =
        super.clearanceViolations();
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
        if (other != null && other.sharesNet(this)) {
          continue;
        }
      }
      filteredViolations.add(violation);
    }
    return filteredViolations;
  }

  @Override
  public Shape getShape(int p_index) {
    if (padstack == null) {
      FRLogger.warn("Via.get_shape: padstack is null");
      return null;
    }
    if (this.precalculatedShapes == null) {
      this.precalculatedShapes = new Shape[padstack.toLayer() - padstack.fromLayer() + 1];
      for (int i = 0; i < this.precalculatedShapes.length; i++) {
        int padstackLayer = i + this.firstLayer();
        Vector translateVector = getCenter().differenceBy(Point.ZERO);
        Shape currShape = padstack.getShape(padstackLayer);

        if (currShape == null) {
          this.precalculatedShapes[i] = null;
        } else {
          this.precalculatedShapes[i] = (Shape) currShape.translateBy(translateVector);
        }
      }
    }
    return this.precalculatedShapes[p_index];
  }

  @Override
  public Padstack getPadstack() {
    return padstack;
  }

  public void setPadstack(Padstack p_padstack) {
    padstack = p_padstack;
  }

  @Override
  public boolean isRoutable() {
    return !isUserFixed() && (this.netCount() > 0);
  }

  @Override
  public boolean isObstacle(Item p_other) {
    if (p_other == this || p_other instanceof ComponentObstacleArea) {
      return false;
    }
    if ((p_other instanceof ConductionArea area) && !area.getIsObstacle()) {
      return false;
    }
    if (!p_other.sharesNet(this)) {
      return true;
    }
    if (p_other instanceof Trace) {
      return false;
    }
    return !this.attachAllowed || !(p_other instanceof Pin) || !((Pin) p_other).drillAllowed();
  }

  /** Checks, if the Via has contacts on at most 1 layer. */
  @Override
  public boolean isTail() {
    Collection<Item> contactList = this.getNormalContacts();
    if (contactList.size() <= 1) {
      return true;
    }
    Iterator<Item> it = contactList.iterator();
    Item currContactItem = it.next();
    int firstContactFirstLayer = currContactItem.firstLayer();
    int firstContactLastLayer = currContactItem.lastLayer();
    while (it.hasNext()) {
      currContactItem = it.next();
      if (currContactItem.firstLayer() != firstContactFirstLayer
          || currContactItem.lastLayer() != firstContactLastLayer) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void changePlacementSide(IntPoint p_pole) {
    if (this.board == null) {
      return;
    }
    Padstack newPadstack = this.board.library.getMirroredViaPadstack(this.padstack);
    if (newPadstack == null) {
      return;
    }
    this.padstack = newPadstack;
    super.changePlacementSide(p_pole);
    clearDerivedData();
  }

  public ExpansionDrill getAutorouteDrillInfo(ShapeSearchTree p_autoroute_tree) {
    if (this.autorouteDrillInfo == null) {
      ItemAutorouteInfo viaAutorouteInfo = this.getAutorouteInfo();
      TileShape currDrillShape = TileShape.getInstance(this.getCenter());
      this.autorouteDrillInfo =
          new ExpansionDrill(
              currDrillShape, this.getCenter(), this.firstLayer(), this.lastLayer());
      int viaLayerCount = this.lastLayer() - this.firstLayer() + 1;
      for (int i = 0; i < viaLayerCount; i++) {
        this.autorouteDrillInfo.roomArr[i] =
            viaAutorouteInfo.getExpansionRoom(i, p_autoroute_tree);
      }
    }
    return this.autorouteDrillInfo;
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.precalculatedShapes = null;
    this.autorouteDrillInfo = null;
  }

  @Override
  public void clearAutorouteInfo() {
    super.clearAutorouteInfo();
    this.autorouteDrillInfo = null;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter p_filter) {
    if (!this.isSelectedByFixedFilter(p_filter)) {
      return false;
    }
    return p_filter.isSelected(ItemSelectionFilter.SelectableChoices.VIAS);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    Color[] result;
    if (this.netCount() == 0) {
      // display unconnected vias as obstacles
      result = p_graphics_context.getObstacleColors();
    } else {
      result = p_graphics_context.getTraceColors(this.isUserFixed());
    }
    return result;
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    double result;
    if (this.netCount() == 0) {
      // display unconnected vias as obstacles
      result = p_graphics_context.getObstacleColorIntensity();

    } else if (this.firstLayer() >= this.lastLayer()) {
      // display vias with only one layer as pins
      result = p_graphics_context.getPinColorIntensity();
    } else {
      result = p_graphics_context.getViaColorIntensity();
    }
    return result;
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("via"));
    p_window.append(" " + tm.getText("at") + " ");
    p_window.append(this.getCenter().toFloat());
    p_window.append(", " + tm.getText("padstack"));
    p_window.append(padstack.name, tm.getText("padstack_info"), padstack);
    this.printConnectableItemInfo(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public String getHoverInfo(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    String fromLayer = this.board.layerStructure.arr[this.firstLayer()].name;
    String toLayer = this.board.layerStructure.arr[this.lastLayer()].name;
    String padstackName = padstack.name;
    String connInfo = this.getConnectableItemHoverInfo(p_locale);

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
