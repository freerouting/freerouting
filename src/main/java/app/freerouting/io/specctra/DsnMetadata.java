package app.freerouting.io.specctra;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Unit;
import app.freerouting.settings.RouterSettings;

/**
 * Lightweight, immutable snapshot of the information that can be extracted from
 * the DSN {@code (parser ...)}, {@code (structure (layer ...))} and
 * {@code (structure (rule ...))} / {@code (autoroute ...)} scopes without
 * constructing a full {@link app.freerouting.board.BasicBoard}.
 *
 * <p>All fields map one-to-one to DSN header/structure tokens:
 * <ul>
 *   <li>{@code hostCad}        — {@code (parser (host_cad ...))}</li>
 *   <li>{@code hostVersion}    — {@code (parser (host_version ...))}</li>
 *   <li>{@code layerCount}     — number of {@code (structure (layer ...))} entries</li>
 *   <li>{@code unit}           — {@code (unit ...)} scope</li>
 *   <li>{@code resolution}     — {@code (resolution ...)} scope</li>
 *   <li>{@code snapAngle}      — {@code (structure (rule (snap_angle ...)))}</li>
 *   <li>{@code routerSettings} — nullable; only present when an {@code (autoroute ...)} scope exists</li>
 * </ul>
 */
public record DsnMetadata(
    String hostCad,
    String hostVersion,
    int layerCount,
    Unit unit,
    int resolution,
    AngleRestriction snapAngle,
    RouterSettings routerSettings
) {}

