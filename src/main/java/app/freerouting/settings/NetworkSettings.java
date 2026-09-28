package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Configuration for network proxies and custom TLS truststores. */
public class NetworkSettings implements Serializable {

  @SerializedName("proxy_url")
  public String proxyUrl;

  @SerializedName("no_proxy")
  public String noProxy;

  @SerializedName("custom_truststore_path")
  public String customTruststorePath;

  @SerializedName("custom_truststore_password")
  public String customTruststorePassword;

  @SerializedName("custom_truststore_type")
  public String customTruststoreType;
}
