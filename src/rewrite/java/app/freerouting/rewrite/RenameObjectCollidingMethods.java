package app.freerouting.rewrite;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;

/**
 * Renames domain methods that would collide with {@code java.lang.Object} if camelCased naively.
 */
public class RenameObjectCollidingMethods extends Recipe {

  @Override
  public String getDisplayName() {
    return "Rename Object-colliding domain methods";
  }

  @Override
  public String getDescription() {
    return "Renames Net.getNetClass() to getNetClass() to avoid clashing with Object.getClass().";
  }

  @Override
  public Set<String> getTags() {
    return new LinkedHashSet<>(Arrays.asList("RSPEC-S100", "freerouting"));
  }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(1);
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new ChangeMethodName(
            "app.freerouting.rules.Net getNetClass()", "getNetClass", false, false)
        .getVisitor();
  }
}
