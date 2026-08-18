package app.freerouting.gui.controls;

/**
 * Translated labels supplied by the owner of a reusable {@link ProgressPanel}.
 *
 * <p>Keeping labels outside the component avoids adding a second progress resource bundle. Owners
 * can use the existing translated text for their window or provide labels for an embedded surface.
 */
public record ProgressLabels(
    String rootName,
    String statusName,
    String phaseName,
    String countersName,
    String progressName,
    String cancelText) {

  /** Validates that each supplied translated label is usable by assistive technology. */
  public ProgressLabels {
    rootName = requireText(rootName, "rootName");
    statusName = requireText(statusName, "statusName");
    phaseName = requireText(phaseName, "phaseName");
    countersName = requireText(countersName, "countersName");
    progressName = requireText(progressName, "progressName");
    cancelText = requireText(cancelText, "cancelText");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
