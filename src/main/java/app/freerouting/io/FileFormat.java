package app.freerouting.io;

/** Supported board and routing file formats. */
public enum FileFormat {
  UNKNOWN,
  DSN,
  FRB,
  SES,
  RULES,
  SCR,
  DRC_JSON,
  KICAD_DESIGN_JSON,
  KICAD_SESSION_JSON
}
