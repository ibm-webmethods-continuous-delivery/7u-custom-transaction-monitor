# PowerShell Scripts for License Monitor

This directory contains PowerShell scripts for processing and analyzing service metrics collected by the License Monitor.

## Scripts

### Merge-ServiceMetrics.ps1

Merges multiple service metrics CSV files into a single unified CSV for analysis.

**Purpose:**
- Consolidate metrics from multiple IS instances, nodes, and subfolders
- Deduplicate records keeping the most complete data
- Filter out unwanted services based on regex patterns
- Classify services as transactional or non-transactional using config-driven rules
- Compute effective transaction intervals for downstream analysis

**Usage:**

```powershell
.\Merge-ServiceMetrics.ps1 `
  -InputFolder <path> `
  -OutputFolder <path> `
  [-FilterFile <path>] `
  [-TransactionBasePrefixesFile <path>] `
  [-TransactionExceptionServicesFile <path>] `
  [-NoFilter]
```

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `-InputFolder` | Yes | Path to folder containing input CSV files; `metrics_*.csv` files are discovered recursively in all subfolders |
| `-OutputFolder` | Yes | Path to folder where unified CSV will be created |
| `-FilterFile` | No | Path to filter patterns file used to remove services before merge |
| `-TransactionBasePrefixesFile` | No | Path to file containing non-transaction namespace prefixes. Default: `.\transaction-base-prefixes.txt` |
| `-TransactionExceptionServicesFile` | No | Path to file containing exact service names that are transactional exceptions. Default: `.\transaction-exception-services.txt` |
| `-NoFilter` | No | Switch to disable filtering |

**Examples:**

```powershell
# Basic usage (recursively scans all subfolders under the input folder)
.\Merge-ServiceMetrics.ps1 -InputFolder "C:\metrics\raw" -OutputFolder "C:\metrics\processed"

# With custom filter file
.\Merge-ServiceMetrics.ps1 -InputFolder ".\raw" -OutputFolder ".\output" -FilterFile ".\my-filters.txt"

# With explicit transaction-rule config files
.\Merge-ServiceMetrics.ps1 `
  -InputFolder ".\raw" `
  -OutputFolder ".\output" `
  -FilterFile ".\service-filters.txt" `
  -TransactionBasePrefixesFile ".\transaction-base-prefixes.txt" `
  -TransactionExceptionServicesFile ".\transaction-exception-services.txt"

# Without filtering
.\Merge-ServiceMetrics.ps1 -InputFolder ".\raw" -OutputFolder ".\output" -NoFilter
```

**Output:**
- Creates `unified_metrics.csv` in the output folder
- Logs processing details to console
- Reports statistics:
  - records read
  - filtered records
  - duplicates found
  - unique records
  - transaction classification counts

**Added Output Columns:**
- `TxnBaseFlag`
- `TxnExceptionFlag`
- `TxnEffectiveFlag`
- `ActualTransactionIntervals`

### Add-TransactionFlags.ps1

Adds transaction classification flags to an existing unified metrics CSV.

This script is still available for post-processing an already merged CSV, but the same logic is now integrated directly into `Merge-ServiceMetrics.ps1`.

**Usage:**

```powershell
.\Add-TransactionFlags.ps1 `
  -InputFile <path-to-unified_metrics.csv> `
  -OutputFile <path-to-output.csv>
