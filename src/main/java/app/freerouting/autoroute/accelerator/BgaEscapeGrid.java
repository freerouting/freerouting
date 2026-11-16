package app.freerouting.autoroute.accelerator;

/**
 * Input of one BGA escape solve, in the flat int layout used by the escape kernels.
 *
 * @param pins pin records, {@code pinCount * 12} ints (see {@code BgaBufferMarshaller})
 * @param pinCount number of pins
 * @param obstacles obstacle records, {@code obstacleCount * 10} ints
 * @param obstacleCount number of obstacle records (at least 1, as passed to the OpenCL kernels)
 * @param gridWidth grid cells along u
 * @param gridHeight grid cells along v
 * @param layerCount copper layers
 * @param gridStep grid step in board units
 * @param originX u of grid cell 0
 * @param originY v of grid cell 0
 * @param pitch ball pitch in board units
 * @param maxRing index of the innermost ring
 */
public record BgaEscapeGrid(
    int[] pins,
    int pinCount,
    int[] obstacles,
    int obstacleCount,
    int gridWidth,
    int gridHeight,
    int layerCount,
    int gridStep,
    int originX,
    int originY,
    int pitch,
    int maxRing) {

  /** Number of ints per pin in the solution buffer returned by the kernels. */
  public static final int SOLUTION_STRIDE = 16;

  /** Total number of grid cells over all layers. */
  public int totalCells() {
    return gridWidth * gridHeight * layerCount;
  }
}
