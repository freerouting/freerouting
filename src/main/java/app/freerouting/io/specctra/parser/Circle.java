package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;

/** Class for reading and writing circle scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Circle extends Shape {

  public final double[] coor;

  /**
   * Creates a new circle from the input parameters. p_coor is an array of dimension 3. p_coor[0] is
   * the radius of the circle, p_coor[1] is the x coordinate of the circle, p_coor[2] is the y
   * coordinate of the circle.
   */
  public Circle(Layer layer, double[] coor) {
    super(layer);
    this.coor = coor;
  }

  public Circle(Layer layer, double radius, double centerX, double centerY) {
    super(layer);
    coor = new double[3];
    coor[0] = radius;
    coor[1] = centerX;
    coor[2] = centerY;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform coordinateTransform) {
    double[] location = new double[2];
    location[0] = coor[1];
    location[1] = coor[2];
    IntPoint center = coordinateTransform.dsnToBoard(location).round();
    int radius = (int) Math.round(coordinateTransform.dsnToBoard(coor[0]) / 2);
    return new app.freerouting.geometry.planar.Circle(center, radius);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform coordinateTransform) {
    int[] newCoor = new int[3];
    newCoor[0] = (int) Math.round(coordinateTransform.dsnToBoard(coor[0]) / 2);
    for (int i = 1; i < 3; i++) {
      newCoor[i] = (int) Math.round(coordinateTransform.dsnToBoard(coor[i]));
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
  public void writeScope(IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.newLine();
    file.write("(circle ");
    identifierType.write(this.layer.name, file);
    for (int i = 0; i < coor.length; i++) {
      file.write(" ");
      file.write(String.valueOf(coor[i]));
    }
    file.write(")");
  }

  @Override
  public void writeScopeInt(IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.newLine();
    file.write("(circle ");
    identifierType.write(this.layer.name, file);
    for (int i = 0; i < coor.length; i++) {
      file.write(" ");
      int currCoor = (int) Math.round(coor[i]);
      file.write(String.valueOf(currCoor));
    }
    file.write(")");
  }
}
