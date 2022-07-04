package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;

/** Describes a rectangle in a Specctra dsn file. */
public class Rectangle extends Shape {
  public final double[] coor;

  /**
   * Creates a new instance of Rectangle p_coor is an array of dimension 4 and contains the
   * rectangle coordinates in the following order: lower left x, lower left y, upper right x, uppper
   * right y.
   */
  public Rectangle(Layer p_layer, double[] p_coor) {
    super(p_layer);
    coor = p_coor;
  }

  public Rectangle bounding_box() {
    return this;
  }

  /** Creates the smallest rectangle containing this rectangle and p_other */
  public Rectangle union(Rectangle p_other) {
    double[] result_coor = new double[4];
    result_coor[0] = Math.min(this.coor[0], p_other.coor[0]);
    result_coor[1] = Math.min(this.coor[1], p_other.coor[1]);
    result_coor[2] = Math.max(this.coor[2], p_other.coor[2]);
    result_coor[3] = Math.max(this.coor[3], p_other.coor[3]);
    return new Rectangle(this.layer, result_coor);
  }

  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    int[] box_coor = new int[4];
    for (int i = 0; i < 4; ++i) {
      box_coor[i] = (int) Math.round(p_coordinate_transform.dsn_to_board(this.coor[i]));
    }

    IntBox result;
    if (box_coor[1] <= box_coor[3]) {
      // box_coor describe lower left and upper right corner
      result = new IntBox(box_coor[0], box_coor[1], box_coor[2], box_coor[3]);
    } else {
      // box_coor describe upper left and lower right corner
      result = new IntBox(box_coor[0], box_coor[3], box_coor[2], box_coor[1]);
    }
    return result;
  }

  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    double[] curr_point = new double[2];
    curr_point[0] = Math.min(coor[0], coor[2]);
    curr_point[1] = Math.min(coor[1], coor[3]);
    FloatPoint lower_left = p_coordinate_transform.dsn_to_board(curr_point);
    curr_point[0] = Math.max(coor[0], coor[2]);
    curr_point[1] = Math.max(coor[1], coor[3]);
    FloatPoint upper_right = p_coordinate_transform.dsn_to_board(curr_point);
    return new IntBox(lower_left.round(), upper_right.round());
  }

  /** Writes this rectangle as a scope to an output dsn-file. */
  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier)
      throws java.io.IOException {
    p_file.new_line();
    p_file.write("(rect ");
    p_identifier.write(this.layer.name, p_file);
    for (int i = 0; i < coor.length; ++i) {
      p_file.write(" ");
      p_file.write(Double.valueOf(coor[i]).toString());
    }
    p_file.write(")");
  }

  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier)
      throws java.io.IOException {
    p_file.new_line();
    p_file.write("(rect ");
    p_identifier.write(this.layer.name, p_file);
    for (int i = 0; i < coor.length; ++i) {
      p_file.write(" ");
      Integer curr_coor = (int) Math.round(coor[i]);
      p_file.write(curr_coor.toString());
    }
    p_file.write(")");
  }
}
