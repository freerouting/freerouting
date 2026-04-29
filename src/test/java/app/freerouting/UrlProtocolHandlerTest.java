package app.freerouting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlProtocolHandlerTest {

  @Test
  void isProtocolUrl_detectsFreeroutingScheme() {
    assertTrue(UrlProtocolHandler.isProtocolUrl("freerouting://open"));
    assertTrue(UrlProtocolHandler.isProtocolUrl("freerouting://open?--gui.enabled=false"));
    assertTrue(UrlProtocolHandler.isProtocolUrl("FREEROUTING://open"));
  }

  @Test
  void isProtocolUrl_rejectsNonProtocol() {
    assertFalse(UrlProtocolHandler.isProtocolUrl("--gui.enabled=false"));
    assertFalse(UrlProtocolHandler.isProtocolUrl("http://example.com"));
    assertFalse(UrlProtocolHandler.isProtocolUrl(null));
    assertFalse(UrlProtocolHandler.isProtocolUrl(""));
  }

  @Test
  void parseUrlToArgs_multipleParams() {
    String[] args = UrlProtocolHandler.parseUrlToArgs(
        "freerouting://open?--gui.enabled=false&--api_server.enabled=true");
    assertArrayEquals(new String[]{"--gui.enabled=false", "--api_server.enabled=true"}, args);
  }

  @Test
  void parseUrlToArgs_singleParam() {
    String[] args = UrlProtocolHandler.parseUrlToArgs(
        "freerouting://open?--api_server.enabled=true");
    assertArrayEquals(new String[]{"--api_server.enabled=true"}, args);
  }

  @Test
  void parseUrlToArgs_noQueryString() {
    String[] args = UrlProtocolHandler.parseUrlToArgs("freerouting://open");
    assertEquals(0, args.length);
  }

  @Test
  void parseUrlToArgs_urlDecodesPercent() {
    String[] args = UrlProtocolHandler.parseUrlToArgs(
        "freerouting://open?--de=%2Fpath%2Fto%2Ffile.dsn");
    assertArrayEquals(new String[]{"--de=/path/to/file.dsn"}, args);
  }

  @Test
  void parseUrlToArgs_urlDecodesPlusAsSpace() {
    String[] args = UrlProtocolHandler.parseUrlToArgs(
        "freerouting://open?--name=hello+world");
    assertArrayEquals(new String[]{"--name=hello world"}, args);
  }

  @Test
  void parseUrlToArgs_emptyValue() {
    String[] args = UrlProtocolHandler.parseUrlToArgs(
        "freerouting://open?--gui.enabled=");
    assertArrayEquals(new String[]{"--gui.enabled="}, args);
  }

  @Test
  void parseUrlToArgs_malformedUrlReturnsOriginal() {
    String malformed = "freerouting://open?%zz";
    String[] args = UrlProtocolHandler.parseUrlToArgs(malformed);
    assertArrayEquals(new String[]{malformed}, args);
  }
}
