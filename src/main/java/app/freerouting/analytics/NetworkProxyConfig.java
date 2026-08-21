package app.freerouting.analytics;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.NetworkSettings;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Manages automated proxy discovery and composite TLS truststores for enterprise networks. */
public final class NetworkProxyConfig {

  private static volatile SSLSocketFactory cachedSslSocketFactory;

  private NetworkProxyConfig() {}

  /**
   * Automatically configures JVM proxy and truststore settings from environment variables and
   * application settings.
   *
   * @param settings optional network settings from configuration
   */
  public static synchronized void configure(NetworkSettings settings) {
    try {
      configureProxy(settings);
    } catch (Exception e) {
      FRLogger.warn("Failed to automatically configure network proxy: " + e.getMessage());
    }

    try {
      configureTrustStore(settings);
    } catch (Exception e) {
      FRLogger.warn("Failed to automatically configure trust store: " + e.getMessage());
    }
  }

  private static void configureProxy(NetworkSettings settings) {
    String proxyUrl = null;
    if (settings != null && settings.proxyUrl != null && !settings.proxyUrl.isBlank()) {
      proxyUrl = settings.proxyUrl.trim();
    }

    if (proxyUrl == null || proxyUrl.isBlank()) {
      proxyUrl =
          getFirstEnv(
              "HTTPS_PROXY", "https_proxy", "ALL_PROXY", "all_proxy", "HTTP_PROXY", "http_proxy");
    }

    if (proxyUrl != null && !proxyUrl.isBlank()) {
      applyProxyUrl(proxyUrl);
    }

    String noProxy = null;
    if (settings != null && settings.noProxy != null && !settings.noProxy.isBlank()) {
      noProxy = settings.noProxy.trim();
    }
    if (noProxy == null || noProxy.isBlank()) {
      noProxy = getFirstEnv("NO_PROXY", "no_proxy");
    }

    applyNoProxy(noProxy);
  }

