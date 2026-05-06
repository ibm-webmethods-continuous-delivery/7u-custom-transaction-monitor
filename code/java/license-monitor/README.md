# License Monitor - Transaction Monitoring for webMethods Integration Server

## Overview

This Java library provides transaction monitoring capabilities for webMethods Integration Server (IS). It intercepts service invocations at the top level and tracks metrics such as invocation counts, transaction intervals, execution durations, and duration histograms. The collected data helps monitor license usage and service performance.

## Purpose

The License Monitor is designed to:
- Track service invocation counts and transaction consumption
- Monitor service execution times and identify performance bottlenecks
- Generate histogram data showing transaction time distribution
- Export metrics to CSV format for analysis and reporting
- Support license compliance monitoring by tracking transaction usage

## Architecture

### Core Components

1. **InvokeChainInterceptor** - Intercepts top-level service calls in the IS invoke chain
2. **LicenseMonitor** - Singleton that manages metrics collection (thread-safe)
3. **ServiceMetrics** - Thread-safe value object storing metrics per service
4. **ConfigLoader** - Loads configuration from properties file

### How It Works

1. The `InvokeChainInterceptor` registers itself with the IS InvokeManager at class loading time
2. When a top-level service is invoked, the interceptor:
   - Records the start time
   - Allows the service to execute
   - Calculates the duration
   - Computes transaction intervals based on configured threshold
   - Updates metrics in the `LicenseMonitor`
3. Metrics are stored per service namespace in a thread-safe `ConcurrentHashMap`
4. Data can be exported to CSV for analysis

### Transaction Calculation

Transaction intervals are calculated as:
```
transactionIntervals = serviceDuration / transactionMillisecondsThreshold
```

For example, with a 30-second threshold:
- A 45-second service = 1 transaction interval (45000ms / 30000ms = 1.5, truncated to 1)
- A 90-second service = 3 transaction intervals (90000ms / 30000ms = 3)

## Project Structure

```
license-monitor/
├── pom.xml                          # Maven build configuration
├── README.md                        # This file
└── src/main/java/com/ibm/tel/wm/
    ├── InvokeChainInterceptor.java  # IS invoke chain processor
    ├── LicenseMonitor.java          # Main metrics manager (singleton)
    ├── ServiceMetrics.java          # Per-service metrics storage
    └── ConfigLoader.java            # Configuration loader (singleton)
```

## Configuration

The monitor reads configuration from a properties file specified by:
- Environment variable: `ZZ_LICMON_CONFIG_FILE`
- Default location: `./packages/ZZLicMon/config/default.properties`

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `transaction.milliseconds.threshold` | 30000 | Duration in milliseconds that counts as one transaction interval |
| `transactions.histogram.count` | 5 | Number of histogram buckets for transaction distribution |

## Building the Project

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- webMethods IS JAR files (wm-isserver.jar, wm-isclient.jar) in `/cp/` directory

### Build Commands

```bash
# Clean and build
mvn clean package

# Run tests
mvn test

# Build without tests
mvn package -DskipTests
```

### Build Output

The build produces:
- `target/license-monitor-1.0-SNAPSHOT.jar` - The compiled JAR
- Automatically copies JAR to: `${7U_CTM_WORKSPACE}/code/is-packages/ZzLicMon/code/jars/static/`

## Integration with webMethods IS

### Deployment

1. Build the project using Maven
2. The JAR is automatically copied to the IS package: `ZzLicMon/code/jars/static/`
3. Restart the Integration Server to load the interceptor
4. The interceptor registers automatically at class loading time

### Package Location

The resulting JAR is used by the webMethods IS package located at:
```
c/iwcd/7u-custom-transaction-monitor/code/is-packages/ZzLicMon
```

### Verification

Check the IS server log for the registration message:
```
Expert Labs License monitor processor registered
```

## Usage

### Accessing Metrics Programmatically

```java
// Get the singleton instance
LicenseMonitor monitor = LicenseMonitor.getInstance();

// Get metrics for a specific service
ServiceMetrics metrics = monitor.getMetrics("my.package:myService");

// Get all tracked services
String[] services = monitor.getServiceNamespaces();

// Export to CSV string
String csv = monitor.exportToCSV();

// Export to CSV file with auto-generated filename (recommended)
String filePath = monitor.exportToCSVFile("/path/to/directory");
// Returns: /path/to/directory/metrics_hostname_20260505_103000.csv

// Export to CSV file with explicit filename
monitor.exportToCSVFileWithName("/path/to/metrics.csv");
```

