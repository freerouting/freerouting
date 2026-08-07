package app.freerouting.rewrite;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import static java.util.Collections.emptyMap;
import static org.openrewrite.internal.NameCaseConvention.LOWER_CAMEL;

/**
 * Renames snake_case method and constructor parameters to lowerCamelCase.
 *
 * <p>Stock {@code RenameLocalVariablesToCamelCase} explicitly skips method parameters.
 */
public class RenameMethodParametersToCamelCase extends Recipe {

  @Option(
      displayName = "Apply recipe to test source set",
      description = "When true, also renames parameters in test sources.",
      required = false)
  @Nullable
  Boolean includeTestSources;

  @Override
  public String getDisplayName() {
    return "Reformat method parameter names to camelCase";
  }

  @Override
  public String getDescription() {
    return "Rename snake_case method and constructor parameters to lowerCamelCase. "
        + "RenameLocalVariablesToCamelCase deliberately skips parameters.";
  }

  @Override
  public Set<String> getTags() {
    return new LinkedHashSet<>(Arrays.asList("RSPEC-S117", "freerouting"));
  }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(2);
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.not(new CSharpFileChecker<>()), new ParameterRenamerVisitor());
  }

  private final class ParameterRenamerVisitor extends JavaIsoVisitor<ExecutionContext> {
    private static final String RENAME_VARIABLES_KEY = "RENAME_PARAMETERS_KEY";
    private static final String HAS_NAME_KEY = "HAS_PARAMETER_NAME_KEY";

    @Nullable
    private Cursor sourceFileCursor;

    private Cursor getSourceFileCursor() {
      if (sourceFileCursor == null) {
        sourceFileCursor =
            getCursor().getPathAsCursors(c -> c.getValue() instanceof JavaSourceFile).next();
      }
      return sourceFileCursor;
    }

    @Override
    public J preVisit(J tree, ExecutionContext ctx) {
      if (tree instanceof JavaSourceFile cu) {
        var sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
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
    public J.VariableDeclarations visitVariableDeclarations(
        J.VariableDeclarations multiVariable, ExecutionContext ctx) {
      J.VariableDeclarations updated = super.visitVariableDeclarations(multiVariable, ctx);
      if (!isMethodParameter()) {
        return updated;
      }
      for (J.VariableDeclarations.NamedVariable parameter : updated.getVariables()) {
        queueParameterRename(parameter);
      }
      return updated;
    }

    @Override
    public @Nullable J postVisit(J tree, ExecutionContext ctx) {
      if (tree instanceof JavaSourceFile cu) {
        Map<J.VariableDeclarations.NamedVariable, String> renameVariablesMap =
            getCursor().getMessage(RENAME_VARIABLES_KEY, emptyMap());
        Set<String> hasNameSet =
            getCursor().computeMessageIfAbsent(HAS_NAME_KEY, ignored -> new HashSet<>());
        JavaSourceFile updated = cu;
        for (Map.Entry<J.VariableDeclarations.NamedVariable, String> entry :
            renameVariablesMap.entrySet()) {
          J.VariableDeclarations.NamedVariable variable = entry.getKey();
          String toName = entry.getValue();
          if (shouldRename(hasNameSet, variable, toName)) {
            updated =
                (JavaSourceFile)
                    new RenameVariable<>(variable, toName).visitNonNull(updated, ctx);
            hasNameSet.add(computeKey(toName, variable));
          }
        }
        return updated;
      }
      return super.postVisit(tree, ctx);
    }

    private boolean isMethodParameter() {
      return getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration;
    }

    private void queueParameterRename(J.VariableDeclarations.NamedVariable parameter) {
      String name = parameter.getSimpleName();
      if (name.length() <= 1 || LOWER_CAMEL.matches(name) || !name.contains("_")) {
        hasNameKey(computeKey(name, parameter));
        return;
      }
      String toName = LOWER_CAMEL.format(name);
      if (toName.isEmpty() || !Character.isAlphabetic(toName.charAt(0))) {
        hasNameKey(computeKey(name, parameter));
        return;
      }
      queueRename(parameter, toName);
    }

    private boolean shouldRename(
        Set<String> hasNameSet, J.VariableDeclarations.NamedVariable variable, String toName) {
      if (toName.isEmpty() || !Character.isAlphabetic(toName.charAt(0))) {
        return false;
      }
      return !hasNameSet.contains(toName) && !hasNameSet.contains(computeKey(toName, variable));
    }

    private void queueRename(J.VariableDeclarations.NamedVariable variable, String toName) {
      getSourceFileCursor()
          .computeMessageIfAbsent(RENAME_VARIABLES_KEY, ignored -> new LinkedHashMap<>())
          .put(variable, toName);
    }

    private void hasNameKey(String variableName) {
      getSourceFileCursor()
          .computeMessageIfAbsent(HAS_NAME_KEY, ignored -> new HashSet<>())
          .add(variableName);
    }

    private String computeKey(String identifier, J context) {
      JavaType.Variable fieldType = getFieldType(context);
      if (fieldType != null && fieldType.getOwner() != null) {
        return fieldType.getOwner() + " " + identifier;
      }
      return identifier;
    }

    private JavaType.@Nullable Variable getFieldType(J tree) {
      if (tree instanceof J.Identifier identifier) {
        return identifier.getFieldType();
      }
      if (tree instanceof J.VariableDeclarations.NamedVariable variable) {
        return variable.getVariableType();
      }
      return null;
    }
  }
}
