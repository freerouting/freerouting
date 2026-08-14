# DSN Fixture Coverage Map

This is the Phase 0/1 DSN coverage inventory for the current repository tree. It was derived
mechanically from the 147 `*.dsn` files under `fixtures/` and references found in
`src/test/java/**/*.java`.

Classification is based on executable or fixture-path references, not prose mentions:

- `ACTIVE-SOLE` — exactly one active Java test method/path references the file.
- `ACTIVE-MULTI` — more than one active Java test method/path references the file.
- `DISABLED-ONLY` — references exist only from `@Disabled` test methods.
- `UNREFERENCED` — no Java test reference was found.

`active` means not `@Disabled`; `@Tag("slow")` tests remain active for this inventory even though
the default test task may exclude them. `setUp` entries identify a shared fixture-loading path used
by the test class. Methods are shown as `ClassName#methodName`; paths are relative to
`src/test/java/`.

| Fixture filename | Owning Java test class/method(s) | Coverage |
| --- | --- | --- |
| `Issue015-StackOverflow.dsn` | — | `UNREFERENCED` |
| `Issue022-AutoRouter_interrupted.dsn` | — | `UNREFERENCED` |
| `Issue026-J2_reference.dsn` | `fixtures/BendCostRoutingTest#routingWithBendCosts`; `fixtures/J2ReferenceRoutingTest#issue026AutorouterInterruptedAndConnectionsNotFound`; `fixtures/MaxFanoutOptimizerSettingsTest#optimizerMaxPassesAndMaxItems`; `fixtures/MaxItemsRoutingTest#maxItemsLimit`; `fixtures/MaxPassesSettingRoutingTest#issue522MaxPassesSettingIsRespected`; `io/specctra/SesRoundTripTest#sesWriterProducesValidHeader` | `ACTIVE-MULTI` |
| `Issue027-zMRETestFixture.dsn` | — | `UNREFERENCED` |
| `Issue029-hw48na.dsn` | `gui/Issue029LoadPhaseTimingTest#profileAllPhases`; `io/specctra/RulesRoundTripTest#rulesRoundTrip`, `#invalidRulesFileReturnsFalse`, `#readExistingRulesFixture`, `#loadingProducesWarningsForDegenerateWires` | `ACTIVE-MULTI` |
| `Issue034-Green14SegLED.dsn` | `io/specctra/DsnReaderTest#readBoardLoadsIssue034WithMultipleBoundaryPaths` | `ACTIVE-SOLE` |
| `Issue035-ReadPlaceScope.dsn` | — | `UNREFERENCED` |
| `Issue039-bug-design.dsn` | — | `UNREFERENCED` |
| `Issue054-tairakb.dsn` | — | `UNREFERENCED` |
| `Issue066-Project_GP8B.dsn` | `fixtures/MultiLayerBoardRoutingTest#test4layerBoardIssue066LayerCountAndInnerLayerUsage`; `settings/sources/DsnFileSettingsTest#dsnFileSettingsReads4LayerCountFrom4LayerBoardWithoutAutorouteBlock` | `ACTIVE-MULTI` |
| `Issue069-TestSensel/TestSensel-KiCad6.dsn` | — | `UNREFERENCED` |
| `Issue069-TestSensel/TestSensel.dsn` | — | `UNREFERENCED` |
| `Issue070-Autorouter_FQ101_PCB_2022-05-13.dsn` | — | `UNREFERENCED` |
| `Issue093-interf_u.dsn` | — | `UNREFERENCED` |
| `Issue102-Mars-64-revE-rot00.dsn` | — | `UNREFERENCED` |
| `Issue103-Board-Routed.dsn` | — | `UNREFERENCED` |
| `Issue103-Board-Unrouted.dsn` | — | `UNREFERENCED` |
| `Issue107-freq_teiler_200kHz_kicad.dsn` | — | `UNREFERENCED` |
| `Issue107-freq_teiler_200kHz_kicad_bad.dsn` | — | `UNREFERENCED` |
| `Issue110-Pajalnaja_stancija.dsn` | — | `UNREFERENCED` |
| `Issue110-RelayModule.dsn` | — | `UNREFERENCED` |
| `Issue110-testPCBSpecctraFile.dsn` | — | `UNREFERENCED` |
| `Issue110-testProjectFromFreeroutingBugTest.dsn` | — | `UNREFERENCED` |
| `Issue110-testProjectFromFreeroutingBugTest01.dsn` | — | `UNREFERENCED` |
| `Issue110-Паяльная станция.dsn` | — | `UNREFERENCED` |
| `Issue113-Protein.dsn` | — | `UNREFERENCED` |
| `Issue143-rpi_splitter.dsn` | `io/specctra/DsnFileSettingsTest#dsnFileSettingsReturnsNonNullSettings`, `#dsnFileSettingsPriorityIs20`, `#dsnFileSettingsSourceNameContainsFilename`; `io/specctra/DsnReaderMetadataTest#readMetadataExtractsLayerCount`, `#readMetadataPopulatesUnit`; `io/specctra/DsnReaderTest#readBoardReturnsSuccess`, `#readBoardSetsHostCad`; `io/specctra/DsnWriterTest#writesValidDsnHeader`, `#roundtripPreservesLayerCount`, `#compatModeProducesOutput`, `#outputStreamContainsDataAfterWrite`; `io/specctra/SesRoundTripTest#invalidSesThrowsOnRead`, `#nullInputStreamThrowsIoException`, `#sesWriterOutputIsNonEmpty` | `ACTIVE-MULTI` |
| `Issue143-rpi_splitter_mod.dsn` | — | `UNREFERENCED` |
| `Issue145-smoothieboard.dsn` | — | `UNREFERENCED` |
| `Issue153-wavefolder.dsn` | — | `UNREFERENCED` |
| `Issue155-CH376_MCP795_Module.dsn` | — | `UNREFERENCED` |
| `Issue157-TeamAdapt-LinePCB.dsn` | — | `UNREFERENCED` |
| `Issue159-setonix_2hp-pcb.dsn` | `fixtures/SetonixRoutingTest#issue159OutOfMemoryError`; `autoroute/BoardHistoryTest#setUp` (shared load path) | `ACTIVE-MULTI` |
| `Issue163-pic_programmer.dsn` | — | `UNREFERENCED` |
| `Issue178-KeebMaker_Sofle_Choc.dsn` | — | `UNREFERENCED` |
| `Issue179-Autorouter_PCB1_2023-3-24.dsn` | — | `UNREFERENCED` |
| `Issue187-processor.Z80.dsn` | `io/specctra/DsnReaderMetadataTest#readMetadataCompletesWithinReasonableTimeOnLargeDsn` | `ACTIVE-SOLE` |
| `Issue190-processor.Z80.dsn` | — | `UNREFERENCED` |
| `Issue191-processor.Z80/processor.Z80.dsn` | — | `UNREFERENCED` |
| `Issue199-StackOverflow/Signale_Vor+Block.dsn` | — | `UNREFERENCED` |
| `Issue208-freerouting.dsn` | — | `UNREFERENCED` |
| `Issue209-split05.dsn` | — | `UNREFERENCED` |
| `Issue209-split10.dsn` | — | `UNREFERENCED` |
| `Issue214-freerouting.dsn` | — | `UNREFERENCED` |
| `Issue217-8088sbc.dsn` | — | `UNREFERENCED` |
| `Issue219-LogicBoard_smt.dsn` | — | `UNREFERENCED` |
| `Issue229-display-8-digit-hc595.dsn` | `fixtures/Display8DigitRoutingTest#issue229KeepoutZoneWasNotExportedCorrectly` | `ACTIVE-SOLE` |
| `Issue230-CNH_Functional_Tester_1.dsn` | `fixtures/HoleKeepoutClearanceTest#npthKeepoutsGetHoleEdgeClearanceClass`; `fixtures/InactiveLayerRoutingTest#issue230WiresOnInactiveLayers` | `ACTIVE-MULTI` |
| `Issue230-CNH_Functional_Tester/CNH_Functional_Tester_1.dsn` | — | `UNREFERENCED` |
| `Issue269-NoViasOnPowerPlanes/Issue269-NoViasOnPowerPlanes.dsn` | — | `UNREFERENCED` |
| `Issue269-caniot-tiny-arm.dsn` | — | `UNREFERENCED` |
| `Issue269-min_fr_test/min_fr_test.dsn` | — | `UNREFERENCED` |
| `Issue269-min_fr_test/min_fr_test_no_quotes.dsn` | — | `UNREFERENCED` |
| `Issue269-z10_module.dsn` | — | `UNREFERENCED` |
| `Issue270-non-ansi_bracket.dsn` | — | `UNREFERENCED` |
| `Issue283-UnconnectedTracesUnderPads/Natural_Tone_Preamp.dsn` | — | `UNREFERENCED` |
| `Issue289-Autorouter_PCB_FHT-8086_2024-03-08.dsn` | `fixtures/MultiLayerBoardRoutingTest#test6layerBoardIssue289LayerCountAndInnerLayerUsage` | `ACTIVE-SOLE` |
| `Issue289-Autorouter_PCB_FHT-VGA_2024-03-25.dsn` | — | `UNREFERENCED` |
| `Issue297-myboard.dsn` | — | `UNREFERENCED` |
| `Issue313-FastTest.dsn` | — | `UNREFERENCED` |
| `Issue326-Mars-64-revE.dsn` | — | `UNREFERENCED` |
| `Issue367-Charger.dsn` | — | `UNREFERENCED` |
| `Issue367-UltraFlactyl/UltraFlactyl.dsn` | — | `UNREFERENCED` |
| `Issue368-CorneyIslandWireless/corney_island_wireless.dsn` | — | `UNREFERENCED` |
| `Issue368-CorneyIslandWireless_input_design.dsn` | — | `UNREFERENCED` |
| `Issue413-test.dsn` | `settings/sources/DsnFileSettingsTest#dsnFileSettingsReads2LayerCountFrom2LayerBoardWithoutAutorouteBlock` | `ACTIVE-SOLE` |
| `Issue420-contribution-board.dsn` | `fixtures/Issue420ContributionBoardRoutingTest#routingCompletesWithoutOutOfMemoryError`, `#optimizerCompletesWithoutOutOfMemoryError` | `ACTIVE-MULTI` |
| `Issue433-my-board.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020_bm01.dsn` | `fixtures/Dac2020BenchmarkRoutingTest#issue508Bm01First2NetsOnly`, `#issue508Bm01First43NetsOnly`, `#issue508Bm01First61NetsOnly`, `#issue508Bm01First111NetsOnly`, `#issue508Bm01First151NetsOnly`, `#issue508Bm01FirstPassOnly`, `#issue508Bm01First2PassesOnly`; `fixtures/Dac2020Bm01RoutingTest#issue508Bm01First2NetsOnly`; `fixtures/HeadlessCompleteRoutingTest#headlessRoutingCompletesWithoutInteractiveSettingsAccess`, `#headlessRoutingBoardIsNonNullAfterCompletion`; `gui/session/HeadlessRoutingTest#headlessRoutingCompletesWithoutNpeOrIllegalState`, `#headlessRoutingBoardIsNonNullAfterCompletion`; `autoroute/RoutableLayersSafetyCheckTest#testRoutingFailsWhenAllLayersDisabledCurrent`, `#testRoutingFailsWhenAllLayersDisabledV19`; `datastructures/MinAreaTreeConcurrencyTest#overlapsConcurrentQueriesDoNotCorruptStack`; `io/specctra/DsnReaderMetadataTest#readMetadataExtractsHostCad` | `ACTIVE-MULTI` |
| `Issue508-DAC2020_bm02.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020_bm04.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020_bm05.dsn` | `fixtures/Dac2020Bm05RoutingTest#issue508Bm05First2Items`, `#issue508Bm05First5Items`, `#issue508Bm05FirstPass`, `#issue508Bm05FullRouting`; `settings/SettingsMergerTest#legacyBatchModeEnablesRouterWhenJsonDisablesIt`, `#explicitRouterEnabledFalseOverridesLegacyBatchMode`, `#cliRoutableLayersDoesNotDisableRouter` (CLI argument paths) | `ACTIVE-MULTI` |
| `Issue508-DAC2020_bm06.dsn` | `fixtures/SmdPinFanoutRoutingTest#issue508Bm06` | `ACTIVE-SOLE` |
| `Issue508-DAC2020_bm07.dsn` | `fixtures/Dac2020BenchmarkRoutingTest#issue508Bm07` | `ACTIVE-SOLE` |
| `Issue508-DAC2020_bm08.dsn` | `fixtures/Dac2020BenchmarkRoutingTest#issue508Bm08`; `io/specctra/DsnReaderTest#readBoardMergesKicadDefaultIntoFreeroutingDefault` | `ACTIVE-MULTI` |
| `Issue508-DAC2020_bm09.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020_bm10.dsn` | `fixtures/SmdPinFanoutRoutingTest#issue508Bm10` | `ACTIVE-SOLE` |
| `Issue508-DAC2020_bm11.dsn` | — | `UNREFERENCED` |
| `Issue508-SMD-routing-issue-demo.dsn` | `fixtures/SmdPinFanoutRoutingTest#smdRoutingIssueDemo` | `ACTIVE-SOLE` |
| `Issue508-DAC2020/DAC2020_bm01/DAC2020_bm01.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm01/FRv1.9.0/DAC2020_bm01.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm01/FRv2.1.0/DAC2020_bm01.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm02/DAC2020_bm02.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm02/FRv1.9.0/DAC2020_bm02.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm02/FRv2.1.0/DAC2020_bm02.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm04/DAC2020_bm04.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm04/FRv1.9.0/DAC2020_bm04.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm04/FRv2.1.0/DAC2020_bm04.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm05/DAC2020_bm05.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm05/FRv1.9.0/DAC2020_bm05.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm05/FRv2.1.0/DAC2020_bm05.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm06/DAC2020_bm06.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm06/FRv1.9.0/DAC2020_bm06.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm06/FRv2.1.0/DAC2020_bm06.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm07/DAC2020_bm07.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm07/FRv1.9.0/DAC2020_bm07.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm07/FRv2.1.0/DAC2020_bm07.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm08/DAC2020_bm08.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm08/FRv1.9.0/DAC2020_bm08.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm08/FRv2.1.0/DAC2020_bm08.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm09/DAC2020_bm09.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm09/FRv1.9.0/DAC2020_bm09.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm09/FRv2.1.0/DAC2020_bm09.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm10/DAC2020_bm10.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm10/FRv1.9.0/DAC2020_bm10.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm10/FRv2.1.0/DAC2020_bm10.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm11/DAC2020_bm11.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm11/FRv1.9.0/DAC2020_bm11.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue508-DAC2020/DAC2020_bm11/FRv2.1.0/DAC2020_bm11.unrouted.dsn` | — | `UNREFERENCED` |
| `Issue555-BBD_Mars-64.dsn` | `fixtures/BbdMars64PerformanceRoutingTest#issue555RoutingPerformanceWithBbdmars64`; `drc/DesignRulesCheckerTest#testDrcReportStructure`, `#testDrcReportJsonFormat` | `ACTIVE-MULTI` |
| `Issue555-CNH_Functional_Tester_1.dsn` | `fixtures/BbdMars64PerformanceRoutingTest#issue555RoutingPerformanceWithCnhfunctionalTester1`; `fixtures/StrictDrcRoutingTest#strictDrcDoesNotAddViolationsBeyondPreExisting` | `ACTIVE-MULTI` |
| `Issue558-dev-board.dsn` | `fixtures/DevBoardClearanceRoutingTest#issue558ClearanceViolationAtBoardEdge`; `fixtures/MaxFanoutOptimizerSettingsTest#fanoutMaxPassesAndMaxItems`; `fixtures/SmdPinFanoutRoutingTest#issue558DevBoard`, `#fanoutOnlyMode` | `ACTIVE-MULTI` |
| `Issue575-drc_BBD_Mars-64_6_track_1_hole_clearance_violations.dsn` | `fixtures/DrcViolationRoutingTest#issue5756TrackAnd1HoleClearanceViolations`; `autoroute/StrictDrcEnforcementTest#ripsNewItemsWhenTheyCarryViolations`, `#keepsConnectionsWhoseNewItemsAreClean` | `ACTIVE-MULTI` |
| `Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn` | `fixtures/DrcViolationRoutingTest#issue5757UnconnectedItems`; `drc/DrcCoordinateTest#testDrcCoordinatesAreInCorrectRange`; `drc/UnconnectedItemsReproductionTest#testIssue575DrcReproductionSinglePass` | `ACTIVE-MULTI` |
| `Issue575-drc_dev-board_4_hole_clearance_violations.dsn` | `fixtures/DrcViolationRoutingTest#issue5754HoleClearanceViolations`; `board/BoardItemRenderingMetadataTest#everyBoardItemExposesNeutralSemanticMetadata`; `board/DrillHoleClearanceShapeTest#holeClearanceInflatesDrilledItemShapesInAllTreeVariants`, `#nullShapeLayersGetSynthesizedHoleObstacles`, `#zeroHoleClearanceKeepsLegacyShapes`; `drc/RatsnestClearanceHeadlessTest#incompletesAreComputableViaDesignRulesCheckerWithoutGuiFacade`, `#clearanceViolationsAreComputableViaDrcWithoutGuiFacade`, `#clearanceViolationAggregationHelpersAreHeadlessAndSeveritySorted`; `gui/ViolationsIncompletesListA11yTest#clearanceViolationsListIsAccessibleAndHasCorrectCount`, `#incompletesListIsAccessibleAndHasCorrectCount`, `#listAccessibleNamesAreTranslatedAcrossLocales`; `gui/rendering/BoardRendererOffscreenTest#rendersRepresentativeBoardIntoOffscreenImage`, `#rendersAutorouteDiagnosticSnapshotIntoOffscreenImage` | `ACTIVE-MULTI` |
| `Issue593-BBD_Mars-64.dsn` | `io/specctra/SesRoundTripTest#sesRoundTripPreservesWireCount`, `#writerOutputCanBeReadBackBySesReader`, `#endpointSnappingIsStableAndRoundTrips` | `ACTIVE-MULTI` |
| `Issue632-MiniAutoPilot/Mini Auto Pilot.dsn` | — | `UNREFERENCED` |
| `Issue649-kicad_ecc83-pp_input_board_v1.dsn` | — | `UNREFERENCED` |
| `Issue649-kicad_ecc83-pp_input_board_v2.dsn` | — | `UNREFERENCED` |
| `Issue676-ch32v-tx118s.dsn` | `fixtures/Issue676RoutingTest#issue676LayerCountCorrectAfterMergeWithoutDsnFileSettings`, `#issue676RoutingCompletesWithoutExceptions`; `fixtures/Issue676FullPassTest#issue676FullRouting3passes` | `ACTIVE-MULTI` |
| `Issue684-Autorouter_PCB1_2026-5-8.dsn` | `fixtures/Issue684MemoryLeakRoutingTest#routingCompletesWithoutOutOfMemoryError` | `ACTIVE-SOLE` |
| `Issue689-BBD_Mars-64.dsn` | — | `UNREFERENCED` |
| `Issue690-ecc83.dsn` | `fixtures/Issue690RoutingTest#issue690Ecc83` | `ACTIVE-SOLE` |
| `Issue690-kit-dev-coldfire-xilinx_5213.dsn` | `fixtures/Issue690RoutingTest#issue690KitDevColdfireXilinx` | `ACTIVE-SOLE` |
| `Issue690-sonde_xilinx.dsn` | `fixtures/Issue690RoutingTest#issue690SondeXilinx` | `ACTIVE-SOLE` |
| `Issue721-Autorouter_CE2632_HarryMu_2026-6-15.dsn` | — | `UNREFERENCED` |
| `Issue723-CombineStackOverflow.dsn` | `fixtures/CombineStackOverflowTest#combineDoesNotOverflowOnLongCollinearTrace` | `ACTIVE-SOLE` |
| `Issue730-DAC2020_bm11.dsn` | `fixtures/Dac2020Bm11FanoutTraceTest#dac2020Bm11FanoutTrace`, `#dac2020Bm11FanoutEscapeRate` | `ACTIVE-MULTI` |
| `Issue732-CM5_MINIMA_3.dsn` | — | `UNREFERENCED` |
| `Issue732-DAC2020_bm10.dsn` | — | `UNREFERENCED` |
| `Issue732-RoyalBlue54L-Feather.dsn` | — | `UNREFERENCED` |
| `Issue733-kicad_complex_hierarchy_input_design.dsn` | `fixtures/Issue733DsnJsonParityTest#sesJsonOutputKiCadComplexHierarchy`; `[disabled] fixtures/Issue733DsnJsonParityTest#dsnJsonInputParityKiCadComplexHierarchy` | `ACTIVE-SOLE` (+ disabled reference) |
| `Issue733-kicad_interf_u_input_design.dsn` | `[disabled] fixtures/Issue733DsnJsonParityTest#dsnJsonInputParityKiCadInterf` | `DISABLED-ONLY` |
| `Issue742-tastexx-pcb.dsn` | `io/specctra/SesRoundTripTest#issue742SesRoundTripsWithoutErrors` | `ACTIVE-SOLE` |
| `Issue742-tastexx-pcb/tastexx-pcb.dsn` | — | `UNREFERENCED` |
| `Issue753-CPU-85_r104.dsn` | — | `UNREFERENCED` |
| `Issue754-avionics_hub.dsn` | — | `UNREFERENCED` |
| `Issue756-minimal-hang.dsn` | — | `UNREFERENCED` |
| `Issue756-minimal-ok.dsn` | — | `UNREFERENCED` |
| `Issue756-tomu-fpga.dsn` | — | `UNREFERENCED` |
| `Issue756-tomu-fpga11.dsn` | — | `UNREFERENCED` |
| `Issue756-tomu-fpga7.dsn` | — | `UNREFERENCED` |
| `Issue756-tomu-fpga8.dsn` | — | `UNREFERENCED` |
| `Issue756-tomu-fpga9.dsn` | — | `UNREFERENCED` |
| `Issue757-minimal-soe-ok.dsn` | — | `UNREFERENCED` |
| `Issue757-minimal-soe.dsn` | — | `UNREFERENCED` |
| `empty_board.dsn` | `api/ApiRoutingTest#apiRoutingCompletesWithoutInteractiveSettingsNpe`; `api/McpEndpointsTest#customToolsFileUploadAndDownloadRunLocally`; `autoroute/BoardHistoryTest#setUp` (shared load path); `drc/RatsnestClearanceHeadlessTest#emptyBoardHasNoIncompletesAndNoViolations`; `fixtures/HeadlessCompleteRoutingTest#headlessRoutingEmptyBoardCompletesWithoutNpe`; `gui/session/GuiStartupHeadlessTest#setUp` (shared load path); `gui/session/InteractiveSettingsPropertyChangeTest#setUp` (shared load path); `gui/session/InteractiveSettingsSingletonTest#setUp` (shared load path); `gui/session/SettingsMergerGuiIntegrationTest#setUp` (shared load path); `io/specctra/DsnReaderTest#readBoardSucceedsForEmptyBoard` | `ACTIVE-MULTI` |

## Scope and limitations

- This map covers only files currently under `fixtures/` and Java references currently under
  `src/test/java/`. It does not infer coverage from filenames, issue numbers, comments, Javadoc, or
  test class names.
- Non-Java scripts, benchmark PowerShell, Gradle tasks, CI workflows, and manual command-line
  invocations are intentionally outside the owning-test map. In particular, benchmark fixture
  references under `scripts/benchmark/fixtures/` are not part of this 147-file inventory.
- Indirect fixture loaders and dynamic filenames can evade a literal reference scan. The map records
  known shared setup paths, but cannot prove every runtime-loaded filename when a value is assembled
  dynamically or supplied externally.
- Java references that name a root-level filename resolve through the test fixture helpers; they do
  not implicitly cover same-named or related nested DSN variants. Those nested variants are listed
  independently and marked `UNREFERENCED` unless a Java path names them.
- `DISABLED-ONLY` means the Java path exists but is not executed by the ordinary JUnit engine while
  disabled. Slow or GUI-tagged tests are still classified as active here.
