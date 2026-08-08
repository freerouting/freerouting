package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import java.io.IOException;

/** Class for writing path scopes from dsn-files. */
public abstract class Path extends Shape {

  public final double width;
  public final double[] coordinateArr;

  /** Creates a new instance of Path */
  Path(Layer pLayer, double pWidth, double[] pCoordinateArr) {
    super(pLayer);
    width = pWidth;
    coordinateArr = pCoordinateArr;
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public abstract void writeScope(IndentFileWriter pFile, IdentifierType pIdentifier)
      throws IOException;
}
