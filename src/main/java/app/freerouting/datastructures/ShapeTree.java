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

  /**
   * the fixed directions for calculating bounding RegularTileShapes of shapes to store in this
   * tree.
   */
  protected final ShapeBoundingDirections boundingDirections;

  /** Root node - initially null */
  protected TreeNode root;

  /** The number of entries stored in the tree */
  protected int leafCount;

  /** Creates a new instance of ShapeTree */
  protected ShapeTree(ShapeBoundingDirections p_directions) {
    boundingDirections = p_directions;
    root = null;
    leafCount = 0;
  }

  /** Inserts all shapes of p_obj into the tree */
  public void insert(ShapeTree.Storable p_obj) {
    int shapeCount = p_obj.treeShapeCount(this);
    if (shapeCount <= 0) {
      return;
    }
    Leaf[] leafArr = new Leaf[shapeCount];
    for (int i = 0; i < shapeCount; i++) {
      leafArr[i] = insert(p_obj, i);
    }
    p_obj.setSearchTreeEntries(leafArr, this);
  }

  /** Insert a shape - creates a new node with a bounding shape */
  protected Leaf insert(ShapeTree.Storable p_object, int p_index) {
    Shape objectShape = p_object.getTreeShape(this, p_index);
    if (objectShape == null) {
      return null;
    }

    RegularTileShape boundingShape = objectShape.boundingShape(boundingDirections);
    if (boundingShape == null) {
      FRLogger.warn("ShapeTree.insert: bounding shape of TreeObject is null");
      return null;
    }
    // Construct a new KdLeaf and set it up
    Leaf newLeaf = new Leaf(p_object, p_index, null, boundingShape);
    this.insert(newLeaf);
    return newLeaf;
  }

  /** Inserts the leaves of this tree into an array. */
  public Leaf[] toArray() {
    Leaf[] result = new Leaf[this.leafCount];
    if (result.length == 0) {
      return result;
    }
    TreeNode currNode = this.root;
    int currIndex = 0;
    for (; ; ) {
      // go down from currNode to the left most leaf
      while (currNode instanceof InnerNode) {
        currNode = ((InnerNode) currNode).firstChild;
      }
      result[currIndex] = (Leaf) currNode;

      ++currIndex;
      // go up until parent.secondChild != currNode, which means we came from firstChild
      InnerNode currParent = currNode.parent;
      while (currParent != null && currParent.secondChild == currNode) {
        currNode = currParent;
        currParent = currNode.parent;
      }
      if (currParent == null) {
        break;
      }
      currNode = currParent.secondChild;
    }
    return result;
  }

  abstract void insert(Leaf p_leaf);

  abstract void removeLeaf(Leaf p_leaf);

  /** removes all entries of p_obj in the tree. */
  public void remove(Leaf[] p_entries) {
    if (p_entries == null) {
      return;
    }
    for (int i = 0; i < p_entries.length; i++) {
      removeLeaf(p_entries[i]);
    }
  }

  /** Returns the number of entries stored in the tree. */
  public int size() {
    return leafCount;
  }

  /** Outputs some statistic information about the tree. */
  public void statistics(String p_message) {
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
            + p_message);
  }

  /** Interface, which must be implemented by objects to be stored in a ShapeTree. */
  public interface Storable extends Comparable<Object> {

    /** Number of shapes of an object to store in p_shape_tree */
    int treeShapeCount(ShapeTree p_shape_tree);

    /**
     * Get the Shape of this object with index p_index stored in the ShapeTree with index
     * identification number p_tree_id_no
     */
    TileShape getTreeShape(ShapeTree p_tree, int p_index);

    /**
     * Stores the entries in the ShapeTrees of this object for better performance while for example
     * deleting tree entries. Called only by insert methods of class ShapeTree.
     */
    void setSearchTreeEntries(Leaf[] p_entries, ShapeTree p_tree);
  }

  /** Information of a single object stored in a tree */
  public static class TreeEntry {

    public final ShapeTree.Storable object;
    public final int shapeIndexInObject;

    public TreeEntry(ShapeTree.Storable p_object, int p_shape_index_in_object) {
      object = p_object;
      shapeIndexInObject = p_shape_index_in_object;
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

    public InnerNode(RegularTileShape p_bounding_shape, InnerNode p_parent) {
      boundingShape = p_bounding_shape;
      parent = p_parent;
      firstChild = null;
      secondChild = null;
    }
  }

  //////////////////////////////////////////////////////////

  /** Description of a leaf of the Tree, where the geometric information is stored. */
  public static class Leaf extends TreeNode implements Comparable<Leaf> {

    /** Actual object stored */
    public ShapeTree.Storable object;

    /** index of the shape in the object */
    public int shapeIndexInObject;

    public Leaf(
        ShapeTree.Storable p_object,
        int p_index,
        InnerNode p_parent,
        RegularTileShape p_bounding_shape) {
      boundingShape = p_bounding_shape;
      parent = p_parent;
      object = p_object;
      shapeIndexInObject = p_index;
    }

    @Override
    public int compareTo(Leaf p_other) {
      int result = this.object.compareTo(p_other.object);
      if (result == 0) {
        result = shapeIndexInObject - p_other.shapeIndexInObject;
      }
      return result;
    }

    /** Returns the number of nodes between this leaf and the croot of the tree. */
    public int distanceToRoot() {
      int result = 1;
      InnerNode currParent = this.parent;
      while (currParent.parent != null) {
        currParent = currParent.parent;
        ++result;
      }
      return result;
    }
  }
}
