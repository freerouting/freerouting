package app.freerouting.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Architectural boundaries for major Freerouting modules.
 *
 * <p>The rules are grouped in two buckets:
 *
 * <ul>
 *   <li>Strict boundaries already expected to hold.
 *   <li>Strict boundaries that prevent architectural drift.
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
    "app.freerouting.core..",
    "app.freerouting.analytics.."
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
            "app.freerouting.gui..", "app.freerouting.gui.interactive..", "app.freerouting.api..")
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
            "app.freerouting.gui.interactive..",
            "app.freerouting.api..",
            "app.freerouting.management..",
            "app.freerouting.analytics..")
        .check(classes);
  }

  @Test
  void apiAndManagementMustNotDependOnGuiBoardManagerOrInteractiveStateMachine() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage(
            "app.freerouting.api..", "app.freerouting.management..", "app.freerouting.analytics..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.gui.workspace.GuiBoardManager")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.gui.interactive.InteractiveState")
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
        .resideInAnyPackage("app.freerouting.gui..", "app.freerouting.gui.interactive..")
        .check(classes);
  }

  @Test
  void apiAndManagementShouldNotDependOnGuiEnumsOrTypes() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideInAnyPackage(
            "app.freerouting.api..", "app.freerouting.management..", "app.freerouting.analytics..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui..", "app.freerouting.gui.rendering..")
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
        .haveFullyQualifiedName("app.freerouting.gui.workspace.GuiBoardManager")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("app.freerouting.gui.interactive.InteractiveState")
        .because("core services should stay headless-ready and not require GUI manager types")
        .check(classes);
  }

  @Test
  void guiStateMachineShouldOnlyBeUsedFromGuiAndInteractiveLayers() {
    JavaClasses classes = importMainClasses();

    noClasses()
        .that()
        .resideOutsideOfPackages("app.freerouting.gui..", "app.freerouting.gui.interactive..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("app.freerouting.gui.interactive..")
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
  // Strict rules stay green as the GUI/headless separation evolves.
  // ----------------------------------------------------------------------------------------------

  /**
   * F1 (strict): pipeline/support packages must not depend on Swing. The text and file-conversion
   * boundaries are enforced here so headless routing remains safe.
   */
  @Test
  void pipelineMustNotDependOnSwing() {
    JavaClasses classes = importMainClasses();
    noClasses()
        .that()
        .resideInAnyPackage(PIPELINE_SUPPORT_PACKAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("javax.swing..")
        .because("pipeline/support packages must stay headless-safe and must not use Swing (D15)")
        .check(classes);
  }

  /**
   * F2 (strict): pipeline/support packages must not depend on AWT UI types; only {@code
   * java.awt.geom} is whitelisted. Rendering and font concerns belong to the GUI layer.
   */
  @Test
  void pipelineMustNotDependOnAwtUiTypes() {
    JavaClasses classes = importMainClasses();
    noClasses()
        .that()
        .resideInAnyPackage(PIPELINE_SUPPORT_PACKAGES)
        .should()
        .dependOnClassesThat(
            resideInAnyPackage("java.awt..").and(resideOutsideOfPackage("java.awt.geom..")))
        .because(
            "only java.awt.geom is whitelisted in pipeline/support packages (D15); AWT UI"
                + " types are GUI concerns")
        .check(classes);
  }

  /** F3 (strict): pipeline code must not depend on GUI rendering. */
  @Test
  void boardAndAutorouteMustNotDependOnGuiRendering() {
    JavaClasses classes = importMainClasses();
    noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.board..", "app.freerouting.autoroute..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui.rendering..")
        .because("domain must not paint; rendering is owned by the GUI layer (D10/D26)")
        .check(classes);
  }

  /**
   * R4 (strict): gui subpackages must be free of dependency cycles. Trivially green until Phases
   * 8-10 introduce gui.interactive/gui.workspace/gui.rendering; must stay green after Phase 9
   * (D27/D30).
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

  /** D27/D30: the workspace facade must never name concrete interactive states. */
  @Test
  void guiWorkspaceMustNotDependOnConcreteInteractiveStates() {
    JavaClasses classes = importMainClasses();
    noClasses()
        .that()
        .resideInAnyPackage("app.freerouting.gui.workspace..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("app.freerouting.gui.interactive..")
        .because("workspace owns opaque handles; views bootstrap concrete states (D27/D30)")
        .check(classes);
  }

  /** Background workspace workers may publish events, but must not reach Swing or window owners. */
  @Test
  void guiWorkspaceWorkersMustUseWorkspacePortsForPresentation() {
    JavaClasses classes = importMainClasses();
    noClasses()
        .that()
        .haveFullyQualifiedName("app.freerouting.gui.workspace.InteractiveActionThread")
        .or()
        .haveFullyQualifiedName("app.freerouting.gui.workspace.AutorouterAndRouteOptimizerThread")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "javax.swing..",
            "app.freerouting.gui.BoardFrame",
            "app.freerouting.gui.BoardPanel",
            "app.freerouting.gui.workspace.GuiBoardManager")
        .because(
            "background workers must publish workspace events; only the EDT adapter may reach Swing"
                + " or window-owned GUI state")
        .check(classes);
  }

  /**
   * R5 (strict): pipeline/support packages must not depend on the GUI layer. Currently green
   * (io/util gap closed); complements the narrower existing rules.
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
