package app.freerouting.datastructures;

import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Side;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates a Delaunay triangulation in the plane for the input objects. The objects in the input
 * list must implement the interface PlanarDelaunayTriangulation.Storable, which consists of the
 * method get_triangulation_corners(). The result can be read by the function get_edge_lines(). The
 * algorithm is from Chapter 9.3. of the book Computational Geometry, Algorithms and Applications
 * from M. de Berg, M. van Kreveld, M Overmars and O Schwarzkopf.
 */
public class PlanarDelaunayTriangulation {

  /**
   * Randum generatur to shuffle the input corners. A fixed seed is used to make the results
   * reproducible.
   */
  private static final int seed = 99;

  private static final Random randomGenerator = new Random(seed);

  /** The structure for searching the triangle containing a given input corner. */
  private final TriangleGraph searchGraph;

  /**
   * This list contain the edges of the triangulation, where the start corner and end corner are
   * equal.
   */
  private final Collection<Edge> degenerateEdges;

  /**
   * id numbers are for implementing an ordering on the Edges so that they can be used in a set for
   * example
   */
  private int lastEdgeIdNo;

  /** Creates a new instance of PlanarDelaunayTriangulation from p_object_list. */
  public PlanarDelaunayTriangulation(
      Collection<PlanarDelaunayTriangulation.Storable> p_object_list) {
    List<Corner> cornerList = new LinkedList<>();
    for (PlanarDelaunayTriangulation.Storable currObject : p_object_list) {
      Point[] currCorners = currObject.get_triangulation_corners();
      for (Point currCorner : currCorners) {
        cornerList.add(new Corner(currObject, currCorner));
      }
    }

    // create a random permutation of the corners.
    // use a fixed seed to get reproducible result
    randomGenerator.setSeed(seed);
    Collections.shuffle(cornerList, randomGenerator);

    // create a big triangle containing all corners in the list to start with.

    int boundingCoor = Limits.CRIT_INT;
    Corner[] boundingCorners = new Corner[3];
    boundingCorners[0] = new Corner(null, new IntPoint(boundingCoor, 0));
    boundingCorners[1] = new Corner(null, new IntPoint(0, boundingCoor));
    boundingCorners[2] = new Corner(null, new IntPoint(-boundingCoor, -boundingCoor));

    Edge[] edgeLines = new Edge[3];
    for (int i = 0; i < 2; i++) {
      edgeLines[i] = new Edge(boundingCorners[i], boundingCorners[i + 1]);
    }
    edgeLines[2] = new Edge(boundingCorners[2], boundingCorners[0]);

    Triangle startTriangle = new Triangle(edgeLines, null);

    // Set the left triangle of the edge lines to startTriangle.
    // The right triangles remains null.
    for (Edge currEdge : edgeLines) {
      currEdge.set_left_triangle(startTriangle);
    }

    // Initialize the search graph.

    this.searchGraph = new TriangleGraph(startTriangle);
    this.degenerateEdges = new LinkedList<>();

    // Insert the corners in the corner list into the search graph.

    for (Corner currCorner : cornerList) {
      Triangle triangleToSplit = this.searchGraph.position_locate(currCorner);
      this.split(triangleToSplit, currCorner);
    }
  }

  /** Returns all edge lines of the result of the Delaunay Triangulation. */
  public Collection<ResultEdge> get_edge_lines() {
    Collection<ResultEdge> result = new LinkedList<>();
    for (Edge currEdge : this.degenerateEdges) {
      result.add(
          new ResultEdge(
              currEdge.startCorner.coor,
              currEdge.startCorner.object,
              currEdge.endCorner.coor,
              currEdge.endCorner.object));
    }
    if (this.searchGraph.anchor != null) {
      Set<Edge> resultEdges = new TreeSet<>();
      this.searchGraph.anchor.get_leaf_edges(resultEdges);
      for (Edge currEdge : resultEdges) {
        result.add(
            new ResultEdge(
                currEdge.startCorner.coor,
                currEdge.startCorner.object,
                currEdge.endCorner.coor,
                currEdge.endCorner.object));
      }
    }
    return result;
  }

