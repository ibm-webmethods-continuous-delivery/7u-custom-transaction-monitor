package com.ibm.tel.wm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
//import com.wm.app.b2b.server.Server;
import com.wm.app.b2b.server.ListenerAdmin;
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
     * @param transactionIntervalsCountDelta the amount to increment transaction count
     * @param durationMillis the duration of the service call in milliseconds
     */
    public void incrementMetrics(String serviceNS, long invokeCountDelta, long transactionIntervalsCountDelta, long durationMillis) {
        if (serviceNS == null) {
            throw new IllegalArgumentException("serviceNS cannot be null");
        }

        // Use computeIfAbsent for thread-safe initialization
        // This only synchronizes on write (when key is absent), not on read
        ServiceMetrics metrics = metricsMap.computeIfAbsent(serviceNS, k -> new ServiceMetrics());

        // Increment operations are thread-safe at the ServiceMetrics level
        // using AtomicLong, so no additional synchronization needed here
        metrics.incrementInvokeCount(invokeCountDelta);
        metrics.incrementTransactionIntervalsCount(transactionIntervalsCountDelta);
        metrics.updateDuration(durationMillis, transactionIntervalsCountDelta, serviceNS);
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
     * Exports all metrics as a CSV string with context information.
     * Format: Hostname,RuntimePort,ExportTimestampMillis,CollectionStartMillis,CollectionDurationSeconds,ServiceNS,InvokeCount,TransactionIntervalsCount,MaxDurationMillis,AvgSecondDuration,Histogram[1],...,Histogram[N],Histogram[>N]
     *
     * @return CSV string containing all metrics with context
     */
    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();

        // Get context information
        String hostname = getHostname();
        int runtimePort = getRuntimePort();
        long exportTimestamp = System.currentTimeMillis();
        long collectionStart = initMillis;
        long collectionDurationSeconds = (exportTimestamp - collectionStart) / 1000;

        // Build header with context columns first
        csv.append("Hostname,RuntimePort,ExportTimestampMillis,CollectionStartMillis,CollectionDurationSeconds,");
        csv.append("ServiceNS,InvokeCount,TransactionIntervalsCount,MaxDurationMillis,AvgSecondDuration");

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
                // Add context columns
                csv.append(hostname).append(",");
                csv.append(runtimePort).append(",");
                csv.append(exportTimestamp).append(",");
                csv.append(collectionStart).append(",");
                csv.append(collectionDurationSeconds).append(",");

                // Add service metrics
                csv.append(escapeCSV(serviceNS)).append(",");
                csv.append(metrics.getInvokeCount()).append(",");
                csv.append(metrics.getTransactionIntervalsCount()).append(",");
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
     * Exports all metrics to a CSV file with auto-generated filename.
     * Filename format: metrics_hostname_yyyyMMdd_HHmmss.csv
     * Creates parent directories if they don't exist.
     *
     * @param directoryPath the directory where CSV file should be written
     * @return the full path of the created file
     * @throws IOException if an I/O error occurs while writing the file
     * @throws IllegalArgumentException if directoryPath is null or empty
     */
    public String exportToCSVFileInFolder(String directoryPath) throws IOException {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {  
            throw new IllegalArgumentException("directoryPath cannot be null or empty");
        }

        // Generate filename with hostname and timestamp
        String hostname = getHostname();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String filename = String.format("metrics_%s_%s.csv", hostname, timestamp);

        Path dirPath = Paths.get(directoryPath);
        Path filePath = dirPath.resolve(filename);

        // Create parent directories if they don't exist
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
                JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                    "Created directory: " + dirPath);
            } catch (IOException e) {
                JournalLogger.logInfo(JournalLogger.LOG_MSG, JournalLogger.FAC_LICENSE_MGR,
                    "Failed to create directory: " + dirPath + " - " + e.getMessage());
                throw new IOException("Failed to create directory: " + dirPath, e);
            }
        }

        // Get CSV content
        String csvContent = exportToCSV();

        // Write to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toString()))) {
            writer.write(csvContent);
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                "Successfully exported metrics to: " + filePath);
        } catch (IOException e) {
            JournalLogger.logInfo(JournalLogger.LOG_MSG, JournalLogger.FAC_LICENSE_MGR,
                "Failed to write CSV to file: " + filePath + " - " + e.getMessage());
            throw new IOException("Failed to write CSV to file: " + filePath, e);
        }

        return filePath.toString();
    }

    /**
     * Exports all metrics to a CSV file with explicit filename.
     * Creates parent directories if they don't exist.
     *
     * @param filePath the full path to the file where CSV data should be written
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
     * Gets the hostname of the system.
     *
     * @return the hostname, or "unknown-host" if it cannot be determined
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                "Warning: Could not determine hostname: " + e.getMessage());
            return "unknown-host";
        }
    }

    /**
     * Gets the runtime port of the Integration Server.
     *
     * @return the primary port number, or 0 if it cannot be determined
     */
    private int getRuntimePort() {
        try {
            return ListenerAdmin.getPrimaryListener().getPort();
        } catch (Exception e) {
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                "Warning: Could not determine runtime port: " + e.getMessage());
            return 0;
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
