package app.freerouting.board;

/**
 * Neutral semantic categories for board items.
 *
 * <p>The categories describe the domain object represented by an item without prescribing how it is
 * rendered. GUI renderers may use them to select a rendering strategy while headless code can
 * inspect item families without importing GUI or AWT types.
 */
public enum BoardItemType {
  TRACE,
  PIN,
  VIA,
  OBSTACLE_AREA,
  VIA_OBSTACLE_AREA,
  CONDUCTION_AREA,
  COMPONENT_OBSTACLE_AREA,
  BOARD_OUTLINE,
  COMPONENT_OUTLINE,
  OTHER
}