  /**
   * Splits p_triangle into 3 new triangles at p_corner, if p_corner lies in the interior. If
   * p_corner lies on the border, p_triangle and the corresponding neighbour are split into 2 new
   * triangles each at p_corner. If p_corner lies outside this triangle or on a corner, nothing is
   * split. In this case the function returns false.
   */
  private boolean split(Triangle p_triangle, Corner p_corner) {

    // check, if p_corner is in the interior of this triangle or
    // if p_corner is contained in an edge line.

    Edge containingEdge = null;
    for (int i = 0; i < 3; i++) {
      Edge currEdge = p_triangle.edgeLines[i];
      Side currSide;
      if (currEdge.leftTriangle == p_triangle) {
        currSide = p_corner.side_of(currEdge.startCorner, currEdge.endCorner);
      } else {
        currSide = p_corner.side_of(currEdge.endCorner, currEdge.startCorner);
      }
      if (currSide == Side.ON_THE_RIGHT) {
        // p_corner is outside this triangle
        FRLogger.warn("PlanarDelaunayTriangulation.split: p_corner is outside");
        return false;
      } else if (currSide == Side.COLLINEAR) {
        if (containingEdge != null) {
          // p_corner is equal to a corner of this triangle

          Corner commonCorner = currEdge.common_corner(containingEdge);
          if (commonCorner == null) {
            FRLogger.warn("PlanarDelaunayTriangulation.split: common corner expected");
            return false;
          }
          if (p_corner.object == commonCorner.object) {
            return false;
          }
          this.degenerateEdges.add(new Edge(p_corner, commonCorner));
          return true;
        }
        containingEdge = currEdge;
      }
    }

    if (containingEdge == null) {
      // split p_triangle into 3 new triangles by adding edges from
      // the corners of  p_triangle to p_corner.

      Triangle[] newTriangles = p_triangle.split_at_inner_point(p_corner);

      if (newTriangles == null) {
        return false;
      }

      for (Triangle curr_triangle : newTriangles) {
        this.searchGraph.insert(curr_triangle, p_triangle);
      }

      for (int i = 0; i < 3; i++) {
        legalize_edge(p_corner, p_triangle.edgeLines[i]);
      }

    } else {
      // split this triangle and the neighbour triangle into 4 new triangles by adding edges from
      // the corners of the triangles to p_corner.

      Triangle neighbourToSplit = containingEdge.other_neighbour(p_triangle);

      Triangle[] newTriangles = p_triangle.split_at_border_point(p_corner, neighbourToSplit);
      if (newTriangles == null) {
        return false;
      }

      // There are exact four new triangles with the first 2 dividing p_triangle and
      // the last 2 dividing neighbourToSplit.
      this.searchGraph.insert(newTriangles[0], p_triangle);
      this.searchGraph.insert(newTriangles[1], p_triangle);
      this.searchGraph.insert(newTriangles[2], neighbourToSplit);
      this.searchGraph.insert(newTriangles[3], neighbourToSplit);

      for (int i = 0; i < 3; i++) {
        Edge currEdge = p_triangle.edgeLines[i];
        if (currEdge != containingEdge) {
          legalize_edge(p_corner, currEdge);
        }
      }
      for (int i = 0; i < 3; i++) {
        Edge currEdge = neighbourToSplit.edgeLines[i];
        if (currEdge != containingEdge) {
          legalize_edge(p_corner, currEdge);
        }
      }
    }
    return true;
  }

  /**
   * Flips p_edge, if it is no legal edge of the Delaunay Triangulation. p_corner is the last
   * inserted corner of the triangulation Return true, if the triangulation was changed.
   */
  private boolean legalize_edge(Corner p_corner, Edge p_edge) {
    if (p_edge.is_legal()) {
      return false;
    }
    Triangle triangleToChange;
    if (p_edge.leftTriangle.opposite_corner(p_edge) == p_corner) {
      triangleToChange = p_edge.rightTriangle;
    } else if (p_edge.rightTriangle.opposite_corner(p_edge) == p_corner) {
      triangleToChange = p_edge.leftTriangle;
    } else {
      FRLogger.warn("PlanarDelaunayTriangulation.legalize_edge: edge lines inconsistent");
      return false;
    }
    Edge flippedEdge = p_edge.flip();

    // Update the search graph.

    this.searchGraph.insert(flippedEdge.leftTriangle, p_edge.leftTriangle);
    this.searchGraph.insert(flippedEdge.rightTriangle, p_edge.leftTriangle);
    this.searchGraph.insert(flippedEdge.leftTriangle, p_edge.rightTriangle);
    this.searchGraph.insert(flippedEdge.rightTriangle, p_edge.rightTriangle);

    // Call this function recursively for the other edge lines of triangleToChange.
    for (int i = 0; i < 3; i++) {
      Edge currEdge = triangleToChange.edgeLines[i];
      if (currEdge != p_edge) {
        legalize_edge(p_corner, currEdge);
      }
    }
    return true;
  }

