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

  public static void writeScope(WriteScopeParameter p_par, ConductionArea p_conduction)
      throws IOException {
    int netCount = p_conduction.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count at '" + p_conduction.name + "'");
      return;
    }
    String netName = p_par.board.rules.nets.get(p_conduction.getNetNo(0)).name;
    Area currArea = p_conduction.getArea();
    int layerNo = p_conduction.getLayer();
    app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[layerNo];
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
    p_par.file.startScope();
    p_par.file.write("plane ");
    p_par.identifierType.write(netName, p_par.file);
    Shape dsnShape = p_par.coordinateTransform.boardToDsn(boundaryShape, planeLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(p_par.file, p_par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = p_par.coordinateTransform.boardToDsn(holes[i], planeLayer);
      dsnHole.writeHoleScope(p_par.file, p_par.identifierType);
    }
    p_par.file.endScope();
  }

  @Override
  public boolean readScope(ReadScopeParameter p_par) {
    // read the net name
    String netName;
    boolean skipWindowScopes = "allegro".equalsIgnoreCase(p_par.hostCad);
    // Cadence Allegro cutouts the pins on power planes, which leads to performance problems
    // when dividing a conduction area into convex pieces.
    Shape.ReadAreaScopeResult conductionArea;
    try {
      Object nextToken = p_par.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Plane.read_scope: String expected at '" + p_par.scanner.getScopeIdentifier() + "'");
        return false;
      }
      netName = (String) nextToken;
      p_par.scanner.setScopeIdentifier(netName);
      conductionArea = Shape.readAreaScope(p_par.scanner, p_par.layerStructure, skipWindowScopes);
    } catch (IOException e) {
      FRLogger.error("Plane.read_scope: IO error scanning file", e);
      return false;
    }
    ReadScopeParameter.PlaneInfo planeInfo =
        new ReadScopeParameter.PlaneInfo(conductionArea, netName);
    p_par.planeList.add(planeInfo);
    return true;
  }
}
