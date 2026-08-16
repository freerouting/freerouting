package app.freerouting.gui.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Via;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.rules.Net;
import java.awt.Color;
import java.util.Set;

/**
 * Class implementing the different functionality in the inspect menu, especially the different
 * behaviour of the mouse button 1.
 */
public final class InspectMenuState extends MenuState {

  private Item lastHoveredItem;
  private ClearanceViolation lastHoveredViolation;
  private String backupMessage;

  /** Creates a new instance of InspectMenuState. */
  private InspectMenuState(GuiBoardManager boardHandling) {
    super(boardHandling);
  }

  /** Returns a new instance of InspectMenuState. */
  public static InspectMenuState getInstance(GuiBoardManager boardHandling) {
    return new InspectMenuState(boardHandling);
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return selectItems(location);
  }

  @Override
  public InteractiveState mouseDragged(FloatPoint point) {
    return InspectItemsInRegionState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();

    FloatPoint currentPosition = hdlg.getCurrentMousePosition();
    if (currentPosition == null) {
      return this;
    }

    // Check for clearance violations first (higher priority)
    ClearanceViolation currentViolation = null;
    if (hdlg.clearanceViolations != null && !hdlg.clearanceViolations.list.isEmpty()) {
      for (ClearanceViolation violation : hdlg.clearanceViolations.list) {
        if (violation.shape.contains(currentPosition)) {
          currentViolation = violation;
          break;
        }
      }
    }

    // If we found a violation, handle it
    if (currentViolation != null) {
      if (currentViolation != lastHoveredViolation) {
        // Backup message if entering from no violation
        if (lastHoveredViolation == null && lastHoveredItem == null) {
          backupMessage = tm.getText("in_inspect_menu");
        }

        // Clear previous item/violation
        lastHoveredItem = null;
        lastHoveredViolation = currentViolation;
        displayViolationInfo(currentViolation);
        hdlg.repaint();
      }
      return this;
    } else if (lastHoveredViolation != null) {
      // We left a violation
      lastHoveredViolation = null;
      if (backupMessage != null) {
        hdlg.screenMessages.setStatusMessage(backupMessage);
        backupMessage = null;
      }
      hdlg.repaint();
    }

    // If no violation, check for items
    Set<Item> pickedItems = hdlg.pickItems(currentPosition);

    // Find the first relevant item (DrillItem or Trace)
    Item currentItem = null;
    for (Item item : pickedItems) {
      if (item instanceof DrillItem || item instanceof PolylineTrace) {
        currentItem = item;
        break;
      }
    }

    // Handle item change
    if (currentItem != lastHoveredItem) {
      // Restore backup message if we left the previous item
      if (lastHoveredItem != null && currentItem == null && backupMessage != null) {
        hdlg.screenMessages.setStatusMessage(backupMessage);
        backupMessage = null;
      }

      // Clear highlight on previous item
      if (lastHoveredItem != null) {
        lastHoveredItem = null;
        hdlg.repaint();
      }

      // Set new item
      if (currentItem != null) {
        // Backup current message if we're entering a new item from no item
        if (lastHoveredItem == null) {
          backupMessage = tm.getText("in_inspect_menu");
        }

        lastHoveredItem = currentItem;
        displayItemInfo(currentItem);
        hdlg.repaint();
      }
    }

    return this;
  }

  private void displayItemInfo(Item item) {
    StringBuilder info = new StringBuilder();

    if (item instanceof Pin) {
      Pin pin = (Pin) item;
      info.append("Pin: ");
      if (pin.getComponentId() > 0) {
        info.append(hdlg.getRoutingBoard().components.get(pin.getComponentId()).name);
        info.append(" - ");
      }
      info.append("Padstack: ").append(pin.getPadstack().name);
      appendNetInfo(info, item);
    } else if (item instanceof Via) {
      Via via = (Via) item;
      info.append("Via: ");
      info.append("Padstack: ").append(via.getPadstack().name);
      info.append(" (L").append(via.getPadstack().fromLayer());
      info.append("-L").append(via.getPadstack().toLayer()).append(")");
      appendNetInfo(info, item);
    } else if (item instanceof PolylineTrace) {
      PolylineTrace trace = (PolylineTrace) item;
      info.append("Trace: ");
      info.append("ID ").append(trace.getId());
      info.append(", Layer: ")
          .append(hdlg.getRoutingBoard().layerStructure.layers[trace.getLayer()].name);
      info.append(", Width: ").append(2 * trace.getHalfWidth());

      // Add segment count
      int segmentCount = trace.cornerCount() - 1;
      info.append(", Segments: ").append(segmentCount);

      // Add total length
      double length = trace.getLength();
      info.append(", Length: ").append(String.format("%.2f", length));

      appendNetInfo(info, item);
    }

    hdlg.screenMessages.setStatusMessage(info.toString());
  }

