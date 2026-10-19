package app.freerouting.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class JobOutputStreamLifecycleTest {
  private final UUID userId = UUID.randomUUID();
  private final RoutingJob job = new RoutingJob();
  private final JobOutputResource resource = new JobOutputResource();
  private final Sse sse = mock(Sse.class);
  private final OutboundSseEvent.Builder eventBuilder =
      mock(OutboundSseEvent.Builder.class, RETURNS_SELF);
  private final SseEventSink sink = mock(SseEventSink.class);
  private final AtomicBoolean closed = new AtomicBoolean();

  @BeforeEach
  void setUp() throws Exception {
    GlobalSettings.setCurrent(new GlobalSettings());
    FRAnalytics.setEnabled(false);
    resource.setUserIdOverride(userId);
    job.sessionId = UUID.randomUUID();
    job.state = RoutingJobState.RUNNING;
    when(sse.newEventBuilder()).thenReturn(eventBuilder);
    when(eventBuilder.build()).thenReturn(mock(OutboundSseEvent.class));
    when(sink.isClosed()).thenAnswer(invocation -> closed.get());
    when(sink.send(any(OutboundSseEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    doAnswer(
            invocation -> {
              closed.set(true);
              return null;
            })
        .when(sink)
        .close();
  }

  @ParameterizedTest
  @EnumSource(
      value = RoutingJobState.class,
      names = {"INVALID", "COMPLETED", "CANCELLED", "TIMED_OUT", "TERMINATED"})
  void sesStreamReleasesWorkerAfterEveryTerminalState(RoutingJobState state) throws Exception {
    assertStreamCloses(state, false);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoutingJobState.class,
      names = {"INVALID", "COMPLETED", "CANCELLED", "TIMED_OUT", "TERMINATED"})
  void jsonStreamReleasesWorkerAfterEveryTerminalState(RoutingJobState state) throws Exception {
    assertStreamCloses(state, true);
  }

  private void assertStreamCloses(RoutingJobState state, boolean json) throws Exception {
    final Set<Thread> existing = workers();
    try {
      withJob(() -> startStream(json));
      assertTrue(awaitNewWorker(existing));
      job.state = state;
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing), "Terminal jobs must release the stream executor");
    } finally {
      job.state = RoutingJobState.CANCELLED;
      closed.set(true);
      awaitWorkerExit(existing);
    }
  }

  @Test
  void disconnectedIdleClientReleasesWorker() throws Exception {
    final Set<Thread> existing = workers();
    try {
      withJob(() -> startStream(false));
      assertTrue(awaitNewWorker(existing));
      closed.set(true);
      assertTrue(
          awaitWorkerExit(existing), "A disconnected client must not retain a polling worker");
    } finally {
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  void emptyOutputReturnsNoContentWithoutPublishing() {
    job.output = new BoardFileDetails();
    withJob(
        () -> {
          try (Response response = resource.downloadOutput(job.id.toString())) {
            assertEquals(204, response.getStatus());
          }
          try (Response response = resource.downloadOutputJson(job.id.toString())) {
            assertEquals(204, response.getStatus());
          }
        });
  }

  @Test
  void separateSubscribersReceiveSameCurrentSnapshot() throws Exception {
    final Set<Thread> existing = workers();
    SseEventSink secondSink = mock(SseEventSink.class);
    when(secondSink.send(any(OutboundSseEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    job.output = new BoardFileDetails();
    job.output.setData("current-output".getBytes(StandardCharsets.UTF_8));
    try {
      withJob(() -> resource.streamOutput(job.id.toString(), sink, sse));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      withJob(() -> resource.streamOutput(job.id.toString(), secondSink, sse));
      verify(secondSink, timeout(2000)).send(any(OutboundSseEvent.class));
    } finally {
      job.state = RoutingJobState.CANCELLED;
      closed.set(true);
      awaitWorkerExit(existing);
    }
  }

  @Test
  void completedStreamWaitsForFinalSendBeforeClosing() throws Exception {
    final Set<Thread> existing = workers();
    CompletableFuture<Void> delivery = new CompletableFuture<>();
    when(sink.send(any(OutboundSseEvent.class))).thenAnswer(invocation -> delivery);
    job.output = new BoardFileDetails();
    job.output.setData("final-output".getBytes(StandardCharsets.UTF_8));
    job.state = RoutingJobState.COMPLETED;
    try {
      withJob(() -> startStream(false));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      verify(sink, never()).close();
      delivery.complete(null);
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing));
    } finally {
      delivery.complete(null);
      closed.set(true);
      awaitWorkerExit(existing);
    }
  }

  @Test
  @app.freerouting.logger.AllowErrorLogs("Exercises asynchronous SSE delivery failure")
  void asynchronousSendFailureReleasesWorker() throws Exception {
    final Set<Thread> existing = workers();
    CompletableFuture<Void> delivery = new CompletableFuture<>();
    when(sink.send(any(OutboundSseEvent.class))).thenAnswer(invocation -> delivery);
    job.output = new BoardFileDetails();
    job.output.setData("current-output".getBytes(StandardCharsets.UTF_8));
    try {
      withJob(() -> startStream(false));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      delivery.completeExceptionally(new java.io.IOException("Disconnected test client"));
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing));
    } finally {
      closed.set(true);
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  void invalidJobDoesNotPublishEarlierPreview() throws Exception {
    final Set<Thread> existing = workers();
    job.output = new BoardFileDetails();
    job.output.setData("earlier-preview".getBytes(StandardCharsets.UTF_8));
    job.state = RoutingJobState.INVALID;
    try {
      withJob(() -> startStream(false));
      verify(sink, timeout(2000)).close();
      verify(sink, never()).send(any(OutboundSseEvent.class));
      assertTrue(awaitWorkerExit(existing));
    } finally {
      closed.set(true);
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  void cancellationReleasesPendingSend() throws Exception {
    final Set<Thread> existing = workers();
    CompletableFuture<Void> delivery = new CompletableFuture<>();
    when(sink.send(any(OutboundSseEvent.class))).thenAnswer(invocation -> delivery);
    job.output = new BoardFileDetails();
    job.output.setData("current-output".getBytes(StandardCharsets.UTF_8));
    try {
      withJob(() -> startStream(false));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      job.state = RoutingJobState.CANCELLED;
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing));
      assertTrue(delivery.isCancelled());
    } finally {
      closed.set(true);
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  @app.freerouting.logger.AllowErrorLogs("Exercises the bounded SSE send deadline")
  void stalledSendTimesOutAndReleasesWorker() throws Exception {
    final Set<Thread> existing = workers();
    CompletableFuture<Void> delivery = new CompletableFuture<>();
    when(sink.send(any(OutboundSseEvent.class))).thenAnswer(invocation -> delivery);
    job.output = new BoardFileDetails();
    job.output.setData("current-output".getBytes(StandardCharsets.UTF_8));
    try {
      withJob(() -> startStream(false));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      verify(sink, timeout(13000)).close();
      assertTrue(delivery.isCompletedExceptionally());
      assertTrue(awaitWorkerExit(existing));
    } finally {
      closed.set(true);
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  void pendingDeliveryCoalescesChangesAndStillSendsFinalSnapshot() throws Exception {
    final Set<Thread> existing = workers();
    CompletableFuture<Void> delivery = new CompletableFuture<>();
    when(sink.send(any(OutboundSseEvent.class))).thenAnswer(invocation -> delivery);
    job.output = new BoardFileDetails();
    job.output.setData("preview-output".getBytes(StandardCharsets.UTF_8));
    try {
      withJob(() -> startStream(false));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      job.output.setData("final-output".getBytes(StandardCharsets.UTF_8));
      job.state = RoutingJobState.COMPLETED;
      Thread.sleep(500);
      verify(sink, times(1)).send(any(OutboundSseEvent.class));
      verify(sink, never()).close();
      delivery.complete(null);
      verify(sink, timeout(2000).times(2)).send(any(OutboundSseEvent.class));
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing));
      ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
      verify(eventBuilder, times(2)).data(payload.capture());
      assertTrue(payload.getAllValues().getLast().contains("ZmluYWwtb3V0cHV0"));
    } finally {
      delivery.complete(null);
      closed.set(true);
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  void jsonStreamSuppressesUnchangedBoardAndWaitsForFinalDelivery() throws Exception {
    final Set<Thread> existing = workers();
    job.board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");
    try {
      withJob(() -> startStream(true));
      verify(sink, timeout(2000)).send(any(OutboundSseEvent.class));
      Thread.sleep(1100);
      verify(sink, times(1)).send(any(OutboundSseEvent.class));
      verify(eventBuilder).name("json-output");
      ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
      verify(eventBuilder).data(json.capture());
      assertTrue(json.getValue().contains("layers"));

      CompletableFuture<Void> delivery = new CompletableFuture<>();
      when(sink.send(any(OutboundSseEvent.class))).thenAnswer(invocation -> delivery);
      job.name = "final-json-board";
      job.state = RoutingJobState.COMPLETED;
      verify(sink, timeout(2000).times(2)).send(any(OutboundSseEvent.class));
      verify(sink, never()).close();
      delivery.complete(null);
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing));
    } finally {
      closed.set(true);
      job.state = RoutingJobState.CANCELLED;
      awaitWorkerExit(existing);
    }
  }

  @Test
  void pausedJobKeepsStreamOpen() throws Exception {
    final Set<Thread> existing = workers();
    job.state = RoutingJobState.PAUSED;
    try {
      withJob(() -> startStream(false));
      assertTrue(awaitNewWorker(existing));
      Thread.sleep(500);
      verify(sink, never()).close();
      assertFalse(closed.get());
    } finally {
      job.state = RoutingJobState.CANCELLED;
      closed.set(true);
      awaitWorkerExit(existing);
    }
  }

  @Test
  void nullJobStateRemainsNonTerminalDuringPolling() throws Exception {
    final Set<Thread> existing = workers();
    try {
      withJob(() -> startStream(false));
      assertTrue(awaitNewWorker(existing));
      job.state = null;
      Thread.sleep(300);
      verify(sink, never()).close();
      assertFalse(closed.get());
      job.state = RoutingJobState.CANCELLED;
      verify(sink, timeout(2000)).close();
      assertTrue(awaitWorkerExit(existing));
    } finally {
      job.state = RoutingJobState.CANCELLED;
      closed.set(true);
      awaitWorkerExit(existing);
    }
  }

  private void startStream(boolean json) {
    if (json) {
      resource.streamOutputJson(job.id.toString(), sink, sse);
    } else {
      resource.streamOutput(job.id.toString(), sink, sse);
    }
  }

  private void withJob(Runnable request) {
    RoutingJobScheduler scheduler = mock(RoutingJobScheduler.class);
    SessionManager sessions = mock(SessionManager.class);
    Session session = mock(Session.class);
    when(scheduler.getJob(job.id.toString())).thenReturn(job);
    when(sessions.getSession(job.sessionId.toString(), userId)).thenReturn(session);
    try (var schedulerScope = mockStatic(RoutingJobScheduler.class);
        var sessionScope = mockStatic(SessionManager.class)) {
      schedulerScope.when(RoutingJobScheduler::getInstance).thenReturn(scheduler);
      sessionScope.when(SessionManager::getInstance).thenReturn(sessions);
      request.run();
    }
  }

  private static Set<Thread> workers() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> thread.getName().startsWith("job-output-") && thread.isAlive())
        .collect(Collectors.toSet());
  }

  private static boolean awaitNewWorker(Set<Thread> existing) throws InterruptedException {
    long deadline = System.nanoTime() + 2_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (workers().stream().anyMatch(thread -> !existing.contains(thread))) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private static boolean awaitWorkerExit(Set<Thread> existing) throws InterruptedException {
    long deadline = System.nanoTime() + 2_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (workers().stream().noneMatch(thread -> !existing.contains(thread))) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }
}
