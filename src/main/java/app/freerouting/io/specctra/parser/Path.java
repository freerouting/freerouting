package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import java.io.IOException;

/** Class for writing path scopes from dsn-files. */
public abstract class Path extends Shape {

  public final double width;
  public final double[] coordinateArr;

  /** Creates a new instance of Path */
  Path(Layer p_layer, double p_width, double[] p_coordinate_arr) {
    super(p_layer);
    width = p_width;
    coordinateArr = p_coordinate_arr;
  }

  /** Writes this path as a scope to an output dsn-file. */
  @Override
  public abstract void writeScope(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException;
}
