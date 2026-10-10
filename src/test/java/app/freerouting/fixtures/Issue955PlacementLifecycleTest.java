package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnWriter;
import app.freerouting.io.specctra.SesWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression coverage for precise placement throughout the component lifecycle. */
class Issue955PlacementLifecycleTest {
  private RoutingBoard load() throws Exception {
    try (var in = Files.newInputStream(Path.of("fixtures/Issue955-rotated-pad-contacts.dsn"))) {
      return (RoutingBoard) ((BoardReadResult.Success) DsnReader.readBoard(in, null, null)).board();
    }
  }

  private Pin find(BasicBoard board, String name) {
    return board.getPins().stream()
        .filter(p -> p.componentName().equals("S21") && p.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private RoutingBoard roundTrip(BasicBoard board) throws Exception {
    var out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "review", false);
    return (RoutingBoard)
        ((BoardReadResult.Success)
                DsnReader.readBoard(new ByteArrayInputStream(out.toByteArray()), null, null))
            .board();
  }

  @Test
  void dsnRoundTripPreservesBothContacts() throws Exception {
    var board = load();
    var reload = roundTrip(roundTrip(roundTrip(board)));
    for (Pin pin : board.getPins()) {
      Pin reloadedPin =
          reload.getPins().stream()
              .filter(
                  p -> p.componentName().equals(pin.componentName()) && p.pinIndex == pin.pinIndex)
              .findFirst()
              .orElseThrow();
      assertEquals(pin.getCenter(), reloadedPin.getCenter());
      assertEquals(pin.getNormalContacts().size(), reloadedPin.getNormalContacts().size());
    }
    assertAll(
        () -> assertEquals(1, find(reload, "2@1").getNormalContacts().size()),
        () -> assertEquals(1, find(reload, "1").getNormalContacts().size()));
  }

  private Pin synthetic(
      RoutingBoard board, FloatPoint placement, FloatPoint offset, double rotation, boolean front) {
    int pad =
        board.library.padstacks.add(
                new ConvexShape[] {new Circle(Point.ZERO, 100), new Circle(Point.ZERO, 100)})
            .id;
    var pkg = board.library.packages.add(new Package.Pin[] {new Package.Pin("1", pad, offset, 0)});
    var component =
        board.components.addPrecise("REVIEW", placement, rotation, front, pkg, pkg, false, null);
    return board.insertPin(component.id, 0, new int[] {1}, 1, FixedState.UNFIXED);
  }

  @Test
  void quarterTurnPreservesContact() throws Exception {
    transform(90, false);
  }

  @Test
  void arbitraryTurnPreservesContact() throws Exception {
    transform(45, false);
  }

  @Test
  void mirrorPreservesContact() throws Exception {
    transform(0, true);
  }

  private void transform(double angle, boolean mirror) throws Exception {
    var board = load();
    var pin = synthetic(board, new FloatPoint(20000.5, 50000.5), FloatPoint.ZERO, 0, true);
    var before = pin.getCenter();
    var trace =
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {before, before.translateBy(new IntVector(1000, 0))}),
            0,
            10,
            new int[] {1},
            1,
            FixedState.UNFIXED);
    assertEquals(1, pin.getNormalContacts().size());
    board.detachItemForMove(pin);
    board.detachItemForMove(trace);
    var moved =
        app.freerouting.board.actions.ComponentItemTransform.apply(
            board, java.util.List.of(pin, trace), angle, mirror, Point.ZERO);
    org.junit.jupiter.api.Assertions.assertNotNull(moved);
    pin = (Pin) moved.get(0);
    trace = (app.freerouting.board.trace.PolylineTrace) moved.get(1);
    for (var item : moved) {
      board.insertItem(item);
    }
    assertEquals(trace.firstCorner(), pin.getCenter());
    assertEquals(1, pin.getNormalContacts().size());
  }

  @Test
  void shapeAndPinUseSameAnchor() throws Exception {
    var board = load();
    var pin =
        synthetic(board, new FloatPoint(20000.49, 50000.49), new FloatPoint(0.49, 0.49), 0, true);
    assertEquals(pin.getCenter(), pin.getShape(0).centreOfGravity().round());
  }

  @Test
  void copyConstructionPreservesLocation() throws Exception {
    var board = load();
    var original = find(board, "2@1");
    var c = board.components.get(original.getComponentId());
    var copied = board.components.copy(c, Vector.ZERO, c.getPackage());
    var p = board.insertPin(copied.id, original.pinIndex, new int[] {1}, 1, FixedState.UNFIXED);
    assertEquals(original.getCenter(), p.getCenter());
  }

  @Test
  void copyRebindingInvalidatesCachedPadGeometry() throws Exception {
    var board = load();
    var original = find(board, "2@1");
    var c = board.components.get(original.getComponentId());
    var copy = (Pin) original.copy(0);
    var oldCenter = copy.getCenter();
    copy.getShape(0);
    var delta = new IntVector(10000, -20000);
    var copiedComponent = board.components.copy(c, delta, c.getPackage());
    copy.assignComponentId(copiedComponent.id);
    assertEquals(oldCenter.translateBy(delta), copy.getCenter());
    assertTrue(copy.getShape(0).contains(copy.getCenter()));
  }

  @Test
  void copyRebindingInvalidatesCachedKeepoutGeometry() throws Exception {
    var board = load();
    var c = board.components.get(find(board, "2@1").getComponentId());
    var copy =
        new ObstacleArea(
            new Circle(Point.ZERO, 100),
            0,
            Vector.ZERO,
            0,
            false,
            1,
            0,
            c.id,
            "copy-keepout",
            FixedState.UNFIXED,
            board);
    final var oldBounds = copy.getArea().boundingBox();
    var delta = new IntVector(10000, -20000);
    copy.translateBy(delta);
    // Populate the preview geometry before binding to the newly created component.
    copy.getArea();
    var copiedComponent = board.components.copy(c, delta, c.getPackage());
    copy.assignComponentId(copiedComponent.id);
    assertEquals(oldBounds.ll.translateBy(delta), copy.getArea().boundingBox().ll);
    assertEquals(oldBounds.ur.translateBy(delta), copy.getArea().boundingBox().ur);
    assertEquals(
        copy.getArea().boundingBox().ll, ((ObstacleArea) copy.copy(0)).getArea().boundingBox().ll);
  }

  @Test
  void mirroredExitDirectionsFollowPadOrientation() throws Exception {
    for (boolean rotateFirst : new boolean[] {false, true}) {
      var board = load();
      board.components.setFlipStyleRotateFirst(rotateFirst);
      int pad =
          board.library.padstacks.add(
                  new ConvexShape[] {
                    new app.freerouting.geometry.planar.IntBox(-400, -50, 400, 50),
                    new app.freerouting.geometry.planar.IntBox(-400, -50, 400, 50)
                  })
              .id;
      var pkg =
          board.library.packages.add(
              new Package.Pin[] {new Package.Pin("1", pad, Vector.ZERO, 45)});
      var component =
          board.components.add(
              "EXIT",
              new app.freerouting.geometry.planar.IntPoint(20000, 50000),
              30,
              false,
              pkg,
              pkg,
              false,
              null);
      var pin = board.insertPin(component.id, 0, new int[] {1}, 1, FixedState.UNFIXED);
      var exits = pin.getTraceExitRestrictions(0);
      assertEquals(2, exits.size());
      double angle = rotateFirst ? 105 : 165;
      var expected =
          app.freerouting.geometry.planar.Direction.getInstanceApprox(Math.toRadians(angle));
      assertTrue(exits.stream().anyMatch(exit -> exit.direction.equals(expected)));
      assertTrue(exits.stream().allMatch(exit -> exit.minLength > 0));
    }
  }

  @Test
  void packageIdentityIncludesFractionalOffsets() throws Exception {
    var board = load();
    var p = synthetic(board, new FloatPoint(20000.2, 50000), new FloatPoint(0.1, 0), 0, true);
    var pkg = board.components.get(p.getComponentId()).getPackage();
    var alternate =
        new Package.Pin[] {
          new Package.Pin("1", pkg.getPin(0).padstackId, new FloatPoint(0.4, 0), 0)
        };
    var method =
        app.freerouting.io.kicad.KiCadJsonReader.class.getDeclaredMethod(
            "arePackagePinsIdentical", Package.class, Package.Pin[].class);
    method.setAccessible(true);
    assertFalse((boolean) method.invoke(null, pkg, alternate));
  }

  @Test
  void sesPreservesFractionalPlacement() throws Exception {
    var board = load();
    var out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "review.dsn");
    Files.createDirectories(Path.of("logs/Issue955"));
    Files.write(Path.of("logs/Issue955/round-trip.ses"), out.toByteArray());
    String placement =
        out.toString(java.nio.charset.StandardCharsets.UTF_8)
            .lines()
            .filter(l -> l.contains("place S21 "))
            .findFirst()
            .orElseThrow();
    assertTrue(placement.contains("2097468.06 -1213891.58"));
  }

  @Test
  void fractionalRotationSurvivesDsnRoundTrip() throws Exception {
    var board = load();
    synthetic(board, new FloatPoint(20000, 50000), new FloatPoint(1000, 0), 12.2501234567, true);
    var reload = roundTrip(board);
    var component = reload.components.get("REVIEW");
    assertEquals(12.2501234567, component.getRotationInDegree());
  }

  @Test
  void backSideRotateFirstQuarterTurnUsesWorldDirection() throws Exception {
    var board = load();
    board.components.setFlipStyleRotateFirst(true);
    var pin = synthetic(board, new FloatPoint(20000, 50000), new FloatPoint(1000, 0), 30, false);
    final var expected = pin.getCenter().turn90Degree(1, Point.ZERO);
    board.searchTreeManager.remove(pin);
    board.components.turn90Degree(pin.getComponentId(), 1, Point.ZERO);
    pin.turn90Degree(1, Point.ZERO);
    board.searchTreeManager.insert(pin);
    assertEquals(expected, pin.getCenter());
  }

  @Test
  void backSideMirrorFirstReflectionUsesWorldDirection() throws Exception {
    var board = load();
    var pin = synthetic(board, new FloatPoint(20000, 50000), new FloatPoint(1000, 0), 30, true);
    final var expected = pin.getCenter().mirrorVertical(Point.ZERO);
    board.searchTreeManager.remove(pin);
    board.components.changeSide(pin.getComponentId(), Point.ZERO);
    pin.changePlacementSide(Point.ZERO);
    board.searchTreeManager.insert(pin);
    assertEquals(expected, pin.getCenter());
  }
}
