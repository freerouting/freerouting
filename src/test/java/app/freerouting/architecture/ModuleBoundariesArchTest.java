package app.freerouting.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
  void settingsLoggerAndDebugMustStayIndependentFromUiAndApiLayers() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage(
            "app.freerouting.settings..",
            "app.freerouting.logger..",
            "app.freerouting.debug..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "app.freerouting.gui..",
            "app.freerouting.interactive..",
            "app.freerouting.api..",
            "app.freerouting.management..")
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
  void coreBoardAutorouteMustNotDependOnGuiOrInteractive() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.core..", "app.freerouting.board..", "app.freerouting.autoroute..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui..", "app.freerouting.interactive..")
        .check(classes);
  }

  @Test
  void apiAndManagementShouldNotDependOnGuiEnumsOrTypes() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.api..", "app.freerouting.management..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui..", "app.freerouting.boardgraphics..")
        .check(classes);
  }

  @Test
  void coreShouldNotUseGuiBoardManagerDirectly() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAPackage("app.freerouting.core..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.interactive.GuiBoardManager")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.interactive.InteractiveState")
        .because("core services should stay headless-ready and not require GUI manager types")
        .check(classes);
  }

  @Test
  void guiStateMachineShouldOnlyBeUsedFromGuiAndInteractiveLayers() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideOutsideOfPackages("app.freerouting.gui..", "app.freerouting.interactive..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("app.freerouting.interactive..")
        .because("interactive is a GUI-session state machine and should not leak to headless/service modules")
        .check(classes);
  }

  @Test
  void specctraParserInternalsShouldNotLeakOutsideIoSpecctra() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideOutsideOfPackage("app.freerouting.io..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("app.freerouting.io.specctra.parser..")
        .because("specctra parser internals are implementation details; only io packages (specctra, kicad) may access them")
        .check(classes);
  }
}