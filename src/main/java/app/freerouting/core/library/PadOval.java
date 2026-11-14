package app.freerouting.core.library;

import app.freerouting.geometry.planar.FloatPoint;
import java.io.Serializable;

/**
 * The copper of a round or oval pad, as KiCad writes it: a wide path of one or two points. It is
 * every point within {@code radius} of the segment from {@code start} to {@code end} (a circle when
 * they are the same point). The padstack keeps the bounding octagon of this copper as its shape,
 * for the search trees and the maze; clearance measurements use this exact copper instead.
 *
 * @param start one end of the centre line, in board units, relative to the pad's origin
 * @param end the other end of the centre line
 * @param radius half the width of the path, in board units
 */
public record PadOval(FloatPoint start, FloatPoint end, double radius) implements Serializable {}