```

## Configuration Files

### service-filters.txt

Contains regular expression patterns (one per line) to filter out services from the unified CSV before export.

**Format:**
```text
# Comments start with #
^zz\.licmon[:\.].*      # Filter license monitor services
^wm\.server[:\.].*      # Filter webMethods server services
```

**Pattern Syntax:**
- PowerShell regular expressions
- `^` = start of string
- `$` = end of string
- `.*` = wildcard
- `\.` = literal dot
- `[:\.]` = match either `:` or `.`

**Common Patterns:**
```text
^wm\.server[:\.].*      # All wm.server.* and wm.server:* services
^zz\.licmon[:\.].*      # All zz.licmon.* and zz.licmon:* services
.*:test$                # Services ending with :test
^(wm|pub)\..*           # Services starting with wm. or pub.
```

### transaction-base-prefixes.txt

Contains namespace prefixes that are treated as non-transaction by default.

If `ServiceNS` starts with any prefix in this file:
- `TxnBaseFlag = 0`

Otherwise:
- `TxnBaseFlag = 1`

**Default contents:**
```text
wm.
pub.
com.
ws.
/ws/
```

### transaction-exception-services.txt

Contains exact service namespaces that are treated as transactional exceptions, even if they match a non-transaction base prefix.

If `ServiceNS` exactly matches an entry in this file:
- `TxnExceptionFlag = 1`

Otherwise:
- `TxnExceptionFlag = 0`

## Deduplication Logic

When multiple CSV files contain the same service metrics, the script uses this key to identify duplicates:

**Unique Key:** `Hostname|RuntimePort|ServiceNS|CollectionStartMillis`

**Selection:** Keeps record with maximum `CollectionDurationSeconds`

**Rationale:** Longer collection periods include all data from shorter periods for the same monitoring session.

Transaction classification is preserved when duplicates are replaced.

## Filtering Logic

1. Loads regex patterns from the filter file, if filtering is enabled
2. For each record, checks whether `ServiceNS` matches any pattern
3. Skips records that match any filter pattern
4. Reports count of filtered records

## Transaction Classification Logic

Transaction classification is intentionally separate from regex filtering.

### Base Rule

Services are treated as non-transaction by default if they start with any configured internal prefix from `transaction-base-prefixes.txt`.

Examples of default internal prefixes:
- `wm.`
- `pub.`
- `com.`
- `ws.`
- `/ws/`

### Exception Rule

Some exact internal services still represent real transactions. Those are listed in `transaction-exception-services.txt`.

### Derived Flags

For each exported row:

- `TxnBaseFlag`
  - `0` if service starts with one of the configured base prefixes
  - `1` otherwise

- `TxnExceptionFlag`
  - `1` if service exactly matches an exception service
  - `0` otherwise

- `TxnEffectiveFlag`
  - `TxnBaseFlag + TxnExceptionFlag`

- `ActualTransactionIntervals`
  - `TransactionIntervalsCount * TxnEffectiveFlag`

### Interpretation

- External/business service:
  - `TxnBaseFlag = 1`
  - `TxnExceptionFlag = 0`
  - `TxnEffectiveFlag = 1`

- Internal non-transaction service:
  - `TxnBaseFlag = 0`
  - `TxnExceptionFlag = 0`
  - `TxnEffectiveFlag = 0`

- Internal transactional exception:
  - `TxnBaseFlag = 0`
  - `TxnExceptionFlag = 1`
  - `TxnEffectiveFlag = 1`

## Integration with License Monitor

These scripts process CSV files generated by the License Monitor Java library.

**Input CSV Format:**
```csv
Hostname,RuntimePort,ExportTimestampMillis,CollectionStartMillis,CollectionDurationSeconds,ServiceNS,InvokeCount,TransactionIntervalsCount,MaxDurationMillis,AvgSecondDuration,Histogram[1],Histogram[2],Histogram[3],Histogram[4],Histogram[5],Histogram[>5]
```

**Workflow:**
1. License Monitor collects metrics on each IS instance
2. Exports CSV files periodically
3. Collect CSV files from all instances
4. Run `Merge-ServiceMetrics.ps1` to consolidate and classify
5. Analyze unified CSV with Excel, Power BI, or other tools

## Requirements

- PowerShell 5.1 or higher
- Windows, Linux, or macOS with PowerShell Core

## Version History

- **v1.0** - Initial release with merge and deduplication
- **v1.1** - Added configurable regex filtering
- **v1.2** - Separated input/output folders, made parameters mandatory
- **v1.3** - Added config-driven transaction classification and derived transaction columns

## Support

For questions or issues, contact the IBM Expert Labs team.