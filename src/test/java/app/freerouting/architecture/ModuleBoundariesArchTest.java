package app.freerouting.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architectural boundaries for major Freerouting modules.
 *
 * <p>The rules are grouped in two buckets:
 * <ul>
 *   <li>Strict boundaries already expected to hold.</li>
 *   <li>Frozen boundaries that document current debt and prevent further drift.</li>
 * </ul>
 */
class ModuleBoundariesArchTest {

  private JavaClasses importMainClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("app.freerouting..");
  }

  @Test
  void algorithmicFoundationsMustStayIndependentFromUiAndApiLayers() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage(
            "app.freerouting.rules..",
            "app.freerouting.drc..",
            "app.freerouting.geometry..",
            "app.freerouting.datastructures..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "app.freerouting.gui..",
            "app.freerouting.interactive..",
            "app.freerouting.api..")
        .check(classes);
  }

  @Test
  void apiAndManagementMustNotDependOnGuiBoardManagerOrInteractiveStateMachine() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.api..", "app.freerouting.management..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.interactive.GuiBoardManager")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.interactive.InteractiveState")
        .check(classes);
  }

  @Test
  void coreBoardAutorouteMustNotDependOnGuiOrInteractive_frozen() {
    JavaClasses classes = importMainClasses();

    ArchRule rule = noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.core..", "app.freerouting.board..", "app.freerouting.autoroute..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui..", "app.freerouting.interactive..");

    FreezingArchRule.freeze(rule).check(classes);
  }

  @Test
  void apiAndManagementShouldNotDependOnGuiEnumsOrTypes_frozen() {
    JavaClasses classes = importMainClasses();

    ArchRule rule = noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.api..", "app.freerouting.management..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui..", "app.freerouting.boardgraphics..");

    FreezingArchRule.freeze(rule).check(classes);
  }

  @Test
  void coreShouldNotUseGuiBoardManagerDirectly_frozen() {
    JavaClasses classes = importMainClasses();

    ArchRule rule = noClasses()
        .that()
        .resideInAPackage("app.freerouting.core..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.interactive.GuiBoardManager")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.interactive.InteractiveState")
        .because("core services should stay headless-ready and not require GUI manager types");

    FreezingArchRule.freeze(rule).check(classes);
  }

  @Test
  void guiStateMachineShouldOnlyBeUsedFromGuiAndInteractiveLayers_frozen() {
    JavaClasses classes = importMainClasses();

    ArchRule rule = noClasses()
        .that()
        .resideOutsideOfPackages("app.freerouting.gui..", "app.freerouting.interactive..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("app.freerouting.interactive..")
        .because("interactive is a GUI-session state machine and should not leak to headless/service modules");

    FreezingArchRule.freeze(rule).check(classes);
  }

  @Test
  void specctraParserInternalsShouldNotLeakOutsideIoSpecctra_frozen() {
    JavaClasses classes = importMainClasses();

    ArchRule rule = noClasses()
        .that()
        .resideOutsideOfPackage("app.freerouting.io.specctra..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("app.freerouting.io.specctra.parser..");

    FreezingArchRule.freeze(rule).check(classes);
  }
}
