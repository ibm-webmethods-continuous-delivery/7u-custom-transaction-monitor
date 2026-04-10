package com.ibm.tel.wm;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.wm.util.JournalLogger;

/**
 * Singleton class for loading and accessing configuration properties.
 * Reads from the file specified by ZZ_LICMON_CONFIG_FILE environment variable,
 * or defaults to ./packages/ZZLicMon/config/default.properties
 */
public class ConfigLoader {
    
    private static final String ENV_VAR_NAME = "ZZ_LICMON_CONFIG_FILE";
    private static final String DEFAULT_CONFIG_PATH = "./packages/ZZLicMon/config/default.properties";
    
    private static final ConfigLoader INSTANCE = new ConfigLoader();
    
    private final Properties properties;
    private final long transactionMillisecondsThreshold;
    private final int transactionsHistogramCount;
    
    /**
     * Private constructor to prevent instantiation.
     * Loads properties from the configured file.
     */
    private ConfigLoader() {
        this.properties = new Properties();
        loadProperties();
        
        // Parse and cache frequently accessed properties
        this.transactionMillisecondsThreshold = Long.parseLong(
            properties.getProperty("transaction.milliseconds.threshold", "30000")
        );
        this.transactionsHistogramCount = Integer.parseInt(
            properties.getProperty("transactions.histogram.count", "5")
        );
        

        JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR, 
            "Expert Labs License Monitor ConfigLoader initialized:" + 
            "\n    transaction.milliseconds.threshold=" + transactionMillisecondsThreshold + 
            "\n    transactions.histogram.count=" + transactionsHistogramCount
        );


    }
    
    /**
     * Gets the singleton instance.
     * 
     * @return the ConfigLoader instance
     */
    public static ConfigLoader getInstance() {
        return INSTANCE;
    }
    
    /**
     * Loads properties from the configured file path.
     */
    private void loadProperties() {
        String configPath = System.getenv(ENV_VAR_NAME);
        if (configPath == null || configPath.trim().isEmpty()) {
            configPath = DEFAULT_CONFIG_PATH;
        }
        
        JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
            "Loading configuration from: " + configPath);
        
        try (InputStream input = new FileInputStream(configPath)) {
            properties.load(input);
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                    "Configuration loaded successfully");
        } catch (IOException e) {
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                    "Warning: Could not load configuration file: " + configPath);
            JournalLogger.logInfo(JournalLogger.LOG_EXCEPTION, JournalLogger.FAC_LICENSE_MGR,
                    "Using default values. Error: " + e.getMessage());
            // Properties will use default values specified in getter methods
        }
    }
    
    /**
     * Gets the transaction milliseconds threshold.
     * This is the duration in milliseconds that counts as one transaction time.
     * 
     * @return the threshold in milliseconds
     */
    public long getTransactionMillisecondsThreshold() {
        return transactionMillisecondsThreshold;
    }
    
    /**
     * Gets the number of histogram buckets for transaction time distribution.
     * 
     * @return the histogram count
     */
    public int getTransactionsHistogramCount() {
        return transactionsHistogramCount;
    }
    
    /**
     * Gets a property value by key.
     * 
     * @param key the property key
     * @param defaultValue the default value if key is not found
     * @return the property value or default value
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
