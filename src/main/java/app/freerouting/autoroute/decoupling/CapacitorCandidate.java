package app.freerouting.autoroute.decoupling;

import java.util.List;

/**
 * Immutable candidate evaluation for a two-pin SMD component.
 *
 * @param componentId the component ID
 * @param pinIdA the first pin item ID (guaranteed pinIdA &lt; pinIdB)
 * @param pinIdB the second pin item ID
 * @param netNumberA electrical net number of pin A
 * @param netNumberB electrical net number of pin B
 * @param railNetNumber resolved power rail net number (-1 if unresolved)
 * @param groundNetNumber resolved ground return net number (-1 if unresolved)
 * @param layer copper layer of the component pads
 * @param padAreaA area of pad A bounding box in square board units
 * @param padAreaB area of pad B bounding box in square board units
 * @param score overall confidence score (0 to 100)
 * @param verdict classification verdict (DECOUPLING, REVIEW, REJECTED)
 * @param reasonMask bitmask of {@link ReasonCode} ordinals explaining the decision
 * @param audit reference designator audit outcome
 * @param owners ordered list of nearby candidate IC supply load pins
 */
public record CapacitorCandidate(
    int componentId,
    int pinIdA,
    int pinIdB,
    int netNumberA,
    int netNumberB,
    int railNetNumber,
    int groundNetNumber,
    int layer,
    long padAreaA,
    long padAreaB,
    int score,
    Verdict verdict,
    long reasonMask,
    AuditResult audit,
    List<OwnerCandidate> owners) {

  public CapacitorCandidate {
    owners = owners == null ? List.of() : List.copyOf(owners);
  }
}
