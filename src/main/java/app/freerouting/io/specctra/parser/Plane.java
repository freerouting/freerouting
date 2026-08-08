package app.freerouting.io.specctra.parser;

import app.freerouting.board.ConductionArea;
import app.freerouting.geometry.planar.Area;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading and writing plane scopes from dsn-files. */
public class Plane extends ScopeKeyword {

  /** Creates a new instance of Plane */
  public Plane() {
    super("plane");
  }

  public static void writeScope(WriteScopeParameter pPar, ConductionArea pConduction)
      throws IOException {
    int netCount = pConduction.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count at '" + pConduction.name + "'");
      return;
    }
    String netName = pPar.board.rules.nets.get(pConduction.getNetNo(0)).name;
    Area currArea = pConduction.getArea();
    int layerNo = pConduction.getLayer();
    app.freerouting.board.Layer boardLayer = pPar.board.layerStructure.arr[layerNo];
    Layer planeLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currArea.getBorder();
      holes = currArea.getHoles();
    }
    pPar.file.startScope();
    pPar.file.write("plane ");
    pPar.identifierType.write(netName, pPar.file);
    Shape dsnShape = pPar.coordinateTransform.boardToDsn(boundaryShape, planeLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(pPar.file, pPar.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = pPar.coordinateTransform.boardToDsn(holes[i], planeLayer);
      dsnHole.writeHoleScope(pPar.file, pPar.identifierType);
    }
    pPar.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter pPar) {
    // read the net name
    String netName;
    boolean skipWindowScopes = "allegro".equalsIgnoreCase(pPar.hostCad);
    // Cadence Allegro cutouts the pins on power planes, which leads to performance problems
    // when dividing a conduction area into convex pieces.
    Shape.ReadAreaScopeResult conductionArea;
    try {
      Object nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Plane.read_scope: String expected at '" + pPar.scanner.getScopeIdentifier() + "'");
        return false;
      }
      netName = (String) nextToken;
      pPar.scanner.setScopeIdentifier(netName);
      conductionArea = Shape.readAreaScope(pPar.scanner, pPar.layerStructure, skipWindowScopes);
    } catch (IOException e) {
      FRLogger.error("Plane.read_scope: IO error scanning file", e);
      return false;
    }
    ReadScopeParameter.PlaneInfo planeInfo =
        new ReadScopeParameter.PlaneInfo(conductionArea, netName);
    pPar.planeList.add(planeInfo);
    return true;
  }
}
