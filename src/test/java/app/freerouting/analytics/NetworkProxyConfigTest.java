package app.freerouting.analytics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.NetworkSettings;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkProxyConfigTest {

  private static final String[] PROPS = {
    "https.proxyHost",
    "https.proxyPort",
    "http.proxyHost",
    "http.proxyPort",
    "http.nonProxyHosts",
    "javax.net.ssl.trustStore",
    "javax.net.ssl.trustStorePassword",
    "javax.net.ssl.trustStoreType"
  };

  private final Map<String, String> originalProps = new HashMap<>();

  @BeforeEach
  void setUp() {
    originalProps.clear();
    for (String prop : PROPS) {
      originalProps.put(prop, System.getProperty(prop));
    }
  }

  @AfterEach
  void tearDown() {
    for (String prop : PROPS) {
      String val = originalProps.get(prop);
      if (val != null) {
        System.setProperty(prop, val);
      } else {
        System.clearProperty(prop);
      }
    }
  }

  @Test
  void testConfigureWithNetworkSettings() {
    NetworkSettings settings = new NetworkSettings();
    settings.proxyUrl = "http://corp-proxy.local:3128";
    settings.noProxy = "localhost,127.0.0.1,.example.com";
    settings.customTruststorePath = "fixtures/test-truststore.jks";
    settings.customTruststorePassword = "secretPassword";
    settings.customTruststoreType = "JKS";

    assertDoesNotThrow(() -> NetworkProxyConfig.configure(settings));
  }

  @Test
  void testGetCompositeSslSocketFactoryReturnsNonNull() {
    NetworkSettings settings = new NetworkSettings();
    SSLSocketFactory factory = NetworkProxyConfig.getCompositeSslSocketFactory(settings);
    assertNotNull(factory, "SSLSocketFactory should not be null");
  }

  @Test
  void testBuildNonProxyHostsContainsLocalhostAndWildcards() {
    String merged =
        NetworkProxyConfig.buildNonProxyHosts(
            "*.local|169.254/16", "internal.corp,api.internal,.example.com");
    assertNotNull(merged);
    assertTrue(merged.contains("localhost"), "Should include localhost");
    assertTrue(merged.contains("127.0.0.1"), "Should include 127.0.0.1");
    assertTrue(merged.contains("*.local"), "Should retain existing entry");
    assertTrue(merged.contains("internal.corp"), "Should include new entry");
    assertTrue(merged.contains("*.example.com"), "Should prefix dot domains with wildcard");
  }

  @Test
  void testNoProxyContainsLocalhost() {
    NetworkSettings settings = new NetworkSettings();
    settings.noProxy = "internal.corp,api.internal";
    NetworkProxyConfig.configure(settings);

    String nonProxyHosts = System.getProperty("http.nonProxyHosts");
    assertNotNull(nonProxyHosts);
    assertTrue(nonProxyHosts.contains("localhost"), "Should include localhost");
    assertTrue(nonProxyHosts.contains("127.0.0.1"), "Should include 127.0.0.1");
  }
}
