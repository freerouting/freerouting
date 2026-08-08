package app.freerouting.rewrite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import static org.openrewrite.internal.NameCaseConvention.LOWER_CAMEL;

/**
 * Renames non-constant instance fields at any access level to lowerCamelCase.
 *
 * <p>Renames field declarations, then applies {@link ApplyFieldRenamesTextually} for cross-file
 * references. Does not use {@code ChangeFieldName}, which also renames parameters and locals
 * that share a field name.
 */
public class RenameInstanceFieldsToCamelCase extends ScanningRecipe<RenameInstanceFieldsToCamelCase.FieldRenamePlan> {

  /** Shared with {@link ApplyCollectedFieldRenames} within the same rewriteRun invocation. */
  static final Map<String, String> COLLECTED_RENAMES = new java.util.concurrent.ConcurrentHashMap<>();

  private static final AnnotationMatcher LOMBOK_ANNOTATION = new AnnotationMatcher("@lombok.*");

  @Option(
      displayName = "Apply recipe to test source set",
      description =
          "Changes only apply to main by default. When true, also renames fields in test sources.",
      required = false)
  @Nullable
  Boolean includeTestSources;

  @Override
  public String getDisplayName() {
    return "Reformat instance field names to camelCase (all visibilities)";
  }

  @Override
  public String getDescription() {
    return """
        Reformat instance field names to lowerCamelCase at any access level. \
        Skips static final constants and Lombok-annotated types.""";
  }

