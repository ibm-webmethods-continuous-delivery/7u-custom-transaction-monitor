package com.ibm.tel.wm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import com.wm.util.JournalLogger;

/**
 * Singleton class for monitoring service license metrics.
 * Initialized at class loading time and provides thread-safe operations.
 */
public class LicenseMonitor {
    
    private static final long initMillis = System.currentTimeMillis();
    
    // Singleton instance - initialized at class loading time
    private static final LicenseMonitor INSTANCE = new LicenseMonitor();
    
    // Thread-safe map for storing service metrics
    private final ConcurrentHashMap<String, ServiceMetrics> metricsMap;
    
    /**
     * Private constructor to prevent instantiation.
     */
    private LicenseMonitor() {
        this.metricsMap = new ConcurrentHashMap<>();
    }
    
    /**
     * Gets the initMillis - time counters started from zero
     * 
     * @return the initMillis
     */
    public static long getInitMillis() {
        return initMillis;
    }

        /**
     * Gets the singleton instance.
     * 
     * @return the LicenseMonitor instance
     */
    public static LicenseMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Increments the metrics for a given service namespace.
     * This method is thread-safe and optimized to synchronize only on the specific
     * ServiceMetrics object being updated, not on the entire map.
     * 
     * @param serviceNS the service namespace key
     * @param invokeCountDelta the amount to increment invoke count
     * @param transactionCountDelta the amount to increment transaction count
     * @param durationMillis the duration of the service call in milliseconds
     */
    public void incrementMetrics(String serviceNS, long invokeCountDelta, long transactionCountDelta, long durationMillis) {
        if (serviceNS == null) {
            throw new IllegalArgumentException("serviceNS cannot be null");
        }
        
        // Use computeIfAbsent for thread-safe initialization
        // This only synchronizes on write (when key is absent), not on read
        ServiceMetrics metrics = metricsMap.computeIfAbsent(serviceNS, k -> new ServiceMetrics());
        
        // Increment operations are thread-safe at the ServiceMetrics level
        // using AtomicLong, so no additional synchronization needed here
        metrics.incrementInvokeCount(invokeCountDelta);
        metrics.incrementTransactionCount(transactionCountDelta);
        metrics.updateDuration(durationMillis, transactionCountDelta, serviceNS);
    }
    
    /**
     * Gets the metrics for a given service namespace.
     * 
     * @param serviceNS the service namespace key
     * @return the ServiceMetrics for the given key, or null if not present
     */
    public ServiceMetrics getMetrics(String serviceNS) {
        return metricsMap.get(serviceNS);
    }

    /**
     * Gets all tracked service namespaces.
     *
     * @return an array containing all tracked service namespace keys
     */
    public String[] getServiceNamespaces() {
        return metricsMap.keySet().toArray(new String[0]);
    }
    
    /**
     * Exports all metrics as a CSV string.
     * Format: ServiceNS,InvokeCount,TransactionCount,MaxDurationMillis,AvgSecondDuration,Histogram[1],Histogram[2],...,Histogram[N],Histogram[>N]
     * 
     * @return CSV string containing all metrics
     */
    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();
        
        // Build header
        csv.append("ServiceNS,InvokeCount,TransactionCount,MaxDurationMillis,AvgSecondDuration");
        
        // Add histogram headers
        int histogramSize = ConfigLoader.getInstance().getTransactionsHistogramCount();
        for (int i = 1; i <= histogramSize; i++) {
            csv.append(",Histogram[").append(i).append("]");
        }
        csv.append(",Histogram[>").append(histogramSize).append("]");
        csv.append("\n");
        
        // Add data rows
        String[] serviceNamespaces = getServiceNamespaces();
        for (String serviceNS : serviceNamespaces) {
            ServiceMetrics metrics = getMetrics(serviceNS);
            if (metrics != null) {
                csv.append(escapeCSV(serviceNS)).append(",");
                csv.append(metrics.getInvokeCount()).append(",");
                csv.append(metrics.getTransactionCount()).append(",");
                csv.append(metrics.getMaxDurationMillis()).append(",");
                csv.append(String.format("%.2f", metrics.getAvgSecondDuration()));
                
                // Add histogram data
                long[] histogram = metrics.getHistogramArray();
                for (long count : histogram) {
                    csv.append(",").append(count);
                }
                csv.append("\n");
            }
        }
        
        return csv.toString();
    }
    
    /**
     * Exports all metrics to a CSV file.
     * Creates parent directories if they don't exist.
     * 
     * @param filePath the path to the file where CSV data should be written
     * @throws IOException if an I/O error occurs while writing the file
     * @throws IllegalArgumentException if filePath is null or empty
     */
    public void exportToCSVFile(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("filePath cannot be null or empty");
        }
        
        Path path = Paths.get(filePath);
        
        // Create parent directories if they don't exist
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
                JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                    "Created directory: " + parentDir);
            } catch (IOException e) {
                JournalLogger.logInfo(JournalLogger.LOG_MSG, JournalLogger.FAC_LICENSE_MGR,
                    "Failed to create directory: " + parentDir + " - " + e.getMessage());
                throw new IOException("Failed to create directory: " + parentDir, e);
            }
        }
        
        // Get CSV content
        String csvContent = exportToCSV();
        
        // Write to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(csvContent);
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                "Successfully exported metrics to: " + filePath);
        } catch (IOException e) {
            JournalLogger.logInfo(JournalLogger.LOG_MSG, JournalLogger.FAC_LICENSE_MGR,
                "Failed to write CSV to file: " + filePath + " - " + e.getMessage());
            throw new IOException("Failed to write CSV to file: " + filePath, e);
        }
    }
    
    /**
     * Escapes a CSV field value by wrapping it in quotes if it contains special characters.
     * 
     * @param value the value to escape
     * @return the escaped value
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Clears all metrics. Useful for testing.
     */
    void clearAllMetrics() {
        metricsMap.clear();
    }
}
