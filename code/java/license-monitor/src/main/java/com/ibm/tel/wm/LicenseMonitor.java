package com.ibm.tel.wm;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class for monitoring service license metrics.
 * Initialized at class loading time and provides thread-safe operations.
 */
public class LicenseMonitor {
    
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
     */
    public void incrementMetrics(String serviceNS, long invokeCountDelta, long transactionCountDelta) {
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
     * Clears all metrics. Useful for testing.
     */
    void clearAllMetrics() {
        metricsMap.clear();
    }
}
