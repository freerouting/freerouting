package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.TrackPropagation;
import app.freerouting.rules.TuningProfile;
import app.freerouting.rules.TuningProfileType;
import app.freerouting.rules.ViaDelayOverride;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration and round-trip verification tests for Specctra DSN tuning profiles and net class
 * bindings.
 */
class DsnTuningProfileTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  private static String createDsn(String networkBody) {
    return """
        (pcb test_tuning_profile.dsn
          (parser
            (string_quote ")
            (space_in_quoted_tokens on)
          )
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (layer In1.Cu (type power) (property (index 1)))
            (layer In2.Cu (type power) (property (index 2)))
            (layer B.Cu (type signal) (property (index 3)))
            (boundary
              (path pcb 0  0 0  50000 0  50000 50000  0 50000  0 0)
            )
            (rule (width 200) (clearance 150))
          )
          (placement
            (component PAD
              (place U1 5000 10000 front 0)
              (place U2 25000 10000 front 0)
            )
          )
          (library
            (image PAD
              (pin CirclePad 1 0 0)
            )
            (padstack CirclePad
              (shape (circle F.Cu 800))
              (attach off)
            )
          )
          (network
        """
        + networkBody
        + """
          )
        )
        """;
  }

  private RoutingBoard loadBoardFromString(String dsn) throws Exception {
    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result, "DSN read must succeed");
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void testParseTuningProfileWithTrackAndViaPropagation() throws Exception {
    String dsn =
        createDsn(
            """
            (tuning_profile DDR4_DIFF
              (type differential)
              (time_domain on)
              (target_impedance 90.0)
              (track_propagation
                (layer_rule F.Cu
                  (rule (width 180) (time_length_factor 62.5))
                  (top_reference In1.Cu)
                  (bottom_reference <None>)
                )
                (layer_rule B.Cu
                  (rule (width 200) (time_length_factor 65.0))
                  (top_reference In2.Cu)
                  (bottom_reference In1.Cu)
                )
              )
              (via_propagation
                (global_unit_delay 14.2)
                (override
                  (signal_layers F.Cu B.Cu)
                  (via_layers F.Cu B.Cu)
                  (delay 18.5)
                )
              )
            )
            (net DQ0 (pins U1-1 U2-1))
            (class HIGH_SPEED DQ0
              (tuning_profile DDR4_DIFF)
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    assertNotNull(board.rules.tuningProfiles);
    assertEquals(1, board.rules.tuningProfiles.count());

    TuningProfile profile = board.rules.tuningProfiles.get("DDR4_DIFF");
    assertNotNull(profile);
    assertEquals("DDR4_DIFF", profile.name());
    assertEquals(TuningProfileType.DIFFERENTIAL, profile.type());
    assertTrue(profile.enableTimeDomainTuning());
    assertEquals(90.0, profile.targetImpedanceOhms(), 1e-4);

    // Track propagations
    assertEquals(2, profile.trackPropagations().size());
    TrackPropagation tp1 = profile.trackPropagations().get(0);
    assertEquals("F.Cu", tp1.signalLayer());
    assertEquals("In1.Cu", tp1.topReference());
    assertNull(tp1.bottomReference(), "<None> must be normalized to null");
    assertEquals(180.0, board.communication.coordinateTransform.boardToDsn(tp1.trackWidth()), 1e-4);
    assertEquals(62.5, tp1.timeLengthFactor(), 1e-4);

    TrackPropagation tp2 = profile.trackPropagations().get(1);
    assertEquals("B.Cu", tp2.signalLayer());
    assertEquals("In2.Cu", tp2.topReference());
    assertEquals("In1.Cu", tp2.bottomReference());
    assertEquals(200.0, board.communication.coordinateTransform.boardToDsn(tp2.trackWidth()), 1e-4);
    assertEquals(65.0, tp2.timeLengthFactor(), 1e-4);

    // Via propagation
    assertEquals(14.2, profile.globalViaUnitDelay(), 1e-4);
    assertEquals(1, profile.viaDelayOverrides().size());
    ViaDelayOverride override = profile.viaDelayOverrides().get(0);
    assertEquals("F.Cu", override.signalLayerFrom());
    assertEquals("B.Cu", override.signalLayerTo());
    assertEquals("F.Cu", override.viaLayerFrom());
    assertEquals("B.Cu", override.viaLayerTo());
    assertEquals(18.5, override.delayPs(), 1e-4);

    // NetClass binding
    NetClass netClass = board.rules.netClasses.get("HIGH_SPEED");
    assertNotNull(netClass);
    assertEquals("DDR4_DIFF", netClass.getTuningProfileName());
    assertEquals(profile, netClass.getTuningProfile(board.rules));

    Net net = board.rules.nets.get("DQ0", 1);
    assertNotNull(net);
    assertEquals(netClass, net.getNetClass());
    assertEquals(profile, net.getNetClass().getTuningProfile(board.rules));
  }

  @Test
  void testParseAlternativeEntryAndOverrideSyntax() throws Exception {
    String dsn =
        createDsn(
            """
            (tuning_profile ALT_SYNTAX
              (type single)
              (time_domain off)
              (target_impedance 50.0)
              (track_propagation
                (entry
                  (signal_layer F.Cu)
                  (width 210)
                  (time_length_factor 58.0)
                  (top_reference none)
                  (bottom_reference In1.Cu)
                )
              )
              (via_propagation
                (global_unit_delay 10.0)
                (override
                  (signal_layer_from F.Cu)
                  (signal_layer_to B.Cu)
                  (via_layer_from F.Cu)
                  (via_layer_to B.Cu)
                  (delay 12.0)
                )
              )
            )
            (net N1 (pins U1-1 U2-1))
            (class SINGLE_ENDED N1
              (tuning_profile ALT_SYNTAX)
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    TuningProfile profile = board.rules.tuningProfiles.get("ALT_SYNTAX");
    assertNotNull(profile);
    assertEquals(TuningProfileType.SINGLE, profile.type());
    assertFalse(profile.enableTimeDomainTuning());
    assertEquals(50.0, profile.targetImpedanceOhms(), 1e-4);

    assertEquals(1, profile.trackPropagations().size());
    TrackPropagation tp = profile.trackPropagations().get(0);
    assertEquals("F.Cu", tp.signalLayer());
    assertNull(tp.topReference());
    assertEquals("In1.Cu", tp.bottomReference());
    assertEquals(210.0, board.communication.coordinateTransform.boardToDsn(tp.trackWidth()), 1e-4);
    assertEquals(58.0, tp.timeLengthFactor(), 1e-4);

    assertEquals(1, profile.viaDelayOverrides().size());
    ViaDelayOverride vo = profile.viaDelayOverrides().get(0);
    assertEquals("F.Cu", vo.signalLayerFrom());
    assertEquals("B.Cu", vo.signalLayerTo());
    assertEquals(12.0, vo.delayPs(), 1e-4);
  }

  @Test
  void testDsnRoundTripPreservesTuningProfilesAndClassAssignments() throws Exception {
    String dsn =
        createDsn(
            """
            (tuning_profile DDR4_DATA
              (type differential)
              (time_domain on)
              (target_impedance 85.0)
              (track_propagation
                (layer_rule F.Cu
                  (rule (width 190) (time_length_factor 60.0))
                  (top_reference In1.Cu)
                  (bottom_reference none)
                )
              )
              (via_propagation
                (global_unit_delay 15.0)
                (override
                  (signal_layers F.Cu B.Cu)
                  (via_layers F.Cu B.Cu)
                  (delay 22.0)
                )
              )
            )
            (net D0 (pins U1-1 U2-1))
            (class DATA_CLASS D0
              (tuning_profile DDR4_DATA)
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    TuningProfile origProfile = board.rules.tuningProfiles.get("DDR4_DATA");
    assertNotNull(origProfile);

    // Export to DSN
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "test_roundtrip_tuning_profile", false);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    // Verify exported DSN tokens
    assertTrue(
        exportedDsn.contains("tuning_profile \"DDR4_DATA\"")
            || exportedDsn.contains("tuning_profile DDR4_DATA"));
    assertTrue(exportedDsn.contains("(type differential)"));
    assertTrue(exportedDsn.contains("(time_domain on)"));
    assertTrue(exportedDsn.contains("(target_impedance 85.0)"));
    assertTrue(exportedDsn.contains("(time_length_factor 60.0)"));
    assertTrue(exportedDsn.contains("(global_unit_delay 15.0)"));
    assertTrue(exportedDsn.contains("(delay 22.0)"));
    assertTrue(
        exportedDsn.contains("(tuning_profile \"DDR4_DATA\")")
            || exportedDsn.contains("(tuning_profile DDR4_DATA)"));

    // Reload exported DSN
    RoutingBoard reloadedBoard = loadBoardFromString(exportedDsn);
    TuningProfile reloadedProfile = reloadedBoard.rules.tuningProfiles.get("DDR4_DATA");
    assertNotNull(reloadedProfile);
    assertEquals(origProfile.name(), reloadedProfile.name());
    assertEquals(origProfile.type(), reloadedProfile.type());
    assertEquals(origProfile.enableTimeDomainTuning(), reloadedProfile.enableTimeDomainTuning());
    assertEquals(origProfile.targetImpedanceOhms(), reloadedProfile.targetImpedanceOhms(), 1e-4);
    assertEquals(origProfile.globalViaUnitDelay(), reloadedProfile.globalViaUnitDelay(), 1e-4);

    assertEquals(1, reloadedProfile.trackPropagations().size());
    TrackPropagation reloadedTp = reloadedProfile.trackPropagations().get(0);
    assertEquals("F.Cu", reloadedTp.signalLayer());
    assertEquals("In1.Cu", reloadedTp.topReference());
    assertNull(reloadedTp.bottomReference());
    assertEquals(60.0, reloadedTp.timeLengthFactor(), 1e-4);

    assertEquals(1, reloadedProfile.viaDelayOverrides().size());
    ViaDelayOverride reloadedVo = reloadedProfile.viaDelayOverrides().get(0);
    assertEquals("F.Cu", reloadedVo.signalLayerFrom());
    assertEquals("B.Cu", reloadedVo.signalLayerTo());
    assertEquals(22.0, reloadedVo.delayPs(), 1e-4);

    // Check reloaded NetClass binding
    NetClass reloadedClass = reloadedBoard.rules.netClasses.get("DATA_CLASS");
    assertNotNull(reloadedClass);
    assertEquals("DDR4_DATA", reloadedClass.getTuningProfileName());
    assertEquals(reloadedProfile, reloadedClass.getTuningProfile(reloadedBoard.rules));
  }

  @Test
  void testCompatModeOmitsTuningProfileScopes() throws Exception {
    String dsn =
        createDsn(
            """
            (tuning_profile DDR4_DATA
              (type single)
              (time_domain on)
              (target_impedance 50.0)
            )
            (net D0 (pins U1-1 U2-1))
            (class DATA_CLASS D0
              (tuning_profile DDR4_DATA)
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    assertNotNull(board.rules.tuningProfiles.get("DDR4_DATA"));

    // Export in compatMode = true
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "compat_test", true);
    String exportedDsn = out.toString(StandardCharsets.UTF_8);

    assertFalse(exportedDsn.contains("tuning_profile"));
  }

  @Test
  void testMissingProfileReferenceHandledGracefully() throws Exception {
    String dsn =
        createDsn(
            """
            (net D0 (pins U1-1 U2-1))
            (class DATA_CLASS D0
              (tuning_profile NON_EXISTENT_PROFILE)
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    NetClass netClass = board.rules.netClasses.get("DATA_CLASS");
    assertNotNull(netClass);
    assertEquals("NON_EXISTENT_PROFILE", netClass.getTuningProfileName());
    assertNull(netClass.getTuningProfile(board.rules));
  }

  @Test
  void testSameLayerTransitionAndZeroDelayOverride() throws Exception {
    String dsn =
        createDsn(
            """
            (tuning_profile ZERO_DELAY
              (type single)
              (time_domain on)
              (target_impedance 0.0)
              (via_propagation
                (global_unit_delay 0.0)
                (override
                  (signal_layers F.Cu F.Cu)
                  (via_layers F.Cu B.Cu)
                  (delay 0.0)
                )
              )
            )
            """);

    RoutingBoard board = loadBoardFromString(dsn);
    TuningProfile profile = board.rules.tuningProfiles.get("ZERO_DELAY");
    assertNotNull(profile);
    assertEquals(0.0, profile.targetImpedanceOhms(), 1e-4);
    assertEquals(0.0, profile.globalViaUnitDelay(), 1e-4);

    assertEquals(1, profile.viaDelayOverrides().size());
    ViaDelayOverride vo = profile.viaDelayOverrides().get(0);
    assertEquals("F.Cu", vo.signalLayerFrom());
    assertEquals("F.Cu", vo.signalLayerTo());
    assertEquals(0.0, vo.delayPs(), 1e-4);
    assertTrue(vo.matchesSignalTransition("F.Cu", "F.Cu"));
  }
}
