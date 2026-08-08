package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.io.CoordinateTransform;
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
  public void writeScope(IndentFileWriter p_file, IdentifierType p_identifier) throws IOException {
    p_file.startScope();
    p_file.write("polyline_path ");
    p_identifier.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(this.width));
    int lineCount = coordinateArr.length / 4;
    for (int i = 0; i < lineCount; i++) {
      p_file.newLine();
      for (int j = 0; j < 4; j++) {
        p_file.write(String.valueOf(coordinateArr[4 * i + j]));
        p_file.write(" ");
      }
    }
    p_file.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException {
    p_file.startScope();
    p_file.write("polyline_path ");
    p_identifier.write(this.layer.name, p_file);
    p_file.write(" ");
    p_file.write(String.valueOf(this.width));
    int lineCount = coordinateArr.length / 4;
    for (int i = 0; i < lineCount; i++) {
      p_file.newLine();
      for (int j = 0; j < 4; j++) {
        int currCoor = (int) Math.round(coordinateArr[4 * i + j]);
        p_file.write(String.valueOf(currCoor));
        p_file.write(" ");
      }
    }
    p_file.endScope();
  }

  @Override
  public Shape transformToBoardRel(CoordinateTransform p_coordinate_transform) {
    FRLogger.warn("PolylinePath.transform_to_board_rel not implemented");
    return null;
  }

  @Override
  public Shape transformToBoard(CoordinateTransform p_coordinate_transform) {
    FRLogger.warn("PolylinePath.transform_to_board not implemented");
    return null;
  }

  @Override
  public Rectangle boundingBox() {
    FRLogger.warn("PolylinePath.boundingBox not implemented");
    return null;
  }
}
