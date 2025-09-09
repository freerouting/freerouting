package app.freerouting.tests;

import app.freerouting.Freerouting;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class Issue522Test {

    @Test
    public void testMaxPassesInCliMode() {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("freerouting-test-");
            Path inputDsn = findTestFile("Issue026-J2_reference.dsn");
            if (inputDsn == null) {
                fail("Could not find test DSN file");
                return;
            }

            Path outputSes = tempDir.resolve("output.ses");
            Path logFile = tempDir.resolve("freerouting.log");

            String[] args = {
                    "--gui.enabled=false",
                    "--user_data_path=" + tempDir.toString(),
                    "-de", inputDsn.toString(),
                    "-do", outputSes.toString(),
                    "-mp", "2"
            };

            // Replicate the logic from Freerouting.main() without calling System.exit()
            GlobalSettings globalSettings = new GlobalSettings();
            globalSettings.applyCommandLineArguments(args);
            Freerouting.globalSettings = globalSettings;
            GlobalSettings.setUserDataPath(tempDir);

            // Get the scheduler instance to ensure its thread is running
            RoutingJobScheduler.getInstance();

            // Use reflection to call the private InitializeCLI method
            Method initializeCSLIMethod = Freerouting.class.getDeclaredMethod("InitializeCLI", GlobalSettings.class);
            initializeCSLIMethod.setAccessible(true);
            initializeCSLIMethod.invoke(null, globalSettings);


            assertTrue(Files.exists(logFile), "Log file should have been created");
            String logContent = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);

            assertTrue(logContent.contains("Auto-router pass #1"), "Log should contain pass #1");
            assertTrue(logContent.contains("Auto-router pass #2"), "Log should contain pass #2");
            assertFalse(logContent.contains("Auto-router pass #3"), "Log should not contain pass #3");

        } catch (Exception e) {
            fail("Test failed with exception", e);
        } finally {
            // Clean up
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    // Ignore cleanup exception
                }
            }
        }
    }

    private Path findTestFile(String filename) {
        Path testDirectory = Paths.get(".").toAbsolutePath();
        File testFile = testDirectory.resolve("tests").resolve(filename).toFile();
        while (!testFile.exists()) {
            testDirectory = testDirectory.getParent();
            if (testDirectory == null) {
                return null;
            }
            testFile = testDirectory.resolve("tests").resolve(filename).toFile();
        }
        return testFile.toPath();
    }
}
