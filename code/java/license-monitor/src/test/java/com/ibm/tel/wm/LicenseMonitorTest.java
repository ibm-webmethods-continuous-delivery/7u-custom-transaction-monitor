package com.ibm.tel.wm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LicenseMonitor singleton class.
 */
class LicenseMonitorTest {

    private LicenseMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = LicenseMonitor.getInstance();
        monitor.clearAllMetrics();
    }

    @Test
    void testSingletonInstance() {
        LicenseMonitor instance1 = LicenseMonitor.getInstance();
        LicenseMonitor instance2 = LicenseMonitor.getInstance();
        
        assertSame(instance1, instance2, "getInstance should always return the same instance");
    }

    @Test
    void testSingletonAcrossThreads() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        LicenseMonitor[] instances = new LicenseMonitor[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    instances[index] = LicenseMonitor.getInstance();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        LicenseMonitor firstInstance = instances[0];
        for (int i = 1; i < threadCount; i++) {
            assertSame(firstInstance, instances[i], 
                "All threads should get the same singleton instance");
        }
    }

    @Test
    void testIncrementMetricsCreatesNewEntry() {
        String serviceNS = "test.service";
        
        assertNull(monitor.getMetrics(serviceNS), "Metrics should not exist initially");
        
        monitor.incrementMetrics(serviceNS, 5, 3, 100L);
        
        ServiceMetrics metrics = monitor.getMetrics(serviceNS);
        assertNotNull(metrics, "Metrics should be created after increment");
        assertEquals(5, metrics.getInvokeCount(), "Invoke count should be 5");
        assertEquals(3, metrics.getTransactionCount(), "Transaction count should be 3");
    }

    @Test
    void testIncrementMetricsUpdatesExistingEntry() {
        String serviceNS = "test.service";
        
        monitor.incrementMetrics(serviceNS, 5, 3, 100L);
        monitor.incrementMetrics(serviceNS, 2, 7, 200L);
        
        ServiceMetrics metrics = monitor.getMetrics(serviceNS);
        assertNotNull(metrics, "Metrics should exist");
        assertEquals(7, metrics.getInvokeCount(), "Invoke count should be 7");
        assertEquals(10, metrics.getTransactionCount(), "Transaction count should be 10");
    }

    @Test
    void testIncrementMetricsWithNullServiceNS() {
        assertThrows(IllegalArgumentException.class, 
            () -> monitor.incrementMetrics(null, 1, 1, 100L),
            "Should throw IllegalArgumentException for null serviceNS");
    }

    @Test
    void testMultipleServiceNamespaces() {
        monitor.incrementMetrics("service1", 10, 20, 100L);
        monitor.incrementMetrics("service2", 30, 40, 200L);
        monitor.incrementMetrics("service3", 50, 60, 300L);

        ServiceMetrics metrics1 = monitor.getMetrics("service1");
        ServiceMetrics metrics2 = monitor.getMetrics("service2");
        ServiceMetrics metrics3 = monitor.getMetrics("service3");

        assertNotNull(metrics1);
        assertNotNull(metrics2);
        assertNotNull(metrics3);

        assertEquals(10, metrics1.getInvokeCount());
        assertEquals(20, metrics1.getTransactionCount());
        assertEquals(30, metrics2.getInvokeCount());
        assertEquals(40, metrics2.getTransactionCount());
        assertEquals(50, metrics3.getInvokeCount());
        assertEquals(60, metrics3.getTransactionCount());
    }

    @Test
    void testConcurrentIncrementsOnSameService() throws InterruptedException {
        String serviceNS = "concurrent.service";
        int threadCount = 100;
        int incrementsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        monitor.incrementMetrics(serviceNS, 1, 2, 50L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Executor should terminate");

        ServiceMetrics metrics = monitor.getMetrics(serviceNS);
        assertNotNull(metrics, "Metrics should exist");

        long expectedInvokeCount = (long) threadCount * incrementsPerThread;
        long expectedTransactionCount = expectedInvokeCount * 2;

        assertEquals(expectedInvokeCount, metrics.getInvokeCount(), 
            "Invoke count should be " + expectedInvokeCount + " after concurrent increments");
        assertEquals(expectedTransactionCount, metrics.getTransactionCount(), 
            "Transaction count should be " + expectedTransactionCount + " after concurrent increments");
    }

    @Test
    void testConcurrentIncrementsOnDifferentServices() throws InterruptedException {
        int serviceCount = 50;
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    int serviceIndex = counter.getAndIncrement() % serviceCount;
                    String serviceNS = "service." + serviceIndex;
                    monitor.incrementMetrics(serviceNS, 1, 1, 100L);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");

        // Each service should have been incremented threadCount/serviceCount times
        int expectedCountPerService = threadCount / serviceCount;
        for (int i = 0; i < serviceCount; i++) {
            String serviceNS = "service." + i;
            ServiceMetrics metrics = monitor.getMetrics(serviceNS);
            assertNotNull(metrics, "Metrics should exist for " + serviceNS);
            assertEquals(expectedCountPerService, metrics.getInvokeCount(), 
                "Invoke count for " + serviceNS + " should be " + expectedCountPerService);
            assertEquals(expectedCountPerService, metrics.getTransactionCount(), 
                "Transaction count for " + serviceNS + " should be " + expectedCountPerService);
        }
    }

    @Test
    void testConcurrentMixedOperations() throws InterruptedException {
        String serviceNS = "mixed.service";
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Some threads increment, some read
                    if (threadId % 2 == 0) {
                        monitor.incrementMetrics(serviceNS, threadId, threadId * 2, threadId * 10L);
                    } else {
                        ServiceMetrics metrics = monitor.getMetrics(serviceNS);
                        // Just reading, no assertion needed as it might be null initially
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");

        ServiceMetrics metrics = monitor.getMetrics(serviceNS);
        assertNotNull(metrics, "Metrics should exist after mixed operations");
        
        // Calculate expected sum: sum of even numbers from 0 to 48 (0,2,4,...,48)
        // Sum = 0 + 2 + 4 + ... + 48 = 2(0 + 1 + 2 + ... + 24) = 2 * (24 * 25 / 2) = 600
        long expectedInvokeCount = 0;
        long expectedTransactionCount = 0;
        for (int i = 0; i < threadCount; i += 2) {
            expectedInvokeCount += i;
            expectedTransactionCount += i * 2;
        }

        assertEquals(expectedInvokeCount, metrics.getInvokeCount(), 
            "Invoke count should match expected sum");
        assertEquals(expectedTransactionCount, metrics.getTransactionCount(), 
            "Transaction count should match expected sum");
    }

    @Test
    void testGetMetricsForNonExistentService() {
        assertNull(monitor.getMetrics("non.existent.service"), 
            "Should return null for non-existent service");
    }

    @Test
    void testClearAllMetrics() {
        monitor.incrementMetrics("service1", 10, 20, 100L);
        monitor.incrementMetrics("service2", 30, 40, 200L);
        
        assertNotNull(monitor.getMetrics("service1"));
        assertNotNull(monitor.getMetrics("service2"));
        
        monitor.clearAllMetrics();
        
        assertNull(monitor.getMetrics("service1"), "Metrics should be cleared");
        assertNull(monitor.getMetrics("service2"), "Metrics should be cleared");
    }

    @Test
    void testExportToCSVFile() throws IOException {
        // Setup test data
        monitor.incrementMetrics("service1", 10, 20, 100L);
        monitor.incrementMetrics("service2", 30, 40, 200L);
        
        // Create temp file path
        String tempDir = System.getProperty("java.io.tmpdir");
        String filePath = tempDir + "/test-metrics-" + System.currentTimeMillis() + ".csv";
        
        try {
            // Export to file
            monitor.exportToCSVFile(filePath);
            
            // Verify file exists
            Path path = Paths.get(filePath);
            assertTrue(Files.exists(path), "CSV file should be created");
            
            // Read and verify content
            String content = new String(Files.readAllBytes(path));
            assertTrue(content.contains("ServiceNS,InvokeCount,TransactionCount"), 
                "CSV should contain header");
            assertTrue(content.contains("service1"), "CSV should contain service1 data");
            assertTrue(content.contains("service2"), "CSV should contain service2 data");
            
        } finally {
            // Cleanup
            Files.deleteIfExists(Paths.get(filePath));
        }
    }

    @Test
    void testExportToCSVFileWithNullPath() {
        assertThrows(IllegalArgumentException.class, 
            () -> monitor.exportToCSVFile(null),
            "Should throw IllegalArgumentException for null file path");
    }

    @Test
    void testExportToCSVFileWithEmptyPath() {
        assertThrows(IllegalArgumentException.class, 
            () -> monitor.exportToCSVFile(""),
            "Should throw IllegalArgumentException for empty file path");
        
        assertThrows(IllegalArgumentException.class, 
            () -> monitor.exportToCSVFile("   "),
            "Should throw IllegalArgumentException for whitespace-only file path");
    }

    @Test
    void testExportToCSVFileCreatesParentDirectory() throws IOException {
        // Setup test data
        monitor.incrementMetrics("test.service", 5, 10, 150L);
        
        // Create path with non-existent parent directory
        String tempDir = System.getProperty("java.io.tmpdir");
        String subDir = "test-metrics-" + System.currentTimeMillis();
        String filePath = tempDir + "/" + subDir + "/metrics.csv";
        
        try {
            // Export to file
            monitor.exportToCSVFile(filePath);
            
            // Verify file and directory exist
            Path path = Paths.get(filePath);
            assertTrue(Files.exists(path), "CSV file should be created");
            assertTrue(Files.exists(path.getParent()), "Parent directory should be created");
            
        } finally {
            // Cleanup
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            if (path.getParent() != null) {
                Files.deleteIfExists(path.getParent());
            }
        }
    }

    @Test
    void testExportToCSVFileOverwritesExisting() throws IOException {
        // Setup test data
        monitor.incrementMetrics("service1", 10, 20, 100L);
        
        String tempDir = System.getProperty("java.io.tmpdir");
        String filePath = tempDir + "/test-overwrite-" + System.currentTimeMillis() + ".csv";
        
        try {
            // First export
            monitor.exportToCSVFile(filePath);
            String firstContent = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Add more data and export again
            monitor.incrementMetrics("service2", 30, 40, 200L);
            monitor.exportToCSVFile(filePath);
            String secondContent = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Verify file was overwritten with new content
            assertNotEquals(firstContent, secondContent, "File should be overwritten");
            assertTrue(secondContent.contains("service2"), "New content should include service2");
            
        } finally {
            // Cleanup
            Files.deleteIfExists(Paths.get(filePath));
        }
    }

}