  /** Checks the consistency of the triangles in this triangulation. Used for debugging purposes. */
  public boolean validate() {
    boolean result = this.searchGraph.anchor.validate();
    if (result) {
      FRLogger.warn("Delaunay triangulation check passed ok");
    } else {
      FRLogger.warn("Delaunay triangulation check has detected problems");
    }
    return result;
  }

  /** Creates a new unique edge id number. */
  private int new_edge_id_no() {
    ++this.lastEdgeIdNo;
    return this.lastEdgeIdNo;
  }

  /** Interface with functionality required for objects to be used in a planar triangulation. */
  public interface Storable {

    /** Returns an array of corners, which can be used in a planar triangulation. */
    Point[] get_triangulation_corners();
  }

  /** Describes a line segment in the result of the Delaunay Triangulation. */
  public static final class ResultEdge {

    /** The start point of the line segment */
    public final Point startPoint;

    /** The object at the start point of the line segment */
    public final PlanarDelaunayTriangulation.Storable startObject;

    /** The end point of the line segment */
    public final Point endPoint;

    /** The object at the end point of the line segment */
    public final PlanarDelaunayTriangulation.Storable endObject;

    private ResultEdge(
        Point p_start_point,
        PlanarDelaunayTriangulation.Storable p_start_object,
        Point p_end_point,
        PlanarDelaunayTriangulation.Storable p_end_object) {
      startPoint = p_start_point;
      startObject = p_start_object;
      endPoint = p_end_point;
      endObject = p_end_object;
    }
  }

  /** Contains a corner point together with the objects this corner belongs to. */
  private static class Corner {

    public final PlanarDelaunayTriangulation.Storable object;
    public final Point coor;

    public Corner(PlanarDelaunayTriangulation.Storable p_object, Point p_coor) {
      object = p_object;
      coor = p_coor;
    }

    /**
     * The function returns Side.ON_THE_LEFT, if this corner is on the left of the line from p_1 to
     * p_2; Side.ON_THE_RIGHT, if this corner is on the right of the line from p_1 to p_2; and
     * Side.COLLINEAR, if this corner is collinear with p_1 and p_2.
     */
    public Side side_of(Corner p_1, Corner p_2) {
      return this.coor.side_of(p_1.coor, p_2.coor);
    }
  }

  /**
   * Directed acyclic graph for finding the triangle containing a search point p. The leaves contain
   * the triangles of the current triangulation. The internal nodes are triangles, that were part of
   * the triangulation at some earlier stage, but have been replaced their children.
   */
  private static class TriangleGraph {

    private Triangle anchor;

    public TriangleGraph(Triangle p_triangle) {
      if (p_triangle != null) {
        insert(p_triangle, null);
      } else {
        this.anchor = null;
      }
    }

    public void insert(Triangle p_triangle, Triangle p_parent) {
      p_triangle.initialize_is_on_the_left_of_edge_line_array();
      if (p_parent == null) {
        anchor = p_triangle;
      } else {
        p_parent.children.add(p_triangle);
      }
    }

    /**
     * Search for the leaf triangle containing p_corner. It will not be unique, if p_corner lies on
     * a triangle edge.
     */
    public Triangle position_locate(Corner p_corner) {
      if (this.anchor == null) {
        return null;
      }
      if (this.anchor.children.isEmpty()) {
        return this.anchor;
      }
      for (Triangle curr_child : this.anchor.children) {
        Triangle result = position_locate_reku(p_corner, curr_child);
        if (result != null) {
          return result;
        }
      }
      FRLogger.warn("TriangleGraph.position_locate: containing triangle not found");
      return null;
    }

    /** Recursive part of position_locate. */
    private Triangle position_locate_reku(Corner p_corner, Triangle p_triangle) {
      if (!p_triangle.contains(p_corner)) {
        return null;
      }

      if (p_triangle.is_leaf()) {
        return p_triangle;
      }
      for (Triangle curr_child : p_triangle.children) {
        Triangle result = position_locate_reku(p_corner, curr_child);
        if (result != null) {
          return result;
        }
      }
      FRLogger.warn("TriangleGraph.position_locate_reku: containing triangle not found");
      return null;
    }
  }

  /**
   * Describes an edge between two triangles in the triangulation. The unique id_nos are for making
   * edges comparable.
   */
  private class Edge implements Comparable<Edge> {

