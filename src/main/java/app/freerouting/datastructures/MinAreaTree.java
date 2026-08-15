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

  /** Constructor with a fixed set of directions defining the keys and the surrounding shapes. */
  public MinAreaTree(ShapeBoundingDirections directions) {
    super(directions);
  }

  /** Calculates the objects in this tree, which overlap with shape. */
  public Set<Leaf> overlaps(RegularTileShape shape) {
    Set<Leaf> foundOverlaps = new TreeSet<>();
    if (this.root == null) {
      return foundOverlaps;
    }
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currentNode;
    for (; ; ) {
      currentNode = nodeStack.pop();
      if (currentNode == null) {
        break;
      }
      if (currentNode.boundingShape.intersects(shape)) {
        if (currentNode instanceof Leaf leaf) {
          foundOverlaps.add(leaf);
        } else {
          nodeStack.push(((InnerNode) currentNode).firstChild);
          nodeStack.push(((InnerNode) currentNode).secondChild);
        }
      }
    }
    return foundOverlaps;
  }

  @Override
  void insert(Leaf leaf) {
    ++this.leafCount;

    // Tree is empty - just insert the new leaf
    if (root == null) {
      root = leaf;
      return;
    }

    // Non-empty tree - do a recursive location for leaf replacement
    Leaf leafToReplace = positionLocate(root, leaf);

    // Construct a new node - whenever a leaf is added so is a new node
    RegularTileShape newBounds = leaf.boundingShape.union(leafToReplace.boundingShape);
    InnerNode currentParent = leafToReplace.parent;
    InnerNode newNode = new InnerNode(newBounds, currentParent);

    if (leafToReplace.parent != null) {
      // Replace the pointer from the parent to the leaf with our new node
      if (leafToReplace == currentParent.firstChild) {
        currentParent.firstChild = newNode;
      } else {
        currentParent.secondChild = newNode;
      }
    }
    // Update the parent pointers of the old leaf and new leaf to point to new node
    leafToReplace.parent = newNode;
    leaf.parent = newNode;

    // Insert the children in any order.
    newNode.firstChild = leafToReplace;
    newNode.secondChild = leaf;

    if (root == leafToReplace) {
      root = newNode;
    }
  }

  private Leaf positionLocate(TreeNode currentNode, Leaf leafToInsert) {
    TreeNode node = currentNode;

    while (!(node instanceof Leaf)) {
      InnerNode currentInnerNode = (InnerNode) node;
      currentInnerNode.boundingShape =
          leafToInsert.boundingShape.union(currentInnerNode.boundingShape);

      // Choose the child, so that the area increase of that child after taking the union
      // with the shape of leafToInsert is minimal.

      RegularTileShape firstChildShape = currentInnerNode.firstChild.boundingShape;
      RegularTileShape unionWithFirstChildShape = leafToInsert.boundingShape.union(firstChildShape);
      double firstAreaIncrease = unionWithFirstChildShape.area() - firstChildShape.area();

      RegularTileShape secondChildShape = currentInnerNode.secondChild.boundingShape;
      RegularTileShape unionWithSecondChildShape =
          leafToInsert.boundingShape.union(secondChildShape);
      double secondAreaIncrease = unionWithSecondChildShape.area() - secondChildShape.area();

      if (firstAreaIncrease <= secondAreaIncrease) {
        node = currentInnerNode.firstChild;
      } else {
        node = currentInnerNode.secondChild;
      }
    }
    return (Leaf) node;
  }

  /** Removes an entry from this tree. */
  @Override
  public void removeLeaf(Leaf leaf) {
    if (leaf == null) {
      return;
    }
    // remove the leaf node
    InnerNode parent = leaf.parent;
    leaf.boundingShape = null;
    leaf.parent = null;
    leaf.object = null;
    --this.leafCount;
    if (parent == null) {
      // tree gets empty
      root = null;
      return;
    }
    // find the other leaf of the parent
    TreeNode otherLeaf;
    if (parent.secondChild == leaf) {
      otherLeaf = parent.firstChild;
    } else if (parent.firstChild == leaf) {
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
    // as long as it gets smaller after removing leaf
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
