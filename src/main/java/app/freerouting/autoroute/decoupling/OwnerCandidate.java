package app.freerouting.autoroute.decoupling;

/**
 * Candidate IC supply load pin that may be decoupled by a capacitor candidate.
 *
 * @param pinId the unique item ID of the IC supply pin
 * @param componentId the component ID of the IC
 * @param componentPinCount total number of pins on the IC (e.g. 5 for an LDO, 200 for an MCU)
 * @param distanceSquared squared Euclidean distance between capacitor and IC pin in board units
 */
public record OwnerCandidate(
    int pinId, int componentId, int componentPinCount, long distanceSquared) {}