    public final Corner startCorner;
    public final Corner endCorner;

    /** The unique id number of this triangle. */
    private final int idNo;

    /** The triangle on the left side of this edge. */
    private Triangle leftTriangle;

    /** The triangle on the right side of this edge. */
    private Triangle rightTriangle;

    public Edge(Corner p_start_corner, Corner p_end_corner) {
      startCorner = p_start_corner;
      endCorner = p_end_corner;
      idNo = new_edge_id_no();
    }

    @Override
    public int compareTo(Edge p_other) {
      return this.idNo - p_other.idNo;
    }

    public Triangle get_left_triangle() {
      return leftTriangle;
    }

    public void set_left_triangle(Triangle p_triangle) {
      leftTriangle = p_triangle;
    }

    public Triangle get_right_triangle() {
      return rightTriangle;
    }

    public void set_right_triangle(Triangle p_triangle) {
      rightTriangle = p_triangle;
    }

    /** Returns the common corner of this edge and p_other, or null, if no common corner exists. */
    public Corner common_corner(Edge p_other) {
      Corner result = null;
      if (p_other.startCorner.equals(this.startCorner)
          || p_other.endCorner.equals(this.startCorner)) {
        result = this.startCorner;
      } else if (p_other.startCorner.equals(this.endCorner)
          || p_other.endCorner.equals(this.endCorner)) {
        result = this.endCorner;
      }
      return result;
    }

    /**
     * Returns the neighbour triangle of this edge, which is different from p_triangle. If
     * p_triangle is not a neighbour of this edge, null is returned.
     */
    public Triangle other_neighbour(Triangle p_triangle) {
      Triangle result;
      if (p_triangle == this.leftTriangle) {
        result = this.rightTriangle;
      } else if (p_triangle == this.rightTriangle) {
        result = this.leftTriangle;
      } else {
        FRLogger.warn("Edge.other_neighbour: inconsistent neighbour triangle");
        result = null;
      }
      return result;
    }

    /** Returns true, if this is a legal edge of the Delaunay Triangulation. */
    public boolean is_legal() {
      if (this.leftTriangle == null || this.rightTriangle == null) {
        return true;
      }
      Corner leftOppositeCorner = this.leftTriangle.opposite_corner(this);
      Corner rightOppositeCorner = this.rightTriangle.opposite_corner(this);

      boolean insideCircle =
          rightOppositeCorner
              .coor
              .to_float()
              .inside_circle(
                  this.startCorner.coor.to_float(),
                  leftOppositeCorner.coor.to_float(),
                  this.endCorner.coor.to_float());
      return !insideCircle;
    }

    /**
     * Flips this edge line to the edge line between the opposite corners of the adjacent triangles.
     * Returns the new constructed Edge.
     */
    public Edge flip() {
      // Create the flipped edge, so that the start corner of this edge is on the left
      // and the end corner of this edge on the right.
      Edge flippedEdge =
          new Edge(
              this.rightTriangle.opposite_corner(this), this.leftTriangle.opposite_corner(this));

      Triangle firstParent = this.leftTriangle;

      // Calculate the index of this edge line in the left and right adjacent triangles.

      int leftIndex = -1;
      int rightIndex = -1;
      for (int i = 0; i < 3; i++) {
        if (this.leftTriangle.edgeLines[i] == this) {
          leftIndex = i;
        }
        if (this.rightTriangle.edgeLines[i] == this) {
          rightIndex = i;
        }
      }
      if (leftIndex < 0 || rightIndex < 0) {
        FRLogger.warn("Edge.flip: edge line inconsistent");
        return null;
      }
      Edge leftPrevEdge = this.leftTriangle.edgeLines[(leftIndex + 2) % 3];
      Edge leftNextEdge = this.leftTriangle.edgeLines[(leftIndex + 1) % 3];
      Edge rightPrevEdge = this.rightTriangle.edgeLines[(rightIndex + 2) % 3];
      Edge rightNextEdge = this.rightTriangle.edgeLines[(rightIndex + 1) % 3];

      // Create the left triangle of the flipped edge.

      Edge[] currEdgeLines = new Edge[3];
      currEdgeLines[0] = flippedEdge;
      currEdgeLines[1] = leftPrevEdge;
      currEdgeLines[2] = rightNextEdge;
      Triangle newLeftTriangle = new Triangle(currEdgeLines, firstParent);
      flippedEdge.leftTriangle = newLeftTriangle;
      if (leftPrevEdge.leftTriangle == this.leftTriangle) {
        leftPrevEdge.leftTriangle = newLeftTriangle;
      } else {
        leftPrevEdge.rightTriangle = newLeftTriangle;
      }
      if (rightNextEdge.leftTriangle == this.rightTriangle) {
        rightNextEdge.leftTriangle = newLeftTriangle;
      } else {
        rightNextEdge.rightTriangle = newLeftTriangle;
      }

      // Create the right triangle of the flipped edge.

      currEdgeLines = new Edge[3];
      currEdgeLines[0] = flippedEdge;
      currEdgeLines[1] = rightPrevEdge;
      currEdgeLines[2] = leftNextEdge;
      Triangle newRightTriangle = new Triangle(currEdgeLines, firstParent);
      flippedEdge.rightTriangle = newRightTriangle;
      if (rightPrevEdge.leftTriangle == this.rightTriangle) {
        rightPrevEdge.leftTriangle = newRightTriangle;
      } else {
        rightPrevEdge.rightTriangle = newRightTriangle;
      }
      if (leftNextEdge.leftTriangle == this.leftTriangle) {
        leftNextEdge.leftTriangle = newRightTriangle;
      } else {
        leftNextEdge.rightTriangle = newRightTriangle;
      }

      return flippedEdge;
    }

