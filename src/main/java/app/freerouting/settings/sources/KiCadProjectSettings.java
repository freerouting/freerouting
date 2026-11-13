package app.freerouting.settings.sources;

import app.freerouting.io.kicad.KiCadProject;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Router settings taken from a KiCad project file ({@code .kicad_pro}): the larger of its minimum
 * track width and minimum connection width becomes {@code neck_width_um}, the narrowest trace the
 * router may use. It ranks above the DSN file, which does not carry that minimum, and below every
 * setting the user gives (CLI, environment, GUI, API), so an explicit {@code
 * --router.neck_width_um} still wins.
 */
public class KiCadProjectSettings implements SettingsSource {

  private static final int PRIORITY = 25;
  private final RouterSettings settings = new RouterSettings();
  private final String sourceName;

  public KiCadProjectSettings(KiCadProject project) {
    this.sourceName = project.sourceName();
    if (project.narrowestTraceWidthUm() != null) {
      this.settings.neckWidthUm = project.narrowestTraceWidthUm();
    }
  }

  @Override
  public RouterSettings getSettings() {
    return settings;
  }

  @Override
  public String getSourceName() {
    return "KiCad project file: " + sourceName;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
