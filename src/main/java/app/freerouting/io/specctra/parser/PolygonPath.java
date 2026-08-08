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
  public PolygonPath(Layer pLayer, double pWidth, double[] pCoordinateArr) {
    super(pLayer, pWidth, pCoordinateArr);
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public void writeScope(IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.startScope();
    pFile.write("path ");
    pIdentifierType.write(this.layer.name, pFile);
    pFile.write(" ");
    pFile.write(String.valueOf(this.width));
    int cornerCount = coordinateArr.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      pFile.newLine();
      pFile.write(String.valueOf(coordinateArr[2 * i]));
      pFile.write(" ");
      pFile.write(String.valueOf(coordinateArr[2 * i + 1]));
    }
    pFile.endScope();
  }

  @Override
  public void writeScopeInt(IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.startScope();
    pFile.write("path ");
    pIdentifierType.write(this.layer.name, pFile);
    pFile.write(" ");
    pFile.write(String.valueOf(this.width));
    int cornerCount = coordinateArr.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      pFile.newLine();
      int currCoor = (int) Math.round(coordinateArr[2 * i]);
      pFile.write(String.valueOf(currCoor));
      pFile.write(" ");
      currCoor = (int) Math.round(coordinateArr[2 * i + 1]);
      pFile.write(String.valueOf(currCoor));
    }
    pFile.endScope();
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform pCoordinateTransform) {
    FloatPoint[] cornerArr = new FloatPoint[this.coordinateArr.length / 2];
    double[] currPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currPoint[0] = this.coordinateArr[2 * i];
      currPoint[1] = this.coordinateArr[2 * i + 1];
      cornerArr[i] = pCoordinateTransform.dsnToBoard(currPoint);
    }
    double offset = pCoordinateTransform.dsnToBoard(this.width) / 2;
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
      CoordinateTransform pCoordinateTransform) {
    FloatPoint[] cornerArr = new FloatPoint[this.coordinateArr.length / 2];
    double[] currPoint = new double[2];
    for (int i = 0; i < cornerArr.length; i++) {
      currPoint[0] = this.coordinateArr[2 * i];
      currPoint[1] = this.coordinateArr[2 * i + 1];
      cornerArr[i] = pCoordinateTransform.dsnToBoardRel(currPoint);
    }
    double offset = pCoordinateTransform.dsnToBoard(this.width) / 2;
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
