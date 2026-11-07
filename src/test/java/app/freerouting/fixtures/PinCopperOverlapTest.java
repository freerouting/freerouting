package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.management.BoardLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The pins whose copper overlaps a pin, filtered before the exact overlap test of the search tree,
 * are the ones of the former query, in the same order.
 */
class PinCopperOverlapTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(strings = {"Issue756-tomu-fpga.dsn", "Issue367-Charger.dsn", "Issue029-hw48na.dsn"})
  void copperOverlappingPinsMatchTheUnfilteredQuery(String fixture) {
    var job = getRoutingJob(fixture);
    BoardLoader.loadBoardIfNeeded(job);
    BasicBoard board = job.board;

    int pins = 0;
    int overlaps = 0;
    for (Item item : board.getItems()) {
      if (item instanceof Pin pin) {
        Predicate<Pin> anyPin = other -> true;
        List<Pin> expected = unfilteredCopperOverlappingPins(board, pin, anyPin);
        assertEquals(expected, pin.copperOverlappingPins(anyPin), fixture + ", pin " + pin.getId());
        Predicate<Pin> sameNet = other -> other.sharesNet(pin);
        assertEquals(
            unfilteredCopperOverlappingPins(board, pin, sameNet),
            pin.copperOverlappingPins(sameNet),
            fixture + ", pin " + pin.getId() + ", same net");
        pins++;
        overlaps += expected.size();
      }
    }
    assertTrue(pins > 0, fixture + " has no pin");
    assertTrue(overlaps > 0, fixture + " has no overlapping pins: nothing was compared");
  }

  /** Pin.copperOverlappingPins before the pin tests came first. */
  private static List<Pin> unfilteredCopperOverlappingPins(
      BasicBoard board, Pin pin, Predicate<Pin> accept) {
    List<Pin> result = new LinkedList<>();
    IntBox box = pin.boundingBox();
    for (SearchTreeObject obj : board.overlappingObjects(box, -1)) {
      if (!(obj instanceof Pin other)) {
        continue;
      }
      if (other == pin
          || !accept.test(other)
          || !other.sharesLayer(pin)
          || !box.intersects(other.boundingBox())) {
        continue;
      }
      boolean overlaps = false;
      for (int i = 0; i < pin.tileShapeCount() && !overlaps; i++) {
        Shape mine = pin.getShape(i);
        for (int j = 0; j < other.tileShapeCount() && !overlaps; j++) {
          Shape theirs = other.getShape(j);
          overlaps =
              mine != null
                  && theirs != null
                  && pin.shapeLayer(i) == other.shapeLayer(j)
                  && mine.intersects(theirs);
        }
      }
      if (overlaps) {
        result.add(other);
      }
    }
    return result;
  }
}
