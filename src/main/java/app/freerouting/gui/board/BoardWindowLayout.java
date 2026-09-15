package app.freerouting.gui.board;

import app.freerouting.gui.controls.ColorManager;
import app.freerouting.gui.windows.board.WindowAbout;
import app.freerouting.gui.windows.board.WindowComponents;
import app.freerouting.gui.windows.board.WindowDisplayMisc;
import app.freerouting.gui.windows.board.WindowIncompletes;
import app.freerouting.gui.windows.board.WindowNets;
import app.freerouting.gui.windows.board.WindowPackages;
import app.freerouting.gui.windows.board.WindowPadstacks;
import app.freerouting.gui.windows.board.WindowVisibility;
import app.freerouting.gui.windows.routing.WindowAssignNetClass;
import app.freerouting.gui.windows.routing.WindowAutorouteParameter;
import app.freerouting.gui.windows.routing.WindowClearanceMatrix;
import app.freerouting.gui.windows.routing.WindowClearanceViolations;
import app.freerouting.gui.windows.routing.WindowEditVias;
import app.freerouting.gui.windows.routing.WindowLengthViolations;
import app.freerouting.gui.windows.routing.WindowMoveParameter;
import app.freerouting.gui.windows.routing.WindowNetClasses;
import app.freerouting.gui.windows.routing.WindowRouteParameter;
import app.freerouting.gui.windows.routing.WindowRouteStubs;
import app.freerouting.gui.windows.routing.WindowSelectParameter;
import app.freerouting.gui.windows.routing.WindowUnconnectedRoute;
import app.freerouting.gui.windows.routing.WindowVia;
import java.util.Locale;
import javax.swing.SwingUtilities;

/**
 * Owns the lifecycle and placement of a {@link BoardFrame}'s permanent utility windows.
 *
 * <p>The frame remains the public façade and retains the window fields because binary GUI
 * serialization and existing callers depend on them. This collaborator isolates allocation,
 * positioning, refresh, repaint, and disposal policy from board loading and export actions.
 */
public class BoardWindowLayout {

  private final BoardFrame boardFrame;
  private final Locale locale;
  private final String freeroutingVersion;

  public BoardWindowLayout(BoardFrame boardFrame, Locale locale, String freeroutingVersion) {
    this.boardFrame = boardFrame;
    this.locale = locale;
    this.freeroutingVersion = freeroutingVersion;
  }

  public void allocatePermanentSubwindows() {
    allocateEssentialSubwindows();
    allocateRemainingSubwindows();
  }

  public void allocateEssentialSubwindows() {
    if (boardFrame.selectParameterWindow == null) {
      boardFrame.selectParameterWindow = new WindowSelectParameter(boardFrame);
      boardFrame.permanentSubwindows[6] = boardFrame.selectParameterWindow;
    }
  }

  public void allocateRemainingSubwindows() {
    if (boardFrame.colorManager != null) {
      return;
    }
    boardFrame.colorManager = new ColorManager(boardFrame);
    boardFrame.permanentSubwindows[0] = boardFrame.colorManager;
    boardFrame.visibilityWindow = new WindowVisibility(boardFrame);
    boardFrame.permanentSubwindows[1] = boardFrame.visibilityWindow;
    boardFrame.permanentSubwindows[2] = null;
    boardFrame.displayMiscWindow = new WindowDisplayMisc(boardFrame);
    boardFrame.permanentSubwindows[3] = boardFrame.displayMiscWindow;
    boardFrame.routeParameterWindow = new WindowRouteParameter(boardFrame);
    boardFrame.permanentSubwindows[5] = boardFrame.routeParameterWindow;
    boardFrame.clearanceMatrixWindow = new WindowClearanceMatrix(boardFrame);
    boardFrame.permanentSubwindows[7] = boardFrame.clearanceMatrixWindow;
    boardFrame.padstacksWindow = new WindowPadstacks(boardFrame);
    boardFrame.permanentSubwindows[8] = boardFrame.padstacksWindow;
    boardFrame.packagesWindow = new WindowPackages(boardFrame);
    boardFrame.permanentSubwindows[9] = boardFrame.packagesWindow;
    boardFrame.componentsWindow = new WindowComponents(boardFrame);
    boardFrame.permanentSubwindows[10] = boardFrame.componentsWindow;
    boardFrame.incompletesWindow = new WindowIncompletes(boardFrame);
    boardFrame.permanentSubwindows[11] = boardFrame.incompletesWindow;
    boardFrame.clearanceViolationsWindow = new WindowClearanceViolations(boardFrame);
    boardFrame.permanentSubwindows[12] = boardFrame.clearanceViolationsWindow;
    boardFrame.netInfoWindow = new WindowNets(boardFrame);
    boardFrame.permanentSubwindows[13] = boardFrame.netInfoWindow;
    boardFrame.viaWindow = new WindowVia(boardFrame);
    boardFrame.permanentSubwindows[14] = boardFrame.viaWindow;
    boardFrame.editViasWindow = new WindowEditVias(boardFrame);
    boardFrame.permanentSubwindows[15] = boardFrame.editViasWindow;
    boardFrame.editNetRulesWindow = new WindowNetClasses(boardFrame);
    boardFrame.permanentSubwindows[16] = boardFrame.editNetRulesWindow;
    boardFrame.assignNetClassesWindow = new WindowAssignNetClass(boardFrame);
    boardFrame.permanentSubwindows[17] = boardFrame.assignNetClassesWindow;
    boardFrame.lengthViolationsWindow = new WindowLengthViolations(boardFrame);
    boardFrame.permanentSubwindows[18] = boardFrame.lengthViolationsWindow;
    boardFrame.aboutWindow = new WindowAbout(locale, freeroutingVersion);
    boardFrame.permanentSubwindows[19] = boardFrame.aboutWindow;
    boardFrame.moveParameterWindow = new WindowMoveParameter(boardFrame);
    boardFrame.permanentSubwindows[20] = boardFrame.moveParameterWindow;
    boardFrame.unconnectedRouteWindow = new WindowUnconnectedRoute(boardFrame);
    boardFrame.permanentSubwindows[21] = boardFrame.unconnectedRouteWindow;
    boardFrame.routeStubsWindow = new WindowRouteStubs(boardFrame);
    boardFrame.permanentSubwindows[22] = boardFrame.routeStubsWindow;
    boardFrame.autorouteParameterWindow = new WindowAutorouteParameter(boardFrame);
    boardFrame.permanentSubwindows[23] = boardFrame.autorouteParameterWindow;
  }