  private static void applyProxyUrl(String proxyUrl) {
    try {
      if (!proxyUrl.contains("://")) {
        proxyUrl = "http://" + proxyUrl;
      }
      URI uri = URI.create(proxyUrl);
      String host = uri.getHost();
      int port = uri.getPort() > 0 ? uri.getPort() : 8080;

      if (host != null && !host.isBlank()) {
        if (System.getProperty("https.proxyHost") == null) {
          System.setProperty("https.proxyHost", host);
          System.setProperty("https.proxyPort", String.valueOf(port));
        }
        if (System.getProperty("http.proxyHost") == null) {
          System.setProperty("http.proxyHost", host);
          System.setProperty("http.proxyPort", String.valueOf(port));
        }

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
          String[] parts = userInfo.split(":", 2);
          String user = parts[0];
          String pass = parts.length > 1 ? parts[1] : "";
          Authenticator.setDefault(
              new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                  if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(user, pass.toCharArray());
                  }
                  return super.getPasswordAuthentication();
                }
              });
        }
        FRLogger.info("Configured network proxy: " + host + ":" + port);
      }
    } catch (Exception e) {
      FRLogger.warn("Could not parse proxy URL '" + proxyUrl + "': " + e.getMessage());
    }
  }

  /**
   * Combines an existing non-proxy hosts string with new bypass entries, ensuring localhost and
   * 127.0.0.1 are always present.
   *
   * @param existing current value of {@code http.nonProxyHosts}, or {@code null}
   * @param noProxy additional comma/space/pipe-separated bypass list, or {@code null}
   * @return pipe-separated non-proxy hosts string
   */
  public static String buildNonProxyHosts(String existing, String noProxy) {
    Set<String> hosts = new LinkedHashSet<>();
    hosts.add("localhost");
    hosts.add("127.0.0.1");

    if (existing != null && !existing.isBlank()) {
      String[] existingEntries = existing.split("[|]+");
      for (String entry : existingEntries) {
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
          hosts.add(trimmed);
        }
      }
    }

    if (noProxy != null && !noProxy.isBlank()) {
      String[] entries = noProxy.split("[,;\\s|]+");
      for (String entry : entries) {
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
          if (trimmed.startsWith(".")) {
            trimmed = "*" + trimmed;
          }
          hosts.add(trimmed);
        }
      }
    }

    return String.join("|", hosts);
  }

  private static void applyNoProxy(String noProxy) {
    String existing = System.getProperty("http.nonProxyHosts");
    String merged = buildNonProxyHosts(existing, noProxy);
    System.setProperty("http.nonProxyHosts", merged);
  }

  private static void configureTrustStore(NetworkSettings settings) {
    String trustStorePath = null;
    if (settings != null
        && settings.customTruststorePath != null
        && !settings.customTruststorePath.isBlank()) {
      trustStorePath = settings.customTruststorePath.trim();
    }
    if (trustStorePath == null || trustStorePath.isBlank()) {
      trustStorePath = getFirstEnv("SSL_CERT_FILE", "REQUESTS_CA_BUNDLE", "CURL_CA_BUNDLE");
    }

    if (trustStorePath != null
        && !trustStorePath.isBlank()
        && System.getProperty("javax.net.ssl.trustStore") == null) {
      System.setProperty("javax.net.ssl.trustStore", trustStorePath);
      if (settings != null
          && settings.customTruststorePassword != null
          && !settings.customTruststorePassword.isBlank()) {
        System.setProperty("javax.net.ssl.trustStorePassword", settings.customTruststorePassword);
      }
      if (settings != null
          && settings.customTruststoreType != null
          && !settings.customTruststoreType.isBlank()) {
        System.setProperty("javax.net.ssl.trustStoreType", settings.customTruststoreType);
      }
      FRLogger.info("Configured custom SSL truststore: " + trustStorePath);
    }
  }

  /**
   * Returns a composite SSLSocketFactory that trusts both standard JDK root certificates and
   * OS/enterprise certificates.
   *
   * @param settings optional network settings
   * @return the composite socket factory, or {@code null} if default is sufficient
   */
  public static SSLSocketFactory getCompositeSslSocketFactory(NetworkSettings settings) {
    if (cachedSslSocketFactory != null) {
      return cachedSslSocketFactory;
    }

    synchronized (NetworkProxyConfig.class) {
      if (cachedSslSocketFactory != null) {
        return cachedSslSocketFactory;
      }

      try {
        List<X509TrustManager> trustManagers = new ArrayList<>();

        // 1. Standard default trust managers (JDK cacerts)
        TrustManagerFactory defaultTmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);
        for (TrustManager tm : defaultTmf.getTrustManagers()) {
          if (tm instanceof X509TrustManager x509Tm) {
            trustManagers.add(x509Tm);
          }
        }

        // 2. Windows OS root trust store if running on Windows
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
          try {
            KeyStore windowsStore = KeyStore.getInstance("Windows-ROOT");
            windowsStore.load(null, null);
            TrustManagerFactory winTmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            winTmf.init(windowsStore);
            for (TrustManager tm : winTmf.getTrustManagers()) {
              if (tm instanceof X509TrustManager x509Tm) {
                trustManagers.add(x509Tm);
              }
            }
          } catch (Throwable _) {
            // Windows-ROOT keystore not supported or available in current environment; continue
          }
        }

        // 3. Custom certificate file if provided
        String customCaPath =
            settings != null && settings.customTruststorePath != null
                ? settings.customTruststorePath
                : getFirstEnv("SSL_CERT_FILE", "REQUESTS_CA_BUNDLE", "CURL_CA_BUNDLE");
        if (customCaPath != null && !customCaPath.isBlank()) {
          try (InputStream is = new FileInputStream(customCaPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> certs =
                cf.generateCertificates(is);
            if (certs != null && !certs.isEmpty()) {
              KeyStore customKs = KeyStore.getInstance(KeyStore.getDefaultType());
              customKs.load(null, null);
              int i = 0;
              for (java.security.cert.Certificate cert : certs) {
                customKs.setCertificateEntry("custom-ca-" + (++i), cert);
              }
              TrustManagerFactory customTmf =
                  TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
              customTmf.init(customKs);
              for (TrustManager tm : customTmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509Tm) {
                  trustManagers.add(x509Tm);
                }
              }
            }
          } catch (Throwable t) {
            FRLogger.warn(
                "Could not load custom CA certificates from '"
                    + customCaPath
                    + "': "
                    + t.getMessage());
          }
        }

        if (trustManagers.size() <= 1) {
          cachedSslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
          return cachedSslSocketFactory;
        }

        X509TrustManager compositeTrustManager = new CompositeX509TrustManager(trustManagers);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {compositeTrustManager}, null);
        cachedSslSocketFactory = sslContext.getSocketFactory();
        return cachedSslSocketFactory;
      } catch (Exception e) {
        FRLogger.warn("Could not initialize composite SSL socket factory: " + e.getMessage());
        return null;
      }
    }
  }

  private static String getFirstEnv(String... names) {
    for (String name : names) {
      String val = System.getenv(name);
      if (val != null && !val.isBlank()) {
        return val.trim();
      }
    }
    return null;
  }

  /** Trust manager that delegates certificate validation across multiple trust managers. */
  private static final class CompositeX509TrustManager implements X509TrustManager {
    private final List<X509TrustManager> trustManagers;

    private CompositeX509TrustManager(List<X509TrustManager> trustManagers) {
      this.trustManagers = trustManagers;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
      java.security.cert.CertificateException lastException = null;
      for (X509TrustManager tm : trustManagers) {
        try {
          tm.checkClientTrusted(chain, authType);
          return;
        } catch (java.security.cert.CertificateException e) {
          lastException = e;
        }
      }
      if (lastException != null) {
        throw lastException;
      }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
      java.security.cert.CertificateException lastException = null;
      for (X509TrustManager tm : trustManagers) {
        try {
          tm.checkServerTrusted(chain, authType);
          return;
        } catch (java.security.cert.CertificateException e) {
          lastException = e;
        }
      }
      if (lastException != null) {
        throw lastException;
      }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      List<X509Certificate> certificates = new ArrayList<>();
      for (X509TrustManager tm : trustManagers) {
        certificates.addAll(Arrays.asList(tm.getAcceptedIssuers()));
      }
      return certificates.toArray(new X509Certificate[0]);
    }
  }
}
