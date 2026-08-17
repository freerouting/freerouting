package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.GlobalSettings;
import java.io.StringReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KiCadJsonReaderTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testParseValidKiCadJson() {
    String json =
        "{\n"
            + "  \"designName\": \"TestBoard\",\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"layers\": [\n"
            + "    {\"index\": 0, \"name\": \"F.Cu\", \"type\": \"signal\"},\n"
            + "    {\"index\": 1, \"name\": \"B.Cu\", \"type\": \"signal\"}\n"
            + "  ],\n"
            + "  \"netClasses\": [\n"
            + "    {\n"
            + "      \"name\": \"Power\",\n"
            + "      \"clearance\": 0.25,\n"
            + "      \"traceWidth\": 0.5,\n"
            + "      \"viaDiameter\": 0.8,\n"
            + "      \"viaDrill\": 0.4,\n"
            + "      \"netNames\": [\"VCC\", \"GND\"]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"nets\": [\n"
            + "    {\"id\": 1, \"name\": \"VCC\", \"className\": \"Power\", "
            + "\"containsPlane\": false},\n"
            + "    {\"id\": 2, \"name\": \"GND\", \"className\": \"Power\", "
            + "\"containsPlane\": true}\n"
            + "  ],\n"
            + "  \"outline\": {\n"
            + "    \"corners\": [\n"
            + "      {\"x\": 0.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 80.0},\n"
            + "      {\"x\": 0.0, \"y\": 80.0}\n"
            + "    ],\n"
            + "    \"clearance\": 0.5\n"
            + "  },\n"
            + "  \"components\": [\n"
            + "    {\n"
            + "      \"reference\": \"R1\",\n"
            + "      \"value\": \"10k\",\n"
            + "      \"footprint\": \"Resistor_SMD:R_0805_2012Metric\",\n"
            + "      \"position\": {\"x\": 10.0, \"y\": 20.0},\n"
            + "      \"rotation\": 0.0,\n"
            + "      \"layer\": \"F.Cu\",\n"
            + "      \"pads\": [\n"
            + "        {\n"
            + "          \"name\": \"1\",\n"
            + "          \"netName\": \"VCC\",\n"
            + "          \"shape\": \"rect\",\n"
            + "          \"size\": {\"x\": 1.2, \"y\": 1.4},\n"
            + "          \"offset\": {\"x\": -1.0, \"y\": 0.0},\n"
            + "          \"layers\": [\"F.Cu\"]\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"2\",\n"
            + "          \"netName\": \"GND\",\n"
            + "          \"shape\": \"rect\",\n"
            + "          \"size\": {\"x\": 1.2, \"y\": 1.4},\n"
            + "          \"offset\": {\"x\": 1.0, \"y\": 0.0},\n"
            + "          \"layers\": [\"F.Cu\"]\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"traces\": [\n"
            + "    {\n"
            + "      \"id\": 1,\n"
            + "      \"netName\": \"VCC\",\n"
            + "      \"width\": 0.3,\n"
            + "      \"layerIndex\": 0,\n"
            + "      \"points\": [\n"
            + "        {\"x\": 5.0, \"y\": 10.0},\n"
            + "        {\"x\": 10.0, \"y\": 10.0}\n"
            + "      ]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"vias\": [\n"
            + "    {\n"
            + "      \"id\": 1,\n"
            + "      \"netName\": \"GND\",\n"
            + "      \"position\": {\"x\": 15.0, \"y\": 15.0},\n"
            + "      \"diameter\": 0.8,\n"
            + "      \"drill\": 0.4,\n"
            + "      \"startLayerIndex\": 0,\n"
            + "      \"endLayerIndex\": 1\n"
            + "    }\n"
            + "  ],\n"
            + "  \"conductionAreas\": [\n"
            + "    {\n"
            + "      \"id\": 1,\n"
            + "      \"netName\": \"GND\",\n"
            + "      \"layerIndex\": 1,\n"
            + "      \"isObstacle\": false,\n"
            + "      \"polygon\": [\n"
            + "        {\"x\": 20.0, \"y\": 20.0},\n"
            + "        {\"x\": 40.0, \"y\": 20.0},\n"
            + "        {\"x\": 40.0, \"y\": 40.0},\n"
            + "        {\"x\": 20.0, \"y\": 40.0}\n"
            + "      ]\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    BoardReadResult result = KiCadJsonReader.readBoard(new StringReader(json), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    BoardReadResult.Success success = (BoardReadResult.Success) result;
    RoutingBoard board = (RoutingBoard) success.board();

    assertNotNull(board);
    assertEquals(2, board.getLayerCount());
    assertEquals("F.Cu", board.layerStructure.layers[0].name);
    assertEquals(2, board.rules.nets.maxNetNumber());
    assertEquals("VCC", board.rules.nets.get(1).name);
    assertEquals("GND", board.rules.nets.get(2).name);
    assertTrue(board.rules.nets.get(2).containsPlane());
  }

  @Test
  void testParseKiCadJsonWithImplicitNets() {
    String json =
        "{\n"
            + "  \"designName\": \"TestImplicitBoard\",\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"layers\": [\n"
            + "    {\"index\": 0, \"name\": \"F.Cu\", \"type\": \"signal\"},\n"
            + "    {\"index\": 1, \"name\": \"B.Cu\", \"type\": \"signal\"}\n"
            + "  ],\n"
            + "  \"netClasses\": [],\n"
            + "  \"nets\": [],\n" // empty nets list
            + "  \"outline\": {\n"
            + "    \"corners\": [\n"
            + "      {\"x\": 0.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 80.0},\n"
            + "      {\"x\": 0.0, \"y\": 80.0}\n"
            + "    ],\n"
            + "    \"clearance\": 0.5\n"
            + "  },\n"
            + "  \"components\": [\n"
            + "    {\n"
            + "      \"reference\": \"R1\",\n"
            + "      \"value\": \"10k\",\n"
            + "      \"footprint\": \"Resistor_SMD:R_0805_2012Metric\",\n"
            + "      \"position\": {\"x\": 10.0, \"y\": 20.0},\n"
            + "      \"rotation\": 0.0,\n"
            + "      \"layer\": \"F.Cu\",\n"
            + "      \"pads\": [\n"
            + "        {\n"
            + "          \"name\": \"1\",\n"
            + "          \"netName\": \"Net-(R1-Pad1)\",\n" // referenced net name
            + "          \"shape\": \"rect\",\n"
            + "          \"size\": {\"x\": 1.2, \"y\": 1.4},\n"
            + "          \"offset\": {\"x\": -1.0, \"y\": 0.0},\n"
            + "          \"layers\": [\"F.Cu\"]\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    BoardReadResult result = KiCadJsonReader.readBoard(new StringReader(json), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    BoardReadResult.Success success = (BoardReadResult.Success) result;
    RoutingBoard board = (RoutingBoard) success.board();

    assertNotNull(board);
    assertEquals(1, board.rules.nets.maxNetNumber());
    assertEquals("Net-(R1-Pad1)", board.rules.nets.get(1).name);
  }

  @Test
  void testParseKiCadJsonRegistersViaRules() {
    String json =
        "{\n"
            + "  \"designName\": \"TestViasBoard\",\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"layers\": [\n"
            + "    {\"index\": 0, \"name\": \"F.Cu\", \"type\": \"signal\"},\n"
            + "    {\"index\": 1, \"name\": \"B.Cu\", \"type\": \"signal\"}\n"
            + "  ],\n"
            + "  \"netClasses\": [\n"
            + "    {\n"
            + "      \"name\": \"Power\",\n"
            + "      \"clearance\": 0.25,\n"
            + "      \"traceWidth\": 0.5,\n"
            + "      \"viaDiameter\": 0.8,\n"
            + "      \"viaDrill\": 0.4,\n"
            + "      \"netNames\": []\n"
            + "    }\n"
            + "  ],\n"
            + "  \"nets\": [],\n"
            + "  \"outline\": {\n"
            + "    \"corners\": [\n"
            + "      {\"x\": 0.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 80.0},\n"
            + "      {\"x\": 0.0, \"y\": 80.0}\n"
            + "    ],\n"
            + "    \"clearance\": 0.5\n"
            + "  },\n"
            + "  \"components\": []\n"
            + "}";

    BoardReadResult result = KiCadJsonReader.readBoard(new StringReader(json), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    BoardReadResult.Success success = (BoardReadResult.Success) result;
    RoutingBoard board = (RoutingBoard) success.board();

    assertNotNull(board);
    // Verify default via rules are created
    assertNotNull(board.rules.getDefaultViaRule());
    assertEquals(2, board.library.viaPadstackCount());

    // Verify NetClass via rule assignment
    assertNotNull(board.rules.netClasses.get("Power").getViaRule());
  }

  @Test
  void testParseKiCadJsonAppliesDefaultNetClassParameters() {
    String json =
        "{\n"
            + "  \"designName\": \"DefaultClassBoard\",\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"layers\": [\n"
            + "    {\"index\": 0, \"name\": \"F.Cu\", \"type\": \"signal\"},\n"
            + "    {\"index\": 1, \"name\": \"B.Cu\", \"type\": \"signal\"}\n"
            + "  ],\n"
            + "  \"netClasses\": [\n"
            + "    {\n"
            + "      \"name\": \"Default\",\n"
            + "      \"clearance\": 0.254,\n"
            + "      \"traceWidth\": 0.4,\n"
            + "      \"viaDiameter\": 1.4,\n"
            + "      \"viaDrill\": 0.6,\n"
            + "      \"netNames\": []\n"
            + "    },\n"
            + "    {\n"
            + "      \"name\": \"Power\",\n"
            + "      \"clearance\": 0.25,\n"
            + "      \"traceWidth\": 0.5,\n"
            + "      \"viaDiameter\": 1.6,\n"
            + "      \"viaDrill\": 0.6,\n"
            + "      \"netNames\": []\n"
            + "    }\n"
            + "  ],\n"
            + "  \"nets\": [\n"
            + "    {\"id\": 1, \"name\": \"SIG1\", \"className\": \"Default\", "
            + "\"containsPlane\": false},\n"
            + "    {\"id\": 2, \"name\": \"VCC\", \"className\": \"Power\", "
            + "\"containsPlane\": false}\n"
            + "  ],\n"
            + "  \"outline\": {\n"
            + "    \"corners\": [\n"
            + "      {\"x\": 0.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 80.0},\n"
            + "      {\"x\": 0.0, \"y\": 80.0}\n"
            + "    ],\n"
            + "    \"clearance\": 0.5\n"
            + "  },\n"
            + "  \"components\": []\n"
            + "}";

    BoardReadResult result = KiCadJsonReader.readBoard(new StringReader(json), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    assertEquals(2, board.rules.netClasses.count());
    assertNull(board.rules.netClasses.get("Default"));
    assertNull(board.rules.netClasses.get("kicad_default"));

    NetClass defaultClass = board.rules.getDefaultNetClass();
    assertEquals("default", defaultClass.getName());
    assertEquals(200, defaultClass.getTraceHalfWidth(0));
    assertEquals(1, defaultClass.getTraceClearanceClass());
    assertEquals(254, board.rules.clearanceMatrix.getValue(1, 1, 0, false));
    assertEquals("default", board.rules.nets.get("SIG1", 1).getNetClass().getName());
    assertEquals("Power", board.rules.nets.get("VCC", 1).getNetClass().getName());
    assertEquals(2, board.library.viaPadstackCount());
  }

  @Test
  void testParseNullReader() {
    BoardReadResult result = KiCadJsonReader.readBoard(null, null, null);
    assertInstanceOf(BoardReadResult.ParseError.class, result);
  }

  @Test
  void testParseInvalidJson() {
    BoardReadResult result =
        KiCadJsonReader.readBoard(new StringReader("{invalid JSON"), null, null);
    assertInstanceOf(BoardReadResult.ParseError.class, result);
  }

  @Test
  void testImportSession() throws Exception {
    String baseJson =
        "{\n"
            + "  \"designName\": \"TestBoard\",\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"layers\": [\n"
            + "    {\"index\": 0, \"name\": \"F.Cu\", \"type\": \"signal\"},\n"
            + "    {\"index\": 1, \"name\": \"B.Cu\", \"type\": \"signal\"}\n"
            + "  ],\n"
            + "  \"outline\": {\n"
            + "    \"corners\": [\n"
            + "      {\"x\": 0.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 0.0},\n"
            + "      {\"x\": 100.0, \"y\": 80.0},\n"
            + "      {\"x\": 0.0, \"y\": 80.0}\n"
            + "    ],\n"
            + "    \"clearance\": 0.5\n"
            + "  },\n"
            + "  \"nets\": [\n"
            + "    {\"id\": 1, \"name\": \"VCC\"}\n"
            + "  ]\n"
            + "}";

    BoardReadResult baseResult = KiCadJsonReader.readBoard(new StringReader(baseJson), null, null);
    assertTrue(baseResult instanceof BoardReadResult.Success);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) baseResult).board();

    assertEquals(0, board.getTraces().size());
    assertEquals(0, board.getVias().size());

    String sessionJson =
        "{\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"traces\": [\n"
            + "    {\n"
            + "      \"id\": 1,\n"
            + "      \"netName\": \"VCC\",\n"
            + "      \"width\": 0.3,\n"
            + "      \"layerIndex\": 0,\n"
            + "      \"points\": [\n"
            + "        {\"x\": 5.0, \"y\": 10.0},\n"
            + "        {\"x\": 10.0, \"y\": 10.0}\n"
            + "      ]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"vias\": [\n"
            + "    {\n"
            + "      \"id\": 1,\n"
            + "      \"netName\": \"VCC\",\n"
            + "      \"position\": {\"x\": 15.0, \"y\": 15.0},\n"
            + "      \"diameter\": 0.8,\n"
            + "      \"drill\": 0.4,\n"
            + "      \"startLayerIndex\": 0,\n"
            + "      \"endLayerIndex\": 1\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    KiCadJsonReader.importSession(new StringReader(sessionJson), board);

    assertEquals(1, board.getTraces().size());
    assertEquals(1, board.getVias().size());
  }

  @Test
  void testMissingOutlineFallback() {
    String json =
        "{\n"
            + "  \"unit\": \"MM\",\n"
            + "  \"resolution\": 1000.0,\n"
            + "  \"layers\": [\n"
            + "    {\"name\": \"F.Cu\", \"type\": \"signal\"},\n"
            + "    {\"name\": \"B.Cu\", \"type\": \"signal\"}\n"
            + "  ],\n"
            + "  \"nets\": [],\n"
            + "  \"netClasses\": [],\n"
            + "  \"components\": [\n"
            + "    {\n"
            + "      \"reference\": \"U1\",\n"
            + "      \"value\": \"CHIP\",\n"
            + "      \"footprint\": \"Generic\",\n"
            + "      \"position\": {\"x\": 10.0, \"y\": 20.0},\n"
            + "      \"rotation\": 0.0,\n"
            + "      \"layer\": \"F.Cu\",\n"
            + "      \"pads\": []\n"
            + "    },\n"
            + "    {\n"
            + "      \"reference\": \"U2\",\n"
            + "      \"value\": \"CHIP2\",\n"
            + "      \"footprint\": \"Generic\",\n"
            + "      \"position\": {\"x\": 30.0, \"y\": 40.0},\n"
            + "      \"rotation\": 0.0,\n"
            + "      \"layer\": \"F.Cu\",\n"
            + "      \"pads\": []\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    BoardReadResult result = KiCadJsonReader.readBoard(new StringReader(json), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    BoardReadResult.Success success = (BoardReadResult.Success) result;
    RoutingBoard board = (RoutingBoard) success.board();

    // Verify warning was populated
    assertFalse(success.warnings().isEmpty());
    assertTrue(success.warnings().get(0).contains("Board Outline/Boundary is missing"));

    // Check outline bounding box (10.0 to 30.0 x, 20.0 to 40.0 y; with 5mm padding -> x: 5 to 35,
    // y: 15 to 45)
    // Board space scales by 1000, and y is negated
    IntBox bounds = board.getOutline().boundingBox();
    // Padding of 5.0 mm = 5000 in board units
    // Min X: 10 * 1000 - 5000 = 5000
    // Max X: 30 * 1000 + 5000 = 35000
    // Min Y: 20 * 1000 - 5000 = 15000 (negated y coordinates will swap min/max in board space)
    // Max Y: 40 * 1000 + 5000 = 45000
    assertEquals(5000, bounds.ll.x);
    assertEquals(35000, bounds.ur.x);
    assertEquals(-45000, bounds.ll.y);
    assertEquals(-15000, bounds.ur.y);
  }
}