    /** Checks the consistency of this edge in its database. Used for debugging purposes. */
    public boolean validate() {
      boolean result = true;
      if (this.leftTriangle == null) {
        if (this.startCorner.object != null || this.endCorner.object != null) {
          FRLogger.warn("Edge.validate: left triangle may be null only for bounding edges");
          result = false;
        }
      } else {
        // check if the left triangle contains this edge
        boolean found = false;
        for (int i = 0; i < 3; i++) {
          if (leftTriangle.edgeLines[i] == this) {
            found = true;
            break;
          }
        }
        if (!found) {
          FRLogger.warn("Edge.validate: left triangle does not contain this edge");
          result = false;
        }
      }
      if (this.rightTriangle == null) {
        if (this.startCorner.object != null || this.endCorner.object != null) {
          FRLogger.warn("Edge.validate: right triangle may be null only for bounding edges");
          result = false;
        }
      } else {
        // check if the left triangle contains this edge
        boolean found = false;
        for (int i = 0; i < 3; i++) {
          if (rightTriangle.edgeLines[i] == this) {
            found = true;
            break;
          }
        }
        if (!found) {
          FRLogger.warn("Edge.validate: right triangle does not contain this edge");
          result = false;
        }
      }

      return result;
    }
  }

  /**
   * Describes a triangle in the triangulation. edgeLines ia an array of dimension 3. The edge lines
   * arec sorted in counter clock sense around the border of this triangle. The list children points
   * to the children of this triangle, when used as a node in the search graph.
   */
  private class Triangle {

    /** The 3 edge lines of this triangle sorted in counter clock sense around the border. */
    private final Edge[] edgeLines;

    /**
     * Triangles resulting from an edge flip have 2 parents, all other triangles have 1 parent.
     * first parent is used when traversing the graph sequentially to avoid visiting children nodes
     * more than once.
     */
    private final Triangle firstParent;

    /** The children of this triangle when used as a node in the triangle search graph. */
    private final Collection<Triangle> children;

    /**
     * Indicates, if this triangle is on the left of the i-th edge line for i = 0 to 2. Must be set,
     * if this triangle is an inner node because leftTriangle and rightTriangle of edge lines point
     * only to leaf nodes.
     */
    private boolean[] isOnTheLeftOfEdgeLine;

    public Triangle(Edge[] p_edge_lines, Triangle p_first_parent) {
      this.edgeLines = p_edge_lines;
      // create an empty list for the children.
      this.children = new LinkedList<>();
      this.firstParent = p_first_parent;
    }

    /** Returns true, if this triangle node is a leaf, and false, if it is an inner node. */
    public boolean is_leaf() {
      return this.children.isEmpty();
    }

    /** Gets the corner with index p_no. */
    public Corner get_corner(int p_no) {
      if (p_no < 0 || p_no >= 3) {
        FRLogger.warn("Triangle.get_corner: p_no out of range");
        return null;
      }
      Edge currEdge = edgeLines[p_no];
      Corner result;
      if (currEdge.leftTriangle == this) {
        result = currEdge.startCorner;
      } else if (currEdge.rightTriangle == this) {
        result = currEdge.endCorner;
      } else {
        FRLogger.warn("Triangle.get_corner: inconsistent edge lines");
        result = null;
      }
      return result;
    }

