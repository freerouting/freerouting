package app.freerouting.designforms;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Permanent architectural guard ensuring that the {@code designforms.specctra} I/O layer
 * has zero dependency on the {@code interactive}, {@code gui}, or {@code management} packages.
 *
 * <p>This test exists to prevent boundary violations from being re-introduced as the
 * codebase evolves. If it ever fails, the most likely cause is one of:
 * <ul>
 *   <li>A new class in {@code designforms.specctra} importing a GUI/management type</li>
 *   <li>A refactored method accepting a {@code BoardManager} or {@code GuiBoardManager}</li>
 *   <li>A new {@code @Deprecated} bridge that was not yet migrated</li>
 * </ul>
 *
 * <p>Fix: move the offending logic to the appropriate layer (the {@code interactive} or
 * {@code gui} package), and have the {@code designforms.specctra} code accept only domain
 * types ({@code BasicBoard}, {@code RoutingBoard}, stream types, etc.).
 */
class SpecctraPackageArchTest {

  @Test
  void specctraPackageHasNoInteractiveOrGuiDependencies() {
    JavaClasses classes = new ClassFileImporter()
        .importPackages("app.freerouting.designforms.specctra");

    ArchRuleDefinition.noClasses()
        .that().resideInAPackage("app.freerouting.designforms.specctra..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "app.freerouting.interactive..",
            "app.freerouting.gui..",
            "app.freerouting.management.."
        )
        .check(classes);
  }
}

