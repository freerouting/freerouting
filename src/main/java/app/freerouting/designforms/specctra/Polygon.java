package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Simplex;

import java.io.IOException;

/** Describes a polygon in a Specctra dsn file. */
public class Polygon extends Shape {
  public final double[] coor;

  /**
   * Creates a new instance of Polygon p_coor is an array of dimension 2 * point_count
   * and contains x0, y0, x1, y1, ... If the polygon is used as rectangle,
   */
  public Polygon(Layer p_layer, double[] p_coor) {
    super(p_layer);
    coor = p_coor;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    IntPoint[] corner_arr = new IntPoint[coor.length / 2];
    double[] curr_point = new double[2];
    for (int i = 0; i < corner_arr.length; ++i) {
      curr_point[0] = coor[2 * i];
      curr_point[1] = coor[2 * i + 1];
      corner_arr[i] = p_coordinate_transform.dsn_to_board(curr_point).round();
    }
    return new PolygonShape(corner_arr);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    if (coor.length < 2) {
      return Simplex.EMPTY;
    }
    IntPoint[] corner_arr = new IntPoint[coor.length / 2];
    for (int i = 0; i < corner_arr.length; ++i) {
      int curr_x = (int) Math.round(p_coordinate_transform.dsn_to_board(coor[2 * i]));
      int curr_y = (int) Math.round(p_coordinate_transform.dsn_to_board(coor[2 * i + 1]));
      corner_arr[i] = new IntPoint(curr_x, curr_y);
    }
    return new PolygonShape(corner_arr);
  }

  @Override
  public Rectangle bounding_box() {
    double[] bounds = new double[4];
    bounds[0] = Integer.MAX_VALUE;
    bounds[1] = Integer.MAX_VALUE;
    bounds[2] = Integer.MIN_VALUE;
    bounds[3] = Integer.MIN_VALUE;
    for (int i = 0; i < coor.length; ++i) {
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
  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.start_scope();
    p_file.write("polygon ");
    p_identifier_type.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(0));
    int corner_count = coor.length / 2;
    for (int i = 0; i < corner_count; ++i) {
      p_file.new_line();
      p_file.write(String.valueOf(coor[2 * i]));
      p_file.write(" ");
      p_file.write(String.valueOf(coor[2 * i + 1]));
    }
    p_file.end_scope();
  }

  @Override
  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.start_scope();
    p_file.write("polygon ");
    p_identifier_type.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(0));
    int corner_count = coor.length / 2;
    for (int i = 0; i < corner_count; ++i) {
      p_file.new_line();
      int curr_coor = (int) Math.round(coor[2 * i]);
      p_file.write(String.valueOf(curr_coor));
      p_file.write(" ");
      curr_coor = (int) Math.round(coor[2 * i + 1]);
      p_file.write(String.valueOf(curr_coor));
    }
    p_file.end_scope();
  }
}
