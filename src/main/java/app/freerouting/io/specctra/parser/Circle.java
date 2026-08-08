package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;

/** Class for reading and writing circle scopes from dsn-files. */
public class Circle extends Shape {

  public final double[] coor;

  /**
   * Creates a new circle from the input parameters. p_coor is an array of dimension 3. p_coor[0] is
   * the radius of the circle, p_coor[1] is the x coordinate of the circle, p_coor[2] is the y
   * coordinate of the circle.
   */
  public Circle(Layer pLayer, double[] pCoor) {
    super(pLayer);
    coor = pCoor;
  }

  public Circle(Layer pLayer, double pRadius, double pCenterX, double pCenterY) {
    super(pLayer);
    coor = new double[3];
    coor[0] = pRadius;
    coor[1] = pCenterX;
    coor[2] = pCenterY;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform pCoordinateTransform) {
    double[] location = new double[2];
    location[0] = coor[1];
    location[1] = coor[2];
    IntPoint center = pCoordinateTransform.dsnToBoard(location).round();
    int radius = (int) Math.round(pCoordinateTransform.dsnToBoard(coor[0]) / 2);
    return new app.freerouting.geometry.planar.Circle(center, radius);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform pCoordinateTransform) {
    int[] newCoor = new int[3];
    newCoor[0] = (int) Math.round(pCoordinateTransform.dsnToBoard(coor[0]) / 2);
    for (int i = 1; i < 3; i++) {
      newCoor[i] = (int) Math.round(pCoordinateTransform.dsnToBoard(coor[i]));
    }
    return new app.freerouting.geometry.planar.Circle(
        new IntPoint(newCoor[1], newCoor[2]), newCoor[0]);
  }

  @Override
  public Rectangle boundingBox() {
    double[] bounds = new double[4];
    bounds[0] = coor[1] - coor[0];
    bounds[1] = coor[2] - coor[0];
    bounds[2] = coor[1] + coor[0];
    bounds[3] = coor[2] + coor[0];
    return new Rectangle(layer, bounds);
  }

  @Override
  public void writeScope(IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.newLine();
    pFile.write("(circle ");
    pIdentifierType.write(this.layer.name, pFile);
    for (int i = 0; i < coor.length; i++) {
      pFile.write(" ");
      pFile.write(String.valueOf(coor[i]));
    }
    pFile.write(")");
  }

  @Override
  public void writeScopeInt(IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.newLine();
    pFile.write("(circle ");
    pIdentifierType.write(this.layer.name, pFile);
    for (int i = 0; i < coor.length; i++) {
      pFile.write(" ");
      int currCoor = (int) Math.round(coor[i]);
      pFile.write(String.valueOf(currCoor));
    }
    pFile.write(")");
  }
}
