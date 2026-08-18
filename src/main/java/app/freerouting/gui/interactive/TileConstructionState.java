package app.freerouting.gui.interactive;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.rules.BoardRules;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for interactive construction of a tile-shaped obstacle. */
public final class TileConstructionState extends CornerItemConstructionState {

  /** Creates a new instance of TileConstructionState. */
  private TileConstructionState(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    this.addCorner(location);
  }

  /**
   * Returns a new instance of this class. The creation of this item is stored in a logfile when
   * logging is enabled.
   */
  public static TileConstructionState getInstance(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    return new TileConstructionState(location, parentState, boardHandling);
  }

  /** Adds a corner to the tile under construction. */
  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    super.leftButtonClicked(location);
    removeConcaveCorners();
    hdlg.repaint();
    return this;
  }

  @Override
  public InteractiveState complete() {
    removeConcaveCornersAtClose();
    int cornerCount = cornerList.size();
    boolean constructionSucceeded = cornerCount > 2;
    if (constructionSucceeded) {
      // create the edgelines of the new tile
      Line[] edgeLines = new Line[cornerCount];
      Iterator<IntPoint> it = cornerList.iterator();
      IntPoint firstCorner = it.next();
      IntPoint prevCorner = firstCorner;
      for (int i = 0; i < cornerCount - 1; i++) {
        IntPoint nextCorner = it.next();
        edgeLines[i] = new Line(prevCorner, nextCorner);
        prevCorner = nextCorner;
      }
      edgeLines[cornerCount - 1] = new Line(prevCorner, firstCorner);
      TileShape obstacleShape = TileShape.getInstance(edgeLines);
      RoutingBoard board = hdlg.getRoutingBoard();
      int layer = hdlg.getWorkspaceSettings().getLayer();
      int clearanceClassIndex = BoardRules.clearanceClassNone();

      constructionSucceeded =
          board.checkShape(obstacleShape, layer, new int[0], clearanceClassIndex);
      if (constructionSucceeded) {
        // insert the new shape as keepout
        this.observersActivated = !hdlg.getRoutingBoard().observersActive();
        if (this.observersActivated) {
          hdlg.getRoutingBoard().startNotifyObservers();
        }
        board.generateSnapshot();
        board.insertObstacle(obstacleShape, layer, clearanceClassIndex, FixedState.UNFIXED);
        if (this.observersActivated) {
          hdlg.getRoutingBoard().endNotifyObservers();
          this.observersActivated = false;
        }
      }
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.setStatusMessage(tm.getText("keepout_successful_completed"));
    } else {
      hdlg.screenMessages.setStatusMessage(tm.getText("keepout_cancelled_because_of_overlaps"));
    }
    return this.returnState;
  }

  /** Skips concave corners at the end of the corner list. */
  private void removeConcaveCorners() {
    IntPoint[] corners = new IntPoint[cornerList.size()];
    Iterator<IntPoint> it = cornerList.iterator();
    for (int i = 0; i < corners.length; i++) {
      corners[i] = it.next();
    }

    int newLength = corners.length;
    if (newLength < 3) {
      return;
    }
    IntPoint lastCorner = corners[newLength - 1];
    IntPoint currentCorner = corners[newLength - 2];
    while (newLength > 2) {
      IntPoint prevCorner = corners[newLength - 3];
      Side lastCornerSide = lastCorner.sideOf(prevCorner, currentCorner);
      if (lastCornerSide == Side.ON_THE_LEFT) {
        // side is ok, nothing to skip
        break;
      }
      if (this.hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
          != AngleRestriction.FORTYFIVE_DEGREE) {
        // skip concave corner
        corners[newLength - 2] = lastCorner;
      }
      --newLength;
      // In 45 degree case just skip last corner as nothing like the following
      // calculation for the 90 degree case to keep
      // the angle restrictions is implemented.
      if (this.hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
          == AngleRestriction.NINETY_DEGREE) {
        // prevent generating a non orthogonal line by changing the previous corner
        IntPoint prevPrevCorner = null;
        if (newLength >= 3) {
          prevPrevCorner = corners[newLength - 3];
        }
        if (prevPrevCorner != null && prevPrevCorner.x == prevCorner.x) {
          corners[newLength - 2] = new IntPoint(prevCorner.x, lastCorner.y);
        } else {
          corners[newLength - 2] = new IntPoint(lastCorner.x, prevCorner.y);
        }
      }
      currentCorner = prevCorner;
    }
    if (newLength < corners.length) {
      // something skipped, update cornerList
      cornerList = new LinkedList<>(Arrays.asList(corners).subList(0, newLength));
    }
  }

  /**
   * Removes corners from the end of the corner list so closing the polygon will not create a
   * concave corner.
   */
  private void removeConcaveCornersAtClose() {
    addCornerForSnapAngle();
    if (cornerList.size() < 4) {
      return;
    }
    IntPoint[] corners = new IntPoint[cornerList.size()];
    Iterator<IntPoint> it = cornerList.iterator();
    for (int i = 0; i < corners.length; i++) {
      corners[i] = it.next();
    }
    int newLength = corners.length;

    IntPoint firstCorner = corners[0];
    IntPoint secondCorner = corners[1];
    while (newLength > 3) {
      IntPoint lastCorner = corners[newLength - 1];
      if (lastCorner.sideOf(secondCorner, firstCorner) != Side.ON_THE_LEFT) {
        break;
      }
      --newLength;
    }

    if (newLength != corners.length) {
      // recalculate the cornerList
      cornerList = new LinkedList<>(Arrays.asList(corners).subList(0, newLength));
      addCornerForSnapAngle();
    }
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("creating_tile"));
  }
}
