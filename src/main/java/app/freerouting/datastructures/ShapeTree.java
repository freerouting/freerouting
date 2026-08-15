package app.freerouting.datastructures;

import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.ShapeBoundingDirections;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;

/**
 * Abstract binary search tree for shapes in the plane. The shapes are stored in the leafs of the
 * tree. Objects to be stored in the tree must implement the interface ShapeTree.Storable.
 */
public abstract class ShapeTree {

  /** Fixed directions for calculating bounding regular tile shapes stored in this tree. */
  protected final ShapeBoundingDirections boundingDirections;

  /** Root node - initially null. */
  protected TreeNode root;

  /** The number of entries stored in the tree. */
  protected int leafCount;

  /** Creates a new instance of ShapeTree. */
  protected ShapeTree(ShapeBoundingDirections directions) {
    boundingDirections = directions;
    root = null;
    leafCount = 0;
  }

  /** Inserts all shapes of obj into the tree. */
  public void insert(ShapeTree.Storable obj) {
    int shapeCount = obj.treeShapeCount(this);
    if (shapeCount <= 0) {
      return;
    }
    Leaf[] leafArr = new Leaf[shapeCount];
    for (int i = 0; i < shapeCount; i++) {
      leafArr[i] = insert(obj, i);
    }
    obj.setSearchTreeEntries(leafArr, this);
  }

  /** Insert a shape - creates a new node with a bounding shape. */
  protected Leaf insert(ShapeTree.Storable object, int index) {
    Shape objectShape = object.getTreeShape(this, index);
    if (objectShape == null) {
      return null;
    }

    RegularTileShape boundingShape = objectShape.boundingShape(boundingDirections);
    if (boundingShape == null) {
      FRLogger.warn("ShapeTree.insert: bounding shape of TreeObject is null");
      return null;
    }
    // Construct a new KdLeaf and set it up
    Leaf newLeaf = new Leaf(object, index, null, boundingShape);
    this.insert(newLeaf);
    return newLeaf;
  }

  abstract void insert(Leaf leaf);

  abstract void removeLeaf(Leaf leaf);

  /** Inserts the leaves of this tree into an array. */
  public Leaf[] toArray() {
    Leaf[] result = new Leaf[this.leafCount];
    if (result.length == 0) {
      return result;
    }
    TreeNode currentNode = this.root;
    int currentIndex = 0;
    for (; ; ) {
      // go down from currentNode to the left most leaf
      while (currentNode instanceof InnerNode) {
        currentNode = ((InnerNode) currentNode).firstChild;
      }
      result[currentIndex] = (Leaf) currentNode;

      ++currentIndex;
      // go up until parent.secondChild != currentNode, which means we came from firstChild
      InnerNode currentParent = currentNode.parent;
      while (currentParent != null && currentParent.secondChild == currentNode) {
        currentNode = currentParent;
        currentParent = currentNode.parent;
      }
      if (currentParent == null) {
        break;
      }
      currentNode = currentParent.secondChild;
    }
    return result;
  }

  /** Removes all entries of obj in the tree. */
  public void remove(Leaf[] entries) {
    if (entries == null) {
      return;
    }
    for (int i = 0; i < entries.length; i++) {
      removeLeaf(entries[i]);
    }
  }

  /** Returns the number of entries stored in the tree. */
  public int size() {
    return leafCount;
  }

  /** Outputs some statistic information about the tree. */
  public void statistics(String message) {
    Leaf[] leafArr = this.toArray();
    double cumulativeDepth = 0;
    int maximumDepth = 0;
    for (int i = 0; i < leafArr.length; i++) {
      if (leafArr[i] != null) {
        int distanceToRoot = leafArr[i].distanceToRoot();
        cumulativeDepth += distanceToRoot;
        maximumDepth = Math.max(maximumDepth, distanceToRoot);
      }
    }
    double averageDepth = cumulativeDepth / leafArr.length;
    FRLogger.info(
        "MinAreaTree: Entry count: "
            + leafArr.length
            + " log: "
            + Math.round(Math.log(leafArr.length))
            + " Average depth: "
            + Math.round(averageDepth)
            + " "
            + " Maximum depth: "
            + maximumDepth
            + " "
            + message);
  }

  /** Interface, which must be implemented by objects to be stored in a ShapeTree. */
  public interface Storable extends Comparable<Object> {

    /** Number of shapes of an object to store in shapeTree. */
    int treeShapeCount(ShapeTree shapeTree);

    /**
     * Get the Shape of this object with index stored in the ShapeTree with index identification
     * number treeIdNo.
     */
    TileShape getTreeShape(ShapeTree tree, int index);

    /**
     * Stores the entries in the ShapeTrees of this object for better performance while for example
     * deleting tree entries. Called only by insert methods of class ShapeTree.
     */
    void setSearchTreeEntries(Leaf[] entries, ShapeTree tree);
  }

  /** Information of a single object stored in a tree. */
  public static class TreeEntry {

    public final ShapeTree.Storable object;
    public final int shapeIndexInObject;

    /** Creates a tree entry for object and shapeIndexInObject. */
    public TreeEntry(ShapeTree.Storable object, int shapeIndexInObject) {
      this.object = object;
      this.shapeIndexInObject = shapeIndexInObject;
    }
  }

  //////////////////////////////////////////////////////////

  /** Common functionality of inner nodes and leaf nodes. */
  protected static class TreeNode {

    public RegularTileShape boundingShape;
    InnerNode parent;
  }

  //////////////////////////////////////////////////////////

  /** Description of an inner node of the tree, which implements a fork to its two children. */
  public static class InnerNode extends TreeNode {

    public TreeNode firstChild;
    public TreeNode secondChild;

    /** Creates an inner node with boundingShape and parent. */
    public InnerNode(RegularTileShape boundingShape, InnerNode parent) {
      this.boundingShape = boundingShape;
      this.parent = parent;
      firstChild = null;
      secondChild = null;
    }
  }

  //////////////////////////////////////////////////////////

  /** Description of a leaf of the Tree, where the geometric information is stored. */
  public static class Leaf extends TreeNode implements Comparable<Leaf> {

    /** Actual object stored. */
    public ShapeTree.Storable object;

    /** Index of the shape in the object. */
    public int shapeIndexInObject;

    /** Creates a leaf node for object at index with parent and boundingShape. */
    public Leaf(
        ShapeTree.Storable object, int index, InnerNode parent, RegularTileShape boundingShape) {
      this.boundingShape = boundingShape;
      this.parent = parent;
      this.object = object;
      this.shapeIndexInObject = index;
    }

    @Override
    public int compareTo(Leaf other) {
      int result = this.object.compareTo(other.object);
      if (result == 0) {
        result = shapeIndexInObject - other.shapeIndexInObject;
      }
      return result;
    }

    /** Returns the number of nodes between this leaf and the croot of the tree. */
    public int distanceToRoot() {
      int result = 1;
      InnerNode currentParent = this.parent;
      while (currentParent.parent != null) {
        currentParent = currentParent.parent;
        ++result;
      }
      return result;
    }
  }
}
