package app.freerouting.analytics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.NetworkSettings;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

class NetworkProxyConfigTest {

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
