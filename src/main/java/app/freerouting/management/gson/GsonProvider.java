package app.freerouting.management.gson;

import app.freerouting.board.BoardFileDetails;
import app.freerouting.gui.FileFormat;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Instant;

import app.freerouting.core.RoutingStage;

public class GsonProvider
{
  public static final Gson GSON = new GsonBuilder()
          .setPrettyPrinting()
          .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
          .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
          .registerTypeAdapter(Path.class, new PathTypeAdapter())
          .registerTypeAdapter(RoutingStage.class, new RoutingStageDeserializer())
          .registerTypeAdapter(BoardFileDetails.class, new BoardFileDetailsDeserializer())
          .create();

  private static class RoutingStageDeserializer implements JsonDeserializer<RoutingStage> {
    @Override
    public RoutingStage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      try {
        return RoutingStage.fromString(json.getAsString());
      } catch (IllegalArgumentException e) {
        throw new JsonParseException("Unknown RoutingStage: " + json.getAsString(), e);
      }
    }
  }

  private static class BoardFileDetailsDeserializer implements JsonDeserializer<BoardFileDetails> {
    @Override
    public BoardFileDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject jsonObject = json.getAsJsonObject();
      BoardFileDetails details = new BoardFileDetails();

      if (jsonObject.has("size")) details.setSize(jsonObject.get("size").getAsLong());
      if (jsonObject.has("crc32")) details.setCrc32(jsonObject.get("crc32").getAsLong());
      if (jsonObject.has("format")) details.setFormat(context.deserialize(jsonObject.get("format"), FileFormat.class));
      if (jsonObject.has("layer_count")) details.setLayerCount(jsonObject.get("layer_count").getAsInt());
      if (jsonObject.has("component_count")) details.setComponentCount(jsonObject.get("component_count").getAsInt());
      if (jsonObject.has("netclass_count")) details.setNetclassCount(jsonObject.get("netclass_count").getAsInt());
      if (jsonObject.has("net_count")) details.setNetCount(jsonObject.get("net_count").getAsInt());
      if (jsonObject.has("track_count")) details.setTrackCount(jsonObject.get("track_count").getAsInt());
      if (jsonObject.has("trace_count")) details.setTraceCount(jsonObject.get("trace_count").getAsInt());
      if (jsonObject.has("via_count")) details.setViaCount(jsonObject.get("via_count").getAsInt());
      if (jsonObject.has("filename")) details.setFilename(jsonObject.get("filename").getAsString());
      if (jsonObject.has("path")) details.setDirectoryPath(jsonObject.get("path").getAsString());

      return details;
    }
  }
}