  public void initializeWindows(boolean showEssentialImmediately) {
    allocateEssentialSubwindows();
    boardFrame.setLocation(120, 0);
    boardFrame.selectParameterWindow.setLocation(0, 0);

    if (showEssentialImmediately) {
      boardFrame.selectParameterWindow.setVisible(true);
      SwingUtilities.invokeLater(
          () -> {
            allocateRemainingSubwindows();
            positionRemainingSubwindows();
          });
    } else {
      SwingUtilities.invokeLater(
          () -> {
            boardFrame.selectParameterWindow.setVisible(true);
            allocateRemainingSubwindows();
            positionRemainingSubwindows();
          });
    }
  }

  public void initializeWindows() {
    initializeWindows(false);
  }

  public void positionRemainingSubwindows() {
    boardFrame.routeParameterWindow.setLocation(0, 100);
    boardFrame.autorouteParameterWindow.setLocation(0, 200);
    boardFrame.moveParameterWindow.setLocation(0, 50);
    boardFrame.clearanceMatrixWindow.setLocation(0, 150);
    boardFrame.viaWindow.setLocation(50, 150);
    boardFrame.editViasWindow.setLocation(100, 150);
    boardFrame.editNetRulesWindow.setLocation(100, 200);
    boardFrame.assignNetClassesWindow.setLocation(100, 250);
    boardFrame.padstacksWindow.setLocation(100, 30);
    boardFrame.packagesWindow.setLocation(200, 30);
    boardFrame.componentsWindow.setLocation(300, 30);
    boardFrame.incompletesWindow.setLocation(400, 30);
    boardFrame.clearanceViolationsWindow.setLocation(500, 30);
    boardFrame.lengthViolationsWindow.setLocation(550, 30);
    boardFrame.netInfoWindow.setLocation(350, 30);
    boardFrame.unconnectedRouteWindow.setLocation(650, 30);
    boardFrame.routeStubsWindow.setLocation(600, 30);
    boardFrame.visibilityWindow.setLocation(0, 450);
    boardFrame.displayMiscWindow.setLocation(0, 350);
    boardFrame.colorManager.setLocation(0, 600);
    boardFrame.aboutWindow.setLocation(200, 200);
  }

  public void disposePermanentSubwindows() {
    for (int i = 0; i < boardFrame.permanentSubwindows.length; i++) {
      if (boardFrame.permanentSubwindows[i] != null) {
        boardFrame.permanentSubwindows[i].dispose();
        boardFrame.permanentSubwindows[i] = null;
      }
    }
    boardFrame.selectParameterWindow = null;
    boardFrame.colorManager = null;
    boardFrame.visibilityWindow = null;
    boardFrame.displayMiscWindow = null;
    boardFrame.routeParameterWindow = null;
    boardFrame.autorouteParameterWindow = null;
    boardFrame.moveParameterWindow = null;
    boardFrame.clearanceMatrixWindow = null;
    boardFrame.viaWindow = null;
    boardFrame.editViasWindow = null;
    boardFrame.editNetRulesWindow = null;
    boardFrame.assignNetClassesWindow = null;
    boardFrame.padstacksWindow = null;
    boardFrame.packagesWindow = null;
    boardFrame.componentsWindow = null;
    boardFrame.incompletesWindow = null;
    boardFrame.clearanceViolationsWindow = null;
    boardFrame.lengthViolationsWindow = null;
    boardFrame.netInfoWindow = null;
    boardFrame.unconnectedRouteWindow = null;
    boardFrame.routeStubsWindow = null;
    boardFrame.aboutWindow = null;
  }

  public void refreshWindows() {
    for (BoardSavableSubWindow subwindow : boardFrame.permanentSubwindows) {
      if (subwindow != null) {
        subwindow.refresh();
      }
    }
  }

  public void repaintAll() {
    boardFrame.repaint();
    for (BoardSavableSubWindow subwindow : boardFrame.permanentSubwindows) {
      if (subwindow != null) {
        subwindow.repaint();
      }
    }
  }

  public BoardSavableSubWindow[] getPermanentSubwindows() {
    return boardFrame.permanentSubwindows;
  }
}
