package app.freerouting.gui.a11y;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

/**
 * Pure-JDK accessibility test harness for component-only, forced-headless GUI tests (decisions
 * D7/D8/D22).
 *
 * <ul>
 *   <li>All construction/mutation/action runs on the EDT via {@link #onEdt}; {@link #assertOnEdt}
 *       guards workflow mutations.
 *   <li>Components are found by stable locator ({@link Component#getName()}), never by translated
 *       label.
 *   <li>Assertions read the {@link AccessibleContext} (name/role/state/action).
 *   <li>Failures report the accessible path, role, and locator sought.
 *   <li>No private-field reflection, no screen coordinates, no top-level frame {@code setVisible}.
 * </ul>
 */
public final class GuiA11yHarness {

  private GuiA11yHarness() {}

  // ------------------------------------------------------------------ EDT

  /** Runs {@code action} on the EDT, blocking until done. */
  public static void onEdt(Runnable action) {
    runEdt(
        () -> {
          action.run();
          return null;
        });
  }

  /** Runs {@code action} on the EDT, returning its result. */
  public static <T> T onEdt(Supplier<T> action) {
    return runEdt(action);
  }

  private static <T> T runEdt(Supplier<T> action) {
    if (EventQueue.isDispatchThread()) {
      return action.get();
    }
    final Object[] box = new Object[1];
    try {
      EventQueue.invokeAndWait(() -> box[0] = action.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while executing on the EDT", e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw new AssertionError("EDT action threw", cause);
    }
    @SuppressWarnings("unchecked")
    T result = (T) box[0];
    return result;
  }

  /** Asserts the current thread is the EDT. Call inside workflow mutations/actions. */
  public static void assertOnEdt() {
    if (!EventQueue.isDispatchThread()) {
      throw new AssertionError(
          "Expected to run on the Event Dispatch Thread, but was on '"
              + Thread.currentThread().getName()
              + "'");
    }
  }

  // ------------------------------------------------------------------ lookup

  /** Finds the component for {@code locator} under {@code root}; fails with the accessible tree. */
  public static Component findByLocator(Container root, String locator) {
    Component found = A11y.findByLocator(root, locator);
    if (found == null) {
      throw new AssertionError(
          "No component found for locator '"
              + locator
              + "' under root. Accessible tree:\n"
              + describeTree(root));
    }
    return found;
  }

  /** Returns the accessible name of a component (may be {@code null}). */
  public static String accessibleName(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    return ac == null ? null : ac.getAccessibleName();
  }

  /** Returns the accessible role of a component (may be {@code null}). */
  public static AccessibleRole accessibleRole(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    return ac == null ? null : ac.getAccessibleRole();
  }

  // ------------------------------------------------------------------ assertions

  /** Asserts the component exposes a non-empty accessible name. */
  public static void requireAccessibleName(Component c, String locator) {
    String name = accessibleName(c);
    if (name == null || name.isBlank()) {
      throw new AssertionError(
          "Component at "
              + pathOf(c)
              + " (locator '"
              + locator
              + "', role "
              + accessibleRole(c)
              + ") has an empty or missing accessible name");
    }
  }

  /** Asserts the component's accessible role equals {@code expected}. */
  public static void requireRole(Component c, String locator, AccessibleRole expected) {
    AccessibleRole actual = accessibleRole(c);
    if (!expected.equals(actual)) {
      throw new AssertionError(
          "Component at "
              + pathOf(c)
              + " (locator '"
              + locator
              + "') expected role "
              + expected
              + " but was "
              + actual);
    }
  }

  /** Asserts the component is in the ENABLED accessible state. */
  public static void requireEnabled(Component c, String locator) {
    AccessibleContext ac = requireAccessibleContext(c, locator);
    AccessibleStateSet states = ac.getAccessibleStateSet();
    if (!states.contains(AccessibleState.ENABLED)) {
      throw new AssertionError(
          "Component at "
              + pathOf(c)
              + " (locator '"
              + locator
              + "', role "
              + ac.getAccessibleRole()
              + ") is expected to be ENABLED but its state set is "
              + states);
    }
  }

  /** Asserts the component is not in the ENABLED accessible state. */
  public static void requireDisabled(Component c, String locator) {
    AccessibleContext ac = requireAccessibleContext(c, locator);
    AccessibleStateSet states = ac.getAccessibleStateSet();
    if (states.contains(AccessibleState.ENABLED)) {
      throw new AssertionError(
          "Component at "
              + pathOf(c)
              + " (locator '"
              + locator
              + "', role "
              + ac.getAccessibleRole()
              + ") is expected to be DISABLED but its state set is "
              + states);
    }
  }

  /** Asserts the component has an AccessibleContext at all. */
  public static AccessibleContext requireAccessibleContext(Component c, String locator) {
    AccessibleContext ac = c.getAccessibleContext();
    if (ac == null) {
      throw new AssertionError(
          "Component for locator '"
              + locator
              + "' ("
              + pathOf(c)
              + ") exposes no AccessibleContext");
    }
    return ac;
  }

  /**
   * Asserts no two siblings under {@code root} share a duplicate accessible name, and that
   * interactive siblings (those exposing an AccessibleAction) have non-empty names.
   */
  public static void requireUniqueSiblingNames(Container root) {
    for (Component c : A11y.flatten(root)) {
      if (!(c instanceof Container container)) {
        continue;
      }
      Map<String, Component> seen = new HashMap<>();
      for (Component child : A11y.accessibleChildren(container)) {
        String name = accessibleName(child);
        boolean interactive =
            child.getAccessibleContext() != null
                && child.getAccessibleContext().getAccessibleAction() != null;
        if (interactive && (name == null || name.isBlank())) {
          throw new AssertionError(
              "Interactive sibling at "
                  + pathOf(child)
                  + " (role "
                  + accessibleRole(child)
                  + ") has an empty accessible name");
        }
        if (name != null && !name.isBlank()) {
          Component prior = seen.putIfAbsent(name, child);
          if (prior != null) {
            throw new AssertionError(
                "Duplicate sibling accessible name '"
                    + name
                    + "' under "
                    + pathOf(container)
                    + " for "
                    + pathOf(prior)
                    + " and "
                    + pathOf(child));
          }
        }
      }
    }
  }

  // ------------------------------------------------------------------ actions

  /**
   * Invokes the first accessible action on the component (must run on the EDT).
   *
   * @throws AssertionError if the component has no accessible action or the action is refused
   */
  public static void invoke(Component c, String locator) {
    assertOnEdt();
    AccessibleContext ac = requireAccessibleContext(c, locator);
    AccessibleAction action = ac.getAccessibleAction();
    if (action == null) {
      throw new AssertionError(
          "Component at "
              + pathOf(c)
              + " (locator '"
              + locator
              + "', role "
              + ac.getAccessibleRole()
              + ") has no AccessibleAction");
    }
    if (action.getAccessibleActionCount() < 1 || !action.doAccessibleAction(0)) {
      throw new AssertionError(
          "Accessible action on component at "
              + pathOf(c)
              + " (locator '"
              + locator
              + "') was refused or absent");
    }
  }

  /**
   * Verifies that component-only workflows did not create displayable windows or named GUI worker
   * threads. The AWT event-dispatch thread and ordinary JDK daemon threads are expected.
   */
  public static void requireNoLeakedGuiResources() {
    for (Window window : Window.getWindows()) {
      if (window.isDisplayable()) {
        throw new AssertionError(
            "Component-only workflow leaked a displayable top-level window: "
                + window.getClass().getName());
      }
    }
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      String name = thread.getName().toLowerCase();
      if (thread.isAlive()
          && (name.startsWith("gui-")
              || name.startsWith("plane-fill")
              || name.startsWith("routing-job"))) {
        throw new AssertionError(
            "Component-only workflow leaked a GUI/routing worker thread: " + thread.getName());
      }
    }
  }

