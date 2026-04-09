package com.ibm.tel.wm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceMetrics class.
 */
class ServiceMetricsTest {

    private ServiceMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ServiceMetrics();
    }

    @Test
    void testInitialCountsAreZero() {
        assertEquals(0, metrics.getInvokeCount(), "Initial invoke count should be 0");
        assertEquals(0, metrics.getTransactionCount(), "Initial transaction count should be 0");
    }

    @Test
    void testIncrementInvokeCount() {
        metrics.incrementInvokeCount(5);
        assertEquals(5, metrics.getInvokeCount(), "Invoke count should be 5 after increment");
        assertEquals(0, metrics.getTransactionCount(), "Transaction count should remain 0");
    }

    @Test
    void testIncrementTransactionCount() {
        metrics.incrementTransactionCount(3);
        assertEquals(0, metrics.getInvokeCount(), "Invoke count should remain 0");
        assertEquals(3, metrics.getTransactionCount(), "Transaction count should be 3 after increment");
    }

    @Test
    void testMultipleIncrements() {
        metrics.incrementInvokeCount(10);
        metrics.incrementInvokeCount(5);
        metrics.incrementTransactionCount(7);
        metrics.incrementTransactionCount(3);

        assertEquals(15, metrics.getInvokeCount(), "Invoke count should be 15");
        assertEquals(10, metrics.getTransactionCount(), "Transaction count should be 10");
    }

    @Test
    void testIncrementWithNegativeValues() {
        metrics.incrementInvokeCount(10);
        metrics.incrementInvokeCount(-3);
        assertEquals(7, metrics.getInvokeCount(), "Invoke count should be 7 after negative increment");
    }

    @Test
    void testConcurrentIncrements() throws InterruptedException {
        int threadCount = 100;
        int incrementsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metrics.incrementInvokeCount(1);
                        metrics.incrementTransactionCount(1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");

        long expectedCount = (long) threadCount * incrementsPerThread;
        assertEquals(expectedCount, metrics.getInvokeCount(), 
            "Invoke count should be " + expectedCount + " after concurrent increments");
        assertEquals(expectedCount, metrics.getTransactionCount(), 
            "Transaction count should be " + expectedCount + " after concurrent increments");
    }

    @Test
    void testConcurrentIncrementsWithDifferentDeltas() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int delta = i + 1;
            executor.submit(() -> {
                try {
                    metrics.incrementInvokeCount(delta);
                    metrics.incrementTransactionCount(delta * 2);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");

        // Sum of 1 to 50 = 50 * 51 / 2 = 1275
        long expectedInvokeCount = (long) threadCount * (threadCount + 1) / 2;
        long expectedTransactionCount = expectedInvokeCount * 2;

        assertEquals(expectedInvokeCount, metrics.getInvokeCount(), 
            "Invoke count should be " + expectedInvokeCount);
        assertEquals(expectedTransactionCount, metrics.getTransactionCount(), 
            "Transaction count should be " + expectedTransactionCount);
    }
}
