package app.freerouting.io;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Unit;
import app.freerouting.settings.RouterSettings;

/**
 * Lightweight, immutable snapshot of the information that can be extracted from a board design
 * file's header and structure sections without constructing a full {@link
 * app.freerouting.board.BasicBoard}.
 *
 * <p>All fields map one-to-one to file format tokens:
 *
 * <ul>
 *   <li>{@code hostCad} — the CAD tool that created the file (e.g. "KiCad", "EAGLE")
 *   <li>{@code hostVersion} — version of the CAD tool
 *   <li>{@code layerCount} — number of layers in the design
 *   <li>{@code unit} — the design units (mm, mil, or um)
 *   <li>{@code resolution} — the resolution/scale factor for coordinate mapping
 *   <li>{@code snapAngle} — angle restriction for routing (e.g. 45°, 90°)
 *   <li>{@code routerSettings} — nullable; only present when autoroute settings are available
 * </ul>
 *
 * <p>This record is used by both DSN and KiCad JSON readers to provide consistent metadata access
 * across different file formats.
 */
public record BoardMetadata(
    String hostCad,
    String hostVersion,
    int layerCount,
    Unit unit,
    int resolution,
    AngleRestriction snapAngle,
    RouterSettings routerSettings) {}
