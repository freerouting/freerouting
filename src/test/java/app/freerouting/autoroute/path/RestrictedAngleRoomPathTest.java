package app.freerouting.autoroute.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class RestrictedAngleRoomPathTest {
  @ParameterizedTest
  @EnumSource(
      value = AngleRestriction.class,
      names = {"FORTYFIVE_DEGREE", "NINETY_DEGREE"})
  void buildsContainedStaircaseWhenBothSingleElbowsLeaveTheRoom(AngleRestriction angle) {
    TileShape room = corridor(10000, 1000);
    IntPoint from = new IntPoint(1000, 800), to = new IntPoint(9000, 3200);
    var path = RestrictedAngleRoomPath.connect(room, 150, from, to, angle);
    assertPath(room, from, to, angle, path);
    assertTrue(path.size() > 2);
    // The middle portion must respect the requested wall inset, not just centreline containment.
    for (IntPoint point : path) {
      assertTrue(((TileShape) room.offset(-150)).contains(point));
    }
    assertEquals(path, RestrictedAngleRoomPath.connect(room, 150, from, to, angle));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void triesBothLatticeRoundingsOfMidpoint(int rotation) {
    TileShape band =
        Simplex.getInstance(new Line[] {new Line(0, 0, 3, 2), new Line(5, 4, 2, 2)})
            .intersection(new IntBox(0, -10, 3, 10));
    IntPoint origin = new IntPoint(0, 0);
    TileShape room = (TileShape) band.turn90Degree(rotation, origin);
    IntPoint from = origin, to = (IntPoint) new IntPoint(3, 2).turn90Degree(rotation, origin);
    assertPath(
        room,
        from,
        to,
        AngleRestriction.FORTYFIVE_DEGREE,
        RestrictedAngleRoomPath.connect(room, 0, from, to, AngleRestriction.FORTYFIVE_DEGREE));
  }

  @Test
  void allowsDoorEndpointsWithoutOpeningTheOtherRoomWalls() {
    TileShape room = corridor(10000, 1000);
    IntPoint from = new IntPoint(0, 500), to = new IntPoint(10000, 3500);
    assertPath(
        room,
        from,
        to,
        AngleRestriction.NINETY_DEGREE,
        RestrictedAngleRoomPath.connect(room, 150, from, to, AngleRestriction.NINETY_DEGREE));
    assertNull(
        RestrictedAngleRoomPath.connect(
            room, 150, new IntPoint(-1, 500), to, AngleRestriction.NINETY_DEGREE));
  }

  @Test
  void preservesRoundedObliqueDoorPointButRejectsMoreThanGridRounding() {
    TileShape room = corridor(10000, 1000);
    IntPoint from = new IntPoint(1001, 300), to = new IntPoint(9000, 3200);
    assertFalse(room.contains(from));
    var path =
        RestrictedAngleRoomPath.connect(room, 150, from, to, AngleRestriction.FORTYFIVE_DEGREE);
    assertPath((TileShape) room.offset(1), from, to, AngleRestriction.FORTYFIVE_DEGREE, path);
    assertNull(
        RestrictedAngleRoomPath.connect(
            room, 150, new IntPoint(1003, 300), to, AngleRestriction.FORTYFIVE_DEGREE));
  }

  @Test
  void declinesUnsupportedLatticeAndBoundsLongConstruction() {
    TileShape line =
        new Polyline(new IntPoint(0, 0), new IntPoint(10000000, 3000000)).offsetShape(0, 0);
    assertNull(
        RestrictedAngleRoomPath.connect(
            line,
            0,
            new IntPoint(0, 0),
            new IntPoint(10000000, 3000000),
            AngleRestriction.NINETY_DEGREE));
    TileShape room = corridor(10000000, 1);
    assertNull(
        RestrictedAngleRoomPath.connect(
            room,
            0,
            new IntPoint(0, 0),
            new IntPoint(10000000, 3000000),
            AngleRestriction.NINETY_DEGREE));
  }

  private static TileShape corridor(int length, int width) {
    return new PolygonShape(
            new Point[] {
              new IntPoint(0, 0),
              new IntPoint(length, 3 * length / 10),
              new IntPoint(length, 3 * length / 10 + width),
              new IntPoint(0, width)
            })
        .splitToConvex()[0];
  }

  private static void assertPath(
      TileShape room, IntPoint from, IntPoint to, AngleRestriction angle, List<IntPoint> corners) {
    assertNotNull(corners);
    assertFalse(corners.isEmpty());
    assertEquals(to, corners.getLast());
    IntPoint previous = from;
    for (IntPoint point : corners) {
      assertTrue(room.contains(point), point.toString());
      int dx = point.x - previous.x, dy = point.y - previous.y;
      assertTrue(
          dx == 0
              || dy == 0
              || angle == AngleRestriction.FORTYFIVE_DEGREE && Math.abs(dx) == Math.abs(dy));
      previous = point;
    }
  }
}
