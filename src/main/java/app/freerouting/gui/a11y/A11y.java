package app.freerouting.gui.a11y;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * Shared accessibility helper and locator registry for the Freerouting GUI (decision D22).
 *
 * <p>Product code uses {@link #tag} to attach a stable, locale-independent locator and {@link
 * #describe} to set the translated accessible name/description. The test harness ({@code
 * GuiA11yHarness}) uses {@link #findByLocator} and {@link #flatten} to resolve and audit
 * components. All traversal is pure JDK (component containment tree + {@code AccessibleContext});
 * no reflection, screen coordinates, or top-level window visibility tricks.
 *
 * <p>This class lives in the {@code gui} package so it may be used by views; it has no dependency
 * on the routing pipeline, keeping the SoC boundary (plan §1.1) intact.
 */
public final class A11y {

  private A11y() {}

  /**
   * Attaches a stable, locale-independent locator to a component and returns it (fluent).
   *
   * <p>The locator is stored via {@link Component#setName(String)} — the canonical, non-displayed
   * programmatic ID. It is independent of the translated accessible name.
   *
   * @param component the component to tag
   * @param locator a constant from {@link GuiLocators}
   * @return the same component, for chaining
   */
  public static <T extends Component> T tag(T component, String locator) {
    component.setName(locator);
    return component;
  }

  /**
   * Sets the translated accessible name and (optional) description used by assistive technology.
   *
   * @param component the component
   * @param accessibleName the human-readable, translated name
   * @param accessibleDescription the human-readable, translated description (may be {@code null})
   * @return the same component, for chaining
   */
  public static <T extends JComponent> T describe(
      T component, String accessibleName, String accessibleDescription) {
    if (component.getAccessibleContext() != null) {
      if (accessibleName != null) {
        component.getAccessibleContext().setAccessibleName(accessibleName);
      }
      if (accessibleDescription != null) {
        component.getAccessibleContext().setAccessibleDescription(accessibleDescription);
      }
    }
    return component;
  }

  /**
   * Returns the accessible children of {@code component} that are real {@link Component}s, in
   * accessible order. This mirrors the {@link AccessibleContext} structure (contract §3) rather
   * than {@link Container#getComponents()}, so components whose accessible children live in an
   * internal popup/inner hierarchy (e.g. {@code JMenu} items, combo-box internals) are discovered
   * correctly.
   */
  public static List<Component> accessibleChildren(Component component) {
    List<Component> out = new ArrayList<>();
    AccessibleContext ac = component.getAccessibleContext();
    if (ac != null) {
      int count = ac.getAccessibleChildrenCount();
      for (int i = 0; i < count; i++) {
        Accessible child = ac.getAccessibleChild(i);
        if (child instanceof Component comp) {
          out.add(comp);
        }
      }
    }
    return out;
  }

  /**
   * Finds the first component (inclusive of the root itself) whose programmatic name equals {@code
   * locator}, traversing via {@link AccessibleContext} children.
   *
   * @param root the container root to search under
   * @param locator a constant from {@link GuiLocators}
   * @return the matching component, or {@code null} if none
   */
  public static Component findByLocator(Container root, String locator) {
    if (locator.equals(root.getName())) {
      return root;
    }
    for (Component c : accessibleChildren(root)) {
      if (locator.equals(c.getName())) {
        return c;
      }
      if (c instanceof Container child) {
        Component found = findByLocator(child, locator);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Collects every component in the accessible tree (pre-order, excluding the root itself) for
   * sibling duplicate/empty accessible-name audits. Swing popup windows ({@link JPopupMenu} and
   * subclasses, e.g. the combo box's {@code BasicComboPopup}) are skipped: their LAF-internal
   * chrome is not product-owned content and would otherwise produce false findings.
   *
   * @param root the container root
   * @return all descendant components
   */
  public static List<Component> flatten(Container root) {
    List<Component> out = new ArrayList<>();
    collect(root, out);
    return out;
  }

  private static void collect(Component component, List<Component> out) {
    for (Component c : accessibleChildren(component)) {
      // Skip Swing popup windows (JPopupMenu and subclasses, e.g. the JComboBox's
      // BasicComboPopup) for the sibling-name audit: their LAF-internal chrome (scrollbars,
      // buttons) is not product-owned sibling content and would produce false findings. Popup
      // content is still reachable via {@link #findByLocator}, which walks the accessible tree
      // independently.
      if (c instanceof JPopupMenu) {
        continue;
      }
      out.add(c);
      collect(c, out);
    }
  }
}
