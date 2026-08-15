package app.freerouting.io.specctra.parser;

import app.freerouting.board.ConductionArea;
import app.freerouting.geometry.planar.Area;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading and writing plane scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Plane extends ScopeKeyword {

  /** Creates a new instance of Plane. */
  public Plane() {
    super("plane");
  }

  public static void writeScope(WriteScopeParameter scopeParameter, ConductionArea conduction)
      throws IOException {
    int netCount = conduction.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count at '" + conduction.name + "'");
      return;
    }
    final String netName = scopeParameter.board.rules.nets.get(conduction.getNetNumber(0)).name;
    Area currentArea = conduction.getArea();
    int layerIndex = conduction.getLayer();
    app.freerouting.board.Layer boardLayer = scopeParameter.board.layerStructure.arr[layerIndex];
    final Layer planeLayer = new Layer(boardLayer.name, layerIndex, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currentArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currentArea.getBorder();
      holes = currentArea.getHoles();
    }
    scopeParameter.file.startScope();
    scopeParameter.file.write("plane ");
    scopeParameter.identifierType.write(netName, scopeParameter.file);
    Shape dsnShape = scopeParameter.coordinateTransform.boardToDsn(boundaryShape, planeLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(scopeParameter.file, scopeParameter.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = scopeParameter.coordinateTransform.boardToDsn(holes[i], planeLayer);
      dsnHole.writeHoleScope(scopeParameter.file, scopeParameter.identifierType);
    }
    scopeParameter.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter scopeParameter) {
    // read the net name
    String netName;
    boolean skipWindowScopes = "allegro".equalsIgnoreCase(scopeParameter.hostCad);
    // Cadence Allegro cutouts the pins on power planes, which leads to performance problems
    // when dividing a conduction area into convex pieces.
    Shape.ReadAreaScopeResult conductionArea;
    try {
      Object nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Plane.read_scope: String expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      netName = (String) nextToken;
      scopeParameter.scanner.setScopeIdentifier(netName);
      conductionArea =
          Shape.readAreaScope(
              scopeParameter.scanner, scopeParameter.layerStructure, skipWindowScopes);
    } catch (IOException e) {
      FRLogger.error("Plane.read_scope: IO error scanning file", e);
      return false;
    }
    ReadScopeParameter.PlaneInfo planeInfo =
        new ReadScopeParameter.PlaneInfo(conductionArea, netName);
    scopeParameter.planeList.add(planeInfo);
    return true;
  }
}