### CSV Export Format

The exported CSV includes **context columns** for multi-instance analysis:

**Context Columns (added to support multi-node, multi-runtime environments):**
- `Hostname` - Identifies the node where metrics were collected
- `RuntimePort` - Integration Server primary port (distinguishes multiple IS instances on same node)
- `ExportTimestampMillis` - When the data was exported (epoch milliseconds)
- `CollectionStartMillis` - When monitoring started (epoch milliseconds)
- `CollectionDurationSeconds` - How long data was collected (in seconds)

**Service Metrics Columns:**
- `ServiceNS` - Full service namespace
- `InvokeCount` - Total number of invocations
- `TransactionIntervalsCount` - Total transaction intervals consumed
- `MaxDurationMillis` - Maximum execution time in milliseconds
- `AvgSecondDuration` - Average execution time in seconds
- `Histogram[1]` through `Histogram[N]` - Count of services consuming 1 to N transaction intervals
- `Histogram[>N]` - Count of services consuming more than N transaction intervals

**Example CSV Output:**
```csv
Hostname,RuntimePort,ExportTimestampMillis,CollectionStartMillis,CollectionDurationSeconds,ServiceNS,InvokeCount,TransactionIntervalsCount,MaxDurationMillis,AvgSecondDuration,Histogram[1],Histogram[2],Histogram[3],Histogram[4],Histogram[5],Histogram[>5]
server01,5555,1746437940000,1746437880000,60,my.package:myService,150,45,2500,1.25,100,30,15,3,2,0
```

### Multi-Instance Analysis

The context columns enable easy aggregation and analysis across:
- **Multiple nodes** - Use `Hostname` to identify different servers
- **Multiple runtimes** - Use `RuntimePort` to distinguish IS instances on the same node
- **Time periods** - Use timestamps to track metrics over time
- **Collection intervals** - Use `CollectionDurationSeconds` to normalize metrics

**Merging CSVs from multiple instances:**
```bash
# Simple concatenation (skip headers from subsequent files)
cat metrics_server01_*.csv > combined.csv
tail -n +2 metrics_server02_*.csv >> combined.csv

# Or use tools like pandas, SQL, Excel Power Query for advanced analysis
```

## Thread Safety

All components are designed for thread-safe operation:
- `LicenseMonitor` uses `ConcurrentHashMap` for metrics storage
- `ServiceMetrics` uses `AtomicLong` and `AtomicLongArray` for counters
- `ConfigLoader` is immutable after initialization
- No external synchronization required

## Java Version Compatibility

- **Source/Target**: Java 11
- **Runtime**: Java 11 or higher
- **Features Used**: Lambda expressions, try-with-resources, NIO.2 file operations
- **No Java 12+ features** - Fully compatible with Java 11

## Development

### Adding New Metrics

To add new metrics:
1. Add fields to `ServiceMetrics` class (use atomic types for thread safety)
2. Update `incrementMetrics()` in `LicenseMonitor`
3. Update `exportToCSV()` to include new fields in the appropriate section (context or service metrics)
4. Update CSV header in `exportToCSV()`
5. Update unit tests to verify new fields

### Testing

Tests are located in `src/test/java/` and use JUnit 5.

Run tests:
```bash
mvn test
```

**Test Coverage:**
- Singleton pattern verification
- Thread-safe concurrent operations
- Metrics collection and aggregation
- CSV export with context information
- File I/O operations
- Error handling

### Code Style

- Follow Java naming conventions
- Use meaningful variable names
- Document public APIs with Javadoc
- Keep methods focused and concise
- Prefer immutability where possible

## Troubleshooting

### JAR Not Loading

- Verify JAR is in `ZzLicMon/code/jars/static/` directory
- Check IS server log for class loading errors
- Ensure Java 11+ is being used

### Configuration Not Loading

- Check `ZZ_LICMON_CONFIG_FILE` environment variable
- Verify properties file exists and is readable
- Check IS server log for configuration messages

### Metrics Not Collecting

- Verify interceptor registered (check server log)
- Ensure services are top-level (not called from other services)
- Check for exceptions in server log

## License

IBM Expert Labs - Internal Use

## Contact

For questions or issues, contact the IBM Expert Labs team.