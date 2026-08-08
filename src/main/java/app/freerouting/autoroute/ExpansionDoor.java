package app.freerouting.autoroute;

import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;

/** An ExpansionDoor is a common edge between two ExpansionRooms */
public class ExpansionDoor implements ExpandableObject {

  /** The first room of this door. */
  public final ExpansionRoom firstRoom;

  /** The second room of this door. */
  public final ExpansionRoom secondRoom;

  /** The dimension of a door may be 1 or 2. */
  public final int dimension;

  /** each section of the following array can be expanded separately by the maze search algorithm */
  MazeSearchElement[] sectionArr;

  /** Creates a new instance of ExpansionDoor */
  public ExpansionDoor(ExpansionRoom p_first_room, ExpansionRoom p_second_room, int p_dimension) {
    firstRoom = p_first_room;
    secondRoom = p_second_room;
    dimension = p_dimension;
  }

  /** Creates a new instance of ExpansionDoor */
  public ExpansionDoor(ExpansionRoom p_first_room, ExpansionRoom p_second_room) {
    firstRoom = p_first_room;
    secondRoom = p_second_room;
    dimension = firstRoom.getShape().intersection(secondRoom.getShape()).dimension();
  }

  /** Calculates the intersection of the shapes of the 2 rooms belonging to this door. */
  @Override
  public TileShape getShape() {
    TileShape firstShape = firstRoom.getShape();
    TileShape secondShape = secondRoom.getShape();
    return firstShape.intersection(secondShape);
  }

  /**
   * The dimension of a door may be 1 or 2. 2-dimensional doors can only exist between
   * ObstacleExpansionRooms
   */
  @Override
  public int getDimension() {
    return this.dimension;
  }

  /**
   * Returns the other room of this door, or null, if p_room is neither equal to this.firstRoom nor
   * to this.secondRoom.
   */
  public ExpansionRoom otherRoom(ExpansionRoom p_room) {
    ExpansionRoom result;
    if (p_room == firstRoom) {
      result = secondRoom;
    } else if (p_room == secondRoom) {
      result = firstRoom;
    } else {
      result = null;
    }
    return result;
  }

  /**
   * Returns the other room of this door, or null, if p_room is neither equal to this.firstRoom nor
   * to this.secondRoom, or if the other room is not a CompleteExpansionRoom.
   */
  @Override
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom p_room) {
    ExpansionRoom result;
    if (p_room == firstRoom) {
      result = secondRoom;
    } else if (p_room == secondRoom) {
      result = firstRoom;
    } else {
      result = null;
    }
    if (!(result instanceof CompleteExpansionRoom)) {
      result = null;
    }
    return (CompleteExpansionRoom) result;
  }

  @Override
  public int mazeSearchElementCount() {
    return this.sectionArr.length;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int p_no) {
    return this.sectionArr[p_no];
  }

  /** Calculates the Line segments of the sections of this door. */
  public FloatLine[] getSectionSegments(double p_offset) {
    double offset = p_offset + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
    TileShape doorShape = this.getShape();
    {
      if (doorShape.isEmpty()) {
        return new FloatLine[0];
      }
    }
    FloatLine doorLineSegment;
    FloatLine shrinkedLineSegment;
    if (this.dimension == 1) {
      doorLineSegment = doorShape.diagonalCornerSegment();
      shrinkedLineSegment = doorLineSegment.shrinkSegment(offset);
    } else if (this.dimension == 2
        && this.firstRoom instanceof CompleteFreeSpaceExpansionRoom
        && this.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
      // Overlapping doors at a corner possible in case of 90- or 45-degree routing.
      // In case of freeangle routing the corners are cut off.
      doorLineSegment = calcDoorLineSegment(doorShape);
      if (doorLineSegment == null) {
        // CompleteFreeSpaceExpansionRoom inside other room
        return new FloatLine[0];
      }
      if (doorLineSegment.b.distanceSquare(doorLineSegment.a) < 4 * offset * offset) {
        // door is small, 2 dimensional small doors are not yet expanded.
        return new FloatLine[0];
      }
      shrinkedLineSegment = doorLineSegment.shrinkSegment(offset);
    } else {
      FloatPoint gravityPoint = doorShape.centreOfGravity();
      doorLineSegment = new FloatLine(gravityPoint, gravityPoint);
      shrinkedLineSegment = doorLineSegment;
    }
    final double cMaxDoorSectionWidth = 10 * offset;
    int sectionCount =
        (int) (doorLineSegment.b.distance(doorLineSegment.a) / cMaxDoorSectionWidth) + 1;
    this.allocateSections(sectionCount);
    return shrinkedLineSegment.divideSegmentIntoSections(sectionCount);
  }

  /**
   * Calculates a diagonal line of the 2-dimensional p_door_shape which represents the restraint
   * line between the shapes of this.firstRoom and this.secondRoom.
   */
  private FloatLine calcDoorLineSegment(TileShape p_door_shape) {
    TileShape firstRoomShape = this.firstRoom.getShape();
    TileShape secondRoomShape = this.secondRoom.getShape();
    Point firstCorner = null;
    Point secondCorner = null;
    int cornerCount = p_door_shape.borderLineCount();
    for (int i = 0; i < cornerCount; i++) {
      Point currCorner = p_door_shape.corner(i);
      if (!firstRoomShape.containsInside(currCorner)
          && !secondRoomShape.containsInside(currCorner)) {
        // currCorner is on the border of both room shapes.
        if (firstCorner == null) {
          firstCorner = currCorner;
        } else if (!firstCorner.equals(currCorner)) {
          secondCorner = currCorner;
          break;
        }
      }
    }
    if (firstCorner == null || secondCorner == null) {
      return null;
    }
    return new FloatLine(firstCorner.toFloat(), secondCorner.toFloat());
  }

  /** Resets this ExpandableObject for autorouting the next connection. */
  @Override
  public void reset() {
    if (sectionArr != null) {
      for (MazeSearchElement currSection : sectionArr) {
        currSection.reset();
      }
    }
  }

  @Override
  public int getIdNo() {
    int id1 = firstRoom.getIdNo();
    int id2 = secondRoom.getIdNo();
    // Use a stable combination of room IDs. Note: min/max ensures order-independence.
    return Math.min(id1, id2) * 31 + Math.max(id1, id2);
  }

  /** allocates and initialises p_section_count sections */
  void allocateSections(int p_section_count) {
    if (sectionArr != null && sectionArr.length == p_section_count) {
      return; // already allocated
    }
    sectionArr = new MazeSearchElement[p_section_count];
    for (int i = 0; i < sectionArr.length; i++) {
      sectionArr[i] = new MazeSearchElement();
    }
  }
}