  private void displayViolationInfo(ClearanceViolation violation) {
    StringBuilder info = new StringBuilder();

    info.append("CLEARANCE VIOLATION");

    // Add layer information
    String layerName = hdlg.getRoutingBoard().layerStructure.layers[violation.layer].name;
    info.append(" | Layer: ").append(layerName);

    // Add clearance information - convert from board units to display units
    double expectedMm = violation.expectedClearance / 10000.0;
    double actualMm = violation.actualClearance / 10000.0;
    double violationMm = expectedMm - actualMm;

    info.append(String.format(" | Required: %.4f mm", expectedMm));
    info.append(String.format(", Actual: %.4f mm", actualMm));
    info.append(String.format(", Violation: %.4f mm", violationMm));

    // Add clearance class information
    String clearanceClass1 =
        hdlg.getRoutingBoard()
            .rules
            .clearanceMatrix
            .getName(violation.firstItem.clearanceClassIndex());
    String clearanceClass2 =
        hdlg.getRoutingBoard()
            .rules
            .clearanceMatrix
            .getName(violation.secondItem.clearanceClassIndex());

    info.append(" | Classes: ").append(clearanceClass1).append(" <-> ").append(clearanceClass2);

    // Add item information
    info.append(" | Between: ");
    info.append(getItemDescription(violation.firstItem));
    info.append(" and ");
    info.append(getItemDescription(violation.secondItem));

    hdlg.screenMessages.setStatusMessage(info.toString());
  }

  private String getItemDescription(Item item) {
    if (item instanceof Pin) {
      Pin pin = (Pin) item;
      if (pin.getComponentId() > 0) {
        return "Pin(" + hdlg.getRoutingBoard().components.get(pin.getComponentId()).name + ")";
      }
      return "Pin";
    } else if (item instanceof Via) {
      return "Via";
    } else if (item instanceof PolylineTrace) {
      return "Trace(ID:" + item.getId() + ")";
    } else {
      return item.getClass().getSimpleName();
    }
  }

  private void appendNetInfo(StringBuilder info, Item item) {
    if (item.netCount() > 0) {
      info.append(" | Net: ");
      for (int i = 0; i < item.netCount(); i++) {
        if (i > 0) {
          info.append(", ");
        }
        Net net = hdlg.getRoutingBoard().rules.nets.get(item.getNetNumber(i));
        info.append(net.name);
      }
    }
  }

  @Override
  public void draw(java.awt.Graphics graphics) {
    // Draw the hovered clearance violation with highlight
    if (lastHoveredViolation != null && hdlg.graphicsContext != null) {
      Color violationColor = hdlg.graphicsContext.getViolationsColor();
      double intensity = hdlg.graphicsContext.getLayerVisibility(lastHoveredViolation.layer);

      // Draw the violation area with increased brightness
      hdlg.graphicsContext.fillArea(
          lastHoveredViolation.shape, graphics, violationColor, Math.min(1.0, intensity * 1.8));

      // Draw a prominent circle around the violation
      double drawRadius = hdlg.getRoutingBoard().rules.getMinTraceHalfWidth() * 8;
      hdlg.graphicsContext.drawCircle(
          lastHoveredViolation.shape.centreOfGravity(),
          drawRadius,
          0.15 * drawRadius,
          violationColor,
          graphics,
          Math.min(1.0, intensity * 1.5));
    }

    // Draw the hovered item with highlight
    if (lastHoveredItem != null && hdlg.graphicsContext != null) {
      BoardRenderer.drawHighlightedOverlayItem(lastHoveredItem, graphics, hdlg.graphicsContext);
    }
  }

  @Override
  public void displayDefaultMessage() {
    if (lastHoveredItem == null && lastHoveredViolation == null) {
      hdlg.screenMessages.setStatusMessage(tm.getText("in_inspect_menu"));
    }
  }

  @Override
  public String getHelpId() {
    return "MenuState_InspectMenuState";
  }

  /** Returns the item currently under the mouse pointer, if any. */
  public Item getLastHoveredItem() {
    return lastHoveredItem;
  }
}