    /**
     * Calculates the opposite corner of this triangle to p_edge_line. Returns null, if p_edge_line
     * is nor an edge line of this triangle.
     */
    public Corner opposite_corner(Edge p_edge_line) {
      int edgeLineNo = -1;
      for (int i = 0; i < 3; i++) {
        if (this.edgeLines[i] == p_edge_line) {
          edgeLineNo = i;
          break;
        }
      }
      if (edgeLineNo < 0) {
        FRLogger.warn("Triangle.opposite_corner: p_edge_line not found");
        return null;
      }
      Edge nextEdge = this.edgeLines[(edgeLineNo + 1) % 3];
      Corner result;
      if (nextEdge.leftTriangle == this) {
        result = nextEdge.endCorner;
      } else {
        result = nextEdge.startCorner;
      }
      return result;
    }

    /** Checks if p_point is inside or on the border of this triangle. */
    public boolean contains(Corner p_corner) {
      if (this.isOnTheLeftOfEdgeLine == null) {
        FRLogger.warn("Triangle.contains: array isOnTheLeftOfEdgeLine not initialized");
        return false;
      }
      for (int i = 0; i < 3; i++) {
        Edge currEdge = this.edgeLines[i];
        Side currSide = p_corner.side_of(currEdge.startCorner, currEdge.endCorner);
        if (this.isOnTheLeftOfEdgeLine[i])
        // checking currEdge.leftTriangle == this instead will not work, if this triangle is an
        // inner node.
        {
          if (currSide == Side.ON_THE_RIGHT) {
            return false;
          }
        } else {
          if (currSide == Side.ON_THE_LEFT) {
            return false;
          }
        }
      }
      return true;
    }

    /** Puts the edges of all leafs below this node into the list p_result_edges */
    public void get_leaf_edges(Set<Edge> p_result_edges) {
      if (this.is_leaf()) {
        for (int i = 0; i < 3; i++) {
          Edge currEdge = this.edgeLines[i];
          if (currEdge.startCorner.object != null && currEdge.endCorner.object != null) {
            // Skip edges containing a bounding corner.
            p_result_edges.add(currEdge);
          }
        }

      } else {
        for (Triangle curr_child : this.children) {
          if (curr_child.firstParent == this) // to prevent traversing nodes more than once
          {
            curr_child.get_leaf_edges(p_result_edges);
          }
        }
      }
    }

    /**
     * Split this triangle into 3 new triangles by adding edges from the corners of this triangle to
     * p_corner, p_corner has to be located in the interior of this triangle.
     */
    public Triangle[] split_at_inner_point(Corner p_corner) {
      Triangle[] newTriangles = new Triangle[3];

      Edge[] newEdges = new Edge[3];
      for (int i = 0; i < 3; i++) {
        newEdges[i] = new Edge(this.get_corner(i), p_corner);
      }

      // construct the 3 new triangles.
      Edge[] currEdgeLines = new Edge[3];

      currEdgeLines[0] = this.edgeLines[0];
      currEdgeLines[1] = new Edge(this.get_corner(1), p_corner);
      currEdgeLines[2] = new Edge(p_corner, this.get_corner(0));
      newTriangles[0] = new Triangle(currEdgeLines, this);

      currEdgeLines = new Edge[3];
      currEdgeLines[0] = this.edgeLines[1];
      currEdgeLines[1] = new Edge(this.get_corner(2), p_corner);
      currEdgeLines[2] = newTriangles[0].edgeLines[1];
      newTriangles[1] = new Triangle(currEdgeLines, this);

      currEdgeLines = new Edge[3];
      currEdgeLines[0] = this.edgeLines[2];
      currEdgeLines[1] = newTriangles[0].edgeLines[2];
      currEdgeLines[2] = newTriangles[1].edgeLines[1];
      newTriangles[2] = new Triangle(currEdgeLines, this);

      // Set the new neighbour triangles of the edge lines.

      for (int i = 0; i < 3; i++) {
        Edge currEdge = newTriangles[i].edgeLines[0];
        if (currEdge.get_left_triangle() == this) {
          currEdge.set_left_triangle(newTriangles[i]);
        } else {
          currEdge.set_right_triangle(newTriangles[i]);
        }
        // The other neighbour triangle remains valid.
      }

      Edge currEdge = newTriangles[0].edgeLines[1];
      currEdge.set_left_triangle(newTriangles[0]);
      currEdge.set_right_triangle(newTriangles[1]);

      currEdge = newTriangles[1].edgeLines[1];
      currEdge.set_left_triangle(newTriangles[1]);
      currEdge.set_right_triangle(newTriangles[2]);

      currEdge = newTriangles[2].edgeLines[1];
      currEdge.set_left_triangle(newTriangles[0]);
      currEdge.set_right_triangle(newTriangles[2]);
      return newTriangles;
    }

