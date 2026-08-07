package app.freerouting.datastructures;

import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.ShapeBoundingDirections;
import app.freerouting.logger.FRLogger;
import java.util.Set;
import java.util.TreeSet;

/**
 * Binary search tree for shapes in the plane. The shapes are stored in the leaves of the tree. The
 * algorithm for storing a new shape is as following. Starting from the root go to the child, so
 * that the increase of the bounding shape of that child is minimal after adding the new shape,
 * until you reach a leaf. The use of ShapeDirections to calculate the bounding shape is for
 * historical reasons (coming from a Kd-Tree). Instead, any algorithm to calculate a bounding shape
 * of two input shapes can be used. The algorithm would of course also work for higher dimensions.
 */
public class MinAreaTree extends ShapeTree {

  /** Constructor with a fixed set of directions defining the keys and the surrounding shapes */
  public MinAreaTree(ShapeBoundingDirections p_directions) {
    super(p_directions);
  }

  /** Calculates the objects in this tree, which overlap with p_shape */
  public Set<Leaf> overlaps(RegularTileShape p_shape) {
    Set<Leaf> foundOverlaps = new TreeSet<>();
    if (this.root == null) {
      return foundOverlaps;
    }
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currNode;
    for (; ; ) {
      currNode = nodeStack.pop();
      if (currNode == null) {
        break;
      }
      if (currNode.boundingShape.intersects(p_shape)) {
        if (currNode instanceof Leaf leaf) {
          foundOverlaps.add(leaf);
        } else {
          nodeStack.push(((InnerNode) currNode).firstChild);
          nodeStack.push(((InnerNode) currNode).secondChild);
        }
      }
    }
    return foundOverlaps;
  }

  @Override
  void insert(Leaf p_leaf) {
    ++this.leafCount;

    // Tree is empty - just insert the new leaf
    if (root == null) {
      root = p_leaf;
      return;
    }

    // Non-empty tree - do a recursive location for leaf replacement
    Leaf leafToReplace = position_locate(root, p_leaf);

    // Construct a new node - whenever a leaf is added so is a new node
    RegularTileShape newBounds = p_leaf.boundingShape.union(leafToReplace.boundingShape);
    InnerNode currParent = leafToReplace.parent;
    InnerNode newNode = new InnerNode(newBounds, currParent);

    if (leafToReplace.parent != null) {
      // Replace the pointer from the parent to the leaf with our new node
      if (leafToReplace == currParent.firstChild) {
        currParent.firstChild = newNode;
      } else {
        currParent.secondChild = newNode;
      }
    }
    // Update the parent pointers of the old leaf and new leaf to point to new node
    leafToReplace.parent = newNode;
    p_leaf.parent = newNode;

    // Insert the children in any order.
    newNode.firstChild = leafToReplace;
    newNode.secondChild = p_leaf;

    if (root == leafToReplace) {
      root = newNode;
    }
  }

  private Leaf position_locate(TreeNode p_curr_node, Leaf p_leaf_to_insert) {
    TreeNode currNode = p_curr_node;

    while (!(currNode instanceof Leaf)) {
      InnerNode currInnerNode = (InnerNode) currNode;
      currInnerNode.boundingShape =
          p_leaf_to_insert.boundingShape.union(currInnerNode.boundingShape);

      // Choose the child, so that the area increase of that child after taking the union
      // with the shape of p_leaf_to_insert is minimal.

      RegularTileShape firstChildShape = currInnerNode.firstChild.boundingShape;
      RegularTileShape unionWithFirstChildShape =
          p_leaf_to_insert.boundingShape.union(firstChildShape);
      double firstAreaIncrease = unionWithFirstChildShape.area() - firstChildShape.area();

      RegularTileShape secondChildShape = currInnerNode.secondChild.boundingShape;
      RegularTileShape unionWithSecondChildShape =
          p_leaf_to_insert.boundingShape.union(secondChildShape);
      double secondAreaIncrease = unionWithSecondChildShape.area() - secondChildShape.area();

      if (firstAreaIncrease <= secondAreaIncrease) {
        currNode = currInnerNode.firstChild;
      } else {
        currNode = currInnerNode.secondChild;
      }
    }
    return (Leaf) currNode;
  }

  /** removes an entry from this tree */
  @Override
  public void remove_leaf(Leaf p_leaf) {
    if (p_leaf == null) {
      return;
    }
    // remove the leaf node
    InnerNode parent = p_leaf.parent;
    p_leaf.boundingShape = null;
    p_leaf.parent = null;
    p_leaf.object = null;
    --this.leafCount;
    if (parent == null) {
      // tree gets empty
      root = null;
      return;
    }
    // find the other leaf of the parent
    TreeNode otherLeaf;
    if (parent.secondChild == p_leaf) {
      otherLeaf = parent.firstChild;
    } else if (parent.firstChild == p_leaf) {
      otherLeaf = parent.secondChild;
    } else {
      FRLogger.warn("MinAreaTree.remove_leaf: parent inconsistent");
      otherLeaf = null;
    }
    // link the other leaf to the grandParent and remove the parent node
    InnerNode grandParent = parent.parent;
    otherLeaf.parent = grandParent;
    if (grandParent == null) {
      // only one leaf left in the tree
      root = otherLeaf;
    } else {
      if (grandParent.secondChild == parent) {
        grandParent.secondChild = otherLeaf;
      } else if (grandParent.firstChild == parent) {
        grandParent.firstChild = otherLeaf;
      } else {
        FRLogger.warn("MinAreaTree.remove_leaf: grandParent inconsistent");
      }
    }
    parent.parent = null;
    parent.firstChild = null;
    parent.secondChild = null;
    parent.boundingShape = null;

    // recalculate the bounding shapes of the ancestors
    // as long as it gets smaller after removing p_leaf
    InnerNode nodeToRecalculate = grandParent;
    while (nodeToRecalculate != null) {
      RegularTileShape newBounds =
          nodeToRecalculate.secondChild.boundingShape.union(
              nodeToRecalculate.firstChild.boundingShape);
      if (newBounds.contains(nodeToRecalculate.boundingShape)) {
        // the new bounds are not smaller, no further recalculate necessary
        break;
      }
      nodeToRecalculate.boundingShape = newBounds;
      nodeToRecalculate = nodeToRecalculate.parent;
    }
  }
}
