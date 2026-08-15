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

  /** Creates a new instance of PolygonPath. */
  public PolygonPath(Layer layer, double width, double[] coordinateArr) {
    super(layer, width, coordinateArr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public void writeScope(IndentFileWriter file, IdentifierType identifierType) throws IOException {
    file.startScope();
    file.write("path ");
    identifierType.write(this.layer.name, file);
    file.write(" ");
    file.write(String.valueOf(this.width));
    int cornerCount = coordinateArr.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      file.newLine();
      file.write(String.valueOf(coordinateArr[2 * i]));
      file.write(" ");
      file.write(String.valueOf(coordinateArr[2 * i + 1]));
    }
    file.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.startScope();
    file.write("path ");
    identifierType.write(this.layer.name, file);
    file.write(" ");
    file.write(String.valueOf(this.width));
    int cornerCount = coordinateArr.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      file.newLine();
      int currentCoor = (int) Math.round(coordinateArr[2 * i]);
      file.write(String.valueOf(currentCoor));
      file.write(" ");
      currentCoor = (int) Math.round(coordinateArr[2 * i + 1]);
      file.write(String.valueOf(currentCoor));
    }
    file.endScope();
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform coordinateTransform) {
    FloatPoint[] cornerArr = new FloatPoint[this.coordinateArr.length / 2];
    double[] currentPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currentPoint[0] = this.coordinateArr[2 * i];
      currentPoint[1] = this.coordinateArr[2 * i + 1];
      cornerArr[i] = coordinateTransform.dsnToBoard(currentPoint);
    }
    final double offset = coordinateTransform.dsnToBoard(this.width) / 2;
    if (cornerArr.length <= 2) {
      IntOctagon boundingOct = FloatPoint.boundingOctagon(cornerArr);
      return boundingOct.enlarge(offset);
    }
    IntPoint[] roundedCornerArr = new IntPoint[cornerArr.length];
    for (int i = 0; i < cornerArr.length; i++) {
      roundedCornerArr[i] = cornerArr[i].round();
    }
    app.freerouting.geometry.planar.Shape result = new PolygonShape(roundedCornerArr);
    if (offset > 0) {
      result = result.boundingTile().enlarge(offset);
    }
    return result;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform coordinateTransform) {
    FloatPoint[] cornerArr = new FloatPoint[this.coordinateArr.length / 2];
    double[] currentPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currentPoint[0] = this.coordinateArr[2 * i];
      currentPoint[1] = this.coordinateArr[2 * i + 1];
      cornerArr[i] = coordinateTransform.dsnToBoardRel(currentPoint);
    }
    final double offset = coordinateTransform.dsnToBoard(this.width) / 2;
    if (cornerArr.length <= 2) {
      IntOctagon boundingOct = FloatPoint.boundingOctagon(cornerArr);
      return boundingOct.enlarge(offset);
    }
    IntPoint[] roundedCornerArr = new IntPoint[cornerArr.length];
    for (int i = 0; i < cornerArr.length; i++) {
      roundedCornerArr[i] = cornerArr[i].round();
    }
    app.freerouting.geometry.planar.Shape result = new PolygonShape(roundedCornerArr);
    if (offset > 0) {
      result = result.boundingTile().enlarge(offset);
    }
    return result;
  }

  @Override
  public Rectangle boundingBox() {
    final double offset = this.width / 2;
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