    /**
     * Split this triangle and p_neighbour_to_split into 4 new triangles by adding edges from the
     * corners of the triangles to p_corner. p_corner is assumed to be located on the common edge
     * line of this triangle and p_neighbour_to_split. If that is not true, the function returns
     * null. The first 2 result triangles are from splitting this triangle, and the last 2 result
     * triangles are from splitting p_neighbour_to_split.
     */
    public Triangle[] split_at_border_point(Corner p_corner, Triangle p_neighbour_to_split) {
      Triangle[] newTriangles = new Triangle[4];
      // look for the triangle edge of this and the neighbour triangle containing p_point;
      int thisTouchingEdgeNo = -1;
      int neighbourTouchingEdgeNo = -1;
      Edge touchingEdge = null;
      Edge otherTouchingEdge = null;
      for (int i = 0; i < 3; i++) {
        Edge currEdge = this.edgeLines[i];
        if (p_corner.side_of(currEdge.startCorner, currEdge.endCorner) == Side.COLLINEAR) {
          thisTouchingEdgeNo = i;
          touchingEdge = currEdge;
        }
        currEdge = p_neighbour_to_split.edgeLines[i];
        if (p_corner.side_of(currEdge.startCorner, currEdge.endCorner) == Side.COLLINEAR) {
          neighbourTouchingEdgeNo = i;
          otherTouchingEdge = currEdge;
        }
      }
      if (thisTouchingEdgeNo < 0 || neighbourTouchingEdgeNo < 0) {
        FRLogger.warn("Triangle.split_at_border_point: touching edge not found");
        return null;
      }
      if (touchingEdge != otherTouchingEdge) {
        FRLogger.warn("Triangle.split_at_border_point: edges inconsistent");
        return null;
      }

      Edge firstCommonNewEdge;
      Edge secondCommonNewEdge;
      // Construct the new edge lines that 2 split triangles of this triangle
      // will be on the left side of the new common touching edges.
      if (this == touchingEdge.leftTriangle) {
        firstCommonNewEdge = new Edge(touchingEdge.startCorner, p_corner);
        secondCommonNewEdge = new Edge(p_corner, touchingEdge.endCorner);
      } else {
        firstCommonNewEdge = new Edge(touchingEdge.endCorner, p_corner);
        secondCommonNewEdge = new Edge(p_corner, touchingEdge.startCorner);
      }

      // Construct the first split triangle of this triangle.

      Edge prevEdge = this.edgeLines[(thisTouchingEdgeNo + 2) % 3];
      Edge thisSplittingEdge;
      // construct the splitting edge line of this triangle, so that the first split
      // triangle lies on the left side, and the second split triangle on the right side.
      if (this == prevEdge.leftTriangle) {
        thisSplittingEdge = new Edge(p_corner, prevEdge.startCorner);
      } else {
        thisSplittingEdge = new Edge(p_corner, prevEdge.endCorner);
      }
      Edge[] currEdgeLines = new Edge[3];
      currEdgeLines[0] = prevEdge;
      currEdgeLines[1] = firstCommonNewEdge;
      currEdgeLines[2] = thisSplittingEdge;
      newTriangles[0] = new Triangle(currEdgeLines, this);
      if (this == prevEdge.leftTriangle) {
        prevEdge.set_left_triangle(newTriangles[0]);
      } else {
        prevEdge.set_right_triangle(newTriangles[0]);
      }
      firstCommonNewEdge.set_left_triangle(newTriangles[0]);
      thisSplittingEdge.set_left_triangle(newTriangles[0]);

      // Construct the second split triangle of this triangle.

      Edge nextEdge = this.edgeLines[(thisTouchingEdgeNo + 1) % 3];
      currEdgeLines = new Edge[3];
      currEdgeLines[0] = thisSplittingEdge;
      currEdgeLines[1] = secondCommonNewEdge;
      currEdgeLines[2] = nextEdge;
      newTriangles[1] = new Triangle(currEdgeLines, this);
      thisSplittingEdge.set_right_triangle(newTriangles[1]);
      secondCommonNewEdge.set_left_triangle(newTriangles[1]);
      if (this == nextEdge.leftTriangle) {
        nextEdge.set_left_triangle(newTriangles[1]);
      } else {
        nextEdge.set_right_triangle(newTriangles[1]);
      }

      // construct the first split triangle of p_neighbour_to_split
      nextEdge = p_neighbour_to_split.edgeLines[(neighbourTouchingEdgeNo + 1) % 3];
      Edge neighbourSplittingEdge;
      // construct the splitting edge line of p_neighbour_to_split, so that the first split
      // triangle lies on the left side, and the second split triangle on the right side.
      if (p_neighbour_to_split == nextEdge.leftTriangle) {
        neighbourSplittingEdge = new Edge(nextEdge.endCorner, p_corner);
      } else {
        neighbourSplittingEdge = new Edge(nextEdge.startCorner, p_corner);
      }
      currEdgeLines = new Edge[3];
      currEdgeLines[0] = neighbourSplittingEdge;
      currEdgeLines[1] = firstCommonNewEdge;
      currEdgeLines[2] = nextEdge;
      newTriangles[2] = new Triangle(currEdgeLines, p_neighbour_to_split);
      neighbourSplittingEdge.set_left_triangle(newTriangles[2]);
      firstCommonNewEdge.set_right_triangle(newTriangles[2]);
      if (p_neighbour_to_split == nextEdge.leftTriangle) {
        nextEdge.set_left_triangle(newTriangles[2]);
      } else {
        nextEdge.set_right_triangle(newTriangles[2]);
      }

      // construct the second split triangle of p_neighbour_to_split
      prevEdge = p_neighbour_to_split.edgeLines[(neighbourTouchingEdgeNo + 2) % 3];
      currEdgeLines = new Edge[3];
      currEdgeLines[0] = prevEdge;
      currEdgeLines[1] = secondCommonNewEdge;
      currEdgeLines[2] = neighbourSplittingEdge;
      newTriangles[3] = new Triangle(currEdgeLines, p_neighbour_to_split);
      if (p_neighbour_to_split == prevEdge.leftTriangle) {
        prevEdge.set_left_triangle(newTriangles[3]);
      } else {
        prevEdge.set_right_triangle(newTriangles[3]);
      }
      secondCommonNewEdge.set_right_triangle(newTriangles[3]);
      neighbourSplittingEdge.set_right_triangle(newTriangles[3]);

      return newTriangles;
    }

