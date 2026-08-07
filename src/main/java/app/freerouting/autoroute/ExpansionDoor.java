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
    dimension = firstRoom.get_shape().intersection(secondRoom.get_shape()).dimension();
  }

  /** Calculates the intersection of the shapes of the 2 rooms belonging to this door. */
  @Override
  public TileShape get_shape() {
    TileShape firstShape = firstRoom.get_shape();
    TileShape secondShape = secondRoom.get_shape();
    return firstShape.intersection(secondShape);
  }

  /**
   * The dimension of a door may be 1 or 2. 2-dimensional doors can only exist between
   * ObstacleExpansionRooms
   */
  @Override
  public int get_dimension() {
    return this.dimension;
  }

  /**
   * Returns the other room of this door, or null, if p_room is neither equal to this.firstRoom nor
   * to this.secondRoom.
   */
  public ExpansionRoom other_room(ExpansionRoom p_room) {
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
  public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room) {
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
  public int maze_search_element_count() {
    return this.sectionArr.length;
  }

  @Override
  public MazeSearchElement get_maze_search_element(int p_no) {
    return this.sectionArr[p_no];
  }

  /** Calculates the Line segments of the sections of this door. */
  public FloatLine[] get_section_segments(double p_offset) {
    double offset = p_offset + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
    TileShape doorShape = this.get_shape();
    {
      if (doorShape.is_empty()) {
        return new FloatLine[0];
      }
    }
    FloatLine doorLineSegment;
    FloatLine shrinkedLineSegment;
    if (this.dimension == 1) {
      doorLineSegment = doorShape.diagonal_corner_segment();
      shrinkedLineSegment = doorLineSegment.shrink_segment(offset);
    } else if (this.dimension == 2
        && this.firstRoom instanceof CompleteFreeSpaceExpansionRoom
        && this.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
      // Overlapping doors at a corner possible in case of 90- or 45-degree routing.
      // In case of freeangle routing the corners are cut off.
      doorLineSegment = calc_door_line_segment(doorShape);
      if (doorLineSegment == null) {
        // CompleteFreeSpaceExpansionRoom inside other room
        return new FloatLine[0];
      }
      if (doorLineSegment.b.distance_square(doorLineSegment.a) < 4 * offset * offset) {
        // door is small, 2 dimensional small doors are not yet expanded.
        return new FloatLine[0];
      }
      shrinkedLineSegment = doorLineSegment.shrink_segment(offset);
    } else {
      FloatPoint gravityPoint = doorShape.centre_of_gravity();
      doorLineSegment = new FloatLine(gravityPoint, gravityPoint);
      shrinkedLineSegment = doorLineSegment;
    }
    final double cMaxDoorSectionWidth = 10 * offset;
    int sectionCount =
        (int) (doorLineSegment.b.distance(doorLineSegment.a) / cMaxDoorSectionWidth) + 1;
    this.allocate_sections(sectionCount);
    return shrinkedLineSegment.divide_segment_into_sections(sectionCount);
  }

  /**
   * Calculates a diagonal line of the 2-dimensional p_door_shape which represents the restraint
   * line between the shapes of this.firstRoom and this.secondRoom.
   */
  private FloatLine calc_door_line_segment(TileShape p_door_shape) {
    TileShape firstRoomShape = this.firstRoom.get_shape();
    TileShape secondRoomShape = this.secondRoom.get_shape();
    Point firstCorner = null;
    Point secondCorner = null;
    int cornerCount = p_door_shape.border_line_count();
    for (int i = 0; i < cornerCount; i++) {
      Point currCorner = p_door_shape.corner(i);
      if (!firstRoomShape.contains_inside(currCorner)
          && !secondRoomShape.contains_inside(currCorner)) {
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
    return new FloatLine(firstCorner.to_float(), secondCorner.to_float());
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
  public int get_id_no() {
    int id1 = firstRoom.get_id_no();
    int id2 = secondRoom.get_id_no();
    // Use a stable combination of room IDs. Note: min/max ensures order-independence.
    return Math.min(id1, id2) * 31 + Math.max(id1, id2);
  }

  /** allocates and initialises p_section_count sections */
  void allocate_sections(int p_section_count) {
    if (sectionArr != null && sectionArr.length == p_section_count) {
      return; // already allocated
    }
    sectionArr = new MazeSearchElement[p_section_count];
    for (int i = 0; i < sectionArr.length; i++) {
      sectionArr[i] = new MazeSearchElement();
    }
  }
}
