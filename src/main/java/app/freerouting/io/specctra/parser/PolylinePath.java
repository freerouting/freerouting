package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Describes a path defined by a sequence of lines instead of a sequence of corners. */
public class PolylinePath extends Path {

  /** Creates a new instance of PolylinePath. */
  public PolylinePath(Layer layer, double width, double[] cornerArr) {
    super(layer, width, cornerArr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public void writeScope(IndentFileWriter file, IdentifierType identifier) throws IOException {
    file.startScope();
    file.write("polyline_path ");
    identifier.write(this.layer.name, file);
    file.write(" ");
    file.write(String.valueOf(this.width));
    int lineCount = coordinateArr.length / 4;
    for (int i = 0; i < lineCount; i++) {
      file.newLine();
      for (int j = 0; j < 4; j++) {
        file.write(String.valueOf(coordinateArr[4 * i + j]));
        file.write(" ");
      }
    }
    file.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter file, IdentifierType identifier) throws IOException {
    file.startScope();
    file.write("polyline_path ");
    identifier.write(this.layer.name, file);
    file.write(" ");
    file.write(String.valueOf(this.width));
    int lineCount = coordinateArr.length / 4;
    for (int i = 0; i < lineCount; i++) {
      file.newLine();
      for (int j = 0; j < 4; j++) {
        int currentCoor = (int) Math.round(coordinateArr[4 * i + j]);
        file.write(String.valueOf(currentCoor));
        file.write(" ");
      }
    }
    file.endScope();
  }

  @Override
  public Shape transformToBoardRel(CoordinateTransform coordinateTransform) {
    FRLogger.warn("PolylinePath.transform_to_board_rel not implemented");
    return null;
  }

  @Override
  public Shape transformToBoard(CoordinateTransform coordinateTransform) {
    FRLogger.warn("PolylinePath.transform_to_board not implemented");
    return null;
  }

  @Override
  public Rectangle boundingBox() {
    FRLogger.warn("PolylinePath.boundingBox not implemented");
    return null;
  }
}
