package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.io.kicad.KiCadJsonWriter;
import app.freerouting.logger.FRLogger;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.CRC32;

/** Owns one subscriber's output polling, change detection, and connection cleanup. */
final class JobOutputStream {
  // Bound a stalled asynchronous send so it cannot retain a job after the client stops reading.
  private static final int SEND_TIMEOUT_SECONDS = 10;
  private final RoutingJob job;
  private final SseEventSink sink;
  private final ScheduledExecutorService executor;
  private final Supplier<OutboundSseEvent> nextEvent;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean sending = new AtomicBoolean();
  private volatile CompletableFuture<?> pendingSend;

  private JobOutputStream(
      RoutingJob job, SseEventSink sink, Supplier<OutboundSseEvent> nextEvent, String workerName) {
    this.job = job;
    this.sink = sink;
    this.nextEvent = nextEvent;
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread worker = new Thread(runnable, workerName);
              worker.setDaemon(true);
              return worker;
            });
  }

  static void startSes(RoutingJob job, SseEventSink sink, Sse sse) {
    // A checksum belongs to this subscription, not to the shared job or other clients.
    AtomicLong previousChecksum = new AtomicLong(-1);
    JobOutputStream stream =
        new JobOutputStream(
            job,
            sink,
            () -> {
              var output = job.output;
              if (output == null) {
                return null;
              }
              byte[] bytes = output.getData().readAllBytes();
              if (bytes.length == 0) {
                return null;
              }
              BoardFilePayload payload = new BoardFilePayload();
              payload.jobId = job.id;
              payload.setFilename(output.getFilename());
              payload.setData(bytes);
              if (payload.crc32 == previousChecksum.get()) {
                return null;
              }
              previousChecksum.set(payload.crc32);
              payload.dataBase64 = Base64.getEncoder().encodeToString(bytes);
              return sse.newEventBuilder()
                  .id(String.valueOf(System.currentTimeMillis()))
                  .data(GSON.toJson(payload))
                  .build();
            },
            "job-output-stream-worker");
    stream.executor.scheduleAtFixedRate(stream::poll, 0, 200, TimeUnit.MILLISECONDS);
  }

  static void startJson(RoutingJob job, SseEventSink sink, Sse sse) {
    AtomicLong previousChecksum = new AtomicLong(-1);
    JobOutputStream stream =
        new JobOutputStream(
            job,
            sink,
            () -> {
              if (job.board == null) {
                return null;
              }
              String json = KiCadJsonWriter.write(job.board, job.name);
              CRC32 checksum = new CRC32();
              checksum.update(json.getBytes(StandardCharsets.UTF_8));
              if (checksum.getValue() == previousChecksum.get()) {
                return null;
              }
              previousChecksum.set(checksum.getValue());
              return sse.newEventBuilder()
                  .id(String.valueOf(System.currentTimeMillis()))
                  .name("json-output")
                  .data(json)
                  .mediaType(MediaType.APPLICATION_JSON_TYPE)
                  .build();
            },
            "job-output-board-stream-worker");
    stream.executor.scheduleAtFixedRate(stream::poll, 0, 500, TimeUnit.MILLISECONDS);
  }

  private void poll() {
    try {
      if (closed.get() || sink.isClosed() || failed()) {
        close();
        return;
      }
      if (!sending.compareAndSet(false, true)) {
        return;
      }
      boolean finalSnapshot = job.state == RoutingJobState.COMPLETED;
      OutboundSseEvent event = nextEvent.get();
      if (failed()) {
        close();
        return;
      }
      if (event == null) {
        sending.set(false);
        if (finalSnapshot) {
          close();
        }
        return;
      }
      pendingSend = sink.send(event).toCompletableFuture();
      pendingSend
          .orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .whenComplete(
              (ignored, error) -> {
                if (closed.get()) {
                  return;
                }
                if (error != null) {
                  FRLogger.error("Error sending routing output", error);
                  close();
                } else if (finalSnapshot) {
                  close();
                } else {
                  sending.set(false);
                }
              });
    } catch (Exception error) {
      FRLogger.error("Error while streaming routing output", error);
      close();
    }
  }

  private boolean failed() {
    RoutingJobState state = job.state;
    return state != null && state.isTerminal() && state != RoutingJobState.COMPLETED;
  }

  private void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      sink.close();
    } catch (Exception error) {
      FRLogger.error("Error closing SSE event sink", error);
    } finally {
      executor.shutdown();
      var pending = pendingSend;
      if (pending != null) {
        pending.cancel(false);
      }
    }
  }
}
