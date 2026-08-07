package app.freerouting.rewrite;

import java.util.Map;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * Renames instance field declarations only, leaving parameters and locals unchanged.
 */
final class RenameFieldDeclarationsVisitor extends JavaIsoVisitor<ExecutionContext> {

  private final Map<String, String> renames;

  RenameFieldDeclarationsVisitor(Map<String, String> renames) {
    this.renames = renames;
  }

  @Override
  public J.VariableDeclarations.NamedVariable visitVariable(
      J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
    if (!isInstanceField(getCursor(), variable)) {
      return variable;
    }
    String newName = renames.get(variable.getSimpleName());
    if (newName == null || newName.equals(variable.getSimpleName())) {
      return variable;
    }
    return variable.withName(variable.getName().withSimpleName(newName));
  }

  private static boolean isInstanceField(
      Cursor cursor, J.VariableDeclarations.NamedVariable variable) {
    JavaType.Variable type = variable.getVariableType();
    if (type == null || type.hasFlags(Flag.Static, Flag.Final)) {
      return false;
    }
    Cursor parentScope =
        cursor.dropParentUntil(
            is ->
                is instanceof J.ClassDeclaration
                    || is instanceof J.Block
                    || is instanceof J.MethodDeclaration
                    || is instanceof org.openrewrite.SourceFile);
    return parentScope.getValue() instanceof J.Block
        && parentScope.getParent() != null
        && parentScope.getParent().getValue() instanceof J.ClassDeclaration;
  }
}
