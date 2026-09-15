package app.freerouting.util.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Shared Gson instance configured for Freerouting API and settings serialization. */
public final class GsonProvider {

  public static final Gson GSON =
      new GsonBuilder()
          .setPrettyPrinting()
          .disableHtmlEscaping()
          .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
          .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
          .registerTypeAdapter(Path.class, new PathTypeAdapter())
          .registerTypeAdapter(Float.class, new TwoDecimalFloatAdapter())
          .registerTypeAdapter(float.class, new TwoDecimalFloatAdapter())
          .registerTypeAdapter(Double.class, new TwoDecimalDoubleAdapter())
          .registerTypeAdapter(double.class, new TwoDecimalDoubleAdapter())
          .registerTypeAdapterFactory(new RouterSettingsTypeAdapterFactory())
          .setStrictness(Strictness.LENIENT)
          .create();

  private GsonProvider() {}

  private static final class TwoDecimalFloatAdapter extends TypeAdapter<Float> {
    @Override
    public void write(JsonWriter out, Float value) throws IOException {
      if (value == null || !Float.isFinite(value)) {
        out.nullValue();
      } else {
        out.value(new BigDecimal(String.format(Locale.ROOT, "%.2f", value)));
      }
    }

    @Override
    public Float read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return (float) in.nextDouble();
    }
  }

  private static final class TwoDecimalDoubleAdapter extends TypeAdapter<Double> {
    @Override
    public void write(JsonWriter out, Double value) throws IOException {
      if (value == null || !Double.isFinite(value)) {
        out.nullValue();
      } else {
        out.value(new BigDecimal(String.format(Locale.ROOT, "%.2f", value)));
      }
    }

    @Override
    public Double read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return in.nextDouble();
    }
  }
}
