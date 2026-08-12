package app.freerouting.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architectural boundaries for major Freerouting modules.
 *
 * <p>The rules are grouped in two buckets:
 *
 * <ul>
 *   <li>Strict boundaries already expected to hold.
 *   <li>Frozen boundaries that document current debt and prevent further drift.
 * </ul>
 */
class ModuleBoundariesArchTest {

  /**
   * Pipeline + headless-support packages that must stay independent of the GUI layer, Swing, and
   * AWT UI types (only {@code java.awt.geom} is whitelisted). See the SoC plan §1.1 (D15).
   */
  private static final String[] PIPELINE_SUPPORT_PACKAGES = {
    "app.freerouting.board..",
    "app.freerouting.rules..",
    "app.freerouting.autoroute..",
    "app.freerouting.drc..",
    "app.freerouting.geometry..",
    "app.freerouting.datastructures..",
    "app.freerouting.settings..",
    "app.freerouting.logger..",
    "app.freerouting.debug..",
    "app.freerouting.util..",
    "app.freerouting.io..",
    "app.freerouting.core.."
  };

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
            "app.freerouting.gui..", "app.freerouting.interactive..", "app.freerouting.api..")
        .check(classes);
  }

  @Test
  void settingsLoggerAndDebugMustStayIndependentFromUiAndApiLayers() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage(
            "app.freerouting.settings..", "app.freerouting.logger..", "app.freerouting.debug..")
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
        .resideInAnyPackage(
            "app.freerouting.core..", "app.freerouting.board..", "app.freerouting.autoroute..")
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
        .because(
            "interactive is a GUI-session state machine and should not leak to"
                + " headless/service modules")
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
        .because(
            "specctra parser internals are implementation details; only io packages"
                + " (specctra, kicad) may access them")
        .check(classes);
  }

  // ----------------------------------------------------------------------------------------------
  // SoC GUI separation & accessibility (plan §1.1 / §12). Added in Phase 1.
  // Frozen rules carry an exact violation baseline (archunit_store); strict rules must stay green.
  // ----------------------------------------------------------------------------------------------

  /**
   * F1 (frozen): pipeline/support packages must not depend on Swing. Owner: GUI SoC initiative.
   * Removal: Phase 4 (RoutingJob file chooser, datastructures.FileFilter) + Phase 12 cleanup
   * (util.TextManager, io).
   */
  @Test
  void pipelineMustNotDependOnSwing() {
    JavaClasses classes = importMainClasses();
    FreezingArchRule.freeze(
            noClasses()
                .that()
                .resideInAnyPackage(PIPELINE_SUPPORT_PACKAGES)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("javax.swing..")
                .because(
                    "pipeline/support packages must stay headless-safe and must not use Swing (D15)"))
        .check(classes);
  }

  /**
   * F2 (frozen): pipeline/support packages must not depend on AWT UI types; only {@code
   * java.awt.geom} is whitelisted. Owner: GUI SoC initiative. Removal: Phase 6 (board paint) +
   * Phase 7 (autoroute diagnostics) + Phase 12 (util.TextManager fonts).
   */
  @Test
  void pipelineMustNotDependOnAwtUiTypes() {
    JavaClasses classes = importMainClasses();
    FreezingArchRule.freeze(
            noClasses()
                .that()
                .resideInAnyPackage(PIPELINE_SUPPORT_PACKAGES)
                .should()
                .dependOnClassesThat(
                    resideInAnyPackage("java.awt..").and(resideOutsideOfPackage("java.awt.geom..")))
                .because(
                    "only java.awt.geom is whitelisted in pipeline/support packages (D15); AWT UI"
                        + " types are GUI concerns"))
        .check(classes);
  }

  /**
   * F3 (frozen): board/autoroute must not depend on boardgraphics (rendering). Owner: GUI SoC
   * initiative. Removal: Phase 6/10 (board rendering inversion; move to gui.rendering).
   */
  @Test
  void boardAndAutorouteMustNotDependOnBoardgraphics() {
    JavaClasses classes = importMainClasses();
    FreezingArchRule.freeze(
            noClasses()
                .that()
                .resideInAnyPackage("app.freerouting.board..", "app.freerouting.autoroute..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("app.freerouting.boardgraphics..")
                .because(
                    "domain must not paint; rendering is inverted into the GUI layer (Phase 6/10)"))
        .check(classes);
  }

  /**
   * R4 (strict): gui subpackages must be free of dependency cycles. Trivially green until Phases
   * 8-10 introduce gui.interactive/gui.session/gui.rendering; must stay green after Phase 9 (D27/D30).
   */
  @Test
  void guiSlicesMustBeFreeOfCycles() {
    JavaClasses classes = importMainClasses();
    slices()
        .matching("app.freerouting.gui.(*)..")
        .should()
        .beFreeOfCycles()
        .allowEmptyShould(true) // gui has no subpackages until Phases 8-10; stays green until then
        .check(classes);
  }

  /**
   * R5 (strict): pipeline/support packages must not depend on the GUI layer. Currently green (io/util
   * gap closed); complements the narrower existing rules.
   */
  @Test
  void pipelineMustNotDependOnGui() {
    JavaClasses classes = importMainClasses();
    noClasses()
        .that()
        .resideInAnyPackage(PIPELINE_SUPPORT_PACKAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui..")
        .because("pipeline/support packages must not depend on the GUI layer (SoC plan §1.1)")
        .check(classes);
  }
}
