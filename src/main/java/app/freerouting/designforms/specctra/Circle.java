package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;

/** Class for reading and writing circle scopes from dsn-files. */
public class Circle extends Shape {
  public final double[] coor;

  /**
   * Creates a new circle from the input parameters. p_coor is an array of dimension 3. p_coor[0] is
   * the radius of the circle, p_coor[1] is the x coordinate of the circle, p_coor[2] is the y
   * coordinate of the circle.
   */
  public Circle(Layer p_layer, double[] p_coor) {
    super(p_layer);
    coor = p_coor;
  }

  public Circle(Layer p_layer, double p_radius, double p_center_x, double p_center_y) {
    super(p_layer);
    coor = new double[3];
    coor[0] = p_radius;
    coor[1] = p_center_x;
    coor[2] = p_center_y;
  }

  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    double[] location = new double[2];
    location[0] = coor[1];
    location[1] = coor[2];
    IntPoint center = p_coordinate_transform.dsn_to_board(location).round();
    int radius = (int) Math.round(p_coordinate_transform.dsn_to_board(coor[0]) / 2);
    return new app.freerouting.geometry.planar.Circle(center, radius);
  }

  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    int[] new_coor = new int[3];
    new_coor[0] = (int) Math.round(p_coordinate_transform.dsn_to_board(coor[0]) / 2);
    for (int i = 1; i < 3; ++i) {
      new_coor[i] = (int) Math.round(p_coordinate_transform.dsn_to_board(coor[i]));
    }
    return new app.freerouting.geometry.planar.Circle(
        new IntPoint(new_coor[1], new_coor[2]), new_coor[0]);
  }

  public Rectangle bounding_box() {
    double[] bounds = new double[4];
    bounds[0] = coor[1] - coor[0];
    bounds[1] = coor[2] - coor[0];
    bounds[2] = coor[1] + coor[0];
    bounds[3] = coor[2] + coor[0];
    return new Rectangle(layer, bounds);
  }

  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws java.io.IOException {
    p_file.new_line();
    p_file.write("(circle ");
    p_identifier_type.write(this.layer.name, p_file);
    for (int i = 0; i < coor.length; ++i) {
      p_file.write(" ");
      p_file.write(Double.valueOf(coor[i]).toString());
    }
    p_file.write(")");
  }

  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws java.io.IOException {
    p_file.new_line();
    p_file.write("(circle ");
    p_identifier_type.write(this.layer.name, p_file);
    for (int i = 0; i < coor.length; ++i) {
      p_file.write(" ");
      Integer curr_coor = (int) Math.round(coor[i]);
      p_file.write(curr_coor.toString());
    }
    p_file.write(")");
  }
}
