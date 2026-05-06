# Custom Transaction Monitor for webMethods Integration Server

A comprehensive transaction monitoring solution for webMethods Integration Server (IS) / Microservices Runtime (MSR) that tracks service invocations, execution times, and transaction consumption for license compliance and performance analysis.

## Overview

This solution provides automated monitoring of service invocations at the Integration Server level through an invoke chain interceptor. It collects metrics continuously and exports them to CSV files for analysis, supporting multi-instance deployments with automatic aggregation capabilities.

### Key Features

- **Automatic Monitoring**: Intercepts all top-level service invocations without code changes
- **Transaction Tracking**: Calculates transaction intervals based on configurable thresholds
- **Performance Metrics**: Tracks invocation counts, execution times, and duration histograms
- **Multi-Instance Support**: Context-aware CSV exports with hostname, port, and timestamps
- **Scheduled Exports**: Automatic nightly CSV generation via scheduler
- **Graceful Shutdown**: Exports final metrics on runtime shutdown
- **Aggregation Tools**: PowerShell scripts for merging and analyzing multi-instance data

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│  webMethods Integration Server / MSR                       │
│                                                            │
│  ┌────────────────────────────────────────────────────┐    │
│  │  ZzLicMon Package (requires runtime restart)       │    │
│  │                                                    │    │
│  │  ┌──────────────────────────────────────────┐      │    │
│  │  │  Java Library (license-monitor.jar)      │      │    │
│  │  │  - InvokeChainInterceptor (static load)  │      │    │
│  │  │  - LicenseMonitor (singleton)            │      │    │
│  │  │  - ServiceMetrics (thread-safe)          │      │    │
│  │  └──────────────────────────────────────────┘      │    │
│  │                                                    │    │
│  │  ┌──────────────────────────────────────────┐      │    │
│  │  │  IS Services                             │      │    │
│  │  │  - startup: Initialize monitor           │      │    │
│  │  │  - declareScheduler: Nightly CSV export  │      │    │
│  │  │  - produceCsvReport: Export on shutdown  │      │    │
│  │  │  - getMetrics: Query current metrics     │      │    │
│  │  └──────────────────────────────────────────┘      │    │
│  └────────────────────────────────────────────────────┘    │
│                                                            │
│  CSV Exports: metrics_<hostname>_<port>_<timestamp>.csv    │
└────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  PowerShell Aggregation Scripts                             │
│  - Merge-ServiceMetrics.ps1: Consolidate multi-instance CSVs│
│  - service-filters.txt: Filter unwanted services            │
│  - Output: unified_metrics.csv                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
                    Analysis Tools
              (Excel, Power BI, SQL, etc.)
```

### Components

1. **Java Library** (`code/java/license-monitor/`)
   - Invoke chain interceptor that registers at class loading time
   - Thread-safe metrics collection using concurrent data structures
   - CSV export with context information (hostname, port, timestamps)
   - See [Java Library Documentation](code/java/license-monitor/README.md)

2. **IS Package** (`code/is-packages/ZzLicMon/`)
   - Encapsulates the Java JAR in `code/jars/static/` for static loading
   - Startup service to initialize monitoring
   - Nightly scheduler for automatic CSV exports
   - Shutdown service for final metrics export
   - Configuration file: `config/default.properties`

3. **PowerShell Scripts** (`code/pwsh/`)
   - Merge multiple CSV files from different instances and time periods
   - Deduplicate records keeping the most complete data
   - Filter unwanted services using regex patterns
   - See [PowerShell Scripts Documentation](code/pwsh/README.md)

## Quick Start

### Prerequisites

- webMethods Integration Server or MSR 10.11+. For 10.x use a recent fix level is possible
- Java 11 or higher
- Maven 3.6+ (for building)
- PowerShell 5.1+ (for aggregation scripts)

### Installation

#### 1. Build the Java Library

A convenient .devcontainer is provided for java development. It is sufficient to have Visual Studio Code with the devcontainer extension installed and a docker capable system.

```sh
cd code/java/license-monitor
mvn clean package
```

The build automatically copies the JAR to `code/is-packages/ZzLicMon/code/jars/static/`.

#### 2. Deploy the IS Package

Download a release package and install it using IS capabilities, such as the replicate/inbound folder.

For building from source purposes, use a sandbox similar to the one in `.sbx/7u-ctm-srv-dev`. In this case, the package is linked.

Mind that `.class`, `.frag`, `.bak` files are not committed in git and you will need to run the jcode.sh tool on the package. Example

```sh
cd .sbx/7u-ctm-srv-dev/

