package com.ibm.tel.wm;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import com.wm.util.JournalLogger;

/**
 * Value object representing metrics for a service.
 * Thread-safe using atomic operations for counters.
 */
public class ServiceMetrics {
    private final AtomicLong invokeCount;
    private final AtomicLong transactionIntervalsCount;
    private final AtomicLong maxDurationMillis;
    private final AtomicLong totalDurationSeconds;
    private final AtomicLongArray transactionHistogram;
    private final int histogramSize;

    /**
     * Creates a new ServiceMetrics instance with counts initialized to zero.
     */
    public ServiceMetrics() {
        this.invokeCount = new AtomicLong(0);
        this.transactionIntervalsCount = new AtomicLong(0);
        this.maxDurationMillis = new AtomicLong(0);
        this.totalDurationSeconds = new AtomicLong(0);
        
        // Initialize histogram with size from config
        this.histogramSize = ConfigLoader.getInstance().getTransactionsHistogramCount();
        // Size is histogramSize + 1 to include the ">N" bucket
        this.transactionHistogram = new AtomicLongArray(histogramSize + 1);
    }

    /**
     * Increments the invoke count by the specified delta.
     * 
     * @param delta the amount to increment
     */
    public void incrementInvokeCount(long delta) {
        invokeCount.addAndGet(delta);
    }

    /**
     * Increments the transaction count by the specified delta.
     * 
     * @param delta the amount to increment
     */
    public void incrementtransactionIntervalsCount(long delta) {
        transactionIntervalsCount.addAndGet(delta);
    }

    /**
     * Updates metrics with duration information.
     * This method is thread-safe and handles:
     * - Updating max duration if current duration is greater
     * - Adding to total duration for average calculation
     * - Updating histogram bucket based on transaction count
     * 
     * @param durationMillis the duration of the service call in milliseconds
     * @param transactionIntervalsCount the number of transaction times consumed
     * @param serviceNS the service namespace (for logging new records)
     */
    public void updateDuration(long durationMillis, long transactionIntervalsCount, String serviceNS) {
        // Update max duration using compare-and-swap loop
        long currentMax;
        do {
            currentMax = maxDurationMillis.get();
            if (durationMillis <= currentMax) {
                break; // Current duration is not greater than max
            }
        } while (!maxDurationMillis.compareAndSet(currentMax, durationMillis));
        
        // If we successfully updated the max, log it
        if (durationMillis > currentMax && currentMax > 0) {
            // BUG ? Seems that LOG_MSG and LOG_EXCEPTION are inverted!
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR, 
                "New time record for service '" + serviceNS + "': " + durationMillis + " ms");
        }
        
        // Add to total duration in seconds for average calculation
        long durationSeconds = durationMillis / 1000;
        totalDurationSeconds.addAndGet(durationSeconds);
        
        // Update histogram bucket
        // transactionIntervalsCount represents how many transaction time intervals were consumed
        // Index 0 = 1 interval, Index 1 = 2 intervals, etc.
        // Last index = >N intervals
        int bucketIndex;
        if (transactionIntervalsCount <= 0) {
            bucketIndex = 0; // Edge case: treat as 1 interval
        } else if (transactionIntervalsCount > histogramSize) {
            bucketIndex = histogramSize; // ">N" bucket
        } else {
            bucketIndex = (int) transactionIntervalsCount - 1; // 1-based to 0-based index
        }
        transactionHistogram.incrementAndGet(bucketIndex);
    }

    /**
     * Gets the current invoke count.
     * 
     * @return the invoke count
     */
    public long getInvokeCount() {
        return invokeCount.get();
    }

    /**
     * Gets the current transaction count.
     * 
     * @return the transaction count
     */
    public long gettransactionIntervalsCount() {
        return transactionIntervalsCount.get();
    }

    /**
     * Gets the maximum duration in milliseconds.
     * 
     * @return the max duration in milliseconds
     */
    public long getMaxDurationMillis() {
        return maxDurationMillis.get();
    }

    /**
     * Gets the average duration in seconds.
     * 
     * @return the average duration in seconds, or 0 if no invokes
     */
    public double getAvgSecondDuration() {
        long invokes = invokeCount.get();
        if (invokes == 0) {
            return 0.0;
        }
        return (double) totalDurationSeconds.get() / invokes;
    }

    /**
     * Gets the histogram bucket count for a specific index.
     * 
     * @param index the bucket index (0 to histogramSize)
     * @return the count for that bucket
     */
    public long getHistogramBucket(int index) {
        if (index < 0 || index > histogramSize) {
            return 0;
        }
        return transactionHistogram.get(index);
    }

    /**
     * Gets the histogram size (number of buckets excluding the ">N" bucket).
     * 
     * @return the histogram size
     */
    public int getHistogramSize() {
        return histogramSize;
    }

    /**
     * Gets a copy of the entire histogram as an array.
     * 
     * @return array of histogram counts
     */
    public long[] getHistogramArray() {
        long[] result = new long[histogramSize + 1];
        for (int i = 0; i <= histogramSize; i++) {
            result[i] = transactionHistogram.get(i);
        }
        return result;
    }
}
