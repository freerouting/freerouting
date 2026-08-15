package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.Locale;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression coverage for User Settings layout under enlarged UI fonts. */
@Tag("gui")
class WindowUserSettingsLayoutTest {

  private static final Dimension LEGACY_DIALOG_SIZE = new Dimension(480, 570);
  private static final double[] FONT_SCALES = {1.58, 2.0};

  @Test
  void legacyFixedEnvelopeCannotContainEnlargedUserSettingsContent() {
    GuiA11yHarness.onEdt(
        () -> {
          JPanel content = WindowUserSettings.createContentOnly(Locale.ENGLISH);
          scaleFonts(content, 2.0);
          content.setSize(LEGACY_DIALOG_SIZE);
          content.doLayout();

          assertTrue(
              content.getPreferredSize().height > LEGACY_DIALOG_SIZE.height,
              "The old 480x570 dialog cannot contain the enlarged content preferred height");
        });
  }

  @Test
  void enlargedContentKeepsEssentialControlsReachableThroughVerticalScrolling() {
    for (double fontScale : FONT_SCALES) {
      GuiA11yHarness.onEdt(
          () -> {
            JComponent surface = WindowUserSettings.createComponentOnly(Locale.ENGLISH);
            JScrollPane scrollPane =
                assertInstanceOf(
                    JScrollPane.class,
                    surface,
                    "User Settings must expose a scrollable component-only surface");
            JPanel content = assertInstanceOf(JPanel.class, scrollPane.getViewport().getView());

            scaleFonts(content, fontScale);
            scrollPane.setSize(LEGACY_DIALOG_SIZE);
            scrollPane.revalidate();
            scrollPane.doLayout();
            content.revalidate();
            content.doLayout();

            boolean scrollingRequired =
                content.getPreferredSize().height > scrollPane.getViewport().getExtentSize().height;
            assertTrue(
                !scrollingRequired || scrollPane.getVerticalScrollBar().isVisible(),
                "A vertical scrollbar is required when content exceeds the viewport at font scale "
                    + fontScale);

            requireReachable(
                scrollPane, content, GuiLocators.USER_SETTINGS_EMAIL, AccessibleRole.TEXT);
            requireReachable(
                scrollPane, content, GuiLocators.USER_SETTINGS_SAVE, AccessibleRole.PUSH_BUTTON);
            String sponsorLocator = GuiLocators.USER_SETTINGS_SPONSOR;
            requireReachable(scrollPane, content, sponsorLocator, AccessibleRole.PUSH_BUTTON);
            GuiA11yHarness.requireNoLeakedGuiResources();
          });
    }
  }

  private static void requireReachable(
      JScrollPane scrollPane, JPanel content, String locator, AccessibleRole role) {
    Component component = GuiA11yHarness.findByLocator(content, locator);
    GuiA11yHarness.requireRole(component, locator, role);
    GuiA11yHarness.requireAccessibleName(component, locator);

    Rectangle bounds = component.getBounds();
    content.scrollRectToVisible(bounds);
    JViewport viewport = scrollPane.getViewport();
    Rectangle visible = viewport.getViewRect();
    assertTrue(
        visible.y <= bounds.y && bounds.y + bounds.height <= visible.y + visible.height,
        () ->
            "Control '"
                + locator
                + "' is not vertically reachable; control="
                + bounds
                + ", viewport="
                + visible);
  }

  private static void scaleFonts(Component component, double scale) {
    Font font = component.getFont();
    if (font != null) {
      component.setFont(font.deriveFont((float) (font.getSize2D() * scale)));
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        scaleFonts(child, scale);
      }
    }
  }
}
