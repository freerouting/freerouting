package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;

/** Describes a rectangle in a Specctra dsn file. */
public class Rectangle extends Shape {

  public final double[] coor;

  /**
   * Creates a new instance of Rectangle p_coor is an array of dimension 4 and contains the
   * rectangle coordinates in the following order: lower left x, lower left y, upper right x, upper
   * right y.
   */
  public Rectangle(Layer p_layer, double[] p_coor) {
    super(p_layer);
    coor = p_coor;
  }

  @Override
  public Rectangle bounding_box() {
    return this;
  }

  /** Creates the smallest rectangle containing this rectangle and p_other */
  public Rectangle union(Rectangle p_other) {
    double[] resultCoor = new double[4];
    resultCoor[0] = Math.min(this.coor[0], p_other.coor[0]);
    resultCoor[1] = Math.min(this.coor[1], p_other.coor[1]);
    resultCoor[2] = Math.max(this.coor[2], p_other.coor[2]);
    resultCoor[3] = Math.max(this.coor[3], p_other.coor[3]);
    return new Rectangle(this.layer, resultCoor);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    int[] boxCoor = new int[4];
    for (int i = 0; i < 4; i++) {
      boxCoor[i] = (int) Math.round(p_coordinate_transform.dsn_to_board(this.coor[i]));
    }

    IntBox result;
    if (boxCoor[1] <= boxCoor[3]) {
      // boxCoor describe lower left and upper right corner
      result = new IntBox(boxCoor[0], boxCoor[1], boxCoor[2], boxCoor[3]);
    } else {
      // boxCoor describe upper left and lower right corner
      result = new IntBox(boxCoor[0], boxCoor[3], boxCoor[2], boxCoor[1]);
    }
    return result;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    double[] currPoint = new double[2];
    currPoint[0] = Math.min(coor[0], coor[2]);
    currPoint[1] = Math.min(coor[1], coor[3]);
    FloatPoint lowerLeft = p_coordinate_transform.dsn_to_board(currPoint);
    currPoint[0] = Math.max(coor[0], coor[2]);
    currPoint[1] = Math.max(coor[1], coor[3]);
    FloatPoint upperRight = p_coordinate_transform.dsn_to_board(currPoint);
    return new IntBox(lowerLeft.round(), upperRight.round());
  }

  /** Writes this rectangle as a scope to an output dsn-file. */
  @Override
  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier) throws IOException {
    p_file.new_line();
    p_file.write("(rect ");
    p_identifier.write(this.layer.name, p_file);
    for (int i = 0; i < coor.length; i++) {
      p_file.write(" ");
      p_file.write(String.valueOf(coor[i]));
    }
    p_file.write(")");
  }

  @Override
  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException {
    p_file.new_line();
    p_file.write("(rect ");
    p_identifier.write(this.layer.name, p_file);
    for (int i = 0; i < coor.length; i++) {
      p_file.write(" ");
      int currCoor = (int) Math.round(coor[i]);
      p_file.write(String.valueOf(currCoor));
    }
    p_file.write(")");
  }
}
