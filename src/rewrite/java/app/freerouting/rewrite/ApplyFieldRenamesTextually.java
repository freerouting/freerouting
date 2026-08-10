package app.freerouting.rewrite;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

/**
 * Renames field reference tokens from a field rename map using safe text replacement.
 *
 * <p>OpenRewrite {@code ChangeFieldName} misses many references when type attribution is absent
 * (e.g. inherited {@code bounding_box} in a subclass). This pass closes that gap for Phase 2a
 * without renaming method calls or unrelated local variables.
 */
public class ApplyFieldRenamesTextually extends JavaIsoVisitor<ExecutionContext> {

  private final Map<String, String> renames;

  /** Creates a visitor that applies the supplied field renames textually. */
  public ApplyFieldRenamesTextually(Map<String, String> renames) {
    this.renames = renames;
  }

  @Override
  public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
    if (!(tree instanceof JavaSourceFile cu)) {
      return (J) tree;
    }
    String original = cu.print();
    String updated = FieldRenameTextReplacer.apply(original, renames);
    if (updated.equals(original)) {
      return cu;
    }
    SourceFile parsed =
        JavaParser.fromJavaVersion().build().parse(updated).findFirst().orElseThrow();
    if (!(parsed instanceof JavaSourceFile reparsed)) {
      return cu;
    }
    return reparsed.withSourcePath(cu.getSourcePath()).withMarkers(cu.getMarkers());
  }
}
