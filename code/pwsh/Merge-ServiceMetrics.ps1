<#
.SYNOPSIS
    Merges multiple service metrics CSV files into a single unified CSV.

.DESCRIPTION
    This script processes all CSV files in the input folder and creates a unified CSV in the output folder.
    For duplicate entries (same Hostname, RuntimePort, ServiceNS, CollectionStartMillis),
    it keeps only the record with the maximum CollectionDurationSeconds.

    Supports filtering out services based on regular expression patterns defined in a filter file.

.PARAMETER InputFolder
    Path to the folder containing the input CSV files to merge.

.PARAMETER OutputFolder
    Path to the folder where the unified CSV will be created.

.PARAMETER FilterFile
    Path to a text file containing regex patterns (one per line) to filter out services.
    Lines starting with # are treated as comments.

.PARAMETER NoFilter
    Switch to disable filtering even if filter file exists.

.EXAMPLE
    .\Merge-ServiceMetrics.ps1 -InputFolder ".\raw" -OutputFolder ".\processed"

.EXAMPLE
    .\Merge-ServiceMetrics.ps1 -InputFolder "C:\metrics\raw" -OutputFolder "C:\metrics\processed" -FilterFile "C:\config\filters.txt"

.EXAMPLE
    .\Merge-ServiceMetrics.ps1 -InputFolder ".\data" -OutputFolder ".\output" -NoFilter
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$InputFolder,

    [Parameter(Mandatory=$true)]
    [string]$OutputFolder,

    [string]$FilterFile = "",

    [string]$TransactionBasePrefixesFile = ".\transaction-base-prefixes.txt",

    [string]$TransactionExceptionServicesFile = ".\transaction-exception-services.txt",

    [switch]$NoFilter
)

# Function to write log messages
function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] $Message"
}

Write-Log "Starting metrics merge process..."
Write-Log "Input folder: $InputFolder"
Write-Log "Output folder: $OutputFolder"
Write-Log "Transaction base prefixes file: $TransactionBasePrefixesFile"
Write-Log "Transaction exception services file: $TransactionExceptionServicesFile"

# Validate input folder
if (-not (Test-Path $InputFolder)) {
    Write-Error "Input folder not found: $InputFolder"
    exit 1
}

# Create output folder if it doesn't exist
if (-not (Test-Path $OutputFolder)) {
    Write-Log "Creating output folder: $OutputFolder"
    try {
        New-Item -Path $OutputFolder -ItemType Directory -Force | Out-Null
    } catch {
        Write-Error "Failed to create output folder: $_"
        exit 1
    }
}

# Set output file path
$OutputFile = Join-Path $OutputFolder "unified_metrics.csv"
Write-Log "Output file: $OutputFile"

# Load filter patterns if not disabled
$filterPatterns = @()
$filteringEnabled = $false

if (-not $NoFilter -and $FilterFile -ne "" -and (Test-Path $FilterFile)) {
    Write-Log "Loading filter patterns from: $FilterFile"

    try {
        $filterLines = Get-Content $FilterFile | Where-Object {
            $_.Trim() -ne "" -and -not $_.Trim().StartsWith("#")
        }

        foreach ($line in $filterLines) {
            $pattern = $line.Trim()
            try {
                # Test if pattern is valid regex
                [void]($pattern -match "test")
                $filterPatterns += $pattern
                Write-Log "  Added filter pattern: $pattern"
            } catch {
                Write-Log "  WARNING: Invalid regex pattern skipped: $pattern"
            }
        }

        if ($filterPatterns.Count -gt 0) {
            $filteringEnabled = $true
            Write-Log "Filtering enabled with $($filterPatterns.Count) patterns"
        } else {
            Write-Log "No valid filter patterns found"
        }
    } catch {
        Write-Log "WARNING: Could not load filter file: $_"
    }
} elseif ($NoFilter) {
    Write-Log "Filtering disabled by -NoFilter switch"
} elseif ($FilterFile -eq "") {
    Write-Log "No filter file specified (filtering disabled)"
} else {
    Write-Log "Filter file not found: $FilterFile (filtering disabled)"
}

# Function to check if a service should be filtered out
function Test-ServiceFiltered {
    param([string]$ServiceNS)

    if (-not $filteringEnabled) {
        return $false
    }

    foreach ($pattern in $filterPatterns) {
        if ($ServiceNS -match $pattern) {
            return $true
        }
    }

    return $false
}

# Load transaction classification configuration
$transactionBasePrefixes = @()
$transactionExceptionServices = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