  // ------------------------------------------------------------------ failure reporting

  /** Builds a readable root→leaf path using each ancestor's locator, accessible name, and role. */
  public static String pathOf(Component c) {
    List<String> parts = new ArrayList<>();
    Component cur = c;
    while (cur != null) {
      StringBuilder sb = new StringBuilder();
      sb.append(cur.getClass().getSimpleName());
      String locator = cur.getName();
      if (locator != null && !locator.isBlank()) {
        sb.append("[").append(locator).append("]");
      }
      String name = accessibleName(cur);
      if (name != null && !name.isBlank()) {
        sb.append("\"").append(name).append("\"");
      }
      parts.add(0, sb.toString());
      cur = cur.getParent();
    }
    return String.join(" / ", parts);
  }

  /**
   * Dumps the accessible tree under {@code root} (locator + role + name per node) for failure
   * messages.
   */
  public static String describeTree(Container root) {
    StringBuilder sb = new StringBuilder();
    describeInto(root, 0, sb);
    return sb.toString();
  }

  private static void describeInto(Component c, int depth, StringBuilder sb) {
    sb.append("  ".repeat(Math.max(0, depth)));
    sb.append(c.getClass().getSimpleName());
    String locator = c.getName();
    if (locator != null && !locator.isBlank()) {
      sb.append(" [").append(locator).append("]");
    }
    sb.append(" role=").append(accessibleRole(c));
    String name = accessibleName(c);
    if (name != null && !name.isBlank()) {
      sb.append(" name=\"").append(name).append("\"");
    }
    sb.append('\n');
    for (Component child : A11y.accessibleChildren(c)) {
      describeInto(child, depth + 1, sb);
    }
  }
}