  @Override
  public Set<String> getTags() {
    return new LinkedHashSet<>(Arrays.asList("RSPEC-S116", "RSPEC-S3008", "freerouting"));
  }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(2);
  }

  @Override
  public FieldRenamePlan getInitialValue(ExecutionContext ctx) {
    COLLECTED_RENAMES.clear();
    resetRenameMapFile();
    return new FieldRenamePlan();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(FieldRenamePlan plan) {
    return new JavaIsoVisitor<>() {
      private final Set<String> namesInClass = new HashSet<>();

      @Override
      public J preVisit(J tree, ExecutionContext ctx) {
        if (tree instanceof JavaSourceFile cu) {
          namesInClass.clear();
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
      public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        if (service(AnnotationService.class).matches(getCursor(), LOMBOK_ANNOTATION)) {
          return classDecl;
        }
        return super.visitClassDeclaration(classDecl, ctx);
      }

      @Override
      public J.VariableDeclarations.NamedVariable visitVariable(
          J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
        Cursor parentScope = getCursorToParentScope(getCursor());
        JavaType.Variable type = variable.getVariableType();
        J.ClassDeclaration enclosingClass =
            getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (parentScope.getValue() instanceof J.Block
            && parentScope.getParent() != null
            && parentScope.getParent().getValue() instanceof J.ClassDeclaration
            && type != null
            && !type.hasFlags(Flag.Static, Flag.Final)
            && !LOWER_CAMEL.matches(variable.getSimpleName())
            && enclosingClass != null
            && enclosingClass.getType() != null) {
          String toName = LOWER_CAMEL.format(variable.getSimpleName());
          if (shouldRename(toName, variable.getSimpleName())) {
            JavaType.FullyQualified classType =
                TypeUtils.asFullyQualified(enclosingClass.getType());
            if (classType != null) {
              plan.add(
                  classType.getFullyQualifiedName(),
                  new ChangeFieldName(
                      classType.getFullyQualifiedName(), variable.getSimpleName(), toName));
            }
            namesInClass.add(toName);
          }
        }
        namesInClass.add(variable.getSimpleName());
        return super.visitVariable(variable, ctx);
      }

      private boolean shouldRename(String toName, String fromName) {
        return !StringUtils.isBlank(toName)
            && Character.isAlphabetic(toName.charAt(0))
            && !namesInClass.contains(toName)
            && !namesInClass.contains(fromName);
      }

      private Cursor getCursorToParentScope(Cursor cursor) {
        return cursor.dropParentUntil(
            is ->
                is instanceof J.ClassDeclaration
                    || is instanceof J.Block
                    || is instanceof J.MethodDeclaration
                    || is instanceof org.openrewrite.SourceFile);
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(FieldRenamePlan plan) {
    if (plan.isEmpty()) {
      return TreeVisitor.noop();
    }
    writeRenameMapFile(plan.renameMap());
    return new JavaIsoVisitor<>() {
      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        if (cd.getType() != null) {
          String classFqn = cd.getType().getFullyQualifiedName();
          List<ChangeFieldName> matching = plan.changesForClass(classFqn);
          Map<String, String> renamesInClass = new LinkedHashMap<>();
          for (ChangeFieldName change : matching) {
            renamesInClass.put(change.getHasName(), change.getToName());
            cd = (J.ClassDeclaration) change.visitNonNull(cd, ctx);
          }
          if (!renamesInClass.isEmpty()) {
            cd =
                (J.ClassDeclaration)
                    new RenameSameClassFieldRefsVisitor(renamesInClass).visitNonNull(cd, ctx);
          }
        }
        return cd;
      }
    };
  }

  private static final class RenameSameClassFieldRefsVisitor extends JavaIsoVisitor<ExecutionContext> {
    private final Map<String, String> renamesInClass;

    RenameSameClassFieldRefsVisitor(Map<String, String> renamesInClass) {
      this.renamesInClass = renamesInClass;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(
        J.MethodDeclaration method, ExecutionContext ctx) {
      Set<String> methodParamsAndLocals = new HashSet<>();
      for (J parameter : method.getParameters()) {
        if (parameter instanceof J.VariableDeclarations varDecls) {
          for (J.VariableDeclarations.NamedVariable v : varDecls.getVariables()) {
            methodParamsAndLocals.add(v.getSimpleName());
          }
        }
      }
      return (J.MethodDeclaration)
          new MethodBodyFieldRefVisitor(renamesInClass, methodParamsAndLocals)
              .visitNonNull(method, ctx);
    }
  }

  private static final class MethodBodyFieldRefVisitor extends JavaIsoVisitor<ExecutionContext> {
    private final Map<String, String> renamesInClass;
    private final Set<String> scopedLocals;

    MethodBodyFieldRefVisitor(Map<String, String> renamesInClass, Set<String> outerLocals) {
      this.renamesInClass = renamesInClass;
      this.scopedLocals = new HashSet<>(outerLocals);
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(
        J.VariableDeclarations multiVariable, ExecutionContext ctx) {
      Cursor parentScope =
          getCursor()
              .dropParentUntil(
                  is ->
                      is instanceof J.ClassDeclaration
                          || is instanceof J.Block
                          || is instanceof J.MethodDeclaration
                          || is instanceof org.openrewrite.SourceFile);
      boolean isField =
          parentScope.getValue() instanceof J.Block
              && parentScope.getParent() != null
              && parentScope.getParent().getValue() instanceof J.ClassDeclaration;
      if (!isField) {
        for (J.VariableDeclarations.NamedVariable v : multiVariable.getVariables()) {
          scopedLocals.add(v.getSimpleName());
        }
      }
      return super.visitVariableDeclarations(multiVariable, ctx);
    }

    @Override
    public J.Identifier visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
      J.Identifier i = super.visitIdentifier(ident, ctx);
      String toName = renamesInClass.get(i.getSimpleName());
      if (toName != null && !scopedLocals.contains(i.getSimpleName()) && isRenameableIdent(i)) {
        if (i.getFieldType() != null) {
          i = i.withFieldType(i.getFieldType().withName(toName));
        }
        return i.withSimpleName(toName);
      }
      return i;
    }

    private boolean isRenameableIdent(J.Identifier ident) {
      var parent = getCursor().getParentTreeCursor().getValue();
      if (parent instanceof J.MethodDeclaration method && method.getName() == ident) {
        return false;
      }
      if (parent instanceof J.ClassDeclaration classDecl && classDecl.getName() == ident) {
        return false;
      }
      if (parent instanceof J.MethodInvocation method && method.getName() == ident) {
        return false;
      }
      if (parent instanceof J.VariableDeclarations variableDecls
          && variableDecls.getTypeExpression() == ident) {
        return false;
      }
      if (parent instanceof J.NewClass newClass && newClass.getClazz() == ident) {
        return false;
      }
      if (parent instanceof J.Annotation annotation && annotation.getAnnotationType() == ident) {
        return false;
      }
      return true;
    }
  }

  public static final class FieldRenamePlan {
    private final Map<String, List<ChangeFieldName>> changesByClass = new LinkedHashMap<>();
    private final Map<String, FieldRenameInfo> renameMap = new LinkedHashMap<>();

    public record FieldRenameInfo(String classFqn, String fromName, String toName) {}

    void add(String enclosingClassFqn, ChangeFieldName change) {
      changesByClass.computeIfAbsent(enclosingClassFqn, k -> new ArrayList<>()).add(change);
      renameMap.put(
          change.getHasName(),
          new FieldRenameInfo(enclosingClassFqn, change.getHasName(), change.getToName()));
      COLLECTED_RENAMES.put(change.getHasName(), change.getToName());
      appendRenameToMapFile(enclosingClassFqn, change.getHasName(), change.getToName());
    }

    List<ChangeFieldName> changesForClass(String fqn) {
      return changesByClass.getOrDefault(fqn, java.util.Collections.emptyList());
    }

    Map<String, FieldRenameInfo> renameMap() {
      return renameMap;
    }

    boolean isEmpty() {
      return changesByClass.isEmpty();
    }
  }

  private static final Path RENAME_MAP_PATH = Path.of("build", "field-renames.tsv");
  private static final Path RENAME_MAP_FALLBACK_PATH = Path.of("src/rewrite/.field-renames.tsv");

  private static void resetRenameMapFile() {
    try {
      Files.createDirectories(RENAME_MAP_PATH.getParent());
      Files.writeString(RENAME_MAP_PATH, "");
    } catch (IOException e) {
      throw new IllegalStateException("Failed to reset " + RENAME_MAP_PATH, e);
    }
  }

  private static void appendRenameToMapFile(String classFqn, String fromName, String toName) {
    try {
      Files.createDirectories(RENAME_MAP_PATH.getParent());
      Files.writeString(
          RENAME_MAP_PATH,
          classFqn + '\t' + fromName + '\t' + toName + '\n',
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to append to " + RENAME_MAP_PATH, e);
    }
  }

  static void writeRenameMapFile(Map<String, FieldRenamePlan.FieldRenameInfo> renames) {
    try {
      writeRenameMapFileTo(RENAME_MAP_PATH, renames);
      writeRenameMapFileTo(RENAME_MAP_FALLBACK_PATH, renames);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write field rename map", e);
    }
  }

  private static void writeRenameMapFileTo(
      Path path, Map<String, FieldRenamePlan.FieldRenameInfo> renames) throws IOException {
    Files.createDirectories(path.getParent());
    StringBuilder content = new StringBuilder();
    for (FieldRenamePlan.FieldRenameInfo info : renames.values()) {
      content
          .append(info.classFqn())
          .append('\t')
          .append(info.fromName())
          .append('\t')
          .append(info.toName())
          .append('\n');
    }
    Files.writeString(path, content.toString());
  }
}
