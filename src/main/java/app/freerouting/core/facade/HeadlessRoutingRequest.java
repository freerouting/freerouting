package app.freerouting.core.facade;

import app.freerouting.settings.RouterSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable configuration request for {@link FreeroutingHeadlessEngine}.
 *
 * <p>Provides fluent configuration for in-memory board inputs (DSN content), optional rules file,
 * settings overrides, progress listener, cancellation token, and output generation options.
 */
public record HeadlessRoutingRequest(
    byte[] dsnBytes,
    String designName,
    byte[] rulesBytes,
    String rulesName,
    RouterSettings settings,
    CancellationToken cancellationToken,
    HeadlessRoutingProgressListener progressListener,
    boolean runDrc,
    boolean computeSes) {

  /** Validates inputs and defensively copies byte buffers to preserve immutability. */
  public HeadlessRoutingRequest {
    if (dsnBytes == null || dsnBytes.length == 0) {
      throw new IllegalArgumentException("dsnBytes must not be null or empty");
    }
    dsnBytes = dsnBytes.clone();
    rulesBytes = rulesBytes != null ? rulesBytes.clone() : null;
    settings = settings != null ? settings.clone() : null;
    if (designName == null || designName.isBlank()) {
      designName = "design.dsn";
    }
    if (cancellationToken == null) {
      cancellationToken = CancellationToken.none();
    }
  }

  @Override
  public byte[] dsnBytes() {
    return dsnBytes.clone();
  }

  @Override
  public byte[] rulesBytes() {
    return rulesBytes != null ? rulesBytes.clone() : null;
  }

  @Override
  public RouterSettings settings() {
    return settings != null ? settings.clone() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HeadlessRoutingRequest that)) {
      return false;
    }
    return runDrc == that.runDrc
        && computeSes == that.computeSes
        && Arrays.equals(dsnBytes, that.dsnBytes)
        && Objects.equals(designName, that.designName)
        && Arrays.equals(rulesBytes, that.rulesBytes)
        && Objects.equals(rulesName, that.rulesName)
        && Objects.equals(settings, that.settings)
        && Objects.equals(cancellationToken, that.cancellationToken)
        && Objects.equals(progressListener, that.progressListener);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            designName,
            rulesName,
            settings,
            cancellationToken,
            progressListener,
            runDrc,
            computeSes);
    result = 31 * result + Arrays.hashCode(dsnBytes);
    result = 31 * result + Arrays.hashCode(rulesBytes);
    return result;
  }

  /** Creates a new builder for {@link HeadlessRoutingRequest}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Creates a request with default settings for the given raw DSN bytes. */
  public static HeadlessRoutingRequest of(byte[] dsnBytes) {
    return builder().dsnBytes(dsnBytes).build();
  }

  /** Creates a request with default settings for the given DSN string content. */
  public static HeadlessRoutingRequest of(String dsnContent) {
    return builder().dsnContent(dsnContent).build();
  }

  /**
   * Creates a request with default settings by reading all bytes from the given DSN input stream.
   */
  public static HeadlessRoutingRequest of(InputStream dsnStream) throws IOException {
    return builder().dsnStream(dsnStream).build();
  }

  /** Builder for constructing {@link HeadlessRoutingRequest} instances. */
  public static class Builder {
    private byte[] dsnBytes;
    private String designName = "design.dsn";
    private byte[] rulesBytes;
    private String rulesName;
    private RouterSettings settings;
    private CancellationToken cancellationToken = CancellationToken.none();
    private HeadlessRoutingProgressListener progressListener;
    private boolean runDrc = true;
    private boolean computeSes = true;

    /** Sets the input DSN file content as raw bytes. */
    public Builder dsnBytes(byte[] dsnBytes) {
      this.dsnBytes = dsnBytes;
      return this;
    }

    /** Sets the input DSN file content from a UTF-8 string. */
    public Builder dsnContent(String dsnContent) {
      if (dsnContent != null) {
        this.dsnBytes = dsnContent.getBytes(StandardCharsets.UTF_8);
      }
      return this;
    }

    /** Reads the input DSN file content from an input stream. */
    public Builder dsnStream(InputStream stream) throws IOException {
      if (stream != null) {
        this.dsnBytes = stream.readAllBytes();
      }
      return this;
    }

    /** Sets the design name hint used in session headers and log messages. */
    public Builder designName(String designName) {
      this.designName = designName;
      return this;
    }

    /** Sets the optional RULES file content as raw bytes. */
    public Builder rulesBytes(byte[] rulesBytes) {
      this.rulesBytes = rulesBytes;
      return this;
    }

    /** Sets the optional RULES file content from a UTF-8 string. */
    public Builder rulesContent(String rulesContent) {
      if (rulesContent != null) {
        this.rulesBytes = rulesContent.getBytes(StandardCharsets.UTF_8);
      }
      return this;
    }

    /** Sets the filename of the optional RULES file. */
    public Builder rulesName(String rulesName) {
      this.rulesName = rulesName;
      return this;
    }

    /** Sets custom router settings overrides (applied at highest priority). */
    public Builder settings(RouterSettings settings) {
      this.settings = settings;
      return this;
    }

    /** Sets the cooperative cancellation token. */
    public Builder cancellationToken(CancellationToken cancellationToken) {
      this.cancellationToken = cancellationToken;
      return this;
    }

    /** Sets an optional progress callback listener. */
    public Builder progressListener(HeadlessRoutingProgressListener progressListener) {
      this.progressListener = progressListener;
      return this;
    }

    /**
     * Configures whether to perform authoritative DRC checks upon completion. Defaults to {@code
     * true}.
     */
    public Builder runDrc(boolean runDrc) {
      this.runDrc = runDrc;
      return this;
    }

    /**
     * Configures whether to serialize the final board to Specctra SES format. Defaults to {@code
     * true}.
     */
    public Builder computeSes(boolean computeSes) {
      this.computeSes = computeSes;
      return this;
    }

    /** Builds the immutable {@link HeadlessRoutingRequest}. */
    public HeadlessRoutingRequest build() {
      return new HeadlessRoutingRequest(
          dsnBytes,
          designName,
          rulesBytes,
          rulesName,
          settings,
          cancellationToken,
          progressListener,
          runDrc,
          computeSes);
    }
  }
}
