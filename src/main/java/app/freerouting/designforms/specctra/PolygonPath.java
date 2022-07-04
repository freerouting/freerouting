package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;

/** Class for reading and writing path scopes consisting of a polygon from dsn-files. */
public class PolygonPath extends Path {

  /** Creates a new instance of PolygonPath */
  public PolygonPath(Layer p_layer, double p_width, double[] p_coordinate_arr) {
    super(p_layer, p_width, p_coordinate_arr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws java.io.IOException {
    p_file.start_scope();
    p_file.write("path ");
    p_identifier_type.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write((Double.valueOf(this.width)).toString());
    int corner_count = coordinate_arr.length / 2;
    for (int i = 0; i < corner_count; ++i) {
      p_file.new_line();
      p_file.write(Double.valueOf(coordinate_arr[2 * i]).toString());
      p_file.write(" ");
      p_file.write(Double.valueOf(coordinate_arr[2 * i + 1]).toString());
    }
    p_file.end_scope();
  }

  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws java.io.IOException {
    p_file.start_scope();
    p_file.write("path ");
    p_identifier_type.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write((Double.valueOf(this.width)).toString());
    int corner_count = coordinate_arr.length / 2;
    for (int i = 0; i < corner_count; ++i) {
      p_file.new_line();
      Integer curr_coor = (int) Math.round(coordinate_arr[2 * i]);
      p_file.write(curr_coor.toString());
      p_file.write(" ");
      curr_coor = (int) Math.round(coordinate_arr[2 * i + 1]);
      p_file.write(curr_coor.toString());
    }
    p_file.end_scope();
  }

  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    FloatPoint[] corner_arr = new FloatPoint[this.coordinate_arr.length / 2];
    double[] curr_point = new double[2];
    for (int i = 0; i < corner_arr.length; ++i) {
      curr_point[0] = this.coordinate_arr[2 * i];
      curr_point[1] = this.coordinate_arr[2 * i + 1];
      corner_arr[i] = p_coordinate_transform.dsn_to_board(curr_point);
    }
    double offset = p_coordinate_transform.dsn_to_board(this.width) / 2;
    if (corner_arr.length <= 2) {
      IntOctagon bounding_oct = FloatPoint.bounding_octagon(corner_arr);
      return bounding_oct.enlarge(offset);
    }
    IntPoint[] rounded_corner_arr = new IntPoint[corner_arr.length];
    for (int i = 0; i < corner_arr.length; ++i) {
      rounded_corner_arr[i] = corner_arr[i].round();
    }
    app.freerouting.geometry.planar.Shape result =
        new app.freerouting.geometry.planar.PolygonShape(rounded_corner_arr);
    if (offset > 0) {
      result = result.bounding_tile().enlarge(offset);
    }
    return result;
  }

  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    FloatPoint[] corner_arr = new FloatPoint[this.coordinate_arr.length / 2];
    double[] curr_point = new double[2];
    for (int i = 0; i < corner_arr.length; ++i) {
      curr_point[0] = this.coordinate_arr[2 * i];
      curr_point[1] = this.coordinate_arr[2 * i + 1];
      corner_arr[i] = p_coordinate_transform.dsn_to_board_rel(curr_point);
    }
    double offset = p_coordinate_transform.dsn_to_board(this.width) / 2;
    if (corner_arr.length <= 2) {
      IntOctagon bounding_oct = FloatPoint.bounding_octagon(corner_arr);
      return bounding_oct.enlarge(offset);
    }
    IntPoint[] rounded_corner_arr = new IntPoint[corner_arr.length];
    for (int i = 0; i < corner_arr.length; ++i) {
      rounded_corner_arr[i] = corner_arr[i].round();
    }
    app.freerouting.geometry.planar.Shape result =
        new app.freerouting.geometry.planar.PolygonShape(rounded_corner_arr);
    if (offset > 0) {
      result = result.bounding_tile().enlarge(offset);
    }
    return result;
  }

  public Rectangle bounding_box() {
    double offset = this.width / 2;
    double[] bounds = new double[4];
    bounds[0] = Integer.MAX_VALUE;
    bounds[1] = Integer.MAX_VALUE;
    bounds[2] = Integer.MIN_VALUE;
    bounds[3] = Integer.MIN_VALUE;
    for (int i = 0; i < coordinate_arr.length; ++i) {
      if (i % 2 == 0) {
        // x coordinate
        bounds[0] = Math.min(bounds[0], coordinate_arr[i] - offset);
        bounds[2] = Math.max(bounds[2], coordinate_arr[i]) + offset;
      } else {
        // x coordinate
        bounds[1] = Math.min(bounds[1], coordinate_arr[i] - offset);
        bounds[3] = Math.max(bounds[3], coordinate_arr[i] + offset);
      }
    }
    return new Rectangle(layer, bounds);
  }
}
