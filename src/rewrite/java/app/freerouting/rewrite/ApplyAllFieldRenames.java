package app.freerouting.rewrite;

import java.util.Map;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;

/**
 * Applies field declaration and reference renames from a global rename map using OpenRewrite AST.
 */
public class ApplyAllFieldRenames extends JavaIsoVisitor<ExecutionContext> {

  private final Map<String, String> renames;

  /** Creates a visitor that applies the supplied field renames. */
  public ApplyAllFieldRenames(Map<String, String> renames) {
    this.renames = renames;
  }

  @Override
  public J.VariableDeclarations.NamedVariable visitVariable(
      J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
    J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
    String toName = renames.get(variable.getSimpleName());
    if (toName != null && variable.isField(getCursor())) {
      if (v.getVariableType() != null) {
        v = v.withVariableType(v.getVariableType().withName(toName));
      }
      v = v.withName(v.getName().withSimpleName(toName));
    }
    if (variable.getPadding().getInitializer() != null) {
      v =
          v.getPadding()
              .withInitializer(
                  visitLeftPadded(
                      variable.getPadding().getInitializer(),
                      JLeftPadded.Location.VARIABLE_INITIALIZER,
                      ctx));
    }
    return v;
  }

  @Override
  public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
    J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
    if (f.getName() instanceof J.Identifier nameIdent) {
      String toName = renames.get(nameIdent.getSimpleName());
      if (toName != null && isRenameableFieldReference(nameIdent)) {
        J.Identifier renamed = nameIdent.withSimpleName(toName);
        if (nameIdent.getFieldType() != null) {
          renamed = renamed.withFieldType(nameIdent.getFieldType().withName(toName));
        }
        f = f.withName(renamed);
      }
    }
    return f;
  }

  @Override
  public J.Identifier visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
    J.Identifier i = super.visitIdentifier(ident, ctx);
    String toName = renames.get(i.getSimpleName());
    if (toName == null || !isRenameableFieldReference(i)) {
      return i;
    }
    if (i.getFieldType() != null) {
      i = i.withFieldType(i.getFieldType().withName(toName));
    }
    return i.withSimpleName(toName);
  }

  private boolean isRenameableFieldReference(J.Identifier ident) {
    // If type information is available, check if the variable's owner is a method (local/param)
    JavaType.Variable fieldType = ident.getFieldType();
    if (fieldType != null && fieldType.getOwner() instanceof JavaType.Method) {
      return false;
    }

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
    if (parent instanceof J.VariableDeclarations.NamedVariable namedVar
        && namedVar.getName() == ident
        && !namedVar.isField(getCursor().getParentTreeCursor())) {
      return false;
    }
    return true;
  }
}