# Compile for 10.11
# set the msr tag to 10.11.0.2-ubi (not -slim) in the .env file
docker compose build

docker compose up -d

docker exec -it 7u-ctm-srv-dev-msr-1 sh

cd /opt/softwareag/IntegrationServer/bin

./jcode.sh make ZZLicMon
```

This will produce a .class file in the appropriate package folder. Deliver the package to the target runtimes as appropriate.

#### 3. Configure the Monitor

Edit `code/is-packages/ZzLicMon/config/default.properties`:

```properties
# Duration threshold for one transaction interval (in milliseconds)
transaction.milliseconds.threshold=60000

# Number of histogram buckets for transaction distribution
transactions.histogram.count=5
```

**Optional**: Set environment variable to use a custom config location:
```bash
export ZZ_LICMON_CONFIG_FILE=/path/to/custom.properties
```

#### 4. Restart Integration Server

**IMPORTANT**: The package requires a full runtime restart to load the interceptor into the root class loader.

```bash
# Stop IS
<IS_HOME>/bin/shutdown.sh

# Start IS
<IS_HOME>/bin/startup.sh
```

#### 5. Verify Installation

Check the IS server log for:
```
Expert Labs License monitor processor registered
```

### Usage

#### Automatic CSV Exports

The monitor automatically exports metrics in two scenarios:

1. **Nightly Scheduler**: Runs at 4:04 in the night
2. **Runtime Shutdown**: Exports final metrics when IS shuts down gracefully

CSV files are created in: `<IS_HOME>/packages/ZzLicMon/resources/`

**File naming convention**: `metrics_<hostname>_<port>_<timestamp>.csv`

Example: `metrics_server01_5555_20260506_120000.csv`

#### Manual CSV Export

Use the IS service `zz.licmon:produceCsvReport` to export metrics on demand.

#### Query Current Metrics

Use the IS service `zz.licmon:getMetrics` to retrieve current metrics programmatically.

### Multi-Instance Analysis

#### Understanding Multiple CSV Files

**Multiple runtimes produce multiple CSV files:**
- Each IS instance exports its own CSV with unique hostname/port
- Runtime restarts create new CSV files with different start times
- Each CSV contains cumulative metrics since process start
- The last CSV from a process includes all previous data from that process

**Example scenario:**
```
Server A (port 5555):
  - metrics_serverA_5555_20260506_000000.csv  (nightly export)
  - metrics_serverA_5555_20260506_120000.csv  (restart at noon)
  - metrics_serverA_5555_20260507_000000.csv  (next nightly export)

Server B (port 5555):
  - metrics_serverB_5555_20260506_000000.csv
  - metrics_serverB_5555_20260507_000000.csv
```

#### Aggregating CSV Files

Use the PowerShell script to merge multiple CSV files:

```powershell
cd code/pwsh

# Basic merge (with default filtering)
.\Merge-ServiceMetrics.ps1 -InputFolder "C:\metrics\raw" -OutputFolder "C:\metrics\processed"

# Merge without filtering
.\Merge-ServiceMetrics.ps1 -InputFolder ".\raw" -OutputFolder ".\output" -NoFilter

# Merge with custom filter file
.\Merge-ServiceMetrics.ps1 -InputFolder ".\raw" -OutputFolder ".\output" -FilterFile ".\custom-filters.txt"
```

**Output**: `unified_metrics.csv` in the output folder

#### Deduplication Logic

The script automatically handles duplicates using this key:
- `Hostname|RuntimePort|ServiceNS|CollectionStartMillis`

When duplicates are found, it keeps the record with the longest `CollectionDurationSeconds` (most complete data).

#### Filtering Services

Edit `code/pwsh/service-filters.txt` to exclude services from analysis:

```text
# Filter out license monitor services
^zz\.licmon\..*

# Filter out webMethods server internal services
^wm\.server\..*

