package app.freerouting.gui.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.IntBox;
import java.awt.Dimension;
import org.junit.jupiter.api.Test;

/** Tests for the design-related helpers of {@link ScreenTransform}. */
class ScreenTransformTest {

  private static final IntBox DESIGN_BOX = new IntBox(-5_000, -2_000, 15_000, 8_000);

  @Test
  void withPanelSizeKeepsDesignMirroringAndRotation() {
    ScreenTransform original = new ScreenTransform(DESIGN_BOX, new Dimension(800, 600));
    original.setMirrorLeftRight(true);
    original.setMirrorTopBottom(true);
    original.setRotation(Math.PI / 2);
    Dimension newPanelSize = new Dimension(1024, 768);

    ScreenTransform resized = original.withPanelSize(newPanelSize);

    // Equivalent to building the transform by hand, as GraphicsContext did before.
    ScreenTransform expected = new ScreenTransform(DESIGN_BOX, newPanelSize);
    expected.setMirrorLeftRight(original.isMirrorLeftRight());
    expected.setMirrorTopBottom(original.isMirrorTopBottom());
    expected.setRotation(original.getRotation());

    assertNotSame(original, resized);
    assertTrue(expected.isSameTransformState(resized));
    assertEquals(newPanelSize, resized.screenBounds);
    assertEquals(DESIGN_BOX, resized.designBox);
    assertTrue(resized.isMirrorLeftRight());
    assertTrue(resized.isMirrorTopBottom());
    assertEquals(Math.PI / 2, resized.getRotation());
  }

  @Test
  void withPanelSizeLeavesOriginalUnchanged() {
    ScreenTransform original = new ScreenTransform(DESIGN_BOX, new Dimension(800, 600));
    ScreenTransform copy = new ScreenTransform(original);

    original.withPanelSize(new Dimension(320, 200));

    assertTrue(copy.isSameTransformState(original));
    assertEquals(new Dimension(800, 600), original.screenBounds);
  }

  @Test
  void designCenterOnScreenIsCenterOfOffsetDesignBox() {
    ScreenTransform transform = new ScreenTransform(DESIGN_BOX, new Dimension(800, 600));

    assertEquals(
        transform.boardToScreen(transform.designBoxWithOffset.centreOfGravity()),
        transform.getDesignCenterOnScreen());
  }

  @Test
  void designBoundsOnScreenIsDesignBoxInScreenCoordinates() {
    ScreenTransform transform = new ScreenTransform(DESIGN_BOX, new Dimension(800, 600));

    assertEquals(transform.boardToScreen(DESIGN_BOX), transform.getDesignBoundsOnScreen());
  }
}
