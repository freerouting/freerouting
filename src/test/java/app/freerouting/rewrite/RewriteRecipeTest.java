package app.freerouting.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

class RewriteRecipeTest {

  @Test
  void parsesThreeColumnFieldRenameMap() {
    Map<String, String> renames =
        ApplyCollectedFieldRenames.parseRenameMapLines(
            List.of(
                "",
                "app.example.Board\told_field\tnewField",
                "legacy_field\tlegacyField",
                "invalid"));

    assertEquals(Map.of("old_field", "newField", "legacy_field", "legacyField"), renames);
  }

  @Test
  void objectCollisionRecipeRenamesLegacyNetMethod() {
    String source =
        """
        package app.freerouting.rules;

        class Net {
          NetClass get_class() {
            return null;
          }
        }

        class NetClass {}
        """;
    var compilationUnit =
        JavaParser.fromJavaVersion().build().parse(source).findFirst().orElseThrow();

    J.CompilationUnit rewritten =
        (J.CompilationUnit)
            new RenameObjectCollidingMethods()
                .getVisitor()
                .visit(compilationUnit, new InMemoryExecutionContext(Throwable::printStackTrace));
    String rewrittenSource = rewritten.printAll();

    assertTrue(rewrittenSource.contains("getNetClass()"));
    assertEquals(1, countOccurrences(rewrittenSource, "getNetClass()"));
  }

  private static int countOccurrences(String text, String value) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(value, index)) >= 0) {
      count++;
      index += value.length();
    }
    return count;
  }
}
