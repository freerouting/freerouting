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
      Padstack pPadstack,
      Point pCenter,
      int[] pNetNoArr,
      int pClearanceType,
      int pIdNo,
      int pGroupNo,
      FixedState pFixedState,
      boolean pAttachAllowed,
      BasicBoard pBoard) {
    super(pCenter, pNetNoArr, pClearanceType, pIdNo, pGroupNo, pFixedState, pBoard);
    this.padstack = pPadstack;
    this.attachAllowed = pAttachAllowed;
  }

  @Override
  public Item copy(int pIdNo) {
    Via copy =
        new Via(
            padstack,
            getCenter(),
            netNoArr,
            clearanceClassNo(),
            pIdNo,
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
  public Shape getShape(int pIndex) {
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
    return this.precalculatedShapes[pIndex];
  }

  @Override
  public Padstack getPadstack() {
    return padstack;
  }

  public void setPadstack(Padstack pPadstack) {
    padstack = pPadstack;
  }

  @Override
  public boolean isRoutable() {
    return !isUserFixed() && (this.netCount() > 0);
  }

  @Override
  public boolean isObstacle(Item pOther) {
    if (pOther == this || pOther instanceof ComponentObstacleArea) {
      return false;
    }
    if ((pOther instanceof ConductionArea area) && !area.getIsObstacle()) {
      return false;
    }
    if (!pOther.sharesNet(this)) {
      return true;
    }
    if (pOther instanceof Trace) {
      return false;
    }
    return !this.attachAllowed || !(pOther instanceof Pin) || !((Pin) pOther).drillAllowed();
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
  public void changePlacementSide(IntPoint pPole) {
    if (this.board == null) {
      return;
    }
    Padstack newPadstack = this.board.library.getMirroredViaPadstack(this.padstack);
    if (newPadstack == null) {
      return;
    }
    this.padstack = newPadstack;
    super.changePlacementSide(pPole);
    clearDerivedData();
  }

  public ExpansionDrill getAutorouteDrillInfo(ShapeSearchTree pAutorouteTree) {
    if (this.autorouteDrillInfo == null) {
      ItemAutorouteInfo viaAutorouteInfo = this.getAutorouteInfo();
      TileShape currDrillShape = TileShape.getInstance(this.getCenter());
      this.autorouteDrillInfo =
          new ExpansionDrill(currDrillShape, this.getCenter(), this.firstLayer(), this.lastLayer());
      int viaLayerCount = this.lastLayer() - this.firstLayer() + 1;
      for (int i = 0; i < viaLayerCount; i++) {
        this.autorouteDrillInfo.roomArr[i] = viaAutorouteInfo.getExpansionRoom(i, pAutorouteTree);
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
  public boolean isSelectedByFilter(ItemSelectionFilter pFilter) {
    if (!this.isSelectedByFixedFilter(pFilter)) {
      return false;
    }
    return pFilter.isSelected(ItemSelectionFilter.SelectableChoices.VIAS);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    Color[] result;
    if (this.netCount() == 0) {
      // display unconnected vias as obstacles
      result = pGraphicsContext.getObstacleColors();
    } else {
      result = pGraphicsContext.getTraceColors(this.isUserFixed());
    }
    return result;
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    double result;
    if (this.netCount() == 0) {
      // display unconnected vias as obstacles
      result = pGraphicsContext.getObstacleColorIntensity();

    } else if (this.firstLayer() >= this.lastLayer()) {
      // display vias with only one layer as pins
      result = pGraphicsContext.getPinColorIntensity();
    } else {
      result = pGraphicsContext.getViaColorIntensity();
    }
    return result;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("via"));
    pWindow.append(" " + tm.getText("at") + " ");
    pWindow.append(this.getCenter().toFloat());
    pWindow.append(", " + tm.getText("padstack"));
    pWindow.append(padstack.name, tm.getText("padstack_info"), padstack);
    this.printConnectableItemInfo(pWindow, pLocale);
    pWindow.newline();
  }

  @Override
  public String getHoverInfo(Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    String fromLayer = this.board.layerStructure.arr[this.firstLayer()].name;
    String toLayer = this.board.layerStructure.arr[this.lastLayer()].name;
    String padstackName = padstack.name;
    String connInfo = this.getConnectableItemHoverInfo(pLocale);

    return tm.getText("via_hover_info", padstackName, fromLayer, toLayer, connInfo);
  }

  @Override
  public boolean write(ObjectOutputStream pStream) {
    try {
      pStream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
