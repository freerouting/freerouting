package app.freerouting.logger;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

class LogEntriesTest {

    @Test
    void addAndRemoveLogEntryAddedListener() {
        LogEntries logEntries = new LogEntries();
        AtomicInteger listenerCallCount = new AtomicInteger(0);

        LogEntries.LogEntryAddedListener listener = _ -> listenerCallCount.incrementAndGet();

        // Add listener and verify it's called
        logEntries.addLogEntryAddedListener(listener);
        logEntries.add(LogEntryType.Info, "Test message 1", null);
        assertEquals(1, listenerCallCount.get(), "Listener should be called after being added");

        // Remove listener and verify it's no longer called
        logEntries.removeLogEntryAddedListener(listener);
        logEntries.add(LogEntryType.Info, "Test message 2", null);
        assertEquals(1, listenerCallCount.get(), "Listener should not be called after being removed");
    }
}
