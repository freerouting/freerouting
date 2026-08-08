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
   * Creates a new instance of Polygon p_coor is an array of dimension 2 * point_count and contains
   * x0, y0, x1, y1, ... If the polygon is used as rectangle,
   */
  public Polygon(Layer pLayer, double[] pCoor) {
    super(pLayer);
    coor = pCoor;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform pCoordinateTransform) {
    IntPoint[] cornerArr = new IntPoint[coor.length / 2];
    double[] currPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currPoint[0] = coor[2 * i];
      currPoint[1] = coor[2 * i + 1];
      cornerArr[i] = pCoordinateTransform.dsnToBoard(currPoint).round();
    }
    return new PolygonShape(cornerArr);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform pCoordinateTransform) {
    if (coor.length < 2) {
      return Simplex.EMPTY;
    }
    IntPoint[] cornerArr = new IntPoint[coor.length / 2];
    for (int i = 0; i < cornerArr.length; i++) {
      int currX = (int) Math.round(pCoordinateTransform.dsnToBoard(coor[2 * i]));
      int currY = (int) Math.round(pCoordinateTransform.dsnToBoard(coor[2 * i + 1]));
      cornerArr[i] = new IntPoint(currX, currY);
    }
    return new PolygonShape(cornerArr);
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
  public void writeScope(IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.startScope();
    pFile.write("polygon ");
    pIdentifierType.write(this.layer.name, pFile);
    pFile.write(" ");
    pFile.write(String.valueOf(0));
    int cornerCount = coor.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      pFile.newLine();
      pFile.write(String.valueOf(coor[2 * i]));
      pFile.write(" ");
      pFile.write(String.valueOf(coor[2 * i + 1]));
    }
    pFile.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.startScope();
    pFile.write("polygon ");
    pIdentifierType.write(this.layer.name, pFile);
    pFile.write(" ");
    pFile.write(String.valueOf(0));
    int cornerCount = coor.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      pFile.newLine();
      int currCoor = (int) Math.round(coor[2 * i]);
      pFile.write(String.valueOf(currCoor));
      pFile.write(" ");
      currCoor = (int) Math.round(coor[2 * i + 1]);
      pFile.write(String.valueOf(currCoor));
    }
    pFile.endScope();
  }
}
