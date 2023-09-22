package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;

import java.io.IOException;

/** Describes a path defined by a sequence of lines instead of a sequence of corners. */
public class PolylinePath extends Path {

  /** Creates a new instance of PolylinePath */
  public PolylinePath(Layer p_layer, double p_width, double[] p_corner_arr) {
    super(p_layer, p_width, p_corner_arr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException {
    p_file.start_scope();
    p_file.write("polyline_path ");
    p_identifier.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(this.width));
    int line_count = coordinate_arr.length / 4;
    for (int i = 0; i < line_count; ++i) {
      p_file.new_line();
      for (int j = 0; j < 4; ++j) {
        p_file.write(String.valueOf(coordinate_arr[4 * i + j]));
        p_file.write(" ");
      }
    }
    p_file.end_scope();
  }

  @Override
  public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException {
    p_file.start_scope();
    p_file.write("polyline_path ");
    p_identifier.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(this.width));
    int line_count = coordinate_arr.length / 4;
    for (int i = 0; i < line_count; ++i) {
      p_file.new_line();
      for (int j = 0; j < 4; ++j) {
        int curr_coor = (int) Math.round(coordinate_arr[4 * i + j]);
        p_file.write(String.valueOf(curr_coor));
        p_file.write(" ");
      }
    }
    p_file.end_scope();
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform) {
    FRLogger.warn("PolylinePath.transform_to_board_rel not implemented");
    return null;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform) {
    FRLogger.warn("PolylinePath.transform_to_board not implemented");
    return null;
  }

  @Override
  public Rectangle bounding_box() {
    FRLogger.warn("PolylinePath.bounding_box not implemented");
    return null;
  }
}
