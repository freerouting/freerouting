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
  public PolylinePath(Layer pLayer, double pWidth, double[] pCornerArr) {
    super(pLayer, pWidth, pCornerArr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public void writeScope(IndentFileWriter pFile, IdentifierType pIdentifier) throws IOException {
    pFile.startScope();
    pFile.write("polyline_path ");
    pIdentifier.write(this.layer.name, pFile);
    pFile.write(" ");
    pFile.write(String.valueOf(this.width));
    int lineCount = coordinateArr.length / 4;
    for (int i = 0; i < lineCount; i++) {
      pFile.newLine();
      for (int j = 0; j < 4; j++) {
        pFile.write(String.valueOf(coordinateArr[4 * i + j]));
        pFile.write(" ");
      }
    }
    pFile.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter pFile, IdentifierType pIdentifier) throws IOException {
    pFile.startScope();
    pFile.write("polyline_path ");
    pIdentifier.write(this.layer.name, pFile);
    pFile.write(" ");
    pFile.write(String.valueOf(this.width));
    int lineCount = coordinateArr.length / 4;
    for (int i = 0; i < lineCount; i++) {
      pFile.newLine();
      for (int j = 0; j < 4; j++) {
        int currCoor = (int) Math.round(coordinateArr[4 * i + j]);
        pFile.write(String.valueOf(currCoor));
        pFile.write(" ");
      }
    }
    pFile.endScope();
  }

  @Override
  public Shape transformToBoardRel(CoordinateTransform pCoordinateTransform) {
    FRLogger.warn("PolylinePath.transform_to_board_rel not implemented");
    return null;
  }

  @Override
  public Shape transformToBoard(CoordinateTransform pCoordinateTransform) {
    FRLogger.warn("PolylinePath.transform_to_board not implemented");
    return null;
  }

  @Override
  public Rectangle boundingBox() {
    FRLogger.warn("PolylinePath.boundingBox not implemented");
    return null;
  }
}
