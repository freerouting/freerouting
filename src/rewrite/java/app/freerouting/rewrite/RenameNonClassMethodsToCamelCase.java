package app.freerouting.rewrite;

import static org.openrewrite.internal.NameCaseConvention.LOWER_CAMEL;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Renames snake_case methods declared in interfaces and enums.
 *
 * <p>Stock {@code MethodNameCasing} only visits {@code J.ClassDeclaration.Kind.Type.Class}, so
 * interface/enum declarations keep snake_case while class implementations are renamed.
 */
public class RenameNonClassMethodsToCamelCase
    extends ScanningRecipe<RenameNonClassMethodsToCamelCase.MethodRenamePlan> {

  @Option(
      displayName = "Apply recipe to test source set",
      description = "When true, also renames methods in test sources.",
      required = false)
  @Nullable
  Boolean includeTestSources;

  @Override
  public String getDisplayName() {
    return "Reformat interface and enum method names to camelCase";
  }

  @Override
  public String getDescription() {
    return "Rename snake_case methods declared in interfaces and enums to lowerCamelCase.";
  }

  @Override
  public Set<String> getTags() {
    return new LinkedHashSet<>(Arrays.asList("RSPEC-S100", "freerouting"));
  }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(2);
  }

  @Override
  public MethodRenamePlan getInitialValue(ExecutionContext ctx) {
    return new MethodRenamePlan();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(MethodRenamePlan plan) {
    return new JavaIsoVisitor<>() {
      @Override
      public J preVisit(J tree, ExecutionContext ctx) {
        if (tree instanceof JavaSourceFile cu) {
          Optional<JavaSourceSet> sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
          if (sourceSet.isEmpty()) {
            stopAfterPreVisit();
          } else if (!Boolean.TRUE.equals(includeTestSources)
              && !"main".equals(sourceSet.get().getName())) {
            stopAfterPreVisit();
          }
        }
        return super.preVisit(tree, ctx);
      }

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (enclosingClass == null
            || !isSupportedTypeKind(enclosingClass.getKind())
            || method.isConstructor()
            || method.getMethodType() == null) {
          return method;
        }

        String simpleName = method.getSimpleName();
        if (!simpleName.contains("_")) {
          return method;
        }
        String toName = LOWER_CAMEL.format(simpleName);
        if (StringUtils.isBlank(toName)
            || toName.equals(simpleName)
            || StringUtils.isNumeric(toName)
            || methodExists(method.getMethodType(), toName)) {
          return method;
        }

        String fqn = method.getMethodType().getDeclaringType().getFullyQualifiedName();
        String pattern = fqn + "+ " + method.getSimpleName() + "(..)";
        plan.add(new ChangeMethodName(pattern, toName, true, false));
        return method;
      }

      private boolean isSupportedTypeKind(J.ClassDeclaration.Kind.Type kind) {
        return kind == J.ClassDeclaration.Kind.Type.Interface
            || kind == J.ClassDeclaration.Kind.Type.Enum;
      }

      private boolean methodExists(JavaType.Method method, String newName) {
        return TypeUtils.findDeclaredMethod(
                method.getDeclaringType(), newName, method.getParameterTypes())
            .isPresent();
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(MethodRenamePlan plan) {
    if (plan.isEmpty()) {
      return TreeVisitor.noop();
    }
    return new JavaIsoVisitor<>() {
      @Override
      public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (!(tree instanceof JavaSourceFile cu)) {
          return (J) tree;
        }
        JavaSourceFile updated = cu;
        for (ChangeMethodName change : plan.changes()) {
          updated = (JavaSourceFile) change.getVisitor().visitNonNull(updated, ctx);
        }
        return updated;
      }
    };
  }

  /** Collects method rename changes for later application. */
  public static final class MethodRenamePlan {
    private final List<ChangeMethodName> changes = new ArrayList<>();

    void add(ChangeMethodName change) {
      changes.add(change);
    }

    List<ChangeMethodName> changes() {
      return changes;
    }

    boolean isEmpty() {
      return changes.isEmpty();
    }
  }
}
