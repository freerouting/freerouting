package app.freerouting.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RandomSeedTest extends TestBasedOnAnIssue
{
    @Test
    void testRandomSeed() {
        // Test with a fixed seed
        long fixedSeed = 12345L;
        String firstHash = null;
        System.out.println("Testing with fixed seed: " + fixedSeed);
        for (int i = 0; i < 3; i++) {
            var job = GetRoutingJob("Issue026-J2_reference.dsn", fixedSeed);
            job = RunRoutingJob(job, job.routerSettings);
            String currentHash = job.board.get_hash();
            if (i == 0) {
                firstHash = currentHash;
            } else {
                assertEquals(firstHash, currentHash, "Seeded runs should produce identical results. Run " + (i+1));
            }
        }

        // Test without a seed
        String previousHash = null;
        boolean foundDifference = false;
        System.out.println("Testing without seed.");
        // Increase loop iterations to reduce chance of flaky pass
        for (int i = 0; i < 5; i++) {
            var job = GetRoutingJob("Issue026-J2_reference.dsn"); // No seed
            job = RunRoutingJob(job, job.routerSettings);
            String currentHash = job.board.get_hash();
            if (i > 0) {
                if (!previousHash.equals(currentHash)) {
                    foundDifference = true;
                    break;
                }
            }
            previousHash = currentHash;
        }
        assertTrue(foundDifference, "Unseeded runs should produce different results. This might fail by chance, try running again.");
    }
}
