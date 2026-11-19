package app.freerouting.autoroute.decoupling;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Components;
import app.freerouting.core.library.Package;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cross-checks topological classification verdicts against component reference designators and
 * footprint packages.
 *
 * <p>Reference designator audit never promotes a candidate on its own; it acts as an audit trail
 * and sanity barrier against obvious misidentifications (e.g. resistors, diodes).
 */
public final class RefDesAudit {

  private static final Pattern CAP_NAME_PATTERN = Pattern.compile("(?i)^(C|CP|CA)\\d.*");

  private static final Pattern NON_CAP_NAME_PATTERN =
      Pattern.compile(
          "(?i)^(?:R|RN|RT|RP|L|FB|FL|BLM|IND|D|LED|Q|FET|TR|SW|S|BTN|KEY|TACT|PUSH|SWITCH|BUTTON|J|JP|HDR|P|TP|PIN|CONN|Y|X|XTAL|OSC|F|FUSE|RV|MOV|VR|RESET|K)[0-9_\\-].*");

  private static final Pattern CAP_PACKAGE_PATTERN =
      Pattern.compile("(?i).*(C0402|C0603|C0805|C1206|C1210|CAP).*");

  private static final Pattern NON_CAP_PACKAGE_PATTERN =
      Pattern.compile(
          "(?i).*(R0402|R0603|R0805|R1206|RES|DIODE|LED|SMC_D|SOD|DO-\\d|SMA|SMB|SMC|PinHeader|Header|Jumper|SolderJumper|Switch|Button|Tactile|PushButton|SlideSwitch|ToggleSwitch|SW_).*");

  private RefDesAudit() {}

  /**
   * Evaluates reference designators for candidates using the board's component repository.
   *
   * @param states evaluated candidate states
   * @param board routing board providing component metadata
   * @param config configuration parameters
   * @return total count of audit conflicts discovered
   */
  public static int audit(
      List<CapacitorScorer.EvaluationState> states, BasicBoard board, IdentifierConfig config) {
    return audit(states, board != null ? board.components : null, config);
  }

  /**
   * Evaluates reference designators for candidates using the provided {@link Components}
   * repository.
   *
   * @param states evaluated candidate states
   * @param components component repository
   * @param config configuration parameters
   * @return total count of audit conflicts discovered
   */
  public static int audit(
      List<CapacitorScorer.EvaluationState> states,
      Components components,
      IdentifierConfig config) {
    if (states == null || states.isEmpty() || !config.runRefDesAudit()) {
      return 0;
    }

    int conflicts = 0;

    for (CapacitorScorer.EvaluationState state : states) {
      if (state.verdict == Verdict.DECOUPLING) {
        Component comp = (components != null) ? components.get(state.componentId) : null;
        String compName = (comp != null && comp.name != null) ? comp.name.trim() : "";
        Package pkg = comp != null ? comp.getPackage() : null;
        String pkgName = (pkg != null && pkg.name != null) ? pkg.name.trim() : "";

        if (CAP_NAME_PATTERN.matcher(compName).matches()) {
          state.audit = AuditResult.AGREE;
        } else if (NON_CAP_NAME_PATTERN.matcher(compName).matches()) {
          state.audit = AuditResult.CONFLICT;
          conflicts++;
          state.verdict = Verdict.REJECTED;
          state.reasonMask |= ReasonCode.USER_EXCLUDED.mask();
        } else if (CAP_PACKAGE_PATTERN.matcher(pkgName).matches()) {
          state.audit = AuditResult.AGREE;
        } else if (NON_CAP_PACKAGE_PATTERN.matcher(pkgName).matches()) {
          state.audit = AuditResult.CONFLICT;
          conflicts++;
          state.verdict = Verdict.REJECTED;
          state.reasonMask |= ReasonCode.USER_EXCLUDED.mask();
        } else {
          state.audit = AuditResult.UNKNOWN;
        }
      } else {
        state.audit = AuditResult.NOT_RUN;
      }
    }

    return conflicts;
  }
}
