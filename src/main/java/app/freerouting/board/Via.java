package app.freerouting.board;

import app.freerouting.autoroute.ExpansionDrill;
import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
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

  /** Creates a new instance of Via with the input parameters. */
  public Via(
      Padstack padstack,
      Point center,
      int[] netNumbers,
      int clearanceClassIndex,
      int idNo,
      int groupNo,
      FixedState fixedState,
      boolean attachAllowed,
      BasicBoard board) {
    super(center, netNumbers, clearanceClassIndex, idNo, groupNo, fixedState, board);
    this.padstack = padstack;
    this.attachAllowed = attachAllowed;
  }

  @Override
  public Item copy(int idNo) {
    Via copy =
        new Via(
            padstack,
            getCenter(),
            netNumbers,
            clearanceClassIndex(),
            idNo,
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
  public Shape getShape(int index) {
    if (padstack == null) {
      FRLogger.warn("Via.get_shape: padstack is null");
      return null;
    }
    if (this.precalculatedShapes == null) {
      this.precalculatedShapes = new Shape[padstack.toLayer() - padstack.fromLayer() + 1];
      for (int i = 0; i < this.precalculatedShapes.length; i++) {
        int padstackLayer = i + this.firstLayer();
        Vector translateVector = getCenter().differenceBy(Point.ZERO);
        Shape currentShape = padstack.getShape(padstackLayer);

        if (currentShape == null) {
          this.precalculatedShapes[i] = null;
        } else {
          this.precalculatedShapes[i] = (Shape) currentShape.translateBy(translateVector);
        }
      }
    }
    return this.precalculatedShapes[index];
  }

  @Override
  public Padstack getPadstack() {
    return padstack;
  }

  public void setPadstack(Padstack padstack) {
    this.padstack = padstack;
  }

  @Override
  public boolean isRoutable() {
    return !isUserFixed() && (this.netCount() > 0);
  }

  @Override
  public boolean isObstacle(Item other) {
    if (other == this || other instanceof ComponentObstacleArea) {
      return false;
    }
    if ((other instanceof ConductionArea area) && !area.getIsObstacle()) {
      return false;
    }
    if (!other.sharesNet(this)) {
      return true;
    }
    if (other instanceof Trace) {
      return false;
    }
    return !this.attachAllowed || !(other instanceof Pin) || !((Pin) other).drillAllowed();
  }

  /** Checks, if the Via has contacts on at most 1 layer. */
  @Override
  public boolean isTail() {
    Collection<Item> contactList = this.getNormalContacts();
    if (contactList.size() <= 1) {
      return true;
    }
    Iterator<Item> it = contactList.iterator();
    Item currentContactItem = it.next();
    int firstContactFirstLayer = currentContactItem.firstLayer();
    int firstContactLastLayer = currentContactItem.lastLayer();
    while (it.hasNext()) {
      currentContactItem = it.next();
      if (currentContactItem.firstLayer() != firstContactFirstLayer
          || currentContactItem.lastLayer() != firstContactLastLayer) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    if (this.board == null) {
      return;
    }
    Padstack newPadstack = this.board.library.getMirroredViaPadstack(this.padstack);
    if (newPadstack == null) {
      return;
    }
    this.padstack = newPadstack;
    super.changePlacementSide(pole);
    clearDerivedData();
  }

  /** GetAutorouteDrillInfo. */
  public ExpansionDrill getAutorouteDrillInfo(ShapeSearchTree autorouteTree) {
    if (this.autorouteDrillInfo == null) {
      ItemAutorouteInfo viaAutorouteInfo = this.getAutorouteInfo();
      TileShape currentDrillShape = TileShape.getInstance(this.getCenter());
      this.autorouteDrillInfo =
          new ExpansionDrill(
              currentDrillShape, this.getCenter(), this.firstLayer(), this.lastLayer());
      int viaLayerCount = this.lastLayer() - this.firstLayer() + 1;
      for (int i = 0; i < viaLayerCount; i++) {
        this.autorouteDrillInfo.roomArr[i] = viaAutorouteInfo.getExpansionRoom(i, autorouteTree);
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
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.VIAS);
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("via"));
    window.append(" " + tm.getText("at") + " ");
    window.append(this.getCenter().toFloat());
    window.append(", " + tm.getText("padstack"));
    window.append(padstack.name, tm.getText("padstack_info"), padstack);
    this.printConnectableItemInfo(window, locale);
    window.newline();
  }

  @Override
  public String getHoverInfo(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    String fromLayer = this.board.layerStructure.layers[this.firstLayer()].name;
    String toLayer = this.board.layerStructure.layers[this.lastLayer()].name;
    String padstackName = padstack.name;
    String connInfo = this.getConnectableItemHoverInfo(locale);

    return tm.getText("via_hover_info", padstackName, fromLayer, toLayer, connInfo);
  }

  @Override
  public boolean write(ObjectOutputStream stream) {
    try {
      stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