if (Test-Path $TransactionBasePrefixesFile) {
    Write-Log "Loading transaction base prefixes from: $TransactionBasePrefixesFile"
    try {
        $transactionBasePrefixes = Get-Content $TransactionBasePrefixesFile | Where-Object {
            $_.Trim() -ne "" -and -not $_.Trim().StartsWith("#")
        } | ForEach-Object { $_.Trim() }
        Write-Log "Loaded $($transactionBasePrefixes.Count) transaction base prefixes"
    } catch {
        Write-Error "Failed to load transaction base prefixes file: $_"
        exit 1
    }
} else {
    Write-Error "Transaction base prefixes file not found: $TransactionBasePrefixesFile"
    exit 1
}

if (Test-Path $TransactionExceptionServicesFile) {
    Write-Log "Loading transaction exception services from: $TransactionExceptionServicesFile"
    try {
        $exceptionLines = Get-Content $TransactionExceptionServicesFile | Where-Object {
            $_.Trim() -ne "" -and -not $_.Trim().StartsWith("#")
        } | ForEach-Object { $_.Trim() }

        foreach ($service in $exceptionLines) {
            [void]$transactionExceptionServices.Add($service)
        }

        Write-Log "Loaded $($transactionExceptionServices.Count) transaction exception services"
    } catch {
        Write-Error "Failed to load transaction exception services file: $_"
        exit 1
    }
} else {
    Write-Error "Transaction exception services file not found: $TransactionExceptionServicesFile"
    exit 1
}

function Test-InternalNamespace {
    param([string]$ServiceNS)

    if ([string]::IsNullOrWhiteSpace($ServiceNS)) {
        return $false
    }

    foreach ($prefix in $transactionBasePrefixes) {
        if ($ServiceNS.StartsWith($prefix)) {
            return $true
        }
    }

    return $false
}

function Get-TransactionFlags {
    param([string]$ServiceNS)

    $txnBaseFlag = if (Test-InternalNamespace -ServiceNS $ServiceNS) { 0 } else { 1 }
    $txnExceptionFlag = if ($transactionExceptionServices.Contains($ServiceNS)) { 1 } else { 0 }
    $txnEffectiveFlag = $txnBaseFlag + $txnExceptionFlag

    return [PSCustomObject]@{
        TxnBaseFlag = $txnBaseFlag
        TxnExceptionFlag = $txnExceptionFlag
        TxnEffectiveFlag = $txnEffectiveFlag
    }
}

# Get all CSV files recursively
$csvFiles = Get-ChildItem -Path $InputFolder -Filter "metrics_*.csv" -Recurse -File
Write-Log "Found $($csvFiles.Count) CSV files to process (including subfolders)"

if ($csvFiles.Count -eq 0) {
    Write-Error "No CSV files found in $InputFolder or its subfolders"
    exit 1
}

# Hash table to store unique records
# Key: "Hostname|RuntimePort|ServiceNS|CollectionStartMillis"
# Value: PSCustomObject with all fields
$uniqueRecords = @{}
$totalRecordsRead = 0
$duplicatesFound = 0
$filteredRecords = 0
$emptyFiles = 0
$baseTransactionalCount = 0
$exceptionTransactionalCount = 0
$effectiveTransactionalCount = 0

