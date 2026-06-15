package app.freerouting.util.gson;

import app.freerouting.settings.LayerSettings;
import app.freerouting.settings.RouterSettings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/**
 * Custom GSON TypeAdapter Factory that preserves default serialization behavior
 * and extends deserialization to populate the transient layers settings array
 * within RouterSettings.
 */
public class RouterSettingsTypeAdapterFactory implements TypeAdapterFactory {

  @Override
  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (!RouterSettings.class.isAssignableFrom(type.getRawType())) {
      return null;
    }

    // Retrieve default delegate TypeAdapter (which ignores transient fields)
    TypeAdapter<RouterSettings> delegate = (TypeAdapter<RouterSettings>) gson.getDelegateAdapter(this, type);
    TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

    return (TypeAdapter<T>) new TypeAdapter<RouterSettings>() {
      @Override
      public void write(JsonWriter out, RouterSettings value) throws IOException {
        if (value == null) {
          out.nullValue();
          return;
        }

        // Serialize using default delegate adapter
        JsonElement tree = delegate.toJsonTree(value);

        elementAdapter.write(out, tree);
      }

      @Override
      public RouterSettings read(JsonReader in) throws IOException {
        JsonElement tree = elementAdapter.read(in);
        if (tree == null || tree.isJsonNull()) {
          return null;
        }

        // Deserialize using default delegate adapter
        RouterSettings settings = delegate.fromJsonTree(tree);

        // Explicitly extract the transient layers array if present
        if (tree.isJsonObject() && settings != null) {
          JsonObject jsonObject = tree.getAsJsonObject();
          if (jsonObject.has("layers")) {
            settings.layers = gson.fromJson(jsonObject.get("layers"), LayerSettings[].class);
          }
        }

        return settings;
      }
    };
  }
}
