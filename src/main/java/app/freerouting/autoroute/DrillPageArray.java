package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.TileShape;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Describes the 2 dimensional array of pages of ExpansionDrill`s used in the maze search algorithm.
 * The pages are rectangles of about equal width and height covering the bounding box of the board
 * area.
 */
public class DrillPageArray {

  private final IntBox bounds;

  /** The number of columns in the array. */
  private final int columnCount;

  /** The number of rows in the array. */
  private final int rowCount;

  /** The width of a single page in this array. */
  private final int pageWidth;

  /** The height of a single page in this array. */
  private final int pageHeight;

  private final DrillPage[][] pageArr;

  /** Creates a new instance of DrillPageArray */
  public DrillPageArray(RoutingBoard pBoard, int pMaxPageWidth) {
    this.bounds = pBoard.boundingBox;
    double length = bounds.ur.x - bounds.ll.x;
    double height = bounds.ur.y - bounds.ll.y;
    this.columnCount = (int) Math.ceil(length / pMaxPageWidth);
    this.rowCount = (int) Math.ceil(height / pMaxPageWidth);
    this.pageWidth = (int) Math.ceil(length / columnCount);
    this.pageHeight = (int) Math.ceil(height / rowCount);
    this.pageArr = new DrillPage[rowCount][columnCount];
    for (int j = 0; j < this.rowCount; j++) {
      for (int i = 0; i < this.columnCount; i++) {
        int llX = bounds.ll.x + i * pageWidth;
        int urX;
        if (i == columnCount - 1) {
          urX = bounds.ur.x;
        } else {
          urX = llX + pageWidth;
        }
        int llY = bounds.ll.y + j * pageHeight;
        int urY;
        if (j == rowCount - 1) {
          urY = bounds.ur.y;
        } else {
          urY = llY + pageHeight;
        }
        pageArr[j][i] = new DrillPage(new IntBox(llX, llY, urX, urY), pBoard);
      }
    }
  }

  /**
   * Invalidates all drill pages intersecting with p_shape, so they must be recalculated at the next
   * call of get_ddrills()
   */
  public void invalidate(TileShape pShape) {
    Collection<DrillPage> overlaps = overlappingPages(pShape);
    for (DrillPage currPage : overlaps) {
      currPage.invalidate();
    }
  }

  /** Collects all drill pages with a 2-dimensional overlap with p_shape. */
  public Collection<DrillPage> overlappingPages(TileShape pShape) {
    Collection<DrillPage> result = new LinkedList<>();

    IntBox shapeBox = pShape.boundingBox().intersection(this.bounds);

    int minJ = (int) Math.floor(((double) (shapeBox.ll.y - bounds.ll.y)) / (double) pageHeight);
    double maxJ = ((double) (shapeBox.ur.y - bounds.ll.y)) / (double) pageHeight;

    int minI = (int) Math.floor(((double) (shapeBox.ll.x - bounds.ll.x)) / (double) pageWidth);
    double maxI = ((double) (shapeBox.ur.x - bounds.ll.x)) / (double) pageWidth;

    for (int j = minJ; j < maxJ; j++) {
      for (int i = minI; i < maxI; i++) {
        DrillPage currPage = this.pageArr[j][i];
        TileShape intersection = pShape.intersection(currPage.shape);
        if (intersection.dimension() > 1) {
          result.add(this.pageArr[j][i]);
        }
      }
    }
    return result;
  }

  /** Resets all drill pages for autorouting the next connection. */
  public void reset() {
    for (int j = 0; j < pageArr.length; j++) {
      DrillPage[] currRow = pageArr[j];
      for (int i = 0; i < currRow.length; i++) {
        currRow[i].reset();
      }
    }
  }

  /*
   * Test draw of the all drills
   */
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext, double pIntensity) {
    for (int j = 0; j < pageArr.length; j++) {
      DrillPage[] currRow = pageArr[j];
      for (int i = 0; i < currRow.length; i++) {
        currRow[i].draw(pGraphics, pGraphicsContext, pIntensity);
      }
    }
  }
}
