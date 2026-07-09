package app.freerouting.io.kicad;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class KiCadJsonReaderTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testParseValidKiCadJson() {
    String json = "{\n"
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
        + "    {\"id\": 1, \"name\": \"VCC\", \"className\": \"Power\", \"containsPlane\": false},\n"
        + "    {\"id\": 2, \"name\": \"GND\", \"className\": \"Power\", \"containsPlane\": true}\n"
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
    assertEquals(2, board.get_layer_count());
    assertEquals("F.Cu", board.layer_structure.arr[0].name);
    assertEquals(2, board.rules.nets.max_net_no());
    assertEquals("VCC", board.rules.nets.get(1).name);
    assertEquals("GND", board.rules.nets.get(2).name);
    assertTrue(board.rules.nets.get(2).contains_plane());
  }

  @Test
  void testParseKiCadJsonWithImplicitNets() {
    String json = "{\n"
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
    assertEquals(1, board.rules.nets.max_net_no());
    assertEquals("Net-(R1-Pad1)", board.rules.nets.get(1).name);
  }

  @Test
  void testParseKiCadJsonRegistersViaRules() {
    String json = "{\n"
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
    assertNotNull(board.rules.get_default_via_rule());
    assertEquals(2, board.library.via_padstack_count());
    
    // Verify NetClass via rule assignment
    assertNotNull(board.rules.net_classes.get("Power").get_via_rule());
  }

  @Test
  void testParseKiCadJsonAppliesDefaultNetClassParameters() {
    String json = "{\n"
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
        + "    {\"id\": 1, \"name\": \"SIG1\", \"className\": \"Default\", \"containsPlane\": false},\n"
        + "    {\"id\": 2, \"name\": \"VCC\", \"className\": \"Power\", \"containsPlane\": false}\n"
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

    assertEquals(2, board.rules.net_classes.count());
    assertNull(board.rules.net_classes.get("Default"));
    assertNull(board.rules.net_classes.get("kicad_default"));

    NetClass defaultClass = board.rules.get_default_net_class();
    assertEquals("default", defaultClass.get_name());
    assertEquals(200, defaultClass.get_trace_half_width(0));
    assertEquals(1, defaultClass.get_trace_clearance_class());
    assertEquals(254, board.rules.clearance_matrix.get_value(1, 1, 0, false));
    assertEquals("default", board.rules.nets.get("SIG1", 1).get_class().get_name());
    assertEquals("Power", board.rules.nets.get("VCC", 1).get_class().get_name());
    assertEquals(2, board.library.via_padstack_count());
  }

  @Test
  void testParseNullReader() {
    BoardReadResult result = KiCadJsonReader.readBoard(null, null, null);
    assertInstanceOf(BoardReadResult.ParseError.class, result);
  }

  @Test
  void testParseInvalidJson() {
    BoardReadResult result = KiCadJsonReader.readBoard(new StringReader("{invalid JSON"), null, null);
    assertInstanceOf(BoardReadResult.ParseError.class, result);
  }

  @Test
  void testImportSession() throws Exception {
    String baseJson = "{\n"
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

    assertEquals(0, board.get_traces().size());
    assertEquals(0, board.get_vias().size());

    String sessionJson = "{\n"
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

    assertEquals(1, board.get_traces().size());
    assertEquals(1, board.get_vias().size());
  }
}
