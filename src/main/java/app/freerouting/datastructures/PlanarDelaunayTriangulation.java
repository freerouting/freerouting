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
   * Id numbers are for implementing an ordering on the Edges so that they can be used in a set for
   * example.
   */
  private int lastEdgeIdNo;

  /** Creates a new instance of PlanarDelaunayTriangulation from objectList. */
  public PlanarDelaunayTriangulation(Collection<PlanarDelaunayTriangulation.Storable> objectList) {
    List<Corner> cornerList = new LinkedList<>();
    for (PlanarDelaunayTriangulation.Storable currentObject : objectList) {
      Point[] currentCorners = currentObject.getTriangulationCorners();
      for (Point currentCorner : currentCorners) {
        cornerList.add(new Corner(currentObject, currentCorner));
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
    for (Edge currentEdge : edgeLines) {
      currentEdge.setLeftTriangle(startTriangle);
    }

    // Initialize the search graph.

    this.searchGraph = new TriangleGraph(startTriangle);
    this.degenerateEdges = new LinkedList<>();

    // Insert the corners in the corner list into the search graph.

    for (Corner currentCorner : cornerList) {
      Triangle triangleToSplit = this.searchGraph.positionLocate(currentCorner);
      this.split(triangleToSplit, currentCorner);
    }
  }

  /** Returns all edge lines of the result of the Delaunay Triangulation. */
  public Collection<ResultEdge> getEdgeLines() {
    Collection<ResultEdge> result = new LinkedList<>();
    for (Edge currentEdge : this.degenerateEdges) {
      result.add(
          new ResultEdge(
              currentEdge.startCorner.coor,
              currentEdge.startCorner.object,
              currentEdge.endCorner.coor,
              currentEdge.endCorner.object));
    }
    if (this.searchGraph.anchor != null) {
      Set<Edge> resultEdges = new TreeSet<>();
      this.searchGraph.anchor.getLeafEdges(resultEdges);
      for (Edge currentEdge : resultEdges) {
        result.add(
            new ResultEdge(
                currentEdge.startCorner.coor,
                currentEdge.startCorner.object,
                currentEdge.endCorner.coor,
                currentEdge.endCorner.object));
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
  private boolean split(Triangle triangle, Corner corner) {

    // check, if corner is in the interior of this triangle or
    // if corner is contained in an edge line.

    Edge containingEdge = null;
    for (int i = 0; i < 3; i++) {
      Edge currentEdge = triangle.edgeLines[i];
      Side currentSide;
      if (currentEdge.leftTriangle == triangle) {
        currentSide = corner.sideOf(currentEdge.startCorner, currentEdge.endCorner);
      } else {
        currentSide = corner.sideOf(currentEdge.endCorner, currentEdge.startCorner);
      }
      if (currentSide == Side.ON_THE_RIGHT) {
        // corner is outside this triangle
        FRLogger.warn("PlanarDelaunayTriangulation.split: p_corner is outside");
        return false;
      } else if (currentSide == Side.COLLINEAR) {
        if (containingEdge != null) {
          // corner is equal to a corner of this triangle

          Corner commonCorner = currentEdge.commonCorner(containingEdge);
          if (commonCorner == null) {
            FRLogger.warn("PlanarDelaunayTriangulation.split: common corner expected");
            return false;
          }
          if (corner.object == commonCorner.object) {
            return false;
          }
          this.degenerateEdges.add(new Edge(corner, commonCorner));
          return true;
        }
        containingEdge = currentEdge;
      }
    }

    if (containingEdge == null) {
      // split triangle into 3 new triangles by adding edges from
      // the corners of  triangle to corner.

      Triangle[] newTriangles = triangle.splitAtInnerPoint(corner);

      if (newTriangles == null) {
        return false;
      }

      for (Triangle currentTriangle : newTriangles) {
        this.searchGraph.insert(currentTriangle, triangle);
      }

      for (int i = 0; i < 3; i++) {
        legalizeEdge(corner, triangle.edgeLines[i]);
      }

    } else {
      // split this triangle and the neighbour triangle into 4 new triangles by adding edges from
      // the corners of the triangles to corner.

      Triangle neighbourToSplit = containingEdge.otherNeighbour(triangle);

      Triangle[] newTriangles = triangle.splitAtBorderPoint(corner, neighbourToSplit);
      if (newTriangles == null) {
        return false;
      }

      // There are exact four new triangles with the first 2 dividing triangle and
      // the last 2 dividing neighbourToSplit.
      this.searchGraph.insert(newTriangles[0], triangle);
      this.searchGraph.insert(newTriangles[1], triangle);
      this.searchGraph.insert(newTriangles[2], neighbourToSplit);
      this.searchGraph.insert(newTriangles[3], neighbourToSplit);

      for (int i = 0; i < 3; i++) {
        Edge currentEdge = triangle.edgeLines[i];
        if (currentEdge != containingEdge) {
          legalizeEdge(corner, currentEdge);
        }
      }
      for (int i = 0; i < 3; i++) {
        Edge currentEdge = neighbourToSplit.edgeLines[i];
        if (currentEdge != containingEdge) {
          legalizeEdge(corner, currentEdge);
        }
      }
    }
    return true;
  }

  /**
   * Flips edge, if it is no legal edge of the Delaunay Triangulation. corner is the last inserted
   * corner of the triangulation Return true, if the triangulation was changed.
   */
  private boolean legalizeEdge(Corner corner, Edge edge) {
    if (edge.isLegal()) {
      return false;
    }
    Triangle triangleToChange;
    if (edge.leftTriangle.oppositeCorner(edge) == corner) {
      triangleToChange = edge.rightTriangle;
    } else if (edge.rightTriangle.oppositeCorner(edge) == corner) {
      triangleToChange = edge.leftTriangle;
    } else {
      FRLogger.warn("PlanarDelaunayTriangulation.legalize_edge: edge lines inconsistent");
      return false;
    }
    Edge flippedEdge = edge.flip();

    // Update the search graph.

    this.searchGraph.insert(flippedEdge.leftTriangle, edge.leftTriangle);
    this.searchGraph.insert(flippedEdge.rightTriangle, edge.leftTriangle);
    this.searchGraph.insert(flippedEdge.leftTriangle, edge.rightTriangle);
    this.searchGraph.insert(flippedEdge.rightTriangle, edge.rightTriangle);

    // Call this function recursively for the other edge lines of triangleToChange.
    for (int i = 0; i < 3; i++) {
      Edge currentEdge = triangleToChange.edgeLines[i];
      if (currentEdge != edge) {
        legalizeEdge(corner, currentEdge);
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
  private int newEdgeIdNo() {
    ++this.lastEdgeIdNo;
    return this.lastEdgeIdNo;
  }

  /** Interface with functionality required for objects to be used in a planar triangulation. */
  public interface Storable {

    /** Returns an array of corners, which can be used in a planar triangulation. */
    Point[] getTriangulationCorners();
  }

  /** Describes a line segment in the result of the Delaunay Triangulation. */
  public static final class ResultEdge {

    /** The start point of the line segment. */
    public final Point startPoint;

    /** The object at the start point of the line segment. */
    public final PlanarDelaunayTriangulation.Storable startObject;

    /** The end point of the line segment. */
    public final Point endPoint;

    /** The object at the end point of the line segment. */
    public final PlanarDelaunayTriangulation.Storable endObject;

    private ResultEdge(
        Point startPoint,
        PlanarDelaunayTriangulation.Storable startObject,
        Point endPoint,
        PlanarDelaunayTriangulation.Storable endObject) {
      this.startPoint = startPoint;
      this.startObject = startObject;
      this.endPoint = endPoint;
      this.endObject = endObject;
    }
  }

  /** Contains a corner point together with the objects this corner belongs to. */
  private static class Corner {

    public final PlanarDelaunayTriangulation.Storable object;
    public final Point coor;

    public Corner(PlanarDelaunayTriangulation.Storable object, Point coor) {
      this.object = object;
      this.coor = coor;
    }

    /**
     * The function returns Side.ON_THE_LEFT, if this corner is on the left of the line from p_1 to
     * p_2; Side.ON_THE_RIGHT, if this corner is on the right of the line from p_1 to p_2; and
     * Side.COLLINEAR, if this corner is collinear with p_1 and p_2.
     */
    public Side sideOf(Corner p1, Corner p2) {
      return this.coor.sideOf(p1.coor, p2.coor);
    }
  }

  /**
   * Directed acyclic graph for finding the triangle containing a search point p. The leaves contain
   * the triangles of the current triangulation. The internal nodes are triangles, that were part of
   * the triangulation at some earlier stage, but have been replaced their children.
   */
  private static class TriangleGraph {

    private Triangle anchor;

    public TriangleGraph(Triangle triangle) {
      if (triangle != null) {
        insert(triangle, null);
      } else {
        this.anchor = null;
      }
    }

    public void insert(Triangle triangle, Triangle parent) {
      triangle.initializeIsOnTheLeftOfEdgeLineArray();
      if (parent == null) {
        anchor = triangle;
      } else {
        parent.children.add(triangle);
      }
    }

    /**
     * Search for the leaf triangle containing corner. It will not be unique, if corner lies on a
     * triangle edge.
     */
    public Triangle positionLocate(Corner corner) {
      if (this.anchor == null) {
        return null;
      }
      if (this.anchor.children.isEmpty()) {
        return this.anchor;
      }
      for (Triangle currentChild : this.anchor.children) {
        Triangle result = positionLocateReku(corner, currentChild);
        if (result != null) {
          return result;
        }
      }
      FRLogger.warn("TriangleGraph.position_locate: containing triangle not found");
      return null;
    }

    /** Recursive part of position_locate. */
    private Triangle positionLocateReku(Corner corner, Triangle triangle) {
      if (!triangle.contains(corner)) {
        return null;
      }

      if (triangle.isLeaf()) {
        return triangle;
      }
      for (Triangle currentChild : triangle.children) {
        Triangle result = positionLocateReku(corner, currentChild);
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

    public Edge(Corner startCorner, Corner endCorner) {
      this.startCorner = startCorner;
      this.endCorner = endCorner;
      idNo = newEdgeIdNo();
    }

    @Override
    public int compareTo(Edge other) {
      return this.idNo - other.idNo;
    }

    public Triangle getLeftTriangle() {
      return leftTriangle;
    }

    public void setLeftTriangle(Triangle triangle) {
      leftTriangle = triangle;
    }

    public Triangle getRightTriangle() {
      return rightTriangle;
    }

    public void setRightTriangle(Triangle triangle) {
      rightTriangle = triangle;
    }

    /** Returns the common corner of this edge and other, or null, if no common corner exists. */
    public Corner commonCorner(Edge other) {
      Corner result = null;
      if (other.startCorner.equals(this.startCorner) || other.endCorner.equals(this.startCorner)) {
        result = this.startCorner;
      } else if (other.startCorner.equals(this.endCorner)
          || other.endCorner.equals(this.endCorner)) {
        result = this.endCorner;
      }
      return result;
    }

    /**
     * Returns the neighbour triangle of this edge, which is different from triangle. If triangle is
     * not a neighbour of this edge, null is returned.
     */
    public Triangle otherNeighbour(Triangle triangle) {
      Triangle result;
      if (triangle == this.leftTriangle) {
        result = this.rightTriangle;
      } else if (triangle == this.rightTriangle) {
        result = this.leftTriangle;
      } else {
        FRLogger.warn("Edge.other_neighbour: inconsistent neighbour triangle");
        result = null;
      }
      return result;
    }

    /** Returns true, if this is a legal edge of the Delaunay Triangulation. */
    public boolean isLegal() {
      if (this.leftTriangle == null || this.rightTriangle == null) {
        return true;
      }
      Corner leftOppositeCorner = this.leftTriangle.oppositeCorner(this);
      Corner rightOppositeCorner = this.rightTriangle.oppositeCorner(this);

      boolean insideCircle =
          rightOppositeCorner
              .coor
              .toFloat()
              .insideCircle(
                  this.startCorner.coor.toFloat(),
                  leftOppositeCorner.coor.toFloat(),
                  this.endCorner.coor.toFloat());
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
          new Edge(this.rightTriangle.oppositeCorner(this), this.leftTriangle.oppositeCorner(this));

      final Triangle firstParent = this.leftTriangle;

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
      final Edge leftPrevEdge = this.leftTriangle.edgeLines[(leftIndex + 2) % 3];
      final Edge leftNextEdge = this.leftTriangle.edgeLines[(leftIndex + 1) % 3];
      final Edge rightPrevEdge = this.rightTriangle.edgeLines[(rightIndex + 2) % 3];
      final Edge rightNextEdge = this.rightTriangle.edgeLines[(rightIndex + 1) % 3];

      // Create the left triangle of the flipped edge.

      Edge[] currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = flippedEdge;
      currentEdgeLines[1] = leftPrevEdge;
      currentEdgeLines[2] = rightNextEdge;
      Triangle newLeftTriangle = new Triangle(currentEdgeLines, firstParent);
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

      currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = flippedEdge;
      currentEdgeLines[1] = rightPrevEdge;
      currentEdgeLines[2] = leftNextEdge;
      Triangle newRightTriangle = new Triangle(currentEdgeLines, firstParent);
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

    public Triangle(Edge[] edgeLines, Triangle firstParent) {
      this.edgeLines = edgeLines;
      // create an empty list for the children.
      this.children = new LinkedList<>();
      this.firstParent = firstParent;
    }

    /** Returns true, if this triangle node is a leaf, and false, if it is an inner node. */
    public boolean isLeaf() {
      return this.children.isEmpty();
    }

    /** Gets the corner with index no. */
    public Corner getCorner(int no) {
      if (no < 0 || no >= 3) {
        FRLogger.warn("Triangle.get_corner: p_no out of range");
        return null;
      }
      Edge currentEdge = edgeLines[no];
      Corner result;
      if (currentEdge.leftTriangle == this) {
        result = currentEdge.startCorner;
      } else if (currentEdge.rightTriangle == this) {
        result = currentEdge.endCorner;
      } else {
        FRLogger.warn("Triangle.get_corner: inconsistent edge lines");
        result = null;
      }
      return result;
    }

    /**
     * Calculates the opposite corner of this triangle to edgeLine. Returns null, if edgeLine is nor
     * an edge line of this triangle.
     */
    public Corner oppositeCorner(Edge edgeLine) {
      int edgeLineNo = -1;
      for (int i = 0; i < 3; i++) {
        if (this.edgeLines[i] == edgeLine) {
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

    /** Checks if point is inside or on the border of this triangle. */
    public boolean contains(Corner corner) {
      if (this.isOnTheLeftOfEdgeLine == null) {
        FRLogger.warn("Triangle.contains: array isOnTheLeftOfEdgeLine not initialized");
        return false;
      }
      for (int i = 0; i < 3; i++) {
        Edge currentEdge = this.edgeLines[i];
        Side currentSide = corner.sideOf(currentEdge.startCorner, currentEdge.endCorner);
        if (this.isOnTheLeftOfEdgeLine[i]) {
          // checking currentEdge.leftTriangle == this instead will not work, if this triangle is an
          // inner node.
          if (currentSide == Side.ON_THE_RIGHT) {
            return false;
          }
        } else {
          if (currentSide == Side.ON_THE_LEFT) {
            return false;
          }
        }
      }
      return true;
    }

    /** Puts the edges of all leafs below this node into the list resultEdges. */
    public void getLeafEdges(Set<Edge> resultEdges) {
      if (this.isLeaf()) {
        for (int i = 0; i < 3; i++) {
          Edge currentEdge = this.edgeLines[i];
          if (currentEdge.startCorner.object != null && currentEdge.endCorner.object != null) {
            // Skip edges containing a bounding corner.
            resultEdges.add(currentEdge);
          }
        }

      } else {
        for (Triangle currentChild : this.children) {
          if (currentChild.firstParent == this) { // to prevent traversing nodes more than once
            currentChild.getLeafEdges(resultEdges);
          }
        }
      }
    }

    /**
     * Split this triangle into 3 new triangles by adding edges from the corners of this triangle to
     * corner, corner has to be located in the interior of this triangle.
     */
    public Triangle[] splitAtInnerPoint(Corner corner) {
      Edge[] newEdges = new Edge[3];
      for (int i = 0; i < 3; i++) {
        newEdges[i] = new Edge(this.getCorner(i), corner);
      }

      final Triangle[] newTriangles = new Triangle[3];

      // construct the 3 new triangles.
      Edge[] currentEdgeLines = new Edge[3];

      currentEdgeLines[0] = this.edgeLines[0];
      currentEdgeLines[1] = new Edge(this.getCorner(1), corner);
      currentEdgeLines[2] = new Edge(corner, this.getCorner(0));
      newTriangles[0] = new Triangle(currentEdgeLines, this);

      currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = this.edgeLines[1];
      currentEdgeLines[1] = new Edge(this.getCorner(2), corner);
      currentEdgeLines[2] = newTriangles[0].edgeLines[1];
      newTriangles[1] = new Triangle(currentEdgeLines, this);

      currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = this.edgeLines[2];
      currentEdgeLines[1] = newTriangles[0].edgeLines[2];
      currentEdgeLines[2] = newTriangles[1].edgeLines[1];
      newTriangles[2] = new Triangle(currentEdgeLines, this);

      // Set the new neighbour triangles of the edge lines.

      for (int i = 0; i < 3; i++) {
        Edge currentEdge = newTriangles[i].edgeLines[0];
        if (currentEdge.getLeftTriangle() == this) {
          currentEdge.setLeftTriangle(newTriangles[i]);
        } else {
          currentEdge.setRightTriangle(newTriangles[i]);
        }
        // The other neighbour triangle remains valid.
      }

      Edge currentEdge = newTriangles[0].edgeLines[1];
      currentEdge.setLeftTriangle(newTriangles[0]);
      currentEdge.setRightTriangle(newTriangles[1]);

      currentEdge = newTriangles[1].edgeLines[1];
      currentEdge.setLeftTriangle(newTriangles[1]);
      currentEdge.setRightTriangle(newTriangles[2]);

      currentEdge = newTriangles[2].edgeLines[1];
      currentEdge.setLeftTriangle(newTriangles[0]);
      currentEdge.setRightTriangle(newTriangles[2]);
      return newTriangles;
    }

    /**
     * Split this triangle and neighbourToSplit into 4 new triangles by adding edges from the
     * corners of the triangles to corner. corner is assumed to be located on the common edge line
     * of this triangle and neighbourToSplit. If that is not true, the function returns null. The
     * first 2 result triangles are from splitting this triangle, and the last 2 result triangles
     * are from splitting neighbourToSplit.
     */
    public Triangle[] splitAtBorderPoint(Corner corner, Triangle neighbourToSplit) {
      // look for the triangle edge of this and the neighbour triangle containing corner;
      int thisTouchingEdgeNo = -1;
      int neighbourTouchingEdgeNo = -1;
      Edge touchingEdge = null;
      Edge otherTouchingEdge = null;
      for (int i = 0; i < 3; i++) {
        Edge currentEdge = this.edgeLines[i];
        if (corner.sideOf(currentEdge.startCorner, currentEdge.endCorner) == Side.COLLINEAR) {
          thisTouchingEdgeNo = i;
          touchingEdge = currentEdge;
        }
        currentEdge = neighbourToSplit.edgeLines[i];
        if (corner.sideOf(currentEdge.startCorner, currentEdge.endCorner) == Side.COLLINEAR) {
          neighbourTouchingEdgeNo = i;
          otherTouchingEdge = currentEdge;
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

      final Triangle[] newTriangles = new Triangle[4];

      Edge firstCommonNewEdge;
      Edge secondCommonNewEdge;
      // Construct the new edge lines that 2 split triangles of this triangle
      // will be on the left side of the new common touching edges.
      if (this == touchingEdge.leftTriangle) {
        firstCommonNewEdge = new Edge(touchingEdge.startCorner, corner);
        secondCommonNewEdge = new Edge(corner, touchingEdge.endCorner);
      } else {
        firstCommonNewEdge = new Edge(touchingEdge.endCorner, corner);
        secondCommonNewEdge = new Edge(corner, touchingEdge.startCorner);
      }

      // Construct the first split triangle of this triangle.

      Edge prevEdge = this.edgeLines[(thisTouchingEdgeNo + 2) % 3];
      Edge thisSplittingEdge;
      // construct the splitting edge line of this triangle, so that the first split
      // triangle lies on the left side, and the second split triangle on the right side.
      if (this == prevEdge.leftTriangle) {
        thisSplittingEdge = new Edge(corner, prevEdge.startCorner);
      } else {
        thisSplittingEdge = new Edge(corner, prevEdge.endCorner);
      }
      Edge[] currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = prevEdge;
      currentEdgeLines[1] = firstCommonNewEdge;
      currentEdgeLines[2] = thisSplittingEdge;
      newTriangles[0] = new Triangle(currentEdgeLines, this);
      if (this == prevEdge.leftTriangle) {
        prevEdge.setLeftTriangle(newTriangles[0]);
      } else {
        prevEdge.setRightTriangle(newTriangles[0]);
      }
      firstCommonNewEdge.setLeftTriangle(newTriangles[0]);
      thisSplittingEdge.setLeftTriangle(newTriangles[0]);

      // Construct the second split triangle of this triangle.

      final Edge nextEdge = this.edgeLines[(thisTouchingEdgeNo + 1) % 3];
      currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = thisSplittingEdge;
      currentEdgeLines[1] = secondCommonNewEdge;
      currentEdgeLines[2] = nextEdge;
      newTriangles[1] = new Triangle(currentEdgeLines, this);
      thisSplittingEdge.setRightTriangle(newTriangles[1]);
      secondCommonNewEdge.setLeftTriangle(newTriangles[1]);
      if (this == nextEdge.leftTriangle) {
        nextEdge.setLeftTriangle(newTriangles[1]);
      } else {
        nextEdge.setRightTriangle(newTriangles[1]);
      }

      // construct the first split triangle of neighbourToSplit
      Edge neighbourNextEdge = neighbourToSplit.edgeLines[(neighbourTouchingEdgeNo + 1) % 3];
      Edge neighbourSplittingEdge;
      // construct the splitting edge line of neighbourToSplit, so that the first split
      // triangle lies on the left side, and the second split triangle on the right side.
      if (neighbourToSplit == neighbourNextEdge.leftTriangle) {
        neighbourSplittingEdge = new Edge(neighbourNextEdge.endCorner, corner);
      } else {
        neighbourSplittingEdge = new Edge(neighbourNextEdge.startCorner, corner);
      }
      currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = neighbourSplittingEdge;
      currentEdgeLines[1] = firstCommonNewEdge;
      currentEdgeLines[2] = neighbourNextEdge;
      newTriangles[2] = new Triangle(currentEdgeLines, neighbourToSplit);
      neighbourSplittingEdge.setLeftTriangle(newTriangles[2]);
      firstCommonNewEdge.setRightTriangle(newTriangles[2]);
      if (neighbourToSplit == neighbourNextEdge.leftTriangle) {
        neighbourNextEdge.setLeftTriangle(newTriangles[2]);
      } else {
        neighbourNextEdge.setRightTriangle(newTriangles[2]);
      }

      // construct the second split triangle of neighbourToSplit
      prevEdge = neighbourToSplit.edgeLines[(neighbourTouchingEdgeNo + 2) % 3];
      currentEdgeLines = new Edge[3];
      currentEdgeLines[0] = prevEdge;
      currentEdgeLines[1] = secondCommonNewEdge;
      currentEdgeLines[2] = neighbourSplittingEdge;
      newTriangles[3] = new Triangle(currentEdgeLines, neighbourToSplit);
      if (neighbourToSplit == prevEdge.leftTriangle) {
        prevEdge.setLeftTriangle(newTriangles[3]);
      } else {
        prevEdge.setRightTriangle(newTriangles[3]);
      }
      secondCommonNewEdge.setRightTriangle(newTriangles[3]);
      neighbourSplittingEdge.setRightTriangle(newTriangles[3]);

      return newTriangles;
    }

    /** Checks the consistency of this triangle and its children. Used for debugging purposes. */
    public boolean validate() {
      boolean result = true;
      if (this.isLeaf()) {
        Edge prevEdge = this.edgeLines[2];
        for (int i = 0; i < 3; i++) {
          Edge currentEdge = this.edgeLines[i];
          if (!currentEdge.validate()) {
            result = false;
          }
          // Check, if the ens corner of the previous line equals to the start corner of this line.
          Corner prevEndCorner;
          if (prevEdge.leftTriangle == this) {
            prevEndCorner = prevEdge.endCorner;
          } else {
            prevEndCorner = prevEdge.startCorner;
          }
          Corner currentStartCorner;
          if (currentEdge.leftTriangle == this) {
            currentStartCorner = currentEdge.startCorner;
          } else if (currentEdge.rightTriangle == this) {
            currentStartCorner = currentEdge.endCorner;
          } else {
            FRLogger.warn("Triangle.validate: edge inconsistent");
            return false;
          }
          if (currentStartCorner != prevEndCorner) {
            FRLogger.warn("Triangle.validate: corner inconsistent");
            result = false;
          }
          prevEdge = currentEdge;
        }
      } else {
        for (Triangle currentChild : this.children) {
          if (currentChild.firstParent == this) { // to avoid traversing nodes more than once.
            currentChild.validate();
          }
        }
      }
      return result;
    }

    /**
     * Must be done as long as this triangle node is a leaf and after for all its edge lines the
     * leftTriangle or the rightTriangle reference is set to this triangle.
     */
    private void initializeIsOnTheLeftOfEdgeLineArray() {
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