    /** Checks the consistency of this triangle and its children. Used for debugging purposes. */
    public boolean validate() {
      boolean result = true;
      if (this.is_leaf()) {
        Edge prevEdge = this.edgeLines[2];
        for (int i = 0; i < 3; i++) {
          Edge currEdge = this.edgeLines[i];
          if (!currEdge.validate()) {
            result = false;
          }
          // Check, if the ens corner of the previous line equals to the start corner of this line.
          Corner prevEndCorner;
          if (prevEdge.leftTriangle == this) {
            prevEndCorner = prevEdge.endCorner;
          } else {
            prevEndCorner = prevEdge.startCorner;
          }
          Corner currStartCorner;
          if (currEdge.leftTriangle == this) {
            currStartCorner = currEdge.startCorner;
          } else if (currEdge.rightTriangle == this) {
            currStartCorner = currEdge.endCorner;
          } else {
            FRLogger.warn("Triangle.validate: edge inconsistent");
            return false;
          }
          if (currStartCorner != prevEndCorner) {
            FRLogger.warn("Triangle.validate: corner inconsistent");
            result = false;
          }
          prevEdge = currEdge;
        }
      } else {
        for (Triangle curr_child : this.children) {
          if (curr_child.firstParent == this) // to avoid traversing nodes more than once.
          {
            curr_child.validate();
          }
        }
      }
      return result;
    }

    /**
     * Must be done as long as this triangle node is a leaf and after for all its edge lines the
     * leftTriangle or the rightTriangle reference is set to this triangle.
     */
    private void initialize_is_on_the_left_of_edge_line_array() {
      if (this.isOnTheLeftOfEdgeLine != null) {
        return; // already initialized
      }
      this.isOnTheLeftOfEdgeLine = new boolean[3];
      for (int i = 0; i < 3; i++) {
        this.isOnTheLeftOfEdgeLine[i] = this.edgeLines[i].leftTriangle == this;
      }
    }
  }
}
