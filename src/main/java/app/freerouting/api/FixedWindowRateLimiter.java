package app.freerouting.api;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory fixed-window limiter for per-key request throttling.
 */
public class FixedWindowRateLimiter {

  public record Decision(boolean allowed, long retryAfterSeconds) {
  }

  private static final int MAX_TRACKED_KEYS = 10_000;

  private static final class WindowCounter {
    long windowStartMs;
    int count;
  }

  private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

  public synchronized Decision check(String key, int maxRequests, int windowSeconds) {
    long now = System.currentTimeMillis();
    long windowMs = Math.max(1L, windowSeconds) * 1000L;

    WindowCounter counter = counters.computeIfAbsent(key, k -> {
      WindowCounter c = new WindowCounter();
      c.windowStartMs = now;
      c.count = 0;
      return c;
    });

    if (now - counter.windowStartMs >= windowMs) {
      counter.windowStartMs = now;
      counter.count = 0;
    }

    if (counter.count >= Math.max(1, maxRequests)) {
      long elapsed = now - counter.windowStartMs;
      long remainingMs = Math.max(0L, windowMs - elapsed);
      long retryAfter = Math.max(1L, (remainingMs + 999L) / 1000L);
      return new Decision(false, retryAfter);
    }

    counter.count++;
    cleanupIfNeeded(now, windowMs);
    return new Decision(true, 0L);
  }

  private void cleanupIfNeeded(long now, long windowMs) {
    if (counters.size() <= MAX_TRACKED_KEYS) {
      return;
    }

    Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, WindowCounter> entry = iterator.next();
      if (now - entry.getValue().windowStartMs > windowMs * 2L) {
        iterator.remove();
      }
    }
  }
}