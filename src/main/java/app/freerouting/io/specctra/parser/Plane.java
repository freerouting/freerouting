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

  public static void writeScope(WriteScopeParameter par, ConductionArea conduction)
      throws IOException {
    int netCount = conduction.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count at '" + conduction.name + "'");
      return;
    }
    final String netName = par.board.rules.nets.get(conduction.getNetNo(0)).name;
    Area currArea = conduction.getArea();
    int layerNo = conduction.getLayer();
    app.freerouting.board.Layer boardLayer = par.board.layerStructure.arr[layerNo];
    final Layer planeLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currArea.getBorder();
      holes = currArea.getHoles();
    }
    par.file.startScope();
    par.file.write("plane ");
    par.identifierType.write(netName, par.file);
    Shape dsnShape = par.coordinateTransform.boardToDsn(boundaryShape, planeLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(par.file, par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = par.coordinateTransform.boardToDsn(holes[i], planeLayer);
      dsnHole.writeHoleScope(par.file, par.identifierType);
    }
    par.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter par) {
    // read the net name
    String netName;
    boolean skipWindowScopes = "allegro".equalsIgnoreCase(par.hostCad);
    // Cadence Allegro cutouts the pins on power planes, which leads to performance problems
    // when dividing a conduction area into convex pieces.
    Shape.ReadAreaScopeResult conductionArea;
    try {
      Object nextToken = par.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Plane.read_scope: String expected at '" + par.scanner.getScopeIdentifier() + "'");
        return false;
      }
      netName = (String) nextToken;
      par.scanner.setScopeIdentifier(netName);
      conductionArea = Shape.readAreaScope(par.scanner, par.layerStructure, skipWindowScopes);
    } catch (IOException e) {
      FRLogger.error("Plane.read_scope: IO error scanning file", e);
      return false;
    }
    ReadScopeParameter.PlaneInfo planeInfo =
        new ReadScopeParameter.PlaneInfo(conductionArea, netName);
    par.planeList.add(planeInfo);
    return true;
  }
}
