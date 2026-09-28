package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;

/** Describes a polygon in a Specctra dsn file. */
public class Polygon extends Shape {

  public final double[] coor;

  /**
   * Creates a new instance of Polygon coor is an array of dimension 2 * point_count and contains
   * x0, y0, x1, y1, ... If the polygon is used as rectangle,
   */
  public Polygon(Layer layer, double[] coor) {
    super(layer);
    this.coor = coor;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform coordinateTransform) {
    IntPoint[] corners = new IntPoint[coor.length / 2];
    double[] currentPoint = new double[2];
    for (int i = 0; i < corners.length; i++) {
      currentPoint[0] = coor[2 * i];
      currentPoint[1] = coor[2 * i + 1];
      corners[i] = coordinateTransform.dsnToBoard(currentPoint).round();
    }
    return new PolygonShape(corners);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform coordinateTransform) {
    if (coor.length < 2) {
      return Simplex.EMPTY;
    }
    IntPoint[] corners = new IntPoint[coor.length / 2];
    for (int i = 0; i < corners.length; i++) {
      int currentX = (int) Math.round(coordinateTransform.dsnToBoard(coor[2 * i]));
      int currentY = (int) Math.round(coordinateTransform.dsnToBoard(coor[2 * i + 1]));
      corners[i] = new IntPoint(currentX, currentY);
    }
    return new PolygonShape(corners);
  }

  @Override
  public Rectangle boundingBox() {
    double[] bounds = new double[4];
    bounds[0] = Integer.MAX_VALUE;
    bounds[1] = Integer.MAX_VALUE;
    bounds[2] = Integer.MIN_VALUE;
    bounds[3] = Integer.MIN_VALUE;
    for (int i = 0; i < coor.length; i++) {
      if (i % 2 == 0) {
        // x coordinate
        bounds[0] = Math.min(bounds[0], coor[i]);
        bounds[2] = Math.max(bounds[2], coor[i]);
      } else {
        // x coordinate
        bounds[1] = Math.min(bounds[1], coor[i]);
        bounds[3] = Math.max(bounds[3], coor[i]);
      }
    }
    return new Rectangle(layer, bounds);
  }

  /** Writes this polygon as a scope to an output dsn-file. */
  @Override
  public void writeScope(IndentFileWriter file, IdentifierType identifierType) throws IOException {
    file.startScope();
    file.write("polygon ");
    identifierType.write(this.layer.name, file);
    file.write(" ");
    file.write(String.valueOf(0));
    int cornerCount = coor.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      file.newLine();
      file.write(String.valueOf(coor[2 * i]));
      file.write(" ");
      file.write(String.valueOf(coor[2 * i + 1]));
    }
    file.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.startScope();
    file.write("polygon ");
    identifierType.write(this.layer.name, file);
    file.write(" ");
    file.write(String.valueOf(0));
    int cornerCount = coor.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      file.newLine();
      int currentCoor = (int) Math.round(coor[2 * i]);
      file.write(String.valueOf(currentCoor));
      file.write(" ");
      currentCoor = (int) Math.round(coor[2 * i + 1]);
      file.write(String.valueOf(currentCoor));
    }
    file.endScope();
  }
}
