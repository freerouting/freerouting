package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;

/** Describes a rectangle in a Specctra dsn file. */
public class Rectangle extends Shape {

  public final double[] coor;

  /**
   * Creates a new instance of Rectangle p_coor is an array of dimension 4 and contains the
   * rectangle coordinates in the following order: lower left x, lower left y, upper right x, upper
   * right y.
   */
  public Rectangle(Layer pLayer, double[] pCoor) {
    super(pLayer);
    coor = pCoor;
  }

  @Override
  public Rectangle boundingBox() {
    return this;
  }

  /** Creates the smallest rectangle containing this rectangle and p_other */
  public Rectangle union(Rectangle pOther) {
    double[] resultCoor = new double[4];
    resultCoor[0] = Math.min(this.coor[0], pOther.coor[0]);
    resultCoor[1] = Math.min(this.coor[1], pOther.coor[1]);
    resultCoor[2] = Math.max(this.coor[2], pOther.coor[2]);
    resultCoor[3] = Math.max(this.coor[3], pOther.coor[3]);
    return new Rectangle(this.layer, resultCoor);
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform pCoordinateTransform) {
    int[] boxCoor = new int[4];
    for (int i = 0; i < 4; i++) {
      boxCoor[i] = (int) Math.round(pCoordinateTransform.dsnToBoard(this.coor[i]));
    }

    IntBox result;
    if (boxCoor[1] <= boxCoor[3]) {
      // boxCoor describe lower left and upper right corner
      result = new IntBox(boxCoor[0], boxCoor[1], boxCoor[2], boxCoor[3]);
    } else {
      // boxCoor describe upper left and lower right corner
      result = new IntBox(boxCoor[0], boxCoor[3], boxCoor[2], boxCoor[1]);
    }
    return result;
  }

  @Override
  public app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform pCoordinateTransform) {
    double[] currPoint = new double[2];
    currPoint[0] = Math.min(coor[0], coor[2]);
    currPoint[1] = Math.min(coor[1], coor[3]);
    FloatPoint lowerLeft = pCoordinateTransform.dsnToBoard(currPoint);
    currPoint[0] = Math.max(coor[0], coor[2]);
    currPoint[1] = Math.max(coor[1], coor[3]);
    FloatPoint upperRight = pCoordinateTransform.dsnToBoard(currPoint);
    return new IntBox(lowerLeft.round(), upperRight.round());
  }

  /** Writes this rectangle as a scope to an output dsn-file. */
  @Override
  public void writeScope(IndentFileWriter pFile, IdentifierType pIdentifier) throws IOException {
    pFile.newLine();
    pFile.write("(rect ");
    pIdentifier.write(this.layer.name, pFile);
    for (int i = 0; i < coor.length; i++) {
      pFile.write(" ");
      pFile.write(String.valueOf(coor[i]));
    }
    pFile.write(")");
  }

  @Override
  public void writeScopeInt(IndentFileWriter pFile, IdentifierType pIdentifier) throws IOException {
    pFile.newLine();
    pFile.write("(rect ");
    pIdentifier.write(this.layer.name, pFile);
    for (int i = 0; i < coor.length; i++) {
      pFile.write(" ");
      int currCoor = (int) Math.round(coor[i]);
      pFile.write(String.valueOf(currCoor));
    }
    pFile.write(")");
  }
}
