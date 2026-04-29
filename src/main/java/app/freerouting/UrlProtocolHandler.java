package app.freerouting;

import app.freerouting.logger.FRLogger;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class UrlProtocolHandler {

  private static final String PROTOCOL_PREFIX = "freerouting://";

  public static boolean isProtocolUrl(String arg) {
    return arg != null && arg.toLowerCase().startsWith(PROTOCOL_PREFIX);
  }

  public static String[] parseUrlToArgs(String url) {
    try {
      URI uri = new URI(url);
      String query = uri.getRawQuery();
      if (query == null || query.isEmpty()) {
        return new String[0];
      }

      String[] parts = query.split("&");
      List<String> args = new ArrayList<>();
      for (String part : parts) {
        String decoded = URLDecoder.decode(part, StandardCharsets.UTF_8);
        if (!decoded.isEmpty()) {
          args.add(decoded);
        }
      }
      return args.toArray(new String[0]);
    } catch (Exception e) {
      FRLogger.warn("Failed to parse protocol URL: " + url + " (" + e.getMessage() + ")");
      return new String[]{url};
    }
  }
}
