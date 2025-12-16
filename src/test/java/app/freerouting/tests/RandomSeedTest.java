package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RandomSeedTest extends TestBasedOnAnIssue
{
    @Test
    void testRandomSeed()
    {
        // Test with a fixed seed
        long fixedSeed = 12345L;
        String firstHash = null;
      IO.println("Testing with fixed seed: " + fixedSeed);
        for (int i = 0; i < 3; i++)
        {
            var job = GetRoutingJob("Issue026-J2_reference.dsn", fixedSeed);
            job = RunRoutingJob(job, job.routerSettings);
            String currentHash = job.board.get_hash();
            if (i == 0)
            {
                firstHash = currentHash;
            } else
            {
                assertEquals(firstHash, currentHash, "Seeded runs should produce identical results. Run " + (i + 1));
            }
        }

        // Test without a seed
        String previousHash = null;
        boolean foundDifference = false;
      IO.println("Testing without seed.");
        // Increase loop iterations to reduce chance of flaky pass
        for (int i = 0; i < 5; i++)
        {
            var job = GetRoutingJob("Issue026-J2_reference.dsn"); // No seed
            job = RunRoutingJob(job, job.routerSettings);
            String currentHash = job.board.get_hash();
            if (i > 0)
            {
                if (!previousHash.equals(currentHash))
                {
                    foundDifference = true;
                    break;
                }
            }
            previousHash = currentHash;
        }
        assertTrue(foundDifference, "Unseeded runs should produce different results. This might fail by chance, try running again.");
    }

    @Test
    void testRepeatSameInputSeedProducesSameHash()
    {
        long seed = 987654321L;
        // First run
        var job1 = GetRoutingJob("Issue026-J2_reference.dsn", seed);
        job1 = RunRoutingJob(job1, job1.routerSettings);
        String hash1 = job1.board.get_hash();

        // Second run with the same input and seed
        var job2 = GetRoutingJob("Issue026-J2_reference.dsn", seed);
        job2 = RunRoutingJob(job2, job2.routerSettings);
        String hash2 = job2.board.get_hash();

        assertEquals(hash1, hash2, "Two runs with the same input and seed should produce identical hashes.");
    }
}
