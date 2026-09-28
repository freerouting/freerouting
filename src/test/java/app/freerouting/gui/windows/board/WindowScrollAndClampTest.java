package app.freerouting.gui.windows.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.GuiA11yHarness;
import java.awt.Color;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests for scrollable container creation and window height clamping in WindowBase. */
@Tag("gui")
class WindowScrollAndClampTest {

  @Test
  void testCreateScrollableContainerConfiguresDefaults() {
    GuiA11yHarness.onEdt(
        () -> {
          JPanel panel = new JPanel();
          panel.setBackground(Color.LIGHT_GRAY);

          JScrollPane scrollPane = WindowBase.createScrollableContainer(panel);

          assertNotNull(scrollPane);
          assertEquals(panel, scrollPane.getViewport().getView());
          assertEquals(
              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
              scrollPane.getVerticalScrollBarPolicy());
          assertEquals(
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
              scrollPane.getHorizontalScrollBarPolicy());
          assertEquals(16, scrollPane.getVerticalScrollBar().getUnitIncrement());
          assertEquals(16, scrollPane.getHorizontalScrollBar().getUnitIncrement());
          assertEquals(Color.LIGHT_GRAY, scrollPane.getViewport().getBackground());
        });
  }

  @Test
  void testCalculateClampedBoundsConstrainsOversizedBounds() {
    Rectangle screenWorkArea = new Rectangle(0, 40, 1920, 1040);
    Rectangle oversized = new Rectangle(760, -100, 400, 1200);

    int expectedMaxHeight = (int) (1040 * 0.85); // 884
    int scrollBarWidth = 18;

    Rectangle clamped =
        WindowBase.calculateClampedBounds(oversized, screenWorkArea, scrollBarWidth);

    assertEquals(expectedMaxHeight, clamped.height);
    assertEquals(418, clamped.width);
    assertTrue(clamped.y >= screenWorkArea.y, "Top of window should not be above screen work area");
    assertTrue(
        clamped.y + clamped.height <= screenWorkArea.y + screenWorkArea.height,
        "Bottom of window should not exceed screen work area");
  }

  @Test
  void testCalculateClampedBoundsPreservesAppropriatelySizedBounds() {
    Rectangle screenWorkArea = new Rectangle(0, 40, 1920, 1040);
    Rectangle normal = new Rectangle(100, 100, 400, 300);

    Rectangle clamped = WindowBase.calculateClampedBounds(normal, screenWorkArea, 18);

    assertEquals(300, clamped.height);
    assertEquals(400, clamped.width);
    assertEquals(100, clamped.x);
    assertEquals(100, clamped.y);
  }

  @Test
  void testClampWindowHeightDoesNotThrowInHeadless() {
    // clampWindowHeight should safely return without exception when running in headless test runner
    WindowBase.clampWindowHeight(null, null);
  }
}
