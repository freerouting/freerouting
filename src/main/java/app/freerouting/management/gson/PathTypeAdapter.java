package app.freerouting.management.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.nio.file.Path;

public class PathTypeAdapter extends TypeAdapter<Path>
{

  @Override
  public void write(JsonWriter out, Path value) throws IOException
  {
    // add a check for null
    if (value == null)
    {
      out.nullValue();
      return;
    }

    out.value(value.toString());
  }

  @Override
  public Path read(JsonReader in) throws IOException
  {
    // add a check for null
    if (in.peek() == com.google.gson.stream.JsonToken.NULL)
    {
      in.nextNull();
      return null;
    }

    return Path.of(in.nextString());
  }
}