# Process each CSV file
foreach ($file in $csvFiles) {
    Write-Log "Processing: $($file.Name)"

    try {
        # Import CSV
        $records = Import-Csv -Path $file.FullName

        if ($null -eq $records -or $records.Count -eq 0) {
            Write-Log "  WARNING: File is empty or contains only header"
            $emptyFiles++
            continue
        }

        $fileRecordCount = 0

        foreach ($record in $records) {
            $totalRecordsRead++
            $fileRecordCount++

            # Check if service should be filtered out
            if (Test-ServiceFiltered -ServiceNS $record.ServiceNS) {
                $filteredRecords++
                continue
            }

            # Create unique key
            $key = "$($record.Hostname)|$($record.RuntimePort)|$($record.ServiceNS)|$($record.CollectionStartMillis)"

            # Check if we already have this key
            if ($uniqueRecords.ContainsKey($key)) {
                # Compare CollectionDurationSeconds and keep the one with maximum duration
                $existingDuration = [int]$uniqueRecords[$key].CollectionDurationSeconds
                $newDuration = [int]$record.CollectionDurationSeconds

                if ($newDuration -gt $existingDuration) {
                    Write-Log "  Replacing record for key: $key (duration: $existingDuration -> $newDuration)"

                    $txnFlags = Get-TransactionFlags -ServiceNS $record.ServiceNS
                    $actualTransactionIntervals = 0
                    try {
                        $actualTransactionIntervals = [int]$record.TransactionIntervalsCount * [int]$txnFlags.TxnEffectiveFlag
                    } catch {
                        $actualTransactionIntervals = 0
                    }

                    $ordered = [ordered]@{}
                    foreach ($property in $record.PSObject.Properties) {
                        $ordered[$property.Name] = $property.Value
                    }
                    $ordered["TxnBaseFlag"] = $txnFlags.TxnBaseFlag
                    $ordered["TxnExceptionFlag"] = $txnFlags.TxnExceptionFlag
                    $ordered["TxnEffectiveFlag"] = $txnFlags.TxnEffectiveFlag
                    $ordered["ActualTransactionIntervals"] = $actualTransactionIntervals

                    $uniqueRecords[$key] = [PSCustomObject]$ordered
                    $duplicatesFound++
                } else {
                    Write-Log "  Skipping duplicate with lower duration: $key (duration: $newDuration <= $existingDuration)"
                    $duplicatesFound++
                }
            } else {
                # New record, add it with transaction classification fields
                $txnFlags = Get-TransactionFlags -ServiceNS $record.ServiceNS

                if ($txnFlags.TxnBaseFlag -eq 1) {
                    $baseTransactionalCount++
                }

                if ($txnFlags.TxnExceptionFlag -eq 1) {
                    $exceptionTransactionalCount++
                }

                if ($txnFlags.TxnEffectiveFlag -gt 0) {
                    $effectiveTransactionalCount++
                }

                $actualTransactionIntervals = 0
                try {
                    $actualTransactionIntervals = [int]$record.TransactionIntervalsCount * [int]$txnFlags.TxnEffectiveFlag
                } catch {
                    $actualTransactionIntervals = 0
                }

                $ordered = [ordered]@{}
                foreach ($property in $record.PSObject.Properties) {
                    $ordered[$property.Name] = $property.Value
                }
                $ordered["TxnBaseFlag"] = $txnFlags.TxnBaseFlag
                $ordered["TxnExceptionFlag"] = $txnFlags.TxnExceptionFlag
                $ordered["TxnEffectiveFlag"] = $txnFlags.TxnEffectiveFlag
                $ordered["ActualTransactionIntervals"] = $actualTransactionIntervals

                $uniqueRecords[$key] = [PSCustomObject]$ordered
            }
        }

        Write-Log "  Processed $fileRecordCount records from this file"

    } catch {
        Write-Error "Error processing file $($file.Name): $_"
    }
}

Write-Log ""
Write-Log "Processing Summary:"
Write-Log "  Total records read: $totalRecordsRead"
Write-Log "  Filtered records: $filteredRecords"
Write-Log "  Unique records: $($uniqueRecords.Count)"
Write-Log "  Duplicates found: $duplicatesFound"
Write-Log "  Empty files: $emptyFiles"
Write-Log "  TxnBaseFlag = 1 records: $baseTransactionalCount"
Write-Log "  TxnExceptionFlag = 1 records: $exceptionTransactionalCount"
Write-Log "  TxnEffectiveFlag > 0 records: $effectiveTransactionalCount"

# Export unified CSV
Write-Log ""
Write-Log "Exporting unified CSV to: $OutputFile"

try {
    # Convert hash table values to array and export
    $uniqueRecords.Values | Export-Csv -Path $OutputFile -NoTypeInformation -Encoding UTF8

    Write-Log "Successfully exported $($uniqueRecords.Count) unique records"

    # Display file size
    $fileInfo = Get-Item $OutputFile
    $fileSizeMB = [math]::Round($fileInfo.Length / 1MB, 2)
    Write-Log "Output file size: $fileSizeMB MB"

} catch {
    Write-Error "Error exporting CSV: $_"
    exit 1
}

Write-Log ""
Write-Log "Merge completed successfully!"

# Display some statistics
Write-Log ""
Write-Log "Statistics by Hostname:"
$uniqueRecords.Values | Group-Object Hostname | Sort-Object Count -Descending | ForEach-Object {
    Write-Log "  $($_.Name): $($_.Count) services"
}

Write-Log ""
Write-Log "Statistics by RuntimePort:"
$uniqueRecords.Values | Group-Object RuntimePort | Sort-Object Count -Descending | ForEach-Object {
    Write-Log "  Port $($_.Name): $($_.Count) services"
}

# Made with Bob