# Add custom patterns
^wm\.dev\..*
^pub\.test\..*
```

### CSV Format

The exported CSV includes context columns for multi-instance analysis:

**Context Columns:**
- `Hostname` - Server where metrics were collected
- `RuntimePort` - IS primary port (distinguishes multiple instances)
- `ExportTimestampMillis` - When data was exported (epoch ms)
- `CollectionStartMillis` - When monitoring started (epoch ms)
- `CollectionDurationSeconds` - Collection period duration

**Service Metrics Columns:**
- `ServiceNS` - Full service namespace
- `InvokeCount` - Total invocations
- `TransactionIntervalsCount` - Total transaction intervals consumed
- `MaxDurationMillis` - Maximum execution time
- `AvgSecondDuration` - Average execution time (seconds)
- `Histogram[1]` through `Histogram[N]` - Transaction interval distribution
- `Histogram[>N]` - Services exceeding N intervals

## Configuration

### Transaction Threshold

The `transaction.milliseconds.threshold` determines how service duration maps to transaction intervals:

```
transactionIntervals = serviceDuration / threshold
```

**Examples with 60-second threshold:**
- 45-second service = 0 intervals (45000ms / 60000ms = 0.75, truncated to 0)
- 90-second service = 1 interval (90000ms / 60000ms = 1.5, truncated to 1)
- 180-second service = 3 intervals (180000ms / 60000ms = 3)

### Histogram Buckets

The `transactions.histogram.count` defines the number of histogram buckets for analyzing transaction distribution.

With `count=5`, you get:
- `Histogram[1]` - Services consuming 1 interval
- `Histogram[2]` - Services consuming 2 intervals
- `Histogram[3]` - Services consuming 3 intervals
- `Histogram[4]` - Services consuming 4 intervals
- `Histogram[5]` - Services consuming 5 intervals
- `Histogram[>5]` - Services consuming more than 5 intervals

## Development

### Building from Source

```bash
# Build Java library
cd code/java/license-monitor
mvn clean package

# Run tests
mvn test

# Build without tests
mvn package -DskipTests
```

### Development Sandbox

A development sandbox is available in `.sbx/7u-ctm-srv-dev/`:

```bash
cd .sbx/7u-ctm-srv-dev

# Configure environment
cp EXAMPLE.env .env
# Edit .env with your settings

# Start sandbox
docker-compose up -d

# View logs
docker-compose logs -f

# Stop sandbox
docker-compose down
```

### Testing

The Java library includes comprehensive unit tests:
- Singleton pattern verification
- Thread-safe concurrent operations
- Metrics collection and aggregation
- CSV export with context information
- File I/O operations
- Error handling

## Troubleshooting

### Interceptor Not Loading

**Symptoms**: No metrics being collected, no registration message in log

**Solutions**:
1. Verify JAR is in `ZzLicMon/code/jars/static/` directory
2. Ensure package is enabled in IS Administrator
3. Confirm runtime was fully restarted (not just package reload)
4. Check server log for class loading errors
5. Verify Java 11+ is being used

### Configuration Not Loading

**Symptoms**: Using default values instead of custom configuration

**Solutions**:
1. Check `ZZ_LICMON_CONFIG_FILE` environment variable
2. Verify properties file exists and is readable
3. Check file path is absolute
4. Review server log for configuration messages

### CSV Files Not Generated

**Symptoms**: No CSV files in resources directory

**Solutions**:
1. Verify scheduler is enabled and running
2. Check write permissions on `packages/ZzLicMon/resources/`
3. Review server log for export errors
4. Manually invoke `zz.licmon:produceCsvReport` to test

### Duplicate or Missing Data

**Symptoms**: Unexpected record counts in unified CSV

**Solutions**:
1. Review deduplication logic in PowerShell script
2. Check CSV file timestamps and collection periods
3. Verify all source CSV files are included in input folder
4. Use `-NoFilter` to see all records before filtering

## Documentation

- [Java Library Documentation](code/java/license-monitor/README.md) - Detailed technical documentation
- [PowerShell Scripts Documentation](code/pwsh/README.md) - Aggregation and analysis tools

## License

Apache 2.0. Use under guidance of IBM's Expert Labs or Client Engineering.

## Support

For questions, issues, or contributions, contact the IBM Expert Labs team.

## Version History

- **v0.0.2** - Initial release with invoke chain monitoring, CSV export, and PowerShell aggregation