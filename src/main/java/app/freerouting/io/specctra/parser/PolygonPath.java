package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;

/** Class for reading and writing path scopes consisting of a polygon from dsn-files. */
public class PolygonPath extends Path {

  /** Creates a new instance of PolygonPath */
  public PolygonPath(Layer p_layer, double p_width, double[] p_coordinate_arr) {
    super(p_layer, p_width, p_coordinate_arr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.start_scope();
    p_file.write("path ");
    p_identifier_type.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(this.width));
    int cornerCount = coordinateArr.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      p_file.new_line();
      p_file.write(String.valueOf(coordinateArr[2 * i]));
      p_file.write(" ");
      p_file.write(String.valueOf(coordinateArr[2 * i + 1]));
    }
    p_file.end_scope();
  }

  @Override
  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.start_scope();
    p_file.write("path ");
    p_identifier_type.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(this.width));
    int cornerCount = coordinateArr.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      p_file.new_line();
      int currCoor = (int) Math.round(coordinateArr[2 * i]);
      p_file.write(String.valueOf(currCoor));
      p_file.write(" ");
      currCoor = (int) Math.round(coordinateArr[2 * i + 1]);
      p_file.write(String.valueOf(currCoor));
    }
    p_file.end_scope();
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    FloatPoint[] cornerArr = new FloatPoint[this.coordinateArr.length / 2];
    double[] currPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currPoint[0] = this.coordinateArr[2 * i];
      currPoint[1] = this.coordinateArr[2 * i + 1];
      cornerArr[i] = p_coordinate_transform.dsn_to_board(currPoint);
    }
    double offset = p_coordinate_transform.dsn_to_board(this.width) / 2;
    if (cornerArr.length <= 2) {
      IntOctagon boundingOct = FloatPoint.bounding_octagon(cornerArr);
      return boundingOct.enlarge(offset);
    }
    IntPoint[] roundedCornerArr = new IntPoint[cornerArr.length];
    for (int i = 0; i < cornerArr.length; i++) {
      roundedCornerArr[i] = cornerArr[i].round();
    }
    app.freerouting.geometry.planar.Shape result = new PolygonShape(roundedCornerArr);
    if (offset > 0) {
      result = result.bounding_tile().enlarge(offset);
    }
    return result;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    FloatPoint[] cornerArr = new FloatPoint[this.coordinateArr.length / 2];
    double[] currPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currPoint[0] = this.coordinateArr[2 * i];
      currPoint[1] = this.coordinateArr[2 * i + 1];
      cornerArr[i] = p_coordinate_transform.dsn_to_board_rel(currPoint);
    }
    double offset = p_coordinate_transform.dsn_to_board(this.width) / 2;
    if (cornerArr.length <= 2) {
      IntOctagon boundingOct = FloatPoint.bounding_octagon(cornerArr);
      return boundingOct.enlarge(offset);
    }
    IntPoint[] roundedCornerArr = new IntPoint[cornerArr.length];
    for (int i = 0; i < cornerArr.length; i++) {
      roundedCornerArr[i] = cornerArr[i].round();
    }
    app.freerouting.geometry.planar.Shape result = new PolygonShape(roundedCornerArr);
    if (offset > 0) {
      result = result.bounding_tile().enlarge(offset);
    }
    return result;
  }

  @Override
  public Rectangle bounding_box() {
    double offset = this.width / 2;
    double[] bounds = new double[4];
    bounds[0] = Integer.MAX_VALUE;
    bounds[1] = Integer.MAX_VALUE;
    bounds[2] = Integer.MIN_VALUE;
    bounds[3] = Integer.MIN_VALUE;
    for (int i = 0; i < coordinateArr.length; i++) {
      if (i % 2 == 0) {
        // x coordinate
        bounds[0] = Math.min(bounds[0], coordinateArr[i] - offset);
        bounds[2] = Math.max(bounds[2], coordinateArr[i]) + offset;
      } else {
        // x coordinate
        bounds[1] = Math.min(bounds[1], coordinateArr[i] - offset);
        bounds[3] = Math.max(bounds[3], coordinateArr[i] + offset);
      }
    }
    return new Rectangle(layer, bounds);
  }
}
