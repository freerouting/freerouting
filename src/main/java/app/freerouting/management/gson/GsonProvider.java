package app.freerouting.management.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;

public class GsonProvider
{
  public static final Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class, new InstantTypeAdapter()).create();
}