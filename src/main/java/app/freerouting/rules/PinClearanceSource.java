package app.freerouting.rules;

/**
 * Erik Gavriluk (freerouting.net): records a pin's clearance source before DRC or tolerance copies.
 */
public record PinClearanceSource(int originalClass, int maskClearance, Integer localClearance) {}
