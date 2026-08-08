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
  protected ShapeTree(ShapeBoundingDirections pDirections) {
    boundingDirections = pDirections;
    root = null;
    leafCount = 0;
  }

  /** Inserts all shapes of p_obj into the tree */
  public void insert(ShapeTree.Storable pObj) {
    int shapeCount = pObj.treeShapeCount(this);
    if (shapeCount <= 0) {
      return;
    }
    Leaf[] leafArr = new Leaf[shapeCount];
    for (int i = 0; i < shapeCount; i++) {
      leafArr[i] = insert(pObj, i);
    }
    pObj.setSearchTreeEntries(leafArr, this);
  }

  /** Insert a shape - creates a new node with a bounding shape */
  protected Leaf insert(ShapeTree.Storable pObject, int pIndex) {
    Shape objectShape = pObject.getTreeShape(this, pIndex);
    if (objectShape == null) {
      return null;
    }

    RegularTileShape boundingShape = objectShape.boundingShape(boundingDirections);
    if (boundingShape == null) {
      FRLogger.warn("ShapeTree.insert: bounding shape of TreeObject is null");
      return null;
    }
    // Construct a new KdLeaf and set it up
    Leaf newLeaf = new Leaf(pObject, pIndex, null, boundingShape);
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

  abstract void insert(Leaf pLeaf);

  abstract void removeLeaf(Leaf pLeaf);

  /** removes all entries of p_obj in the tree. */
  public void remove(Leaf[] pEntries) {
    if (pEntries == null) {
      return;
    }
    for (int i = 0; i < pEntries.length; i++) {
      removeLeaf(pEntries[i]);
    }
  }

  /** Returns the number of entries stored in the tree. */
  public int size() {
    return leafCount;
  }

  /** Outputs some statistic information about the tree. */
  public void statistics(String pMessage) {
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
            + pMessage);
  }

  /** Interface, which must be implemented by objects to be stored in a ShapeTree. */
  public interface Storable extends Comparable<Object> {

    /** Number of shapes of an object to store in p_shape_tree */
    int treeShapeCount(ShapeTree pShapeTree);

    /**
     * Get the Shape of this object with index p_index stored in the ShapeTree with index
     * identification number p_tree_id_no
     */
    TileShape getTreeShape(ShapeTree pTree, int pIndex);

    /**
     * Stores the entries in the ShapeTrees of this object for better performance while for example
     * deleting tree entries. Called only by insert methods of class ShapeTree.
     */
    void setSearchTreeEntries(Leaf[] pEntries, ShapeTree pTree);
  }

  /** Information of a single object stored in a tree */
  public static class TreeEntry {

    public final ShapeTree.Storable object;
    public final int shapeIndexInObject;

    public TreeEntry(ShapeTree.Storable pObject, int pShapeIndexInObject) {
      object = pObject;
      shapeIndexInObject = pShapeIndexInObject;
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

    public InnerNode(RegularTileShape pBoundingShape, InnerNode pParent) {
      boundingShape = pBoundingShape;
      parent = pParent;
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
        ShapeTree.Storable pObject,
        int pIndex,
        InnerNode pParent,
        RegularTileShape pBoundingShape) {
      boundingShape = pBoundingShape;
      parent = pParent;
      object = pObject;
      shapeIndexInObject = pIndex;
    }

    @Override
    public int compareTo(Leaf pOther) {
      int result = this.object.compareTo(pOther.object);
      if (result == 0) {
        result = shapeIndexInObject - pOther.shapeIndexInObject;
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
