package com.ibm.tel.wm;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Value object representing metrics for a service.
 * Thread-safe using atomic operations for counters.
 */
public class ServiceMetrics {
    private final AtomicLong invokeCount;
    private final AtomicLong transactionCount;

    /**
     * Creates a new ServiceMetrics instance with counts initialized to zero.
     */
    public ServiceMetrics() {
        this.invokeCount = new AtomicLong(0);
        this.transactionCount = new AtomicLong(0);
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
    public void incrementTransactionCount(long delta) {
        transactionCount.addAndGet(delta);
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
    public long getTransactionCount() {
        return transactionCount.get();
    }
}
